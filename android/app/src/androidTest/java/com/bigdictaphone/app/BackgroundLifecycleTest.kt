package com.bigdictaphone.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bigdictaphone.app.data.*
import com.bigdictaphone.app.services.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Device coverage for capture and offline processing while the UI is backgrounded. */
@RunWith(AndroidJUnit4::class)
class BackgroundLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val app get() = context.applicationContext as BigDicTaphoneApplication
    private val ownedIds = mutableListOf<String>()
    private val ownedFiles = mutableListOf<String>()

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    @Before fun grantRuntimePermissions() {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    }

    @After fun cleanup() = runBlocking {
        runCatching { RecordingForegroundService.stopForReview(context) }
        runCatching { ProcessingForegroundService.finish(context) }
        shell("input keyevent KEYCODE_WAKEUP")
        ownedIds.forEach { runCatching { ProcessingForegroundService.cancel(context, it) }; app.recordings.remove(it) }
        ownedFiles.forEach { app.audioRecorder.deleteRecording(it) }
        instrumentation.uiAutomation.dropShellPermissionIdentity()
    }

    @Test fun captureSurvivesPauseResumeHomeLockWakeAndActivityRecreation() = runBlocking {
        val id = UUID.randomUUID().toString()
        val secondId = UUID.randomUUID().toString()
        val fileName = "lifecycle-$id.m4a"
        val secondFile = "lifecycle-$secondId.m4a"
        ownedIds += listOf(id, secondId); ownedFiles += listOf(fileName, secondFile)
        app.recordings.insert(Recording(id = id, title = "Lifecycle", audioFileName = fileName, captureStatus = CaptureStatus.ACTIVE))
        app.recordings.insert(Recording(id = secondId, title = "Second", audioFileName = secondFile, captureStatus = CaptureStatus.ACTIVE))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            RecordingForegroundService.startCapture(context, id)
            withTimeout(15_000) { app.audioRecorder.isRecording.first { it } }
            RecordingForegroundService.pause(context)
            withTimeout(5_000) { app.audioRecorder.isPaused.first { it } }
            RecordingForegroundService.resume(context)
            withTimeout(5_000) { app.audioRecorder.isPaused.first { !it } }
            shell("input keyevent KEYCODE_HOME")
            shell("input keyevent KEYCODE_SLEEP")
            Thread.sleep(2_000)
            shell("input keyevent KEYCODE_WAKEUP")
            scenario.recreate()
            RecordingForegroundService.stopForReview(context)
            withTimeout(15_000) { app.audioRecorder.isRecording.first { !it } }
            val pending = withTimeout(15_000) { app.recordings.recordings.first { it.firstOrNull { r -> r.id == id }?.captureStatus == CaptureStatus.PENDING } }.single { it.id == id }
            assertTrue(pending.duration > 0)
            assertTrue(app.audioRecorder.getRecordingFile(fileName).length() > 0)
            RecordingForegroundService.startCapture(context, secondId)
            withTimeout(15_000) { app.audioRecorder.isRecording.first { it } }
            RecordingForegroundService.stopForReview(context)
            withTimeout(15_000) { app.audioRecorder.isRecording.first { !it } }
            assertNotNull(app.recordings.find(id))
            assertNotNull(app.recordings.find(secondId))
        } finally { scenario.close() }
    }

    @Test(timeout = 180_000)
    fun localProcessingCompletesWhileScreenRemainsLockedAndCancellationIsDurable() = runBlocking {
        val models = LocalModelStore(context)
        assumeTrue("Tiny model is not installed", models.file(LocalModel.TINY).isFile)
        val preferences = PreferencesService(context)
        val originalModel = preferences.localModel.first()
        val source = File(context.cacheDir, "jfk-lifecycle.m4a")
        instrumentation.context.assets.open("jfk.m4a").use { input -> source.outputStream().use { input.copyTo(it) } }
        val id = UUID.randomUUID().toString()
        val cancelId = UUID.randomUUID().toString()
        val fileName = "processing-$id.m4a"
        val cancelFile = "processing-$cancelId.m4a"
        ownedIds += listOf(id, cancelId); ownedFiles += listOf(fileName, cancelFile)
        try {
            preferences.setLocalModel(LocalModel.TINY)
            val target = app.audioRecorder.getRecordingFile(fileName)
            source.copyTo(target, overwrite = true)
            app.recordings.insert(Recording(id = id, title = "Offline", audioFileName = fileName, transcriptionMode = TranscriptionMode.LOCAL))
            app.recordings.enqueue(id)
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            try {
                ProcessingForegroundService.start(context, id)
                shell("input keyevent KEYCODE_HOME")
                shell("input keyevent KEYCODE_SLEEP")
                val result = withTimeout(150_000) { app.recordings.recordings.first { it.firstOrNull { r -> r.id == id }?.jobStatus == JobStatus.COMPLETE } }.single { it.id == id }
                shell("input keyevent KEYCODE_WAKEUP")
                assertTrue(result.transcription.orEmpty().lowercase().contains("country"))
                assertTrue(target.isFile && target.length() > 0)

                val cancelTarget = app.audioRecorder.getRecordingFile(cancelFile)
                source.copyTo(cancelTarget, overwrite = true)
                app.recordings.insert(Recording(id = cancelId, title = "Cancel", audioFileName = cancelFile, transcriptionMode = TranscriptionMode.LOCAL))
                app.recordings.enqueue(cancelId)
                ProcessingForegroundService.start(context, cancelId)
                withTimeout(15_000) { app.recordings.recordings.first { it.firstOrNull { r -> r.id == cancelId }?.jobStatus == JobStatus.RUNNING } }
                val notification = context.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.id == ProcessingForegroundService.NOTIFICATION_ID }
                val cancelAction = notification?.notification?.actions?.firstOrNull { it.title?.toString()?.contains("Cancel", true) == true }
                assumeNotNull(cancelAction)
                cancelAction!!.actionIntent.send()
                val cancelled = withTimeout(30_000) { app.recordings.recordings.first { it.firstOrNull { r -> r.id == cancelId }?.jobStatus == JobStatus.CANCELLED } }.single { it.id == cancelId }
                assertEquals(JobStatus.CANCELLED, cancelled.jobStatus)
                assertTrue(cancelTarget.isFile && cancelTarget.length() > 0)
            } finally { scenario.close(); shell("input keyevent KEYCODE_WAKEUP") }
        } finally {
            preferences.setLocalModel(originalModel)
            source.delete()
        }
    }
}
