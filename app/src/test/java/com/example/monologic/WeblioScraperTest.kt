package com.example.monologic

import com.example.monologic.scraper.WeblioScraper
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WeblioScraperTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    @Test
    fun fetchRandomWord_parses_word_from_html() = runTest {
        val html = """
            <html><body>
              <div class="midashigo"><a href="/content/%E5%9C%9F%E6%98%9F">土星</a></div>
              <div class="midashigo"><a href="/content/%E6%B5%B7">海</a></div>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(html).setResponseCode(200))

        val scraper = WeblioScraper(client, baseUrl = server.url("/").toString())
        val result = scraper.fetchRandomWord()

        assertNotNull(result)
        assertTrue(result!!.word in listOf("土星", "海"))
        // URL should be the production weblio URL (not the mock server URL)
        assertTrue(result.url.startsWith("https://www.weblio.jp/content/"))
    }

    @Test
    fun fetchRandomWord_returns_fallback_on_empty_page() = runTest {
        server.enqueue(MockResponse().setBody("<html><body></body></html>").setResponseCode(200))
        val scraper = WeblioScraper(client, baseUrl = server.url("/").toString())
        val result = scraper.fetchRandomWord()
        assertNotNull(result)
        assertTrue(result!!.word.isNotEmpty())
        assertTrue(result.url.startsWith("https://www.weblio.jp/content/"))
    }

    @Test
    fun fetchRandomWord_returns_fallback_on_network_error() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val scraper = WeblioScraper(client, baseUrl = server.url("/").toString())
        val result = scraper.fetchRandomWord()
        assertNotNull(result)
        assertTrue(result!!.word.isNotEmpty())
    }
}
