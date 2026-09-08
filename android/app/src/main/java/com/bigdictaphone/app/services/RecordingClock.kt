package com.bigdictaphone.app.services

/** Monotonic elapsed time excluding pauses; independent of UI timer frequency. */
class RecordingClock(private val now: () -> Long) {
    private var elapsed = 0L
    private var startedAt: Long? = null
    fun start() { elapsed = 0; startedAt = now() }
    fun pause() { startedAt?.let { elapsed += now() - it }; startedAt = null }
    fun resume() { if (startedAt == null) startedAt = now() }
    fun duration() = elapsed + (startedAt?.let { now() - it } ?: 0)
}
