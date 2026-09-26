package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportWordParserTest {

    @Test
    fun `parse extracts word pos ipa meaning and examples`() {
        val json = """[
            {"word":"château","pos":"n.m.","ipa":"/ʃɑto/","meaning":"城堡","example":"Nous visitons un château.","example_zh":"我们参观一座城堡。"}
        ]"""
        val list = ImportWordParser.parse(json)
        assertEquals(1, list.size)
        val w = list[0]
        assertEquals("château", w.word)
        assertEquals("n.m.", w.pos)
        assertEquals("/ʃɑto/", w.ipa)
        assertEquals("城堡", w.meaning)
        assertEquals("Nous visitons un château.", w.example)
        assertEquals("我们参观一座城堡。", w.exampleZh)
    }

    @Test
    fun `parse keeps accents and skips entries without word`() {
        val json = """[
            {"word":"applaudir","pos":"v.t.","ipa":"/aplɔdiʁ/","meaning":"鼓掌","example":"Le public applaudit.","example_zh":"观众鼓掌。"},
            {"word":"","pos":"adj.","ipa":"","meaning":"无词条目应被跳过","example":"","example_zh":""}
        ]"""
        val list = ImportWordParser.parse(json)
        assertEquals(1, list.size)
        assertEquals("applaudir", list[0].word)
    }

    @Test
    fun `parse tolerates code fence and blank fields`() {
        val json = "```json\n[{\"word\":\"rêve\",\"pos\":\"n.m.\",\"ipa\":\"/ʁɛv/\",\"meaning\":\"梦\",\"example\":\"\",\"example_zh\":\"\"}]\n```"
        val list = ImportWordParser.parse(json)
        assertEquals(1, list.size)
        assertEquals("rêve", list[0].word)
        assertEquals("", list[0].example)
    }

    @Test
    fun `buildMeaning formats pos ipa and examples`() {
        val w = ImportedWord("bonjour", "interj.", "/bɔ̃ʒuʁ/", "你好", "Bonjour tout le monde !", "大家好！")
        val m = ImportWordParser.buildMeaning(w)
        assertTrue(m.contains("【interj.】你好"))
        assertTrue(m.contains("音标 /bɔ̃ʒuʁ/"))
        assertTrue(m.contains("例句：Bonjour tout le monde !"))
        assertTrue(m.contains("中文：大家好！"))
    }

    @Test
    fun `buildMeaning strips redundant brackets on pos`() {
        val w = ImportedWord("chat", "【n.m.】", "", "猫", "", "")
        val m = ImportWordParser.buildMeaning(w)
        assertEquals("【n.m.】猫", m)
    }

    @Test
    fun `chunkLines splits by char budget keeping lines intact`() {
        val longLine = "a".repeat(250)
        val content = List(10) { "$it $longLine" }.joinToString("\n")
        val chunks = ImportWordParser.chunkLines(content)
        assertTrue(chunks.size >= 2)
        val rejoined = chunks.joinToString("\n").lines().map { it.trim() }
        assertEquals(10, rejoined.size)
        assertTrue(rejoined.first().startsWith("0 "))
        assertTrue(rejoined.last().startsWith("9 "))
    }

    @Test
    fun `chunkLines skips blank lines and keeps single huge line`() {
        val huge = "x".repeat(800)
        val chunks = ImportWordParser.chunkLines("\n\n$huge\n\n")
        assertEquals(1, chunks.size)
        assertEquals(huge, chunks[0])
    }

    @Test
    fun `merge keeps richer entry for same word`() {
        val a = ImportedWord("chat", "n.m.", "", "猫", "", "")
        val b = ImportedWord("Chat", "n.m.", "/ʃa/", "猫；家伙", "Le chat dort.", "猫在睡觉。")
        val merged = ImportWordParser.merge(listOf(a, b))
        assertEquals(1, merged.size)
        assertEquals("/ʃa/", merged[0].ipa)
    }

    @Test
    fun `splitHead parses four segments by position`() {
        val w = ImportWordParser.splitHead("la tête｜n.f.｜/la tɛt/｜头；出风头（avoir la tête 出名）")
        assertEquals("la tête", w.word)
        assertEquals("n.f.", w.pos)
        assertEquals("/la tɛt/", w.ipa)
        assertEquals("头；出风头（avoir la tête 出名）", w.meaning)
    }

    @Test
    fun `splitHead handles missing ipa and joins extra segments into meaning`() {
        val w = ImportWordParser.splitHead("truc｜n.m.｜｜东西，玩意儿｜口语")
        assertEquals("truc", w.word)
        assertEquals("n.m.", w.pos)
        assertEquals("", w.ipa)
        assertEquals("东西，玩意儿｜口语", w.meaning)
        val two = ImportWordParser.splitHead("bannir 封禁")
        assertEquals("bannir 封禁", two.word)
        assertEquals("", two.meaning)
        val noIpa = ImportWordParser.splitHead("ben｜感叹词｜好吧")
        assertEquals("感叹词", noIpa.pos)
        assertEquals("好吧", noIpa.meaning)
    }

    @Test
    fun `formatHead adds slashes and drops brackets roundtrip`() {
        val head = ImportWordParser.formatHead("ban", "【v.t.】", "ban", "封号")
        assertEquals("ban｜v.t.｜/ban/｜封号", head)
        val w = ImportWordParser.splitHead(head)
        assertEquals("v.t.", w.pos)
        assertEquals("/ban/", w.ipa)
    }

    @Test
    fun `normalizePos maps english parts of speech and keeps standard ones`() {
        assertEquals("v.", ImportWordParser.normalizePos("verb"))
        assertEquals("v.", ImportWordParser.normalizePos("Verb."))
        assertEquals("n.m.", ImportWordParser.normalizePos("n.m."))
        assertEquals("adj.", ImportWordParser.normalizePos("adjective"))
        assertEquals("interj.", ImportWordParser.normalizePos("interj"))
        assertEquals("感叹词", ImportWordParser.normalizePos("感叹词"))
        assertEquals("", ImportWordParser.normalizePos("  "))
    }

    @Test
    fun `buildMeaning normalizes english pos inside brackets`() {
        val w = ImportedWord("Ferais", "verb", "", "faire 的现在条件式第一/二人称单数：会做", "", "")
        val m = ImportWordParser.buildMeaning(w)
        assertTrue(m.startsWith("【v.】faire 的现在条件式"))
    }
}
