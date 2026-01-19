package com.bigdictaphone.app

import android.app.Application
import android.content.Context

class BigDicTaphoneApplication : Application() {
    
    companion object {
        lateinit var instance: BigDicTaphoneApplication
            private set
        
        val context: Context
            get() = instance.applicationContext
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
