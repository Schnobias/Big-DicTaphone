package com.bigdictaphone.app

import com.bigdictaphone.app.data.RecordingLanguage
import com.bigdictaphone.app.services.GeminiService
import com.bigdictaphone.app.services.GeminiException
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.RandomAccessFile

class GeminiServiceTest {
    private fun response(finish: String = "STOP"): String = buildJsonObject {
        putJsonArray("candidates") { add(buildJsonObject {
            put("finishReason", finish)
            putJsonObject("content") { putJsonArray("parts") { add(buildJsonObject {
                put("text", """{"keyPoints":["Agreed"],"actionItems":[],"futurePoints":[]}""")
            }) } }
        }) }
    }.toString()
    @Test fun apiKeyIsAHeaderAndTranscriptIsJsonEscaped() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response()))
            val service = GeminiService(OkHttpClient(), server.url("/").toString().trimEnd('/'))
            assertEquals(listOf("Agreed"), service.generateSummary("quote \" \n \\ tab\t", RecordingLanguage.DUTCH, "test-key").keyPoints)
            val request = server.takeRequest()
            assertEquals("test-key", request.getHeader("x-goog-api-key"))
            assertFalse(request.path!!.contains("test-key"))
            val body = Json.parseToJsonElement(request.body.readUtf8())
            assertTrue(body.toString().contains("Dutch"))
        }
    }
    @Test fun truncatedResponsesAreRejected() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response("MAX_TOKENS")))
            val service = GeminiService(OkHttpClient(), server.url("/").toString().trimEnd('/'))
            try { service.generateSummary("test", RecordingLanguage.ENGLISH, "key"); fail("Expected failure") }
            catch (e: GeminiException) { assertTrue(e.message!!.contains("did not complete")) }
        }
    }
    @Test fun cancellationClosesPendingNetworkCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response()).setBodyDelay(5, TimeUnit.SECONDS))
            val service = GeminiService(OkHttpClient(), server.url("/").toString().trimEnd('/'))
            val task = async { service.generateSummary("test", RecordingLanguage.ENGLISH, "key") }
            delay(150)
            task.cancel()
            withTimeout(1500) { task.join() }
            assertTrue(task.isCancelled)
        }
    }

    @Test fun largeAudioUsesResumableUploadAndDeletesRemoteCopyOnFailure() = runBlocking {
        val audio = File.createTempFile("cloud-audio", ".m4a")
        try {
            RandomAccessFile(audio, "rw").use { it.setLength(10L * 1024 * 1024 + 1) }
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setHeader("X-Goog-Upload-URL", server.url("/session")))
                server.enqueue(MockResponse().setBody("""{"file":{"name":"files/test-file","uri":"https://example.invalid/audio"}}"""))
                server.enqueue(MockResponse().setBody("""{"state":"ACTIVE"}"""))
                server.enqueue(MockResponse().setResponseCode(429))
                server.enqueue(MockResponse().setResponseCode(200))
                val service = GeminiService(OkHttpClient(), server.url("/").toString().trimEnd('/'))
                try {
                    service.transcribeAndSummarize(audio, RecordingLanguage.AUTO, "test-key")
                    fail("Expected rate limit")
                } catch (e: GeminiException) { assertTrue(e.message!!.contains("rate limit")) }
                val start = server.takeRequest()
                assertEquals("start", start.getHeader("X-Goog-Upload-Command"))
                assertEquals(audio.length().toString(), start.getHeader("X-Goog-Upload-Header-Content-Length"))
                val upload = server.takeRequest()
                assertEquals("upload, finalize", upload.getHeader("X-Goog-Upload-Command"))
                assertEquals(audio.length(), upload.bodySize)
                assertEquals("/v1beta/files/test-file", server.takeRequest().path)
                assertTrue(server.takeRequest().body.readUtf8().contains("file_data"))
                val delete = server.takeRequest()
                assertEquals("DELETE", delete.method)
                assertEquals("/v1beta/files/test-file", delete.path)
                assertTrue(audio.isFile)
            }
        } finally { audio.delete() }
    }

    @Test fun uploadCannotRedirectAudioToAnotherOrigin() = runBlocking {
        val audio = File.createTempFile("cloud-audio", ".m4a")
        try {
            RandomAccessFile(audio, "rw").use { it.setLength(10L * 1024 * 1024 + 1) }
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setHeader("X-Goog-Upload-URL", "https://unexpected.invalid/session"))
                val service = GeminiService(OkHttpClient(), server.url("/").toString().trimEnd('/'))
                try {
                    service.transcribeAndSummarize(audio, RecordingLanguage.ENGLISH, "key")
                    fail("Expected origin rejection")
                } catch (e: IllegalArgumentException) { assertEquals("Unexpected upload server.", e.message) }
                assertEquals(1, server.requestCount)
                assertTrue(audio.isFile)
            }
        } finally { audio.delete() }
    }
}
