package com.coolmoonfrench.dict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * MyMemory 在线翻译。图1/图3 界面均显示 "MyMemory (联网)" 来源。
 * GET https://api.mymemory.translated.net/get?q=<text>&langpair=fr|zh-CN
 */
class MyMemoryTranslator {

    companion object {
        private const val API = "https://api.mymemory.translated.net/get"
        private const val TAG = "MyMemoryTranslator"
        private const val SOURCE = "MyMemory"
    }

    data class TranslateResult(
        val translatedText: String,
        val source: String = SOURCE
    )

    suspend fun translate(text: String): TranslateResult? = translate(text, "fr|zh-CN")

    suspend fun translate(text: String, langpair: String): TranslateResult? = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(text, "UTF-8")
            val url = URL("$API?q=$q&langpair=$langpair")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
            val response = reader.readText()
            reader.close()
            conn.disconnect()

            val json = JSONObject(response)
            val status = json.optInt("responseStatus", -1)
            if (status != 200) return@withContext null
            val data = json.optJSONObject("responseData")
            val text = data?.optString("translatedText", "")?.trim() ?: ""
            if (text.isNotEmpty()) {
                return@withContext TranslateResult(text)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "MyMemory failed: ${e.message}")
        }
        return@withContext null
    }
}
