package com.coolmoonfrench.dict

/**
 * 本地法语发音引擎：把法语拼写转换为 IPA 音标（不含斜杠）。
 *
 * - 优先查询内置音标表（覆盖词族中出现的全部不定式与代动词短语）；
 * - 未命中时用拼写规则推导（覆盖第一/二组规则动词以及绝大多数常见变位形式）；
 * - 全程离线、确定性输出，便于在分组页一次性渲染大量动词音标。
 *
 * 规则覆盖范围经过取舍：以动词变位形式为主，个别名词/形容词的例外不在此处理。
 */
object FrenchIpa {

    /** 返回不含斜杠的 IPA；无法推导时返回空串。 */
    fun lookup(raw: String): String {
        val w = raw.trim().lowercase().replace('’', '\'')
        if (w.isEmpty()) return ""
        overrides[w]?.let { return it }
        return guess(w)
    }

    /** 返回带斜杠的 IPA；无法推导时返回空串。 */
    fun wrap(raw: String): String {
        val ipa = lookup(raw)
        return if (ipa.isEmpty()) "" else "/$ipa/"
    }

    // ------------------------------------------------------------------
    // 内置音标（不定式 / 代动词短语）
    // ------------------------------------------------------------------
    private val overrides: Map<String, String> = mapOf(
        // 助动词
        "être" to "ɛtʁ", "avoir" to "avwaʁ",
        // aller / envoyer / faire 族
        "aller" to "ale", "s'en aller" to "sɑ̃.n‿ale",
        "envoyer" to "ɑ̃vwaje",
        "faire" to "fɛʁ", "refaire" to "ʁəfɛʁ",
        // 情态动词
        "pouvoir" to "puvwaʁ", "vouloir" to "vulwaʁ",
        "devoir" to "dəvwaʁ", "savoir" to "savwaʁ",
        // -oir
        "voir" to "vwaʁ", "recevoir" to "ʁəsəvwaʁ", "apercevoir" to "apɛʁsəvwaʁ",
        // -enir
        "venir" to "vəniʁ", "devenir" to "dəvəniʁ", "revenir" to "ʁəvəniʁ",
        "souvenir" to "suvəniʁ", "tenir" to "təniʁ",
        // prendre
        "prendre" to "pʁɑ̃dʁ", "comprendre" to "kɔ̃pʁɑ̃dʁ", "apprendre" to "apʁɑ̃dʁ",
        // -ttre
        "mettre" to "mɛtʁ", "permettre" to "pɛʁmɛtʁ",
        "promettre" to "pʁɔmɛtʁ", "battre" to "batʁ",
        // -tir / -mir / -vir
        "partir" to "paʁtiʁ", "sortir" to "sɔʁtiʁ", "dormir" to "dɔʁmiʁ",
        "sentir" to "sɑ̃tiʁ", "servir" to "sɛʁviʁ", "mentir" to "mɑ̃tiʁ",
        // -vrir
        "ouvrir" to "uvʁiʁ", "offrir" to "ɔfʁiʁ", "souffrir" to "sufʁiʁ",
        "couvrir" to "kuvʁiʁ", "découvrir" to "dekuvʁiʁ",
        // -indre
        "craindre" to "kʁɛ̃dʁ", "peindre" to "pɛ̃dʁ", "éteindre" to "etɛ̃dʁ",
        "joindre" to "ʒwɛ̃dʁ", "plaindre" to "plɛ̃dʁ",
        // -uire
        "conduire" to "kɔ̃dɥiʁ", "produire" to "pʁɔdɥiʁ",
        "construire" to "kɔ̃stʁɥiʁ", "traduire" to "tʁadɥiʁ",
        "réduire" to "ʁedɥiʁ", "détruire" to "detʁɥiʁ",
        // -aître
        "connaître" to "kɔnɛtʁ", "reconnaître" to "ʁəkɔnɛtʁ",
        "paraître" to "paʁɛtʁ", "apparaître" to "apaʁɛtʁ", "naître" to "nɛtʁ",
        // -ire
        "dire" to "diʁ", "lire" to "liʁ", "écrire" to "ekʁiʁ", "décrire" to "dekʁiʁ",
        // -re 规则型
        "vendre" to "vɑ̃dʁ", "perdre" to "pɛʁdʁ", "attendre" to "atɑ̃dʁ",
        "entendre" to "ɑ̃tɑ̃dʁ", "répondre" to "ʁepɔ̃dʁ", "rendre" to "ʁɑ̃dʁ",
        "descendre" to "desɑ̃dʁ", "défendre" to "defɑ̃dʁ", "rompre" to "ʁɔ̃pʁ",
        // -re 词干变化型
        "vivre" to "vivʁ", "survivre" to "syʁvivʁ",
        "suivre" to "sɥivʁ", "poursuivre" to "puʁsɥivʁ",
        "rire" to "ʁiʁ", "sourire" to "suʁiʁ",
        "boire" to "bwaʁ", "croire" to "kʁwaʁ",
        // 单独不规则
        "vaincre" to "vɛ̃kʁ",
        "courir" to "kuʁiʁ", "mourir" to "muʁiʁ", "fuir" to "fɥiʁ",
        "valoir" to "valwaʁ", "pleuvoir" to "pløvwaʁ", "falloir" to "falwaʁ",
        "taire" to "tɛʁ",
        // 代动词短语
        "se laver" to "sə lave", "se lever" to "sə ləve", "se coucher" to "sə kuʃe",
        "se dépêcher" to "sə depeʃe", "se souvenir" to "sə suvəniʁ",
        "se sentir" to "sə sɑ̃tiʁ", "se taire" to "sə tɛʁ", "s'appeler" to "sa.pəle",
        // 常见不规则形式（规则易错项）
        "est" to "ɛ", "sens" to "sɑ̃s",
        "ville" to "vil", "fils" to "fis", "monsieur" to "məsjø",
        // avoir 的 eu-/eû- 系列（规则推导易错）
        "eu" to "y", "eus" to "y", "eut" to "y", "eûmes" to "ym", "eûtes" to "yt",
        "eurent" to "yʁ", "eusse" to "ys", "eusses" to "ys", "eût" to "y",
        "eussions" to "ysjɔ̃", "eussiez" to "ysje", "eussent" to "ys",
        "ayant" to "ɛjɑ̃"
    )

    // ------------------------------------------------------------------
    // 规则推导
    // ------------------------------------------------------------------

    private val vowelChars = setOf(
        'a', 'e', 'i', 'o', 'u', 'y',
        'à', 'â', 'ä', 'é', 'è', 'ê', 'ë',
        'î', 'ï', 'ô', 'ö', 'ù', 'û', 'ü', 'œ', 'æ'
    )

    private fun isVowelChar(c: Char?): Boolean = c != null && c in vowelChars

    /** 词尾拼写规整：把变位结尾转成等价、规则易处理的形式。 */
    private fun applyEndings(input: String): String {
        val s = input
        // 第三人称复数 -ent 整体不发音：parlent -> parle（保留前接辅音发音）
        if (s.length >= 5 && s.endsWith("ent")) return s.dropLast(2)
        // 不定式 / 命令式 -er、-ez -> é
        if (s.length >= 4 && s.endsWith("er")) return s.dropLast(2) + "é"
        if (s.length >= 4 && s.endsWith("ez")) return s.dropLast(2) + "é"
        // 简单将来/简单过去第一人称单数 -ai -> é
        if (s.length >= 4 && s.endsWith("ai")) return s.dropLast(2) + "é"
        // 第二人称单数 -es -> e（词尾 e 不发音）
        if (s.length >= 4 && s.endsWith("es")) return s.dropLast(1)
        return s
    }

    /** 判断鼻化：n/m 之后是词尾或非 n/m 的辅音时成立。 */
    private fun nasal(w: String, nasalIdx: Int): Boolean {
        val after = if (nasalIdx + 1 < w.length) w[nasalIdx + 1] else null
        if (after == null) return true
        if (isVowelChar(after)) return false
        if (after == 'n' || after == 'm') return false
        return true
    }

    /** 该位置起的剩余字符是否全为辅音（即处于词尾辅音区）。 */
    private fun finalRegion(w: String, i: Int): Boolean {
        for (k in i until w.length) {
            val c = w[k]
            if (isVowelChar(c) || c == '\'') return false
        }
        return true
    }

    private val pronouncedFinals = setOf('c', 'r', 'f', 'l')

    private fun silentFinal(w: String, i: Int): Boolean =
        finalRegion(w, i) && w[i] !in pronouncedFinals

    private fun ePhoneme(w: String, i: Int): String? {
        if (i == w.length - 1) return null // 词尾 e 不发音
        val n1 = if (i + 1 < w.length) w[i + 1] else null
        val n2 = if (i + 2 < w.length) w[i + 2] else null
        if (n1 == null) return null
        if (isVowelChar(n1)) return "ə"
        // 闭音节：e + 辅音 + (辅音或词尾) -> ɛ
        if (!isVowelChar(n1) && (n2 == null || !isVowelChar(n2))) return "ɛ"
        return "ə"
    }

    private fun oPhoneme(w: String, i: Int): String {
        val n1 = if (i + 1 < w.length) w[i + 1] else null
        val n2 = if (i + 2 < w.length) w[i + 2] else null
        if (n1 == null) return "o"
        if (!isVowelChar(n1) && (n2 == null || !isVowelChar(n2))) return "ɔ"
        return "o"
    }

    private fun guess(input: String): String {
        if (input.contains(' ') || input.contains('-')) {
            return input.split(Regex("[\\s-]+")).filter { it.isNotEmpty() }
                .joinToString(" ") { guess(it) }
        }
        val w = applyEndings(input.lowercase().replace('’', '\''))
        if (w.isEmpty()) return ""
        val n = w.length
        val out = StringBuilder()
        var i = 0

        fun at(idx: Int): Char? = if (idx in 0 until n) w[idx] else null
        fun vowelAt(idx: Int): Boolean = isVowelChar(at(idx))

        while (i < n) {
            val c = w[i]
            val c1 = at(i + 1)
            val c2 = at(i + 2)
            val c3 = at(i + 3)

            if (c == '\'') { i++; continue }

            // ---- 三元组合 ----
            if (c == 'e' && c1 == 'a' && c2 == 'u') { out.append("o"); i += 3; continue }
            if (c == 'œ' && c1 == 'u') { out.append("ø"); i += 2; continue }

            // ---- ill ----
            if (c == 'i' && c1 == 'l' && c2 == 'l') {
                out.append(if (isVowelChar(at(i - 1))) "j" else "ij")
                i += 3; continue
            }
            // eill / aill
            if ((c == 'e' || c == 'a') && c1 == 'i' && c2 == 'l' && c3 == 'l') {
                out.append(if (c == 'e') "ɛj" else "aj"); i += 4; continue
            }

            // ---- 鼻化元音 ----
            if (c == 'o' && c1 == 'i' && c2 == 'n' && nasal(w, i + 2)) { out.append("wɛ̃"); i += 3; continue }
            if (c == 'i' && c1 == 'e' && c2 == 'n') {
                if (nasal(w, i + 2)) { out.append("jɛ̃"); i += 3; continue }
                out.append("j"); i += 1; continue
            }
            if ((c == 'a' || c == 'e') && c1 == 'i' && (c2 == 'n' || c2 == 'm') && nasal(w, i + 2)) {
                out.append("ɛ̃"); i += 3; continue
            }
            if ((c == 'a' || c == 'e') && (c1 == 'n' || c1 == 'm') && nasal(w, i + 1)) {
                out.append("ɑ̃"); i += 2; continue
            }
            if (c == 'i' && (c1 == 'n' || c1 == 'm') && nasal(w, i + 1)) { out.append("ɛ̃"); i += 2; continue }
            if (c == 'o' && (c1 == 'n' || c1 == 'm') && nasal(w, i + 1)) { out.append("ɔ̃"); i += 2; continue }
            if (c == 'u' && (c1 == 'n' || c1 == 'm') && nasal(w, i + 1)) { out.append("œ̃"); i += 2; continue }

            // ---- 元音组合 ----
            if (c == 'a' && c1 == 'u') { out.append("o"); i += 2; continue }
            if ((c == 'a' && c1 == 'i') || (c == 'e' && c1 == 'i')) { out.append("ɛ"); i += 2; continue }
            if (c == 'e' && c1 == 'u') { out.append("ø"); i += 2; continue }
            if (c == 'o' && c1 == 'u') {
                if (vowelAt(i + 2)) { out.append("w"); i += 2; continue }
                out.append("u"); i += 2; continue
            }
            if (c == 'o' && c1 == 'i') { out.append("wa"); i += 2; continue }
            if (c == 'u' && c1 == 'i') { out.append("ɥi"); i += 2; continue }
            if (c == 'o' && c1 == 'y') { out.append("waj"); i += 2; continue }
            if (c == 'u' && c1 == 'y') { out.append("ɥij"); i += 2; continue }
            if ((c == 'a' || c == 'e') && c1 == 'y') { out.append("ɛj"); i += 2; continue }
            // -rions / -riez 中 i 仍是元音：suivrions -> sɥivʁijɔ̃
            if (c == 'i' && at(i - 1) == 'r' && vowelAt(i + 1)) { out.append("ij"); i += 1; continue }
            // i/u 作半元音
            if (c == 'i' && vowelAt(i + 1)) { out.append("j"); i += 1; continue }
            if (c == 'u' && vowelAt(i + 1)) { out.append("ɥ"); i += 1; continue }
            if (c == 'y') {
                out.append(if (vowelAt(i + 1)) "j" else "i"); i += 1; continue
            }

            // ---- 辅音组合 ----
            if (c == 'c' && c1 == 'h') { out.append("ʃ"); i += 2; continue }
            if (c == 'p' && c1 == 'h') { out.append("f"); i += 2; continue }
            if (c == 'g' && c1 == 'n') { out.append("ɲ"); i += 2; continue }
            if (c == 'q' && c1 == 'u') { out.append("k"); i += 2; continue }
            if (c == 't' && c1 == 'h') { out.append("t"); i += 2; continue }
            if (c == 'g' && c1 == 'u' && (c2 == 'e' || c2 == 'i' || c2 == 'é' || c2 == 'è')) {
                out.append("g"); i += 2; continue
            }
            if (c == 'g' && c1 == 'e' && (c2 == 'a' || c2 == 'o' || c2 == 'u')) {
                out.append("ʒ"); i += 2; continue
            }
            if (c == 's' && c1 == 's') { out.append("s"); i += 2; continue }
            if (c == 'm' && c1 == 'm') { out.append("m"); i += 2; continue }
            if (c == 'n' && c1 == 'n') { out.append("n"); i += 2; continue }
            if (c == 't' && c1 == 't') { out.append("t"); i += 2; continue }
            if (c == 'l' && c1 == 'l') { out.append("l"); i += 2; continue }
            // 其余常见双写辅音只发一次
            if (c == c1 && c in "bdfprz") {
                out.append(if (c == 'r') "ʁ" else c.toString())
                i += 2; continue
            }

            // ---- 单字符 ----
            when (c) {
                'a', 'à', 'ä' -> { out.append("a"); i++ }
                'â' -> { out.append("ɑ"); i++ }
                'é' -> { out.append("e"); i++ }
                'è', 'ê', 'ë' -> { out.append("ɛ"); i++ }
                'i', 'î', 'ï' -> { out.append("i"); i++ }
                'o', 'ô', 'ö' -> { out.append(oPhoneme(w, i)); i++ }
                'u', 'ù', 'û', 'ü' -> { out.append("y"); i++ }
                'œ' -> { out.append("œ"); i++ }
                'æ' -> { out.append("e"); i++ }
                'e' -> { ePhoneme(w, i)?.let { out.append(it) }; i++ }
                'b' -> { out.append("b"); i++ }
                'c', 'ç' -> {
                    if (c == 'ç' || c1 == 'e' || c1 == 'i' || c1 == 'y' ||
                        c1 == 'é' || c1 == 'è' || c1 == 'ê'
                    ) {
                        out.append("s")
                    } else if (!silentFinal(w, i)) {
                        out.append("k")
                    }
                    i++
                }
                'd' -> { if (!silentFinal(w, i)) out.append("d"); i++ }
                'f' -> { out.append("f"); i++ }
                'g' -> {
                    if (c1 == 'e' || c1 == 'i' || c1 == 'y' || c1 == 'é' || c1 == 'è' || c1 == 'ê') {
                        out.append("ʒ")
                    } else if (!silentFinal(w, i)) {
                        out.append("g")
                    }
                    i++
                }
                'h' -> { i++ }
                'j' -> { out.append("ʒ"); i++ }
                'k' -> { out.append("k"); i++ }
                'l' -> { out.append("l"); i++ }
                'm' -> { out.append("m"); i++ }
                'n' -> { out.append("n"); i++ }
                'p' -> { if (!silentFinal(w, i)) out.append("p"); i++ }
                'r' -> { out.append("ʁ"); i++ }
                's' -> {
                    if (isVowelChar(at(i - 1)) && isVowelChar(c1)) out.append("z")
                    else if (!silentFinal(w, i)) out.append("s")
                    i++
                }
                't' -> { if (!silentFinal(w, i)) out.append("t"); i++ }
                'v' -> { out.append("v"); i++ }
                'w' -> { out.append("v"); i++ }
                'x' -> {
                    if (isVowelChar(at(i - 1)) && isVowelChar(c1)) out.append("gz")
                    else if (!silentFinal(w, i)) out.append("ks")
                    i++
                }
                'z' -> { if (!silentFinal(w, i)) out.append("z"); i++ }
                else -> { i++ }
            }
        }
        return out.toString()
    }
}
