package com.moonfrench.dict

import android.content.Context
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

class DictRepository(private val context: Context) {

    private val entries = mutableListOf<DictEntry>()
    private val wordIndex = mutableMapOf<String, MutableList<Int>>() // normalized word -> indexes
    private val stemIndex = mutableMapOf<String, MutableList<Int>>() // stem -> indexes
    private val allWords = mutableListOf<String>()

    suspend fun load() = withContext(Dispatchers.IO) {
        val stream = context.assets.open("dict_combined.json")
        val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
        val text = reader.readText()
        reader.close()

        val arr = JSONArray(text)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val word = obj.optString("word", "").trim()
            val meaning = obj.optString("meaning", "").trim()
            if (word.isNotEmpty()) {
                val entry = DictEntry(word, meaning)
                entries.add(entry)
                val idx = entries.size - 1
                wordIndex.getOrPut(normalize(word)) { mutableListOf() }.add(idx)
                stemIndex.getOrPut(stem(word)) { mutableListOf() }.add(idx)
                allWords.add(word)
            }
        }
    }

    fun lookupExact(query: String): List<DictEntry> {
        val norm = normalize(query)
        return wordIndex[norm]?.map { entries[it] } ?: emptyList()
    }

    fun lookupPrefix(query: String, maxResults: Int = 10): List<DictEntry> {
        val norm = normalize(query)
        if (norm.isEmpty()) return emptyList()
        val results = mutableListOf<DictEntry>()
        for (i in entries.indices) {
            if (results.size >= maxResults) break
            if (normalize(entries[i].word).startsWith(norm)) {
                results.add(entries[i])
            }
        }
        return results
    }

    /**
     * 改进的模糊搜索：编辑距离归一化排序，避免无关词
     */
    fun fuzzySearch(query: String, maxDist: Int = 2, maxResults: Int = 10): List<Pair<DictEntry, Int>> {
        val norm = normalize(query)
        if (norm.isEmpty()) return emptyList()
        val scored = mutableListOf<Pair<Int, Int>>() // (score, index)

        for (i in entries.indices) {
            val candidate = normalize(entries[i].word)
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
        return scored.take(maxResults).map { (s, i) -> entries[i] to (s / 1000) }
    }

    /**
     * 近似词：编辑距离较近的词（展示用，阈值宽松）
     */
    fun similarWords(query: String, maxResults: Int = 6): List<DictEntry> {
        val norm = normalize(query)
        if (norm.isEmpty()) return emptyList()
        val scored = mutableListOf<Pair<Int, Int>>()
        for (i in entries.indices) {
            val candidate = normalize(entries[i].word)
            if (candidate == norm) continue
            if (abs(candidate.length - norm.length) > 3) continue
            val d = levenshtein(norm, candidate)
            if (d <= 2 && d <= maxOf(1, norm.length / 4)) {
                scored.add(d to i)
            }
        }
        scored.sortBy { it.first }
        return scored.take(maxResults).map { entries[it.second] }
    }

    /**
     * 同词根词：共享相同词干的词
     */
    fun relatedWords(query: String, maxResults: Int = 6): List<DictEntry> {
        val s = stem(query)
        if (s.length < 3) return emptyList()
        val results = stemIndex[s]
            ?.map { entries[it] }
            ?.filter { normalize(it.word) != normalize(query) }
            ?.distinctBy { normalize(it.word) }
            ?.take(maxResults)
            ?: emptyList()
        return results
    }

    /**
     * 派生词：共享词干且词长差异在1-4之间（前缀/后缀变化）
     */
    fun derivedWords(query: String, maxResults: Int = 6): List<DictEntry> {
        val norm = normalize(query)
        val s = stem(query)
        if (s.length < 3) return emptyList()
        val results = stemIndex[s]
            ?.map { entries[it] }
            ?.filter { e ->
                val n = normalize(e.word)
                n != norm && abs(n.length - norm.length) in 1..4 && (n.startsWith(s) || e.word.lowercase(Locale.ROOT).startsWith(s))
            }
            ?.distinctBy { normalize(it.word) }
            ?.take(maxResults)
            ?: emptyList()
        return results
    }

    fun allWords(): List<String> = allWords

    fun size(): Int = entries.size

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

    companion object {
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
