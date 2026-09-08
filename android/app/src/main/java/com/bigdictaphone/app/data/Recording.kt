package com.bigdictaphone.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a single voice recording with its metadata and processing status
 */
@Serializable
data class Recording(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long = 0, // Duration in milliseconds
    val audioFileName: String,
    val language: RecordingLanguage = RecordingLanguage.AUTO,
    val transcription: String? = null,
    val summary: MeetingSummary? = null,
    val status: ProcessingStatus = ProcessingStatus.RECORDED,
    val transcriptionMode: TranscriptionMode? = null,
    val processingError: String? = null,
    val jobStatus: JobStatus = JobStatus.NONE,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.NONE,
    val captureStatus: CaptureStatus = CaptureStatus.SAVED
) {
    val formattedDuration: String
        get() {
            val totalSeconds = duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            return if (hours > 0) {
                String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
            }
        }
}

/**
 * Supported languages for transcription
 */
@Serializable
enum class RecordingLanguage(val locale: String, val displayName: String, val flag: String) {
    AUTO("auto", "Auto-detect", "🌐"),
    DUTCH("nl-NL", "Nederlands", "🇳🇱"),
    ENGLISH("en-US", "English (US)", "🇺🇸"),
    ENGLISH_UK("en-GB", "English (UK)", "🇬🇧")
}

/**
 * Processing status of a recording
 */
@Serializable
enum class ProcessingStatus(val displayName: String) {
    RECORDED("Recorded"),
    TRANSCRIBING("Transcribing..."),
    SUMMARIZING("Summarizing..."),
    COMPLETE("Complete"),
    FAILED("Failed");
    
    val isProcessing: Boolean
        get() = this == TRANSCRIBING || this == SUMMARIZING
}

@Serializable
enum class JobStatus { NONE, QUEUED, RUNNING, RETRYABLE, CANCELLED, COMPLETE }

@Serializable
enum class DeliveryStatus { NONE, SENDING, SENT, FAILED, UNCERTAIN }

@Serializable
enum class CaptureStatus { ACTIVE, PAUSED, PENDING, SAVED, INTERRUPTED }
