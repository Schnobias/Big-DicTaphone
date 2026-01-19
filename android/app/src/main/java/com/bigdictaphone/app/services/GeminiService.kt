package com.bigdictaphone.app.services

import android.util.Base64
import com.bigdictaphone.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with Google's Gemini Flash API for AI summarization
 */
class GeminiService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    
    /**
     * Generate a meeting summary from a transcription
     */
    suspend fun generateSummary(
        transcription: String, 
        language: RecordingLanguage,
        apiKey: String
    ): MeetingSummary = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw GeminiException("No API key configured. Please add your Gemini API key in Settings.")
        }
        
        val prompt = createPrompt(transcription, language)
        val response = sendRequest(prompt, apiKey)
        
        val text = response.text
            ?: throw GeminiException(response.error?.message ?: "No response from AI")
        
        parseResponse(text)
    }
    
    /**
     * Transcribe and summarize audio using Gemini's multimodal capabilities
     */
    suspend fun transcribeAndSummarize(
        audioFile: File,
        language: RecordingLanguage,
        apiKey: String
    ): Pair<String, MeetingSummary> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw GeminiException("No API key configured")
        }
        
        if (!audioFile.exists()) {
            throw GeminiException("Audio file not found: ${audioFile.name}")
        }
        
        // Read audio file and encode to base64
        val audioBytes = audioFile.readBytes()
        
        // Check file size (Gemini has limits)
        val fileSizeMB = audioBytes.size / (1024.0 * 1024.0)
        if (fileSizeMB > 20) {
            throw GeminiException("Audio file too large (${String.format("%.1f", fileSizeMB)}MB). Maximum is 20MB.")
        }
        
        val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        
        // Determine MIME type based on file extension
        val mimeType = when {
            audioFile.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
            audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            audioFile.name.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
            audioFile.name.endsWith(".aac", ignoreCase = true) -> "audio/aac"
            else -> "audio/mp4" // Default for Android recordings
        }
        
        val languageInstruction = when (language) {
            RecordingLanguage.AUTO -> "Detect the language automatically and transcribe in that language"
            RecordingLanguage.DUTCH -> "Transcribe in Dutch (Nederlands)"
            else -> "Transcribe in English"
        }
        
        // Escape any quotes in the prompt for JSON
        val prompt = """
            Please listen to this audio recording and:
            1. First, $languageInstruction. IMPORTANT: Identify different speakers and label them as "Speaker 1:", "Speaker 2:", etc. or by their name if mentioned.
            2. Then, provide a structured summary
            
            Respond with a JSON object in this exact format (no markdown, just raw JSON):
            {
                "transcription": "the full transcription text here",
                "keyPoints": ["point 1", "point 2"],
                "actionItems": [
                    {"task": "description", "assignee": "person name or null", "deadline": "date/timeframe or null"}
                ],
                "futurePoints": ["topic 1", "topic 2"],
                "managementDraft": "A concise 2-3 paragraph executive summary suitable for management.",
                "funnyQuote": "A playful, witty, or humorous one-liner related to the meeting content. Be creative and make it memorable!"
            }
        """.trimIndent().replace("\"", "\\\"").replace("\n", "\\n")
        
        val requestBody = """
            {
                "contents": [{
                    "parts": [
                        {"text": "$prompt"},
                        {
                            "inline_data": {
                                "mime_type": "$mimeType",
                                "data": "$audioBase64"
                            }
                        }
                    ]
                }],
                "generationConfig": {
                    "temperature": 0.3,
                    "maxOutputTokens": 4096
                }
            }
        """.trimIndent()
        
        val request = Request.Builder()
            .url("$baseUrl?key=$apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw GeminiException("Empty response from server")
            
            if (!response.isSuccessful) {
                // Try to parse error message
                val errorMsg = try {
                    val errorResponse = json.decodeFromString<GeminiResponse>(responseBody)
                    errorResponse.error?.message ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: ${responseBody.take(200)}"
                }
                throw GeminiException("API Error: $errorMsg")
            }
            
            val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
            val text = geminiResponse.text ?: throw GeminiException("No response text from AI")
            
            // Parse the combined response
            parseTranscriptionResponse(text)
        } catch (e: GeminiException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw GeminiException("Request timed out. The audio file may be too long.")
        } catch (e: Exception) {
            throw GeminiException("Network error: ${e.message}")
        }
    }
    
    /**
     * Create a structured prompt for the AI
     */
    private fun createPrompt(transcription: String, language: RecordingLanguage): String {
        val languageName = if (language == RecordingLanguage.DUTCH) "Dutch" else "English"
        
        return """
            Analyze this meeting/voice note transcription and provide a structured summary.
            The transcription is in $languageName. Please respond in the same language.
            
            TRANSCRIPTION:
            $transcription
            
            Please respond with a JSON object in this exact format (no markdown, just raw JSON):
            {
                "keyPoints": ["point 1", "point 2", ...],
                "actionItems": [
                    {"task": "description", "assignee": "person name or null", "deadline": "date/timeframe or null"},
                    ...
                ],
                "futurePoints": ["topic 1", "topic 2", ...],
                "managementDraft": "A concise 2-3 paragraph executive summary suitable for upper management, focusing on key decisions, progress, and any issues that need attention."
            }
            
            Guidelines:
            - Key points: Main topics discussed, decisions made, important information shared
            - Action items: Specific tasks that need to be done, with who should do them and by when if mentioned
            - Future points: Topics that were deferred or should be discussed in a follow-up meeting
            - Management draft: Professional tone, highlight achievements and progress, mention blockers or risks
        """.trimIndent()
    }
    
    /**
     * Send request to Gemini API
     */
    private fun sendRequest(prompt: String, apiKey: String): GeminiResponse {
        val requestBody = """
            {
                "contents": [{
                    "parts": [{"text": ${json.encodeToString(kotlinx.serialization.serializer(), prompt)}}]
                }],
                "generationConfig": {
                    "temperature": 0.3,
                    "maxOutputTokens": 2048,
                    "responseMimeType": "application/json"
                }
            }
        """.trimIndent()
        
        val request = Request.Builder()
            .url("$baseUrl?key=$apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw GeminiException("Empty response")
        
        if (!response.isSuccessful) {
            val errorResponse = try {
                json.decodeFromString<GeminiResponse>(responseBody)
            } catch (e: Exception) {
                null
            }
            throw GeminiException(errorResponse?.error?.message ?: "HTTP ${response.code}")
        }
        
        return json.decodeFromString<GeminiResponse>(responseBody)
    }
    
    /**
     * Parse the JSON response into a MeetingSummary
     */
    private fun parseResponse(text: String): MeetingSummary {
        var cleanedText = text.trim()
        
        // Remove markdown code blocks if present
        if (cleanedText.startsWith("```json")) {
            cleanedText = cleanedText.removePrefix("```json")
        }
        if (cleanedText.startsWith("```")) {
            cleanedText = cleanedText.removePrefix("```")
        }
        if (cleanedText.endsWith("```")) {
            cleanedText = cleanedText.removeSuffix("```")
        }
        cleanedText = cleanedText.trim()
        
        return try {
            val parsed = json.decodeFromString<ParsedSummary>(cleanedText)
            parsed.toMeetingSummary()
        } catch (e: Exception) {
            throw GeminiException("Failed to parse AI response: ${e.message}")
        }
    }
    
    /**
     * Parse combined transcription and summary response
     */
    private fun parseTranscriptionResponse(text: String): Pair<String, MeetingSummary> {
        var cleanedText = text.trim()
        
        if (cleanedText.startsWith("```json")) {
            cleanedText = cleanedText.removePrefix("```json")
        }
        if (cleanedText.startsWith("```")) {
            cleanedText = cleanedText.removePrefix("```")
        }
        if (cleanedText.endsWith("```")) {
            cleanedText = cleanedText.removeSuffix("```")
        }
        cleanedText = cleanedText.trim()
        
        return try {
            val parsed = json.decodeFromString<TranscriptionAndSummary>(cleanedText)
            Pair(parsed.transcription ?: "", parsed.toMeetingSummary())
        } catch (e: Exception) {
            throw GeminiException("Failed to parse response: ${e.message}")
        }
    }
}

@kotlinx.serialization.Serializable
private data class TranscriptionAndSummary(
    val transcription: String? = null,
    val keyPoints: List<String>? = null,
    val actionItems: List<ParsedActionItem>? = null,
    val futurePoints: List<String>? = null,
    val managementDraft: String? = null,
    val funnyQuote: String? = null
) {
    fun toMeetingSummary(): MeetingSummary = MeetingSummary(
        keyPoints = keyPoints ?: emptyList(),
        actionItems = (actionItems ?: emptyList()).map {
            ActionItem(task = it.task, assignee = it.assignee, deadline = it.deadline)
        },
        futurePoints = futurePoints ?: emptyList(),
        managementDraft = managementDraft,
        funnyQuote = funnyQuote
    )
}

class GeminiException(message: String) : Exception(message)
