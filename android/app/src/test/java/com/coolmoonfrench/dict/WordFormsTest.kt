package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordFormsTest {

    @Test
    fun `regular plural rules`() {
        assertEquals("jardins", WordForms.plural("jardin"))
        assertEquals("travaux", WordForms.plural("travail"))
        assertEquals("festivals", WordForms.plural("festival"))
        assertEquals("bateaux", WordForms.plural("bateau"))
        assertEquals("jeux", WordForms.plural("jeu"))
        assertEquals("choux", WordForms.plural("chou"))
        assertEquals("bras", WordForms.plural("bras"))
    }

    @Test
    fun `vowel sound detection with aspirated h`() {
        assertTrue(WordForms.startsWithVowelSound("homme"))
        assertFalse(WordForms.startsWithVowelSound("hâte"))
        assertTrue(WordForms.startsWithVowelSound("exclusivité"))
        assertFalse(WordForms.startsWithVowelSound("jardin"))
    }

    @Test
    fun `clean word strips leading articles`() {
        assertEquals("jardin", WordForms.cleanWord("le jardin"))
        assertEquals("exclusivité", WordForms.cleanWord("l'exclusivité"))
        assertEquals("exclusivité", WordForms.cleanWord("de l’exclusivité"))
        assertEquals("échec", WordForms.cleanWord("d'un échec"))
        assertEquals("jardin", WordForms.cleanWord("jardin"))
    }

    @Test
    fun `noun forms masculine singular and plural`() {
        val forms = WordForms.generate("jardin", "n.m.").map { it.text }
        assertEquals(
            listOf("le jardin", "un jardin", "du jardin", "les jardins", "des jardins"),
            forms
        )
    }

    @Test
    fun `noun forms feminine with elision`() {
        val forms = WordForms.generate("exclusivité", "n.f.").map { it.text }
        assertEquals(
            listOf(
                "l'exclusivité", "une exclusivité", "de l'exclusivité",
                "les exclusivités", "des exclusivités"
            ),
            forms
        )
        assertTrue(WordForms.generate("exclusivité", "n.f.").first().ipa.startsWith("l‿"))
    }

    @Test
    fun `liaison marked in plural forms before vowel`() {
        val les = WordForms.generate("ami", "n.m.").first { it.text.startsWith("les") }
        assertTrue(les.ipa.contains("‿z"))
    }

    @Test
    fun `plural only pos omits singular forms`() {
        val forms = WordForms.generate("funéraille", "n.f.pl").map { it.text }
        assertTrue(forms.none { it.startsWith("la ") || it.startsWith("une ") })
        assertTrue(forms.all { it.contains("funérailles") })
    }

    @Test
    fun `verb nominalization from regular infinitive`() {
        val forms = WordForms.generate("parler", "v.").map { it.text }
        assertEquals(listOf("le parlé", "du parlé", "des parlé", "au parlé"), forms)
    }

    @Test
    fun `unknown pos returns empty`() {
        assertTrue(WordForms.generate("doucement", "adv.").isEmpty())
    }

    @Test
    fun `append meaning adds single forms line`() {
        val forms = WordForms.generate("jardin", "n.m.")
        val once = WordForms.appendToMeaning("【n.m.】花园", forms)
        assertTrue(once.contains("冠词：le jardin"))
        val twice = WordForms.appendToMeaning(once, WordForms.generate("jardins", "n.m.pl"))
        assertEquals(1, twice.lines().count { it.startsWith(WordForms.MEANING_PREFIX) })
    }
}

class FormsServiceParseTest {

    @Test
    fun `parse object array and strip ipa slashes`() {
        val forms = FormsService.parse(
            "```json\n[{\"text\":\"le jardin\",\"ipa\":\"/lə ʒaʁdɛ̃/\"},{\"text\":\"les jardins\",\"ipa\":\"le ʒaʁdɛ̃\"}]\n```"
        )
        assertEquals(2, forms.size)
        assertEquals("le jardin", forms[0].text)
        assertEquals("lə ʒaʁdɛ̃", forms[0].ipa)
        assertEquals("le ʒaʁdɛ̃", forms[1].ipa)
    }

    @Test
    fun `parse plain string array tolerated`() {
        val forms = FormsService.parse("前缀噪音 [\"le jardin\", \" du jardin \"] 后缀")
        assertEquals(listOf("le jardin", "du jardin"), forms.map { it.text })
        assertTrue(forms.all { it.ipa.isEmpty() })
    }

    @Test
    fun `garbage returns empty`() {
        assertTrue(FormsService.parse("没有 JSON").isEmpty())
    }
}

class FavoriteMeaningFormsTest {

    @Test
    fun `forms line parsed out of meaning`() {
        val text = "【n.f.】独占\n冠词：l'exclusivité / de l'exclusivité / les exclusivités"
        val parsed = FavoriteMeaning.parse(text)
        assertEquals("独占", parsed.zh)
        assertEquals("n.f.", parsed.pos)
        assertEquals("l'exclusivité / de l'exclusivité / les exclusivités", parsed.forms)
        assertFalse(parsed.gloss().contains("冠词"))
    }

    @Test
    fun `meaning without forms keeps forms empty`() {
        val parsed = FavoriteMeaning.parse("【n.m.】花园")
        assertEquals("", parsed.forms)
    }
}
