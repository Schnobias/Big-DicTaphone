package com.bigdictaphone.app

import android.app.Application
import android.content.Context
import com.bigdictaphone.app.services.AudioRecorderService
import com.bigdictaphone.app.services.RecordingRepository
import kotlinx.coroutines.runBlocking

class BigDicTaphoneApplication : Application() {

    lateinit var recordings: RecordingRepository
        private set
    lateinit var audioRecorder: AudioRecorderService
        private set
    
    companion object {
        lateinit var instance: BigDicTaphoneApplication
            private set
        
        val context: Context
            get() = instance.applicationContext
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        recordings = RecordingRepository(applicationContext)
        audioRecorder = AudioRecorderService(applicationContext)
        // Recovery is explicit and runs once at process initialization. Failures are
        // allowed to surface rather than silently discarding durable state.
        runBlocking { try { recordings.initialize() } catch (_: Exception) { /* Repository exposes the error; preserve the unreadable library. */ } }
    }
}
