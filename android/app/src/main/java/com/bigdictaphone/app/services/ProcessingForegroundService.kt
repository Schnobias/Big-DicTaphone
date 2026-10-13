package com.bigdictaphone.app.services

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.bigdictaphone.app.*
import com.bigdictaphone.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** One foreground worker consumes the durable queue independently of any activity. */
class ProcessingForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "processing_channel"
        const val NOTIFICATION_ID = 1002
        const val ACTION_START = "com.bigdictaphone.app.START_PROCESSING"
        const val ACTION_CANCEL = "com.bigdictaphone.app.CANCEL_PROCESSING"
        const val EXTRA_RECORDING_ID = "recording_id"
        private val _activeId = MutableStateFlow<String?>(null)
        val activeId = _activeId.asStateFlow()
        private val _message = MutableStateFlow("")
        val message = _message.asStateFlow()
        fun start(context: Context, recordingId: String) {
            context.startForegroundService(Intent(context, ProcessingForegroundService::class.java)
                .setAction(ACTION_START).putExtra(EXTRA_RECORDING_ID, recordingId))
        }
        fun cancel(context: Context, recordingId: String) {
            context.startService(Intent(context, ProcessingForegroundService::class.java)
                .setAction(ACTION_CANCEL).putExtra(EXTRA_RECORDING_ID, recordingId))
        }
        fun finish(context: Context) { context.stopService(Intent(context, ProcessingForegroundService::class.java)) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository get() = (application as BigDicTaphoneApplication).recordings
    private var worker: Job? = null
    private var activeJob: Job? = null
    private var pendingCommands = 0
    private var lastStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var timingOut = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Transcription", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val id = intent?.getStringExtra(EXTRA_RECORDING_ID)
        if (intent?.action == ACTION_CANCEL) {
            if (id == _activeId.value) activeJob?.cancel()
            else if (id != null) command {
                repository.update(id) { if (it.jobStatus == JobStatus.QUEUED) it.copy(jobStatus = JobStatus.CANCELLED,
                    processingError = "Processing cancelled. Original audio retained.") else it }
            }
            return START_NOT_STICKY
        }
        try {
            val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification("Preparing transcription…", id), type)
            else startForeground(NOTIFICATION_ID, notification("Preparing transcription…", id))
            if (wakeLock == null) wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BigDicTaphone:Transcription").apply { acquire(6 * 60 * 60 * 1000L) }
        } catch (error: Exception) {
            _message.value = "Background processing could not start: ${error.message}"
            stopSelf(); return START_NOT_STICKY
        }
        command {
            if (id != null && id != _activeId.value) repository.update(id) {
                if (it.jobStatus == JobStatus.QUEUED || it.jobStatus == JobStatus.RUNNING) it
                else it.copy(jobStatus = JobStatus.QUEUED, transcriptionMode = it.transcriptionMode ?: TranscriptionMode.LOCAL)
            }
        }
        return START_NOT_STICKY
    }
    private fun command(action: suspend () -> Unit) {
        pendingCommands++
        scope.launch {
            try { action() }
            catch (error: Exception) { _message.value = "Could not save processing state: ${error.message}" }
            finally { pendingCommands--; pump() }
        }
    }
    private fun pump() {
        if (timingOut || worker?.isActive == true) return
        worker = scope.launch {
            try {
                while (isActive) {
                    val next = repository.current().firstOrNull { it.jobStatus == JobStatus.QUEUED } ?: break
                    _activeId.value = next.id
                    _message.value = "Preparing ${next.title}…"
                    activeJob = launch(Dispatchers.IO) {
                        try {
                            ProcessingCoordinator(this@ProcessingForegroundService, repository).process(next.id) { progress ->
                                _message.value = progress
                                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(progress, next.id))
                            }
                        } catch (_: CancellationException) { }
                        catch (error: Exception) { _message.value = "Could not save processing result: ${error.message}" }
                    }
                    activeJob?.join()
                    activeJob = null
                    _activeId.value = null
                }
            } finally {
                worker = null
                if (pendingCommands == 0) {
                    _activeId.value = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(lastStartId)
                }
            }
        }
    }
    private fun notification(text: String, id: String?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Transcribing on Big DicTaphone").setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open)
            .setProgress(0, 0, true).setOngoing(true).setOnlyAlertOnce(true)
        if (id != null) builder.addAction(R.drawable.ic_mic, "Cancel", PendingIntent.getService(this, 4,
            Intent(this, javaClass).setAction(ACTION_CANCEL).putExtra(EXTRA_RECORDING_ID, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        return builder.build()
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        timingOut = true
        activeJob?.cancel(ProcessingServiceTimeout())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        scope.cancel()
        _activeId.value = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        super.onDestroy()
    }
}
