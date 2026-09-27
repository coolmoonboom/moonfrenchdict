package com.coolmoonfrench.dict

/**
 * 收藏释义统一文本（ImportWordParser.buildMeaning 产出，或词典兜底旧格式）的解析器。
 * 悬浮窗按「单词+音标 / 法语例句 / 例句翻译 / 词性中文释义」分块展示时用这里拆出各字段。
 */
data class ParsedMeaning(
    val pos: String = "",
    val zh: String = "",
    val ipa: String = "",
    val exampleFr: String = "",
    val exampleZh: String = ""
) {
    /** 词卡义项行：词性 + 中文释义（不含音标/例句噪声）。 */
    fun gloss(): String = if (pos.isNotEmpty()) "【$pos】$zh" else zh
}

object FavoriteMeaning {

    /** buildMeaning 的逆解析；旧式整段文本原样放 zh，不会丢内容。 */
    fun parse(meaning: String): ParsedMeaning {
        if (meaning.isBlank()) return ParsedMeaning()
        var pos = ""
        var zh = ""
        var ipa = ""
        var fr = ""
        var zhEx = ""
        val zhParts = StringBuilder()
        meaning.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            when {
                line.startsWith("音标") -> ipa = cleanIpa(line.removePrefix("音标").trim())
                line.startsWith("例句：") -> fr = line.removePrefix("例句：").trim()
                line.startsWith("例句:") -> fr = line.removePrefix("例句:").trim()
                line.startsWith("中文：") -> zhEx = line.removePrefix("中文：").trim()
                line.startsWith("中文:") -> zhEx = line.removePrefix("中文:").trim()
                else -> {
                    // 首行可能是【词性】+ 释义；兜底旧文本（多行）合并进 zh
                    if (zhParts.isEmpty()) {
                        val m = Regex("^【(.+?)】(.*)$").find(line)
                        if (m != null) {
                            pos = m.groupValues[1].trim()
                            zhParts.append(m.groupValues[2].trim())
                        } else {
                            zhParts.append(line)
                        }
                    } else {
                        zhParts.append(" ").append(line)
                    }
                }
            }
        }
        zh = zhParts.toString().trim()
        // 旧格式释义常带 's'~ v.pr.' 之类的反身标记在尾部；pos 留空时把行内 v.pr. 收进词性。
        return ParsedMeaning(pos = pos, zh = zh, ipa = ipa, exampleFr = fr, exampleZh = zhEx)
    }

    /**
     * 义项是否「以中文为主」。中英混排的英文词典回声（如
     * "first/second-person singular conditional of faire"）按拉丁/中文字符占比拒收，
     * 词典式释义里少量法语法缩写（s'~ v.pr.）允许保留。
     */
    fun chineseDominant(s: String): Boolean {
        val cjk = s.count { it.code in 0x4E00..0x9FFF }
        if (cjk == 0) return false
        val latin = s.count { it.isLetter() && it.code in 0x41..0x7FF }
        return cjk >= latin * 0.6
    }

    /**
     * 中文语音的朗读文本：只保留汉字和中文标点。
     * AI 偶尔把词性/法语塞进释义行（【n.f.】、s'~ 等），中文引擎读这些只会含糊发音，
     * 法语文本一律交给法语引擎。
     */
    fun chineseForSpeech(s: String): String =
        s.filter { ch ->
            ch != '【' && ch != '】' && ch != '〖' && ch != '〗' && (
                ch.code in 0x4E00..0x9FFF ||
                    ch.code in 0x3000..0x303F ||
                    ch in "，。；：？！、（）“”‘’—…"
                )
        }.trim()

    /**
     * 收藏词键归一：NFC 组合字符、不间断空格、零宽字符、首尾与连续空白收敛。
     * AI 导入/整理反复写回会产生视觉上完全相同但字节不同的键（如 é 的两种编码），
     * 造成收藏列表重复、LazyColumn key 冲突崩溃，统一从这里归一。
     */
    fun normalizeWordKey(raw: String): String {
        var s = java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFC)
        s = s.replace('\u00A0', ' ').replace(Regex("\\s+"), " ")
        val zeroWidth = listOf(0x200B, 0x200C, 0x200D, 0xFEFF, 0x200E, 0x200F)
        s = s.filter { it.code !in zeroWidth }
        return s
    }

    /** 音标行清洗：取第一段 /…/；没有斜杠时去掉行尾粘连的词性缩写再补斜杠。 */
    private fun cleanIpa(raw: String): String {
        if (raw.isEmpty()) return ""
        Regex("/[^/]+/").find(raw)?.value?.let { return it }
        val tokens = raw.split(" ").filter { t ->
            // 粘连进来的词性缩写（n.f. / v.pr. / adj. 之类）丢掉
            !Regex("^[a-zA-Z'.~]+(\\.[a-zA-Z'~]*)+\\.?$").matches(t)
        }
        val body = tokens.joinToString(" ").trim().removeSurrounding("[", "]")
        if (body.isEmpty()) return ""
        return if (body.startsWith("/")) body else "/$body/"
    }

    /**
     * 收藏列表展示规则：用户释义（编辑/整理写入）覆盖词典内容。
     * 词典命中时保留词典词头（可能大小写/连字符更准），词性取用户释义解析结果，
     * 用户释义为空时回落词典词性；释义行/gloss 用用户内容。纯函数便于测试。
     */
    fun applyUserMeaning(dict: DictEntry?, word: String, userMeaning: String): DictEntry {
        val parsed = parse(userMeaning)
        val gloss = parsed.gloss().ifBlank { userMeaning.trim() }
        val head = dict?.word?.takeIf { it.isNotBlank() } ?: word
        val pos = parsed.pos.ifBlank { dict?.pos ?: "" }
        return DictEntry(
            word = head,
            pos = pos,
            zh = gloss,
            en = dict?.en ?: "",
            meaning = userMeaning.trim()
        )
    }
}
