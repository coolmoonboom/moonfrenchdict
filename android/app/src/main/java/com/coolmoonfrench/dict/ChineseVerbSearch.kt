package com.coolmoonfrench.dict

import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** 一个候选法语动词：含义与中文查询相同或相近。 */
data class VerbCandidate(
    val infinitive: String,
    val pos: String,
    val meaning: String,
    val ipa: String,
    val example: String = "",
    val source: String = "local"
)

/** 一次中文查询的完整结果。 */
data class CandidateResult(
    val candidates: List<VerbCandidate>,
    val coreMeaning: String = "",
    val aiAttempted: Boolean = false,
    val aiError: String? = null
)

/**
 * 中文动词查询：本地词典优先、AI 补充。
 *
 * 支持两种输入粒度：
 * - 中文词查询：「喜欢」「吃」
 * - 中文短语查询：「我喜欢你」「去散步」（AI 提取核心动词；无 AI 时本地子串匹配）
 */
object ChineseVerbSearch {

    const val MAX_CANDIDATES = 8

    suspend fun find(
        query: String,
        repository: DictRepository,
        conjugator: VerbConjugator,
        config: AIModelConfig?
    ): CandidateResult {
        val q = query.trim()
        if (q.isEmpty()) return CandidateResult(emptyList())

        val local = repository.searchVerbsByChinese(q, MAX_CANDIDATES)

        var core = ""
        var aiCandidates: List<VerbCandidate> = emptyList()
        var aiAttempted = false
        var aiError: String? = null

        if (config != null && IpaService.isConfigured(config)) {
            aiAttempted = true
            try {
                val parsed = VerbCandidateParser.parse(
                    AIClient.chat(config, listOf(AIMessage("user", buildPrompt(q)))),
                    conjugator
                )
                core = parsed.core
                aiCandidates = fillMissingMeaning(parsed.candidates, repository)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aiError = e.message?.take(100) ?: "AI 查询失败"
            }
        }

        val merged = VerbCandidateMerger.merge(local, aiCandidates, MAX_CANDIDATES)
        return CandidateResult(merged, core, aiAttempted, aiError)
    }

    /** AI 未给出中文释义时用本地词典补全，保证卡片信息完整。 */
    private fun fillMissingMeaning(
        candidates: List<VerbCandidate>,
        repository: DictRepository
    ): List<VerbCandidate> = candidates.map { c ->
        if (c.meaning.isBlank()) {
            val local = repository.lookupExact(c.infinitive).firstOrNull()
            if (local != null) c.copy(meaning = local.meaning, pos = c.pos.ifBlank { local.pos }) else c
        } else {
            c
        }
    }

    private fun buildPrompt(query: String): String = """
        你是法语动词助手。用户给出中文，可能是单个动词，也可能是短语或短句。
        请提取其中表达的核心动作含义，并给出对应的法语动词不定式候选。
        要求：
        1. 只输出一个 JSON 对象，不要解释，不要代码块标记。
        2. JSON 格式：{"core":"核心动词中文含义","candidates":[{"word":"不定式","pos":"词性","zh":"中文释义","ipa":"/音标/","example":"一句简短法语例句"}]}
        3. word 必须是动词不定式；按与中文的契合度从高到低排序；给出 5~8 个候选。
        4. ipa 用一对斜杠包裹；example 用一句自然简短的法语例句。
        5. 无法确定核心动作含义时，core 填空字符串。

        用户输入：$query
    """.trimIndent()
}

/**
 * 本地候选排序：以「查询与中文释义的最长公共子串」作为相关度代理。
 * 阈值 = min(2, 查询长度)，保证单字动词（爱、吃、走）也能命中。
 */
internal object VerbCandidateRanker {

    fun rankLocal(rows: List<DbVerbRow>, query: String, limit: Int): List<VerbCandidate> {
        val q = query.trim()
        if (q.isEmpty() || limit <= 0) return emptyList()
        val minMatch = minOf(2, q.length)
        val scored = ArrayList<Scored>(rows.size)
        for (r in rows) {
            val zh = r.zh
            if (zh.isEmpty()) continue
            val lcs = longestCommonSubstringLength(q, zh)
            if (lcs < minMatch) continue
            scored.add(Scored(r, lcs, zh.startsWith(q)))
        }
        scored.sortWith(
            compareByDescending<Scored> { it.lcs }
                .thenByDescending { it.prefix }
                .thenBy { it.row.zh.length }
                .thenBy { it.row.word }
        )
        val seen = HashSet<String>()
        val out = ArrayList<VerbCandidate>(limit)
        for (s in scored) {
            if (!seen.add(DictRepository.normalize(s.row.word))) continue
            out.add(
                VerbCandidate(
                    infinitive = s.row.word,
                    pos = s.row.pos,
                    meaning = DictEntry.combineMeaning(s.row.zh, s.row.en, s.row.pos),
                    ipa = FrenchIpa.wrap(s.row.word),
                    source = "local"
                )
            )
            if (out.size >= limit) break
        }
        return out
    }

    private class Scored(val row: DbVerbRow, val lcs: Int, val prefix: Boolean)

    /** 标准 DP 最长公共子串长度，使用滚动数组，空间 O(m)。 */
    internal fun longestCommonSubstringLength(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        var prev = IntArray(b.length + 1)
        var cur = IntArray(b.length + 1)
        var best = 0
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                cur[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1 else 0
                if (cur[j] > best) best = cur[j]
            }
            val t = prev; prev = cur; cur = t
            java.util.Arrays.fill(cur, 0)
        }
        return best
    }
}

/** 宽松解析 AI 回复：去代码块、取 JSON、归一化动词、去重。 */
internal object VerbCandidateParser {

    data class Parsed(val core: String, val candidates: List<VerbCandidate>)

    fun parse(reply: String, conjugator: VerbConjugator): Parsed {
        val cleaned = reply.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return Parsed("", emptyList())
        return try {
            val o = JSONObject(cleaned.substring(start, end + 1))
            val core = o.optString("core", "").trim()
            val arr = o.optJSONArray("candidates") ?: return Parsed(core, emptyList())
            val seen = HashSet<String>()
            val out = ArrayList<VerbCandidate>(arr.length())
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val raw = e.optString("word", "").trim()
                if (raw.isEmpty()) continue
                val inf = normalizeToInfinitive(raw, conjugator) ?: continue
                if (!seen.add(DictRepository.normalize(inf))) continue
                val pos = e.optString("pos", "").trim()
                val zh = e.optString("zh", "").trim().ifBlank { e.optString("meaning", "").trim() }
                var ipa = e.optString("ipa", "").trim()
                if (ipa.isNotBlank() && !ipa.startsWith("/")) ipa = "/$ipa/"
                if (ipa.isBlank()) ipa = FrenchIpa.wrap(inf)
                out.add(
                    VerbCandidate(
                        infinitive = inf,
                        pos = pos,
                        meaning = zh,
                        ipa = ipa,
                        example = e.optString("example", "").trim(),
                        source = "ai"
                    )
                )
            }
            Parsed(core, out)
        } catch (e: Exception) {
            Parsed("", emptyList())
        }
    }

    private fun normalizeToInfinitive(word: String, conjugator: VerbConjugator): String? {
        val w = word.trim()
        if (w.isEmpty()) return null
        if (conjugator.isVerb(w)) return w
        return conjugator.findInfinitive(w)
    }
}

/** 合并本地与 AI 候选：AI 优先，本地去重补充，统一截断。 */
internal object VerbCandidateMerger {

    fun merge(local: List<VerbCandidate>, ai: List<VerbCandidate>, limit: Int): List<VerbCandidate> {
        if (limit <= 0) return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<VerbCandidate>(limit)
        for (c in ai) {
            if (!seen.add(DictRepository.normalize(c.infinitive))) continue
            out.add(c)
            if (out.size >= limit) return out
        }
        for (c in local) {
            if (!seen.add(DictRepository.normalize(c.infinitive))) continue
            out.add(c)
            if (out.size >= limit) return out
        }
        return out
    }
}
