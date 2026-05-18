package com.example.monologic

import com.example.monologic.data.db.TopicDao
import com.example.monologic.data.db.TopicEntity
import com.example.monologic.repository.TopicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TopicRepositoryTest {
    private val dao: TopicDao = mockk(relaxed = true)
    private val repo = TopicRepository(dao)

    private fun entity(date: String, word: String) = TopicEntity(
        date = date, word = word,
        weblioUrl = "https://www.weblio.jp/content/$word",
        blueskyPostUri = null, postedAt = 0L
    )

    @Test
    fun saveTopic_delegates_to_dao_upsert() = runTest {
        val e = entity("2026-05-18", "土星")
        repo.saveTopic(e)
        coVerify(exactly = 1) { dao.upsert(e) }
    }

    @Test
    fun getTodayTopic_returns_null_when_not_found() = runTest {
        coEvery { dao.getByDate(any()) } returns null
        assertNull(repo.getTodayTopic("2026-05-18"))
    }

    @Test
    fun getTodayTopic_returns_entity_when_found() = runTest {
        val e = entity("2026-05-18", "土星")
        coEvery { dao.getByDate("2026-05-18") } returns e
        assertEquals(e, repo.getTodayTopic("2026-05-18"))
    }

    @Test
    fun getAllFlow_emits_list_from_dao() = runTest {
        val list = listOf(entity("2026-05-18", "土星"), entity("2026-05-17", "海"))
        every { dao.getAll() } returns flowOf(list)
        val emitted = repo.getAllFlow().first()
        assertEquals(2, emitted.size)
    }
}
