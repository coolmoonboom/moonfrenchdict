package com.coolmoonfrench.dict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 中文 → 法语的翻译辅助：默认优先使用 AI 设置中配置的大模型，未配置或失败时回退 MyMemory 免费翻译。
 * 供查词界面与句子界面在检测到中文输入时调用。
 */
object TranslationAssist {

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
        val config = aiConfig(prefs)
        if (config != null) {
            val reply = withContext(Dispatchers.IO) {
                try {
                    AIClient.chat(
                        config,
                        listOf(
                            AIMessage(
                                "user",
                                "请把下面的中文翻译成自然、地道的法语。" +
                                    "只输出法语译文，不要解释、不要引号、不要多余文字。\n中文：$text"
                            )
                        )
                    )
                } catch (_: Exception) {
                    null
                }
            }
            if (!reply.isNullOrBlank()) {
                return MyMemoryTranslator.TranslateResult(
                    translatedText = reply.trim(),
                    source = "AI(${config.modelName})"
                )
            }
        }
        return translator.translate(text, "zh-CN|fr")
    }
}
