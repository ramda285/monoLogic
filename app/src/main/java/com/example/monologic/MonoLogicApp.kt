package com.example.monologic

import android.app.Application
import com.example.monologic.bluesky.BlueskyClient
import com.example.monologic.data.db.AppDatabase
import com.example.monologic.data.storage.CredentialStore
import com.example.monologic.data.storage.SettingsStore
import com.example.monologic.notification.Notifier
import com.example.monologic.repository.TopicRepository
import com.example.monologic.scraper.WeblioScraper
import okhttp3.OkHttpClient

class MonoLogicApp : Application() {
    private val httpClient by lazy { OkHttpClient() }

    val credentialStore by lazy { CredentialStore(this) }
    val settingsStore by lazy { SettingsStore(this) }
    val weblioScraper by lazy { WeblioScraper(httpClient) }
    val blueskyClient by lazy { BlueskyClient(httpClient) }
    val notifier by lazy { Notifier(this) }
    val topicRepository by lazy {
        TopicRepository(AppDatabase.getInstance(this).topicDao())
    }
}
