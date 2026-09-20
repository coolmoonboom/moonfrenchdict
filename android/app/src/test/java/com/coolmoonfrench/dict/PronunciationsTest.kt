package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 读音分组验证测试。
 */
class PronunciationsTest {

    private val conj = VerbConjugator()

    private fun groups(v: String): List<PronGroup> =
        Pronunciations.groupsOf(conj.conjugate(v)!!)

    @Test fun suivreGroupsCoverAllForms() {
        val c = conj.conjugate("suivre")!!
        val forms = Pronunciations.simpleForms(c)
        val groups = Pronunciations.groupsOf(c)
        val collected = groups.flatMap { it.forms }
        assertEquals(forms.toSet(), collected.toSet())
        assertEquals(forms.size, collected.size)
        assertTrue(groups.all { it.ipa.isNotEmpty() })
        assertTrue(groups.all { it.forms.isNotEmpty() })
    }

    @Test fun suivreHomophoneGroup() {
        val sɥi = groups("suivre").first { it.ipa == "sɥi" }
        assertTrue(sɥi.forms.containsAll(listOf("suis", "suit")))
    }

    @Test fun regularVerbGrouping() {
        val paʁl = groups("parler").first { it.ipa == "paʁl" }
        assertTrue(paʁl.forms.containsAll(listOf("parle", "parles", "parlent")))
        val paʁle = groups("parler").first { it.ipa == "paʁle" }
        assertTrue(paʁle.forms.containsAll(listOf("parler", "parlez")))
    }

    @Test fun simpleFormsExcludeMultiWord() {
        val c = conj.conjugate("se laver")!!
        val forms = Pronunciations.simpleForms(c)
        assertFalse(forms.any { it.contains(' ') })
    }
}
