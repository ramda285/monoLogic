package com.example.monologic.bluesky

import android.util.Log
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

    companion object {
        private const val TAG = "BlueskyClient"
    }

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
        return client.newCall(
            Request.Builder()
                .url("$baseUrl/xrpc/com.atproto.server.createSession")
                .post(body).build()
        ).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val bodyString = response.body?.string() ?: return@use null
            json.decodeFromString(bodyString)
        }
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
        return client.newCall(
            Request.Builder()
                .url("$baseUrl/xrpc/com.atproto.repo.createRecord")
                .post(body)
                .header("Authorization", "Bearer $jwt")
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val bodyString = response.body?.string() ?: return@use null
            json.decodeFromString<CreateRecordResponse>(bodyString).uri
        }
    }

    /**
     * OAuth アクセストークン（DPoP バインド）を使って Bluesky に投稿する。
     * Authorization ヘッダは "DPoP {token}"、DPoP プルーフは ath クレーム付き。
     * use_dpop_nonce エラー時は nonce を更新して1回リトライする。
     * 成功時は投稿 URI、失敗時は null を返す。
     */
    suspend fun postWithOAuth(
        accessToken: String,
        did: String,
        word: String,
        weblioUrl: String,
        oauthManager: OAuthManager
    ): String? = withContext(Dispatchers.IO) {
        try {
            val (text, facets) = buildPostContent(word, weblioUrl)
            val recordUrl = "$baseUrl/xrpc/com.atproto.repo.createRecord"
            val record = PostRecord(
                text = text,
                createdAt = Instant.now().toString(),
                facets = facets
            )
            val bodyBytes = json.encodeToString(CreateRecordRequest(repo = did, record = record))

            var result: String? = null
            repeat(2) { attempt ->
                if (result != null) return@repeat
                val dpopProof = oauthManager.createDpopProof(
                    method = "POST",
                    url = recordUrl,
                    accessToken = accessToken
                )
                val (statusCode, bodyStr, newNonce) = client.newCall(
                    Request.Builder()
                        .url(recordUrl)
                        .post(bodyBytes.toRequestBody(mediaType))
                        .header("Authorization", "DPoP $accessToken")
                        .header("DPoP", dpopProof)
                        .build()
                ).execute().use { r ->
                    Triple(r.code, r.body?.string(), r.header("DPoP-Nonce"))
                }

                Log.d(TAG, "postWithOAuth attempt=$attempt status=$statusCode nonce=$newNonce body=$bodyStr")

                if (newNonce != null) oauthManager.updateDpopNonce(newNonce)

                if (statusCode in 200..299 && bodyStr != null) {
                    result = json.decodeFromString<CreateRecordResponse>(bodyStr).uri
                    return@repeat
                }

                val errorCode = bodyStr?.let {
                    runCatching { json.decodeFromString<OAuthErrorResponse>(it).error }.getOrNull()
                }
                // use_dpop_nonce かつノンスを受け取った場合のみリトライ
                if (errorCode == "use_dpop_nonce" && newNonce != null && attempt == 0) {
                    Log.d(TAG, "Retrying postWithOAuth with DPoP nonce: $newNonce")
                    return@repeat
                }
                // それ以外は即終了
                Log.e(TAG, "postWithOAuth failed: status=$statusCode body=$bodyStr")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "postWithOAuth exception", e)
            null
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
