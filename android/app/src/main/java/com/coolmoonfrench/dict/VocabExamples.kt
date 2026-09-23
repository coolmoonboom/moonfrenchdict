package com.coolmoonfrench.dict

import android.content.Context
import org.json.JSONObject

/**
 * 背词例句：运行时用已配置的大模型生成，按法语词缓存于 SharedPreferences。
 * 未配置 AI 时静默缺席（背词功能不依赖它）。
 */
object VocabExamples {

    data class Example(val fr: String, val zh: String)

    private val CJK = Regex("[\\u4e00-\\u9fff]")

    private val lock = Any()
    private var memCache: MutableMap<String, Example>? = null

    fun isConfigured(context: Context): Boolean =
        IpaService.isConfigured(AIPreferences(context).modelConfig)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("vocab_examples", Context.MODE_PRIVATE)

    private fun cache(): MutableMap<String, Example> {
        memCache?.let { return it }
        synchronized(lock) {
            var m = memCache
            if (m == null) {
                m = HashMap()
                memCache = m
            }
            return m
        }
    }

    /** 同步读取缓存（内存 + 磁盘），无则 null */
    fun cached(context: Context, word: String): Example? {
        cache()[word]?.let { return it }
        val json = prefs(context).getString(word, null) ?: return null
        val e = try {
            val o = JSONObject(json)
            Example(o.optString("fr"), o.optString("zh"))
        } catch (_: Exception) {
            null
        } ?: return null
        if (e.fr.isBlank() || e.zh.isBlank()) return null
        cache()[word] = e
        return e
    }

    /** 请求 AI 生成例句（调用方负责放在协程里执行）；失败返回 null */
    suspend fun generate(context: Context, word: String): Example? {
        val app = context.applicationContext
        cached(app, word)?.let { return it }
        val config = AIPreferences(app).modelConfig
        if (!IpaService.isConfigured(config)) return null
        val prompt = "请用法语词 «$word» 造一个贴近日常生活的简短法语例句，难度不超过 A2-B1。" +
            "只输出两行：第一行法语例句，第二行例句的中文翻译。不要输出编号、解释或其他内容。"
        return try {
            val raw = AIClient.chat(config, listOf(AIMessage("user", prompt)))
            val lines = raw.lines().map { it.trim().trimStart('-', '•', ' ') }
                .filter { it.isNotEmpty() }
            val fr = lines.firstOrNull { !CJK.containsMatchIn(it) } ?: return null
            val zh = lines.firstOrNull { CJK.containsMatchIn(it) } ?: return null
            if (fr.isBlank() || zh.isBlank()) return null
            val e = Example(fr, zh)
            prefs(app).edit()
                .putString(word, JSONObject().put("fr", fr).put("zh", zh).toString())
                .apply()
            cache()[word] = e
            e
        } catch (_: Exception) {
            null
        }
    }
}
