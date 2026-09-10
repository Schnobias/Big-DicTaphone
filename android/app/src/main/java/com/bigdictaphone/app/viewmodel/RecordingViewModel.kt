package com.bigdictaphone.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bigdictaphone.app.BigDicTaphoneApplication
import com.bigdictaphone.app.data.*
import com.bigdictaphone.app.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/** UI adapter. Services own capture/processing; the repository owns durable state. */
class RecordingViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val app = application as BigDicTaphoneApplication
    private val repository = app.recordings
    val audioRecorder = app.audioRecorder
    private val preferencesService = PreferencesService(context)
    val emailService = EmailService(context)
    val modelStore = LocalModelStore(context)
    val recordings = repository.recordings
    val activeProcessingId = ProcessingForegroundService.activeId
    val isProcessing = activeProcessingId.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val processingMessage = ProcessingForegroundService.message
    val queuedCount = recordings.map { list -> list.count { it.jobStatus == JobStatus.QUEUED } }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val rateLimitStatus = MutableStateFlow("").asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val captureBusy = _busy.asStateFlow()
    private val _selectedLanguage = MutableStateFlow(RecordingLanguage.AUTO)
    val selectedLanguage = _selectedLanguage.asStateFlow()
    val currentRecording = recordings.map { list -> list.firstOrNull { it.captureStatus == CaptureStatus.PENDING } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _mode = MutableStateFlow(TranscriptionMode.LOCAL)
    val transcriptionMode = _mode.asStateFlow()
    private var pendingMode: TranscriptionMode? = null
    val localModel = preferencesService.localModel
    private val _modelStatus = MutableStateFlow("")
    val modelStatus = _modelStatus.asStateFlow()
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()
    private var downloadJob: Job? = null
    private var settingsLoaded = false

    val geminiApiKey = preferencesService.geminiApiKey
    val userEmail = preferencesService.userEmail
    val hasApiKey = preferencesService.hasApiKey
    val autoSendEnabled = preferencesService.autoSendEnabled
    val stakeholders = preferencesService.stakeholders
    val defaultLanguage = preferencesService.defaultLanguage
    val smtpHost = preferencesService.smtpHost
    val smtpPort = preferencesService.smtpPort
    val smtpUser = preferencesService.smtpUser
    val smtpPass = preferencesService.smtpPass

    init {
        safeLaunch { repository.storageError.collect { if (it != null) _errorMessage.value = it } }
        safeLaunch {
            preferencesService.migrateSecrets()
            preferencesService.transcriptionMode.collect { mode ->
                if (pendingMode == null || mode == pendingMode) { _mode.value = mode; pendingMode = null }
                settingsLoaded = true
            }
        }
        safeLaunch { preferencesService.defaultLanguage.collect {
            if (!audioRecorder.isRecording.value) _selectedLanguage.value = runCatching { RecordingLanguage.valueOf(it) }.getOrDefault(RecordingLanguage.AUTO)
        } }
    }
    private fun safeLaunch(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _errorMessage.value = e.message ?: "The operation could not be saved. Please retry." }
    }
    private fun captureCommand(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        safeLaunch { try { block() } finally { _busy.value = false } }
    }
    fun setLanguage(language: RecordingLanguage) { _selectedLanguage.value = language }
    fun setTranscriptionMode(mode: TranscriptionMode) {
        pendingMode = mode
        _mode.value = mode
        safeLaunch { preferencesService.setTranscriptionMode(mode) }
    }
    fun setLocalModel(model: LocalModel) { safeLaunch { preferencesService.setLocalModel(model) } }
    fun downloadModel(model: LocalModel) {
        if (_isDownloading.value) return
        _isDownloading.value = true
        downloadJob = viewModelScope.launch {
            try {
                modelStore.download(model) { _modelStatus.value = "Downloading ${model.name.lowercase()}: $it%" }
                _modelStatus.value = "Model verified and ready for offline transcription."
            } catch (e: CancellationException) { _modelStatus.value = "Download cancelled."; throw e }
            catch (e: Exception) { _modelStatus.value = e.message ?: "Download failed. Please retry." }
            finally { _isDownloading.value = false }
        }
    }
    fun cancelModelDownload() { downloadJob?.cancel() }
    fun startRecording() {
        if (audioRecorder.isRecording.value || currentRecording.value != null) return
        captureCommand {
            if (!settingsLoaded && pendingMode == null) _mode.value = preferencesService.transcriptionMode.first()
            val recording = Recording(title = "Recording " + java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                audioFileName = "recording_${UUID.randomUUID()}.m4a", language = selectedLanguage.value,
                transcriptionMode = _mode.value, captureStatus = CaptureStatus.ACTIVE)
            repository.insert(recording)
            try {
                RecordingForegroundService.startCapture(context, recording.id)
                withTimeout(10_000) { audioRecorder.isRecording.first { it } }
            } catch (e: Exception) {
                repository.update(recording.id) { it.copy(captureStatus = CaptureStatus.INTERRUPTED, processingError = "Recording could not start: ${e.message}") }
                throw e
            }
        }
    }
    fun pauseRecording() { RecordingForegroundService.pause(context) }
    fun resumeRecording() { RecordingForegroundService.resume(context) }
    fun stopRecording() { RecordingForegroundService.stopForReview(context) }
    fun cancelRecording() {
        val pending = currentRecording.value
        if (pending != null) captureCommand {
            repository.remove(pending.id)
            audioRecorder.deleteRecording(pending.audioFileName)
        } else RecordingForegroundService.cancel(context)
    }
    fun saveRecording(title: String? = null) {
        val pending = currentRecording.value ?: return
        captureCommand {
            repository.update(pending.id) { it.copy(title = title?.takeIf(String::isNotBlank) ?: it.title, captureStatus = CaptureStatus.SAVED,
                jobStatus = JobStatus.QUEUED, transcriptionMode = it.transcriptionMode ?: TranscriptionMode.LOCAL) }
            // Metadata and queue entry are durable before the dialog disappears.
            ProcessingForegroundService.start(context, pending.id)
        }
    }
    fun reprocessRecording(recording: Recording) {
        safeLaunch {
            if (recording.captureStatus == CaptureStatus.ACTIVE || recording.captureStatus == CaptureStatus.PAUSED) return@safeLaunch
            if (activeProcessingId.value == recording.id) return@safeLaunch
            repository.enqueue(recording.id)
            ProcessingForegroundService.start(context, recording.id)
        }
    }
    fun cancelProcessing() { activeProcessingId.value?.let { ProcessingForegroundService.cancel(context, it) } }
    fun cancelProcessing(id: String) { ProcessingForegroundService.cancel(context, id) }
    fun getRecording(id: String): Recording? = repository.find(id)
    fun deleteRecording(recording: Recording) {
        safeLaunch {
            require(recording.captureStatus != CaptureStatus.ACTIVE && recording.captureStatus != CaptureStatus.PAUSED) { "Stop the recording before deleting it." }
            require(activeProcessingId.value != recording.id) { "Cancel processing before deleting this recording." }
            repository.remove(recording.id)
            audioRecorder.deleteRecording(recording.audioFileName)
        }
    }
    fun clearError() { _errorMessage.value = null }
    fun saveApiKey(value: String) { safeLaunch { preferencesService.saveGeminiApiKey(value) } }
    fun clearApiKey() { safeLaunch { preferencesService.clearGeminiApiKey() } }
    fun saveUserEmail(value: String) { safeLaunch { preferencesService.saveUserEmail(value) } }
    fun setAutoSendEnabled(value: Boolean) { safeLaunch { preferencesService.setAutoSendEnabled(value) } }
    fun saveDefaultLanguage(value: String) { safeLaunch { preferencesService.saveDefaultLanguage(value) } }
    fun saveSmtpSettings(host: String, port: String, user: String, pass: String) { safeLaunch { preferencesService.saveSmtpSettings(host, port, user, pass) } }
    fun addStakeholder(value: Stakeholder) { safeLaunch { preferencesService.addStakeholder(value) } }
    fun updateStakeholder(value: Stakeholder) { safeLaunch { preferencesService.updateStakeholder(value) } }
    fun removeStakeholder(value: Stakeholder) { safeLaunch { preferencesService.removeStakeholder(value) } }
    fun sendEmail(recipient: String, subject: String, body: String) {
        try { emailService.sendEmail(recipient, subject, body); _errorMessage.value = "Email draft opened in your email app." }
        catch (_: Exception) { _errorMessage.value = "No email app could open the draft." }
    }
    fun getAllActionItems(): List<Pair<Recording, ActionItem>> = recordings.value.flatMap { recording ->
        recording.summary?.actionItems.orEmpty().map { recording to it.copy(recordingId = recording.id) }
    }.sortedByDescending { it.first.timestamp }
    fun getIncompleteActionItems() = getAllActionItems().filter { !it.second.completed }
    fun toggleActionItemComplete(recordingId: String, actionItemId: String) { safeLaunch {
        repository.update(recordingId) { recording -> recording.copy(summary = recording.summary?.let { summary ->
            summary.copy(actionItems = summary.actionItems.map { if (it.id == actionItemId) it.copy(completed = !it.completed) else it })
        }) }
    } }
    fun getTotalStorageUsed(): String {
        val bytes = recordings.value.sumOf { runCatching { audioRecorder.getRecordingFile(it.audioFileName).length() }.getOrDefault(0L) }
        return when { bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "${bytes / 1024} KB"; else -> "${bytes / (1024 * 1024)} MB" }
    }
    fun deleteAllRecordings() { safeLaunch {
        require(!audioRecorder.isRecording.value && activeProcessingId.value == null) { "Stop recording and processing before deleting the library." }
        recordings.value.toList().forEach { repository.remove(it.id); audioRecorder.deleteRecording(it.audioFileName) }
    } }
}
