package com.coolmoonfrench.dict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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

/** 中文动词内存索引的一行：词典词形 + 词性 + 中文/英文释义。 */
data class DbVerbRow(
    val word: String,
    val pos: String,
    val zh: String,
    val en: String
)

/**
 * 词典仓库。
 *
 * 使用预构建的 SQLite 词典库（tools/build_dict_db.py 生成 dictionary.db）：
 * 首次启动把 assets 中的 dictionary.db 拷贝到应用数据库目录，
 * 后续启动直接打开，无需运行时解析/导入，加载速度最快。
 */
class DictRepository(private val context: Context) {

    private val dbHelper by lazy { DictDbHelper(context) }

    // 供模糊/相似搜索的轻量内存索引（后台构建，不阻塞首屏）
    private var normSet: Set<String> = emptySet()
    private var normById: HashMap<Int, String> = HashMap()
    private val indexLock = Any()
    @Volatile
    private var indexReady = false

    // 供中文动词查询的内存索引（仅带中文释义的动词行，约 1 万条）
    private var verbRows: List<DbVerbRow> = emptyList()
    @Volatile
    private var verbIndexReady = false

    /**
     * 确保数据库就绪：首次把 assets 中的 dictionary.db 拷贝到应用目录并打开。
     * 启动时只等这一步，快速进入界面，不走全表扫描。
     */
    suspend fun ensureReady() = withContext(Dispatchers.IO) {
        ensureDatabase()
        dbHelper.readableDatabase
    }

    /**
     * 构建模糊搜索内存索引（全表扫描 id+norm，幂等）。
     * 首次调用在调用线程完成，调用方需保证处于 IO 线程。
     */
    fun ensureIndex() {
        if (indexReady) return
        synchronized(indexLock) {
            if (indexReady) return
            dbHelper.readableDatabase.rawQuery("SELECT id, norm FROM dict", null).use { c ->
                val nMap = HashMap<Int, String>()
                val normSetLocal = HashSet<String>()
                while (c.moveToNext()) {
                    val id = c.getInt(0)
                    val norm = c.getString(1)
                    nMap[id] = norm
                    normSetLocal.add(norm)
                }
                normById = nMap
                normSet = normSetLocal
            }
            indexReady = true
        }
    }

    /** 后台线程构建索引（首屏显示后调用） */
    suspend fun ensureIndexInBackground() = withContext(Dispatchers.IO) { ensureIndex() }

    /** 后台线程预热中文动词索引，避免首次中文查询时才扫描。 */
    suspend fun prewarmChineseVerbIndex() = withContext(Dispatchers.IO) { ensureChineseVerbIndex() }

    /**
     * 构建中文动词内存索引（只取带中文释义的动词行，幂等）。
     * 全表扫描但已用 zh<>'' 过滤，约 1 万条，耗时可控。
     */
    fun ensureChineseVerbIndex() {
        if (verbIndexReady) return
        synchronized(indexLock) {
            if (verbIndexReady) return
            dbHelper.readableDatabase.rawQuery(
                "SELECT word, pos, zh, en FROM dict " +
                    "WHERE zh IS NOT NULL AND zh<>'' AND (pos='verb' OR pos LIKE 'v.%')",
                null
            ).use { c ->
                val rows = ArrayList<DbVerbRow>(12000)
                while (c.moveToNext()) {
                    rows.add(
                        DbVerbRow(
                            word = c.getString(0) ?: "",
                            pos = c.getString(1) ?: "",
                            zh = c.getString(2) ?: "",
                            en = c.getString(3) ?: ""
                        )
                    )
                }
                verbRows = rows
            }
            verbIndexReady = true
        }
    }

    /**
     * 中文查询本地候选：在动词中文释义上做最长公共子串匹配，按相关度排序。
     * 支持中文词（喜欢）与中文短语（我喜欢你）两种粒度。
     */
    suspend fun searchVerbsByChinese(query: String, limit: Int = ChineseVerbSearch.MAX_CANDIDATES): List<VerbCandidate> {
        val q = query.trim()
        if (q.isEmpty() || limit <= 0) return emptyList()
        withContext(Dispatchers.IO) { ensureChineseVerbIndex() }
        val rows = verbRows
        if (rows.isEmpty()) return emptyList()
        return withContext(Dispatchers.Default) { VerbCandidateRanker.rankLocal(rows, q, limit) }
    }

    /**
     * 确保 assets 中的预构建 dictionary.db 已拷贝到应用数据库目录，并保持为「当前随包版本」。
     *
     * 只要设备上已有旧版本词典（例如早期安装遗留、词条缺少重音符号），且未做版本标记，
     * 就删除旧库重新拷贝，保证显示的词条与本 APK 内置词典一致。
     */
    private fun ensureDatabase() {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            val prefs = context.getSharedPreferences("dict_db_meta", Context.MODE_PRIVATE)
            val appliedVersion = prefs.getInt("bundled_version", 0)
            val valid = dbFile.exists() && runCatching {
                val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
                val hasNgram = db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='dict_ngram'", null
                ).use { it.moveToFirst() }
                db.close()
                hasNgram
            }.getOrDefault(false)
            if (valid && appliedVersion >= BUNDLED_DB_VERSION) return

            if (dbFile.exists()) dbFile.delete()
            // 旧库的 WAL/SHM 残留一并清理，避免打开到过期页
            java.io.File(dbFile.path + "-wal").delete()
            java.io.File(dbFile.path + "-shm").delete()
            dbFile.parentFile?.mkdirs()
            context.assets.open("dictionary.db").use { input ->
                dbFile.outputStream().use { output -> input.copyTo(output) }
            }
            prefs.edit().putInt("bundled_version", BUNDLED_DB_VERSION).apply()
        } catch (_: Exception) {
            // assets 拷贝失败时回退：允许空库，查询返回空
        }
    }

    /** 从查询游标当前行构造 DictEntry（无 meaning 列，现算释义） */
    private fun rowToEntry(c: android.database.Cursor): DictEntry {
        val word = c.getString(0)
        val pos = c.getString(1)
        val zh = c.getString(2)
        val en = c.getString(3)
        return DictEntry(
            word = word,
            pos = pos,
            zh = zh,
            en = en,
            meaning = DictEntry.combineMeaning(zh, en, pos)
        )
    }

    fun lookupExact(query: String): List<DictEntry> {
        val norm = normalize(query)
        if (norm.isEmpty()) return emptyList()
        val db = dbHelper.readableDatabase
        val result = mutableListOf<DictEntry>()
        db.rawQuery("SELECT word, pos, zh, en FROM dict WHERE norm = ?", arrayOf(norm)).use { c ->
            while (c.moveToNext()) {
                result.add(rowToEntry(c))
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
            "SELECT word, pos, zh, en FROM dict WHERE norm LIKE ? ESCAPE '\\' ORDER BY length(norm), word LIMIT ?",
            arrayOf("$escaped%", maxResults.toString())
        ).use { c ->
            while (c.moveToNext()) {
                result.add(rowToEntry(c))
            }
        }
        return result
    }

    /**
     * 3-gram 候选缩小：取查询词各 gram 的 posting 并集，保留共享 gram 数 ≥ MIN_SHARED 的候选。
     * 返回候选 norm 集合（内存映射），避免逐 id 查库。
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
        // 通过内存映射 id -> norm，避免逐 id 查库
        val norms = HashSet<String>(candidateIds.size)
        for (id in candidateIds) {
            normById[id]?.let { norms.add(it) }
        }
        return norms
    }

    /**
     * 改进的模糊搜索：3-gram 缩小候选 + 编辑距离归一化排序，避免无关词
     */
    fun fuzzySearch(query: String, maxDist: Int = 2, maxResults: Int = 10): List<Pair<DictEntry, Int>> {
        ensureIndex()
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
        ensureIndex()
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
            "SELECT word, pos, zh, en FROM dict WHERE stem = ? ORDER BY length(word), word",
            arrayOf(s)
        ).use { c ->
            while (c.moveToNext()) {
                val w = c.getString(0)
                val n = normalize(w)
                if (n != norm && seen.add(n)) {
                    result.add(rowToEntry(c))
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
            "SELECT word, pos, zh, en FROM dict WHERE stem = ? ORDER BY length(word), word",
            arrayOf(s)
        ).use { c ->
            while (c.moveToNext()) {
                val w = c.getString(0)
                val n = normalize(w)
                if (n != norm && abs(n.length - norm.length) in 1..4 &&
                    (n.startsWith(s) || w.lowercase(Locale.ROOT).startsWith(s)) && seen.add(n)
                ) {
                    result.add(rowToEntry(c))
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
        db.rawQuery("SELECT word, pos, zh, en FROM dict WHERE norm = ? LIMIT 1", arrayOf(norm)).use { c ->
            if (c.moveToFirst()) {
                return rowToEntry(c)
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

    /** 词书词条索引（懒加载）：词典收录不全时收藏列表用词书兜底 */
    private val vocabIndex: Map<String, VocabEntry> by lazy {
        runCatching {
            VocabData.load(context).entries.associateBy { it.word.lowercase() }
        }.getOrElse { emptyMap() }
    }

    fun loadFavorites(): List<DictEntry> {
        val times = loadWordTimes()
        val meanings = loadMeanings()
        return favoriteWords()
            .mapNotNull { word ->
                val entry = lookupExact(word).firstOrNull()
                    ?: vocabIndex[word.lowercase()]?.let { v ->
                        DictEntry(
                            word = v.word,
                            pos = v.pos,
                            zh = v.meaning,
                            en = "",
                            meaning = v.meaning
                        )
                    }
                    ?: meanings[word]?.takeIf { it.isNotBlank() }?.let { m ->
                        DictEntry(word = word, pos = "", zh = m, en = "", meaning = m)
                    }
                // 词典/词书命中但释义为空时，用收藏时记录的中文兜底
                val filled = entry?.let { e ->
                    if (e.meaning.isBlank() && !meanings[word].isNullOrBlank()) {
                        e.copy(meaning = meanings[word].orEmpty(), zh = meanings[word].orEmpty())
                    } else {
                        e
                    }
                }
                filled?.let { it to (times[word] ?: 0L) }
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /** 收藏时间表（word -> epoch millis），用于「新→旧」排序。 */
    private fun loadWordTimes(): MutableMap<String, Long> {
        val json = prefs.getString("word_times", "{}") ?: "{}"
        return runCatching {
            val o = JSONObject(json)
            val m = mutableMapOf<String, Long>()
            o.keys().forEach { k -> m[k] = o.optLong(k, 0L) }
            m
        }.getOrElse { mutableMapOf() }
    }

    private fun timesJson(map: Map<String, Long>): String {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }

    /** 收藏词的中文释义表（word -> meaning）；词典查不到时兜底显示/朗读。 */
    private fun loadMeanings(): MutableMap<String, String> {
        val json = prefs.getString("meanings", "{}") ?: "{}"
        return runCatching {
            val o = JSONObject(json)
            val m = mutableMapOf<String, String>()
            o.keys().forEach { k -> m[k] = o.optString(k, "") }
            m
        }.getOrElse { mutableMapOf() }
    }

    private fun meaningsJson(map: Map<String, String>): String {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }

    fun addFavorite(word: String, meaning: String = "") {
        val set = prefs.getStringSet("words", emptySet())?.toMutableSet() ?: mutableSetOf()
        val isNew = set.add(word)
        val times = loadWordTimes()
        if (isNew || !times.containsKey(word)) {
            times[word] = System.currentTimeMillis()
        }
        val meanings = loadMeanings()
        val m = meaning.trim()
        if (m.isNotEmpty()) meanings[word] = m
        prefs.edit()
            .putStringSet("words", set)
            .putString("word_times", timesJson(times))
            .putString("meanings", meaningsJson(meanings))
            .apply()
    }

    fun removeFavorite(word: String) {
        val set = prefs.getStringSet("words", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(word)
        val times = loadWordTimes().apply { remove(word) }
        val meanings = loadMeanings().apply { remove(word) }
        prefs.edit()
            .putStringSet("words", set)
            .putString("word_times", timesJson(times))
            .putString("meanings", meaningsJson(meanings))
            .apply()
    }

    fun isFavorite(word: String): Boolean {
        return prefs.getStringSet("words", emptySet())?.contains(word) ?: false
    }

    /** 收藏词原样集合（用于同步打包） */
    fun favoriteWords(): Set<String> =
        prefs.getStringSet("words", emptySet()) ?: emptySet()

    /** 整体替换收藏词（用于同步解包/合并回写）；保留已有时间戳，新词以当前时间兜底。 */
    fun replaceFavorites(words: Collection<String>) {
        val newSet = words.toSet()
        val oldTimes = loadWordTimes()
        val now = System.currentTimeMillis()
        val newTimes = newSet.associateWith { oldTimes[it] ?: now }
        val newMeanings = loadMeanings().filterKeys { it in newSet }
        prefs.edit()
            .putStringSet("words", newSet)
            .putString("word_times", timesJson(newTimes))
            .putString("meanings", meaningsJson(newMeanings))
            .apply()
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

    /** 查词历史原样词表（新→旧，用于同步打包，不依赖词库命中） */
    fun historyWords(): List<String> {
        val json = historyPrefs.getString("words", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.optString(i))
        return list
    }

    /** 整体替换查词历史（用于同步解包/合并回写） */
    fun replaceHistory(words: List<String>) {
        historyPrefs.edit().putString("words", JSONArray(words).toString()).apply()
    }

/** 记录一次查词（历史无上限，全部保留） */
    fun addHistory(word: String, limit: Int = Int.MAX_VALUE) {
        val norm = word.trim()
        if (norm.isEmpty()) return
        val json = historyPrefs.getString("words", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.optString(i))
        list.removeAll { it.equals(norm, ignoreCase = true) }
        list.add(0, norm)
        if (list.size > limit) list.subList(limit, list.size).clear()
        historyPrefs.edit().putString("words", JSONArray(list).toString()).apply()
    }

    /** 清空查词历史 */
    fun clearHistory() {
        historyPrefs.edit().putString("words", "[]").apply()
    }

    companion object {
        const val DB_NAME = "dictionary.db"
        const val DB_VERSION = 4

        /**
         * 随包内置词典的内容版本。每次更新 `assets/dictionary.db` 后 +1，
         * 启动时若设备上的已拷贝版本低于此值，则删除旧库重新拷贝（修复旧安装遗留的脏数据）。
         */
        const val BUNDLED_DB_VERSION = 2

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
