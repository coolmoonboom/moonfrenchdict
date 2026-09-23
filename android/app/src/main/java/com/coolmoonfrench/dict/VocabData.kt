package com.coolmoonfrench.dict

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** 单词书级别（A1…B2、专四/专八、高级法语） */
data class VocabLevel(val id: String, val label: String)

/** 词书条目（来自 assets/vocab/vocab.json，tools/build_vocab_assets.py 生成） */
data class VocabEntry(
    val word: String,
    val pos: String,
    val level: String,
    val meaning: String,
    val isVerb: Boolean
)

/**
 * 一本离线词书：全部词表按「背诵模式 × 难度级别」组成单词书。
 * JSON 数组序 [w, pos, level, 中文简义, isVerb01]。
 */
class VocabBook(
    val levels: List<VocabLevel>,
    val entries: List<VocabEntry>
) {
    private val verbsOnly: List<VocabEntry> by lazy { entries.filter { it.isVerb } }
    private val wordsOnly: List<VocabEntry> by lazy { entries.filter { !it.isVerb } }
    private val levelById: Map<String, VocabLevel> = levels.associateBy { it.id }

    /** 所有法语词（用于详情页收藏等），按模式过滤 */
    fun base(verbMode: Boolean): List<VocabEntry> = if (verbMode) verbsOnly else wordsOnly

    /** 出题池：一本单词书 = 一个难度级别；levelId 为空表示全部级别 */
    fun pool(verbMode: Boolean, levelId: String): List<VocabEntry> {
        val b = base(verbMode)
        return if (levelId.isEmpty() || levelId == VocabData.ALL) b
        else b.filter { it.level == levelId }
    }

    /** 级别显示名 */
    fun labelOf(levelId: String): String = when {
        levelId.isEmpty() || levelId == VocabData.ALL -> "全部词汇"
        else -> levelById[levelId]?.label ?: levelId
    }

    companion object {
        fun parse(json: String): VocabBook {
            val root = JSONObject(json)
            val lvArr = root.optJSONArray("levels") ?: org.json.JSONArray()
            val levels = (0 until lvArr.length()).map { i ->
                val a = lvArr.getJSONArray(i)
                VocabLevel(a.optString(0), a.optString(1))
            }
            val wordArr = root.getJSONArray("words")
            val entries = ArrayList<VocabEntry>(wordArr.length())
            for (i in 0 until wordArr.length()) {
                val a = wordArr.getJSONArray(i)
                entries += VocabEntry(
                    word = a.optString(0),
                    pos = a.optString(1),
                    level = a.optString(2),
                    meaning = a.optString(3),
                    isVerb = a.optInt(4) == 1
                )
            }
            return VocabBook(levels, entries)
        }
    }
}

/** 词书资产加载（进程内缓存，只读一次 assets） */
object VocabData {
    const val MODE_WORDS = 0
    const val MODE_VERBS = 1

    /** 表示不限制级别（全部词汇） */
    const val ALL = ""

    private val cached = AtomicReference<VocabBook?>(null)

    fun load(context: Context): VocabBook {
        cached.get()?.let { return it }
        synchronized(this) {
            cached.get()?.let { return it }
            val text = context.assets.open("vocab/vocab.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val book = VocabBook.parse(text)
            cached.set(book)
            return book
        }
    }
}
