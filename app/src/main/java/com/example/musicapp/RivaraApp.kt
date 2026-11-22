package com.example.musicapp

import android.app.Application

class RivaraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Live library sync for Google accounts (phone ↔ Mac ↔ watch).
        runCatching { com.example.musicapp.data.cloud.CloudSync.get(this).start() }
        runCatching { com.example.musicapp.wear.WatchBridge.get(this).start() }
    }
}
