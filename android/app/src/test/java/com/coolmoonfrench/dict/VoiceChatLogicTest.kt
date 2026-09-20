package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceChatLogicTest {

    @Test
    fun `buildHistory keeps window of last twelve lines`() {
        val lines = (0 until 25).map { VoiceLine(user = "u$it", ai = "a$it", status = "ok") }
        val history = buildHistory(lines, 20)
        // 每个 line 产生 user + assistant 两条消息；窗口为 index 9..20 共 12 行
        assertEquals(24, history.size)
        assertEquals("u9", history.first().content)
        assertEquals("user", history.first().role)
        assertEquals("a20", history.last().content)
        assertEquals("assistant", history.last().role)
    }

    @Test
    fun `buildHistory skips assistant replies that never completed`() {
        val lines = listOf(
            VoiceLine("u0", "a0", "ok"),
            VoiceLine("u1", "", "loading"),
            VoiceLine("u2", "", "interrupted"),
            VoiceLine("u3", "a3", "error")
        )
        val history = buildHistory(lines, 3)
        assertEquals(listOf("u0", "a0", "u1", "u2", "u3"), history.map { it.content })
        assertTrue(history.all { it.role == "user" || it.role == "assistant" })
    }

    @Test
    fun `buildHistory handles single current line`() {
        val history = buildHistory(listOf(VoiceLine("bonjour")), 0)
        assertEquals(1, history.size)
        assertEquals("bonjour", history.first().content)
    }

    @Test
    fun `voskField extracts text field`() {
        assertEquals("bonjour le monde", voskField("""{"text":"bonjour le monde"}""", "text"))
    }

    @Test
    fun `voskField returns empty for missing field`() {
        assertEquals("", voskField("""{"text":"bonjour"}""", "partial"))
    }

    @Test
    fun `voskField tolerates invalid or empty json`() {
        assertEquals("", voskField("not a json", "text"))
        assertEquals("", voskField("", "text"))
        assertEquals("ça va", voskField("""{"partial":"ça va"}""", "partial"))
    }
}
