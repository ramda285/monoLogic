package com.example.monologic.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.monologic.MonoLogicApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as MonoLogicApp
            val (hour, minute) = app.settingsStore.loadTime()
            WorkScheduler.schedule(context, hour, minute)
        }
    }
}
