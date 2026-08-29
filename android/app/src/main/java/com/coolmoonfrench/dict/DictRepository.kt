package com.coolmoonfrench.dict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.abs

data class DictEntry(
    val word: String,
    val meaning: String,
    val pos: String = extractPos(meaning)
) {
    companion object {
        private val POS_PATTERN = Regex("【([^】]+)】")

        fun extractPos(meaning: String): String {
            val m = POS_PATTERN.find(meaning)
            return m?.groupValues?.get(1)?.trim() ?: ""
        }
    }
}

/**
 * 词典仓库。
 *
 * 首次启动时把 assets 中的词库导入本地 SQLite（databases/dictionary.db），
 * 后续启动直接打开 SQLite，不再每次解析 JSON，大幅提升加载速度。
 */
class DictRepository(private val context: Context) {

    private val dbHelper by lazy { DictDbHelper(context) }

    // 供模糊/相似搜索的轻量内存索引（norm -> id）
    private var normList: MutableList<String> = mutableListOf()

    suspend fun load() = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val count = db.rawQuery("SELECT COUNT(*) FROM dict", null).use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
        if (count == 0) {
            dbHelper.importFromAssets()
        }
        // 加载模糊/相似搜索所需的内存 norm 列表
        normList = mutableListOf()
        db.rawQuery("SELECT norm FROM dict", null).use { c ->
            while (c.moveToNext()) {
                normList.add(c.getString(0))
            }
        }
    }

    fun lookupExact(query: String): List<DictEntry> {
        val norm = normalize(query)
        if (norm.isEmpty()) return emptyList()
        val db = dbHelper.readableDatabase
        val result = mutableListOf<DictEntry>()
        db.rawQuery("SELECT word, pos, meaning FROM dict WHERE norm = ?", arrayOf(norm)).use { c ->
            while (c.moveToNext()) {
                result.add(DictEntry(c.getString(0), c.getString(2), c.getString(1)))
            }
        }
        return result
    }

    fun lookupPrefix(query: String, maxResults: Int = 10): List<DictEntry> {
        val norm = normalize(query)
        if (norm.isEmpty()) return emptyList()
        val escaped = norm.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val db = dbHelper.readableDatabase
        val result = mutableListOf<DictEntry>()
        db.rawQuery(
            "SELECT word, pos, meaning FROM dict WHERE norm LIKE ? ESCAPE '\\' ORDER BY length(norm), word LIMIT ?",
            arrayOf("$escaped%", maxResults.toString())
        ).use { c ->
            while (c.moveToNext()) {
                result.add(DictEntry(c.getString(0), c.getString(2), c.getString(1)))
            }
        }
        return result
    }

    /**
     * 改进的模糊搜索：编辑距离归一化排序，避免无关词
     */
    fun fuzzySearch(query: String, maxDist: Int = 2, maxResults: Int = 10): List<Pair<DictEntry, Int>> {
        val norm = normalize(query)
        if (norm.isEmpty()) return emptyList()
        val scored = mutableListOf<Pair<Int, Int>>() // (score, index)

        for (i in normList.indices) {
            val candidate = normList[i]
            if (abs(candidate.length - norm.length) > maxDist) continue
            val d = levenshtein(norm, candidate)
            if (d <= maxDist) {
                // 归一化距离：相对较短词更严格
                val maxLen = maxOf(norm.length, candidate.length).coerceAtLeast(1)
                val score = d * 1000 + abs(candidate.length - norm.length)
                if (d * 10 <= maxLen * 3) {
                    scored.add(score to i)
                }
            }
        }
        scored.sortBy { it.first }
        val db = dbHelper.readableDatabase
        return scored.take(maxResults).map { (s, i) ->
            val entry = getEntryById(db, normList[i])
            entry to (s / 1000)
        }.filter { it.first != null }.map { it.first!! to it.second }
    }

    /**
     * 近似词：编辑距离较近的词（展示用，阈值宽松）
     */
    fun similarWords(query: String, maxResults: Int = 6): List<DictEntry> {
        val norm = normalize(query)
        if (norm.isEmpty()) return emptyList()
        val scored = mutableListOf<Pair<Int, Int>>()
        for (i in normList.indices) {
            val candidate = normList[i]
            if (candidate == norm) continue
            if (abs(candidate.length - norm.length) > 3) continue
            val d = levenshtein(norm, candidate)
            if (d <= 2 && d <= maxOf(1, norm.length / 4)) {
                scored.add(d to i)
            }
        }
        scored.sortBy { it.first }
        val db = dbHelper.readableDatabase
        return scored.take(maxResults).mapNotNull { (d, i) ->
            getEntryById(db, normList[i])
        }
    }

    /**
     * 同词根词：共享相同词干的词
     */
    fun relatedWords(query: String, maxResults: Int = 6): List<DictEntry> {
        val s = stem(query)
        if (s.length < 3) return emptyList()
        val norm = normalize(query)
        val db = dbHelper.readableDatabase
        val result = mutableListOf<DictEntry>()
        val seen = mutableSetOf<String>()
        db.rawQuery(
            "SELECT word, pos, meaning FROM dict WHERE stem = ? ORDER BY length(word), word",
            arrayOf(s)
        ).use { c ->
            while (c.moveToNext()) {
                val w = c.getString(0)
                val n = normalize(w)
                if (n != norm && seen.add(n)) {
                    result.add(DictEntry(w, c.getString(2), c.getString(1)))
                    if (result.size >= maxResults) break
                }
            }
        }
        return result
    }

    /**
     * 派生词：共享词干且词长差异在1-4之间（前缀/后缀变化）
     */
    fun derivedWords(query: String, maxResults: Int = 6): List<DictEntry> {
        val norm = normalize(query)
        val s = stem(query)
        if (s.length < 3) return emptyList()
        val db = dbHelper.readableDatabase
        val result = mutableListOf<DictEntry>()
        val seen = mutableSetOf<String>()
        db.rawQuery(
            "SELECT word, pos, meaning FROM dict WHERE stem = ? ORDER BY length(word), word",
            arrayOf(s)
        ).use { c ->
            while (c.moveToNext()) {
                val w = c.getString(0)
                val n = normalize(w)
                if (n != norm && abs(n.length - norm.length) in 1..4 &&
                    (n.startsWith(s) || w.lowercase(Locale.ROOT).startsWith(s)) && seen.add(n)
                ) {
                    result.add(DictEntry(w, c.getString(2), c.getString(1)))
                    if (result.size >= maxResults) break
                }
            }
        }
        return result
    }

    fun allWords(): List<String> {
        val db = dbHelper.readableDatabase
        val result = mutableListOf<String>()
        db.rawQuery("SELECT word FROM dict", null).use { c ->
            while (c.moveToNext()) {
                result.add(c.getString(0))
            }
        }
        return result
    }

    fun size(): Int {
        val db = dbHelper.readableDatabase
        return db.rawQuery("SELECT COUNT(*) FROM dict", null).use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
    }

    private fun getEntryById(db: SQLiteDatabase, norm: String): DictEntry? {
        db.rawQuery("SELECT word, pos, meaning FROM dict WHERE norm = ? LIMIT 1", arrayOf(norm)).use { c ->
            if (c.moveToFirst()) {
                return DictEntry(c.getString(0), c.getString(2), c.getString(1))
            }
        }
        return null
    }

    // ---------- 收藏 ----------

    private val prefs by lazy {
        context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    }

    fun loadFavorites(): List<DictEntry> {
        val set = prefs.getStringSet("words", emptySet()) ?: emptySet()
        return set.mapNotNull { word ->
            lookupExact(word).firstOrNull()
        }
    }

    fun addFavorite(word: String) {
        val set = prefs.getStringSet("words", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(word)
        prefs.edit().putStringSet("words", set).apply()
    }

    fun removeFavorite(word: String) {
        val set = prefs.getStringSet("words", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(word)
        prefs.edit().putStringSet("words", set).apply()
    }

    fun isFavorite(word: String): Boolean {
        return prefs.getStringSet("words", emptySet())?.contains(word) ?: false
    }

    // ---------- 查词历史 ----------

    private val historyPrefs by lazy {
        context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    }

    /** 读取查词历史（新→旧） */
    fun loadHistory(): List<DictEntry> {
        val json = historyPrefs.getString("words", "[]") ?: "[]"
        val arr = JSONArray(json)
        val result = mutableListOf<DictEntry>()
        for (i in 0 until arr.length()) {
            val w = arr.optString(i).trim()
            if (w.isEmpty()) continue
            lookupExact(w).firstOrNull()?.let { result.add(it) }
        }
        return result
    }

    /** 记录一次查词，按 limit 截断历史 */
    fun addHistory(word: String, limit: Int = 50) {
        val norm = word.trim()
        if (norm.isEmpty()) return
        val json = historyPrefs.getString("words", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.optString(i))
        list.removeAll { it.equals(norm, ignoreCase = true) }
        list.add(0, norm)
        if (list.size > limit) {
            for (i in 0 until list.size - limit) {
                list.removeAt(list.size - 1)
            }
        }
        historyPrefs.edit().putString("words", JSONArray(list).toString()).apply()
    }

    /** 清空查词历史 */
    fun clearHistory() {
        historyPrefs.edit().putString("words", "[]").apply()
    }

    companion object {
        const val DB_NAME = "dictionary.db"
        const val DB_VERSION = 3

        private val ACCENT_MAP = mapOf(
            'à' to 'a', 'â' to 'a', 'ä' to 'a', 'æ' to 'a',
            'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
            'î' to 'i', 'ï' to 'i',
            'ô' to 'o', 'ö' to 'o', 'œ' to 'o',
            'ù' to 'u', 'û' to 'u', 'ü' to 'u',
            'ÿ' to 'y',
            'ç' to 'c'
        )

        fun normalize(s: String): String {
            val sb = StringBuilder()
            for (c in s.lowercase(Locale.ROOT)) {
                sb.append(ACCENT_MAP[c] ?: c)
            }
            return sb.toString()
        }

        /**
         * 提取词干：去掉常见词尾与词缀
         */
        fun stem(word: String): String {
            var w = normalize(word)
            // 常见名词/形容词词尾
            val suffixes = listOf(
                "ables", "ation", "ement", "ement", "ité", "iste", "isme",
                "erait", "irait", "aient", "ions", "iez",
                "ant", "ent", "ait", "ait",
                "able", "ible", "euse", "eux", "eur", "esse", "aire",
                "ique", "ette", "eaux", "eau",
                "er", "ir", "re", "e", "s", "x", "z"
            )
            for (sfx in suffixes) {
                if (w.length > sfx.length + 3 && w.endsWith(sfx)) {
                    w = w.dropLast(sfx.length)
                    break
                }
            }
            return w
        }

        fun levenshtein(a: String, b: String): Int {
            val m = a.length
            val n = b.length
            if (m == 0) return n
            if (n == 0) return m
            val dp = IntArray(n + 1) { it }
            for (i in 1..m) {
                var prev = dp[0]
                dp[0] = i
                for (j in 1..n) {
                    val temp = dp[j]
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                    prev = temp
                }
            }
            return dp[n]
        }
    }
}

/**
 * SQLite 帮助类：负责建表与首次从 assets 导入词库。
 */
private class DictDbHelper(context: Context) :
    SQLiteOpenHelper(context, DictRepository.DB_NAME, null, DictRepository.DB_VERSION) {

    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS dict (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "word TEXT NOT NULL, " +
                "norm TEXT NOT NULL, " +
                "pos TEXT, " +
                "meaning TEXT, " +
                "stem TEXT)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_dict_norm ON dict(norm)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dict_stem ON dict(stem)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 简单处理：升级时重建
        db.execSQL("DROP TABLE IF EXISTS dict")
        onCreate(db)
    }

    fun importFromAssets() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            importSjDict(db, "word.sj")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun importSjDict(db: SQLiteDatabase, filename: String) {
        try {
            val stream = appContext.assets.open(filename)
            val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
            val text = reader.readText()
            reader.close()
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val item = arr.getJSONArray(i)
                val word = item.optString(0, "").trim()
                val pos = item.optString(1, "").trim()
                val chinese = item.optString(2, "").trim()
                if (word.isNotEmpty()) {
                    val meaning = if (pos.isNotEmpty()) "【$pos】$chinese" else chinese
                    insertIfAbsent(db, word, meaning)
                }
            }
        } catch (_: Exception) {
            // file not found, skip
        }
    }

    private fun insertIfAbsent(db: SQLiteDatabase, word: String, meaning: String) {
        val norm = DictRepository.normalize(word)
        if (norm.isEmpty()) return
        val stem = DictRepository.stem(word)
        val cv = android.content.ContentValues().apply {
            put("word", word)
            put("norm", norm)
            put("pos", DictEntry.extractPos(meaning))
            put("meaning", meaning)
            put("stem", stem)
        }
        db.insertWithOnConflict("dict", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }
}
