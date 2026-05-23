package com.example.monologic.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Gemini 1.5 Flash API を使って Bluesky リプライ群から
 * 「話題トピック + 感情」を最大3件抽出する。
 *
 * - topicWord と同一のトピックはプロンプトで除外指示
 * - レスポンスは JSON 配列 [{"word":"...","sentiment":"POSITIVE/NEGATIVE/NEUTRAL"}]
 * - API 呼び出し失敗時は空リストを返す
 */
class GeminiTopicAnalyzer(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Gemini API リクエスト・レスポンス用内部モデル
    @Serializable private data class Part(val text: String)
    @Serializable private data class Content(val parts: List<Part>)
    @Serializable private data class GeminiRequest(val contents: List<Content>)
    @Serializable private data class Candidate(val content: Content)
    @Serializable private data class GeminiResponse(val candidates: List<Candidate> = emptyList())

    /**
     * リプライテキストのリストと今日のお題単語を受け取り、
     * 最大3件の KeywordEntry を返す。
     *
     * @param replies リプライの (authorHandle, text) ペアリスト
     * @param topicWord 今日のお題（このワード自体はキーワードに含めない）
     */
    suspend fun analyze(replies: List<Pair<String, String>>, topicWord: String): List<KeywordEntry> {
        if (replies.isEmpty() || apiKey.isBlank()) return emptyList()

        val repliesText = replies.joinToString("\n") { (_, text) -> "- $text" }

        val prompt = """
以下は「$topicWord」というお題に対するSNSリプライです。
リプライ全体を読んで、会話で話題になっているトピック（名詞・固有名詞など）を最大3つ抽出してください。
「$topicWord」自体はトピックに含めないでください。
各トピックについて、リプライ内での文脈がポジティブか、ネガティブか、どちらでもないかを判定してください。

リプライ:
$repliesText

以下のJSON配列のみを返してください（説明文・コードブロック不要）:
[{"word":"トピック名","sentiment":"POSITIVE"},{"word":"トピック名","sentiment":"NEGATIVE"},{"word":"トピック名","sentiment":"NEUTRAL"}]

sentimentの値は必ず POSITIVE / NEGATIVE / NEUTRAL のいずれかにしてください。
抽出できるトピックが3つ未満の場合は、その分だけ返してください。
""".trimIndent()

        val requestBody = json.encodeToString(
            GeminiRequest(listOf(Content(listOf(Part(prompt)))))
        ).toRequestBody("application/json".toMediaType())

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val resp = client.newCall(request).execute()
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val geminiResp = json.decodeFromString<GeminiResponse>(body)
                val rawText = geminiResp.candidates.firstOrNull()
                    ?.content?.parts?.firstOrNull()?.text
                    ?: return@withContext emptyList()

                // JSON ブロック抽出（```json ... ``` や余分なテキストへの耐性）
                val jsonStr = extractJsonArray(rawText)
                json.decodeFromString<List<KeywordEntry>>(jsonStr)
                    .filter { it.word.isNotBlank() && it.word != topicWord }
                    .take(3)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /** レスポンステキストから [ ... ] 部分を抽出する */
    private fun extractJsonArray(text: String): String {
        val start = text.indexOf('[')
        val end   = text.lastIndexOf(']')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else "[]"
    }

    /**
     * KeywordEntry リストの多数決でトップ感情を返す。
     * 同数: POSITIVE > NEGATIVE > NEUTRAL の優先順。
     */
    fun majoritySentiment(keywords: List<KeywordEntry>): String {
        if (keywords.isEmpty()) return Sentiment.NEUTRAL
        val counts = keywords.groupingBy { it.sentiment }.eachCount()
        val max = counts.values.maxOrNull() ?: return Sentiment.NEUTRAL
        return when {
            (counts[Sentiment.POSITIVE] ?: 0) == max &&
            (counts[Sentiment.POSITIVE] ?: 0) >= (counts[Sentiment.NEGATIVE] ?: 0) -> Sentiment.POSITIVE
            (counts[Sentiment.NEGATIVE] ?: 0) == max -> Sentiment.NEGATIVE
            else -> Sentiment.NEUTRAL
        }
    }
}
