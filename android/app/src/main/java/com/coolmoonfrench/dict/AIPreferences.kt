package com.coolmoonfrench.dict

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 大模型接口格式 */
object AIInterfaceType {
    const val OPENAI_CHAT = "openai_chat"
    const val OPENAI_RESPONSES = "openai_responses"
    const val ANTHROPIC = "anthropic"

    val OPTIONS = listOf(
        "OpenAI Chat" to OPENAI_CHAT,
        "OpenAI Responses" to OPENAI_RESPONSES,
        "Anthropic" to ANTHROPIC
    )
}

/** AI 模型配置 */
data class AIModelConfig(
    val interfaceType: String = AIInterfaceType.OPENAI_CHAT,
    val apiUrl: String = "",
    val apiToken: String = "",
    val modelName: String = "",
    val notes: String = ""
)

/** 对话附件 */
data class AIAttachment(
    val type: String,          // image / pdf / docx / pptx / txt
    val name: String,          // 文件名
    val localPath: String,     // 本地 URI 字符串
    val extractedText: String  // 提取出的文本内容（OCR/PDF/DOCX/PPXT/TXT 的文字）
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("name", name)
        put("path", localPath)
        put("text", extractedText)
    }

    companion object {
        fun fromJson(o: JSONObject): AIAttachment = AIAttachment(
            type = o.optString("type", ""),
            name = o.optString("name", ""),
            localPath = o.optString("path", ""),
            extractedText = o.optString("text", "")
        )
    }
}

/** 对话消息 */
data class AIMessage(
    val role: String, // user | assistant
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<AIAttachment> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("role", role)
        put("content", content)
        put("ts", timestamp)
        if (attachments.isNotEmpty()) {
            val arr = JSONArray()
            attachments.forEach { arr.put(it.toJson()) }
            put("attachments", arr)
        }
    }

    companion object {
        fun fromJson(o: JSONObject): AIMessage = AIMessage(
            role = o.optString("role", "user"),
            content = o.optString("content", ""),
            timestamp = o.optLong("ts", System.currentTimeMillis()),
            attachments = o.optJSONArray("attachments")?.let { arr ->
                (0 until arr.length()).map { AIAttachment.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList()
        )
    }
}

/** AI 会话（豆包式多会话） */
data class AIConversation(
    val id: Long,
    val title: String,
    val messages: List<AIMessage>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        val arr = JSONArray()
        messages.forEach { arr.put(it.toJson()) }
        put("messages", arr)
    }

    companion object {
        fun fromJson(o: JSONObject): AIConversation = AIConversation(
            id = o.optLong("id", System.currentTimeMillis()),
            title = o.optString("title", ""),
            messages = o.optJSONArray("messages")?.let { arr ->
                (0 until arr.length()).map { AIMessage.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList()
        )
    }
}

/** AI 收藏的消息 */
data class AIFavorite(    val id: Long,
    val role: String,
    val content: String,
    val timestamp: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role)
        put("content", content)
        put("ts", timestamp)
    }

    companion object {
        fun fromJson(o: JSONObject): AIFavorite = AIFavorite(
            id = o.optLong("id", System.currentTimeMillis()),
            role = o.optString("role", "assistant"),
            content = o.optString("content", ""),
            timestamp = o.optLong("ts", System.currentTimeMillis())
        )
    }
}

/** 收藏的法语句子（带翻译） */
data class SavedSentence(
    val sentence: String,
    val translation: String,
    val timestamp: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sentence", sentence)
        put("translation", translation)
        put("ts", timestamp)
    }

    companion object {
        fun fromJson(o: JSONObject): SavedSentence = SavedSentence(
            sentence = o.optString("sentence", ""),
            translation = o.optString("translation", ""),
            timestamp = o.optLong("ts", System.currentTimeMillis())
        )
    }
}

/**
 * AI 相关持久化：
 * - 模型配置
 * - 对话记录（保留最近 100 条）
 * - AI 收藏
 * - 句子收藏
 */
class AIPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    // ---------- 模型配置 ----------
    var modelConfig: AIModelConfig
        get() {
            val json = prefs.getString("model_config", null) ?: return AIModelConfig()
            return try {
                val o = JSONObject(json)
                AIModelConfig(
                    interfaceType = o.optString("interface_type", AIInterfaceType.OPENAI_CHAT),
                    apiUrl = o.optString("api_url", ""),
                    apiToken = o.optString("api_token", ""),
                    modelName = o.optString("model_name", ""),
                    notes = o.optString("notes", "")
                )
            } catch (e: Exception) {
                AIModelConfig()
            }
        }
        set(value) {
            prefs.edit().putString(
                "model_config",
                JSONObject().apply {
                    put("interface_type", value.interfaceType)
                    put("api_url", value.apiUrl)
                    put("api_token", value.apiToken)
                    put("model_name", value.modelName)
                    put("notes", value.notes)
                }.toString()
            ).apply()
        }

    // ---------- 对话记录（多会话） ----------

    private fun conversationsKey() = "ai_conversations"
    private fun activeKey() = "active_conversation"

    /** 读取全部会话（新→旧） */
    fun loadConversations(): List<AIConversation> {
        // 迁移旧数据：老版单会话 chat_messages → 首个会话
        val legacy = prefs.getString("chat_messages", null)
        val json = prefs.getString(conversationsKey(), null)
        if (json == null && legacy != null) {
            val list = mutableListOf<AIConversation>()
            try {
                val arr = JSONArray(legacy)
                val msgs = (0 until arr.length()).map { AIMessage.fromJson(arr.getJSONObject(it)) }
                if (msgs.isNotEmpty()) {
                    list.add(AIConversation(
                        id = System.currentTimeMillis(),
                        title = msgs.firstOrNull { it.role == "user" }?.content?.take(30) ?: "对话",
                        messages = msgs
                    ))
                }
            } catch (_: Exception) { }
            saveConversations(list)
            prefs.edit().remove("chat_messages").apply()
            if (list.isNotEmpty()) setActiveConversationId(list.first().id)
            return list
        }
        if (json == null) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { AIConversation.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveConversations(conversations: List<AIConversation>) {
        val arr = JSONArray()
        conversations.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(conversationsKey(), arr.toString()).apply()
    }

    /** 当前会话 id；无则返回 -1 */
    fun activeConversationId(): Long {
        return prefs.getLong(activeKey(), -1L)
    }

    fun setActiveConversationId(id: Long) {
        prefs.edit().putLong(activeKey(), id).apply()
    }

    /** 读取当前会话消息 */
    fun loadMessages(): List<AIMessage> {
        val id = activeConversationId()
        if (id < 0) return emptyList()
        return loadConversations().firstOrNull { it.id == id }?.messages ?: emptyList()
    }

    /** 保存当前会话消息 */
    fun saveMessages(messages: List<AIMessage>) {
        val id = activeConversationId()
        if (id < 0) return
        val list = loadConversations().map {
            if (it.id == id) it.copy(messages = messages.takeLast(100)) else it
        }
        saveConversations(list)
    }

    /** 新建会话并设为当前，返回新 id（保证不与现有会话 id 冲突） */
    private var idCounter = 0L

    fun newConversation(): Long {
        idCounter = (idCounter + 1) % 10000
        val list = loadConversations().toMutableList()
        var id = System.currentTimeMillis() * 10000 + idCounter
        while (list.any { it.id == id }) id++
        list.add(0, AIConversation(id, "新对话", emptyList()))
        saveConversations(list)
        setActiveConversationId(id)
        return id
    }

    /**
     * 删除会话；若删除的是当前会话则自动切到最近的会话，返回新的当前会话 id。
     * 若删空，自动新建一个空会话，保证永远有一个可用的当前会话。
     */
    fun deleteConversation(id: Long): Long {
        val list = loadConversations().filterNot { it.id == id }
        saveConversations(list)
        if (activeConversationId() == id) {
            if (list.isEmpty()) {
                return newConversation()
            }
            val next = list.first().id
            setActiveConversationId(next)
            return next
        }
        return activeConversationId()
    }

    fun clearMessages() {
        // 兼容方法：清空当前会话
        val id = activeConversationId()
        if (id < 0) return
        val list = loadConversations().map {
            if (it.id == id) it.copy(messages = emptyList()) else it
        }
        saveConversations(list)
    }

    // ---------- AI 收藏 ----------
    fun loadAIFavorites(): List<AIFavorite> {
        val json = prefs.getString("ai_favorites", "[]") ?: "[]"
        val result = mutableListOf<AIFavorite>()
        return try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) result.add(AIFavorite.fromJson(arr.getJSONObject(i)))
            result
        } catch (e: Exception) {
            result
        }
    }

    fun isAIFavorite(content: String): Boolean = loadAIFavorites().any { it.content == content }

    fun addAIFavorite(message: AIMessage): Boolean {
        val list = loadAIFavorites().toMutableList()
        if (list.any { it.content == message.content }) return false
        var fid = System.currentTimeMillis()
        while (list.any { it.id == fid }) fid++
        list.add(0, AIFavorite(fid, message.role, message.content, message.timestamp))
        saveAIFavorites(list)
        return true
    }

    fun removeAIFavorite(id: Long) {
        saveAIFavorites(loadAIFavorites().filterNot { it.id == id })
    }

    fun removeAIFavoriteByContent(content: String) {
        saveAIFavorites(loadAIFavorites().filterNot { it.content == content })
    }

    fun getAIFavorite(id: Long): AIFavorite? =
        loadAIFavorites().firstOrNull { it.id == id }

    fun updateAIFavorite(id: Long, newContent: String) {
        val list = loadAIFavorites().map {
            if (it.id == id) it.copy(content = newContent, timestamp = System.currentTimeMillis()) else it
        }
        saveAIFavorites(list)
    }

    fun getImageDir(context: Context): File =
        File(context.filesDir, "ai_fav_images").apply { if (!exists()) mkdirs() }

    fun copyImageToStorage(context: Context, uri: android.net.Uri): String? {
        return try {
            val dir = getImageDir(context)
            val name = "img_${System.currentTimeMillis()}.jpg"
            val dest = File(dir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun saveAIFavorites(list: List<AIFavorite>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("ai_favorites", arr.toString()).apply()
    }

    // ---------- 句子收藏 ----------
    fun loadSentenceFavorites(): List<SavedSentence> {
        val json = prefs.getString("sentence_favorites", "[]") ?: "[]"
        val result = mutableListOf<SavedSentence>()
        return try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) result.add(SavedSentence.fromJson(arr.getJSONObject(i)))
            result
        } catch (e: Exception) {
            result
        }
    }

    fun isSentenceFavorite(sentence: String): Boolean =
        loadSentenceFavorites().any { it.sentence == sentence }

    fun addSentenceFavorite(sentence: String, translation: String): Boolean {
        val list = loadSentenceFavorites().toMutableList()
        if (list.any { it.sentence == sentence }) return false
        list.add(0, SavedSentence(sentence, translation, System.currentTimeMillis()))
        saveSentenceFavorites(list)
        return true
    }

    fun removeSentenceFavorite(sentence: String) {
        saveSentenceFavorites(loadSentenceFavorites().filterNot { it.sentence == sentence })
    }

    private fun saveSentenceFavorites(list: List<SavedSentence>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("sentence_favorites", arr.toString()).apply()
    }
}