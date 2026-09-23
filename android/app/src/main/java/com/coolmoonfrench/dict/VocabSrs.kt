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

/** 按「学习日」分组的待复习批次 */
data class VocabReviewGroup(
    val day: Long,
    val words: List<VocabEntry>,
    val due: Int
)

/**
 * 艾宾浩斯记忆排期。按「单词书」（模式 + 难度级别）分档存储：
 * 换书即换 prefs 文件，自然重新开始；同一本书内按遗忘曲线递增复习间隔。
 *
 * 记录格式："阶段:到期日:首次学习日"（均为天数）。
 * 阶段 null=新词；0..MAX_STAGE 已复习次数；阶段 >= MASTERED_STAGE 记为已掌握。
 *
 * 遗忘曲线（天）：刚记住当天即复习一次，之后 1 → 2 → 4 → 7 → 15 → 30 → 60 天。
 * 答对推进一档并顺延间隔；答错回到阶段 0、当天再复习。
 */
class VocabSrs(context: Context, bookKey: String) {

    private val prefs = context.applicationContext
        .getSharedPreferences("vocab_srs_" + sanitize(bookKey), Context.MODE_PRIVATE)

    private val today: Long get() = System.currentTimeMillis() / DAY_MS

    /** 词在当前书中的阶段：null=新词 */
    fun stage(word: String): Int? = state(word)?.first

    private fun state(word: String): Triple<Int, Long, Long>? {
        val raw = prefs.getString(KEY + word, null) ?: return null
        return parse(raw)
    }

    private fun write(word: String, stage: Int, dueDay: Long, learnDay: Long) {
        prefs.edit().putString(KEY + word, "$stage:$dueDay:$learnDay").apply()
    }

    /** 快照统计（一次读全部 prefs，避免逐词查询） */
    fun stats(pool: List<VocabEntry>): VocabStats {
        val all = prefs.all
        val today = today
        var learned = 0
        var mastered = 0
        var due = 0
        for (e in pool) {
            val (st, d, _) = parse(all[KEY + e.word] as? String ?: continue) ?: continue
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
        val all = prefs.all
        return pool.mapNotNull { e ->
            val s = parse(all[KEY + e.word] as? String) ?: return@mapNotNull null
            if (s.second <= today) e to s.second else null
        }.sortedBy { it.second }.map { it.first }
    }

    /** 尚未学习的新词（保持池内原顺序） */
    fun newWords(pool: List<VocabEntry>): List<VocabEntry> =
        pool.filter { stage(it.word) == null }

    /**
     * 组一次练习队列：到期复习词在前（按到期先后），其后补新词至上限。
     */
    fun buildSession(pool: List<VocabEntry>, newLimit: Int): List<VocabEntry> =
        dueWords(pool) + newWords(pool).take(newLimit.coerceAtLeast(0))

    /**
     * 按「首次学习日」分组，返回全部已学习词（含尚未到期的），供「待复习」页按天选择。
     * 组按日期从新到旧。
     */
    fun reviewGroups(pool: List<VocabEntry>): List<VocabReviewGroup> {
        val all = prefs.all
        val today = today
        val byDay = LinkedHashMap<Long, MutableList<VocabEntry>>()
        val dueByDay = HashMap<Long, Int>()
        for (e in pool) {
            val (_, d, learn) = parse(all[KEY + e.word] as? String ?: continue) ?: continue
            byDay.getOrPut(learn) { mutableListOf() }.add(e)
            if (d <= today) dueByDay[learn] = (dueByDay[learn] ?: 0) + 1
        }
        return byDay.entries
            .sortedByDescending { it.key }
            .map { VocabReviewGroup(it.key, it.value, dueByDay[it.key] ?: 0) }
    }

    /** 答题后更新排期；返回是否已"掌握"（阶段≥MASTERED_STAGE） */
    fun record(word: String, correct: Boolean): Boolean {
        val next = record(word, correct, today)
        return next >= MASTERED_STAGE
    }

    private fun record(word: String, correct: Boolean, today: Long): Int {
        val cur = state(word)
        val learn = cur?.third ?: today
        val nextState = nextState(cur?.first ?: -1, correct, today)
        write(word, nextState.first, nextState.second, learn)
        return nextState.first
    }

    /** 忽略当前词：标记为已学习并立刻进入待复习（当天到期），不改动原学习日。 */
    fun ignore(word: String) {
        val t = today
        val learn = state(word)?.third ?: t
        write(word, 0, t, learn)
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
        const val MASTERED_STAGE = 6
        private const val MAX_STAGE = 7
        /** 艾宾浩斯复习间隔（天），下标为阶段：阶段 0 当天复习，其后 1/2/4/7/15/30/60 天。 */
        private val INTERVALS = intArrayOf(0, 1, 2, 4, 7, 15, 30, 60)

        /** 解析 "阶段:到期日:学习日"；兼容旧的两段式 "阶段:到期日"。 */
        fun parse(raw: String?): Triple<Int, Long, Long>? {
            if (raw == null) return null
            val parts = raw.split(':')
            if (parts.size < 2) return null
            val st = parts[0].toIntOrNull() ?: return null
            val due = parts[1].toLongOrNull() ?: return null
            val learn = parts.getOrNull(2)?.toLongOrNull() ?: due
            return Triple(st, due, learn)
        }

        /**
         * 纯排期计算（可脱离 Android 测试）。
         * @param cur 当前阶段：-1=新词
         * @return Pair(新阶段, 到期日)。答对推进并顺延间隔；答错阶段归 0、当天再复习。
         */
        fun nextState(cur: Int, correct: Boolean, today: Long): Pair<Int, Long> =
            if (correct) {
                val next = (cur + 1).coerceIn(0, MAX_STAGE)
                next to today + INTERVALS[next]
            } else {
                0 to today + INTERVALS[0]
            }

        /** 某阶段对应的复习间隔（天） */
        fun intervalDays(stage: Int): Int = INTERVALS[stage.coerceIn(0, INTERVALS.size - 1)]

        /** 单词书键（稳定、可直接做 prefs 文件名后缀） */
        fun bookKey(verbMode: Boolean, levelId: String): String =
            (if (verbMode) "v" else "w") + "." + (if (levelId.isEmpty()) "all" else levelId)

        private fun sanitize(s: String): String =
            s.map { if (it.isLetterOrDigit() || it in ".-_") it else '_' }.joinToString("")
    }
}
