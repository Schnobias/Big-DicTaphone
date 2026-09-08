package com.bigdictaphone.app

import com.bigdictaphone.app.services.PcmResampler
import com.bigdictaphone.app.services.RecordingClock
import com.bigdictaphone.app.data.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AudioProcessingTest {
    @Test fun pauseTimeIsExcludedAndNewRecordingResetsClock() {
        var now = 1000L
        val clock = RecordingClock { now }
        clock.start(); now += 750; clock.pause(); now += 10000
        assertEquals(750L, clock.duration())
        clock.pause(); clock.resume(); now += 1250
        assertEquals(2000L, clock.duration())
        clock.start(); assertEquals(0L, clock.duration())
    }
    @Test fun sampleCountsPreserveDurationForSupportedRates() {
        listOf(8000, 16000, 22050, 44100, 48000, 96000).forEach { rate ->
            val output = mutableListOf<Float>()
            val resampler = PcmResampler(rate, output::add)
            repeat(rate) { resampler.accept(0.2f) }; resampler.finish()
            assertEquals("rate=$rate", 16000, output.size)
            assertEquals(0.2, output[8000].toDouble(), 0.0001)
        }
    }
    @Test fun silenceAndInvalidSamplesRemainFinite() {
        val output = mutableListOf<Float>()
        val resampler = PcmResampler(44100, output::add)
        repeat(44100) { resampler.accept(if (it == 2000) Float.NaN else 0f) }
        resampler.finish()
        assertTrue(output.all { it.isFinite() && it == 0f })
    }
    @Test fun downsamplingSuppressesOutOfBandNoise() {
        fun energy(frequency: Double): Double {
            val output = mutableListOf<Float>()
            val resampler = PcmResampler(48000, output::add)
            repeat(48000) { resampler.accept(sin(2 * PI * frequency * it / 48000).toFloat()) }
            resampler.finish()
            return output.drop(100).dropLast(100).map { it * it }.average()
        }
        assertTrue(energy(1000.0) > 0.4)
        assertTrue(energy(12000.0) < 0.01)
    }
    @Test fun legacyRecordingRemainsReadableWithoutNewFields() {
        val recording = Json.decodeFromString<Recording>("""{"id":"old","title":"Meeting","audioFileName":"recording_1.m4a","status":"COMPLETE","transcription":"Hello"}""")
        assertNull(recording.transcriptionMode)
        assertNull(recording.processingError)
        assertEquals("Hello", recording.transcription)
    }
}
