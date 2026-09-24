package com.coolmoonfrench.dict

import kotlin.random.Random

/**
 * 语法练习的确定性出题器：直接利用本地资产现生成题目，
 * 答案来自词书/变位引擎本身，难度由词书分级锚定。
 */
object GrammarGen {

    private val ELISION_CHARS = "aeiouyâàëéêèïîôùûüœh"

    /** 人称标签（与 6 个变位下标对应） */
    val PERSONS = listOf("je", "tu", "il / elle", "nous", "vous", "ils / elles")

    private fun elid(c: Char?): Boolean = c != null && c.lowercaseChar() in ELISION_CHARS

    /** 词书条目是否为可判定的阳性/阴性名词（排除 n. 模糊与纯复数） */
    private fun genderOf(e: VocabEntry): Char? {
        val p = e.pos.substringBefore('&').trim().lowercase()
        if (!p.startsWith("n.") || "pl" in p) return null
        return when {
            ".m" in p -> 'm'
            ".f" in p -> 'f'
            else -> null
        }
    }

    /**
     * 名词与冠词：从词书签定阴阳性，出 le / la / l' / les 四选一。
     * 元音或 h 开头 → l'；解释里给词性与词义，让「记性数」成为可学习项。
     */
    fun articles(pool: List<VocabEntry>, rnd: Random, count: Int = 36): List<QuizQuestion> {
        val out = ArrayList<QuizQuestion>()
        for (e in pool.shuffled(rnd)) {
            if (out.size >= count) break
            val g = genderOf(e) ?: continue
            val vowelStart = elid(e.word.firstOrNull())
            val correct = when {
                vowelStart -> "l'"
                g == 'm' -> "le"
                else -> "la"
            }
            val explain = buildString {
                append("${e.word}（${e.pos}，${e.meaning}）为")
                append(if (g == 'm') "阳性名词" else "阴性名词")
                when {
                    vowelStart -> append("，单数且以元音或哑音 h 开头，省音用 l'。")
                    g == 'm' -> append("，单数定冠词用 le。")
                    else -> append("，单数定冠词用 la。")
                }
            }
            out += QuizQuestion(
                question = "Choisissez l'article convenable :\n\n___ ${e.word}（${e.meaning}）",
                options = listOf("le", "la", "l'", "les"),
                correct = correct,
                explanation = explain
            )
        }
        return out
    }

    /**
     * 动词变位：调用本地变位引擎取正确形式，干扰项取同动词其他人称/时态的真实形式。
     * @param field 目标时态的 6 人称（命令式为 3 人称）形式提取
     * @param extraFields 生成干扰项用的其他时态
     */
    fun conjugation(
        pool: List<VocabEntry>,
        conjugator: VerbConjugator,
        tenseLabel: String,
        field: (Conjugation) -> List<String>?,
        personLabels: List<String> = PERSONS,
        extraFields: List<(Conjugation) -> List<String>?> = emptyList(),
        rnd: Random,
        count: Int = 30
    ): List<QuizQuestion> {
        val out = ArrayList<QuizQuestion>()
        for (entry in pool.shuffled(rnd)) {
            if (out.size >= count) break
            val c = conjugator.conjugate(entry.word) ?: continue
            if (c.infinitive.startsWith("se ") || c.infinitive.startsWith("s'")) continue
            val forms = field(c) ?: continue
            if (forms.size < 3) continue
            val personIdx = (forms.indices).random(rnd)
            val answer = forms[personIdx].trim()
            if (answer.isBlank()) continue
            val wrongPool = LinkedHashSet<String>()
            for ((i, f) in forms.withIndex()) if (i != personIdx) wrongPool.add(f.trim())
            for (ef in extraFields) ef(c)?.forEach { wrongPool.add(it.trim()) }
            wrongPool.remove(answer)
            val wrong = wrongPool.filter { it.isNotBlank() }.shuffled(rnd).take(3)
            if (wrong.size < 3) continue
            val subject = personLabels.getOrElse(personIdx) { PERSONS[personIdx] }
            val prefix = when {
                subject.startsWith("je") && elid(answer.firstOrNull()) -> "j'"
                subject.contains("/") -> "il/elle "
                else -> "$subject "
            }
            out += QuizQuestion(
                question = "Conjuguez « ${c.infinitive} »（${entry.meaning}）— ${tenseLabel} :\n\n${prefix}______",
                options = (listOf(answer) + wrong).shuffled(rnd),
                correct = answer,
                explanation = "${c.infinitive}（${entry.meaning}，第${c.group}组，助动词 ${c.auxiliary}）" +
                    "在${tenseLabel}中 $subject 的变位为 $prefix$answer。"
            )
        }
        return out
    }

    /**
     * 词汇释义：从词书同级别池出「选择正确释义 / 正确法语词」。
     * 难度真实性由词书分级保证。
     */
    fun wordMeaning(
        pool: List<VocabEntry>,
        frToFront: Boolean,
        rnd: Random,
        count: Int = 30
    ): List<QuizQuestion> {
        val out = ArrayList<QuizQuestion>()
        for (target in pool.shuffled(rnd)) {
            if (out.size >= count) break
            val used = linkedSetOf(target.word, target.meaning)
            val wrong = ArrayList<String>(3)
            for (cand in pool.filter { it.word != target.word }.shuffled(rnd)) {
                if (wrong.size >= 3) break
                val disp = if (frToFront) cand.meaning else cand.word
                if (disp in used) continue
                used += disp
                wrong += disp
            }
            if (wrong.size < 3) continue
            val correct = if (frToFront) target.meaning else target.word
            out += QuizQuestion(
                question = if (frToFront)
                    "Que signifie « ${target.word} »（${target.pos}） ?"
                else
                    "Quel mot correspond à « ${target.meaning} »（${target.pos}） ?",
                options = (listOf(correct) + wrong).shuffled(rnd),
                correct = correct,
                explanation = "${target.word}（${target.pos}）= ${target.meaning}。"
            )
        }
        return out
    }
}
