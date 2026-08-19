package com.moonfrench.dict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

class BaiduTranslator {

    companion object {
        private const val BAIDU_SUG = "https://fanyi.baidu.com/sug"
        private const val TAG = "BaiduTranslator"
    }

    suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        try {
            val params = "kw=${URLEncoder.encode(text, "UTF-8")}"
            val conn = URL(BAIDU_SUG).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.outputStream.write(params.toByteArray())

            val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
            val response = reader.readText()
            reader.close()

            val json = JSONObject(response)
            val data = json.optJSONArray("data")
            if (data != null && data.length() > 0) {
                val v = data.getJSONObject(0).optString("v", "")
                // v format: "中文释义\n法文例句\n法文例句翻译"
                return@withContext v.split("\n").firstOrNull()?.trim() ?: v
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Baidu failed: ${e.message}")
        }
        return@withContext ""
    }
}