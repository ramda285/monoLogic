package com.example.monologic.bluesky

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.KeyPair
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.util.UUID

/**
 * AT Protocol OAuth 2.0 + DPoP (RFC 9449) マネージャー。
 *
 * 担当する処理:
 *  1. PKCE コードベリファイア / チャレンジ生成
 *  2. PAR (Pushed Authorization Request) でブラウザ認可 URL を取得
 *  3. 認可コードをアクセストークンと交換
 *  4. リフレッシュトークンでアクセストークンを更新
 *  5. DPoP プルーフ JWT を生成（API 呼び出しに添付）
 *  6. DID からハンドルを取得（表示用）
 */
class OAuthManager(
    context: Context,
    private val httpClient: OkHttpClient,
    /** EC P-256 鍵ペア — DPoP 署名に使用。CredentialStore で永続化される。 */
    val keyPair: KeyPair
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaTypeForm = "application/x-www-form-urlencoded".toMediaType()

    /**
     * PKCE state を一時保存する SharedPreferences。
     * ブラウザ起動からコールバック受信までの間だけ使用。
     */
    private val statePrefs by lazy {
        appContext.getSharedPreferences("monologic_oauth_state", Context.MODE_PRIVATE)
    }

    /**
     * サーバから受信した最新の DPoP-Nonce。
     * bsky.social は最初のリクエストで use_dpop_nonce を返すため、
     * ノンスを取得後は再試行が必要になる。
     */
    var dpopNonce: String? = null
        private set

    /** API 呼び出し側（BlueskyClient）がノンスを更新するために使用する。 */
    fun updateDpopNonce(nonce: String) { dpopNonce = nonce }

    companion object {
        const val CLIENT_ID = "http://localhost"
        const val REDIRECT_URI = "monologic://oauth/callback"
        // atproto: 基本スコープ、repo:app.bsky.feed.post?action=create: 投稿書き込み権限
        const val SCOPE = "atproto repo:app.bsky.feed.post?action=create"
        const val AUTH_SERVER = "https://bsky.social"
        private const val TAG = "OAuthManager"
    }

    // ─── PKCE ─────────────────────────────────────────────────────────────

    fun generateCodeVerifier(): String =
        ByteArray(32).also { SecureRandom().nextBytes(it) }.b64url()

    private fun codeChallenge(verifier: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII)).b64url()

    fun generateState(): String =
        ByteArray(16).also { SecureRandom().nextBytes(it) }.b64url()

    // ─── PKCE state 永続化 ────────────────────────────────────────────────

    fun savePkceState(codeVerifier: String, state: String) {
        statePrefs.edit()
            .putString("code_verifier", codeVerifier)
            .putString("state", state)
            .apply()
    }

    fun loadCodeVerifier(): String? = statePrefs.getString("code_verifier", null)
    fun loadState(): String? = statePrefs.getString("state", null)

    fun clearPkceState() {
        statePrefs.edit().remove("code_verifier").remove("state").apply()
    }

    // ─── PAR + Authorization URL ──────────────────────────────────────────

    /**
     * PAR エンドポイントにリクエストを送り、ブラウザで開く認可 URL を返す。
     *
     * @param redirectUri ループバックサーバーの URI（例: "http://127.0.0.1:54321"）
     */
    suspend fun buildAuthUrl(codeVerifier: String, state: String, redirectUri: String): String =
        withContext(Dispatchers.IO) {
            // 新しい OAuth フローを開始するたびにノンスをリセットする。
            // 前回ログイン時に受け取ったノンスは期限切れになっている可能性があり、
            // 再利用すると use_dpop_nonce ではなく invalid_request で弾かれる。
            dpopNonce = null
            val challenge = codeChallenge(codeVerifier)
            val form = buildForm(
                "client_id" to CLIENT_ID,
                "redirect_uri" to redirectUri,
                "scope" to SCOPE,
                "state" to state,
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
                "response_type" to "code"
            )
            val requestUri = postWithDpop("$AUTH_SERVER/oauth/par", form) { body ->
                json.decodeFromString<ParResponse>(body).requestUri
            }

            buildString {
                append("$AUTH_SERVER/oauth/authorize")
                append("?request_uri=").append(URLEncoder.encode(requestUri, "UTF-8"))
                append("&client_id=").append(URLEncoder.encode(CLIENT_ID, "UTF-8"))
            }
        }

    // ─── Token Exchange ───────────────────────────────────────────────────

    /** 認可コードをアクセストークンと交換する。失敗時は null を返す。 */
    suspend fun exchangeCode(code: String, codeVerifier: String, redirectUri: String): OAuthTokens? =
        withContext(Dispatchers.IO) {
            runCatching {
                val form = buildForm(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to redirectUri,
                    "client_id" to CLIENT_ID,
                    "code_verifier" to codeVerifier
                )
                postWithDpop("$AUTH_SERVER/oauth/token", form) { body ->
                    val r = json.decodeFromString<TokenResponse>(body)
                    OAuthTokens(accessToken = r.accessToken, refreshToken = r.refreshToken, did = r.sub)
                }
            }.onFailure { Log.e(TAG, "exchangeCode failed", it) }.getOrNull()
        }

    /** リフレッシュトークンで新しいアクセストークンを取得する。失敗時は null を返す。 */
    suspend fun refreshTokens(refreshToken: String): OAuthTokens? =
        withContext(Dispatchers.IO) {
            runCatching {
                val form = buildForm(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                    "client_id" to CLIENT_ID
                )
                postWithDpop("$AUTH_SERVER/oauth/token", form) { body ->
                    val r = json.decodeFromString<TokenResponse>(body)
                    OAuthTokens(accessToken = r.accessToken, refreshToken = r.refreshToken, did = r.sub)
                }
            }.onFailure { Log.e(TAG, "refreshTokens failed", it) }.getOrNull()
        }

    // ─── DPoP Proof ───────────────────────────────────────────────────────

    /**
     * DPoP プルーフ JWT を生成する（RFC 9449）。
     *
     * @param method   HTTP メソッド（"POST", "GET" など）
     * @param url      リクエスト先 URL（クエリパラメータなし）
     * @param nonce    サーバ指定のノンス（省略時は最後に受信したノンスを使用）
     * @param accessToken  既存のアクセストークン（ath クレーム計算用。API 呼び出し時に指定）
     */
    fun createDpopProof(
        method: String,
        url: String,
        nonce: String? = dpopNonce,
        accessToken: String? = null
    ): String {
        val pubKey = keyPair.public as ECPublicKey
        val x = pubKey.w.affineX.toByteArrayFixed(32).b64url()
        val y = pubKey.w.affineY.toByteArrayFixed(32).b64url()
        val jwkJson = """{"crv":"P-256","kty":"EC","x":"$x","y":"$y"}"""
        val headerJson = """{"alg":"ES256","typ":"dpop+jwt","jwk":$jwkJson}"""

        val payloadParts = mutableListOf(
            """"jti":"${UUID.randomUUID()}"""",
            """"htm":"$method"""",
            """"htu":"$url"""",
            """"iat":${System.currentTimeMillis() / 1000}"""
        )
        if (nonce != null) payloadParts += """"nonce":"$nonce""""
        if (accessToken != null) {
            val ath = MessageDigest.getInstance("SHA-256")
                .digest(accessToken.toByteArray(Charsets.US_ASCII)).b64url()
            payloadParts += """"ath":"$ath""""
        }
        val payloadJson = "{${payloadParts.joinToString(",")}}"

        val headerB64 = headerJson.toByteArray(Charsets.UTF_8).b64url()
        val payloadB64 = payloadJson.toByteArray(Charsets.UTF_8).b64url()
        val message = "$headerB64.$payloadB64"

        val derSig = Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyPair.private)
            update(message.toByteArray(Charsets.US_ASCII))
        }.sign()

        return "$message.${derToJose(derSig).b64url()}"
    }

    // ─── DID 解決 / PDS 検出 ────────────────────────────────────────────

    /**
     * DID ドキュメントを解決してユーザーの PDS エンドポイント URL を返す。
     *
     * - did:plc:xxx → https://plc.directory/did:plc:xxx
     * - did:web:xxx → https://xxx/.well-known/did.json
     *
     * DID ドキュメントの service 配列から type="AtprotoPersonalDataServer" の
     * serviceEndpoint を取り出す。失敗時は null を返す。
     */
    suspend fun resolvePdsUrl(did: String): String? = withContext(Dispatchers.IO) {
        try {
            val docUrl = when {
                did.startsWith("did:plc:") -> "https://plc.directory/$did"
                did.startsWith("did:web:") ->
                    "https://${did.removePrefix("did:web:")}/.well-known/did.json"
                else -> return@withContext null
            }
            val body = httpClient.newCall(Request.Builder().url(docUrl).build())
                .execute().use { it.body?.string() } ?: return@withContext null
            Log.d(TAG, "DID doc for $did: $body")
            json.decodeFromString<DidDocument>(body)
                .service?.find { it.type == "AtprotoPersonalDataServer" }?.serviceEndpoint
        } catch (e: Exception) {
            Log.e(TAG, "resolvePdsUrl failed for $did", e)
            null
        }
    }

    // ─── プロフィール取得 ─────────────────────────────────────────────────

    /**
     * DID から Bluesky ハンドル（@xxx.bsky.social）を取得する（表示用）。
     * 公開 API を使用するので OAuth トークン不要。失敗時は null を返す。
     */
    suspend fun fetchHandle(did: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://public.api.bsky.app/xrpc/app.bsky.actor.getProfile" +
                "?actor=${URLEncoder.encode(did, "UTF-8")}"
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                json.decodeFromString<ProfileResponse>(body).handle
            }
        } catch (_: Exception) { null }
    }

    // ─── 内部ヘルパー ─────────────────────────────────────────────────────

    /**
     * DPoP プルーフ付き form-urlencoded POST を実行する。
     *
     * - サーバが `use_dpop_nonce` エラーを返した場合のみノンスを設定して1回再試行する。
     * - それ以外のエラーは即座に例外として投げ、呼び出し元がエラー詳細を表示できるようにする。
     * - すべての試行で詳細なログを出力する。
     */
    private fun <T> postWithDpop(url: String, form: String, parse: (String) -> T): T {
        var lastServerError = ""
        repeat(2) { attempt ->
            val proof = createDpopProof("POST", url)
            val (statusCode, body, newNonce) = httpClient.newCall(
                Request.Builder()
                    .url(url)
                    .post(form.toRequestBody(mediaTypeForm))
                    .header("DPoP", proof)
                    .build()
            ).execute().use { r ->
                Triple(r.code, r.body?.string(), r.header("DPoP-Nonce"))
            }

            Log.d(TAG, "attempt=$attempt url=$url status=$statusCode nonce=$newNonce body=$body")

            if (newNonce != null) dpopNonce = newNonce

            if (statusCode in 200..299 && body != null) {
                return parse(body)
            }

            // エラーレスポンスを解析する
            lastServerError = body ?: "HTTP $statusCode (no body)"
            val errorCode = body?.let {
                runCatching { json.decodeFromString<OAuthErrorResponse>(it).error }.getOrNull()
            }

            // use_dpop_nonce かつノンスが届いた場合のみ再試行する
            if (errorCode == "use_dpop_nonce" && newNonce != null && attempt == 0) {
                Log.d(TAG, "Retrying with DPoP nonce: $newNonce")
                return@repeat
            }

            // それ以外は即エラー（再試行しない）
            throw IllegalStateException("OAuth error ($statusCode): $lastServerError")
        }
        // 2回目も失敗
        throw IllegalStateException("OAuth failed after nonce retry: $lastServerError")
    }

    private fun buildForm(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

    /**
     * DER 形式の ECDSA 署名を JOSE 形式（R || S、各 32 バイト）に変換する。
     * P-256 の DER 署名は INTEGER に 0x00 パディングが付く場合があるため除去する。
     */
    private fun derToJose(der: ByteArray): ByteArray {
        var pos = 2  // SEQUENCE tag (0x30) + length をスキップ
        check(der[pos].toInt() == 0x02) { "Expected INTEGER tag for r" }
        val rLen = der[++pos].toInt() and 0xFF; pos++
        val r = der.copyOfRange(pos, pos + rLen); pos += rLen
        check(der[pos].toInt() == 0x02) { "Expected INTEGER tag for s" }
        val sLen = der[++pos].toInt() and 0xFF; pos++
        val s = der.copyOfRange(pos, pos + sLen)

        val result = ByteArray(64)
        // DER の INTEGER は先頭に 0x00 パディングが入る場合がある（33バイト → 32バイトに正規化）
        val rFinal = if (r.size > 32) r.copyOfRange(r.size - 32, r.size) else r
        val sFinal = if (s.size > 32) s.copyOfRange(s.size - 32, s.size) else s
        rFinal.copyInto(result, destinationOffset = 32 - rFinal.size)
        sFinal.copyInto(result, destinationOffset = 64 - sFinal.size)
        return result
    }

    private fun ByteArray.b64url(): String =
        Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun java.math.BigInteger.toByteArrayFixed(size: Int): ByteArray {
        val bytes = toByteArray()
        return when {
            bytes.size > size -> bytes.copyOfRange(bytes.size - size, bytes.size)
            bytes.size < size -> ByteArray(size - bytes.size) + bytes
            else -> bytes
        }
    }
}
