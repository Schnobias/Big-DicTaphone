package com.bigdictaphone.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bigdictaphone.app.data.RecordingLanguage
import com.bigdictaphone.app.services.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class OfflineTranscriptionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun fixture(name: String): File = File(context.cacheDir, name).also { file ->
        instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
    }
    @Test fun stereoM4aDecodesToMono16k() = runBlocking {
        val pcm = File(context.cacheDir, "test.pcm")
        try {
            AudioDecoder().decode(fixture("jfk.m4a"), pcm)
            assertTrue(pcm.length() in (10 * 16000 * 4L)..(12 * 16000 * 4L))
            val floats = ByteBuffer.wrap(pcm.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            var peak = 0f
            while (floats.hasRemaining()) { val value = floats.get(); assertTrue(value.isFinite()); peak = maxOf(peak, kotlin.math.abs(value)) }
            assertTrue(peak > 0.01f)
        } finally { pcm.delete() }
    }
    @Test fun corruptAudioFailsWithoutTranscript() = runBlocking {
        val source = File(context.cacheDir, "invalid.m4a").apply { writeText("not audio") }
        val pcm = File(context.cacheDir, "invalid.pcm")
        try {
            try { AudioDecoder().decode(source, pcm); fail("Corrupt audio must fail") } catch (_: Exception) { }
        } finally { source.delete(); pcm.delete() }
    }
    @Test fun keystoreSecretsRoundTripAndUseRandomIv() {
        val store = SecretStore()
        val first = store.encrypt("test-only-secret")
        val second = store.encrypt("test-only-secret")
        assertNotEquals(first, second)
        assertFalse(first.contains("test-only-secret"))
        assertEquals("test-only-secret", store.decrypt(first))
        assertEquals("", store.decrypt(SecretStore.PREFIX + "damaged"))
    }
    @Test fun transcribesSavedSpeechOfflineWithNoApiKey() = runBlocking {
        val models = LocalModelStore(context)
        assumeTrue("Install the Tiny model before running inference tests", models.file(LocalModel.TINY).isFile)
        val model = models.verify(LocalModel.TINY)
        val text = withTimeout(180_000) {
            LocalTranscriptionService(context).transcribe(fixture("jfk.m4a"), RecordingLanguage.ENGLISH, model) { }
        }
        assertTrue(text, text.lowercase().contains("country"))
        assertTrue(text, text.lowercase().contains("ask"))
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("transcription-") })
    }
    @Test fun automaticLanguageDetectionUsesMultilingualModel() = runBlocking {
        val models = LocalModelStore(context)
        assumeTrue(models.file(LocalModel.TINY).isFile)
        val text = withTimeout(180_000) {
            LocalTranscriptionService(context).transcribe(fixture("jfk.m4a"), RecordingLanguage.AUTO, models.verify(LocalModel.TINY)) { }
        }
        assertTrue(text, text.lowercase().contains("country"))
    }
    @Test fun cancellationRetainsOriginalAndRemovesTemporaryPcm() = runBlocking {
        val models = LocalModelStore(context)
        assumeTrue(models.file(LocalModel.TINY).isFile)
        val source = fixture("jfk.m4a")
        val loading = CompletableDeferred<Unit>()
        val task = launch(Dispatchers.Default) {
            LocalTranscriptionService(context).transcribe(source, RecordingLanguage.ENGLISH, models.verify(LocalModel.TINY)) {
                if (it.startsWith("Transcribing on this phone")) loading.complete(Unit)
            }
        }
        withTimeout(60_000) { loading.await() }
        task.cancel()
        withTimeout(60_000) { task.join() }
        assertTrue(task.isCancelled)
        assertTrue(source.length() > 0)
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("transcription-") })
    }
    @Test fun silenceDoesNotBecomeAHallucinatedTranscript() = runBlocking {
        val models = LocalModelStore(context)
        assumeTrue(models.file(LocalModel.TINY).isFile)
        try {
            LocalTranscriptionService(context).transcribe(fixture("silence.m4a"), RecordingLanguage.ENGLISH, models.verify(LocalModel.TINY)) { }
            fail("Silence must not generate text")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("No audible speech"))
        }
    }
}
