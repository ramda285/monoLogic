package com.example.monologic.bluesky

import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.ServerSocket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OAuth ループバックリダイレクト用の一時 HTTP サーバー（RFC 8252 §7.3）。
 *
 * ランダムポートで待機し、ブラウザが `http://127.0.0.1:{port}?code=...` に
 * リダイレクトしてきたら:
 *   1. ブラウザに「認証完了、タブを閉じてください」ページを返す
 *   2. コールバック URI をコルーチンに返してサーバーを閉じる
 *
 * 使い方:
 *   val server = LoopbackOAuthServer()
 *   val port   = server.start()
 *   // ブラウザに "http://127.0.0.1:$port" を redirect_uri として渡す
 *   val callbackUri = server.awaitCallback()   // コールバックが来るまで中断
 */
class LoopbackOAuthServer {

    private var serverSocket: ServerSocket? = null

    /** 実際に割り当てられたポート番号。start() 後に有効。 */
    val port: Int get() = serverSocket?.localPort ?: 0

    /** サーバーを起動してポート番号を返す。 */
    fun start(): Int {
        serverSocket = ServerSocket(0)   // OS がポートを選択
        return serverSocket!!.localPort
    }

    /**
     * ブラウザからのコールバックを待機して URI を返す。
     * コルーチンがキャンセルされた場合はサーバーも閉じる。
     */
    suspend fun awaitCallback(): Uri = suspendCancellableCoroutine { cont ->
        val server = serverSocket ?: run {
            cont.resumeWithException(IllegalStateException("Server not started"))
            return@suspendCancellableCoroutine
        }

        cont.invokeOnCancellation { runCatching { server.close() } }

        Thread {
            try {
                server.accept().also { it.soTimeout = 90_000 }.use { socket ->
                    // HTTP ヘッダーを空行まで全て読む（Chrome が応答待ちになるのを防ぐ）
                    val reader = socket.getInputStream().bufferedReader()
                    var requestLine = ""
                    var line = reader.readLine()
                    while (line != null) {
                        if (requestLine.isEmpty() && line.isNotEmpty()) requestLine = line
                        if (line.isEmpty()) break   // 空行 = ヘッダー終端
                        line = reader.readLine()
                    }
                    // "GET /callback?code=xxx&state=yyy HTTP/1.1" からパスを取り出す
                    val path = requestLine.split(" ").getOrElse(1) { "/callback" }
                    val callbackUri = Uri.parse("http://localhost:${server.localPort}$path")

                    // ブラウザに完了ページを返す
                    val html = """
                        <!DOCTYPE html><html><head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width,initial-scale=1">
                        <title>認証完了</title>
                        <style>body{font-family:sans-serif;text-align:center;padding:40px}</style>
                        </head><body>
                        <h2>✅ 認証完了</h2>
                        <p>このタブを閉じてアプリに戻ってください。</p>
                        <script>window.close();</script>
                        </body></html>
                    """.trimIndent()
                    val response = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/html; charset=utf-8\r\n")
                        append("Content-Length: ${html.toByteArray(Charsets.UTF_8).size}\r\n")
                        append("Connection: close\r\n\r\n")
                        append(html)
                    }
                    socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()

                    if (!cont.isCompleted) cont.resume(callbackUri)
                }
            } catch (e: Exception) {
                if (!cont.isCompleted) cont.resumeWithException(e)
            } finally {
                runCatching { server.close() }
            }
        }.start()
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}
