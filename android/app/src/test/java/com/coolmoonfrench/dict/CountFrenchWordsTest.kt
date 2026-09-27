package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Test

class CountFrenchWordsTest {

    @Test
    fun `counts plain word list`() {
        val text = (1..60).joinToString("\n") { "mot${it % 7} exemple" }
        assert(ImportWordParser.countImportableFrenchWords(text) > 50)
    }

    @Test
    fun `single word or phrase is small`() {
        assertEquals(1, ImportWordParser.countImportableFrenchWords("bonjour"))
        assertEquals(3, ImportWordParser.countImportableFrenchWords("pomme de terre"))
    }

    @Test
    fun `chinese annotation lines are skipped`() {
        val text = "1. demeure 住所\n2. sévir 严厉处置"
        assertEquals(0, ImportWordParser.countImportableFrenchWords(text))
    }

    @Test
    fun `numbering prefixes stripped`() {
        assertEquals(1, ImportWordParser.countImportableFrenchWords("12. maison"))
        assertEquals(1, ImportWordParser.countImportableFrenchWords("3) école"))
    }

    @Test
    fun `apostrophes and hyphens kept inside word`() {
        assertEquals(1, ImportWordParser.countImportableFrenchWords("l'homme"))
        assertEquals(1, ImportWordParser.countImportableFrenchWords("porte-monnaie"))
    }
}
