package com.coolmoonfrench.dict

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 中文 → 法语的翻译辅助：**以速度为首要目标**。
 *
 * - AI 翻译：小输出上限 + 12 秒硬超时（默认 OkHttp 读超时高达 120 秒，是此前
 *   「一直在翻译」卡顿的根源）；返回含中文视为无效（模型复述/拒答的常见表现）；
 * - 结果按输入缓存，来回改词不重复请求；
 * - AI 未配置或失败时回退 MyMemory（自带 8 秒超时）。
 *
 * 更优的快路径在调用方：中文若能直接命中本地词典 zh 释义字段（[DictRepository.lookupByZh]），
 * 完全无需网络。
 */
object TranslationAssist {

    /** 翻译结果缓存（中文输入 → 结果），超限即清空避免无界增长。 */
    private val cache = ConcurrentHashMap<String, MyMemoryTranslator.TranslateResult>()
    private const val MAX_CACHE = 200

    private fun aiConfig(prefs: AIPreferences?): AIModelConfig? {
        val config = prefs?.modelConfig ?: return null
        return if (config.apiUrl.isNotBlank() && config.apiToken.isNotBlank() && config.modelName.isNotBlank()) {
            config
        } else null
    }

    /** 把中文翻译成法语。返回结果中的 translatedText 为法语，source 标注来源。 */
    suspend fun zhToFr(
        text: String,
        prefs: AIPreferences?,
        translator: MyMemoryTranslator
    ): MyMemoryTranslator.TranslateResult? {
        val key = text.trim()
        if (key.isEmpty()) return null
        cache[key]?.let { return it }
        val config = aiConfig(prefs)
        if (config != null) {
            val reply = withContext(Dispatchers.IO) {
                try {
                    AIClient.chat(
                        config,
                        listOf(
                            AIMessage(
                                "user",
                                "把下面的中文翻译成地道的法语。只输出法语译文本身；" +
                                    "中文词给出对应的法语单词或短语（附冠词），中文句子给出完整法语句。" +
                                    "不要解释、不要引号、不要中文。\n中文：$key"
                            )
                        ),
                        maxTokens = 80,
                        temperature = 0.2,
                        timeoutSeconds = 12
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            val fr = reply?.trim()
            // 只有「不含中文的拉丁文」才算有效译文；模型复述中文或被安全策略拦截时直接走兜底
            if (!fr.isNullOrBlank() && fr.none { it.code in 0x4E00..0x9FFF } &&
                fr.any { it.isLetter() }
            ) {
                val res = MyMemoryTranslator.TranslateResult(
                    translatedText = fr,
                    source = "AI(${config.modelName})"
                )
                if (cache.size > MAX_CACHE) cache.clear()
                cache[key] = res
                return res
            }
        }
        return translator.translate(key, "zh-CN|fr")
    }
}
