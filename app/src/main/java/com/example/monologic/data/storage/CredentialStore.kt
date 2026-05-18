package com.example.monologic.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "monologic_credentials",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredentials(handle: String, appPassword: String) {
        prefs.edit()
            .putString(KEY_HANDLE, handle)
            .putString(KEY_PASSWORD, appPassword)
            .apply()
    }

    fun loadCredentials(): Pair<String, String>? {
        val handle = prefs.getString(KEY_HANDLE, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return Pair(handle, password)
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_HANDLE = "bluesky_handle"
        private const val KEY_PASSWORD = "bluesky_app_password"
    }
}
