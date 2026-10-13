package com.bigdictaphone.app.services

import kotlin.math.*

/** Streaming windowed-sinc low-pass resampler; state survives codec buffer boundaries. */
class PcmResampler(private val inputRate: Int, private val emit: (Float) -> Unit) {
    private val ring = FloatArray(64)
    private var index = -1L
    private var outputCount = 0L
    private val step = inputRate / 16000.0
    private val cutoff = min(1.0, 16000.0 / inputRate) * 0.90
    init { require(inputRate in 8000..192000) }
    fun accept(sample: Float) {
        index++
        ring[(index % ring.size).toInt()] = if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
        while (outputCount * step + 16 <= index) output()
    }
    fun finish() {
        val targetCount = ((index + 1) / step).roundToLong()
        repeat(17) {
            index++
            ring[(index % ring.size).toInt()] = 0f
            while (outputCount < targetCount && outputCount * step + 16 <= index) output()
        }
    }
    private fun output() {
        val position = outputCount * step
        val center = floor(position).toLong()
        var sum = 0.0
        var weights = 0.0
        for (offset in -15..16) {
            val frame = center + offset
            val distance = frame - position
            val x = PI * distance * cutoff
            val sinc = if (abs(x) < 1e-9) 1.0 else sin(x) / x
            val weight = cutoff * sinc * (0.5 + 0.5 * cos(PI * distance / 16))
            val sample = if (frame < 0) 0f else ring[(frame % ring.size).toInt()]
            sum += sample * weight
            weights += weight
        }
        emit((sum / weights).toFloat().coerceIn(-1f, 1f))
        outputCount++
    }
}
