package com.bigdictaphone.app.services

import android.content.Context
import com.bigdictaphone.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Executes one durable job; cancellation never removes source audio or previous results. */
class ProcessingCoordinator(private val context: Context, private val repository: RecordingRepository) {
    private val preferences = PreferencesService(context)

    suspend fun process(recordingId: String, progress: (String) -> Unit = {}) {
        val source = repository.find(recordingId) ?: return
        val mode = source.transcriptionMode ?: TranscriptionMode.LOCAL
        repository.update(recordingId) { it.copy(status = ProcessingStatus.TRANSCRIBING,
            jobStatus = JobStatus.RUNNING, transcriptionMode = mode, processingError = null) }
        try {
            val result = withTimeout(30 * 60 * 1000L) {
                val audio = AudioRecorderService(context).getRecordingFile(source.audioFileName)
                if (mode == TranscriptionMode.LOCAL) {
                    progress("Verifying local model…")
                    val model = LocalModelStore(context).verify(preferences.localModel.first())
                    LocalTranscriptionService(context).transcribe(audio, source.language, model, progress) to null
                } else {
                    progress("Uploading to Gemini…")
                    val key = preferences.geminiApiKey.first()
                    require(key.isNotBlank()) { "Add a Gemini API key in Settings. This recording remains saved." }
                    val rateLimit = RateLimitService(context)
                    when (val limit = rateLimit.canMakeRequest()) {
                        is RateLimitStatus.MinuteLimitReached -> delay(limit.resetInMs)
                        is RateLimitStatus.DailyLimitReached -> error("Daily API budget reached. Try again tomorrow.")
                        is RateLimitStatus.Available -> Unit
                    }
                    rateLimit.recordRequest()
                    GeminiService().transcribeAndSummarize(audio, source.language, key)
                }
            }
            currentCoroutineContext().ensureActive()
            repository.update(recordingId) { it.copy(transcription = result.first, summary = result.second,
                status = ProcessingStatus.COMPLETE, jobStatus = JobStatus.COMPLETE, processingError = null) }
        } catch (error: Throwable) {
            val timeout = error is TimeoutCancellationException || error is ProcessingServiceTimeout
            withContext(NonCancellable) {
                repository.update(recordingId) { it.copy(
                    status = if (it.transcription != null) ProcessingStatus.COMPLETE else if (error is CancellationException && !timeout) ProcessingStatus.RECORDED else ProcessingStatus.FAILED,
                    jobStatus = if (error is CancellationException && !timeout) JobStatus.CANCELLED else JobStatus.RETRYABLE,
                    processingError = when {
                        timeout -> "Background processing time limit reached. Audio retained; tap Retry."
                        error is CancellationException -> "Processing cancelled. Original audio retained."
                        error is LinkageError -> "Local engine unavailable for this installation. Install the ARM64 or x86_64 build."
                        else -> error.message ?: "Processing failed. Original audio retained."
                    }) }
            }
            if (error is CancellationException) throw error
            return
        }
        // Delivery has an independent state machine. A prior attempt is never retried automatically.
        if (mode == TranscriptionMode.CLOUD) deliverOnce(recordingId, progress)
    }

    private suspend fun deliverOnce(id: String, progress: (String) -> Unit) {
        val recording = repository.find(id) ?: return
        if (recording.deliveryStatus != DeliveryStatus.NONE || !preferences.autoSendEnabled.first()) return
        val recipient = preferences.userEmail.first()
        val host = preferences.smtpHost.first()
        val user = preferences.smtpUser.first()
        val pass = preferences.smtpPass.first()
        if (recipient.isBlank() || host.isBlank() || user.isBlank() || pass.isBlank()) {
            repository.update(id) { it.copy(deliveryStatus = DeliveryStatus.FAILED) }
            return
        }
        repository.update(id) { it.copy(deliveryStatus = DeliveryStatus.SENDING) }
        try {
            progress("Transcript saved. Delivering automatic email…")
            val result = SmtpEmailService().sendEmail(host, preferences.smtpPort.first().toIntOrNull() ?: 587,
                user, pass, recipient, "Meeting Summary: ${recording.title}", EmailService(context).createPersonalSummaryContent(recording))
            currentCoroutineContext().ensureActive()
            repository.update(id) { it.copy(deliveryStatus = if (result.isSuccess) DeliveryStatus.SENT else DeliveryStatus.UNCERTAIN) }
        } catch (error: Throwable) {
            withContext(NonCancellable) { repository.update(id) { it.copy(deliveryStatus = DeliveryStatus.UNCERTAIN) } }
            if (error is CancellationException) throw error
        }
    }
}
class ProcessingServiceTimeout : CancellationException("Android foreground processing time limit")
