package com.example.monologic.bluesky

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class CreateSessionRequest(val identifier: String, val password: String)

@Serializable data class CreateSessionResponse(val accessJwt: String, val did: String)

@Serializable data class FacetIndex(val byteStart: Int, val byteEnd: Int)

/**
 * Facet の機能を表すクラス。
 * - リンク: type = "app.bsky.richtext.facet#link"、uri に URL を指定
 * - ハッシュタグ: type = "app.bsky.richtext.facet#tag"、tag に # なしの文字列を指定
 * uri / tag は一方のみ使用し、不要な方は null にする。
 * Json(explicitNulls=false) により null フィールドは出力されない。
 */
@Serializable data class FacetFeature(
    @SerialName("\$type") val type: String,
    val uri: String? = null,
    val tag: String? = null
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

// ── getPostThread（リプライ取得）用モデル ────────────────────────────────────

@Serializable data class AuthorProfile(
    val handle: String,
    val displayName: String? = null
)

@Serializable data class PostValue(val text: String)

@Serializable data class PostView(
    val uri: String,
    val author: AuthorProfile,
    val record: PostValue
)

@Serializable data class ThreadViewPost(
    @SerialName("\$type") val type: String = "",
    val post: PostView,
    val replies: List<ThreadNode> = emptyList()
)

@Serializable data class ThreadNode(
    @SerialName("\$type") val type: String = "",
    val post: PostView? = null
)

@Serializable data class GetPostThreadResponse(val thread: ThreadViewPost)
