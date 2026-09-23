package com.coolmoonfrench.dict

import kotlin.random.Random

/** 选项显示项：text 为选项文本，entry 供长按查看释义 */
data class VocabOption(val text: String, val entry: VocabEntry)

/** 一道背词题 */
data class VocabQuestion(
    val target: VocabEntry,
    val frenchToFront: Boolean,
    val prompt: String,
    val options: List<VocabOption>,
    val answerIndex: Int
) {
    /** 正确答案文本（法语词或中文释义） */
    val answerText: String get() = options[answerIndex].text
}

/** 出题：目标词 + 3 个干扰项（同池优先同级别），选项之间互不重复 */
object VocabQuiz {

    fun build(
        target: VocabEntry,
        pool: List<VocabEntry>,
        all: List<VocabEntry>,
        rnd: Random = Random.Default,
        frenchToFront: Boolean
    ): VocabQuestion? {
        val used = linkedSetOf(target.word, target.meaning)
        val distractors = ArrayList<VocabOption>(3)
        val tiers = listOf(
            pool.filter { it.level == target.level && it.isVerb == target.isVerb },
            pool.filter { it.level == target.level },
            all.filter { it.level == target.level },
            all
        )
        for (tier in tiers) {
            if (distractors.size >= 3) break
            for (cand in tier.filter { it.word != target.word }.shuffled(rnd)) {
                if (distractors.size >= 3) break
                if (cand.word in used || cand.meaning in used) continue
                used += cand.word
                used += cand.meaning
                distractors += VocabOption(
                    if (frenchToFront) cand.meaning else cand.word, cand
                )
            }
        }
        if (distractors.size < 3) return null
        val options = (distractors + VocabOption(
            if (frenchToFront) target.meaning else target.word, target
        )).shuffled(rnd)
        val answerIndex = options.indexOfFirst { it.entry.word == target.word }
        val prompt = if (frenchToFront) target.word else target.meaning
        return VocabQuestion(target, frenchToFront, prompt, options, answerIndex)
    }
}
