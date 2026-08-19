package com.moonfrench.dict

/**
 * 法语动词变位引擎。
 * 支持：
 * - 规则第一组 (-er)、第二组 (-ir) 自动变位
 * - 第三组不规则动词（内置数据表）
 * - 简单时态 + 复合时态（avoir/être 助动词）
 * - 现在分词、副动词、复合不定式、副动词过去式
 */

data class Conjugation(
    val infinitive: String,
    val group: Int,                 // 1, 2, 3
    val auxiliary: String,          // "avoir" | "être"
    val present: List<String>,      // 直陈现在
    val imparfait: List<String>,    // 未完成过去
    val futurSimple: List<String>,  // 简单将来
    val passeSimple: List<String>,  // 简单过去
    val conditionnel: List<String>, // 条件式现在
    val subjonctifPresent: List<String>, // 虚拟式现在
    val subjonctifImparfait: List<String>, // 虚拟式未完成
    val imperatif: List<String>,    // 命令式 (3 forms)
    val participePresent: String,   // 现在分词
    val participePasse: String,     // 过去分词
    val gerondif: String,           // 副动词
    val infinitifPasse: String,     // 复合不定式
    val gerondifPasse: String       // 副动词过去式
)

data class IrregularVerb(
    val present: List<String>,
    val auxiliary: String = "avoir",
    val imparfait: List<String>? = null,
    val futur: List<String>? = null,
    val futureStem: String? = null,
    val passeSimple: List<String>? = null,
    val subjonctif: List<String>? = null,
    val imperatif: List<String>? = null,
    val participePresent: String,
    val participePasse: String
)

class VerbConjugator {

    private val irregular = loadIrregularVerbs()

    /** 生成动词完整变位。支持 "se laver" / "s'appeler" 形式 */
    fun conjugate(input: String): Conjugation? {
        var pronominal = false
        var infinitive = input.trim().lowercase()
        if (infinitive.startsWith("se ") || infinitive.startsWith("s'")) {
            pronominal = true
            infinitive = infinitive.removePrefix("se ").removePrefix("s'")
        }
        // s'en aller 特殊处理
        if (infinitive == "en aller") {
            return null
        }
        val irr = irregular[infinitive]
        if (irr != null) {
            val c = buildIrregular(infinitive, irr)
            return if (pronominal) pronominalize(c) else c
        }
        val c = buildRegular(infinitive) ?: return null
        return if (pronominal) pronominalize(c) else c
    }

    /** 生成代动词形式的完整变位（se + 各时态）。若已是代动词则原样返回 */
    fun pronominal(c: Conjugation): Conjugation {
        if (c.infinitive.startsWith("se ") || c.infinitive.startsWith("s'")) return c
        return pronominalize(c)
    }

    fun isVerb(word: String): Boolean = conjugate(word) != null

    /** 根据任意动词变体查找原形。如 "écoute" → "écouter", "mange" → "manger" */
    fun findInfinitive(form: String): String? {
        val f = form.trim().lowercase()
        if (f.length < 2) return null
        // 先直接尝试 conjugate（输入即原形）
        if (conjugate(f) != null) return f
        // 遍历候选动词，生成所有变位形式匹配
        val candidates = mutableListOf<String>()
        candidates.addAll(irregular.keys)
        candidates.addAll(regularVerbCandidates())
        for (v in candidates) {
            val c = conjugate(v) ?: continue
            val allForms = buildSet {
                add(v)
                addAll(c.present)
                addAll(c.imparfait)
                addAll(c.futurSimple)
                addAll(c.passeSimple)
                addAll(c.conditionnel)
                addAll(c.subjonctifPresent)
                addAll(c.subjonctifImparfait)
                addAll(c.imperatif)
                add(c.participePresent)
                add(c.participePasse)
                // 复合时态形式（助动词 + 过去分词）
                for (aux in c.present) {
                    add("$aux ${c.participePasse}")
                }
                for (aux in c.imparfait) {
                    add("$aux ${c.participePasse}")
                }
                // 代动词形式
                for (pr in listOf("me", "te", "se", "nous", "vous")) {
                    add("$pr $v")
                    for (p in c.present) add("$pr $p")
                    for (p in c.imparfait) add("$pr $p")
                }
            }
            if (allForms.any { it == f || it.replace("'", "") == f }) {
                return v
            }
        }
        return null
    }

    /** 生成候选规则动词列表（第一组和第二组高频词） */
    private fun regularVerbCandidates(): List<String> {
        // 常见规则动词
        return listOf(
            "parler", "manger", "marcher", "chanter", "donner", "penser", "trouver",
            "aimer", "parler", "regarder", "écouter", "travailler", "étudier", "habiter",
            "jouer", "laver", "lever", "coucher", "dépêcher", "appeler", "jeter",
            "acheter", "préférer", "espérer", "célébrer", "pénétrer", "considérer",
            "commencer", "placer", "avancer", "effacer", "prononcer", "annoncer",
            "nager", "voyager", "manger", "partager", "changer", "exiger", "ranger",
            "finir", "choisir", "grandir", "réussir", "remplir", "obéir", "réfléchir",
            "établir", "applaudir", "bâtir", "bénir", "blanchir", "franchir",
            "garantir", "investir", "nourrir", "pâlir", "réunir", "ronrir", "saisir",
            "vendre", "perdre", "attendre", "entendre", "répondre", "rendre",
            "descendre", "défendre", "rompre", "tordre", "fondre", "confondre"
        )
    }

    // ---------- 规则动词 ----------

    private fun buildRegular(infinitive: String): Conjugation? {
        return when {
            infinitive.endsWith("er") -> buildEr(infinitive)
            infinitive.endsWith("ir") && isSecondGroup(infinitive) -> buildIr(infinitive)
            infinitive.endsWith("re") && isRegularRe(infinitive) -> buildRe(infinitive)
            else -> null
        }
    }

    private fun isSecondGroup(w: String): Boolean {
        val thirdGroupIr = setOf(
            "partir", "sortir", "dormir", "sentir", "servir", "mentir", "courir",
            "mourir", "fuir", "cueillir", "acquérir", "venir", "tenir", "ouvrir",
            "offrir", "souffrir", "couvrir", "découvrir", "vêtir", "bouillir"
        )
        return w !in thirdGroupIr
    }

    private fun isRegularRe(w: String): Boolean {
        val irregularRe = setOf(
            "être", "faire", "dire", "lire", "écrire", "voir", "boire", "croire",
            "prendre", "comprendre", "apprendre", "mettre", "permettre", "promettre",
            "connaître", "reconnaître", "paraître", "apparaître", "naître",
            "vivre", "survivre", "suivre", "poursuivre", "rire", "sourire",
            "conduire", "produire", "construire", "traduire", "réduire", "détruire",
            "craindre", "peindre", "éteindre", "joindre", "plaindre", "vaincre",
            "battre", "valoir", "pleuvoir", "falloir", "savoir", "devoir",
            "pouvoir", "vouloir", "recevoir", "apercevoir", "taire"
        )
        if (w in irregularRe) return false
        for (s in listOf("aindre", "eindre", "oindre", "oudre")) {
            if (w.endsWith(s)) return false
        }
        return true
    }

    /** 第一组 -er */
    private fun buildEr(infinitive: String): Conjugation {
        val stem = infinitive.dropLast(2)
        // 拼写变体：-ger/-cer 只在 nous 需要变
        val p = erPresent(stem, infinitive)
        val imp = imparfaitOf(p[3]) // nous form 词干
        val futStem = infinitive.dropLast(1)
        val fut = listOf("ai", "as", "a", "ons", "ez", "ont").map { "$futStem$it" }
        val cond = listOf("ais", "ais", "ait", "ions", "iez", "aient").map { "$futStem$it" }
        val pSimple = listOf("ai", "as", "a", "âmes", "âtes", "èrent").map { "$stem$it" }
        val subj = listOf(p[0], p[1], p[2], p[3].dropLast(1) + "ions", p[3].dropLast(1) + "iez", p[5])
        val subjImp = listOf("asse", "asses", "ât", "assions", "assiez", "assent").map { "$stem$it" }
        val pp = stem + "é"
        val pPresent = p[3].dropLast(2) + "ant"
        return assemble(infinitive, 1, "avoir", p, imp, fut, pSimple, cond, subj, subjImp,
            listOf(p[0], p[3], p[4]), pPresent, pp)
    }

    private fun erPresent(stem: String, infinitive: String): List<String> {
        if (infinitive.endsWith("ger")) {
            return listOf("${stem}e", "${stem}es", "${stem}e", "${stem}eons", "${stem}ez", "${stem}ent")
        }
        if (infinitive.endsWith("cer")) {
            return listOf("${stem}ce", "${stem}ces", "${stem}ce", "${stem}çons", "${stem}cez", "${stem}cent")
        }
        return listOf("${stem}e", "${stem}es", "${stem}e", "${stem}ons", "${stem}ez", "${stem}ent")
    }

    /** 第二组 -ir */
    private fun buildIr(infinitive: String): Conjugation {
        val stem = infinitive.dropLast(2)
        val iss = stem + "iss"
        val p = listOf("${stem}s", "${stem}s", "${stem}t", "${iss}ons", "${iss}ez", "${iss}ent")
        val imp = listOf("ais", "ais", "ait", "ions", "iez", "aient").map { "$iss$it" }
        val fut = listOf("ai", "as", "a", "ons", "ez", "ont").map { "$infinitive$it" }
        val cond = listOf("ais", "ais", "ait", "ions", "iez", "aient").map { "$infinitive$it" }
        val pSimple = listOf("is", "is", "it", "îmes", "îtes", "irent").map { "$stem$it" }
        val subj = listOf("e", "es", "e", "ions", "iez", "ent").map { "$iss$it" }
        val subjImp = listOf("isse", "isses", "ît", "issions", "issiez", "issent").map { "$iss$it" }
        val pp = stem + "i"
        return assemble(infinitive, 2, "avoir", p, imp, fut, pSimple, cond, subj, subjImp,
            listOf(p[0], p[3], p[4]), iss + "ant", pp)
    }

    /** 规则 -re (vendre 型) */
    private fun buildRe(infinitive: String): Conjugation {
        val stem = infinitive.dropLast(2)
        val p = listOf("${stem}s", "${stem}s", stem, "${stem}ons", "${stem}ez", "${stem}ent")
        val imp = listOf("ais", "ais", "ait", "ions", "iez", "aient").map { "$stem$it" }
        val futStem = infinitive.dropLast(1)
        val fut = listOf("ai", "as", "a", "ons", "ez", "ont").map { "$futStem$it" }
        val cond = listOf("ais", "ais", "ait", "ions", "iez", "aient").map { "$futStem$it" }
        val pSimple = listOf("is", "is", "it", "îmes", "îtes", "irent").map { "$stem$it" }
        val subj = listOf("e", "es", "e", "ions", "iez", "ent").map { "$stem$it" }
        val subjImp = listOf("isse", "isses", "ît", "issions", "issiez", "issent").map { "$stem$it" }
        val pp = stem + "u"
        return assemble(infinitive, 3, "avoir", p, imp, fut, pSimple, cond, subj, subjImp,
            listOf(p[0], p[3], p[4]), stem + "ant", pp)
    }

    // ---------- 不规则动词 ----------

    private fun buildIrregular(infinitive: String, d: IrregularVerb): Conjugation {
        val p = d.present
        val imp = d.imparfait ?: listOf(
            p[3].dropLast(2) + "ais", p[3].dropLast(2) + "ais", p[3].dropLast(2) + "ait",
            p[3].dropLast(2) + "ions", p[3].dropLast(2) + "iez", p[3].dropLast(2) + "aient"
        )
        val fut = d.futur ?: run {
            val fs = d.futureStem ?: infinitive.dropLast(1)
            listOf("ai", "as", "a", "ons", "ez", "ont").map { "$fs$it" }
        }
        val cond = listOf("ais", "ais", "ait", "ions", "iez", "aient").map {
            val fs = d.futureStem ?: infinitive.dropLast(1)
            "$fs$it"
        }
        val pSimple = d.passeSimple ?: imp
        val subj = d.subjonctif ?: imp
        val subjImp = imp
        val impImp = d.imperatif ?: listOf(p[0], p[3], p[4])
        return assemble(infinitive, 3, d.auxiliary, p, imp, fut, pSimple, cond, subj, subjImp,
            impImp, d.participePresent, d.participePasse)
    }

    // ---------- 组装 ----------

    private fun assemble(
        infinitive: String, group: Int, auxiliary: String,
        present: List<String>, imparfait: List<String>, futur: List<String>,
        passeSimple: List<String>, conditionnel: List<String>, subj: List<String>,
        subjImp: List<String>, imperatif: List<String>,
        pPresent: String, pPasse: String
    ): Conjugation {
        val auxPP = if (auxiliary == "être") "été" else "eu"
        return Conjugation(
            infinitive, group, auxiliary,
            present, imparfait, futur, passeSimple, conditionnel, subj, subjImp,
            imperatif, pPresent, pPasse,
            gerondif = "en $pPresent",
            infinitifPasse = "$auxiliary $pPasse",
            gerondifPasse = "en ayant $pPasse"
        )
    }

    private fun imparfaitOf(nousForm: String): List<String> {
        val stem = nousForm.dropLast(2)
        return listOf("ais", "ais", "ait", "ions", "iez", "aient").map { "$stem$it" }
    }

    /** 被动语态：être 各时态 + 过去分词（仅及物动词有意义，通用生成） */
    fun passive(c: Conjugation): Conjugation {
        val e = êtreConj
        val pp = c.participePasse
        return Conjugation(
            c.infinitive, c.group, "être",
            e.present.map { "$it $pp" },
            e.imparfait.map { "$it $pp" },
            e.futurSimple.map { "$it $pp" },
            e.passeSimple.map { "$it $pp" },
            e.conditionnel.map { "$it $pp" },
            e.subjonctifPresent.map { "$it $pp" },
            e.subjonctifImparfait.map { "$it $pp" },
            e.imperatif.map { "$it $pp" },
            "étant $pp", pp,
            "en étant $pp", "avoir été $pp", "en ayant été $pp"
        )
    }

    /** 代动词化：给各人称加 se 代词 */
    private fun pronominalize(c: Conjugation): Conjugation {
        val subjects = listOf("me", "te", "se", "nous", "vous", "se")
        val imperPronouns = listOf("toi", "nous", "vous")
        fun prefix(forms: List<String>): List<String> =
            forms.mapIndexed { i, f -> "${subjects[i]} $f" }
        return Conjugation(
            c.infinitive, c.group, "être",
            prefix(c.present), prefix(c.imparfait), prefix(c.futurSimple),
            prefix(c.passeSimple), prefix(c.conditionnel), prefix(c.subjonctifPresent),
            prefix(c.subjonctifImparfait),
            c.imperatif.mapIndexed { i, f -> "${f}-${imperPronouns[i]}" },
            c.participePresent, c.participePasse,
            c.gerondif, "s'être ${c.participePasse}", c.gerondifPasse
        )
    }

    companion object {
        val êtreConj = Conjugation(
            "être", 3, "être",
            listOf("suis", "es", "est", "sommes", "êtes", "sont"),
            listOf("étais", "étais", "était", "étions", "étiez", "étaient"),
            listOf("serai", "seras", "sera", "serons", "serez", "seront"),
            listOf("fus", "fus", "fut", "fûmes", "fûtes", "furent"),
            listOf("serais", "serais", "serait", "serions", "seriez", "seraient"),
            listOf("sois", "sois", "soit", "soyons", "soyez", "soient"),
            listOf("fusse", "fusses", "fût", "fussions", "fussiez", "fussent"),
            listOf("sois", "soyons", "soyez"),
            "étant", "été", "en étant", "avoir été", "en ayant été"
        )

        val avoirConj = Conjugation(
            "avoir", 3, "avoir",
            listOf("ai", "as", "a", "avons", "avez", "ont"),
            listOf("avais", "avais", "avait", "avions", "aviez", "avaient"),
            listOf("aurai", "auras", "aura", "aurons", "aurez", "auront"),
            listOf("eus", "eus", "eut", "eûmes", "eûtes", "eurent"),
            listOf("aurais", "aurais", "aurait", "aurions", "auriez", "auraient"),
            listOf("aie", "aies", "ait", "ayons", "ayez", "aient"),
            listOf("eusse", "eusses", "eût", "eussions", "eussiez", "eussent"),
            listOf("aie", "ayons", "ayez"),
            "ayant", "eu", "en ayant", "avoir eu", "en ayant eu"
        )
    }
}

private fun loadIrregularVerbs(): Map<String, IrregularVerb> {
    val m = mutableMapOf<String, IrregularVerb>()

    fun put3(word: String, present: List<String>, aux: String = "avoir", pp: String,
             imp: List<String>? = null, fut: List<String>? = null, futureStem: String? = null,
             pSimple: List<String>? = null, subj: List<String>? = null, impImp: List<String>? = null,
             pPresent: String? = null): Unit {
        val nous = present[3]
        val nousStem = nous.dropLast(2)
        m[word] = IrregularVerb(
            present, aux,
            imparfait = imp ?: listOf(
                "${nousStem}ais", "${nousStem}ais", "${nousStem}ait",
                "${nousStem}ions", "${nousStem}iez", "${nousStem}aient"),
            futur = fut ?: futureStem?.let { fs ->
                listOf("ai", "as", "a", "ons", "ez", "ont").map { "$fs$it" }
            },
            futureStem = futureStem,
            passeSimple = pSimple,
            subjonctif = subj,
            imperatif = impImp ?: listOf(present[0], nous, present[4]),
            participePresent = pPresent ?: "${nousStem}ant",
            participePasse = pp
        )
    }

    // === 助动词 ===
    put3("être", listOf("suis", "es", "est", "sommes", "êtes", "sont"), "être", "été",
        imp = listOf("étais", "étais", "était", "étions", "étiez", "étaient"),
        fut = listOf("serai", "seras", "sera", "serons", "serez", "seront"),
        pSimple = listOf("fus", "fus", "fut", "fûmes", "fûtes", "furent"),
        subj = listOf("sois", "sois", "soit", "soyons", "soyez", "soient"),
        impImp = listOf("sois", "soyons", "soyez"), pPresent = "étant")
    put3("avoir", listOf("ai", "as", "a", "avons", "avez", "ont"), "avoir", "eu",
        imp = listOf("avais", "avais", "avait", "avions", "aviez", "avaient"),
        fut = listOf("aurai", "auras", "aura", "aurons", "aurez", "auront"),
        pSimple = listOf("eus", "eus", "eut", "eûmes", "eûtes", "eurent"),
        subj = listOf("aie", "aies", "ait", "ayons", "ayez", "aient"),
        impImp = listOf("aie", "ayons", "ayez"), pPresent = "ayant")

    // === aller 族 ===
    put3("aller", listOf("vais", "vas", "va", "allons", "allez", "vont"), "être", "allé",
        imp = listOf("allais", "allais", "allait", "allions", "alliez", "allaient"),
        fut = listOf("irai", "iras", "ira", "irons", "irez", "iront"),
        pSimple = listOf("allai", "allas", "alla", "allâmes", "allâtes", "allèrent"),
        subj = listOf("aille", "ailles", "aille", "allions", "alliez", "aillent"),
        impImp = listOf("va", "allons", "allez"), pPresent = "allant")

    // === faire 族 ===
    for (w in listOf("faire", "refaire")) {
        val stem = if (w == "faire") "fai" else "refai"
        put3(w, listOf("${stem}s", "${stem}s", "${stem}t", "${stem}sons", "${stem}tes", "${stem}ont".replace("ai", "o").let { if (w == "faire") "font" else "refont" }),
            "avoir", if (w == "faire") "fait" else "refait",
            imp = listOf("${stem}sais", "${stem}sais", "${stem}sait", "${stem}sions", "${stem}siez", "${stem}saient"),
            futureStem = if (w == "faire") "fer" else "refer",
            pSimple = listOf("fis", "fis", "fit", "fîmes", "fîtes", "firent").map { if (w == "faire") it else "re$it" },
            subj = listOf("fasse", "fasses", "fasse", "fassions", "fassiez", "fassent").map { if (w == "faire") it else "re$it" },
            impImp = listOf("${stem}s", "${stem}sons", "${stem}tes"), pPresent = "${stem}sant")
    }

    // === 情态动词 ===
    put3("pouvoir", listOf("peux", "peux", "peut", "pouvons", "pouvez", "peuvent"), "avoir", "pu",
        imp = listOf("pouvais", "pouvais", "pouvait", "pouvions", "pouviez", "pouvaient"),
        futureStem = "pourr",
        pSimple = listOf("pus", "pus", "put", "pûmes", "pûtes", "purent"),
        subj = listOf("puisse", "puisses", "puisse", "puissions", "puissiez", "puissent"),
        pPresent = "pouvant")
    put3("vouloir", listOf("veux", "veux", "veut", "voulons", "voulez", "veulent"), "avoir", "voulu",
        imp = listOf("voulais", "voulais", "voulait", "voulions", "vouliez", "voulaient"),
        futureStem = "voudr",
        pSimple = listOf("voulus", "voulus", "voulut", "voulûmes", "voulûtes", "voulurent"),
        subj = listOf("veuille", "veuilles", "veuille", "voulions", "vouliez", "veuillent"),
        impImp = listOf("veux", "voulons", "voulez"), pPresent = "voulant")
    put3("devoir", listOf("dois", "dois", "doit", "devons", "devez", "doivent"), "avoir", "dû",
        imp = listOf("devais", "devais", "devait", "devions", "deviez", "devaient"),
        futureStem = "devr",
        pSimple = listOf("dus", "dus", "dut", "dûmes", "dûtes", "durent"),
        subj = listOf("doive", "doives", "doive", "devions", "deviez", "doivent"),
        pPresent = "devant")
    put3("savoir", listOf("sais", "sais", "sait", "savons", "savez", "savent"), "avoir", "su",
        imp = listOf("savais", "savais", "savait", "savions", "saviez", "savaient"),
        futureStem = "saur",
        pSimple = listOf("sus", "sus", "sut", "sûmes", "sûtes", "surent"),
        subj = listOf("sache", "saches", "sache", "sachions", "sachiez", "sachent"),
        impImp = listOf("sache", "sachons", "sachez"), pPresent = "sachant")

    // === venir / tenir 族 ===
    val venirData = listOf(
        VenirEntry("venir", "vien", "venons", "ven", "être", "venu"),
        VenirEntry("devenir", "devien", "devenons", "deven", "être", "devenu"),
        VenirEntry("revenir", "revien", "revenons", "reven", "être", "revenu"),
        VenirEntry("souvenir", "souvien", "souvenons", "souven", "être", "souvenu"),
        VenirEntry("tenir", "tien", "tenons", "ten", "avoir", "tenu")
    )
    for (e in venirData) {
        val w = e.word; val st1 = e.stem1; val nous = e.nous; val nousStem = e.nousStem
        val aux = e.auxiliary; val pp = e.participePasse
        put3(w, listOf("${st1}s", "${st1}s", "${st1}t", nous, "${nousStem}ez", "${st1}ent"),
            aux, pp,
            imp = listOf("${nousStem}ais", "${nousStem}ais", "${nousStem}ait", "${nousStem}ions", "${nousStem}iez", "${nousStem}aient"),
            futureStem = w.dropLast(1),
            pSimple = listOf("${nousStem}ins", "${nousStem}ins", "${nousStem}int", "${nousStem}înmes", "${nousStem}întes", "${nousStem}inrent"),
            subj = listOf("${st1}ne", "${st1}nes", "${st1}ne", "${nousStem}ions", "${nousStem}iez", "${st1}nent"),
            impImp = listOf("${st1}s", nous, "${nousStem}ez"), pPresent = "${nousStem}ant")
    }

    // === prendre 族 ===
    for ((w, st, nousStem, pp) in listOf(
        arrayOf("prendre", "pren", "pren", "pris"),
        arrayOf("comprendre", "compren", "compren", "compris"),
        arrayOf("apprendre", "appren", "appren", "appris")
    )) {
        put3(w, listOf("${st}ds", "${st}ds", "${st}d", "${nousStem}ons", "${nousStem}ez", "${st}nent"),
            "avoir", pp,
            futureStem = w.dropLast(1),
            pSimple = listOf("${st}is", "${st}is", "${st}it", "${st}îmes", "${st}îtes", "${st}irent"),
            subj = listOf("${st}ne", "${st}nes", "${st}ne", "${nousStem}ions", "${nousStem}iez", "${st}nent"),
            impImp = listOf("${st}ds", "${nousStem}ons", "${nousStem}ez"), pPresent = "${nousStem}ant")
    }

    // === mettre 族 ===
    for ((w, st, pp) in listOf(
        arrayOf("mettre", "met", "mis"),
        arrayOf("permettre", "permet", "permis"),
        arrayOf("promettre", "promet", "promis")
    )) {
        val nousStem = st + "t"
        put3(w, listOf("${st}s", "${st}s", st, "${nousStem}ons", "${nousStem}ez", "${nousStem}ent"),
            "avoir", pp,
            futureStem = nousStem + "r",
            pSimple = listOf("${st}is", "${st}is", "${st}it", "${st}îmes", "${st}îtes", "${st}irent"),
            subj = listOf("${nousStem}e", "${nousStem}es", "${nousStem}e", "${nousStem}ions", "${nousStem}iez", "${nousStem}ent"),
            impImp = listOf("${st}s", "${nousStem}ons", "${nousStem}ez"), pPresent = "${nousStem}ant")
    }

    // === dire / lire / écrire ===
    put3("dire", listOf("dis", "dis", "dit", "disons", "dites", "disent"), "avoir", "dit",
        futureStem = "dir",
        pSimple = listOf("dis", "dis", "dit", "dîmes", "dîtes", "dirent"),
        subj = listOf("dise", "dises", "dise", "disions", "disiez", "disent"),
        impImp = listOf("dis", "disons", "dites"), pPresent = "disant")
    put3("lire", listOf("lis", "lis", "lit", "lisons", "lisez", "lisent"), "avoir", "lu",
        futureStem = "lir",
        pSimple = listOf("lus", "lus", "lut", "lûmes", "lûtes", "lurent"),
        subj = listOf("lise", "lises", "lise", "lisions", "lisiez", "lisent"),
        impImp = listOf("lis", "lisons", "lisez"), pPresent = "lisant")
    for (w in listOf("écrire", "décrire")) {
        val st = if (w == "écrire") "écriv" else "décriv"
        put3(w, listOf("${st.dropLast(1)}is", "${st.dropLast(1)}is", "${st.dropLast(1)}it", "${st}ons", "${st}ez", "${st}ent"),
            "avoir", if (w == "écrire") "écrit" else "décrit",
            futureStem = w.dropLast(1),
            pSimple = listOf("${st}is", "${st}is", "${st}it", "${st}îmes", "${st}îtes", "${st}irent"),
            subj = listOf("${st}e", "${st}es", "${st}e", "${st}ions", "${st}iez", "${st}ent"),
            impImp = listOf("${st.dropLast(1)}is", "${st}ons", "${st}ez"), pPresent = "${st}ant")
    }

    // === voir / recevoir ===
    put3("voir", listOf("vois", "vois", "voit", "voyons", "voyez", "voient"), "avoir", "vu",
        imp = listOf("voyais", "voyais", "voyait", "voyions", "voyiez", "voyaient"),
        fut = listOf("verrai", "verras", "verra", "verrons", "verrez", "verront"),
        pSimple = listOf("vis", "vis", "vit", "vîmes", "vîtes", "virent"),
        subj = listOf("voie", "voies", "voie", "voyions", "voyiez", "voient"),
        impImp = listOf("vois", "voyons", "voyez"), pPresent = "voyant")
    for ((w, st1, nousStem) in listOf(
        arrayOf("recevoir", "reçoi", "recev"),
        arrayOf("apercevoir", "aperçoi", "apercev")
    )) {
        put3(w, listOf("${st1}s", "${st1}s", "${st1}t", "${nousStem}ons", "${nousStem}ez", "${st1}vent"),
            "avoir", if (w == "recevoir") "reçu" else "aperçu",
            imp = listOf("${nousStem}ais", "${nousStem}ais", "${nousStem}ait", "${nousStem}ions", "${nousStem}iez", "${nousStem}aient"),
            futureStem = nousStem + "r",
            pSimple = listOf("${nousStem}us", "${nousStem}us", "${nousStem}ut", "${nousStem}ûmes", "${nousStem}ûtes", "${nousStem}urent"),
            subj = listOf("${st1}ve", "${st1}ves", "${st1}ve", "${nousStem}ions", "${nousStem}iez", "${st1}vent"),
            impImp = listOf("${st1}s", "${nousStem}ons", "${nousStem}ez"), pPresent = "${nousStem}ant")
    }

    // === connaître 族 ===
    for ((w, st, pp) in listOf(
        arrayOf("connaître", "conna", "connu"),
        arrayOf("reconnaître", "reconna", "reconnu"),
        arrayOf("paraître", "para", "paru"),
        arrayOf("apparaître", "appara", "apparu")
    )) {
        val nousStem = st + "iss"
        put3(w, listOf("${st}is", "${st}is", "${st}ît", "${nousStem}ons", "${nousStem}ez", "${nousStem}ent"),
            "avoir", pp,
            imp = listOf("${nousStem}ais", "${nousStem}ais", "${nousStem}ait", "${nousStem}ions", "${nousStem}iez", "${nousStem}aient"),
            futureStem = w.dropLast(1),
            pSimple = listOf("${st}us", "${st}us", "${st}ut", "${st}ûmes", "${st}ûtes", "${st}urent"),
            subj = listOf("${nousStem}e", "${nousStem}es", "${nousStem}e", "${nousStem}ions", "${nousStem}iez", "${nousStem}ent"),
            impImp = listOf("${st}is", "${nousStem}ons", "${nousStem}ez"), pPresent = "${nousStem}ant")
    }

    // === naître / vivre / suivre / rire / boire / croire ===
    put3("naître", listOf("nais", "nais", "naît", "naissons", "naissez", "naissent"), "être", "né",
        imp = listOf("naissais", "naissais", "naissait", "naissions", "naissiez", "naissaient"),
        futureStem = "naîtr",
        pSimple = listOf("naquis", "naquis", "naquit", "naquîmes", "naquîtes", "naquirent"),
        subj = listOf("naisse", "naisses", "naisse", "naissions", "naissiez", "naissent"),
        impImp = listOf("nais", "naissons", "naissez"), pPresent = "naissant")
    for (w in listOf("vivre", "survivre")) {
        val st = if (w == "vivre") "viv" else "surviv"
        put3(w, listOf("${st.dropLast(1)}s", "${st.dropLast(1)}s", "${st.dropLast(1)}t", "${st}ons", "${st}ez", "${st}ent"),
            "avoir", if (w == "vivre") "vécu" else "survécu",
            futureStem = st + "r",
            pSimple = listOf("${st.dropLast(1)}écus", "${st.dropLast(1)}écus", "${st.dropLast(1)}écut", "${st.dropLast(1)}écûmes", "${st.dropLast(1)}écûtes", "${st.dropLast(1)}écurent").map {
                if (w == "vivre") it else it.replace("v", "surv", ignoreCase = true)
            },
            subj = listOf("${st.dropLast(1)}ive", "${st.dropLast(1)}ives", "${st.dropLast(1)}ive", "${st}ions", "${st}iez", "${st}ent"),
            impImp = listOf("${st.dropLast(1)}is", "${st}ons", "${st}ez"), pPresent = "${st}ant")
    }
    for (w in listOf("suivre", "poursuivre")) {
        val st = if (w == "suivre") "suiv" else "poursuiv"
        put3(w, listOf("${st.dropLast(1)}s", "${st.dropLast(1)}s", "${st.dropLast(1)}t", "${st}ons", "${st}ez", "${st}ent"),
            "avoir", if (w == "suivre") "suivi" else "poursuivi",
            futureStem = st + "r",
            pSimple = listOf("${st}is", "${st}is", "${st}it", "${st}îmes", "${st}îtes", "${st}irent"),
            subj = listOf("${st.dropLast(1)}ive", "${st.dropLast(1)}ives", "${st.dropLast(1)}ive", "${st}ions", "${st}iez", "${st}ent"),
            impImp = listOf("${st.dropLast(1)}is", "${st}ons", "${st}ez"), pPresent = "${st}ant")
    }
    for (w in listOf("rire", "sourire")) {
        val st = if (w == "rire") "ri" else "souri"
        put3(w, listOf("${st}s", "${st}s", "${st}t", "${st}ons", "${st}ez", "${st}ent"),
            "avoir", if (w == "rire") "ri" else "souri",
            futureStem = w.dropLast(1),
            pSimple = listOf("${st}s", "${st}s", "${st}t", "${st}mes", "${st}tes", "${st}rent").map {
                it.replace("mes", "îmes").replace("tes", "îtes")
            },
            subj = listOf("${st}e", "${st}es", "${st}e", "${st}ions", "${st}iez", "${st}ent"),
            impImp = listOf("${st}s", "${st}ons", "${st}ez"), pPresent = "${st}ant")
    }
    put3("boire", listOf("bois", "bois", "boit", "buvons", "buvez", "boivent"), "avoir", "bu",
        imp = listOf("buvais", "buvais", "buvait", "buvions", "buviez", "buvaient"),
        futureStem = "boir",
        pSimple = listOf("bus", "bus", "but", "bûmes", "bûtes", "burent"),
        subj = listOf("boive", "boives", "boive", "buvions", "buviez", "boivent"),
        impImp = listOf("bois", "buvons", "buvez"), pPresent = "buvant")
    put3("croire", listOf("crois", "crois", "croit", "croyons", "croyez", "croient"), "avoir", "cru",
        imp = listOf("croyais", "croyais", "croyait", "croyions", "croyiez", "croyaient"),
        futureStem = "croir",
        pSimple = listOf("crus", "crus", "crut", "crûmes", "crûtes", "crurent"),
        subj = listOf("croie", "croies", "croie", "croyions", "croyiez", "croient"),
        impImp = listOf("crois", "croyons", "croyez"), pPresent = "croyant")

    // === -uire 族 ===
    for ((w, st) in listOf(
        "conduire" to "conduis", "produire" to "produis", "construire" to "construis",
        "traduire" to "traduis", "réduire" to "réduis", "détruire" to "détruis"
    )) {
        put3(w, listOf("${st}", "${st}", "${st}t", "${st}ons", "${st}ez", "${st}ent"),
            "avoir", "${st}it",
            futureStem = w.dropLast(1),
            pSimple = listOf("${st}is", "${st}is", "${st}it", "${st}îmes", "${st}îtes", "${st}irent"),
            subj = listOf("${st}e", "${st}es", "${st}e", "${st}ions", "${st}iez", "${st}ent"),
            impImp = listOf(st, "${st}ons", "${st}ez"), pPresent = "${st}ant")
    }

    // === -aindre/-eindre/-oindre 族 ===
    for ((w, st) in listOf(
        "craindre" to "crain", "peindre" to "pein", "éteindre" to "étein",
        "joindre" to "join", "plaindre" to "plain"
    )) {
        put3(w, listOf("${st}s", "${st}s", "${st}t", "${st}gnons", "${st}gnez", "${st}gnent"),
            "avoir", "${st}t",
            imp = listOf("${st}gnais", "${st}gnais", "${st}gnait", "${st}gnions", "${st}gniez", "${st}gnaient"),
            futureStem = w.dropLast(1),
            pSimple = listOf("${st}gnis", "${st}gnis", "${st}gnit", "${st}gnîmes", "${st}gnîtes", "${st}gnirent"),
            subj = listOf("${st}gne", "${st}gnes", "${st}gne", "${st}gnions", "${st}gniez", "${st}gnent"),
            impImp = listOf("${st}s", "${st}gnons", "${st}gnez"), pPresent = "${st}gnant")
    }

    // === vaincre ===
    put3("vaincre", listOf("vaincs", "vaincs", "vainc", "vainquons", "vainquez", "vainquent"), "avoir", "vaincu",
        imp = listOf("vainquais", "vainquais", "vainquait", "vainquions", "vainquiez", "vainquaient"),
        futureStem = "vaincr",
        pSimple = listOf("vainquis", "vainquis", "vainquit", "vainquîmes", "vainquîtes", "vainquirent"),
        subj = listOf("vainque", "vainques", "vainque", "vainquions", "vainquiez", "vainquent"),
        impImp = listOf("vaincs", "vainquons", "vainquez"), pPresent = "vainquant")

    // === courir / mourir / fuir ===
    put3("courir", listOf("cours", "cours", "court", "courons", "courez", "courent"), "avoir", "couru",
        imp = listOf("courais", "courais", "courait", "courions", "couriez", "couraient"),
        futureStem = "courr",
        pSimple = listOf("courus", "courus", "courut", "courûmes", "courûtes", "coururent"),
        subj = listOf("coure", "coures", "coure", "courions", "couriez", "courent"),
        impImp = listOf("cours", "courons", "courez"), pPresent = "courant")
    put3("mourir", listOf("meurs", "meurs", "meurt", "mourons", "mourez", "meurent"), "être", "mort",
        imp = listOf("mourais", "mourais", "mourait", "mourions", "mouriez", "mouraient"),
        futureStem = "mourr",
        pSimple = listOf("mourus", "mourus", "mourut", "mourûmes", "mourûtes", "moururent"),
        subj = listOf("meure", "meures", "meure", "mourions", "mouriez", "meurent"),
        impImp = listOf("meurs", "mourons", "mourez"), pPresent = "mourant")
    put3("fuir", listOf("fuis", "fuis", "fuit", "fuyons", "fuyez", "fuient"), "avoir", "fui",
        imp = listOf("fuyais", "fuyais", "fuyait", "fuyions", "fuyiez", "fuyaient"),
        futureStem = "fuir",
        pSimple = listOf("fuis", "fuis", "fuit", "fuîmes", "fuîtes", "fuirent"),
        subj = listOf("fuie", "fuies", "fuie", "fuyions", "fuyiez", "fuient"),
        impImp = listOf("fuis", "fuyons", "fuyez"), pPresent = "fuyant")

    // === partir 族 (-tir/-mir/-vrir) ===
    for ((w, st) in listOf(
        "partir" to "par", "sortir" to "sor", "dormir" to "dor", "sentir" to "sen",
        "servir" to "ser", "mentir" to "men"
    )) {
        val nousStem = w.dropLast(2)
        put3(w, listOf("${st}s", "${st}s", "${st}t", "${nousStem}ons", "${nousStem}ez", "${nousStem}ent"),
            "avoir", "${nousStem}i",
            futureStem = w.dropLast(1),
            pSimple = listOf("${nousStem}is", "${nousStem}is", "${nousStem}it", "${nousStem}îmes", "${nousStem}îtes", "${nousStem}irent"),
            subj = listOf("${nousStem}e", "${nousStem}es", "${nousStem}e", "${nousStem}ions", "${nousStem}iez", "${nousStem}ent"),
            impImp = listOf("${st}s", "${nousStem}ons", "${nousStem}ez"), pPresent = "${nousStem}ant")
    }

    // === ouvrir 族 ===
    for (w in listOf("ouvrir", "offrir", "souffrir", "couvrir", "découvrir")) {
        val st = w.dropLast(2)
        val pp = when (w) {
            "ouvrir" -> "ouvert"; "offrir" -> "offert"; "souffrir" -> "souffert"
            "couvrir" -> "couvert"; "découvrir" -> "découvert"; else -> "ouvert"
        }
        put3(w, listOf("${st}e", "${st}es", "${st}e", "${st}ons", "${st}ez", "${st}ent"),
            "avoir", pp,
            futureStem = w.dropLast(1),
            pSimple = listOf("${st}is", "${st}is", "${st}it", "${st}îmes", "${st}îtes", "${st}irent"),
            subj = listOf("${st}e", "${st}es", "${st}e", "${st}ions", "${st}iez", "${st}ent"),
            impImp = listOf("${st}e", "${st}ons", "${st}ez"), pPresent = "${st}ant")
    }

    // === battre ===
    put3("battre", listOf("bats", "bats", "bat", "battons", "battez", "battent"), "avoir", "battu",
        futureStem = "battr",
        pSimple = listOf("battis", "battis", "battit", "battîmes", "battîtes", "battirent"),
        subj = listOf("batte", "battes", "batte", "battions", "battiez", "battent"),
        impImp = listOf("bats", "battons", "battez"), pPresent = "battant")

    // === envoyer ===
    put3("envoyer", listOf("envoie", "envoies", "envoie", "envoyons", "envoyez", "envoient"), "avoir", "envoyé",
        imp = listOf("envoyais", "envoyais", "envoyait", "envoyions", "envoyiez", "envoyaient"),
        fut = listOf("enverrai", "enverras", "enverra", "enverrons", "enverrez", "enverront"),
        pSimple = listOf("envoyai", "envoyas", "envoya", "envoyâmes", "envoyâtes", "envoyèrent"),
        subj = listOf("envoie", "envoies", "envoie", "envoyions", "envoyiez", "envoient"),
        impImp = listOf("envoie", "envoyons", "envoyez"), pPresent = "envoyant")

    // === valoir / pleuvoir / falloir ===
    put3("valoir", listOf("vaux", "vaux", "vaut", "valons", "valez", "valent"), "avoir", "valu",
        futureStem = "vaudr",
        pSimple = listOf("valus", "valus", "valut", "valûmes", "valûtes", "valurent"),
        subj = listOf("vaille", "vailles", "vaille", "valions", "valiez", "vaillent"),
        impImp = listOf("vaux", "valons", "valez"), pPresent = "valant")
    put3("pleuvoir", listOf("pleut", "", "", "", "", ""), "avoir", "plu",
        futureStem = "pleuvr",
        pSimple = listOf("plut", "", "", "", "", ""),
        subj = listOf("pleuve", "", "", "", "", ""), pPresent = "pleuvant")
    put3("falloir", listOf("faut", "", "", "", "", ""), "avoir", "fallu",
        futureStem = "faudr",
        pSimple = listOf("fallut", "", "", "", "", ""),
        subj = listOf("faille", "", "", "", "", ""), pPresent = "fallant")

    // === taire (se taire) ===
    put3("taire", listOf("tais", "tais", "tait", "taisons", "taisez", "taisent"), "avoir", "tu",
        futureStem = "tair",
        pSimple = listOf("tus", "tus", "tut", "tûmes", "tûtes", "turent"),
        subj = listOf("taise", "taises", "taise", "taisions", "taisiez", "taisent"),
        impImp = listOf("tais", "taisons", "taisez"), pPresent = "taisant")

    return m
}

/** venir/tenir 族数据 */
private data class VenirEntry(
    val word: String,
    val stem1: String,
    val nous: String,
    val nousStem: String,
    val auxiliary: String,
    val participePasse: String
)
