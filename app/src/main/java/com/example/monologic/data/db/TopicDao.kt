package com.example.monologic.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(topic: TopicEntity)

    @Query("SELECT * FROM topics ORDER BY date DESC")
    fun getAll(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): TopicEntity?

    // Phase 2以降で使用
    @Query("SELECT * FROM topics WHERE replyText IS NULL AND blueskyPostUri IS NOT NULL")
    suspend fun getUncollected(): List<TopicEntity>

    // Phase 3以降で使用
    @Query("SELECT * FROM topics WHERE replyText IS NOT NULL AND sentiment IS NULL")
    suspend fun getUnanalyzed(): List<TopicEntity>

    // Phase 2: リプライ監視用
    @Query("SELECT * FROM topics WHERE replyStatus = 'PENDING'")
    suspend fun getPendingReplies(): List<TopicEntity>

    @Query("UPDATE topics SET replyStatus = :status WHERE date = :date")
    suspend fun updateReplyStatus(date: String, status: String)

    @Query("UPDATE topics SET replyStatus = :status, keywords = :keywordsJson WHERE date = :date")
    suspend fun updateReplyAndAnalysis(date: String, status: String, keywordsJson: String)
}
