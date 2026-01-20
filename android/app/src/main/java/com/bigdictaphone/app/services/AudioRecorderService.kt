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
    private var startTime: Long = 0
    
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
    fun startRecording(fileName: String): File {
        val file = File(getRecordingsDir(), fileName)
        recordingFile = file
        
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(2) // Enable stereo
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
            
            try {
                prepare()
                start()
                _isRecording.value = true
                _isPaused.value = false
                startTime = System.currentTimeMillis()
            } catch (e: IOException) {
                e.printStackTrace()
                throw e
            }
        }
        
        return file
    }
    
    /**
     * Pause the current recording
     */
    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.pause()
            _isPaused.value = true
        }
    }
    
    /**
     * Resume a paused recording
     */
    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.resume()
            _isPaused.value = false
        }
    }
    
    /**
     * Stop recording and return the duration
     */
    fun stopRecording(): Long {
        val duration = _recordingTime.value
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        mediaRecorder = null
        _isRecording.value = false
        _isPaused.value = false
        _audioLevel.value = 0f
        _audioLevelLeft.value = 0f
        _audioLevelRight.value = 0f
        
        return duration
    }
    
    /**
     * Cancel recording and delete the file
     */
    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        recordingFile?.delete()
        recordingFile = null
        mediaRecorder = null
        _isRecording.value = false
        _isPaused.value = false
        _recordingTime.value = 0
        _audioLevel.value = 0f
        _audioLevelLeft.value = 0f
        _audioLevelRight.value = 0f
    }
    
    /**
     * Update the recording time and audio level (call this from a timer)
     */
    fun updateMetrics() {
        if (_isRecording.value && !_isPaused.value) {
            _recordingTime.value = System.currentTimeMillis() - startTime
            
            // Get audio amplitude (0-32767, normalize to 0-1)
            val maxAmplitude = try {
                mediaRecorder?.maxAmplitude ?: 0
            } catch (e: Exception) {
                0
            }
            val baseLevel = (maxAmplitude.toFloat() / 32767f).coerceIn(0f, 1f)
            _audioLevel.value = baseLevel
            
            // Simulate stereo with natural variation (microphones pick up slightly different levels)
            val variation = kotlin.random.Random.nextFloat() * 0.2f - 0.1f // -10% to +10%
            _audioLevelLeft.value = (baseLevel * (1f + variation)).coerceIn(0f, 1f)
            _audioLevelRight.value = (baseLevel * (1f - variation)).coerceIn(0f, 1f)
        }
    }
    
    /**
     * Get the file path for a recording
     */
    fun getRecordingFile(fileName: String): File {
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
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}
