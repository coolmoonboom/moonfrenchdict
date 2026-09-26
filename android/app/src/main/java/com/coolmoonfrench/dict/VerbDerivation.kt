package com.coolmoonfrench.dict

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** 一个派生形式：词形 + 构词标签 + 中文义 + 语体制备注 + 可选例句。 */
data class DerivativeEntry(
    val form: String = "",
    val label: String = "",
    val zh: String = "",
    val note: String = "",
    val exampleFr: String = "",
    val exampleZh: String = ""
)

/** AI 生成的「动词派生全景」：分词 / 复合时态 / 动作名词 / 施动者 / 形容词 / 副词 / 补充说明。 */
data class VerbDerivationResult(
    val verb: String = "",
    val participePresent: String = "",
    val gerondif: String = "",
    val ppMasculin: String = "",
    val ppFeminin: String = "",
    val ppMasculinPluriel: String = "",
    val ppFemininPluriel: String = "",
    val auxiliaire: String = "",
    val exempleComposeFr: String = "",
    val exempleComposeZh: String = "",
    val exemplePqpFr: String = "",
    val exemplePqpZh: String = "",
    val nomsAction: List<DerivativeEntry> = emptyList(),
    val nomsAgent: List<DerivativeEntry> = emptyList(),
    val adjectifs: List<DerivativeEntry> = emptyList(),
    val adverbes: List<DerivativeEntry> = emptyList(),
    val notes: String = ""
)

/**
 * 用已配置的大模型生成「动词 → 分词 / 名词 / 形容词 / 副词」派生清单。
 * 提示词内嵌用户的教学口径：动作名词 vs 「le + 动词原形」古体的对比、-ant 令人… vs
 * 过去分词 感到… 的对立、过去分词做形容词的 le/la/les 性数配合等，要求每次输出都带上。
 */
object VerbDerivation {

    private val cache = HashMap<String, VerbDerivationResult>()

    /** 生成（带进程内缓存）；未配置 AI / 调用失败 / 解析失败返回 null。 */
    suspend fun generate(config: AIModelConfig, verb: String): VerbDerivationResult? {
        val key = verb.trim().lowercase()
        if (key.isEmpty() || !IpaService.isConfigured(config)) return null
        synchronized(cache) { cache[key] }?.let { return it }
        val reply = try {
            AIClient.chat(config, listOf(AIMessage("user", buildPrompt(verb.trim()))))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        val result = parse(reply) ?: return null
        synchronized(cache) { cache[key] = result }
        return result
    }

    /** 供 UI 判断「动词或未知」时是否值得预填派生输入框。 */
    fun cached(verb: String): VerbDerivationResult? =
        synchronized(cache) { cache[verb.trim().lowercase()] }

    private fun buildPrompt(verb: String): String = """
        你是法语教师。请以法语动词 "$verb" 为中心，生成一份「动词派生全景」，帮学生把动词
        串成一张词形网络。按下面的口径整理（确实不存在的形式留空数组/空字符串，禁止编造）：
        1. 现在分词 participe présent（如 étudier → étudiant）：表主动/进行，可加冠词名词化
           指执行者（l'étudiant 正在学习的人）；另给 gérondif（en + 现在分词，表同时伴随）。
        2. 过去分词 participe passé：表被动/完成，给出阳性单数、阴性单数、阳性复数、阴性复数
           四种形式；它做形容词时须配合所修饰名词的性数（le/la/les + pp，如 les leçons étudiées）；
           并给复合时态例句各一句：avoir/être + 过去分词的复合过去时、愈过去时（注明助动词）。
        3. 动词→名词，分两类：
           A 动作名词（动作/结果本身）：-tion/-sion、-ment、-age、短型阴性（-ie/-ée/-ue 等）；
              每条必须附语体对比备注：冠词 + 动词原形（如 l'étudier「研究这件事本身」）是
              文学/书面古体，现代法语几乎不用；现代标准说法是用派生名词
              （如 étudier → étude，阴性 une/l'étude，日常、考试、口语通用）。
           B 施动者名词（做动作的人/物）：-eur/-euse、-ant/-ante 等。
        4. 动词→形容词：-able/-ible、-if/-ive、-ant/-ante；若 -ant 形容词存在，必须与
           过去分词做形容词成对给出并解释语义差别（如 intéressant 令人感兴趣的，
           intéressé 感到感兴趣的：-ant 是「使人…」，过去分词是「感到…」）。
        5. 动词→副词：阴性形容词词干 + -ment（仅在该副词真实存在时给出）。
        6. notes：用 1-3 句中文补充该动词派生的记忆要点或易错点。
        输出要求：
        - 只输出一个 JSON 对象，不要解释、不要代码块标记。
        - 所有法语保留重音符号；中文说明用简体中文。
        - 字段：
          {"verb":"","participe_present":"","gerondif":"",
           "participe_passe":{"m":"","f":"","mp":"","fp":""},"auxiliaire":"avoir|être",
           "exemple_compose_fr":"","exemple_compose_zh":"",
           "exemple_pqp_fr":"","exemple_pqp_zh":"",
           "noms_action":[{"form":"","zh":"","note":"","example_fr":"","example_zh":""}],
           "noms_agent":[同上],"adjectifs":[同上，note 里写 -ant 与过去分词的对比],"adverbes":[同上],
           "notes":""}
    """.trimIndent()

    internal fun parse(reply: String): VerbDerivationResult? {
        val cleaned = reply.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val o = JSONObject(cleaned.substring(start, end + 1))
            val pp = o.optJSONObject("participe_passe")
            VerbDerivationResult(
                verb = o.optString("verb").trim(),
                participePresent = o.optString("participe_present").trim(),
                gerondif = o.optString("gerondif").trim(),
                ppMasculin = pp?.optString("m").orEmpty().trim(),
                ppFeminin = pp?.optString("f").orEmpty().trim(),
                ppMasculinPluriel = pp?.optString("mp").orEmpty().trim(),
                ppFemininPluriel = pp?.optString("fp").orEmpty().trim(),
                auxiliaire = o.optString("auxiliaire").trim(),
                exempleComposeFr = o.optString("exemple_compose_fr").trim(),
                exempleComposeZh = o.optString("exemple_compose_zh").trim(),
                exemplePqpFr = o.optString("exemple_pqp_fr").trim(),
                exemplePqpZh = o.optString("exemple_pqp_zh").trim(),
                nomsAction = parseList(o.optJSONArray("noms_action")),
                nomsAgent = parseList(o.optJSONArray("noms_agent")),
                adjectifs = parseList(o.optJSONArray("adjectifs")),
                adverbes = parseList(o.optJSONArray("adverbes")),
                notes = o.optString("notes").trim()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseList(arr: JSONArray?): List<DerivativeEntry> {
        if (arr == null) return emptyList()
        val out = mutableListOf<DerivativeEntry>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val form = e.optString("form").trim()
            if (form.isEmpty()) continue
            out += DerivativeEntry(
                form = form,
                label = e.optString("label").trim(),
                zh = e.optString("zh").trim(),
                note = e.optString("note").trim(),
                exampleFr = e.optString("example_fr").trim(),
                exampleZh = e.optString("example_zh").trim()
            )
        }
        return out
    }
}
