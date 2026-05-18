package com.example.monologic.data.db

import androidx.room.*

@Dao
interface TopicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(topic: TopicEntity)

    @Query("SELECT * FROM topics ORDER BY date DESC")
    suspend fun getAll(): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): TopicEntity?

    // Phase 2以降で使用
    @Query("SELECT * FROM topics WHERE replyText IS NULL AND blueskyPostUri IS NOT NULL")
    suspend fun getUncollected(): List<TopicEntity>

    // Phase 3以降で使用
    @Query("SELECT * FROM topics WHERE replyText IS NOT NULL AND sentiment IS NULL")
    suspend fun getUnanalyzed(): List<TopicEntity>
}
