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
    @SerialName("\$type") val type: String = "app.bsky.feed.post",
    val text: String,
    val createdAt: String,
    val facets: List<Facet>
)

@Serializable data class CreateRecordRequest(
    val repo: String,
    val collection: String = "app.bsky.feed.post",
    val record: PostRecord
)

@Serializable data class CreateRecordResponse(val uri: String, val cid: String)
