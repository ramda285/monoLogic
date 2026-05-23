package com.example.monologic.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.monologic.MonoLogicApp
import com.example.monologic.data.db.ReplyStatus
import com.example.monologic.data.db.TopicEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MonoLogicApp

        // 1. Weblioからランダム単語を取得（失敗時はワーカーを終了）
        val weblioResult = app.weblioScraper.fetchRandomWord() ?: return Result.failure()

        // 2. Bluesky に投稿（通知 URL に使うため先に実行）
        //    OAuth トークンがあれば優先して使用。
        //    トークン期限切れ（401）の場合はリフレッシュを試みる。
        //    OAuth 未設定なら App Password にフォールバック。
        val postUri = tryPost(app, weblioResult.word, weblioResult.url)

        // 3. ローカル通知を表示
        //    投稿成功時はその投稿の URL、未投稿時は Bluesky トップを開く
        val tapUrl = postUri?.let { atUriToWebUrl(it, app.credentialStore.loadOAuthHandle()) }
            ?: "https://bsky.app"
        app.notifier.show(weblioResult.word, tapUrl)

        // 投稿失敗時はエラー内容を別通知で表示（デバッグ用）
        if (postUri == null && app.credentialStore.loadOAuthTokens() != null) {
            val errMsg = app.blueskyClient.lastError ?: "投稿失敗（詳細不明）"
            app.notifier.showError("Bluesky 投稿失敗", errMsg)
        }

        // 4. DBに記録（将来フェーズのリプライ収集・AI分析の起点）
        // Locale.USを使用してISO 8601形式を保証する（DBの主キーとして使用するため）
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        app.topicRepository.saveTopic(
            TopicEntity(
                date = today,
                word = weblioResult.word,
                weblioUrl = weblioResult.url,
                blueskyPostUri = postUri,
                postedAt = System.currentTimeMillis(),
                // 投稿成功時はリプライ監視を開始
                replyStatus = if (postUri != null) ReplyStatus.PENDING else null
            )
        )

        // 投稿成功時はリプライチェックワーカーをスケジュール
        if (postUri != null) {
            WorkScheduler.scheduleReplyCheck1h(applicationContext, today)
            WorkScheduler.scheduleReplyCheck3h(applicationContext, today)
        }

        // 5. 翌日分のワーカーを再スケジュール
        val (hour, minute) = app.settingsStore.loadTime()
        WorkScheduler.schedule(applicationContext, hour, minute)

        return Result.success()
    }

    /**
     * AT Protocol URI（at://did/.../rkey）を Bluesky Web URL に変換する。
     * 例: at://did:plc:abc/app.bsky.feed.post/xyz → https://bsky.app/profile/did:plc:abc/post/xyz
     * handle が分かっている場合は DID の代わりに handle を使う。
     */
    private fun atUriToWebUrl(atUri: String, handle: String?): String {
        return try {
            val parts = atUri.removePrefix("at://").split("/")
            val did = parts[0]
            val rkey = parts[2]
            val identifier = handle ?: did
            "https://bsky.app/profile/$identifier/post/$rkey"
        } catch (_: Exception) {
            "https://bsky.app"
        }
    }

    /**
     * OAuth → リフレッシュ → App Password の順に投稿を試みる。
     * すべて失敗した場合（または認証情報なし）は null を返す。
     */
    private suspend fun tryPost(app: MonoLogicApp, word: String, weblioUrl: String): String? {
        val oauthTokens = app.credentialStore.loadOAuthTokens()
        if (oauthTokens != null) {
            val pdsUrl = app.credentialStore.loadOAuthPdsUrl()

            // まず現在のアクセストークンで試みる
            val result = app.blueskyClient.postWithOAuth(
                accessToken = oauthTokens.accessToken,
                did = oauthTokens.did,
                word = word,
                weblioUrl = weblioUrl,
                oauthManager = app.oauthManager,
                pdsUrl = pdsUrl
            )
            if (result != null) return result

            // 失敗した場合はトークンをリフレッシュして再試行
            val newTokens = app.oauthManager.refreshTokens(oauthTokens.refreshToken)
            if (newTokens != null) {
                val handle = app.credentialStore.loadOAuthHandle()
                app.credentialStore.saveOAuthTokens(
                    newTokens.accessToken, newTokens.refreshToken, newTokens.did, handle, pdsUrl
                )
                return app.blueskyClient.postWithOAuth(
                    accessToken = newTokens.accessToken,
                    did = newTokens.did,
                    word = word,
                    weblioUrl = weblioUrl,
                    oauthManager = app.oauthManager,
                    pdsUrl = pdsUrl
                )
            }
            return null
        }

        // OAuth 未設定 → App Password フォールバック
        val credentials = app.credentialStore.loadCredentials()
        return if (credentials != null) {
            app.blueskyClient.post(
                handle = credentials.first,
                appPassword = credentials.second,
                word = word,
                weblioUrl = weblioUrl
            )
        } else null
    }
}
