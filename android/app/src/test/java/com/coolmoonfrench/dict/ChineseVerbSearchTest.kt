package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseVerbSearchTest {

    private val conj = VerbConjugator()

    private fun row(word: String, zh: String, pos: String = "v.") = DbVerbRow(word, pos, zh, "")
    private fun cand(word: String, source: String) =
        VerbCandidate(word, "v.", "释义", "/x/", "", source)

    // ------------------------------------------------------------------
    // 最长公共子串
    // ------------------------------------------------------------------

    @Test
    fun `lcs exact lemma`() {
        assertEquals(2, VerbCandidateRanker.longestCommonSubstringLength("喜欢", "喜欢，爱"))
    }

    @Test
    fun `lcs finds verb inside phrase`() {
        assertEquals(2, VerbCandidateRanker.longestCommonSubstringLength("我喜欢你", "喜欢，爱"))
    }

    @Test
    fun `lcs empty or unrelated`() {
        assertEquals(0, VerbCandidateRanker.longestCommonSubstringLength("", "abc"))
        assertEquals(0, VerbCandidateRanker.longestCommonSubstringLength("x", ""))
        assertEquals(0, VerbCandidateRanker.longestCommonSubstringLength("abc", "xyz"))
    }

    // ------------------------------------------------------------------
    // 本地排序
    // ------------------------------------------------------------------

    @Test
    fun `rank lemma keeps matches drops unrelated and prefers prefix`() {
        val rows = listOf(
            row("aimer", "喜欢，爱"),
            row("adorer", "热爱，喜欢"),
            row("manger", "吃"),
            row("parler", "说话")
        )
        val words = VerbCandidateRanker.rankLocal(rows, "喜欢", 8).map { it.infinitive }
        assertTrue(words.contains("aimer"))
        assertTrue(words.contains("adorer"))
        assertFalse(words.contains("manger"))
        assertEquals("aimer", words.first())
    }

    @Test
    fun `rank phrase matches contained verb`() {
        val rows = listOf(row("aimer", "喜欢，爱"), row("manger", "吃"))
        assertEquals(listOf("aimer"), VerbCandidateRanker.rankLocal(rows, "我喜欢你", 8).map { it.infinitive })
    }

    @Test
    fun `rank single char verb matches`() {
        val rows = listOf(row("aimer", "爱"), row("manger", "吃"))
        assertEquals(listOf("aimer"), VerbCandidateRanker.rankLocal(rows, "爱", 8).map { it.infinitive })
    }

    @Test
    fun `rank dedupes same infinitive and respects limit`() {
        val rows = listOf(
            row("aimer", "喜欢"),
            row("aimer", "喜欢，爱"),
            row("adorer", "喜欢"),
            row("admirer", "喜欢")
        )
        val out = VerbCandidateRanker.rankLocal(rows, "喜欢", 2)
        assertEquals(2, out.size)
        assertEquals(2, out.map { it.infinitive }.distinct().size)
    }

    @Test
    fun `rank empty query returns empty`() {
        assertTrue(VerbCandidateRanker.rankLocal(listOf(row("aimer", "喜欢")), "  ", 8).isEmpty())
    }

    @Test
    fun `rank fills meaning ipa and source`() {
        val out = VerbCandidateRanker.rankLocal(listOf(row("aimer", "喜欢")), "喜欢", 8)
        assertEquals(1, out.size)
        assertEquals("aimer", out[0].infinitive)
        assertTrue(out[0].meaning.contains("喜欢"))
        assertTrue(out[0].ipa.isNotEmpty())
        assertEquals("local", out[0].source)
    }

    // ------------------------------------------------------------------
    // AI 回复解析
    // ------------------------------------------------------------------

    @Test
    fun `parse standard json`() {
        val reply = """{"core":"喜欢","candidates":[{"word":"aimer","pos":"v.","zh":"喜欢，爱","ipa":"/eme/","example":"J'aime le chocolat."}]}"""
        val p = VerbCandidateParser.parse(reply, conj)
        assertEquals("喜欢", p.core)
        assertEquals(1, p.candidates.size)
        assertEquals("aimer", p.candidates[0].infinitive)
        assertEquals("/eme/", p.candidates[0].ipa)
        assertEquals("ai", p.candidates[0].source)
    }

    @Test
    fun `parse strips code fences and surrounding text`() {
        val reply = "好的，结果如下：\n```json\n{\"core\":\"吃\",\"candidates\":[{\"word\":\"manger\",\"pos\":\"v.\",\"zh\":\"吃\"}]}\n```\n希望有帮助"
        val p = VerbCandidateParser.parse(reply, conj)
        assertEquals("吃", p.core)
        assertEquals(listOf("manger"), p.candidates.map { it.infinitive })
    }

    @Test
    fun `parse drops invalid words and dedupes`() {
        val reply = """{"core":"","candidates":[{"word":"zzzz"},{"word":"aimer","zh":"喜欢"},{"word":"aimer","zh":"喜欢"}]}"""
        val p = VerbCandidateParser.parse(reply, conj)
        assertEquals(listOf("aimer"), p.candidates.map { it.infinitive })
    }

    @Test
    fun `parse normalizes variant to infinitive`() {
        val reply = """{"core":"","candidates":[{"word":"mange","zh":"吃"}]}"""
        val p = VerbCandidateParser.parse(reply, conj)
        assertEquals(listOf("manger"), p.candidates.map { it.infinitive })
    }

    @Test
    fun `parse fills ipa when missing`() {
        val reply = """{"core":"","candidates":[{"word":"aimer"}]}"""
        val p = VerbCandidateParser.parse(reply, conj)
        assertEquals(1, p.candidates.size)
        assertTrue(p.candidates[0].ipa.startsWith("/"))
    }

    @Test
    fun `parse garbage returns empty`() {
        val p = VerbCandidateParser.parse("not json at all", conj)
        assertTrue(p.candidates.isEmpty())
        assertEquals("", p.core)
    }

    @Test
    fun `parse missing candidates array returns core only`() {
        val p = VerbCandidateParser.parse("""{"core":"走"}""", conj)
        assertEquals("走", p.core)
        assertTrue(p.candidates.isEmpty())
    }

    @Test
    fun `parse accepts meaning key fallback`() {
        val reply = """{"core":"","candidates":[{"word":"aimer","meaning":"喜欢"}]}"""
        val p = VerbCandidateParser.parse(reply, conj)
        assertEquals("喜欢", p.candidates[0].meaning)
    }

    // ------------------------------------------------------------------
    // 合并
    // ------------------------------------------------------------------

    @Test
    fun `merge ai first then local dedup`() {
        val local = listOf(cand("aimer", "local"), cand("adorer", "local"), cand("manger", "local"))
        val ai = listOf(cand("aimer", "ai"), cand("adorer", "ai"))
        val out = VerbCandidateMerger.merge(local, ai, 8)
        assertEquals(listOf("aimer", "adorer", "manger"), out.map { it.infinitive })
        assertEquals("ai", out[0].source)
    }

    @Test
    fun `merge respects limit`() {
        val ai = (1..10).map { cand("v$it", "ai") }
        assertEquals(8, VerbCandidateMerger.merge(emptyList(), ai, 8).size)
    }

    @Test
    fun `merge falls back to local when ai empty`() {
        assertEquals(
            listOf("aimer"),
            VerbCandidateMerger.merge(listOf(cand("aimer", "local")), emptyList(), 8).map { it.infinitive }
        )
    }

    @Test
    fun `merge normalizes case when deduping`() {
        assertEquals(1, VerbCandidateMerger.merge(listOf(cand("aimer", "local")), listOf(cand("Aimer", "ai")), 8).size)
    }
}
