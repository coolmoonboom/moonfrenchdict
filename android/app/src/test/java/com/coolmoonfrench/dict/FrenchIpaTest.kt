package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地法语发音引擎验证测试。
 * 覆盖：内置音标表、suivre 全变位、第一/二组规则动词、常见不规则形式。
 */
class FrenchIpaTest {

    private fun ipa(w: String) = FrenchIpa.lookup(w)

    private fun assertIpa(w: String, expected: String) = assertEquals("$w", expected, ipa(w))

    // ---------- 内置音标表 ----------
    @Test fun overrides() {
        assertIpa("suivre", "sɥivʁ")
        assertIpa("être", "ɛtʁ")
        assertIpa("avoir", "avwaʁ")
        assertIpa("aller", "ale")
        assertIpa("faire", "fɛʁ")
        assertIpa("se laver", "sə lave")
        assertIpa("Suivre", "sɥivʁ")
    }

    // ---------- suivre 全变位（用户用例） ----------
    @Test fun suivreForms() {
        assertIpa("suis", "sɥi")
        assertIpa("suit", "sɥi")
        assertIpa("suivons", "sɥivɔ̃")
        assertIpa("suivez", "sɥive")
        assertIpa("suivent", "sɥiv")
        assertIpa("suivais", "sɥivɛ")
        assertIpa("suivait", "sɥivɛ")
        assertIpa("suivions", "sɥivjɔ̃")
        assertIpa("suiviez", "sɥivje")
        assertIpa("suivaient", "sɥivɛ")
        assertIpa("suivrai", "sɥivʁe")
        assertIpa("suivras", "sɥivʁa")
        assertIpa("suivrons", "sɥivʁɔ̃")
        assertIpa("suivront", "sɥivʁɔ̃")
        assertIpa("suivrais", "sɥivʁɛ")
        assertIpa("suivrions", "sɥivʁijɔ̃")
        assertIpa("suivraient", "sɥivʁɛ")
        assertIpa("suive", "sɥiv")
        assertIpa("suivent", "sɥiv")
        assertIpa("suivisse", "sɥivis")
        assertIpa("suivît", "sɥivi")
        assertIpa("suivîmes", "sɥivim")
        assertIpa("suivîtes", "sɥivit")
        assertIpa("suivirent", "sɥiviʁ")
        assertIpa("suivi", "sɥivi")
        assertIpa("suivant", "sɥivɑ̃")
    }

    // ---------- 第一组规则动词 ----------
    @Test fun erVerbs() {
        assertIpa("parler", "paʁle")
        assertIpa("parle", "paʁl")
        assertIpa("parles", "paʁl")
        assertIpa("parlent", "paʁl")
        assertIpa("parlons", "paʁlɔ̃")
        assertIpa("parlez", "paʁle")
        assertIpa("parlais", "paʁlɛ")
        assertIpa("parlions", "paʁljɔ̃")
        assertIpa("parlerai", "paʁləʁe")
        assertIpa("manger", "mɑ̃ʒe")
        assertIpa("mangeons", "mɑ̃ʒɔ̃")
        assertIpa("mangeais", "mɑ̃ʒɛ")
        assertIpa("commencer", "kɔmɑ̃se")
        assertIpa("avançons", "avɑ̃sɔ̃")
        assertIpa("appeler", "apəle")
        assertIpa("espérer", "ɛspeʁe")
        assertIpa("essayer", "ɛsɛje")
    }

    // ---------- 第二组规则动词 ----------
    @Test fun irVerbs() {
        assertIpa("finir", "finiʁ")
        assertIpa("finis", "fini")
        assertIpa("finit", "fini")
        assertIpa("finissons", "finisɔ̃")
        assertIpa("finissent", "finis")
        assertIpa("finissais", "finisɛ")
    }

    // ---------- 常见不规则形式 ----------
    @Test fun irregulars() {
        assertIpa("est", "ɛ")
        assertIpa("es", "ɛ")
        assertIpa("sont", "sɔ̃")
        assertIpa("vais", "vɛ")
        assertIpa("vont", "vɔ̃")
        assertIpa("fais", "fɛ")
        assertIpa("font", "fɔ̃")
        assertIpa("faites", "fɛt")
        assertIpa("suis", "sɥi")
        assertIpa("peux", "pø")
        assertIpa("veux", "vø")
        assertIpa("dois", "dwa")
        assertIpa("sais", "sɛ")
        assertIpa("viens", "vjɛ̃")
        assertIpa("prennent", "pʁɛn")
        assertIpa("mettent", "mɛt")
        assertIpa("conduis", "kɔ̃dɥi")
        assertIpa("craignons", "kʁɛɲɔ̃")
    }

    // ---------- 任意形式都有结果（不返回空串） ----------
    @Test fun neverEmptyForRealWords() {
        val words = listOf(
            "suivre", "suis", "parler", "finir", "vendre", "prendre",
            "partir", "ouvrir", "craindre", "conduire", "connaître",
            "vivre", "boire", "courir", "vaincre", "mettre", "dire"
        )
        for (w in words) {
            assertTrue("$w 应有音标", ipa(w).isNotEmpty())
        }
        assertTrue(FrenchIpa.wrap("suivre").startsWith("/") && FrenchIpa.wrap("suivre").endsWith("/"))
    }
}
