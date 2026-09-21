package com.coolmoonfrench.dict

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 原生 TTS 合成前的文本前置判断。
 * 纯标点 / emoji 会被清洗成空音素序列，必须拦在原生层之前。
 */
class EspeakTextTest {

    @Test
    fun `normal sentence is synthesisable`() {
        assertTrue(ttsTextIsSynthesisable("Bonjour, comment ça va ?"))
    }

    @Test
    fun `single letter or digit is synthesisable`() {
        assertTrue(ttsTextIsSynthesisable("a"))
        assertTrue(ttsTextIsSynthesisable("2"))
    }

    @Test
    fun `blank text is not synthesisable`() {
        assertFalse(ttsTextIsSynthesisable(""))
        assertFalse(ttsTextIsSynthesisable("   "))
    }

    @Test
    fun `punctuation only is not synthesisable`() {
        assertFalse(ttsTextIsSynthesisable("..."))
        assertFalse(ttsTextIsSynthesisable("!!! ?!"))
        assertFalse(ttsTextIsSynthesisable("--- »«"))
    }

    @Test
    fun `emoji only is not synthesisable`() {
        assertFalse(ttsTextIsSynthesisable("\uD83D\uDE00\uD83C\uDF89"))
    }
}
