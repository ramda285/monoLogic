package com.example.monologic.repository

import com.example.monologic.data.db.TopicDao
import com.example.monologic.data.db.TopicEntity
import kotlinx.coroutines.flow.Flow

class TopicRepository(private val dao: TopicDao) {

    // Phase 1: 保存・取得
    suspend fun saveTopic(entity: TopicEntity) = dao.upsert(entity)

    suspend fun getTodayTopic(date: String): TopicEntity? = dao.getByDate(date)

    suspend fun getByDate(date: String): TopicEntity? = dao.getByDate(date)

    /** 日付降順で全お題をLiveなFlowとして返す */
    fun getAllFlow(): Flow<List<TopicEntity>> = dao.getAll()

    // Phase 2以降: リプライ収集（スタブ）
    suspend fun getUncollected(): List<TopicEntity> = dao.getUncollected()

    // Phase 3以降: AI分析（スタブ）
    suspend fun getUnanalyzed(): List<TopicEntity> = dao.getUnanalyzed()

    // Phase 2: リプライ監視
    suspend fun getPendingReplies(): List<TopicEntity> = dao.getPendingReplies()

    suspend fun updateReplyStatus(date: String, status: String) =
        dao.updateReplyStatus(date, status)

    suspend fun updateReplyAndAnalysis(date: String, status: String, keywordsJson: String) =
        dao.updateReplyAndAnalysis(date, status, keywordsJson)
}
