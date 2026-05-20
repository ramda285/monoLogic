package com.example.monologic

import android.app.TimePickerDialog
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import com.example.monologic.bluesky.LoopbackOAuthServer
import com.example.monologic.worker.WorkScheduler
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var tvConnectionStatus: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnTimePicker: Button
    private var selectedHour = 8
    private var selectedMinute = 0

    /** 進行中のループバックサーバー（画面破棄時にクリーンアップ） */
    private var loopbackServer: LoopbackOAuthServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        btnTimePicker = findViewById(R.id.btnTimePicker)

        val app = application as MonoLogicApp

        // 保存済み投稿時刻を反映（画面回転後は savedInstanceState から復元）
        val (h, m) = savedInstanceState?.let {
            it.getInt("hour") to it.getInt("minute")
        } ?: app.settingsStore.loadTime()
        selectedHour = h
        selectedMinute = m
        updateTimeButton()

        btnTimePicker.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                updateTimeButton()
            }, selectedHour, selectedMinute, true).show()
        }

        // ── Bluesky 接続ボタン ───────────────────────────────────────────
        btnConnect.setOnClickListener {
            lifecycleScope.launch {
                btnConnect.isEnabled = false
                btnConnect.text = "接続中..."

                val server = LoopbackOAuthServer()
                loopbackServer = server

                try {
                    val port = server.start()
                    val redirectUri = "http://127.0.0.1:$port"

                    val verifier = app.oauthManager.generateCodeVerifier()
                    val state = app.oauthManager.generateState()
                    // PKCE state を保存（Activity が再生成されても使えるよう SharedPrefs に保存）
                    app.oauthManager.savePkceState(verifier, state)

                    val authUrl = app.oauthManager.buildAuthUrl(verifier, state, redirectUri)

                    // 通常ブラウザで開く（Custom Tab は loopback リダイレクトをブロックする場合がある）
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))

                    // ブラウザからのコールバックを待機（ここでコルーチンが中断）
                    val callbackUri = server.awaitCallback()

                    // state 検証（CSRF 対策）
                    val returnedState = callbackUri.getQueryParameter("state")
                    if (returnedState != state) {
                        Toast.makeText(this@SettingsActivity, "認証エラー: state 不一致", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    // エラーパラメータが来た場合（ユーザーキャンセルなど）
                    val error = callbackUri.getQueryParameter("error")
                    if (error != null) {
                        val desc = callbackUri.getQueryParameter("error_description") ?: error
                        Toast.makeText(this@SettingsActivity, "接続キャンセル: $desc", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val code = callbackUri.getQueryParameter("code") ?: run {
                        Toast.makeText(this@SettingsActivity, "認証エラー: code なし", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    // 認可コードをトークンと交換
                    val tokens = app.oauthManager.exchangeCode(code, verifier, redirectUri)
                    if (tokens != null) {
                        // ハンドルと PDS URL を並行して解決
                        val handle = app.oauthManager.fetchHandle(tokens.did)
                        val pdsUrl = app.oauthManager.resolvePdsUrl(tokens.did)
                        app.credentialStore.saveOAuthTokens(
                            tokens.accessToken, tokens.refreshToken, tokens.did, handle, pdsUrl
                        )
                        val display = handle?.let { "@$it" } ?: tokens.did
                        Toast.makeText(
                            this@SettingsActivity,
                            "Bluesky 接続完了: $display (PDS: ${pdsUrl ?: "不明"})",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this@SettingsActivity, "トークン取得失敗", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    val msg = e.message ?: "unknown error"
                    android.util.Log.e("OAuthDebug", "Login failed: $msg", e)
                    androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("接続エラー")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show()
                } finally {
                    loopbackServer = null
                    app.oauthManager.clearPkceState()
                    updateConnectionStatus(app)
                }
            }
        }

        // ── 接続解除ボタン ───────────────────────────────────────────────
        btnDisconnect.setOnClickListener {
            app.credentialStore.clearOAuthTokens()
            updateConnectionStatus(app)
            Toast.makeText(this, "Bluesky 接続を解除しました", Toast.LENGTH_SHORT).show()
        }

        // ── 保存ボタン（投稿時刻のみ） ──────────────────────────────────
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            app.settingsStore.saveTime(selectedHour, selectedMinute)
            WorkScheduler.schedule(this, selectedHour, selectedMinute)
            Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateConnectionStatus(application as MonoLogicApp)
    }

    /**
     * monologic://oauth/done から戻ってきたとき（singleTop 再利用時）に呼ばれる。
     * OAuth フローはすでに lifecycleScope で動いているので何もしない。
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        loopbackServer?.stop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("hour", selectedHour)
        outState.putInt("minute", selectedMinute)
    }

    private fun updateConnectionStatus(app: MonoLogicApp) {
        val handle = app.credentialStore.loadOAuthHandle()
        val tokens = app.credentialStore.loadOAuthTokens()
        val isConnected = tokens != null

        tvConnectionStatus.text = if (isConnected) {
            "接続済み: ${handle?.let { "@$it" } ?: tokens!!.did}"
        } else {
            "未接続"
        }
        btnConnect.isEnabled = !isConnected
        btnConnect.text = "Bluesky でログイン"
        btnDisconnect.isEnabled = isConnected
    }

    private fun updateTimeButton() {
        btnTimePicker.text = "%02d:%02d".format(selectedHour, selectedMinute)
    }
}
