package com.bigdictaphone.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bigdictaphone.app.data.*
import com.bigdictaphone.app.services.RecordingRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecordingRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repository: RecordingRepository
    private lateinit var root: File
    private val recording = Recording(title = "durable", audioFileName = "recording-test.m4a")

    @Before fun setUp() { root = File(context.cacheDir, "repository-test-${System.nanoTime()}").also { it.mkdirs() }; repository = RecordingRepository(context, root) }
    @After fun tearDown() { root.deleteRecursively() }

    @Test fun saveIsReadableByASeparateRepositoryInstance() {
        repository.saveBlocking(listOf(recording))
        val reloaded = RecordingRepository(context, root).load()
        assertEquals(recording.id, reloaded.single().id)
        assertEquals(TranscriptionMode.LOCAL, reloaded.single().transcriptionMode ?: TranscriptionMode.LOCAL)
    }

    @Test fun runningJobRecoversAsRetryableAndKeepsAudioMetadata() {
        val running = recording.copy(status = ProcessingStatus.TRANSCRIBING, jobStatus = JobStatus.RUNNING)
        repository.saveBlocking(listOf(running))
        val recovered = runBlocking { repository.initialize(); repository.snapshot() }.single()
        assertEquals(ProcessingStatus.FAILED, recovered.status)
        assertEquals(JobStatus.RETRYABLE, recovered.jobStatus)
        assertEquals("recording-test.m4a", recovered.audioFileName)
    }

    @Test fun concurrentUpdatesDoNotOverwriteEachOtherAndEnqueueIsDeduplicated() = runBlocking {
        repository.saveBlocking(listOf(recording, recording.copy(id = "second", title = "second")))
        listOf(recording.id, "second").map { id -> async { repository.update(id) { it.copy(title = it.title + " updated") } } }.awaitAll()
        assertTrue(repository.enqueue(recording.id))
        assertTrue(repository.enqueue(recording.id))
        assertEquals(JobStatus.QUEUED, repository.find(recording.id)!!.jobStatus)
        assertEquals(2, repository.snapshot().size)
    }
}
