package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgreementDataTest {

    @Test
    fun `celui paradigm shows all four genders`() {
        val p = AgreementData.findCurated("celui")!!
        assertEquals(listOf("celui", "celle", "ceux", "celles"), p.forms.map { it.form })
        assertEquals(listOf("阳单", "阴单", "阳复", "阴复"), p.forms.map { it.label })
    }

    @Test
    fun `search by any form of the same family`() {
        assertEquals("celle", AgreementData.findCurated("celle")?.forms?.get(1)?.form)
        assertEquals("celui", AgreementData.findCurated("ceux")?.key ?: "")
        assertEquals("ce", AgreementData.findCurated("cette")?.key ?: "")
    }

    @Test
    fun `vieux paradigm includes vieil for vowel-before`() {
        val p = AgreementData.findCurated("vieux")!!
        assertTrue(p.forms.any { it.form == "vieil" && it.note.contains("元音") })
        assertTrue(p.forms.any { it.form == "vieille" })
        assertTrue(p.forms.any { it.form == "vieilles" })
    }

    @Test
    fun `je and il both hit personal pronoun paradigm`() {
        assertEquals("人称主语代词", AgreementData.findCurated("je")?.category)
        assertTrue(AgreementData.findCurated("ils")!!.forms.any { it.form == "ils" })
    }

    @Test
    fun `noun plural rules`() {
        assertEquals("chats", AgreementData.pluralOf("chat"))
        assertEquals("chevaux", AgreementData.pluralOf("cheval"))
        assertEquals("yeux", AgreementData.pluralOf("oeil"))
        assertEquals("travaux", AgreementData.pluralOf("travail"))
        assertEquals("journaux", AgreementData.pluralOf("journal"))
        assertEquals("voitures", AgreementData.pluralOf("voiture"))
        assertEquals("jours", AgreementData.pluralOf("jour"))
        assertEquals("vieux", AgreementData.pluralOf("vieux"))
    }

    @Test
    fun `adjective feminine rules`() {
        assertEquals("belle", AgreementData.feminineOf("beau"))
        assertEquals("nouvelle", AgreementData.feminineOf("nouveau"))
        assertEquals("vieille", AgreementData.feminineOf("vieux"))
        assertEquals("bonne", AgreementData.feminineOf("bon"))
        assertEquals("petite", AgreementData.feminineOf("petit"))
        assertEquals("heureuse", AgreementData.feminineOf("heureux"))
        assertEquals("active", AgreementData.feminineOf("actif"))
        assertEquals("blanche", AgreementData.feminineOf("blanc"))
        assertEquals("longue", AgreementData.feminineOf("long"))
    }

    @Test
    fun `generated noun paradigm carries article`() {
        val p = AgreementData.nounParadigm("chat", DictEntry("chat", "【n.m.】猫"))
        assertNotNull(p)
        val forms = p!!.forms.map { "${it.label}:${it.form}" }
        assertTrue("forms=$forms", p.forms.any { it.form == "le chat" })
        assertTrue("forms=$forms", p.forms.any { it.form == "les chats" })
    }

    @Test
    fun `generated noun from pos n m gives masculine article`() {
        val e = DictEntry(word = "livre", meaning = "", pos = "n.m.", zh = "书")
        val p = AgreementData.nounParadigm("livre", e)!!
        val forms = p.forms.map { "${it.label}:${it.form}" }
        assertTrue("forms=$forms", p.forms.any { it.form == "le livre" && it.label == "阳单" })
    }

    @Test
    fun `generated adjective paradigm covered`() {
        val p = AgreementData.adjectiveParadigm("rouge", DictEntry("rouge", "【adj.】红色的"))
        assertNotNull(p)
        assertTrue(p!!.forms.any { it.form == "rouge" && it.label == "阳单" })
        assertTrue(p.forms.any { it.form == "rouges" })
    }

    @Test
    fun `posKind classifies entries`() {
        assertEquals("noun", AgreementData.posKind(DictEntry("chat", "", "n.m.", "猫")))
        assertEquals("adj", AgreementData.posKind(DictEntry("beau", "", "adj.", "美丽的")))
        assertEquals("verb", AgreementData.posKind(DictEntry("être", "", "v.", "是")))
        assertNull(AgreementData.posKind(null))
    }
}