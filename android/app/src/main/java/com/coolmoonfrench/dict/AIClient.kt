package com.coolmoonfrench.dict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AIClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** 拉取 OpenAI 兼容模型列表 */
    suspend fun listModels(config: AIModelConfig): List<String> = withContext(Dispatchers.IO) {
        val base = config.apiUrl.trim().trimEnd('/')
        val url = base.substringBeforeLast("/chat/completions").substringBeforeLast("/responses").substringBeforeLast("/messages").trimEnd('/')
        val reqUrl = if (url.endsWith("/models")) url else "$url/models"
        val request = Request.Builder()
            .url(reqUrl)
            .header("Authorization", "Bearer ${config.apiToken}")
            .get()
            .build()
        val resp = client.newCall(request).execute()
        if (!resp.isSuccessful) {
            val err = resp.body?.string()?.take(200) ?: "unknown"
            throw RuntimeException("HTTP ${resp.code}: $err")
        }
        val text = resp.body?.string() ?: throw RuntimeException("空响应")
        val data = JSONObject(text).optJSONArray("data") ?: JSONArray()
        val models = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val id = data.getJSONObject(i).optString("id")
            if (id.isNotEmpty()) models.add(id)
        }
        models
    }

    /** 发送对话消息，返回助手回复 */
    suspend fun chat(config: AIModelConfig, messages: List<AIMessage>, webContext: String? = null): String = withContext(Dispatchers.IO) {
        val base = config.apiUrl.trim().trimEnd('/')
        val bodyJson: String
        val url: String

        when (config.interfaceType) {
            AIInterfaceType.ANTHROPIC -> {
                url = base.substringBeforeLast("/messages").trimEnd('/') + "/messages"
                val arr = JSONArray()
                if (!webContext.isNullOrBlank()) {
                    arr.put(JSONObject().put("role", "user").put("content", buildWebContextPrompt(webContext)))
                }
                messages.forEach { m ->
                    arr.put(JSONObject().put("role", m.role).put("content", m.content))
                }
                bodyJson = JSONObject()
                    .put("model", config.modelName)
                    .put("max_tokens", 4096)
                    .put("messages", arr)
                    .toString()
            }
            AIInterfaceType.OPENAI_RESPONSES -> {
                url = base.substringBeforeLast("/responses").trimEnd('/') + "/responses"
                val input = JSONArray()
                if (!webContext.isNullOrBlank()) {
                    input.put(JSONObject().put("role", "system").put("content", buildWebContextPrompt(webContext)))
                }
                messages.forEach { m ->
                    input.put(JSONObject().put("role", m.role).put("content", m.content))
                }
                bodyJson = JSONObject()
                    .put("model", config.modelName)
                    .put("input", input)
                    .toString()
            }
            else -> { // openai_chat
                url = base.substringBeforeLast("/chat/completions").trimEnd('/') + "/chat/completions"
                val arr = JSONArray()
                arr.put(JSONObject().put("role", "system").put("content", "你是一个法语学习助手，帮助用户查词、解答语法、翻译。涉及法语时给出法语原词并附中文。"))
                if (!webContext.isNullOrBlank()) {
                    arr.put(JSONObject().put("role", "system").put("content", buildWebContextPrompt(webContext)))
                }
                messages.forEach { m ->
                    arr.put(JSONObject().put("role", m.role).put("content", m.content))
                }
                bodyJson = JSONObject()
                    .put("model", config.modelName)
                    .put("messages", arr)
                    .put("temperature", 0.7)
                    .toString()
            }
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(jsonMedia))
        when (config.interfaceType) {
            AIInterfaceType.ANTHROPIC -> {
                reqBuilder.header("x-api-key", config.apiToken)
                reqBuilder.header("anthropic-version", "2023-06-01")
            }
            else -> reqBuilder.header("Authorization", "Bearer ${config.apiToken}")
        }

        val resp = client.newCall(reqBuilder.build()).execute()
        val text = resp.body?.string() ?: ""
        if (!resp.isSuccessful) {
            throw RuntimeException("HTTP ${resp.code}: ${text.take(300)}")
        }
        parseReply(config.interfaceType, text)
    }

    private fun buildWebContextPrompt(webContext: String): String {
        return "以下是通过联网搜索获得的网页内容摘要，请结合这些信息回答用户的提问。若信息不足请如实说明。搜索资料：\n$webContext"
    }

    private fun parseReply(interfaceType: String, text: String): String {
        val json = JSONObject(text).takeIf { it.length() > 0 } ?: return text
        if (json.has("error")) {
            throw RuntimeException(json.getJSONObject("error").optString("message", "api 返回错误"))
        }
        return when (interfaceType) {
            AIInterfaceType.ANTHROPIC -> {
                val content = json.optJSONArray("content")
                if (content != null && content.length() > 0) {
                    content.getJSONObject(0).optString("text", "")
                } else json.optString("text", "")
            }
            AIInterfaceType.OPENAI_RESPONSES -> {
                val output = json.optJSONArray("output")
                if (output != null && output.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until output.length()) {
                        val o = output.getJSONObject(i)
                        if (o.optString("type") == "message") {
                            val c = o.optJSONArray("content")
                            if (c != null) {
                                for (j in 0 until c.length()) {
                                    sb.append(c.getJSONObject(j).optString("text", ""))
                                }
                            }
                        }
                    }
                    sb.toString().ifEmpty {
                        output.getJSONObject(0).optString("text", "")
                    }
                } else {
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        choices.getJSONObject(0).optJSONObject("message")?.optString("content", "")
                            ?: choices.getJSONObject(0).optString("text", "")
                    } else json.optString("output_text", "")
                }
            }
            else -> { // openai_chat
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0).optJSONObject("message")?.optString("content", "")
                        ?: choices.getJSONObject(0).optString("text", "")
                } else json.optString("output_text", "")
            }
        }
    }
}