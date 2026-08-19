package com.moonfrench.dict

import java.util.Locale

/**
 * 法语单词构词拆解（前缀 / 词根 / 后缀）。
 * 用法语常见词缀表 + 词库匹配词根，输出每个部分的含义。
 */
data class WordBreakdown(
    val parts: List<MorphPart>,
    val rootEntry: DictEntry? = null
) {
    val isEmpty: Boolean get() = parts.isEmpty()
    val displayText: String
        get() = parts.joinToString(" + ") { it.text }
}

data class MorphPart(
    val text: String,
    val kind: MorphKind,
    val meaning: String
)

enum class MorphKind { PREFIX, ROOT, SUFFIX }

class MorphologyAnalyzer {

    // 常见法语前缀及含义（越长越优先）
    private val prefixes = listOf(
        "contre" to "反；对抗",
        "entre" to "互相；之间",
        "inter" to "之间",
        "trans" to "跨越；穿过",
        "anté" to "在前",
        "extra" to "额外；向外",
        "ultra" to "极端",
        "hyper" to "超；过分",
        "hypo" to "低于；亚",
        "macro" to "宏观",
        "micro" to "微观",
        "multi" to "多",
        "poly" to "多",
        "mono" to "单；一",
        "auto" to "自动；自身",
        "semi" to "半",
        "demi" to "半",
        "arch" to "首要；原始",
        "anti" to "反对；抗",
        "para" to "平行；防",
        "péri" to "周围",
        "dia" to "通过；横穿",
        "dis" to "分离；否定",
        "ex" to "前任；向外",
        "in" to "向内；否定",
        "im" to "向内；否定",
        "il" to "不；无",
        "ir" to "不；无",
        "pré" to "预先；在前",
        "post" to "之后",
        "pro" to "支持；向前",
        "con" to "共同；一起",
        "com" to "共同；一起",
        "cor" to "共同；一起",
        "col" to "共同；一起",
        "co" to "共同；一起",
        "sub" to "在…之下",
        "sur" to "超过；在上",
        "super" to "超级；在上",
        "sous" to "在…之下",
        "re" to "再；重新",
        "ré" to "再；重新",
        "dé" to "去除；向下",
        "dés" to "去除；否定",
        "en" to "进入；使…",
        "em" to "进入；使…",
        "ab" to "离开；偏离",
        "ad" to "朝向",
        "avant" to "之前",
        "arrière" to "向后"
    )

    // 常见法语后缀及含义（越长越优先，含词性）
    private val suffixes = listOf(
        "iquement" to "…地（副词）",
        "ification" to "…化（名词）",
        "isation" to "…化（名词）",
        "ation" to "…行为/结果（名词）",
        "ition" to "…行为/状态（名词）",
        "ement" to "…行为/结果（名词）",
        "ationnel" to "…的（形容词）",
        "éalisme" to "…主义（名词）",
        "alisme" to "…主义（名词）",
        "ité" to "…性质（名词）",
        "té" to "…性质（名词）",
        "trice" to "…者（阴性名词）",
        "teur" to "…者（名词）",
        "rice" to "…者（阴性名词）",
        "eur" to "…者（名词）",
        "euse" to "…者（名词）",
        "esse" to "…性质/状态（名词）",
        "ette" to "小…（名词）",
        "age" to "…行为/集合（名词）",
        "ade" to "…行为（名词）",
        "ure" to "…状态/结果（名词）",
        "ance" to "…性质/状态（名词）",
        "ence" to "…性质/状态（名词）",
        "eux" to "…的（形容词）",
        "ive" to "…的（形容词）",
        "if" to "…的（形容词）",
        "ique" to "…的（形容词）",
        "able" to "可…的（形容词）",
        "ible" to "可…的（形容词）",
        "ain" to "…的（形容词）",
        "aine" to "…的（形容词）",
        "al" to "…的（形容词）",
        "ale" to "…的（形容词）",
        "el" to "…的（形容词）",
        "elle" to "…的（形容词）",
        "ien" to "…的（形容词）",
        "ienne" to "…的（形容词）",
        "ois" to "…的（形容词）",
        "oise" to "…的（形容词）",
        "iste" to "…主义者/家（名词）",
        "isme" to "…主义（名词）",
        "ment" to "…地（副词）",
        "ter" to "…（动词）",
        "tir" to "…（动词）",
        "iser" to "使…化（动词）",
        "ifier" to "使…化（动词）",
        "ir" to "…（动词，第二组）",
        "er" to "…（动词，第一组）",
        "re" to "…（动词）",
        "te" to "…（过去分词）",
        "e" to "…（阴性/分词）",
        "s" to "复数"
    )

    /** 分析一个词：返回拆解结果 */
    fun analyze(word: String, repository: DictRepository): WordBreakdown {
        val w = word.trim().lowercase(Locale.ROOT)
            .replace(Regex("^se "), "")
            .replace(Regex("^s'"), "")
        if (w.length < 3) return WordBreakdown(emptyList())

        var prefix: MorphPart? = null
        var rootLen = 0
        var rootWord = ""

        // 1. 先找前缀词根（从开头直接匹配词库，如 planer → plan）
        for (len in w.length - 1 downTo 3) {
            if (repository.lookupExact(w.substring(0, len)).isNotEmpty()) {
                rootLen = len
                rootWord = w.substring(0, len)
                break
            }
        }

        // 2. 若开头无词根，尝试"前缀 + 词根"（如 refaire → re + faire）
        if (rootLen == 0) {
            for ((p, meaning) in prefixes) {
                if (!w.startsWith(p) || w.length <= p.length + 2) continue
                val rest = w.removePrefix(p)
                var found = false
                for (len in rest.length downTo 3) {
                    if (repository.lookupExact(rest.substring(0, len)).isNotEmpty()) {
                        prefix = MorphPart(p, MorphKind.PREFIX, meaning)
                        rootLen = len
                        rootWord = rest.substring(0, len)
                        found = true
                        break
                    }
                }
                if (found) break
            }
        }

        // 3. 匹配后缀（词根之后）
        val afterRoot = if (rootLen > 0) w.substring(rootLen) else ""
        var suffix: MorphPart? = null
        if (afterRoot.isNotEmpty()) {
            for ((s, meaning) in suffixes) {
                if (afterRoot.endsWith(s)) {
                    val mid = afterRoot.dropLast(s.length)
                    // 允许 mid 为空（后缀直接接词根），或 mid 是元音变体/连接元音
                    if (mid.length <= 3) {
                        suffix = MorphPart(s, MorphKind.SUFFIX, meaning)
                        break
                    }
                }
            }
        }

        // 4. 组装结果
        val parts = mutableListOf<MorphPart>()
        prefix?.let { parts.add(it) }
        if (rootLen > 0) {
            val rootEntry = repository.lookupExact(rootWord).firstOrNull()
            parts.add(
                MorphPart(
                    rootWord,
                    MorphKind.ROOT,
                    rootEntry?.let { extractChinese(it) } ?: "词根"
                )
            )
        }
        suffix?.let { parts.add(it) }

        val rootEntry = if (rootLen > 0) repository.lookupExact(rootWord).firstOrNull() else null
        return WordBreakdown(parts, rootEntry)
    }

    /** 提取词条中的中文含义（若释义是英文则用英文首项） */
    private fun extractChinese(e: DictEntry): String {
        val m = e.meaning.replace("【${e.pos}】", "").trim()
        // 若有中文则取中文
        val hasChinese = m.any { it in '\u4e00'..'\u9fff' }
        if (hasChinese) return m
        // 否则取第一段英文
        val first = m.split(";", ",").firstOrNull()?.trim() ?: m
        return if (first.length > 40) first.take(40) + "…" else first
    }
}
