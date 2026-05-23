package com.example.monologic.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.monologic.MonoLogicApp
import com.example.monologic.analysis.GeminiTopicAnalyzer
import com.example.monologic.analysis.KeywordEntry
import com.example.monologic.data.db.ReplyStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 投稿後 1時間 / 3時間でリプライをチェックするワーカー。
 *
 * CHECK_1H: リプライなし → 再通知、リプライあり → Gemini解析 + REPLIED
 * CHECK_3H: リプライなし → TIMEOUT、リプライあり → Gemini解析 + REPLIED
 *
 * blueskyPostUri（AT-URI）を使ってスレッドを取得する。
 * OAuth トークンがある場合は DPoP ではなく Bearer で fetchReplies を呼ぶ。
 */
class ReplyCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_DATE        = "date"
        const val KEY_CHECK_TYPE  = "checkType"
        const val CHECK_1H        = "1h"
        const val CHECK_3H        = "3h"
    }

    override suspend fun doWork(): Result {
        val app       = applicationContext as MonoLogicApp
        val repo      = app.topicRepository
        val bluesky   = app.blueskyClient
        val notifier  = app.notifier
        val date      = inputData.getString(KEY_DATE)       ?: return Result.failure()
        val checkType = inputData.getString(KEY_CHECK_TYPE) ?: return Result.failure()

        // 対象の TopicEntity を取得（PENDING かつ指定日付）
        val topic = repo.getByDate(date) ?: return Result.success()
        if (topic.replyStatus != ReplyStatus.PENDING) return Result.success()  // 既に処理済み

        val atUri = topic.blueskyPostUri ?: return Result.success()  // 投稿失敗時はスキップ

        // アクセストークン取得（OAuth 優先、なければ App Password セッションを直接使えないのでスキップ）
        val accessToken = app.credentialStore.loadOAuthTokens()?.accessToken
            ?: return Result.success()  // App Password 方式では Bearer 取得手段なし

        // リプライ取得（失敗はリトライ）
        val replies = try {
            bluesky.fetchReplies(atUri, accessToken)
        } catch (e: Exception) {
            return Result.retry()
        }

        return if (replies.isNotEmpty()) {
            // リプライあり → Gemini で話題抽出
            val apiKey   = app.credentialStore.getGeminiApiKey()
            val analyzer = GeminiTopicAnalyzer(apiKey)
            val keywords = analyzer.analyze(replies, topic.word)
            val jsonStr  = Json.encodeToString<List<KeywordEntry>>(keywords)
            repo.updateReplyAndAnalysis(date, ReplyStatus.REPLIED, jsonStr)
            notifier.showReply(topic.word, keywords)
            Result.success()
        } else {
            when (checkType) {
                CHECK_1H -> {
                    // 1時間後でも未リプライ → 再通知のみ（3h ワーカーは既にスケジュール済み）
                    notifier.showNoReplyYet(topic.word)
                    Result.success()
                }
                CHECK_3H -> {
                    // 3時間後でも未リプライ → TIMEOUT
                    repo.updateReplyStatus(date, ReplyStatus.TIMEOUT)
                    notifier.showTimeout(topic.word)
                    Result.success()
                }
                else -> Result.failure()
            }
        }
    }
}
