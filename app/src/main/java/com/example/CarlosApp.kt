package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class CarlosApp : Application() {

    companion object {
        const val CHANNEL_ID_WAKE_WORD = "carlos_wake_word_channel"
        const val CHANNEL_NAME_WAKE_WORD = "Carlos Background Wake Word Listener"
        lateinit var instance: CarlosApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_WAKE_WORD,
                CHANNEL_NAME_WAKE_WORD,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Carlos listening in background for 'Hey Carlos' wake word"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
