package com.coolmoonfrench.dict

import android.content.Context

/** 单词书统计快照 */
data class VocabStats(
    val total: Int,
    val learned: Int,
    val mastered: Int,
    val learning: Int,
    val notStarted: Int,
    val due: Int
)

/**
 * 艾宾浩斯记忆排期。按「单词书」（模式 + 难度级别）分档存储：
 * 换书即换 prefs 文件，自然重新开始；同一本书内按复习阶段递增间隔。
 *
 * 阶段：null 未接触；0..N 已答对次数。-1 不出现。
 * 间隔（天）INTERVALS[stage]，答错回到阶段 0、次日再见；答对推进阶段并顺延。
 */
class VocabSrs(context: Context, bookKey: String) {

    private val prefs = context.applicationContext
        .getSharedPreferences("vocab_srs_" + sanitize(bookKey), Context.MODE_PRIVATE)

    private val today: Long get() = System.currentTimeMillis() / DAY_MS

    /** 词在当前书中的状态：null=新词 */
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

    private fun write(word: String, stage: Int, dueDay: Long) {
        prefs.edit().putString(KEY + word, "$stage:$dueDay").apply()
    }

    /** 快照统计（一次读全部 prefs，避免逐词查询） */
    fun stats(pool: List<VocabEntry>): VocabStats {
        val all = prefs.all
        val today = today
        var learned = 0
        var mastered = 0
        var due = 0
        for (e in pool) {
            val raw = all[KEY + e.word] as? String ?: continue
            val i = raw.indexOf(':')
            if (i < 0) continue
            val st = raw.substring(0, i).toIntOrNull() ?: continue
            val d = raw.substring(i + 1).toLongOrNull() ?: continue
            learned++
            if (st >= MASTERED_STAGE) mastered++
            if (d <= today) due++
        }
        return VocabStats(
            total = pool.size,
            learned = learned,
            mastered = mastered,
            learning = learned - mastered,
            notStarted = pool.size - learned,
            due = due
        )
    }

    /** 到期复习词（按到期先后） */
    fun dueWords(pool: List<VocabEntry>): List<VocabEntry> {
        val today = today
        return pool.filter { stage(it.word) != null && dueDay(it.word) <= today }
            .sortedBy { dueDay(it.word) }
    }

    /** 尚未学习的新词（保持池内原顺序） */
    fun newWords(pool: List<VocabEntry>): List<VocabEntry> =
        pool.filter { stage(it.word) == null }

    /**
     * 组一次练习队列：到期复习词在前（按到期先后），其后补新词至上限。
     */
    fun buildSession(pool: List<VocabEntry>, newLimit: Int): List<VocabEntry> =
        dueWords(pool) + newWords(pool).take(newLimit.coerceAtLeast(0))

    /** 答题后更新排期；返回是否已"掌握"（阶段≥4） */
    fun record(word: String, correct: Boolean): Boolean {
        val next = record(word, correct, today)
        return next >= MASTERED_STAGE
    }

    private fun record(word: String, correct: Boolean, today: Long): Int {
        val cur = stage(word) ?: -1
        val nextState = nextState(cur, correct, today)
        write(word, nextState.first, nextState.second)
        return nextState.first
    }

    /** 每日新词计划（0=未设置） */
    fun dailyPlan(): Int = prefs.getInt(KEY_DAILY, DEFAULT_PLAN)

    fun setDailyPlan(n: Int) = prefs.edit().putInt(KEY_DAILY, n.coerceIn(0, 200)).apply()

    /** 重置当前单词书的记忆进度 */
    fun reset() = prefs.edit().clear().apply()

    companion object {
        private const val DAY_MS = 86_400_000L
        private const val KEY = "w_"
        private const val KEY_DAILY = "daily_plan"
        private const val DEFAULT_PLAN = 20
        const val MASTERED_STAGE = 4
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

        /** 单词书键（稳定、可直接做 prefs 文件名后缀） */
        fun bookKey(verbMode: Boolean, levelId: String): String =
            (if (verbMode) "v" else "w") + "." + (if (levelId.isEmpty()) "all" else levelId)

        private fun sanitize(s: String): String =
            s.map { if (it.isLetterOrDigit() || it in ".-_") it else '_' }.joinToString("")
    }
}
