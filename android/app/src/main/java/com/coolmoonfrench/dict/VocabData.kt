package com.coolmoonfrench.dict

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** 词书条目（来自 assets/vocab/vocab.json，tools/build_vocab_assets.py 生成） */
data class VocabEntry(
    val word: String,
    val pos: String,
    val level: String,
    val themes: List<String>,
    val meaning: String,
    val isVerb: Boolean
)

/** 主题定义 */
data class VocabTheme(val id: String, val zh: String, val fr: String)

/**
 * 一本离线词书：全部词表按「背诵模式 × 难度等级 × 主题」筛选出出题池。
 */
class VocabBook(
    val themes: List<VocabTheme>,
    val entries: List<VocabEntry>
) {
    private val verbsOnly: List<VocabEntry> = entries.filter { it.isVerb }
    private val wordsOnly: List<VocabEntry> = entries.filter { !it.isVerb }
    val themeById: Map<String, VocabTheme> = themes.associateBy { it.id }

    /**
     * 出题池。
     * @param verbMode true=背动词，false=背单词
     * @param levels 选中的难度等级；空集合表示不限
     * @param themes 选中的主题；空集合表示不限
     */
    fun pool(verbMode: Boolean, levels: Set<String>, themes: Set<String>): List<VocabEntry> {
        val base = if (verbMode) verbsOnly else wordsOnly
        return base.filter { e ->
            (levels.isEmpty() || e.level in levels) &&
                (themes.isEmpty() || e.themes.any { it in themes })
        }
    }

    companion object {
        /** JSON 数组序 [w, pos, level, themesCSV, 中文简义, isVerb01] */
        fun parse(json: String): VocabBook {
            val root = JSONObject(json)
            val themeArr = root.optJSONArray("themes") ?: org.json.JSONArray()
            val themes = (0 until themeArr.length()).map { i ->
                val a = themeArr.getJSONArray(i)
                VocabTheme(a.optString(0), a.optString(1), a.optString(2))
            }
            val wordArr = root.getJSONArray("words")
            val entries = ArrayList<VocabEntry>(wordArr.length())
            for (i in 0 until wordArr.length()) {
                val a = wordArr.getJSONArray(i)
                val themesCsv = a.optString(3)
                entries += VocabEntry(
                    word = a.optString(0),
                    pos = a.optString(1),
                    level = a.optString(2),
                    themes = if (themesCsv.isEmpty()) emptyList() else themesCsv.split(','),
                    meaning = a.optString(4),
                    isVerb = a.optInt(5) == 1
                )
            }
            return VocabBook(themes, entries)
        }
    }
}

/** 词书资产加载（进程内缓存，只读一次 assets） */
object VocabData {
    val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")
    const val MODE_WORDS = 0
    const val MODE_VERBS = 1

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
