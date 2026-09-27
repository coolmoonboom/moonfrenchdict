package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteMeaningTest {

    @Test
    fun parseFullStructuredMeaning() {
        val m = FavoriteMeaning.parse(
            "【n.f.】浴室\n音标 /sal də bɛ̃/\n例句：la salle de bain a été entièrement refaite récemment.\n中文：浴室最近被彻底翻修了。"
        )
        assertEquals("n.f.", m.pos)
        assertEquals("浴室", m.zh)
        assertEquals("/sal də bɛ̃/", m.ipa)
        assertEquals("la salle de bain a été entièrement refaite récemment.", m.exampleFr)
        assertEquals("浴室最近被彻底翻修了。", m.exampleZh)
        assertEquals("【n.f.】浴室", m.gloss())
    }

    @Test
    fun parseIpaLineGluedWithPos() {
        // 旧数据显示缺陷：音标行尾粘了词性
        val m = FavoriteMeaning.parse("【n.f.】浴室\n音标 /sal də bɛ̃/n.f.\n例句：x\n中文：y")
        assertEquals("/sal də bɛ̃/", m.ipa)
    }

    @Test
    fun parseLegacyPlainMeaning() {
        val m = FavoriteMeaning.parse("抱,拥抱;接吻")
        assertEquals("", m.pos)
        assertEquals("抱,拥抱;接吻", m.zh)
        assertEquals("", m.ipa)
        assertEquals("抱,拥抱;接吻", m.gloss())
    }

    @Test
    fun parseLegacyMultiLineKeepsEverything() {
        val m = FavoriteMeaning.parse("v. 采取,选择\n举行,进行")
        assertEquals("v. 采取,选择 举行,进行", m.zh)
    }

    @Test
    fun parseBlank() {
        assertEquals(ParsedMeaning(), FavoriteMeaning.parse("  "))
    }

    @Test
    fun chineseDominantRejectsEnglishEcho() {
        assertEquals(false, FavoriteMeaning.chineseDominant("to do/make; to construct, compose"))
        assertEquals(
            false,
            FavoriteMeaning.chineseDominant("first-person singular conditional of faire 会做")
        )
    }

    @Test
    fun chineseDominantAcceptsChineseWithFrenchTags() {
        assertEquals(true, FavoriteMeaning.chineseDominant("互相拥抱;接吻 s'~ v.pr."))
        assertEquals(true, FavoriteMeaning.chineseDominant("浴室"))
        assertEquals(false, FavoriteMeaning.chineseDominant("salle de bain"))
    }

    @Test
    fun chineseForSpeechStripsPosAndLatin() {
        // 词性括号与非汉字不进中文语音；词性括号 【】 本就不在保留字符集
        assertEquals(
            "互相拥抱接吻",
            FavoriteMeaning.chineseForSpeech("【v.pr.】互相拥抱; 接吻 s'embrasser")
        )
        assertEquals("", FavoriteMeaning.chineseForSpeech("verb to embrace"))
    }
}
