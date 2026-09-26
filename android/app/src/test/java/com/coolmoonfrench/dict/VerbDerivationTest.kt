package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerbDerivationTest {

    @Test
    fun parseFullObject() {
        val json = """
            {"verb":"étudier","participe_present":"étudiant","gerondif":"en étudiant",
             "participe_passe":{"m":"étudié","f":"étudiée","mp":"étudiés","fp":"étudiées"},
             "auxiliaire":"avoir",
             "exemple_compose_fr":"Il a étudié le français.","exemple_compose_zh":"他学过法语。",
             "exemple_pqp_fr":"Il avait étudié avant.","exemple_pqp_zh":"他之前学过。",
             "noms_action":[{"form":"l'étude","zh":"研究（现代标准派生名词）","note":"-e 动作名词；l'étudier 为古体","example_fr":"L'étude du français.","example_zh":"法语的学习。"}],
             "noms_agent":[{"form":"étudiant","zh":"学生","note":"-ant 施动者"}],
             "adjectifs":[{"form":"étudiant","zh":"学生的","note":""}],
             "adverbes":[],
             "notes":"étude 现代通用。"}
        """.trimIndent()
        val r = VerbDerivation.parse(json)
        assertNotNull(r)
        r!!
        assertEquals("étudier", r.verb)
        assertEquals("étudiant", r.participePresent)
        assertEquals("en étudiant", r.gerondif)
        assertEquals("étudiée", r.ppFeminin)
        assertEquals("étudiées", r.ppFemininPluriel)
        assertEquals("avoir", r.auxiliaire)
        assertEquals("Il a étudié le français.", r.exempleComposeFr)
        assertEquals(1, r.nomsAction.size)
        assertEquals("l'étude", r.nomsAction[0].form)
        assertEquals("研究（现代标准派生名词）", r.nomsAction[0].zh)
        assertEquals(1, r.nomsAgent.size)
        assertTrue(r.adverbes.isEmpty())
        assertEquals("étude 现代通用。", r.notes)
    }

    @Test
    fun parseIgnoresCodeFenceAndProse() {
        val reply = "好的：\n```json\n{\"verb\":\"finir\",\"adverbes\":[]}\n```\n以上。"
        val r = VerbDerivation.parse(reply)
        assertNotNull(r)
        assertEquals("finir", r!!.verb)
    }

    @Test
    fun parseReturnsNullOnGarbage() {
        assertNull(VerbDerivation.parse("完全没有 JSON"))
        assertNull(VerbDerivation.parse(""))
    }

    @Test
    fun parseSkipsEntriesWithoutForm() {
        val json = """{"verb":"parler","noms_action":[{"zh":"无 form"},{"form":"le parler","zh":"说话这件事（古体）"}]}"""
        val r = VerbDerivation.parse(json)
        assertNotNull(r)
        assertEquals(1, r!!.nomsAction.size)
        assertEquals("le parler", r.nomsAction[0].form)
    }
}
