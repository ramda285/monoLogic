package com.example.monologic.bluesky

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class CreateSessionRequest(val identifier: String, val password: String)

@Serializable data class CreateSessionResponse(val accessJwt: String, val did: String)

@Serializable data class FacetIndex(val byteStart: Int, val byteEnd: Int)

@Serializable data class FacetFeature(
    @SerialName("\$type") val type: String = "app.bsky.richtext.facet#link",
    val uri: String
)

@Serializable data class Facet(val index: FacetIndex, val features: List<FacetFeature>)

@Serializable data class PostRecord(
    @SerialName("\$type") val type: String,   // 常に "app.bsky.feed.post" を明示
    val text: String,
    val createdAt: String,
    val facets: List<Facet>
)

@Serializable data class CreateRecordRequest(
    val repo: String,
    val collection: String,                   // 常に "app.bsky.feed.post" を明示
    val record: PostRecord
)

@Serializable data class CreateRecordResponse(val uri: String, val cid: String)

// ── AT Protocol OAuth ──────────────────────────────────────────────────────

/** PAR エンドポイントのレスポンス */
@Serializable data class ParResponse(
    @SerialName("request_uri") val requestUri: String
)

/** トークンエンドポイントのレスポンス（code 交換・リフレッシュ共通） */
@Serializable data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    val sub: String              // ユーザーの DID
)

/** トークンエンドポイントのエラーレスポンス */
@Serializable data class OAuthErrorResponse(
    val error: String,
    @SerialName("error_description") val errorDescription: String? = null
)

/** 公開プロフィール API レスポンス（ハンドル取得用） */
@Serializable data class ProfileResponse(val handle: String)

/** DID ドキュメントのサービスエントリ */
@Serializable data class DidService(
    val type: String,
    val serviceEndpoint: String
)

/** DID ドキュメント（PDS URL 取得に必要な最小フィールドのみ） */
@Serializable data class DidDocument(
    val service: List<DidService>? = null
)

/** アプリ内で OAuth トークンを保持するデータクラス */
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val did: String
)
