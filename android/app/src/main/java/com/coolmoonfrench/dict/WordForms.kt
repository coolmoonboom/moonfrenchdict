package com.coolmoonfrench.dict

/**
 * 一个「带冠词 / 名词化」的展示形式及其音标。
 * [ipa] 存不带斜杠的纯音标（UI 渲染时自行补 /…/）。
 */
data class WordForm(val text: String, val ipa: String = "")

/**
 * 法语「冠词形式 / 动词名词化」生成器（AI 不可用时的离线兜底，也用于 AI 结果返回前的首帧占位）。
 *
 * - 名词：给出定冠词、不定冠词、部分冠词的单数形式，以及定冠词、不定冠词的复数形式，
 *   处理省音（le/la → l'）、缩合（de + le → du、de + les → des）与常规复数拼写；
 * - 动词：由过去分词构成名词化形式（parler → le parlé / du parlé / des parlé）。
 *
 * 音标由 [FrenchIpa] 推导，冠词与后词之间的省音、连诵用 ‿ 标注。仅供兜底，
 * 名词性音标准确度有限，AI 可用时应优先使用 [FormsService] 的结果。
 */
object WordForms {

    /** 收藏内容里「冠词形式」块的标题前缀。 */
    const val MEANING_PREFIX = "冠词："

    /** 依据词头与词性生成形式；无法判断词性时返回空列表。 */
    fun generate(word: String, pos: String, conjugator: VerbConjugator? = null): List<WordForm> {
        val w = cleanWord(word)
        if (w.isEmpty()) return emptyList()
        val p = normPos(pos)
        return when {
            isNoun(p) -> nounForms(w, p)
            isVerb(p) || conjugator?.isVerb(w) == true -> verbForms(w, conjugator)
            else -> emptyList()
        }
    }

    /** 组装收藏内容里的「冠词：…」行；无形式时返回空串。 */
    fun meaningLine(forms: List<WordForm>): String =
        if (forms.isEmpty()) "" else MEANING_PREFIX + forms.joinToString(" / ") { it.text }

    /** 把「冠词：…」行追加到收藏释义末尾（已存在则不重复追加）。 */
    fun appendToMeaning(meaning: String, forms: List<WordForm>): String {
        val line = meaningLine(forms)
        if (line.isEmpty()) return meaning
        if (meaning.contains(MEANING_PREFIX)) {
            // 已有旧行时用最新结果替换，避免同一条收藏里出现两行
            return meaning.lineSequence()
                .map { if (it.startsWith(MEANING_PREFIX)) line else it }
                .joinToString("\n")
        }
        return if (meaning.isBlank()) line else meaning.trimEnd() + "\n" + line
    }

    // ------------------------------------------------------------------
    // 词性 / 词头清理
    // ------------------------------------------------------------------

    private fun normPos(pos: String) = pos.lowercase().replace("·", ".").replace(" ", "")

    private fun isNoun(p: String) =
        p.contains("n.m") || p.contains("n.f") || p.startsWith("n.") || p == "n" || p.contains("nom")

    private fun isVerb(p: String) =
        p.startsWith("v") && (p.length <= 2 || p.getOrNull(1) == '.') || p.contains("verbe")

    private fun isFem(p: String) = p.contains("n.f") || (p.contains("f.") && p.contains("n."))

    private fun pluralPos(p: String) = p.contains(".pl") || p.contains("pl.")

    private val ARTICLE_RE =
        Regex("^(le|la|les|un|une|des|du|de\\s+la)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val ELISION_RE = Regex("^(l|d)['\u2019](.+)$", RegexOption.IGNORE_CASE)
    private val DE_ELISION_RE = Regex("^de\\s+l['\u2019](.+)$", RegexOption.IGNORE_CASE)

    /** 去掉可能已带上的冠词/缩合前缀，得到裸词头。 */
    fun cleanWord(raw: String): String {
        var w = raw.trim().replace('\u00A0', ' ').replace('\u2019', '\'').trim()
        var changed = true
        while (changed) {
            val before = w
            w = DE_ELISION_RE.find(w)?.groupValues?.get(1)?.trim()
                ?: ARTICLE_RE.find(w)?.groupValues?.get(2)?.trim()
                ?: ELISION_RE.find(w)?.groupValues?.get(2)?.trim()
                ?: w
            changed = w != before
        }
        return w.trim()
    }

    // ------------------------------------------------------------------
    // 名词：冠词形式
    // ------------------------------------------------------------------

    private val AL_EXCEPTIONS =
        setOf("bal", "carnaval", "festival", "récital", "chacal", "régal", "cal", "pal", "aval")
    private val OU_X = setOf("bijou", "caillou", "chou", "genou", "hibou", "joujou", "pou")
    private val AIL_X = mapOf(
        "travail" to "travaux", "vitrail" to "vitraux", "corail" to "coraux",
        "émail" to "émaux", "soupirail" to "soupiraux", "bail" to "baux"
    )

    /** 常规法语名词复数。 */
    fun plural(w: String): String {
        val lw = w.lowercase()
        AIL_X[lw]?.let { return it }
        if (lw.endsWith("al") && lw !in AL_EXCEPTIONS) return w.dropLast(2) + "aux"
        if (lw.endsWith("au") || lw.endsWith("eau") || lw.endsWith("eu")) return w + "x"
        if (lw.endsWith("ou") && lw in OU_X) return w + "x"
        if (w.endsWith("s") || w.endsWith("x") || w.endsWith("z")) return w
        return w + "s"
    }

    private val VOWELS = "aàâäeéèêëiîïoôöuùûüyœæ".toSet()
    private val H_ASPIRE = setOf(
        "hache", "haine", "hair", "halte", "hamac", "hameau", "hanche", "hangar",
        "haricot", "hasard", "hâte", "hausser", "haut", "haute", "hauteur", "hérisson",
        "héros", "hêtre", "heurter", "hibou", "hier", "hiérarchie", "hocher", "hockey",
        "hollande", "honte", "hors", "hublot", "huit", "hurler", "hutte"
    )

    /** 是否以元音或哑音 h 起首（决定是否省音）。 */
    fun startsWithVowelSound(w: String): Boolean {
        val c = w.firstOrNull()?.lowercaseChar() ?: return false
        if (c in VOWELS) return true
        return c == 'h' && w.lowercase() !in H_ASPIRE
    }

    private fun nounForms(w: String, p: String): List<WordForm> {
        val fem = isFem(p)
        val pluralOnly = pluralPos(p)
        val pl = plural(w)
        val vr = startsWithVowelSound(w)
        val vrPl = startsWithVowelSound(pl)
        val body = FrenchIpa.lookup(w)
        val bodyPl = FrenchIpa.lookup(pl)
        val out = mutableListOf<WordForm>()
        if (!pluralOnly) {
            // 定冠词单数
            out += when {
                vr -> WordForm("l'$w", "l‿$body")
                fem -> WordForm("la $w", "la $body")
                else -> WordForm("le $w", "lə $body")
            }
            // 不定冠词单数
            out += if (fem) WordForm("une $w", "yn $body") else WordForm("un $w", "œ̃ $body")
            // 部分冠词单数
            out += when {
                vr -> WordForm("de l'$w", "də l‿$body")
                fem -> WordForm("de la $w", "də la $body")
                else -> WordForm("du $w", "dy $body")
            }
        }
        // 定冠词复数
        out += if (vrPl) WordForm("les $pl", "le‿z$bodyPl") else WordForm("les $pl", "le $bodyPl")
        // 不定 / 部分冠词复数
        out += if (vrPl) WordForm("des $pl", "de‿z$bodyPl") else WordForm("des $pl", "de $bodyPl")
        return out
    }

    // ------------------------------------------------------------------
    // 动词：名词化
    // ------------------------------------------------------------------

    private fun verbForms(w: String, conjugator: VerbConjugator?): List<WordForm> {
        val pp = conjugator?.conjugate(w)?.participePasse?.trim().orEmpty().ifBlank { regularPp(w) }
        if (pp.isEmpty()) return emptyList()
        val body = FrenchIpa.lookup(pp)
        return listOf(
            WordForm("le $pp", "lə $body"),
            WordForm("du $pp", "dy $body"),
            WordForm("des $pp", "de $body"),
            WordForm("au $pp", "o $body")
        )
    }

    /** 规则动词过去分词兜底：-er → -é，-ir → -i，-re → -u。 */
    fun regularPp(w: String): String = when {
        w.endsWith("er") -> w.dropLast(2) + "é"
        w.endsWith("ir") -> w.dropLast(2) + "i"
        w.endsWith("re") -> w.dropLast(2) + "u"
        else -> ""
    }
}
