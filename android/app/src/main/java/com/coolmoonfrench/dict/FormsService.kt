package com.coolmoonfrench.dict

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 「带冠词 / 名词化」形式的 AI 生成服务。
 *
 * 一次调用即可拿到某个法语词的常用冠词形式（名词）或名词化形式（动词）及带连诵的音标，
 * 结果按「词头 + 词性」缓存。AI 未配置或失败时由调用方回退到 [WordForms]。
 */
object FormsService {

    private val cache = ConcurrentHashMap<String, List<WordForm>>()

    private fun key(word: String, pos: String) =
        word.trim().lowercase().replace('’', '\'') + "|" + pos.trim().lowercase()

    /** 同步读取缓存，供首帧直接显示。 */
    fun cached(word: String, pos: String): List<WordForm>? = cache[key(word, pos)]

    suspend fun lookup(config: AIModelConfig, word: String, pos: String): List<WordForm> {
        val w = word.trim()
        if (w.isEmpty()) return emptyList()
        val k = key(w, pos)
        cache[k]?.let { return it }
        if (!IpaService.isConfigured(config)) return emptyList()
        val reply = try {
            AIClient.chat(
                config,
                listOf(AIMessage("user", buildPrompt(w, pos))),
                maxTokens = 700,
                temperature = 0.2,
                timeoutSeconds = 45
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }
        val forms = parse(reply)
        if (forms.isNotEmpty()) cache[k] = forms
        return forms
    }

    private fun buildPrompt(word: String, pos: String): String = """
        你是法语语法助手。为下面的法语词给出「带冠词 / 名词化」的常用形式，只输出一个 JSON 数组，不要解释、不要代码块：
        [{"text":"le jardin","ipa":"/lə ʒaʁdɛ̃/"},{"text":"du jardin","ipa":"/dy ʒaʁdɛ̃/"}]
        要求：
        1. 名词：依次给出定冠词、不定冠词、部分冠词的单数形式，以及定冠词、不定冠词的复数形式（le/la/l'、un/une、du/de la/de l'、les、des）。注意省音、缩合与复数拼写（exclusivité → l'exclusivité / de l'exclusivité / les exclusivités / des exclusivités）。
        2. 动词：给出其名词化形式的带冠词写法（parler → le parlé / du parlé / des parlé）。
        3. 其他词性：给出该词最常见的搭配形式；实在没有就返回空数组 []。
        4. ipa 用 /.../ 包裹，省音与连诵/联诵处必须用 ‿ 标注（exclusivité → /l‿ɛksklyzivite/，les amis → /le‿zami/，d'un échec → /d‿œ̃.e.ʃɛk/）。
        5. 每个词给出 3-6 个最常用的形式即可，不要罗列全部。
        词：$word（词性：${pos.ifBlank { "未知" }}）
    """.trimIndent()

    /** 兼容 JSON 数组（对象元素或纯字符串元素）。 */
    internal fun parse(reply: String): List<WordForm> {
        val cleaned = reply.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        return try {
            val arr = JSONArray(cleaned.substring(start, end + 1))
            val out = mutableListOf<WordForm>()
            for (i in 0 until arr.length()) {
                when (val el = arr.opt(i)) {
                    is JSONObject -> {
                        val text = el.optString("text", "").trim()
                            .ifBlank { el.optString("word", "").trim() }
                        if (text.isNotEmpty()) out += WordForm(text, stripIpa(el.optString("ipa", "")))
                    }
                    is String -> {
                        val text = el.trim()
                        if (text.isNotEmpty()) out += WordForm(text, "")
                    }
                }
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun stripIpa(raw: String): String = raw.trim().removeSurrounding("/").trim()
}
