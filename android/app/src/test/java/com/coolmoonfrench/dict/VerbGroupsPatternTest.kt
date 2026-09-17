package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第三组动词规律验证测试。
 * 用 VerbConjugator 内置数据表逐词校验 VerbGroups 中每个词族的代表变位与描述是否属实。
 */
class VerbGroupsPatternTest {

    private val conj = VerbConjugator()

    private fun presentOf(v: String): List<String> = conj.conjugate(v)!!.present

    private fun assertPresent(v: String, vararg expected: String) {
        assertEquals("$v 现在时", expected.toList(), presentOf(v))
    }

    // ---------- 助动词 / 完全无规律 ----------
    @Test fun helpers() {
        assertPresent("être", "suis", "es", "est", "sommes", "êtes", "sont")
        assertPresent("avoir", "ai", "as", "a", "avons", "avez", "ont")
        assertPresent("aller", "vais", "vas", "va", "allons", "allez", "vont")
        assertPresent("faire", "fais", "fais", "fait", "faisons", "faites", "font")
    }

    // ---------- 情态动词类 ----------
    @Test fun modalVerbs() {
        assertPresent("pouvoir", "peux", "peux", "peut", "pouvons", "pouvez", "peuvent")
        assertPresent("vouloir", "veux", "veux", "veut", "voulons", "voulez", "veulent")
        assertPresent("devoir", "dois", "dois", "doit", "devons", "devez", "doivent")
        assertPresent("savoir", "sais", "sais", "sait", "savons", "savez", "savent")
    }

    // ---------- venir / tenir 族 ----------
    @Test fun venirTenir() {
        assertPresent("venir", "viens", "viens", "vient", "venons", "venez", "viennent")
        assertPresent("tenir", "tiens", "tiens", "tient", "tenons", "tenez", "tiennent")
    }

    // ---------- prendre / mettre 族 ----------
    @Test fun prendreMettre() {
        assertPresent("prendre", "prends", "prends", "prend", "prenons", "prenez", "prennent")
        assertPresent("mettre", "mets", "mets", "met", "mettons", "mettez", "mettent")
        assertPresent("battre", "bats", "bats", "bat", "battons", "battez", "battent")
    }

    // ---------- dire / lire / écrire ----------
    @Test fun direLireEcrire() {
        assertPresent("dire", "dis", "dis", "dit", "disons", "dites", "disent")
        assertPresent("lire", "lis", "lis", "lit", "lisons", "lisez", "lisent")
        assertPresent("écrire", "écris", "écris", "écrit", "écrivons", "écrivez", "écrivent")
    }

    // ---------- voir / recevoir ----------
    @Test fun voirRecevoir() {
        assertPresent("voir", "vois", "vois", "voit", "voyons", "voyez", "voient")
        assertPresent("recevoir", "reçois", "reçois", "reçoit", "recevons", "recevez", "reçoivent")
    }

    // ---------- connaître / naître ----------
    @Test fun connaitreNaitre() {
        assertPresent("connaître", "connais", "connais", "connaît", "connaissons", "connaissez", "connaissent")
        assertPresent("naître", "nais", "nais", "naît", "naissons", "naissez", "naissent")
    }

    // ---------- 单独动词：vivre / suivre / rire / boire / croire ----------
    @Test fun standalone() {
        assertPresent("vivre", "vis", "vis", "vit", "vivons", "vivez", "vivent")
        assertPresent("suivre", "suis", "suis", "suit", "suivons", "suivez", "suivent")
        assertPresent("rire", "ris", "ris", "rit", "rions", "riez", "rient")
        assertPresent("boire", "bois", "bois", "boit", "buvons", "buvez", "boivent")
        assertPresent("croire", "crois", "crois", "croit", "croyons", "croyez", "croient")
    }

    // ---------- -uire 族 ----------
    @Test fun uireFamily() {
        assertPresent("conduire", "conduis", "conduis", "conduit", "conduisons", "conduisez", "conduisent")
        assertPresent("produire", "produis", "produis", "produit", "produisons", "produisez", "produisent")
        assertPresent("construire", "construis", "construis", "construit", "construisons", "construisez", "construisent")
        assertPresent("traduire", "traduis", "traduis", "traduit", "traduisons", "traduisez", "traduisent")
        assertPresent("réduire", "réduis", "réduis", "réduit", "réduisons", "réduisez", "réduisent")
        assertPresent("détruire", "détruis", "détruis", "détruit", "détruisons", "détruisez", "détruisent")
    }

    // ---------- -aindre / -eindre / -oindre 族 ----------
    @Test fun indreFamily() {
        assertPresent("craindre", "crains", "crains", "craint", "craignons", "craignez", "craignent")
        assertPresent("peindre", "peins", "peins", "peint", "peignons", "peignez", "peignent")
        assertPresent("éteindre", "éteins", "éteins", "éteint", "éteignons", "éteignez", "éteignent")
        assertPresent("joindre", "joins", "joins", "joint", "joignons", "joignez", "joignent")
        assertPresent("plaindre", "plains", "plains", "plaint", "plaignons", "plaignez", "plaignent")
    }

    // ---------- vaincre ----------
    @Test fun vaincre() {
        assertPresent("vaincre", "vaincs", "vaincs", "vainc", "vainquons", "vainquez", "vainquent")
    }

    // ---------- courir / mourir / fuir ----------
    @Test fun courirMourirFuir() {
        assertPresent("courir", "cours", "cours", "court", "courons", "courez", "courent")
        assertPresent("mourir", "meurs", "meurs", "meurt", "mourons", "mourez", "meurent")
        assertPresent("fuir", "fuis", "fuis", "fuit", "fuyons", "fuyez", "fuient")
    }

    // ---------- partir 族 ----------
    @Test fun partirFamily() {
        assertPresent("partir", "pars", "pars", "part", "partons", "partez", "partent")
        assertPresent("sortir", "sors", "sors", "sort", "sortons", "sortez", "sortent")
        assertPresent("dormir", "dors", "dors", "dort", "dormons", "dormez", "dorment")
        assertPresent("sentir", "sens", "sens", "sent", "sentons", "sentez", "sentent")
        assertPresent("servir", "sers", "sers", "sert", "servons", "servez", "servent")
        assertPresent("mentir", "mens", "mens", "ment", "mentons", "mentez", "mentent")
    }

    // ---------- ouvrir 族 ----------
    @Test fun ouvrirFamily() {
        assertPresent("ouvrir", "ouvre", "ouvres", "ouvre", "ouvrons", "ouvrez", "ouvrent")
        assertPresent("offrir", "offre", "offres", "offre", "offrons", "offrez", "offrent")
        assertPresent("souffrir", "souffre", "souffres", "souffre", "souffrons", "souffrez", "souffrent")
        assertPresent("couvrir", "couvre", "couvres", "couvre", "couvrons", "couvrez", "couvrent")
        assertPresent("découvrir", "découvre", "découvres", "découvre", "découvrons", "découvrez", "découvrent")
    }

    // ---------- 规则 -re（vendre 型）----------
    @Test fun regularRe() {
        assertPresent("vendre", "vends", "vends", "vend", "vendons", "vendez", "vendent")
        assertPresent("perdre", "perds", "perds", "perd", "perdons", "perdez", "perdent")
        assertPresent("attendre", "attends", "attends", "attend", "attendons", "attendez", "attendent")
        assertPresent("entendre", "entends", "entends", "entend", "entendons", "entendez", "entendent")
        assertPresent("répondre", "réponds", "réponds", "répond", "répondons", "répondez", "répondent")
        assertPresent("rendre", "rends", "rends", "rend", "rendons", "rendez", "rendent")
        assertPresent("descendre", "descends", "descends", "descend", "descendons", "descendez", "descendent")
        assertPresent("défendre", "défends", "défends", "défend", "défendons", "défendez", "défendent")
        assertPresent("rompre", "romps", "romps", "rompt", "rompons", "rompez", "rompent")
    }

    // ---------- envoyer ----------
    @Test fun envoyer() {
        assertPresent("envoyer", "envoie", "envoies", "envoie", "envoyons", "envoyez", "envoient")
    }

    // ---------- valoir / pleuvoir / falloir ----------
    @Test fun valoirImpersonal() {
        assertPresent("valoir", "vaux", "vaux", "vaut", "valons", "valez", "valent")
        assertEquals("pleut", conj.conjugate("pleuvoir")!!.present[0])
        assertEquals("faut", conj.conjugate("falloir")!!.present[0])
        assertEquals("", conj.conjugate("pleuvoir")!!.present[1])
        assertEquals("", conj.conjugate("falloir")!!.present[1])
    }

    // ---------- 代动词 ----------
    @Test fun pronominal() {
        val c = conj.conjugate("se laver")!!
        assertTrue(c.present[0].startsWith("me "))
        assertTrue(c.present[1].startsWith("te "))
        assertTrue(c.present[2].startsWith("se "))
        assertEquals("être", c.auxiliary)
        assertEquals("lavé", c.participePasse)
    }

    // ---------- 分组页：所有词族的每个动词都能被引擎识别并变位 ----------
    @Test fun everyFamilyVerbIsConjugatable() {
        var count = 0
        for (f in VerbGroups.thirdGroupFamilies) {
            for ((v, _) in f.verbs) {
                if (v == "s'en aller") continue
                val c = conj.conjugate(v)
                assertNotNull("分组页中 $v 应可被引擎变位", c)
                if (c != null) {
                    assertTrue("${f.name} 中 $v 的现在时应非空", c.present.isNotEmpty())
                    count++
                }
            }
        }
        assertTrue("至少应覆盖所有词族动词，实际 $count", count >= 60)
    }

    // ---------- 词族查找 ----------
    @Test fun familyOfLooksUp() {
        assertEquals("-tir / -mir / -vir 型（partir 族）", VerbGroups.familyOf("partir")!!.name)
        assertEquals("-vrir / -frir 型（ouvrir 族）", VerbGroups.familyOf("ouvrir")!!.name)
        assertEquals("助动词", VerbGroups.familyOf("être")!!.name)
        assertEquals("-ttre 型（mettre / battre 族）", VerbGroups.familyOf("battre")!!.name)
        assertEquals("-enir 族（venir / tenir 型）", VerbGroups.familyOf("tenir")!!.name)
        assertTrue(VerbGroups.familyOf("partir")!!.verbs.map { it.first }.contains("sortir"))
    }
}
