package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteApplyMeaningTest {

    @Test
    fun `user meaning overrides dictionary entry`() {
        val dict = DictEntry(word = "sévir", meaning = "【v.i.】de l'ancien français", pos = "v.i.", zh = "旧语：严厉处置")
        val out = FavoriteMeaning.applyUserMeaning(dict, "sévir", "【v.i.】（对某人）严厉处置；坚决打击")
        assertEquals("sévir", out.word)
        assertEquals("【v.i.】（对某人）严厉处置；坚决打击", out.meaning)
        assertEquals("【v.i.】（对某人）严厉处置；坚决打击", out.zh)
    }

    @Test
    fun `pos falls back to dictionary when user text lacks pos`() {
        val dict = DictEntry(word = "demeure", meaning = "n.f. 住所", pos = "n.f.")
        val out = FavoriteMeaning.applyUserMeaning(dict, "demeure", "住所；居所（正式用语）")
        assertEquals("n.f.", out.pos)
        assertEquals("住所；居所（正式用语）", out.zh)
    }

    @Test
    fun `head word comes from parsed text when no dictionary hit`() {
        val out = FavoriteMeaning.applyUserMeaning(null, "demeure", "【n.f.】住所")
        assertEquals("demeure", out.word)
        assertEquals("n.f.", out.pos)
    }
}
