package com.bigdictaphone.app.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.bigdictaphone.app.data.RecordingLanguage
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Service for transcribing audio using Android's Speech Recognition
 */
class SpeechRecognizerService(private val context: Context) {
    
    /**
     * Check if speech recognition is available
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
    
    /**
     * Transcribe an audio file
     * Note: Android's SpeechRecognizer works best with live audio.
     * For file-based transcription, we use a workaround with the recognizer intent.
     */
    suspend fun transcribe(audioFile: File, language: RecordingLanguage): String {
        if (!isAvailable()) {
            throw TranscriptionException("Speech recognition is not available on this device")
        }
        
        return suspendCancellableCoroutine { continuation ->
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.locale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            val resultBuilder = StringBuilder()
            
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                
                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Unknown error"
                    }
                    
                    speechRecognizer.destroy()
                    
                    if (resultBuilder.isNotEmpty()) {
                        // If we have partial results, return those
                        continuation.resume(resultBuilder.toString())
                    } else {
                        continuation.resumeWithException(TranscriptionException(errorMessage))
                    }
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { resultBuilder.append(it) }
                    
                    speechRecognizer.destroy()
                    continuation.resume(resultBuilder.toString())
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { partial ->
                        // Update partial results for progress indication
                    }
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            
            speechRecognizer.startListening(intent)
            
            continuation.invokeOnCancellation {
                speechRecognizer.cancel()
                speechRecognizer.destroy()
            }
        }
    }
    
    /**
     * Transcribe text using a simpler approach for pre-recorded audio
     * Since Android's SpeechRecognizer doesn't directly support audio files,
     * we'll use Gemini to transcribe as well (included in the summarization step)
     */
    suspend fun transcribeWithGemini(audioFile: File, language: RecordingLanguage, geminiService: GeminiService): String {
        // For now, we'll use a placeholder since Android SpeechRecognizer 
        // doesn't easily support audio file transcription
        // In a production app, you'd use Google Cloud Speech-to-Text API
        // or include audio data in the Gemini request
        
        return "Audio transcription requires the Gemini API. Please ensure your API key is configured."
    }
}

class TranscriptionException(message: String) : Exception(message)
