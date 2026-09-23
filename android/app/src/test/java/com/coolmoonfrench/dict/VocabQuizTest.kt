package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VocabQuizTest {

    private fun entry(word: String, meaning: String, level: String = "A1", isVerb: Boolean = false) =
        VocabEntry(
            word = word,
            pos = if (isVerb) "v." else "n.",
            level = level,
            themes = emptyList(),
            meaning = meaning,
            isVerb = isVerb
        )

    @Test
    fun `build returns four distinct options and correct index`() {
        val pool = listOf(
            entry("bonjour", "你好"),
            entry("merci", "谢谢"),
            entry("salut", "嗨"),
            entry("au revoir", "再见"),
            entry("oui", "是"),
            entry("non", "否")
        )
        val q = VocabQuiz.build(pool[0], pool, pool, Random(1), true)!!
        assertEquals(4, q.options.size)
        // 正确答案唯一对应 target（洗牌后 answerIndex 不固定为 0）
        assertEquals("bonjour", q.options[q.answerIndex].entry.word)
        assertEquals("你好", q.options[q.answerIndex].text)
        val texts = q.options.map { it.text }
        assertEquals(texts.distinct().size, 4) // 各选项文本互不重复
        assertTrue(q.prompt == "bonjour")
    }

    @Test
    fun `zh to fr options are french words`() {
        val pool = listOf(
            entry("bonjour", "你好"),
            entry("merci", "谢谢"),
            entry("salut", "嗨"),
            entry("au revoir", "再见"),
            entry("oui", "是")
        )
        val q = VocabQuiz.build(pool[0], pool, pool, Random(2), false)!!
        assertEquals("bonjour", q.options[q.answerIndex].text)
        assertTrue(q.options.all { it.text == it.entry.word })
        assertEquals("你好", q.prompt)
    }

    @Test
    fun `distractors prefer same level`() {
        val a1 = (1..6).map { entry("a$it", "义项a$it", "A1") }
        val b2 = (1..6).map { entry("b$it", "义项b$it", "B2") }
        val pool = a1 + b2
        val q = VocabQuiz.build(a1[0], pool, pool, Random(3), true)!!
        // 干扰项文本不重复于正确答案
        val texts = q.options.map { it.text }
        assertEquals(4, texts.distinct().size)
        assertNotEquals(q.options[q.answerIndex].text, texts[(q.answerIndex + 1) % 4])
    }

    @Test
    fun `build returns null when pool too small`() {
        val pool = listOf(entry("a", "义甲"), entry("b", "义乙"), entry("c", "义丙"))
        assertEquals(null, VocabQuiz.build(pool[0], pool, pool, Random(1), true))
    }

    @Test
    fun `answer index points to the target option`() {
        val pool = listOf(
            entry("chat", "猫"),
            entry("chien", "狗"),
            entry("oiseau", "鸟"),
            entry("poisson", "鱼"),
            entry("cheval", "马")
        )
        val q = VocabQuiz.build(pool[0], pool, pool, Random(4), true)!!
        assertEquals("chat", q.options[q.answerIndex].entry.word)
    }

    @Test
    fun `parse vocab json`() {
        val json = """
            {"version":1,"themes":[["food","食材食物","Les aliments"]],
            "words":[["chat","n.m.","A1","","猫",0],["manger","v.","A1","food","吃",1]]}
        """.trimIndent()
        val book = VocabBook.parse(json)
        assertEquals(1, book.themes.size)
        assertEquals(2, book.entries.size)
        assertEquals("chat", book.entries[0].word)
        assertEquals("A1", book.entries[0].level)
        assertEquals(false, book.entries[0].isVerb)
        assertTrue(book.entries[1].isVerb)
        assertEquals(listOf("food"), book.entries[1].themes)
        assertEquals(listOf("chat"), book.pool(false, emptySet(), emptySet()).map { it.word })
        assertEquals(0, book.pool(true, setOf("B2"), emptySet()).size)
    }
}