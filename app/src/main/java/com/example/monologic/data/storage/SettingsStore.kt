package com.example.monologic.data.storage

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("monologic_settings", Context.MODE_PRIVATE)

    fun saveTime(hour: Int, minute: Int) {
        prefs.edit().putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply()
    }

    fun loadTime(): Pair<Int, Int> = Pair(
        prefs.getInt(KEY_HOUR, DEFAULT_HOUR),
        prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)
    )

    companion object {
        private const val KEY_HOUR = "post_hour"
        private const val KEY_MINUTE = "post_minute"
        const val DEFAULT_HOUR = 8
        const val DEFAULT_MINUTE = 0
    }
}
