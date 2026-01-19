package com.bigdictaphone.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bigdictaphone.app.data.*
import com.bigdictaphone.app.data.Stakeholder
import com.bigdictaphone.app.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * ViewModel for managing recordings and app state
 */
class RecordingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context = application.applicationContext
    
    // Services
    val audioRecorder = AudioRecorderService(context)
    private val geminiService = GeminiService()
    private val preferencesService = PreferencesService(context)
    val emailService = EmailService(context)
    private val smtpEmailService = SmtpEmailService()
    private val rateLimitService = RateLimitService(context)
    
    // Rate limit status
    private val _rateLimitStatus = MutableStateFlow("")
    val rateLimitStatus: StateFlow<String> = _rateLimitStatus.asStateFlow()
    
    private val _queuedCount = MutableStateFlow(0)
    val queuedCount: StateFlow<Int> = _queuedCount.asStateFlow()
    
    // State
    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _processingMessage = MutableStateFlow("")
    val processingMessage: StateFlow<String> = _processingMessage.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _selectedLanguage = MutableStateFlow(RecordingLanguage.ENGLISH)
    val selectedLanguage: StateFlow<RecordingLanguage> = _selectedLanguage.asStateFlow()
    
    private val _currentRecording = MutableStateFlow<Recording?>(null)
    val currentRecording: StateFlow<Recording?> = _currentRecording.asStateFlow()
    
    // Timer for recording updates
    private var recordingTimer: Job? = null
    
    // Preferences
    val geminiApiKey = preferencesService.geminiApiKey
    val userEmail = preferencesService.userEmail
    val hasApiKey = preferencesService.hasApiKey
    
    private val json = Json { prettyPrint = true }
    
    init {
        loadRecordings()
    }
    
    // MARK: - Language Selection
    
    fun setLanguage(language: RecordingLanguage) {
        _selectedLanguage.value = language
    }
    
    // MARK: - Recording
    
    fun startRecording(): Boolean {
        return try {
            val fileName = "recording_${System.currentTimeMillis()}.m4a"
            audioRecorder.startRecording(fileName)
            
            _currentRecording.value = Recording(
                id = UUID.randomUUID().toString(),
                title = generateDefaultTitle(),
                audioFileName = fileName,
                language = _selectedLanguage.value
            )
            
            // Start timer for UI updates
            recordingTimer = viewModelScope.launch {
                while (isActive) {
                    delay(100)
                    audioRecorder.updateMetrics()
                }
            }
            
            true
        } catch (e: Exception) {
            _errorMessage.value = "Failed to start recording: ${e.message}"
            false
        }
    }
    
    fun pauseRecording() {
        audioRecorder.pauseRecording()
    }
    
    fun resumeRecording() {
        audioRecorder.resumeRecording()
    }
    
    fun stopRecording(): Recording? {
        recordingTimer?.cancel()
        val duration = audioRecorder.stopRecording()
        
        return _currentRecording.value?.copy(duration = duration)?.also {
            _currentRecording.value = it
        }
    }
    
    fun cancelRecording() {
        recordingTimer?.cancel()
        audioRecorder.cancelRecording()
        _currentRecording.value = null
    }
    
    fun saveRecording(title: String? = null) {
        val recording = _currentRecording.value ?: return
        
        val finalRecording = if (!title.isNullOrBlank()) {
            recording.copy(title = title)
        } else {
            recording
        }
        
        _recordings.value = listOf(finalRecording) + _recordings.value
        saveRecordingsToFile()
        _currentRecording.value = null
        
        // Start processing
        viewModelScope.launch {
            processRecording(finalRecording)
        }
    }
    
    // MARK: - Processing
    
    suspend fun processRecording(recording: Recording) {
        _isProcessing.value = true
        _errorMessage.value = null
        
        var updatedRecording = recording
        
        try {
            // Update status
            _processingMessage.value = "Transcribing and summarizing..."
            updatedRecording = updatedRecording.copy(status = ProcessingStatus.TRANSCRIBING)
            updateRecording(updatedRecording)
            
            // Get API key
            val apiKey = preferencesService.geminiApiKey.first()
            android.util.Log.d("BigDicTaphone", "API key present: ${apiKey.isNotBlank()}, length: ${apiKey.length}")
            
            if (apiKey.isBlank()) {
                throw GeminiException("No API key configured. Please add your Gemini API key in Settings.")
            }
            
            // Check rate limits before making API call
            _rateLimitStatus.value = rateLimitService.getStatusString()
            when (val status = rateLimitService.canMakeRequest()) {
                is RateLimitStatus.DailyLimitReached -> {
                    throw GeminiException("Daily API limit reached (20/day). Try again tomorrow.")
                }
                is RateLimitStatus.MinuteLimitReached -> {
                    _processingMessage.value = "Rate limited, waiting ${status.resetInMs / 1000}s..."
                    kotlinx.coroutines.delay(status.resetInMs)
                }
                is RateLimitStatus.Available -> {
                    // Good to go
                }
            }
            
            // Use Gemini for both transcription and summarization
            val audioFile = audioRecorder.getRecordingFile(recording.audioFileName)
            android.util.Log.d("BigDicTaphone", "Audio file: ${audioFile.absolutePath}, exists: ${audioFile.exists()}, size: ${audioFile.length()} bytes")
            
            // Record the request before making the call
            rateLimitService.recordRequest()
            _rateLimitStatus.value = rateLimitService.getStatusString()
            
            val (transcription, summary) = geminiService.transcribeAndSummarize(
                audioFile = audioFile,
                language = recording.language,
                apiKey = apiKey
            )
            
            android.util.Log.d("BigDicTaphone", "Success! Transcription length: ${transcription.length}")
            
            updatedRecording = updatedRecording.copy(
                transcription = transcription,
                summary = summary,
                status = ProcessingStatus.COMPLETE
            )
            updateRecording(updatedRecording)
            
            // Auto-send email if enabled
            sendAutoEmail(updatedRecording)
            
        } catch (e: Exception) {
            val errorMsg = "${e::class.simpleName}: ${e.message}"
            android.util.Log.e("BigDicTaphone", "Processing failed: $errorMsg", e)
            
            updatedRecording = updatedRecording.copy(status = ProcessingStatus.FAILED)
            updateRecording(updatedRecording)
            _errorMessage.value = e.message ?: "Processing failed"
        }
        
        _isProcessing.value = false
        _processingMessage.value = ""
    }
    
    fun reprocessRecording(recording: Recording) {
        viewModelScope.launch {
            processRecording(recording)
        }
    }
    
    // MARK: - Recording Management
    
    fun updateRecording(recording: Recording) {
        _recordings.value = _recordings.value.map { 
            if (it.id == recording.id) recording else it 
        }
        saveRecordingsToFile()
    }
    
    fun deleteRecording(recording: Recording) {
        audioRecorder.deleteRecording(recording.audioFileName)
        _recordings.value = _recordings.value.filter { it.id != recording.id }
        saveRecordingsToFile()
    }
    
    fun getRecording(id: String): Recording? {
        return _recordings.value.find { it.id == id }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    // MARK: - Settings
    
    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            preferencesService.saveGeminiApiKey(apiKey)
        }
    }
    
    fun clearApiKey() {
        viewModelScope.launch {
            preferencesService.clearGeminiApiKey()
        }
    }
    
    fun saveUserEmail(email: String) {
        viewModelScope.launch {
            preferencesService.saveUserEmail(email)
        }
    }
    
    // MARK: - Auto-Send & Stakeholders
    
    val autoSendEnabled = preferencesService.autoSendEnabled
    val stakeholders = preferencesService.stakeholders
    val defaultLanguage = preferencesService.defaultLanguage
    
    fun setAutoSendEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesService.setAutoSendEnabled(enabled)
        }
    }
    
    fun saveDefaultLanguage(language: String) {
        viewModelScope.launch {
            preferencesService.saveDefaultLanguage(language)
        }
    }
    
    // MARK: - SMTP Settings
    val smtpHost = preferencesService.smtpHost
    val smtpPort = preferencesService.smtpPort
    val smtpUser = preferencesService.smtpUser
    val smtpPass = preferencesService.smtpPass
    
    fun saveSmtpSettings(host: String, port: String, user: String, pass: String) {
        viewModelScope.launch {
            preferencesService.saveSmtpSettings(host, port, user, pass)
        }
    }
    
    fun addStakeholder(stakeholder: Stakeholder) {
        viewModelScope.launch {
            preferencesService.addStakeholder(stakeholder)
        }
    }
    
    fun updateStakeholder(stakeholder: Stakeholder) {
        viewModelScope.launch {
            preferencesService.updateStakeholder(stakeholder)
        }
    }
    
    fun removeStakeholder(stakeholder: Stakeholder) {
        viewModelScope.launch {
            preferencesService.removeStakeholder(stakeholder)
        }
    }
    
    // MARK: - To-Do List (aggregated action items)
    
    fun getAllActionItems(): List<Pair<Recording, ActionItem>> {
        return _recordings.value
            .filter { it.summary != null }
            .flatMap { recording ->
                recording.summary!!.actionItems.map { action ->
                    Pair(recording, action.copy(recordingId = recording.id))
                }
            }
            .sortedByDescending { it.first.timestamp }
    }
    
    fun getIncompleteActionItems(): List<Pair<Recording, ActionItem>> {
        return getAllActionItems().filter { !it.second.completed }
    }
    
    fun toggleActionItemComplete(recordingId: String, actionItemId: String) {
        val recording = _recordings.value.find { it.id == recordingId } ?: return
        val summary = recording.summary ?: return
        
        val updatedActions = summary.actionItems.map { action ->
            if (action.id == actionItemId) {
                action.copy(completed = !action.completed)
            } else {
                action
            }
        }
        
        val updatedRecording = recording.copy(
            summary = summary.copy(actionItems = updatedActions)
        )
        updateRecording(updatedRecording)
    }
    
    // MARK: - Auto-Send Email
    
    // MARK: - Email Sending
    
    fun sendEmail(recipient: String, subject: String, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Try SMTP first
            val host = preferencesService.smtpHost.first()
            val port = preferencesService.smtpPort.first()
            val user = preferencesService.smtpUser.first()
            val pass = preferencesService.smtpPass.first()
            
            val smtpConfigured = host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
            
            if (smtpConfigured) {
                try {
                    val portInt = port.toIntOrNull() ?: 587
                    val result = smtpEmailService.sendEmail(host, portInt, user, pass, recipient, subject, body)
                    
                    if (result.isSuccess) {
                        android.util.Log.d("BigDicTaphone", "Sent email via SMTP to $recipient")
                        return@launch // Success
                    } else {
                        android.util.Log.e("BigDicTaphone", "SMTP failed", result.exceptionOrNull())
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BigDicTaphone", "SMTP error: ${e.message}")
                }
            }
            
            // Fallback (or default if no SMTP) to Intent
            // Main thread is required to start activity
            withContext(Dispatchers.Main) {
                try {
                    emailService.sendEmail(recipient, subject, body)
                    android.util.Log.d("BigDicTaphone", "Triggered email intent for $recipient")
                } catch (e: Exception) {
                    android.util.Log.e("BigDicTaphone", "Failed to trigger intent: ${e.message}")
                }
            }
        }
    }

    private suspend fun sendAutoEmail(recording: Recording) {
        val shouldAutoSend = preferencesService.autoSendEnabled.first()
        val userEmail = preferencesService.userEmail.first()
        
        if (shouldAutoSend && userEmail.isNotBlank() && recording.summary != null) {
            val subject = "Meeting Summary: ${recording.title}"
            val body = emailService.createPersonalSummaryContent(recording)
            sendEmail(userEmail, subject, body)
        }
    }

    // MARK: - Persistence
    
    private fun getRecordingsFile(): File {
        return File(context.filesDir, "recordings.json")
    }
    
    private fun loadRecordings() {
        val file = getRecordingsFile()
        if (file.exists()) {
            try {
                val content = file.readText()
                _recordings.value = json.decodeFromString<List<Recording>>(content)
            } catch (e: Exception) {
                e.printStackTrace()
                _recordings.value = emptyList()
            }
        }
    }
    
    private fun saveRecordingsToFile() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = json.encodeToString(_recordings.value)
                getRecordingsFile().writeText(content)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // MARK: - Helpers
    
    private fun generateDefaultTitle(): String {
        val dateFormat = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
        return "Recording ${dateFormat.format(java.util.Date())}"
    }
    
    /**
     * Get total storage used by recordings
     */
    fun getTotalStorageUsed(): String {
        val totalBytes = _recordings.value.sumOf { recording ->
            audioRecorder.getRecordingFile(recording.audioFileName).length()
        }
        
        return when {
            totalBytes < 1024 -> "$totalBytes B"
            totalBytes < 1024 * 1024 -> "${totalBytes / 1024} KB"
            else -> "${totalBytes / (1024 * 1024)} MB"
        }
    }
    
    fun deleteAllRecordings() {
        _recordings.value.forEach { recording ->
            audioRecorder.deleteRecording(recording.audioFileName)
        }
        _recordings.value = emptyList()
        saveRecordingsToFile()
    }
}
