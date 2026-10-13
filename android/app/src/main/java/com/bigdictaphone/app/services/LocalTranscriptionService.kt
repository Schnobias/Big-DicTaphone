package com.bigdictaphone.app.services

import android.content.Context
import com.bigdictaphone.app.data.RecordingLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/** Saved-file transcription with an app-owned model. This service has no network client. */
class LocalTranscriptionService(private val context: Context) {
    suspend fun transcribe(audioFile: File, language: RecordingLanguage, model: File, onProgress: (String) -> Unit): String = withContext(Dispatchers.IO) {
        require(model.isFile) { "Download a local model in Settings first. Your recording is saved." }
        val pcm = File.createTempFile("transcription-", ".pcm", context.cacheDir)
        try {
            onProgress("Decoding saved audio on this phone…")
            val rms = AudioDecoder().decode(audioFile, pcm)
            require(rms >= 0.0005) { "No audible speech detected. The original recording is still saved." }
            currentCoroutineContext().ensureActive()
            val jobContext = currentCoroutineContext()
            val code = when (language) {
                RecordingLanguage.AUTO -> "auto"
                RecordingLanguage.DUTCH -> "nl"
                else -> "en"
            }
            onProgress("Loading local model…")
            val bytes = WhisperNative().transcribe(model.absolutePath, pcm.absolutePath, code, object : WhisperNative.Callback {
                override fun isCancelled() = !jobContext.isActive
                override fun onProgress(percent: Int) { onProgress("Transcribing on this phone: ${percent.coerceIn(0, 100)}%") }
            })
            jobContext.ensureActive()
            val text = bytes?.toString(Charsets.UTF_8)?.trim().orEmpty()
            require(text.isNotBlank()) { "No speech detected. The original recording is still saved." }
            text
        } finally { pcm.delete() }
    }
}
