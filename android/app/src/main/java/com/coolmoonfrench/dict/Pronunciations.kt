package com.coolmoonfrench.dict

/** 一组同音词形：同一 IPA 对应的所有拼写形式。 */
data class PronGroup(
    val ipa: String,
    val forms: List<String>
)

/**
 * 动词读音分组：把一次变位中所有「单词形」按 IPA 归纳成若干读音组，
 * 供「所有发音」页面逐组展示「发音按钮 + 音标 + 同音拼写形式」。
 *
 * 只收集单词形（不含空格的复合时态与代动词短语），因为复合时态本质是
 * 「助动词 + 过去分词」的短语，逐词注音没有独立意义。
 */
object Pronunciations {

    /** 收集变位中的全部单词形（去重、保持出现顺序）。 */
    fun simpleForms(c: Conjugation): List<String> = buildList {
        add(c.infinitive)
        addAll(c.present)
        addAll(c.imparfait)
        addAll(c.futurSimple)
        addAll(c.passeSimple)
        addAll(c.conditionnel)
        addAll(c.subjonctifPresent)
        addAll(c.subjonctifImparfait)
        addAll(c.imperatif)
        add(c.participePresent)
        add(c.participePasse)
    }
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.contains(' ') }
        .distinct()

    /** 按 IPA 分组；[ipaOf] 返回不含斜杠的 IPA，空串表示跳过该形式。 */
    fun groupsOf(
        c: Conjugation,
        ipaOf: (String) -> String = { FrenchIpa.lookup(it) }
    ): List<PronGroup> {
        val grouped = LinkedHashMap<String, MutableList<String>>()
        for (form in simpleForms(c)) {
            val ipa = ipaOf(form)
            if (ipa.isEmpty()) continue
            grouped.getOrPut(ipa) { mutableListOf() }.add(form)
        }
        return grouped.map { PronGroup(it.key, it.value) }
    }
}
