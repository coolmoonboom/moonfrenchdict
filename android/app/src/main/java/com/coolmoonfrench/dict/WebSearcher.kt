package com.coolmoonfrench.dict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WebResult(
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * 轻量联网搜索：使用 DuckDuckGo HTML 端点（无需 API Key）。
 * 返回搜索结果片段，供 AI 作为上下文使用。
 */
object WebSearcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    /** 搜索并返回最多 maxResults 条结果片段。失败时返回空列表。 */
    suspend fun search(query: String, maxResults: Int = 5): List<WebResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://www.bing.com/search?q=$encoded&setlang=en")
                .header("User-Agent", UA)
                .get()
                .build()
            val resp = client.newCall(request).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return@withContext emptyList()
            }
            val html = resp.body?.string() ?: return@withContext emptyList()
            parseResults(html, maxResults)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 解析 Bing 搜索结果 */
    private fun parseResults(html: String, maxResults: Int): List<WebResult> {
        val results = mutableListOf<WebResult>()
        val blocks = Regex("<li class=\"b_algo\".*?</li>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
        for (m in blocks) {
            if (results.size >= maxResults) break
            val block = m.value
            val url = Regex("<a[^>]+href=\"([^\"]+)\"").find(block)?.groupValues?.get(1) ?: continue
            val title = Regex("<h2[^>]*>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)?.let { stripTags(it).trim() } ?: continue
            val snippet = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)?.let { stripTags(it).trim() } ?: ""
            results.add(WebResult(title, url, snippet))
        }
        return results
    }

    private fun stripTags(s: String): String {
        return s.replace(Regex("<[^>]+>"), "").replace("&amp;", "&")
            .replace("&quot;", "\"").replace("&#x27;", "'").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&#0183;", "·").replace("&ensp;", " ").trim()
    }
}
