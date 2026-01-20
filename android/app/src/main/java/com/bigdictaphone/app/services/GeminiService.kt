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
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

/**
 * Service for interacting with Google's Gemini Flash API for AI summarization
 */
class GeminiService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(300, TimeUnit.SECONDS)  // 5 min for long audio
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    private val uploadUrl = "https://generativelanguage.googleapis.com/upload/v1beta/files"
    private val fileUrl = "https://generativelanguage.googleapis.com/v1beta/files"
    
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
        
        // Check file size (Gemini has limits)
        val fileSizeMB = audioFile.length() / (1024.0 * 1024.0)
        
        // Determine MIME type based on file extension
        val mimeType = when {
            audioFile.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
            audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            audioFile.name.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
            audioFile.name.endsWith(".aac", ignoreCase = true) -> "audio/aac"
            else -> "audio/mp4" // Default for Android recordings
        }

        // Prepare file part - either inline or uploaded
        // Use large file upload for files > 10MB to avoid OOM when Base64 encoding
        val filePartJson = if (fileSizeMB > 10) {
            val fileUri = uploadLargeFile(audioFile, mimeType, audioFile.length(), apiKey)
            """
            {
                "file_data": {
                    "mime_type": "$mimeType",
                    "file_uri": "$fileUri"
                }
            }
            """
        } else {
            val audioBytes = audioFile.readBytes()
            val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            """
            {
                "inline_data": {
                    "mime_type": "$mimeType",
                    "data": "$audioBase64"
                }
            }
            """
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
                        $filePartJson
                    ]
                }],
                "generationConfig": {
                    "temperature": 0.3,
                    "maxOutputTokens": 65536
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
            // Try to fix common JSON issues (unescaped newlines in string values)
            try {
                val fixedText = fixJsonNewlines(cleanedText)
                val parsed = json.decodeFromString<TranscriptionAndSummary>(fixedText)
                Pair(parsed.transcription ?: "", parsed.toMeetingSummary())
            } catch (e2: Exception) {
                // Include a snippet of the problematic JSON for debugging
                val snippet = cleanedText.take(100).replace("\n", "\\n")
                throw GeminiException("Failed to parse response: ${e.message}\nJSON input: .....$snippet.....")
            }
        }
    }
    
    /**
     * Fix common JSON formatting issues from Gemini responses.
     * Gemini sometimes returns unescaped newlines inside string values.
     */
    private fun fixJsonNewlines(jsonText: String): String {
        val result = StringBuilder()
        var inString = false
        var escaped = false
        
        for (i in jsonText.indices) {
            val c = jsonText[i]
            
            when {
                escaped -> {
                    result.append(c)
                    escaped = false
                }
                c == '\\' && inString -> {
                    result.append(c)
                    escaped = true
                }
                c == '"' -> {
                    inString = !inString
                    result.append(c)
                }
                c == '\n' && inString -> {
                    // Unescaped newline inside string - escape it
                    result.append("\\n")
                }
                c == '\r' && inString -> {
                    // Skip carriage returns inside strings
                }
                else -> result.append(c)
            }
        }
        
        return result.toString()
    }

    /**
     * Upload a large file to Gemini API and wait for processing
     */
    private suspend fun uploadLargeFile(file: File, mimeType: String, fileSize: Long, apiKey: String): String {
        // 1. Upload the file
        val requestBody = file.asRequestBody(mimeType.toMediaType())
        
        val request = Request.Builder()
            .url("$uploadUrl?key=$apiKey")
            .header("X-Goog-Upload-Protocol", "raw")
            .header("X-Goog-Upload-Command", "start, upload, finalize")
            .header("X-Goog-Upload-Header-Content-Length", fileSize.toString())
            .header("X-Goog-Upload-Header-Content-Type", mimeType)
            .post(requestBody)
            .build()
            
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw GeminiException("Failed to upload file: ${response.message}")
        }
        
        val responseBody = response.body?.string() ?: throw GeminiException("Empty upload response")
        val uploadResponse = json.decodeFromString<FileUploadResponse>(responseBody)
        val fileUri = uploadResponse.file.uri
        val fileName = uploadResponse.file.name // This is the resource name (files/...)
        
        // 2. Wait for processing to complete
        waitForProcessing(fileName, apiKey)
        
        return fileUri
    }
    
    /**
     * Poll the file status until it's ACTIVE
     */
    private suspend fun waitForProcessing(fileName: String, apiKey: String) {
        var attempts = 0
        while (attempts < 60) { // Wait up to 2 minutes (2s * 60)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/$fileName?key=$apiKey")
                .get()
                .build()
                
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()?.take(200) ?: "no details"
                throw GeminiException("Failed to check file status: HTTP ${response.code} - $errorBody")
            }
            
            val body = response.body?.string() ?: throw GeminiException("Empty status response")
            val status = json.decodeFromString<FileStatusResponse>(body)
            
            when (status.state) {
                "ACTIVE" -> return
                "FAILED" -> throw GeminiException("File processing failed")
                else -> {
                    delay(2000)
                    attempts++
                }
            }
        }
        throw GeminiException("File processing timed out")
    }
}

@kotlinx.serialization.Serializable
private data class FileUploadResponse(val file: FileInfo)

@kotlinx.serialization.Serializable
private data class FileInfo(val name: String, val uri: String)

@kotlinx.serialization.Serializable
private data class FileStatusResponse(val name: String, val state: String)

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
