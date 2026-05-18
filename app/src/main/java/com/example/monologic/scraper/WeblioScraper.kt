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
    private val hiraganaList = listOf(
        "あ","い","う","え","お","か","き","く","け","こ",
        "さ","し","す","せ","そ","た","ち","つ","て","と",
        "な","に","ぬ","ね","の","は","ひ","ふ","へ","ほ",
        "ま","み","む","め","も","や","ゆ","よ",
        "ら","り","る","れ","ろ","わ"
    )

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
            val char = hiraganaList.random()
            // ※ 実装時にWeblioの実際のHTML構造を確認してURLとCSSセレクタを調整すること
            val url = "$baseUrl/cat/dictionary/jtdjn/$char"
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            } ?: return@withContext fallback()
            val words = Jsoup.parse(body)
                .select(".midashigo a")
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
        // baseUrlはテスト用のモックサーバURL。フォールバック時は常に本番Weblio URLを使用する
        return WeblioResult(
            word = word,
            url = "https://www.weblio.jp/content/${URLEncoder.encode(word, "UTF-8")}"
        )
    }
}
