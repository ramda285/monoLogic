package com.example.monologic

import com.example.monologic.bluesky.BlueskyClient
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BlueskyClientTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    @Test
    fun post_returns_uri_on_success() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"accessJwt":"jwt123","did":"did:plc:abc"}"""
        ).setResponseCode(200))
        server.enqueue(MockResponse().setBody(
            """{"uri":"at://did:plc:abc/app.bsky.feed.post/xyz","cid":"cid123"}"""
        ).setResponseCode(200))

        val bsky = BlueskyClient(client, baseUrl = server.url("/").toString())
        val uri = bsky.post(
            handle = "test.bsky.social",
            appPassword = "app-pass-123",
            word = "土星",
            weblioUrl = "https://www.weblio.jp/content/%E5%9C%9F%E6%98%9F"
        )
        assertEquals("at://did:plc:abc/app.bsky.feed.post/xyz", uri)
    }

    @Test
    fun post_returns_null_when_session_fails() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val bsky = BlueskyClient(client, baseUrl = server.url("/").toString())
        val uri = bsky.post("test.bsky.social", "wrong", "土星",
            "https://www.weblio.jp/content/%E5%9C%9F%E6%98%9F")
        assertNull(uri)
    }

    @Test
    fun buildPostContent_includes_word_url_and_facet() {
        val bsky = BlueskyClient(client)
        val (text, facets) = bsky.buildPostContent(
            "土星", "https://www.weblio.jp/content/%E5%9C%9F%E6%98%9F"
        )
        assertTrue(text.contains("土星"))
        assertTrue(text.contains("https://www.weblio.jp/content/"))
        assertEquals(1, facets.size)
        assertTrue(facets[0].index.byteStart > 0)
        assertTrue(facets[0].index.byteEnd > facets[0].index.byteStart)
    }
}
