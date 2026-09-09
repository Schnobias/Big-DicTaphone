package com.bigdictaphone.app.services

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class LocalModel(val title: String, val fileName: String, val bytes: Long, val sha256: String) {
    TINY("Tiny · 74 MiB · faster", "ggml-tiny.bin", 77691713, "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"),
    BASE("Base · 141 MiB · better accuracy", "ggml-base.bin", 147951465, "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe");
}

internal data class ModelDescriptor(val fileName: String, val bytes: Long, val sha256: String)

enum class ModelReadiness { Missing, Verifying, Ready, Downloading, Damaged, Error }

/** Only verified, known multilingual models can reach the native parser. */
class LocalModelStore internal constructor(
    private val directory: File,
    private val client: OkHttpClient,
    private val baseUrl: String = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"
) {
    constructor(context: Context) : this(
        File(context.noBackupFilesDir, "models"),
        OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(20, TimeUnit.MINUTES).build(),
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"
    )
    init { require(directory.mkdirs() || directory.isDirectory) { "Could not create local model directory." } }
    private val _readiness = MutableStateFlow(LocalModel.values().associateWith { readinessOf(it) })
    private val operationMutex = Mutex()
    val readiness: StateFlow<Map<LocalModel, ModelReadiness>> = _readiness.asStateFlow()
    fun file(model: LocalModel) = File(directory, model.fileName)
    fun isInstalled(model: LocalModel) = readiness.value[model] == ModelReadiness.Ready
    suspend fun refresh(model: LocalModel) {
        withContext(Dispatchers.IO) { operationMutex.withLock {
            setReadiness(model, ModelReadiness.Verifying)
            val target = file(model)
            if (!target.exists()) {
                setReadiness(model, ModelReadiness.Missing)
            } else if (!target.isFile || target.length() != model.bytes) {
                setReadiness(model, ModelReadiness.Damaged)
            } else if (hash(target) == model.sha256) {
                setReadiness(model, ModelReadiness.Ready)
            } else {
                setReadiness(model, ModelReadiness.Damaged)
            }
        } }
    }
    suspend fun verify(model: LocalModel): File = withContext(Dispatchers.IO) { operationMutex.withLock {
        val target = file(model)
        setReadiness(model, ModelReadiness.Verifying)
        require(target.isFile && target.length() == model.bytes) {
            setReadiness(model, if (target.exists()) ModelReadiness.Damaged else ModelReadiness.Missing)
            if (target.exists()) "The local model is damaged. Download it again in Settings."
            else "Download ${model.name.lowercase()} in Settings first. Your recording is saved."
        }
        if (hash(target) != model.sha256) {
            setReadiness(model, ModelReadiness.Damaged)
            error("The local model is damaged. Download it again in Settings.")
        }
        setReadiness(model, ModelReadiness.Ready)
        target
    } }
    suspend fun download(model: LocalModel, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) { operationMutex.withLock {
        download(ModelDescriptor(model.fileName, model.bytes, model.sha256), file(model), model, onProgress)
    } }
    internal suspend fun downloadDescriptor(descriptor: ModelDescriptor, target: File, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) { operationMutex.withLock {
        download(descriptor, target, null, onProgress)
    } }
    @OptIn(InternalCoroutinesApi::class)
    private suspend fun download(descriptor: ModelDescriptor, target: File, model: LocalModel?, onProgress: (Int) -> Unit) {
        require(directory.usableSpace > descriptor.bytes + 32L * 1024 * 1024) { "Not enough free storage for this model." }
        val partial = File.createTempFile(".${descriptor.fileName}.", ".part", directory)
        val previousReadiness = model?.let { readiness.value[it] }
        model?.let { setReadiness(it, ModelReadiness.Downloading) }
        try {
            val request = Request.Builder().url(baseUrl + descriptor.fileName).build()
            val call = client.newCall(request)
            val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion(onCancelling = true) { call.cancel() }
            try { call.execute().use { response ->
                check(response.isSuccessful) { "Model download failed (HTTP ${response.code}). Try again on Wi-Fi." }
                val body = requireNotNull(response.body) { "Empty model download." }
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= descriptor.bytes) { "Unexpected model size." }
                            output.write(buffer, 0, count)
                            onProgress((total * 100 / descriptor.bytes).toInt())
                        }
                        check(total == descriptor.bytes) { "Model download was incomplete. Please retry." }
                    }
                }
            } } finally { cancellation?.dispose() }
            check(hash(partial) == descriptor.sha256) { "Model integrity check failed. Please retry." }
            replaceAtomically(partial, target)
            model?.let { setReadiness(it, ModelReadiness.Ready) }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            model?.let { setReadiness(it, if (previousReadiness == ModelReadiness.Ready) ModelReadiness.Ready else verifiedReadiness(it)) }
            throw e
        } finally { partial.delete() }
    }
    private fun readinessOf(model: LocalModel): ModelReadiness =
        if (!file(model).exists()) ModelReadiness.Missing else ModelReadiness.Verifying
    private fun setReadiness(model: LocalModel, state: ModelReadiness) {
        _readiness.update { it.toMutableMap().apply { put(model, state) } }
    }
    private suspend fun verifiedReadiness(model: LocalModel): ModelReadiness {
        val target = file(model)
        if (!target.exists()) return ModelReadiness.Error
        if (!target.isFile || target.length() != model.bytes) return ModelReadiness.Damaged
        return if (hash(target) == model.sha256) ModelReadiness.Ready else ModelReadiness.Damaged
    }
    private fun replaceAtomically(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            val backup = File(directory, "${target.name}.previous")
            if (backup.exists()) backup.delete()
            if (target.exists()) check(target.renameTo(backup)) { "Could not preserve the installed model." }
            try {
                check(source.renameTo(target)) { "Could not install the model." }
                backup.delete()
            } catch (e: Exception) {
                target.delete()
                backup.renameTo(target)
                throw e
            }
        }
    }
    private suspend fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
