package com.example.monologic.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.monologic.MainActivity

class Notifier(context: Context) {
    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "今日のお題", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "毎日のお題通知" }
        )
    }

    fun show(word: String) {
        val pendingIntent = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("今日のお題")
                .setContentText(word)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
        )
    }

    companion object {
        const val CHANNEL_ID = "daily_topic"
        const val NOTIFICATION_ID = 1001
    }
}
