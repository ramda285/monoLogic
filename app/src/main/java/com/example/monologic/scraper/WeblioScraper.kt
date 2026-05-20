package com.example.monologic.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

data class WeblioResult(val word: String, val url: String)

class WeblioScraper(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://www.weblio.jp"
) {
    // Weblio の /category/{slug1}-{slug2} URL に使うひらがな→スラグ対応表
    private val hiraganaToSlug = mapOf(
        "あ" to "aa",  "い" to "ii",  "う" to "uu",  "え" to "ee",  "お" to "oo",
        "か" to "ka",  "き" to "ki",  "く" to "ku",  "け" to "ke",  "こ" to "ko",
        "さ" to "sa",  "し" to "shi", "す" to "su",  "せ" to "se",  "そ" to "so",
        "た" to "ta",  "ち" to "chi", "つ" to "tsu", "て" to "te",  "と" to "to",
        "な" to "na",  "に" to "ni",  "ぬ" to "nu",  "ね" to "ne",  "の" to "no",
        "は" to "ha",  "ひ" to "hi",  "ふ" to "fu",  "へ" to "he",  "ほ" to "ho",
        "ま" to "ma",  "み" to "mi",  "む" to "mu",  "め" to "me",  "も" to "mo",
        "や" to "ya",  "ゆ" to "yu",  "よ" to "yo",
        "ら" to "ra",  "り" to "ri",  "る" to "ru",  "れ" to "re",  "ろ" to "ro",
        "わ" to "wa"
    )

    private val hiraganaList = hiraganaToSlug.keys.toList()

    // フォールバック用内蔵単語リスト（Weblio取得失敗時に使用）
    private val fallbackWords = listOf(
        "土星","いかの刺身","蜃気楼","万華鏡","北極星",
        "ランドセル","砂時計","夜光虫","信号機","朝露",
        "ひまわり","地平線","彗星","風見鶏","水族館",
        "三日月","羅針盤","天の川","観覧車","日時計",
        "迷路","オーロラ","鍾乳洞","磁石","星座",
        "山彦","春雨","木漏れ日","霜柱","稲妻",
        "廃墟","クレーター","浮き輪","烽火","暦",
        "海溝","蛍光灯","桜前線","虹彩","黒板消し",
        "コンパス","蛍","宝石箱","風船","砂漠",
        "氷山","鉛筆","迷子","羊雲","薄明光線"
    )

    suspend fun fetchRandomWord(): WeblioResult? = withContext(Dispatchers.IO) {
        try {
            val slug1 = hiraganaToSlug[hiraganaList.random()]!!
            val slug2 = hiraganaToSlug[hiraganaList.random()]!!
            val url = "$baseUrl/category/$slug1-$slug2"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            } ?: return@withContext fallback()

            // href に "/content/" を含むリンクのみを単語エントリとして抽出
            val words = Jsoup.parse(body)
                .select("li > a[href*='/content/']")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }

            if (words.isEmpty()) fallback() else {
                val word = words.random()
                WeblioResult(
                    word = word,
                    url = "https://www.weblio.jp/content/${URLEncoder.encode(word, "UTF-8")}"
                )
            }
        } catch (e: Exception) {
            fallback()
        }
    }

    private fun fallback(): WeblioResult {
        val word = fallbackWords.random()
        // baseUrl はテスト用モックサーバ URL の場合がある。フォールバック時は常に本番 URL を使用
        return WeblioResult(
            word = word,
            url = "https://www.weblio.jp/content/${URLEncoder.encode(word, "UTF-8")}"
        )
    }
}
