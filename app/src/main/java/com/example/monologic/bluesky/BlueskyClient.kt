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
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true   // $type / collection などデフォルト値フィールドを必ず出力
    }
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
            type = "app.bsky.feed.post",
            text = text,
            createdAt = Instant.now().toString(),
            facets = facets
        )
        val body = json.encodeToString(
            CreateRecordRequest(repo = did, collection = "app.bsky.feed.post", record = record)
        )
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
     *
     * - Authorization: DPoP {token}
     * - DPoP: {proof with ath claim}
     * - use_dpop_nonce エラー時は DPoP-Nonce / WWW-Authenticate 両ヘッダーを確認して
     *   nonce を更新し1回リトライする
     *
     * 成功時は投稿 URI、失敗時は null を返す。
     * 失敗理由は lastError プロパティで取得できる（DailyWorker の通知用）。
     */
    var lastError: String? = null
        private set

    suspend fun postWithOAuth(
        accessToken: String,
        did: String,
        word: String,
        weblioUrl: String,
        oauthManager: OAuthManager,
        pdsUrl: String? = null          // DID ドキュメントから解決した PDS エンドポイント
    ): String? = withContext(Dispatchers.IO) {
        lastError = null
        try {
            val (text, facets) = buildPostContent(word, weblioUrl)
            // PDS URL が指定されていればそちらを使う（bsky.social はエントリウェイのため NG）
            val pdsBase = pdsUrl ?: baseUrl
            val recordUrl = "$pdsBase/xrpc/com.atproto.repo.createRecord"
            Log.d(TAG, "postWithOAuth pdsBase=$pdsBase")
            val bodyJson = json.encodeToString(
                CreateRecordRequest(
                    repo = did,
                    collection = "app.bsky.feed.post",
                    record = PostRecord(
                        type = "app.bsky.feed.post",
                        text = text,
                        createdAt = Instant.now().toString(),
                        facets = facets
                    )
                )
            )
            Log.d(TAG, "postWithOAuth request body: $bodyJson")

            fun attempt(attemptNum: Int): String? {
                val proof = oauthManager.createDpopProof(
                    method = "POST",
                    url = recordUrl,
                    accessToken = accessToken
                )

                val (statusCode, bodyStr, dpopNonceHeader, wwwAuth) =
                    client.newCall(
                        Request.Builder()
                            .url(recordUrl)
                            .post(bodyJson.toRequestBody(mediaType))
                            .header("Authorization", "DPoP $accessToken")
                            .header("DPoP", proof)
                            .build()
                    ).execute().use { r ->
                        // 4つまとめて取得（use {} 内で body を読み切る）
                        listOf(
                            r.code,
                            r.body?.string() ?: "",
                            r.header("DPoP-Nonce"),
                            r.header("WWW-Authenticate")
                        )
                    }

                Log.d(
                    TAG,
                    "postWithOAuth #$attemptNum status=$statusCode " +
                        "DPoP-Nonce=$dpopNonceHeader WWW-Auth=$wwwAuth body=$bodyStr"
                )

                // DPoP-Nonce ヘッダーを優先して更新
                (dpopNonceHeader as? String)?.let { oauthManager.updateDpopNonce(it) }

                // DPoP-Nonce がなければ WWW-Authenticate から nonce を抽出
                if (dpopNonceHeader == null) {
                    (wwwAuth as? String)?.let { header ->
                        extractNonce(header)?.let { oauthManager.updateDpopNonce(it) }
                    }
                }

                if ((statusCode as Int) in 200..299 && (bodyStr as String).isNotEmpty()) {
                    return json.decodeFromString<CreateRecordResponse>(bodyStr).uri
                }

                lastError = "HTTP $statusCode: $bodyStr"
                Log.e(TAG, "postWithOAuth #$attemptNum failed: $lastError wwwAuth=$wwwAuth")
                return null
            }

            val first = attempt(1)
            if (first != null) return@withContext first

            // nonce が更新されていれば2回目を試みる
            Log.d(TAG, "postWithOAuth retrying with nonce=${oauthManager.dpopNonce}")
            attempt(2)

        } catch (e: Exception) {
            lastError = e.message ?: "exception"
            Log.e(TAG, "postWithOAuth exception", e)
            null
        }
    }

    /**
     * WWW-Authenticate ヘッダーから DPoP nonce を抽出する。
     * 例: `DPoP error="use_dpop_nonce", algs="ES256", nonce="abc123"`
     */
    private fun extractNonce(wwwAuthenticate: String): String? =
        Regex("""nonce="([^"]+)"""").find(wwwAuthenticate)?.groupValues?.getOrNull(1)

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
