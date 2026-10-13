package com.bigdictaphone.app.services

import androidx.annotation.Keep

@Keep
class WhisperNative {
    external fun transcribe(model: String, audio: String, language: String, callback: Callback): ByteArray?

    @Keep
    interface Callback {
        fun isCancelled(): Boolean
        fun onProgress(percent: Int)
    }

    companion object { init { System.loadLibrary("dictaphone-whisper") } }
}
