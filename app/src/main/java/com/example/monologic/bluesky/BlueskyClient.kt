package com.example.monologic.bluesky

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

class BlueskyClient(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://bsky.social"
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * お題をBlueskyに投稿する。成功時は投稿URI（at://...形式）、失敗時はnullを返す。
     */
    suspend fun post(
        handle: String,
        appPassword: String,
        word: String,
        weblioUrl: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val session = createSession(handle, appPassword) ?: return@withContext null
            val (text, facets) = buildPostContent(word, weblioUrl)
            createRecord(session.accessJwt, session.did, text, facets)
        } catch (e: Exception) {
            null
        }
    }

    private fun createSession(handle: String, password: String): CreateSessionResponse? {
        val body = json.encodeToString(CreateSessionRequest(handle, password))
            .toRequestBody(mediaType)
        val response = client.newCall(
            Request.Builder()
                .url("$baseUrl/xrpc/com.atproto.server.createSession")
                .post(body).build()
        ).execute()
        if (!response.isSuccessful) return null
        return response.use { json.decodeFromString(it.body!!.string()) }
    }

    private fun createRecord(
        jwt: String, did: String, text: String, facets: List<Facet>
    ): String? {
        val record = PostRecord(
            text = text,
            createdAt = Instant.now().toString(),
            facets = facets
        )
        val body = json.encodeToString(CreateRecordRequest(repo = did, record = record))
            .toRequestBody(mediaType)
        val response = client.newCall(
            Request.Builder()
                .url("$baseUrl/xrpc/com.atproto.repo.createRecord")
                .post(body)
                .header("Authorization", "Bearer $jwt")
                .build()
        ).execute()
        if (!response.isSuccessful) return null
        return response.use {
            json.decodeFromString<CreateRecordResponse>(it.body!!.string()).uri
        }
    }

    /**
     * 投稿テキストとfacets（リンク情報）を生成する。
     * テスト可能なようにinternal visibilityではなくpublicにしている。
     *
     * テキスト形式:
     *   今日のお題：{word} #今日のお題
     *   {weblioUrl}
     *
     * facetsはUTF-8バイトオフセットでURLの範囲を指定する。
     * 日本語1文字 = 3バイトのため、文字数ではなくバイト数で計算する。
     */
    fun buildPostContent(word: String, weblioUrl: String): Pair<String, List<Facet>> {
        val prefix = "今日のお題：$word #今日のお題\n"
        val text = prefix + weblioUrl
        val byteStart = prefix.toByteArray(Charsets.UTF_8).size
        val byteEnd = text.toByteArray(Charsets.UTF_8).size
        val facets = listOf(
            Facet(
                index = FacetIndex(byteStart, byteEnd),
                features = listOf(FacetFeature(uri = weblioUrl))
            )
        )
        return Pair(text, facets)
    }
}
