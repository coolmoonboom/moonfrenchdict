package com.coolmoonfrench.dict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.Locale

/** 词典条目（与 Android 端展示口径一致：中文优先，英文兜底）。 */
data class DictEntry(
    val word: String,
    val meaning: String,
    val pos: String = "",
    val zh: String = "",
    val en: String = ""
) {
    companion object {
        fun combineMeaning(zh: String, en: String, pos: String): String {
            val primary = if (zh.isNotBlank()) zh else en
            return if (pos.isNotBlank()) "【$pos】$primary" else primary
        }
    }
}

/** 中文动词内存索引的一行。 */
data class DbVerbRow(val word: String, val pos: String, val zh: String, val en: String)

/**
 * 桌面端词典仓库：首启把 classpath 内置的 dictionary.db 释放到 ~/.moonfrenchdict，
 * 之后经 sqlite-jdbc 打开。查询 SQL 与 Android 端保持同一套。
 */
class DictRepository {

    private var conn: Connection? = null

    private var verbRows: List<DbVerbRow> = emptyList()
    @Volatile
    private var verbIndexReady = false
    private val indexLock = Any()

    fun ensureReady() {
        if (conn == null) {
            synchronized(this) {
                if (conn == null) {
                    ensureDatabase()
                    conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile().absolutePath)
                }
            }
        }
    }

    fun close() {
        conn?.close()
        conn = null
    }

    private fun dbFile(): File =
        File(System.getProperty("user.home"), ".moonfrenchdict/dictionary.db")

    private fun ensureDatabase() {
        val f = dbFile()
        if (f.exists() && f.length() > 1_000_000) return
        f.parentFile?.mkdirs()
        val res = DictRepository::class.java.getResourceAsStream("/dictionary.db")
            ?: error("内置 dictionary.db 缺失，请检查 desktop 模块资源配置")
        res.use { input -> f.outputStream().use { output -> input.copyTo(output) } }
    }

    fun lookupExact(query: String): List<DictEntry> {
        val norm = normalize(query)
        if (norm.isEmpty()) return emptyList()
        ensureReady()
        val out = ArrayList<DictEntry>()
        conn!!.prepareStatement("SELECT word, pos, zh, en FROM dict WHERE norm = ?").use { ps ->
            ps.setString(1, norm)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val pos = rs.getString(2) ?: ""
                    val zh = rs.getString(3) ?: ""
                    val en = rs.getString(4) ?: ""
                    out.add(
                        DictEntry(
                            word = rs.getString(1),
                            meaning = DictEntry.combineMeaning(zh, en, pos),
                            pos = pos, zh = zh, en = en
                        )
                    )
                }
            }
        }
        return out
    }

    /** 中文动词候选（本地部分），逻辑与 Android 端一致。 */
    suspend fun searchVerbsByChinese(
        query: String,
        limit: Int = ChineseVerbSearch.MAX_CANDIDATES
    ): List<VerbCandidate> {
        val q = query.trim()
        if (q.isEmpty() || limit <= 0) return emptyList()
        withContext(Dispatchers.IO) { ensureChineseVerbIndex() }
        val rows = verbRows
        if (rows.isEmpty()) return emptyList()
        return withContext(Dispatchers.Default) { VerbCandidateRanker.rankLocal(rows, q, limit) }
    }

    private fun ensureChineseVerbIndex() {
        if (verbIndexReady) return
        synchronized(indexLock) {
            if (verbIndexReady) return
            ensureReady()
            val rows = ArrayList<DbVerbRow>(12000)
            conn!!.createStatement().use { st ->
                st.executeQuery(
                    "SELECT word, pos, zh, en FROM dict " +
                        "WHERE zh IS NOT NULL AND zh<>'' AND (pos='verb' OR pos LIKE 'v.%')"
                ).use { rs ->
                    while (rs.next()) {
                        rows.add(
                            DbVerbRow(
                                word = rs.getString(1) ?: "",
                                pos = rs.getString(2) ?: "",
                                zh = rs.getString(3) ?: "",
                                en = rs.getString(4) ?: ""
                            )
                        )
                    }
                }
            }
            verbRows = rows
            verbIndexReady = true
        }
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
            for (c in s.lowercase(Locale.ROOT)) sb.append(ACCENT_MAP[c] ?: c)
            return sb.toString()
        }
    }
}

// ---------------------------------------------------------------------------
// AI 依赖的桌面桩：试水阶段仅本地候选，网络与密钥配置后续再接
// ---------------------------------------------------------------------------

data class AIModelConfig(
    val apiBase: String = "",
    val apiKey: String = "",
    val model: String = ""
)

data class AIMessage(val role: String, val content: String)

object IpaService {
    fun isConfigured(config: AIModelConfig?): Boolean = false
}

object AIClient {
    suspend fun chat(config: AIModelConfig, messages: List<AIMessage>): String =
        throw UnsupportedOperationException("桌面试水版暂未接入 AI")
}
