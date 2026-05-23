package com.example.monologic.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "topics")
data class TopicEntity(
    @PrimaryKey
    val date: String,             // "2026-05-18"（1日1件）

    // Phase 1
    val word: String,
    val weblioUrl: String,
    val blueskyPostUri: String?,  // 投稿失敗時はnull
    val postedAt: Long,           // Unixミリ秒

    // Phase 2（リプライ監視）
    val replyStatus: String? = null,  // ReplyStatus.PENDING / REPLIED / TIMEOUT
    val replyText: String? = null,
    val replyUri: String? = null,
    val collectedAt: Long? = null,

    // Phase 3（AI分析用・予約）
    val sentiment: String? = null,
    val keywords: String? = null, // JSON配列文字列 e.g. "[\"海\",\"塩\"]"
    val analyzedAt: Long? = null,

    // Phase 4（マインドマップ用・予約）
    val mindmapNode: String? = null
)
