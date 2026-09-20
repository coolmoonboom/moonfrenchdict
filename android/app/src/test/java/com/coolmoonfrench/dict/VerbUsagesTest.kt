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