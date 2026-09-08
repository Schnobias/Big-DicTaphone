package com.bigdictaphone.app

import com.bigdictaphone.app.services.LocalModel
import com.bigdictaphone.app.services.LocalModelStore
import com.bigdictaphone.app.services.ModelDescriptor
import com.bigdictaphone.app.services.ModelReadiness
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class LocalModelStoreTest {
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun store(directory: File, server: MockWebServer) = LocalModelStore(
        directory, OkHttpClient(), server.url("/").toString()
    )

    @Test fun missingModelIsObservableAndCannotBeUsed() {
        val directory = createTempDir()
        try {
            val store = LocalModelStore(directory, OkHttpClient())
            assertEquals(ModelReadiness.Missing, store.readiness.value[LocalModel.TINY])
            assertFalse(store.isInstalled(LocalModel.TINY))
        } finally { directory.deleteRecursively() }
    }

    @Test fun wrongSizePresentModelIsDamaged() = runBlocking {
        val directory = createTempDir()
        try {
            val store = LocalModelStore(directory, OkHttpClient())
            store.file(LocalModel.TINY).writeBytes(ByteArray(32))
            store.refresh(LocalModel.TINY)
            assertEquals(ModelReadiness.Damaged, store.readiness.value[LocalModel.TINY])
        } finally { directory.deleteRecursively() }
    }

    @Test fun interruptedResponseLeavesNoPartialTarget() = runBlocking {
        val directory = createTempDir(); val server = MockWebServer()
        try {
            val content = ByteArray(4096) { it.toByte() }
            val descriptor = ModelDescriptor("test.bin", content.size.toLong(), digest(content))
            server.enqueue(MockResponse().setBody(okio.Buffer().write(content.copyOf(1000))))
            server.start()
            val target = File(directory, descriptor.fileName)
            try { store(directory, server).downloadDescriptor(descriptor, target) { }; org.junit.Assert.fail("download should fail") }
            catch (_: Exception) { }
            assertFalse(target.exists())
            assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test fun cancellationLeavesNoPartialTarget() = runBlocking {
        val directory = createTempDir(); val server = MockWebServer()
        try {
            val content = ByteArray(4096) { (it * 3).toByte() }
            val descriptor = ModelDescriptor("test.bin", content.size.toLong(), digest(content))
            server.enqueue(MockResponse().setBody(okio.Buffer().write(content)).setBodyDelay(10, TimeUnit.SECONDS))
            server.start()
            val target = File(directory, descriptor.fileName)
            val task = launch(kotlinx.coroutines.Dispatchers.Default) { store(directory, server).downloadDescriptor(descriptor, target) { } }
            assertNotNull("request did not reach test server", server.takeRequest(2, TimeUnit.SECONDS))
            val started = System.nanoTime()
            task.cancelAndJoin()
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue("network cancellation took ${elapsedMs}ms", elapsedMs < 2_000)
            assertFalse(target.exists())
            assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test fun digestMismatchPreservesExistingVerifiedTarget() = runBlocking {
        val directory = createTempDir(); val server = MockWebServer()
        try {
            val old = ByteArray(4096) { 7 }; val replacement = ByteArray(4096) { 9 }
            val descriptor = ModelDescriptor("test.bin", old.size.toLong(), digest(old))
            server.enqueue(MockResponse().setBody(okio.Buffer().write(replacement)))
            server.start()
            val target = File(directory, descriptor.fileName).apply { writeBytes(old) }
            try { store(directory, server).downloadDescriptor(descriptor, target) { }; org.junit.Assert.fail("download should fail") }
            catch (_: IllegalStateException) { }
            assertArrayEquals(old, target.readBytes())
            assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test fun successfulReplacementInstallsVerifiedBytes() = runBlocking {
        val directory = createTempDir(); val server = MockWebServer()
        try {
            val old = ByteArray(4096) { 7 }; val replacement = ByteArray(4096) { 11 }
            val descriptor = ModelDescriptor("test.bin", replacement.size.toLong(), digest(replacement))
            server.enqueue(MockResponse().setBody(okio.Buffer().write(replacement)))
            server.start()
            val target = File(directory, descriptor.fileName).apply { writeBytes(old) }
            store(directory, server).downloadDescriptor(descriptor, target) { }
            assertArrayEquals(replacement, target.readBytes())
            assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally { server.shutdown(); directory.deleteRecursively() }
    }
}
