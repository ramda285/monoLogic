package com.example.monologic.data.storage

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.monologic.bluesky.OAuthTokens
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class CredentialStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            "monologic_credentials",
            MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ─── App Password（旧方式・後方互換のため残す） ────────────────────────

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

    // ─── OAuth トークン ──────────────────────────────────────────────────

    fun saveOAuthTokens(
        accessToken: String,
        refreshToken: String,
        did: String,
        handle: String?,
        pdsUrl: String? = null
    ) {
        prefs.edit()
            .putString(KEY_OAUTH_ACCESS, accessToken)
            .putString(KEY_OAUTH_REFRESH, refreshToken)
            .putString(KEY_OAUTH_DID, did)
            .putString(KEY_OAUTH_HANDLE, handle)
            .putString(KEY_OAUTH_PDS, pdsUrl)
            .apply()
    }

    fun loadOAuthTokens(): OAuthTokens? {
        val access = prefs.getString(KEY_OAUTH_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_OAUTH_REFRESH, null) ?: return null
        val did = prefs.getString(KEY_OAUTH_DID, null) ?: return null
        return OAuthTokens(accessToken = access, refreshToken = refresh, did = did)
    }

    /** 保存済みのハンドル文字列を返す（表示用）。未接続時は null。 */
    fun loadOAuthHandle(): String? = prefs.getString(KEY_OAUTH_HANDLE, null)

    /** 保存済みの PDS エンドポイント URL を返す。未設定時は null。 */
    fun loadOAuthPdsUrl(): String? = prefs.getString(KEY_OAUTH_PDS, null)

    fun clearOAuthTokens() {
        prefs.edit()
            .remove(KEY_OAUTH_ACCESS)
            .remove(KEY_OAUTH_REFRESH)
            .remove(KEY_OAUTH_DID)
            .remove(KEY_OAUTH_HANDLE)
            .remove(KEY_OAUTH_PDS)
            .apply()
    }

    // ─── DPoP EC 鍵ペア ──────────────────────────────────────────────────

    /**
     * EncryptedSharedPreferences に保存済みの EC P-256 鍵ペアを返す。
     * 存在しない場合は新規生成して保存してから返す。
     * OAuthManager の初期化時に1回だけ呼び出す。
     */
    fun loadOrCreateDpopKeyPair(): KeyPair {
        val privB64 = prefs.getString(KEY_DPOP_PRIV, null)
        val pubB64 = prefs.getString(KEY_DPOP_PUB, null)
        if (privB64 != null && pubB64 != null) {
            return try {
                val factory = KeyFactory.getInstance("EC")
                val priv = factory.generatePrivate(
                    PKCS8EncodedKeySpec(Base64.decode(privB64, Base64.DEFAULT))
                )
                val pub = factory.generatePublic(
                    X509EncodedKeySpec(Base64.decode(pubB64, Base64.DEFAULT))
                )
                KeyPair(pub, priv)
            } catch (_: Exception) {
                generateAndStoreDpopKeyPair()
            }
        }
        return generateAndStoreDpopKeyPair()
    }

    private fun generateAndStoreDpopKeyPair(): KeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        val kp = gen.generateKeyPair()
        prefs.edit()
            .putString(KEY_DPOP_PRIV, Base64.encodeToString(kp.private.encoded, Base64.DEFAULT))
            .putString(KEY_DPOP_PUB, Base64.encodeToString(kp.public.encoded, Base64.DEFAULT))
            .apply()
        return kp
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_HANDLE = "bluesky_handle"
        private const val KEY_PASSWORD = "bluesky_app_password"
        private const val KEY_OAUTH_ACCESS = "oauth_access_token"
        private const val KEY_OAUTH_REFRESH = "oauth_refresh_token"
        private const val KEY_OAUTH_DID = "oauth_did"
        private const val KEY_OAUTH_HANDLE = "oauth_handle"
        private const val KEY_OAUTH_PDS = "oauth_pds_url"
        private const val KEY_DPOP_PRIV = "dpop_private_key"
        private const val KEY_DPOP_PUB = "dpop_public_key"
    }
}
