package com.coolmoonfrench.dict

import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** AI 在线查词结果：单词（原形）、IPA 音标、中文释义。 */
data class AiWordInfo(
    val word: String,
    val ipa: String,
    val meaning: String
)

/**
 * 用已配置的大模型查词：返回「单词 + 音标 + 中文释义」三要素。
 * 用于变位引擎无法处理或本地词典无中文释义的生僻动词。
 */
object AiWordSearch {

    suspend fun search(config: AIModelConfig, word: String): AiWordInfo? {
        val w = word.trim()
        if (w.isEmpty()) return null
        if (!IpaService.isConfigured(config)) return null
        val reply = try {
            AIClient.chat(config, listOf(AIMessage("user", buildPrompt(w))))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return parse(reply, w)
    }

    private fun buildPrompt(word: String): String = """
        你是法语词典助手。请查询法语单词「$word」的中文释义与标准 IPA 音标。
        要求：
        1. 只输出一个 JSON 对象，不要任何解释，不要代码块标记。
        2. JSON 格式：{"word":"单词原形","ipa":"/音标/","meaning":"简洁中文释义（标注词性）"}
        3. ipa 用一对斜杠包裹；若为动词，word 填不定式。
        4. word 必须保留法语重音符号（é è ê à ç î ô û ù 等），禁止写成无重音形式。
        5. 若该词不是法语词，meaning 填「未找到该词的释义」。
    """.trimIndent()

    private fun parse(reply: String, fallbackWord: String): AiWordInfo? {
        val cleaned = reply.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val o = JSONObject(cleaned.substring(start, end + 1))
            val word = o.optString("word", fallbackWord).ifBlank { fallbackWord }
            val ipa = o.optString("ipa", "").trim()
            val meaning = o.optString("meaning", "").trim()
            if (meaning.isEmpty() && ipa.isEmpty()) return null
            AiWordInfo(word, ipa, meaning)
        } catch (e: Exception) {
            null
        }
    }
}
