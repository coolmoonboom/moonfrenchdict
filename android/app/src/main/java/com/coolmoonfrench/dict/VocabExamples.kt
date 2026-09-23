package com.coolmoonfrench.dict

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/**
 * 背词例句：来自离线资产 assets/vocab/examples.json（Tatoeba 法汉句对，CC-BY 2.0 FR）。
 *
 * 完全本地化，不再调用任何大模型，因此不受 AI 速率限制影响；未收录的词静默缺席
 * （背词功能不依赖它）。资产在首次使用时懒加载并进程内缓存。
 */
object VocabExamples {

    data class Example(val fr: String, val zh: String)

    private val cache = AtomicReference<Map<String, Example>?>(null)

    /** 例句数据来源说明（用于设置页致谢）。 */
    const val ATTRIBUTION = "背词例句来自 Tatoeba（CC-BY 2.0 FR，https://tatoeba.org）"

    private fun all(context: Context): Map<String, Example> {
        cache.get()?.let { return it }
        synchronized(this) {
            cache.get()?.let { return it }
            val map = try {
                val text = context.assets.open("vocab/examples.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val obj = JSONObject(text).optJSONObject("examples") ?: JSONObject()
                val out = HashMap<String, Example>(obj.length())
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val w = keys.next()
                    val arr = obj.optJSONArray(w) ?: continue
                    val fr = arr.optString(0)
                    val zh = arr.optString(1)
                    if (fr.isNotBlank() && zh.isNotBlank()) out[w] = Example(fr, zh)
                }
                out
            } catch (_: Exception) {
                emptyMap()
            }
            cache.set(map)
            return map
        }
    }

    /** 取该词的本地例句；建议在 IO 线程调用（首次会解析资产）。 */
    fun lookup(context: Context, word: String): Example? =
        if (word.isBlank()) null else all(context)[word]
}
