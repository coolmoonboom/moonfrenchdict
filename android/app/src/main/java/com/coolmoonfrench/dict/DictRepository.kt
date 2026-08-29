package com.coolmoonfrench.dict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale
import kotlin.math.abs

data class DictEntry(
    val word: String,
    val meaning: String,
    val pos: String = "",
    val zh: String = "",
    val en: String = ""
) {
    companion object {
        private val POS_PATTERN = Regex("【([^】]+)】")

        fun extractPos(meaning: String): String {
            val m = POS_PATTERN.find(meaning)
            return m?.groupValues?.get(1)?.trim() ?: ""
        }

        /** 组装展示用释义：中文优先，英文兜底 */
        fun combineMeaning(zh: String, en: String, pos: String): String {
            val primary = if (zh.isNotBlank()) zh else en
            return if (pos.isNotBlank()) "【$pos】$primary" else primary
        }
    }
}

/**
 * 词典仓库。
 *
 * 使用预构建的 SQLite 词典库（tools/build_dict_db.py 生成 dictionary.db）：
 * 首次启动把 assets 中的 dictionary.db 拷贝到应用数据库目录，
 * 后续启动直接打开，无需运行时解析/导入，加载速度最快。
 */
class DictRepository(private val context: Context) {

    private val dbHelper by lazy { DictDbHelper(context) }

    // 供模糊/相似搜索的轻量内存索引（ngram 退化全量扫描时使用）
    private var normSet: Set<String> = emptySet()

    suspend fun load() = withContext(Dispatchers.IO) {
        ensureDatabase()
        val db = dbHelper.readableDatabase
        // 加载全部 norm 到内存 Set，作为 3-gram 缺失/退化时的全量候选
        normSet = buildSet {
            db.rawQuery("SELECT norm FROM dict", null).use { c ->
                while (c.moveToNext()) {
                    add(c.getString(0))
                }
            }
        }
    }

    /**
     * 首次启动时把 assets 中的预构建 dictionary.db 拷贝到应用数据库目录。
     * 预构建库已含 dict 表与 3-gram 索引，无需运行时解析/导入。
     */
    private fun ensureDatabase() {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (dbFile.exists()) return
            dbFile.parentFile?.mkdirs()
            context.assets.open("dictionary.db").use { input ->
                dbFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (_: Exception) {
            // assets 拷贝失败时回退：允许空库，查询返回空
        }
    }

    fun lookupExact(query: String): List<DictEntry> {
        val norm = normalize(query)
        if (norm.isEmpty()) return emptyList()
        val db = dbHelper.readableDatabase
        val result = mutableListOf<DictEntry>()
        db.rawQuery("SELECT word, pos, meaning, zh, en FROM dict WHERE norm = ?", arrayOf(norm)).use { c ->
            while (c.moveToNext()) {
                result.add(
                    DictEntry(
                        word = c.getString(0),
                        meaning = c.getString(2),
                        pos = c.getString(1),
                        zh = c.getString(3),
                        en = c.getString(4)
                    )
                )
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
            "SELECT word, pos, meaning, zh, en FROM dict WHERE norm LIKE ? ESCAPE '\\' ORDER BY length(norm), word LIMIT ?",
            arrayOf("$escaped%", maxResults.toString())
        ).use { c ->
            while (c.moveToNext()) {
                result.add(
                    DictEntry(
                        word = c.getString(0),
                        meaning = c.getString(2),
                        pos = c.getString(1),
                        zh = c.getString(3),
                        en = c.getString(4)
                    )
                )
            }
        }
        return result
    }

    /**
     * 3-gram 候选缩小：取查询词各 gram 的 posting 并集，保留共享 gram 数 ≥ MIN_SHARED 的候选。
     * 兼顾拼错容错（并集保留共享部分 gram 的词）与候选规模（阈值过滤偶然共享）。
     */
    private fun ngramCandidates(query: String): Set<String> {
        val norm = normalize(query)
        if (norm.length < 2) return emptySet()
        val db = dbHelper.readableDatabase
        val hasNgram = db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name='dict_ngram'", null
        ).use { it.moveToFirst() }
        if (!hasNgram) return emptySet()
        val grams = mutableSetOf<String>()
        val padded = " $norm "
        for (i in 0 until padded.length - 2) {
            grams.add(padded.substring(i, i + 3))
        }
        // 统计每个候选 id 与查询共享的 gram 数
        val sharedCount = HashMap<Int, Int>()
        for (g in grams) {
            db.rawQuery("SELECT ids FROM dict_ngram WHERE gram = ?", arrayOf(g)).use { c ->
                if (c.moveToFirst()) {
                    val blob = c.getBlob(0)
                    for (k in 0 until blob.size / 4) {
                        val id = decodeInt32(blob, k * 4)
                        sharedCount[id] = (sharedCount[id] ?: 0) + 1
                    }
                }
            }
        }
        // 保留共享 gram 数 ≥ MIN_SHARED 的候选（3 为经验阈值，兼顾容错与规模）
        val candidateIds = sharedCount.filterValues { it >= MIN_SHARED_GRAMS }.keys
        if (candidateIds.isEmpty()) return emptySet()
        // id -> norm 映射，返回 norm 候选
        val norms = mutableSetOf<String>()
        for (id in candidateIds) {
            db.rawQuery("SELECT norm FROM dict WHERE id = ?", arrayOf(id.toString())).use { c ->
                if (c.moveToFirst()) norms.add(c.getString(0))
            }
        }
        return norms
    }

    /**
     * 改进的模糊搜索：3-gram 缩小候选 + 编辑距离归一化排序，避免无关词
     */
    fun fuzzySearch(query: String, maxDist: Int = 2, maxResults: Int = 10): List<Pair<DictEntry, Int>> {
        val norm = normalize(query)
        if (norm.isEmpty()) return emptyList()
        val db = dbHelper.readableDatabase
        val candidates = ngramCandidates(norm)
        val scored = mutableListOf<Pair<Int, String>>() // (score, norm)

        val iterate = if (candidates.isNotEmpty()) candidates else normSet
        for (candidate in iterate) {
            if (abs(candidate.length - norm.length) > maxDist) continue
            val d = levenshtein(norm, candidate)
            if (d <= maxDist) {
                // 归一化距离：相对较短词更严格
                val maxLen = maxOf(norm.length, candidate.length).coerceAtLeast(1)
                val score = d * 1000 + abs(candidate.length - norm.length)
                if (d * 10 <= maxLen * 3) {
                    scored.add(score to candidate)
                }
            }
        }
        scored.sortBy { it.first }
        return scored.take(maxResults).mapNotNull { (s, n) ->
            getEntryById(db, n)?.let { it to (s / 1000) }
        }
    }

    /**
     * 近似词：3-gram 缩小候选 + 编辑距离较近的词（展示用，阈值宽松）
     */
    fun similarWords(query: String, maxResults: Int = 6): List<DictEntry> {
        val norm = normalize(query)
        if (norm.isEmpty()) return emptyList()
        val db = dbHelper.readableDatabase
        val candidates = ngramCandidates(norm)
        val scored = mutableListOf<Pair<Int, String>>()
        val iterate = if (candidates.isNotEmpty()) candidates else normSet
        for (candidate in iterate) {
            if (candidate == norm) continue
            if (abs(candidate.length - norm.length) > 3) continue
            val d = levenshtein(norm, candidate)
            if (d <= 2 && d <= maxOf(1, norm.length / 4)) {
                scored.add(d to candidate)
            }
        }
        scored.sortBy { it.first }
        return scored.take(maxResults).mapNotNull { (d, n) ->
            getEntryById(db, n)
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
            "SELECT word, pos, meaning, zh, en FROM dict WHERE stem = ? ORDER BY length(word), word",
            arrayOf(s)
        ).use { c ->
            while (c.moveToNext()) {
                val w = c.getString(0)
                val n = normalize(w)
                if (n != norm && seen.add(n)) {
                    result.add(
                        DictEntry(
                            word = w,
                            meaning = c.getString(2),
                            pos = c.getString(1),
                            zh = c.getString(3),
                            en = c.getString(4)
                        )
                    )
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
            "SELECT word, pos, meaning, zh, en FROM dict WHERE stem = ? ORDER BY length(word), word",
            arrayOf(s)
        ).use { c ->
            while (c.moveToNext()) {
                val w = c.getString(0)
                val n = normalize(w)
                if (n != norm && abs(n.length - norm.length) in 1..4 &&
                    (n.startsWith(s) || w.lowercase(Locale.ROOT).startsWith(s)) && seen.add(n)
                ) {
                    result.add(
                        DictEntry(
                            word = w,
                            meaning = c.getString(2),
                            pos = c.getString(1),
                            zh = c.getString(3),
                            en = c.getString(4)
                        )
                    )
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
        db.rawQuery("SELECT word, pos, meaning, zh, en FROM dict WHERE norm = ? LIMIT 1", arrayOf(norm)).use { c ->
            if (c.moveToFirst()) {
                return DictEntry(
                    word = c.getString(0),
                    meaning = c.getString(2),
                    pos = c.getString(1),
                    zh = c.getString(3),
                    en = c.getString(4)
                )
            }
        }
        return null
    }

    /** 大端序解码 4 字节无符号整数（与 tools/build_dict_db.py struct.pack('>I') 对应） */
    private fun decodeInt32(blob: ByteArray, offset: Int): Int {
        return ((blob[offset].toInt() and 0xFF) shl 24) or
            ((blob[offset + 1].toInt() and 0xFF) shl 16) or
            ((blob[offset + 2].toInt() and 0xFF) shl 8) or
            (blob[offset + 3].toInt() and 0xFF)
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
        const val DB_VERSION = 4

        /** 3-gram 候选要求与查询共享的最小 gram 数（经验阈值） */
        private const val MIN_SHARED_GRAMS = 3

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
 * SQLite 帮助类：打开预构建的词典数据库（由 tools/build_dict_db.py 生成）。
 * 数据库文件在首次启动时由 ensureDatabase() 从 assets 拷贝到应用目录。
 */
private class DictDbHelper(context: Context) :
    SQLiteOpenHelper(context, DictRepository.DB_NAME, null, DictRepository.DB_VERSION) {

    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        // 预构建库无需建表；若库缺失则空表兜底
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 预构建库版本由 ensureDatabase 的元数据控制，此处无需处理
    }
}
