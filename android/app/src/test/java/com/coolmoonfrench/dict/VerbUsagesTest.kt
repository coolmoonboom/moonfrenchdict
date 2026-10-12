package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerbUsagesTest {

    private val conj = VerbConjugator()

    // ---------- 用法查询 ----------
    @Test fun fairePatterns() {
        val patterns = VerbUsages.patternsOf("faire")
        assertTrue(patterns.isNotEmpty())
        val a = patterns.first { it.pattern.contains("à", ignoreCase = true) }
        assertTrue("faire qch à qqn 应包含 à 高亮标记", a.pattern.contains("~à~"))
        assertTrue(a.examples.isNotEmpty())
    }

    @Test fun oublierHighlightsDe() {
        val patterns = VerbUsages.patternsOf("oublier")
        val de = patterns.first { it.note.contains("忘记做某事") }
        assertTrue("oublier de faire 应包含 de 高亮标记", de.pattern.contains("~de~"))
    }

    @Test fun pronominalLookupAliases() {
        // 数据键为 souvenir，但 should 支持 "se souvenir" 输入
        assertTrue(VerbUsages.patternsOf("se souvenIR").isNotEmpty())
        assertTrue(VerbUsages.patternsOf("s'habituer").isNotEmpty())
        // 未收录词返回空
        assertEquals(0, VerbUsages.patternsOf("xyzzy").size)
    }

    @Test fun modalVerbsBareInfinitive() {
        // 情态动词后接不定式，不带介词
        for (v in listOf("devoir", "pouvoir", "vouloir", "savoir")) {
            for (p in VerbUsages.patternsOf(v)) {
                assertTrue("$v 的句型不应含 de 接不定式: ${p.pattern}",
                    !p.pattern.contains("~de~"))
            }
        }
    }

    // ---------- 固定搭配（动词 + 介词 / faire-être-avoir） ----------
    @Test fun faireFixedCollocations() {
        val patterns = VerbUsages.patternsOf("faire")
        val preuve = patterns.firstOrNull { it.pattern.contains("faire preuve") }
        assertNotNull("faire preuve de qch 应被收录", preuve)
        assertTrue("faire preuve de 应高亮 de", preuve!!.pattern.contains("~de~"))
        assertTrue("faire preuve de 应有例句", preuve.examples.isNotEmpty())
        assertTrue(patterns.any { it.pattern.contains("faire partie") })
        assertTrue(patterns.any { it.pattern.contains("faire attention") })
    }

    @Test fun etreFixedCollocations() {
        val patterns = VerbUsages.patternsOf("être")
        val accompagne = patterns.firstOrNull { it.pattern.contains("accompagné") }
        assertNotNull("être accompagné de 应被收录", accompagne)
        assertTrue("être accompagné de 应高亮 de", accompagne!!.pattern.contains("~de~"))
        assertTrue(patterns.any { it.pattern.contains("être en train") })
        assertTrue(patterns.any { it.pattern.contains("être d'accord") })
    }

    @Test fun avoirFixedCollocations() {
        val patterns = VerbUsages.patternsOf("avoir")
        assertTrue(patterns.any { it.pattern.contains("avoir mal") && it.pattern.contains("~à~") })
        assertTrue(patterns.any { it.pattern.contains("avoir lieu") })
        assertTrue(patterns.any { it.pattern.contains("avoir envie") })
    }

    @Test fun reversePronominalLookup() {
        // 数据以代动词形式收录时，查原形也应命中
        assertTrue(VerbUsages.patternsOf("attendre").any { it.pattern.contains("s'attendre") })
        assertTrue(VerbUsages.patternsOf("servir").any { it.pattern.contains("se servir") })
        assertTrue(VerbUsages.patternsOf("occuper").any { it.pattern.contains("s'occuper") })
    }

    @Test fun verbPrepositionCollocations() {
        assertTrue(VerbUsages.patternsOf("dépendre").any { it.pattern.contains("~de~") })
        assertTrue(VerbUsages.patternsOf("participer").any { it.pattern.contains("~à~") })
        assertTrue(VerbUsages.patternsOf("manquer").size >= 2)
    }

    // ---------- 全部时态语态 ----------
    @Test fun activeAllTensesGenerated() {
        val c = conj.conjugate("manger")!!
        assertNotNull(c.subjonctifImparfait)
        // 简单时态补齐：虚拟式未完成
        assertTrue(c.subjonctifImparfait[0].endsWith("sse"))
        // 新增复合时态
        val pse = compoundPasseSimple(c)
        assertEquals("先过去时", "eus mangé", pse[0])
        val spqf = compoundSubjonctifImparfait(c)
        assertEquals("虚拟式愈过去时", "eusse mangé", spqf[0])
    }

    @Test fun êtreCompoundUsesEtre() {
        val c = conj.conjugate("aller")!!
        assertEquals("être", c.auxiliary)
        assertEquals("allé", c.participePasse)
        assertEquals("fut allé", compoundPasseSimple(c)[2])
        assertEquals("fusse allé", compoundSubjonctifImparfait(c)[0])
    }

    @Test fun passiveFullTenses() {
        val c = conj.conjugate("aimer")!!
        val pc = conj.passive(c)
        assertEquals("être", pc.auxiliary)
        assertNotNull(pc.subjonctifImparfait)
        assertEquals("eut été aimé", compoundPasseSimplePassive(pc)[2])
    }
}