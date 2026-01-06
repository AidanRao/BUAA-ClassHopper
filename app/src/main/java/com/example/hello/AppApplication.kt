package com.example.hello

import android.app.Application

class AppApplication : Application() {
    companion object {
        lateinit var instance: AppApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NavigationManager.init(this)
    }
}
