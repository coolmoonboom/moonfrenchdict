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
}
