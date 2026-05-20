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
    fun buildPostContent_format_and_facet_count() {
        val bsky = BlueskyClient(client)
        val (text, facets) = bsky.buildPostContent(
            "土星", "https://www.weblio.jp/content/%E5%9C%9F%E6%98%9F"
        )
        // テキスト形式の確認
        assertTrue(text.startsWith("#今日のお題：土星"))
        assertTrue(text.contains("https://www.weblio.jp/content/"))
        assertTrue(text.endsWith("#monoLogic"))
        // facet が3つ（#今日のお題、URL、#monoLogic）
        assertEquals(3, facets.size)
    }

    @Test
    fun buildPostContent_byte_offsets_are_correct() {
        val bsky = BlueskyClient(client)
        val word = "土星"
        val url = "https://www.weblio.jp/content/%E5%9C%9F%E6%98%9F"
        val (text, facets) = bsky.buildPostContent(word, url)

        fun String.byteLen() = toByteArray(Charsets.UTF_8).size

        // facet[0]: #今日のお題（ハッシュタグ）
        assertEquals(0, facets[0].index.byteStart)
        assertEquals("#今日のお題".byteLen(), facets[0].index.byteEnd)
        assertEquals("今日のお題", facets[0].features[0].tag)

        // facet[1]: URL（リンク）
        val urlStart = "#今日のお題：$word\n".byteLen()
        assertEquals(urlStart, facets[1].index.byteStart)
        assertEquals(urlStart + url.byteLen(), facets[1].index.byteEnd)
        assertEquals(url, facets[1].features[0].uri)

        // facet[2]: #monoLogic（ハッシュタグ）
        val tag2Start = "#今日のお題：$word\n$url\n".byteLen()
        assertEquals(tag2Start, facets[2].index.byteStart)
        assertEquals(tag2Start + "#monoLogic".byteLen(), facets[2].index.byteEnd)
        assertEquals("monoLogic", facets[2].features[0].tag)

        // テキスト全体のバイト長と最後の facet の終端が一致する
        assertEquals(text.byteLen(), facets[2].index.byteEnd)
    }

    @Test
    fun post_returns_null_when_record_creation_fails() = runTest {
        // Session succeeds, but createRecord fails
        server.enqueue(MockResponse().setBody(
            """{"accessJwt":"jwt123","did":"did:plc:abc"}"""
        ).setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(500))

        val bsky = BlueskyClient(client, baseUrl = server.url("/").toString())
        val uri = bsky.post("test.bsky.social", "app-pass-123", "土星",
            "https://www.weblio.jp/content/%E5%9C%9F%E6%98%9F")
        assertNull(uri)
    }
}
