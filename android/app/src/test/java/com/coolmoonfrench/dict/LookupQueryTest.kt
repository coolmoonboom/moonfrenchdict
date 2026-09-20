package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Test

class LookupQueryTest {

    @Test
    fun `trims trailing space`() {
        assertEquals("faire", normalizeLookupQuery("faire "))
    }

    @Test
    fun `trims leading and trailing spaces`() {
        assertEquals("faire", normalizeLookupQuery("   faire   "))
    }

    @Test
    fun `collapses repeated internal spaces`() {
        assertEquals("au revoir", normalizeLookupQuery("au    revoir"))
    }

    @Test
    fun `keeps single spaces in phrases`() {
        assertEquals("s'il vous plaît", normalizeLookupQuery(" s'il vous plaît "))
    }

    @Test
    fun `normalizes non-breaking and full-width spaces`() {
        assertEquals("faire", normalizeLookupQuery("faire\u00A0"))
        assertEquals("d accord", normalizeLookupQuery("d\u3000accord"))
    }

    @Test
    fun `empty and whitespace-only inputs become empty`() {
        assertEquals("", normalizeLookupQuery(""))
        assertEquals("", normalizeLookupQuery("   \u00A0 "))
    }

    @Test
    fun `extracts pos tag from ai meaning`() {
        assertEquals("n.m.", DictEntry.extractPos("【n.m.】书；书本"))
        assertEquals("v.", DictEntry.extractPos("【v.】做；制造"))
        assertEquals("", DictEntry.extractPos("书；书本"))
    }
}
