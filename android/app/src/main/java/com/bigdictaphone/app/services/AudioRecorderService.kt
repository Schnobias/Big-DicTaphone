package com.bigdictaphone.app.services

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/**
 * Service for recording audio using Android's MediaRecorder
 */
class AudioRecorderService(private val context: Context) {
    
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private val clock = RecordingClock { android.os.SystemClock.elapsedRealtime() }
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    
    private val _recordingTime = MutableStateFlow(0L)
    val recordingTime: StateFlow<Long> = _recordingTime.asStateFlow()
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    
    // Stereo audio levels (simulated from mono with natural variation)
    private val _audioLevelLeft = MutableStateFlow(0f)
    val audioLevelLeft: StateFlow<Float> = _audioLevelLeft.asStateFlow()
    
    private val _audioLevelRight = MutableStateFlow(0f)
    val audioLevelRight: StateFlow<Float> = _audioLevelRight.asStateFlow()
    val activeFileName: String? get() = recordingFile?.name
    private val _interrupted = MutableStateFlow(false)
    val interrupted: StateFlow<Boolean> = _interrupted.asStateFlow()
    @Volatile var onInterruption: (() -> Unit)? = null
    
    /**
     * Get the recordings directory
     */
    private fun getRecordingsDir(): File {
        val dir = File(context.filesDir, "recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Start recording to the specified filename
     */
    @Synchronized
    fun startRecording(fileName: String): File {
        check(mediaRecorder == null) { "A recording is already active." }
        val file = getRecordingFile(fileName)
        recordingFile = file
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder = recorder
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioEncodingBitRate(96000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(file.absolutePath)
            recorder.setOnErrorListener { _, _, _ ->
                _interrupted.value = true
                onInterruption?.invoke()
            }
            recorder.prepare()
            recorder.start()
            clock.start()
            _recordingTime.value = 0
            _interrupted.value = false
            _isRecording.value = true
            _isPaused.value = false
            return file
        } catch (e: Exception) {
            recorder.release()
            mediaRecorder = null
            recordingFile = null
            throw e
        }
    }

    /**
     * Pause the current recording
     */
    @Synchronized
    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!_isRecording.value || _isPaused.value) return
            mediaRecorder?.pause()
            clock.pause()
            _recordingTime.value = clock.duration()
            _isPaused.value = true
        }
    }
    
    /**
     * Resume a paused recording
     */
    @Synchronized
    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!_isRecording.value || !_isPaused.value) return
            mediaRecorder?.resume()
            clock.resume()
            _isPaused.value = false
        }
    }
    
    /**
     * Stop recording and return the duration
     */
    @Synchronized
    fun stopRecording(): Long {
        val recorder = mediaRecorder ?: error("No active recording.")
        val duration = clock.duration()
        try {
            recorder.stop()
            check(recordingFile?.length()?.let { it > 0 } == true) { "The recording is empty." }
            _recordingTime.value = duration
            recordingFile = null
            return duration
        } catch (e: Exception) {
            throw IOException("Recording was too short or could not be saved. Please record again.", e)
        } finally {
            try { recorder.release() } finally { mediaRecorder = null; resetLevels() }
        }
    }

    @Synchronized
    fun cancelRecording() {
        try { mediaRecorder?.stop() } catch (_: Exception) { }
        finally { mediaRecorder?.release(); mediaRecorder = null; resetLevels() }
        recordingFile?.delete()
        recordingFile = null
        _recordingTime.value = 0
        _interrupted.value = false
    }

    /** Finalize a recorder after an owner/service interruption without deleting evidence. */
    @Synchronized
    fun preserveAfterInterruption(): File? {
        val file = recordingFile ?: return null
        try { mediaRecorder?.stop() } catch (_: Exception) { }
        try { mediaRecorder?.release() } catch (_: Exception) { }
        mediaRecorder = null
        resetLevels()
        recordingFile = null
        _interrupted.value = false
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    private fun resetLevels() {
        _isRecording.value = false
        _isPaused.value = false
        _audioLevel.value = 0f
        _audioLevelLeft.value = 0f
        _audioLevelRight.value = 0f
    }

    /**
     * Update the recording time and audio level (call this from a timer)
     */
    fun updateMetrics() {
        if (_isRecording.value && !_isPaused.value) {
            _recordingTime.value = clock.duration()
            
            // Get audio amplitude (0-32767, normalize to 0-1)
            val maxAmplitude = try {
                mediaRecorder?.maxAmplitude ?: 0
            } catch (e: Exception) {
                0
            }
            val baseLevel = (maxAmplitude.toFloat() / 32767f).coerceIn(0f, 1f)
            _audioLevel.value = baseLevel
            
            // MediaRecorder exposes one peak meter, not independent channel measurements.
            _audioLevelLeft.value = baseLevel
            _audioLevelRight.value = baseLevel
        }
    }
    
    /**
     * Get the file path for a recording
     */
    fun getRecordingFile(fileName: String): File {
        require(fileName == File(fileName).name && !fileName.contains('\\') && fileName.endsWith(".m4a")) { "Invalid recording filename." }
        return File(getRecordingsDir(), fileName)
    }
    
    /**
     * Delete a recording file
     */
    fun deleteRecording(fileName: String): Boolean {
        return getRecordingFile(fileName).delete()
    }
    
    /**
     * Format time in milliseconds to MM:SS or HH:MM:SS
     */
    companion object {
        fun formatTime(timeMs: Long): String {
            val totalSeconds = timeMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            return if (hours > 0) {
                String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        }
    }
}
