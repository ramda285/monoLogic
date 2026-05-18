package com.example.monologic.repository

import com.example.monologic.data.db.TopicDao
import com.example.monologic.data.db.TopicEntity
import kotlinx.coroutines.flow.Flow

class TopicRepository(private val dao: TopicDao) {

    // Phase 1: 保存・取得
    suspend fun saveTopic(entity: TopicEntity) = dao.upsert(entity)

    suspend fun getTodayTopic(date: String): TopicEntity? = dao.getByDate(date)

    /** LiveなFlowとして全お題を返す（MainActivityのRecyclerView更新に使用） */
    fun getAllFlow(): Flow<List<TopicEntity>> = dao.getAll()

    // Phase 2以降: リプライ収集（スタブ）
    suspend fun getUncollected(): List<TopicEntity> = dao.getUncollected()

    // Phase 3以降: AI分析（スタブ）
    suspend fun getUnanalyzed(): List<TopicEntity> = dao.getUnanalyzed()
}
