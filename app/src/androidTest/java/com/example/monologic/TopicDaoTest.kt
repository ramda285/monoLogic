package com.example.monologic

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.monologic.data.db.AppDatabase
import com.example.monologic.data.db.TopicEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopicDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
    }

    @After fun tearDown() = db.close()

    @Test
    fun upsert_and_getByDate_returns_entity() = runTest {
        val entity = TopicEntity(
            date = "2026-05-18", word = "土星",
            weblioUrl = "https://www.weblio.jp/content/%E5%9C%9F%E6%98%9F",
            blueskyPostUri = "at://did:plc:abc/app.bsky.feed.post/xyz",
            postedAt = 1716000000000L
        )
        db.topicDao().upsert(entity)
        val result = db.topicDao().getByDate("2026-05-18")
        assertNotNull(result)
        assertEquals("土星", result!!.word)
        assertEquals("at://did:plc:abc/app.bsky.feed.post/xyz", result.blueskyPostUri)
    }

    @Test
    fun upsert_replaces_existing_entry() = runTest {
        val original = TopicEntity(
            date = "2026-05-18", word = "土星",
            weblioUrl = "https://www.weblio.jp/content/%E5%9C%9F%E6%98%9F",
            blueskyPostUri = null, postedAt = 1716000000000L
        )
        db.topicDao().upsert(original)
        db.topicDao().upsert(original.copy(blueskyPostUri = "at://did:plc:abc/app.bsky.feed.post/xyz"))
        val result = db.topicDao().getByDate("2026-05-18")
        assertEquals("at://did:plc:abc/app.bsky.feed.post/xyz", result!!.blueskyPostUri)
    }

    @Test
    fun getUncollected_returns_only_posted_without_reply() = runTest {
        db.topicDao().upsert(TopicEntity("2026-05-16", "土星",
            "https://weblio", "at://uri1", 0L, replyText = "replied"))
        db.topicDao().upsert(TopicEntity("2026-05-17", "海",
            "https://weblio", "at://uri2", 0L))
        db.topicDao().upsert(TopicEntity("2026-05-18", "山",
            "https://weblio", blueskyPostUri = null, postedAt = 0L))
        val result = db.topicDao().getUncollected()
        assertEquals(1, result.size)
        assertEquals("海", result[0].word)
    }
}
