package com.example.monologic.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat

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

    /**
     * 通知を表示する。
     * @param word      お題の単語
     * @param tapUrl    タップ時に開く URL。
     *                  投稿成功時は Bluesky の投稿 URL、未投稿時は "https://bsky.app"。
     */
    fun show(word: String, tapUrl: String = "https://bsky.app") {
        val pendingIntent = PendingIntent.getActivity(
            appContext, 0,
            Intent(Intent.ACTION_VIEW, Uri.parse(tapUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
