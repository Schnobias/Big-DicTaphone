package com.bigdictaphone.app.services

import android.content.Context
import android.util.AtomicFile
import com.bigdictaphone.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The single durable source of truth for recordings and their lifecycle state. */
class RecordingRepository private constructor(private val metadata: AtomicFile, private val audioDirectory: File) {
    constructor(context: Context) : this(AtomicFile(File(context.filesDir, "recordings.json")), File(context.filesDir, "recordings"))
    /** Test seam: metadata and audio are isolated under this directory. */
    constructor(context: Context, testRoot: File) : this(AtomicFile(File(testRoot, "recordings.json")), File(testRoot, "recordings"))

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private var initialized = false
    private var recoveredOnce = false
    private suspend fun <T> transaction(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
    private var state = emptyList<Recording>()
    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()
    private val _storageError = MutableStateFlow<String?>(null)
    val storageError: StateFlow<String?> = _storageError.asStateFlow()

    /** Ordinary loading is read-only; recovery is deliberately separate and called once by Application. */
    fun load(): List<Recording> = runBlocking { transaction { ensureLoadedLocked(); state } }

    suspend fun initialize() = transaction {
        ensureLoadedLocked()
        if (recoveredOnce) return@transaction
        val recovered = recoverLocked(state)
        if (recovered != state) writeLocked(recovered)
        recoveredOnce = true
        state = recovered
        _recordings.value = state
    }

    suspend fun snapshot(): List<Recording> = transaction { ensureLoadedLocked(); state }

    fun current(): List<Recording> = recordings.value
    fun find(id: String): Recording? = current().firstOrNull { it.id == id }

    suspend fun update(id: String, transform: (Recording) -> Recording): Recording? = transaction {
        ensureLoadedLocked()
        val index = state.indexOfFirst { it.id == id }
        if (index < 0) return@transaction null
        val next = state.toMutableList().apply { this[index] = transform(this[index]) }
        writeLocked(next); state = next; _recordings.value = state; next[index]
    }

    suspend fun insert(recording: Recording): Boolean = transaction {
        ensureLoadedLocked()
        if (state.any { it.id == recording.id }) return@transaction false
        val next = listOf(recording) + state
        writeLocked(next); state = next; _recordings.value = state; true
    }

    suspend fun remove(id: String): Boolean = transaction {
        ensureLoadedLocked()
        val next = state.filterNot { it.id == id }
        if (next.size == state.size) return@transaction false
        writeLocked(next); state = next; _recordings.value = state; true
    }

    suspend fun enqueue(id: String): Boolean = transaction {
        ensureLoadedLocked()
        val index = state.indexOfFirst { it.id == id }
        if (index < 0 || state[index].jobStatus == JobStatus.QUEUED || state[index].jobStatus == JobStatus.RUNNING) return@transaction false
        val next = state.toMutableList().apply { this[index] = this[index].copy(jobStatus = JobStatus.QUEUED, processingError = null) }
        writeLocked(next); state = next; _recordings.value = state; true
    }

    fun updateByAudioFile(fileName: String, transform: (Recording) -> Recording): Recording? = runBlocking { transaction {
        ensureLoadedLocked()
        val index = state.indexOfFirst { it.audioFileName == fileName }
        if (index < 0) return@transaction null
        val next = state.toMutableList().apply { this[index] = transform(this[index]) }
        writeLocked(next); state = next; _recordings.value = state; next[index]
    } }

    fun recoverInterrupted(): List<Recording> = runBlocking { initialize(); snapshot() }

    /** Compatibility for existing callers; completion means the AtomicFile commit succeeded. */
    fun saveBlocking(recordings: List<Recording>) = runBlocking { transaction {
        ensureLoadedLocked(); writeLocked(recordings); state = recordings; _recordings.value = state
    } }

    suspend fun replace(transform: (List<Recording>) -> List<Recording>) = transaction {
        ensureLoadedLocked(); val next = transform(state); writeLocked(next); state = next; _recordings.value = state
    }

    private fun ensureLoadedLocked() {
        if (initialized) return
        initialized = true
        state = try {
            if (!metadata.baseFile.exists() && !File(metadata.baseFile.path + ".bak").exists()) emptyList() else metadata.openRead().bufferedReader().use { json.decodeFromString<List<Recording>>(it.readText()) }
        } catch (error: Throwable) {
            _storageError.value = "Recording library could not be read: ${error.message ?: "invalid metadata"}"
            initialized = false
            throw error
        }
        _recordings.value = state
    }

    private fun recoverLocked(source: List<Recording>): List<Recording> {
        val known = source.mapTo(mutableSetOf()) { it.audioFileName }
        val recovered = source.map { recording ->
            var next = recording
            if (recording.captureStatus == CaptureStatus.ACTIVE || recording.captureStatus == CaptureStatus.PAUSED) next = next.copy(captureStatus = CaptureStatus.INTERRUPTED, processingError = "Capture was interrupted; original audio was preserved.")
            if (next.jobStatus == JobStatus.RUNNING || next.status.isProcessing) next = next.copy(status = if (next.transcription != null) ProcessingStatus.COMPLETE else ProcessingStatus.FAILED, jobStatus = JobStatus.RETRYABLE, processingError = "Processing was interrupted. Tap Try Again.")
            if (next.deliveryStatus == DeliveryStatus.SENDING) next = next.copy(deliveryStatus = DeliveryStatus.UNCERTAIN)
            next
        }.toMutableList()
        audioDirectory.listFiles { file -> file.isFile && file.extension.equals("m4a", true) }.orEmpty().filterNot { it.name in known }.forEach { orphan ->
            recovered += Recording(id = "recovered-${orphan.nameWithoutExtension}", title = "Recovered recording", audioFileName = orphan.name, captureStatus = CaptureStatus.INTERRUPTED, processingError = "Recovered audio from an interrupted capture.")
        }
        return recovered
    }

    private fun writeLocked(next: List<Recording>) {
        audioDirectory.mkdirs()
        var output: java.io.FileOutputStream? = null
        try {
            output = metadata.startWrite()
            output.write(json.encodeToString(next).toByteArray(Charsets.UTF_8)); output.fd.sync(); metadata.finishWrite(output)
            _storageError.value = null
        } catch (error: Throwable) {
            metadata.failWrite(output)
            _storageError.value = "Recording metadata could not be saved: ${error.message ?: "storage error"}"
            throw error
        }
    }
}
