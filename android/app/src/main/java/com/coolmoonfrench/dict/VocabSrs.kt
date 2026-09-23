package com.coolmoonfrench.dict

import android.content.Context

/**
 * 艾宾浩斯记忆排期。按「单词书」（模式+难度+主题组合）分档存储：
 * 换书即换 prefs 文件，自然重新开始；同一本书内按复习阶段递增间隔。
 *
 * 阶段：-1 未接触；0..N 已答对次数。间隔（天）INTERVALS[stage]，
 * 答错回到阶段 0、次日再见；答对推进阶段并顺延到下一间隔。
 */
class VocabSrs(context: Context, bookId: String) {

    private val prefs = context.applicationContext
        .getSharedPreferences("vocab_srs_" + sanitize(bookId), Context.MODE_PRIVATE)

    private val today: Long get() = System.currentTimeMillis() / DAY_MS

    /** 词在当前书中的状态：null=新书词 */
    fun stage(word: String): Int? = state(word)?.first

    private fun state(word: String): Pair<Int, Long>? {
        val s = prefs.getString(KEY + word, null) ?: return null
        val i = s.indexOf(':')
        if (i < 0) return null
        val st = s.substring(0, i).toIntOrNull() ?: return null
        val d = s.substring(i + 1).toLongOrNull() ?: return null
        return st to d
    }

    private fun dueDay(word: String): Long = state(word)?.second ?: Long.MAX_VALUE

    private fun record(word: String, stage: Int, dueDay: Long) {
        prefs.edit().putString(KEY + word, "$stage:$dueDay").apply()
    }

    /**
     * 组一次练习队列：到期复习词在前（按到期先后），其后补新词至上限。
     */
    fun buildSession(pool: List<VocabEntry>, newLimit: Int): List<VocabEntry> {
        val today = today
        val due = pool.filter { stage(it.word) != null && dueDay(it.word) <= today }
            .sortedBy { dueDay(it.word) }
        val news = pool.filter { stage(it.word) == null }
        return due + news.take(newLimit.coerceAtLeast(0))
    }

    /** 答题后更新排期；阶段封顶在最后一个间隔。返回是否已"记住"（阶段≥4） */
    fun record(word: String, correct: Boolean): Boolean {
        val next = record(word, correct, today)
        return next >= 4
    }

    /** 算出下一 (阶段, 到期日) 并写回 prefs，返回新阶段 */
    private fun record(word: String, correct: Boolean, today: Long): Int {
        val cur = stage(word) ?: -1
        val nextState = nextState(cur, correct, today)
        record(word, nextState.first, nextState.second)
        return nextState.first
    }

    /** 本书进度统计（仅计池中词） */
    fun progress(pool: List<VocabEntry>): IntArray {
        var learned = 0
        var mastered = 0
        for (e in pool) {
            val s = stage(e.word) ?: continue
            learned++
            if (s >= 4) mastered++
        }
        return intArrayOf(learned, mastered)
    }

    /** 重置当前单词书的记忆进度 */
    fun reset() = prefs.edit().clear().apply()

    companion object {
        private const val DAY_MS = 86_400_000L
        private const val KEY = "w_"
        private val INTERVALS = intArrayOf(1, 2, 4, 7, 15, 30)
        private const val MAX_STAGE = 5

        /**
         * 纯排期计算（可脱离 Android 测试）。
         * @param cur 当前阶段：-1=新词
         * @return Pair(新阶段, 到期日)。答对推进并顺延间隔；答错阶段归 0、次日再来。
         */
        fun nextState(cur: Int, correct: Boolean, today: Long): Pair<Int, Long> =
            if (correct) {
                val next = (cur + 1).coerceAtMost(MAX_STAGE)
                next to today + INTERVALS[next]
            } else {
                0 to today + 1
            }

        /** 单词书名（稳定、可直接做 prefs 文件名） */
        fun bookId(verbMode: Boolean, levels: Set<String>, themes: Set<String>): String {
            val l = VocabData.LEVELS.filter { it in levels }.joinToString("-").ifEmpty { "all" }
            val t = themes.sorted().joinToString("-").ifEmpty { "all" }
            return (if (verbMode) "v" else "w") + "." + sanitize("$l.$t")
        }

        private fun sanitize(bookId: String): String =
            bookId.map { if (it.isLetterOrDigit() || it in ".-_") it else '_' }.joinToString("")
    }
}
