package com.bigdictaphone.app.services

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decode saved AAC/M4A (including stereo recordings) to 16 kHz mono float PCM. */
class AudioDecoder {
    suspend fun decode(source: File, destination: File): Double {
        require(source.isFile && source.length() > 0) { "The saved audio file is missing or empty." }
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var sumSquares = 0.0
        var sampleCount = 0L
        try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track found in this recording.")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                require(format.getLong(MediaFormat.KEY_DURATION) <= 7_200_000_000L) { "Local transcription supports recordings up to two hours." }
            }
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var inputDone = false
            var outputDone = false
            var idleSince = android.os.SystemClock.elapsedRealtime()
            val info = MediaCodec.BufferInfo()
            destination.outputStream().buffered().use { output ->
                val bytes = ByteBuffer.allocate(16 * 1024).order(ByteOrder.LITTLE_ENDIAN)
                var samples = 0L
                val resamplerFactory = {
                    PcmResampler(rate) { sample ->
                        sumSquares += sample.toDouble() * sample
                        sampleCount++
                        check(++samples <= 16000L * 7200) { "Local transcription supports recordings up to two hours." }
                        if (bytes.remaining() < 4) {
                            output.write(bytes.array(), 0, bytes.position())
                            bytes.clear()
                        }
                        bytes.putFloat(sample)
                    }
                }
                var resampler = resamplerFactory()
                while (!outputDone) {
                    currentCoroutineContext().ensureActive()
                    if (!inputDone) {
                        val slot = decoder.dequeueInputBuffer(10_000)
                        if (slot >= 0) {
                            val buffer = requireNotNull(decoder.getInputBuffer(slot))
                            buffer.clear()
                            val count = extractor.readSampleData(buffer, 0)
                            if (count < 0) {
                                decoder.queueInputBuffer(slot, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(slot, 0, count, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                            idleSince = android.os.SystemClock.elapsedRealtime()
                        }
                    }
                    val slot = decoder.dequeueOutputBuffer(info, 10_000)
                    if (slot == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val decoded = decoder.outputFormat
                        val newRate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        require(samples == 0L || newRate == rate) { "Audio sample rate changed mid-recording." }
                        rate = newRate
                        channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding = if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)) decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        require(channels in 1..8 && encoding in listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT)) { "Unsupported decoded audio format." }
                        if (samples == 0L) resampler = resamplerFactory()
                    } else if (slot >= 0) {
                        try {
                            val buffer = requireNotNull(decoder.getOutputBuffer(slot)).order(ByteOrder.LITTLE_ENDIAN)
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val frameSize = channels * if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            require(info.size % frameSize == 0) { "Incomplete audio frame." }
                            var frames = 0
                            while (buffer.remaining() >= frameSize) {
                                if (++frames % 4096 == 0) currentCoroutineContext().ensureActive()
                                var mono = 0f
                                repeat(channels) { mono += if (encoding == AudioFormat.ENCODING_PCM_FLOAT) buffer.float else buffer.short / 32768f }
                                resampler.accept(mono / channels)
                            }
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            idleSince = android.os.SystemClock.elapsedRealtime()
                        } finally { decoder.releaseOutputBuffer(slot, false) }
                    }
                    check(android.os.SystemClock.elapsedRealtime() - idleSince < 30_000) { "Audio decoder stopped responding." }
                }
                resampler.finish()
                output.write(bytes.array(), 0, bytes.position())
                require(samples >= 1600) { "Recording is too short to transcribe." }
            }
            return kotlin.math.sqrt(sumSquares / sampleCount)
        } finally {
            try { codec?.release() } finally { extractor.release() }
        }
    }
}
