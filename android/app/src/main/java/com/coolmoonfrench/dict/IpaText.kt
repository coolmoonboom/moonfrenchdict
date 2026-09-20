package com.coolmoonfrench.dict

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/**
 * 法语 IPA 音标服务：通过已配置的大模型生成标准音标，要求连诵/联诵用 ‿ 标注。
 * 结果按文本做内存缓存，避免重复请求。
 */
object IpaService {

    private val cache = ConcurrentHashMap<String, String>()

    private fun key(text: String): String =
        text.trim().lowercase().replace('’', '\'')

    /** 读取缓存（同步，供首帧直接显示） */
    fun cached(text: String): String? = cache[key(text)]

    fun isConfigured(config: AIModelConfig?): Boolean =
        config != null && config.apiUrl.isNotBlank() &&
            config.apiToken.isNotBlank() && config.modelName.isNotBlank()

    /** 获取音标；未配置或失败时返回 null。sentence 为 true 时按整句注音（保留词间空格与连诵）。 */
    suspend fun lookup(config: AIModelConfig, text: String, sentence: Boolean): String? {
        val k = key(text)
        if (k.isEmpty()) return null
        cache[k]?.let { return it }
        if (!isConfigured(config)) return null
        val reply = try {
            AIClient.chat(config, listOf(AIMessage("user", buildPrompt(text, sentence))))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        val ipa = parse(reply) ?: return null
        cache[k] = ipa
        return ipa
    }

    private fun buildPrompt(text: String, sentence: Boolean): String {
        val scope = if (sentence) "句子" else "单词或短语"
        return """
请为下面的法语${scope}标注标准 IPA 音标。
要求：
1. 只输出音标本身，用一对斜杠 /.../ 包裹，不要解释、不要换行、不要任何多余文字。
2. 连诵（liaison）与联诵处必须用连接符 ‿ 标注，例如：
   - d'eau → /d‿o/
   - l'application → /l‿aplikasjɔ̃/
   - les amis → /le‿zami/
   - vous avez → /vu‿zave/
3. 准确标注鼻化元音（ɑ̃ ɛ̃ ɔ̃ œ̃）、半元音（j ɥ w）、圆唇元音（y ø œ）与哑音 e（ə）。
4. 词与词之间保留空格。
文本：$text
""".trimIndent()
    }

    private fun parse(reply: String): String? {
        val cleaned = reply
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val first = cleaned.indexOf('/')
        val last = cleaned.lastIndexOf('/')
        if (first in 0 until last) {
            val inner = cleaned.substring(first + 1, last).trim()
            if (inner.isNotEmpty()) return "/$inner/"
        }
        val line = cleaned.lineSequence().firstOrNull()?.trim().orEmpty()
        return if (line.isNotEmpty() && line.length <= 160) "/$line/" else null
    }
}

/**
 * 音标行：自动按需向大模型请求并展示音标。
 * - target 为空则不渲染；
 * - 未配置大模型时给出提示；
 * - 请求失败时提示失败。
 */
@Composable
fun IpaLine(
    target: String,
    aiPrefs: AIPreferences?,
    sentence: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontSize: TextUnit = 13.sp,
    modifier: Modifier = Modifier
) {
    val key = target.trim()
    if (key.isEmpty()) return

    val config = aiPrefs?.modelConfig
    val configured = IpaService.isConfigured(config)

    var ipa by remember(key) { mutableStateOf(IpaService.cached(key)) }
    var loading by remember(key) { mutableStateOf(false) }
    var failed by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        if (ipa != null || !configured) return@LaunchedEffect
        loading = true
        failed = false
        val result = IpaService.lookup(config!!, key, sentence)
        loading = false
        if (result != null) ipa = result else failed = true
    }

    val display = when {
        ipa != null -> ipa!!
        loading -> "获取中…"
        failed -> "音标获取失败"
        !configured -> "配置 AI 后可显示音标"
        else -> null
    } ?: return

    Text(
        "音标 $display",
        color = textColor,
        fontSize = fontSize,
        modifier = modifier
    )
}
