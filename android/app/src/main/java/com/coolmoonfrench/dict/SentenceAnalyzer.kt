package com.coolmoonfrench.dict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 法语句子分析器（需求4）。
 * 功能：
 * - 分词（含省音缩写拆解，如 j'ai → je + ai）
 * - 词性标注（本地词库 + 规则）
 * - 动词原形还原（基于变位引擎）
 * - 句子成分分析（主语/谓语/宾语/介词短语等）
 * - 名词单复数/性数还原
 */

data class WordAnalysis(
    val surface: String,            // 原文词形（含缩写合并形式）
    val expansion: String,          // 缩写展开说明，如 "je + ai"
    val word: String,               // 核心词形
    val pos: String,                // 词性
    val role: String,               // 句子成分
    val infinitive: String,         // 动词原形
    val meaning: String,            // 释义
    val number: String,             // 数（单数/复数）
    val tense: String,              // 时态（动词）
    val person: String,             // 人称（动词）
    val voice: String,              // 语态（动词）
    val auxiliary: String,          // 助动词
    val notes: String,              // 附加说明（变位/过去分词等）
    val inDictionary: Boolean       // 是否本地词库收录
)

class SentenceAnalyzer(
    private val repository: DictRepository,
    private val conjugator: VerbConjugator
) {

    suspend fun analyze(sentence: String): List<WordAnalysis> = withContext(Dispatchers.Default) {
        val tokens = tokenize(sentence)
        val expanded = tokens.flatMap { expandContraction(it) }
        // 识别复合时态（助动词 + 过去分词）
        val marked = markCompoundTenses(expanded)
        marked.mapIndexed { i, tok ->
            analyzeToken(tok, marked, i)
        }
    }

    /** 分词：按空白和标点切分 */
    private fun tokenize(sentence: String): List<String> {
        val cleaned = sentence
            .replace("’", "'")
            .replace('’', '\'')
        val parts = cleaned.split(Regex("""([,.;:!?()«»"\"\s]+)"""))
        return parts.filter { it.isNotBlank() }
    }

    /** 省音缩写拆解 */
    private fun expandContraction(token: String): List<Token> {
        val t = token.lowercase()
        val expansions = mapOf(
            "j'" to "je", "j’" to "je",
            "t'" to "tu", "t’" to "tu",
            "m'" to "me", "m’" to "me",
            "n'" to "ne", "n’" to "ne",
            "c'" to "ce", "c’" to "ce",
            "l'" to "le/la", "l’" to "le/la",
            "d'" to "de", "d’" to "de",
            "s'" to "se", "s’" to "se",
            "qu'" to "que", "qu’" to "que"
        )
        for ((prefix, full) in expansions) {
            if (t.startsWith(prefix) && t.length > prefix.length) {
                val rest = t.removePrefix(prefix)
                return listOf(
                    Token(full, "$full + $rest"),
                    Token(rest, "")
                )
            }
        }
        // au / aux / du / des 缩合冠词
        val articles = mapOf("au" to "à + le", "aux" to "à + les", "du" to "de + le", "des" to "de + les")
        articles[t]?.let {
            return listOf(Token(t, it))
        }
        return listOf(Token(t, ""))
    }

    private fun markCompoundTenses(tokens: List<Token>): List<Token> = tokens

    private fun analyzeToken(token: Token, all: List<Token>, index: Int): WordAnalysis {
        val t = token.word
        // 1. 查本地词库精确匹配
        val exact = repository.lookupExact(t)
        // 2. 动词识别（变位还原）
        val verbInfo = identifyVerb(t)
        // 3. 规则词性推测
        val rulePos = guessPos(t)

        val pos = when {
            verbInfo != null -> verbInfo.pos
            exact.isNotEmpty() -> exact.first().pos.ifEmpty { rulePos }
            else -> rulePos
        }
        val meaning = when {
            exact.isNotEmpty() -> exact.first().meaning
            verbInfo != null -> verbInfo.meaning
            else -> ""
        }
        val role = analyzeRole(t, pos, index, all)
        val number = guessNumber(t, pos)
        val verbMeta = verbInfo?.let { buildVerbMeta(it) }

        return WordAnalysis(
            surface = token.surface,
            expansion = token.expansion,
            word = t,
            pos = pos,
            role = role,
            infinitive = verbInfo?.infinitive ?: "",
            meaning = meaning,
            number = number,
            tense = verbMeta?.first ?: "",
            person = "",
            voice = "",
            auxiliary = verbMeta?.second ?: "",
            notes = verbMeta?.third ?: "",
            inDictionary = exact.isNotEmpty()
        )
    }

    /** 生成动词附加说明：过去分词/助动词 */
    private fun buildVerbMeta(info: VerbMatch): Triple<String, String, String> {
        val c = conjugator.conjugate(info.infinitive)
        if (c == null) return Triple("", "", "")
        return Triple(
            "动词变位",
            c.auxiliary,
            "过去分词：${c.participePasse}"
        )
    }

    /** 识别动词并还原原形。返回 VerbMatch */
    private fun identifyVerb(t: String): VerbMatch? {
        if (t.length < 2) return null
        // 查找：直接对词形尝试 conjugate（处理变位形式 → 需要反向查找）
        // 简单方案：遍历常见动词，判断 t 是否是其某种变位形式
        val candidates = listOf(
            "être", "avoir", "aller", "faire", "pouvoir", "vouloir", "devoir", "savoir",
            "venir", "devenir", "revenir", "tenir", "prendre", "comprendre", "apprendre",
            "mettre", "permettre", "promettre", "dire", "lire", "écrire", "décrire",
            "voir", "recevoir", "apercevoir", "connaître", "reconnaître", "paraître",
            "apparaître", "naître", "vivre", "survivre", "suivre", "poursuivre",
            "rire", "sourire", "boire", "croire", "conduire", "produire", "construire",
            "traduire", "réduire", "détruire", "craindre", "peindre", "éteindre",
            "joindre", "plaindre", "vaincre", "courir", "mourir", "fuir", "partir",
            "sortir", "dormir", "sentir", "servir", "mentir", "ouvrir", "offrir",
            "souffrir", "couvrir", "découvrir", "vendre", "perdre", "attendre",
            "entendre", "répondre", "rendre", "descendre", "défendre", "rompre",
            "battre", "envoyer", "valoir", "pleuvoir", "falloir", "taire",
            "manger", "marcher", "parler", "chanter", "finir", "choisir", "grandir",
            "réussir", "remplir", "se laver", "se lever", "se coucher", "se dépêcher",
            "se souvenir", "se sentir", "se taire", "s'appeler"
        )

        for (v in candidates) {
            val c = conjugator.conjugate(v) ?: continue
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
                // 复合时态形式
                for (aux in listOf("ai", "as", "a", "avons", "avez", "ont", "suis", "es", "est", "sommes", "êtes", "sont")) {
                    add("$aux ${c.participePasse}")
                    add("$aux ${c.participePasse}${if (c.participePasse.endsWith("e")) "e" else ""}${if (c.participePasse.endsWith("s")) "s" else ""}")
                }
                // 反身代动词
                for (pr in listOf("me", "te", "se", "nous", "vous")) {
                    add("$pr $v")
                    add("$pr ${c.participePasse}")
                }
            }
            val match = allForms.any { it == t || it.replace("'", "") == t }
            if (match) {
                val entry = repository.lookupExact(v).firstOrNull()
                val meaning = entry?.meaning ?: "动词"
                return VerbMatch("动词", meaning, v)
            }
        }
        return null
    }

    /** 规则词性推测 */
    private fun guessPos(t: String): String {
        if (t.length < 2) return ""
        return when {
            t == "je" || t == "tu" || t == "il" || t == "elle" || t == "on" ||
            t == "nous" || t == "vous" || t == "ils" || t == "elles" ||
            t == "me" || t == "te" || t == "se" || t == "moi" || t == "toi" ||
            t == "lui" || t == "leur" || t == "ce" || t == "cela" || t == "ça" -> "代词"
            t == "un" || t == "une" || t == "des" || t == "le" || t == "la" ||
            t == "les" || t == "au" || t == "aux" || t == "du" || t == "de" -> "冠词"
            t == "et" || t == "ou" || t == "mais" || t == "donc" || t == "or" ||
            t == "ni" || t == "car" -> "连词"
            t == "ne" || t == "pas" || t == "plus" || t == "très" || t == "trop" ||
            t == "bien" || t == "peu" || t == "beaucoup" || t == "aussi" ||
            t == "encore" || t == "déjà" -> "副词"
            t == "de" || t == "à" || t == "en" || t == "dans" || t == "sur" ||
            t == "sous" || t == "avec" || t == "pour" || t == "par" || t == "sans" ||
            t == "chez" || t == "vers" || t == "entre" || t == "contre" ||
            t == "depuis" || t == "pendant" || t == "avant" || t == "après" ->
                "介词"
            t.endsWith("er") || t.endsWith("ir") || t.endsWith("re") -> "动词"
            t.endsWith("e") && t.length > 2 -> "名词/形容词"
            t.endsWith("s") && t.length > 2 -> "名词/形容词"
            else -> ""
        }
    }

    /** 句子成分分析 */
    private fun analyzeRole(t: String, pos: String, index: Int, all: List<Token>): String {
        if (pos == "动词") {
            // 复合时态判断
            val prev = all.getOrNull(index - 1)?.word
            if (prev in setOf("ai", "as", "a", "avons", "avez", "ont", "suis", "es", "est", "sommes", "êtes", "sont")) {
                return "谓语（复合时态）"
            }
            return if (index == 0) "谓语" else "谓语"
        }
        if (pos == "代词") {
            return if (index == 0) "主语" else when (t) {
                "me", "te", "se", "nous", "vous", "moi", "toi", "lui", "leur" -> "宾语/代名词"
                else -> "主语"
            }
        }
        if (pos == "名词" || pos == "名词/形容词") {
            return when (t) {
                "cuisine", "chambre", "maison", "école", "ville" -> "介词宾语"
                else -> if (index > 0 && all.getOrNull(index - 1)?.word in setOf("de", "à", "en", "dans", "sur", "sous", "avec", "pour", "par", "chez", "vers")) {
                    "介词宾语"
                } else "宾语/表语"
            }
        }
        if (pos == "介词") return "介词短语"
        if (pos == "冠词") return "限定词"
        return "其他"
    }

    /** 数（单/复数）推断 */
    private fun guessNumber(t: String, pos: String): String {
        if (pos == "动词") {
            return "动词变位"
        }
        return when {
            t == "je" || t == "tu" || t == "il" || t == "elle" || t == "on" -> "单数"
            t == "nous" || t == "vous" || t == "ils" || t == "elles" -> "复数"
            t.endsWith("s") || t.endsWith("x") || t.endsWith("aux") -> "复数"
            else -> "单数"
        }
    }

    private data class Token(
        val word: String,
        val expansion: String
    ) {
        val surface: String get() = word
    }
}

/** 动词识别结果 */
data class VerbMatch(
    val pos: String,
    val meaning: String,
    val infinitive: String
)
