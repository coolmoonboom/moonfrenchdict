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
        assertEquals("bonjour", q.options[q.answerIndex].entry.word)
        assertEquals("你好", q.options[q.answerIndex].text)
        val texts = q.options.map { it.text }
        assertEquals(texts.distinct().size, 4)
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
            {"version":2,"levels":[["A1","A1"],["S8","专八"]],
            "words":[["chat","n.m.","A1","猫",0],["manger","v.","A1","吃",1],["rare","adj.","S8","稀有的",0]]}
        """.trimIndent()
        val book = VocabBook.parse(json)
        assertEquals(2, book.levels.size)
        assertEquals("专八", book.levels[1].label)
        assertEquals(3, book.entries.size)
        assertEquals("chat", book.entries[0].word)
        assertEquals("A1", book.entries[0].level)
        assertEquals(false, book.entries[0].isVerb)
        assertTrue(book.entries[1].isVerb)
        assertEquals(listOf("chat", "rare"), book.pool(false, VocabData.ALL).map { it.word })
        assertEquals(listOf("manger"), book.pool(true, "A1").map { it.word })
        assertEquals(1, book.pool(false, "S8").size)
        assertEquals("全部词汇", book.labelOf(VocabData.ALL))
    }
}
