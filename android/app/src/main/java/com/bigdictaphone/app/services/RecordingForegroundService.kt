package com.bigdictaphone.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.bigdictaphone.app.MainActivity
import com.bigdictaphone.app.R
import com.bigdictaphone.app.BigDicTaphoneApplication
import com.bigdictaphone.app.data.CaptureStatus
import com.bigdictaphone.app.data.ProcessingStatus
import com.bigdictaphone.app.data.JobStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Foreground service to keep the microphone active during recording.
 * Prevents Android from killing the recording when the screen is locked.
 */
class RecordingForegroundService : Service() {
    
    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.bigdictaphone.app.START_RECORDING_SERVICE"
        const val ACTION_STOP = "com.bigdictaphone.app.STOP_RECORDING_SERVICE"
        const val ACTION_STOP_REVIEW = "com.bigdictaphone.app.STOP_RECORDING_FOR_REVIEW"
        const val ACTION_PAUSE = "com.bigdictaphone.app.PAUSE_RECORDING_SERVICE"
        const val ACTION_RESUME = "com.bigdictaphone.app.RESUME_RECORDING_SERVICE"
        const val ACTION_CANCEL = "com.bigdictaphone.app.CANCEL_RECORDING_SERVICE"
        const val EXTRA_FILE_NAME = "recording_file_name"
        const val EXTRA_RECORDING_ID = "recording_id"
        
        private var isRunning = false
        
        fun isServiceRunning(): Boolean = isRunning
        
        fun start(context: Context, recordingId: String) {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RECORDING_ID, recordingId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        /** Synchronous command boundary used by the UI: the service-owned recorder starts before acknowledgement. */
        fun startCapture(context: Context, recordingId: String) {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RECORDING_ID, recordingId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
        fun pause(context: Context) = command(context, ACTION_PAUSE)
        fun resume(context: Context) = command(context, ACTION_RESUME)
        fun stopAndSave(context: Context) = command(context, ACTION_STOP)
        fun stopForReview(context: Context) = command(context, ACTION_STOP_REVIEW)
        fun cancel(context: Context) = command(context, ACTION_CANCEL)
        private fun command(context: Context, action: String) {
            context.startService(Intent(context, RecordingForegroundService::class.java).setAction(action))
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingForegroundService::class.java))
        }
    }
    
    private var wakeLock: PowerManager.WakeLock? = null
    private val metricsHandler = Handler(Looper.getMainLooper())
    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commandMutex = Mutex()
    private var activeRecordingId: String? = null
    private var errorMessage: String? = null
    private val metricsTick = object : Runnable {
        override fun run() {
            if (isRunning) { recorder.updateMetrics(); metricsHandler.postDelayed(this, 100L) }
        }
    }
    private val app get() = application as BigDicTaphoneApplication
    private val recorder get() = app.audioRecorder
    private val repository get() = app.recordings
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        recorder.onInterruption = { metricsHandler.post { commandScope.launch { commandMutex.withLock { stopForegroundRecording(true, CaptureStatus.INTERRUPTED) } } } }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> commandScope.launch { commandMutex.withLock { runCatching { startForegroundRecording(intent.getStringExtra(EXTRA_RECORDING_ID)) }.onFailure { error ->
                android.util.Log.e("BigDicTaphone", "Could not start capture", error)
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            } } }
            ACTION_STOP -> commandScope.launch { commandMutex.withLock { stopForegroundRecording(false, CaptureStatus.SAVED) } }
            ACTION_STOP_REVIEW -> commandScope.launch { commandMutex.withLock { stopForegroundRecording(false, CaptureStatus.PENDING) } }
            ACTION_CANCEL -> commandScope.launch { commandMutex.withLock { cancelCapture() } }
            ACTION_PAUSE -> commandScope.launch { commandMutex.withLock { if (isRunning) runCatching { recorder.pauseRecording(); activeRecordingId?.let { repository.update(it) { r -> r.copy(captureStatus = CaptureStatus.PAUSED) } }; updateNotification(true) }.onFailure { updateNotification(false, "Pause failed; recording remains active") } } }
            ACTION_RESUME -> commandScope.launch { commandMutex.withLock { if (isRunning) runCatching { recorder.resumeRecording(); activeRecordingId?.let { repository.update(it) { r -> r.copy(captureStatus = CaptureStatus.ACTIVE) } }; updateNotification(false) }.onFailure { updateNotification(true, "Resume failed; recording is paused") } } }
        }
        return START_NOT_STICKY
    }
    
    private fun startForegroundRecording(recordingId: String?) {
        if (isRunning) return
        val id = requireNotNull(recordingId) { "Missing recording id." }
        val fileName = requireNotNull(repository.find(id)?.audioFileName) { "Recording metadata is missing." }
        // Promote to foreground before touching the microphone, so Android cannot kill the service during startup.
        updateNotification(false)
        activeRecordingId = id
        if (!recorder.isRecording.value) recorder.startRecording(fileName)
        commandScope.launch { commandMutex.withLock { repository.update(id) { it.copy(captureStatus = CaptureStatus.ACTIVE) } } }
        isRunning = true
        
        // Acquire wake lock to keep CPU active
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BigDicTaphone::RecordingWakeLock"
        ).apply {
            acquire(4 * 60 * 60 * 1000L) // 4 hours max
        }
        
        metricsHandler.removeCallbacks(metricsTick)
        metricsHandler.post(metricsTick)
        updateNotification(false)
    }

    private fun updateNotification(paused: Boolean, overrideText: String? = null) {
        // Create notification with tap action to return to app
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val toggleAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val toggleLabel = if (paused) "Resume" else "Pause"
        val pauseIntent = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(toggleAction), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 2, Intent(this, javaClass).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (paused) "Recording paused" else "Recording in progress")
            .setContentText(overrideText ?: if (paused) "Resume when ready" else "Tap to return to Big DicTaphone")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .addAction(R.drawable.ic_mic, toggleLabel, pauseIntent)
            .addAction(R.drawable.ic_mic, "Stop & Save", stopIntent)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        
        if (isRunning) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(NOTIFICATION_ID, notification)
    }
    
    private suspend fun stopForegroundRecording(interrupted: Boolean, finalStatus: CaptureStatus = CaptureStatus.INTERRUPTED) {
        if (!isRunning && !recorder.isRecording.value) return
        isRunning = false
        metricsHandler.removeCallbacks(metricsTick)

        val id = activeRecordingId
        var stopError: String? = null
        val duration = if (interrupted) {
            val savedDuration = recorder.recordingTime.value
            recorder.preserveAfterInterruption()?.let { savedDuration }
        } else if (recorder.isRecording.value) {
            try { recorder.stopRecording() } catch (e: Exception) {
                stopError = "Recording stop failed; original audio was preserved."
                val savedDuration = recorder.recordingTime.value
                recorder.preserveAfterInterruption()?.let { savedDuration }
            }
        } else null
        val effectiveStatus = if (interrupted || stopError != null) CaptureStatus.INTERRUPTED else finalStatus
        try {
            if (id != null) repository.update(id) { recording ->
                recording.copy(duration = duration ?: recording.duration, status = ProcessingStatus.RECORDED, captureStatus = effectiveStatus,
                    jobStatus = if (effectiveStatus == CaptureStatus.SAVED) JobStatus.QUEUED else recording.jobStatus,
                    processingError = when {
                        interrupted -> "Capture was interrupted; original audio was preserved."
                        stopError != null -> stopError
                        else -> recording.processingError
                    })
            } ?: error("Recording metadata is missing; audio was preserved.")
        } catch (_: Exception) {
            errorMessage = "Could not save capture state; audio was preserved for recovery."
            updateNotification(false, errorMessage)
            releaseServiceResources(stopSelf = true)
            return
        }
        activeRecordingId = null
        
        releaseServiceResources(stopSelf = true)
        if (effectiveStatus == CaptureStatus.SAVED) id?.let { ProcessingForegroundService.start(this, it) }
    }

    private fun releaseServiceResources(stopSelf: Boolean) {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (stopSelf) stopSelf()
    }

    private suspend fun cancelCapture() {
        metricsHandler.removeCallbacks(metricsTick)
        val id = activeRecordingId
        try {
            if (id != null) repository.remove(id)
        } catch (error: Exception) {
            errorMessage = "Could not cancel safely; audio was preserved for recovery."
            updateNotification(false, errorMessage)
            releaseServiceResources(stopSelf = true)
            return
        }
        recorder.cancelRecording()
        activeRecordingId = null
        isRunning = false
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    override fun onDestroy() {
        metricsHandler.removeCallbacks(metricsTick)
        commandScope.cancel()
        if (recorder.isRecording.value) {
            val id = activeRecordingId
            recorder.preserveAfterInterruption()
            if (id != null) CoroutineScope(Dispatchers.IO + SupervisorJob()).launch(NonCancellable) {
                repository.update(id) {
                    it.copy(status = ProcessingStatus.RECORDED, captureStatus = CaptureStatus.INTERRUPTED,
                        processingError = "Capture was interrupted; original audio was preserved.")
                }
            }
        }
        super.onDestroy()
        isRunning = false
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        recorder.onInterruption = null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Big DicTaphone is recording"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
