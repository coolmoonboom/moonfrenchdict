package com.coolmoonfrench.dict

/**
 * 配合页的词形数据与生成逻辑：查询任何法语词（代词/形容词/名词等），
 * 返回该词的阴阳单复（性数配合）全形态，每个形态尽量带本地例句。
 *
 * 数据分两部分：
 *  - [curated]：手工整理的常见/不规则词形范式（代词、限定词、不规则形容词与名词），
 *    例句为内联法语短句 + 中文翻译；
 *  - 生成逻辑：规则名词（按词典词性定阴阳 + 复数规则）、规则形容词（阴性/复数规则）
 *    在运行时补齐，例句尝试从 [VocabExamples]（本地 Tatoeba 法汉句对）取。
 */
data class AgreementForm(
    val form: String,
    val label: String,
    val note: String = "",
    val example: String = "",
    val exampleZh: String = ""
)

data class AgreementParadigm(
    val key: String,
    val category: String,
    val description: String,
    val forms: List<AgreementForm>,
    val aliases: List<String> = emptyList()
) {
    val isCurated: Boolean get() = forms.isNotEmpty() && forms.first().example.isNotEmpty()
}

object AgreementData {

    private val ACCENTS = mapOf(
        'à' to 'a', 'â' to 'a', 'ä' to 'a', 'æ' to 'a',
        'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
        'î' to 'i', 'ï' to 'i',
        'ô' to 'o', 'ö' to 'o', 'œ' to 'o',
        'ù' to 'u', 'û' to 'u', 'ü' to 'u',
        'ÿ' to 'y', 'ç' to 'c'
    )

    /** 归一化：小写、去重音、折叠空白，用于匹配输入与词形。 */
    fun normalize(s: String): String =
        s.lowercase().trim().replace(Regex("\\s+"), " ")
            .map { ACCENTS[it] ?: it }.joinToString("")

    // ---------- 手工整理范式 ----------

    val curated: List<AgreementParadigm> = listOf(
        // —— 人称主语代词 ——
        AgreementParadigm(
            key = "il",
            category = "人称主语代词",
            description = "第三人称及通性人称主语代词按阴阳单复区分。",
            aliases = listOf("je", "j", "tu", "elle", "on", "nous", "vous", "ils", "elles"),
            forms = listOf(
                AgreementForm("je", "通性单", "", "Je suis étudiant.", "我是学生。"),
                AgreementForm("tu", "通性单", "", "Tu es mon ami.", "你是我的朋友。"),
                AgreementForm("il", "阳单", "", "Il est intelligent.", "他很聪明。"),
                AgreementForm("elle", "阴单", "", "Elle est gentille.", "她很和善。"),
                AgreementForm("on", "通性单", "泛指", "On ne sait jamais.", "凡事都难预料。"),
                AgreementForm("nous", "通性单复", "", "Nous partons demain.", "我们明天出发。"),
                AgreementForm("vous", "通性单复", "您/你们", "Vous êtes les bienvenus.", "欢迎你们。"),
                AgreementForm("ils", "阳复", "", "Ils sont arrivés.", "他们到了。"),
                AgreementForm("elles", "阴复", "", "Elles sont contentes.", "她们很高兴。")
            )
        ),
        // —— 重读代词 ——
        AgreementParadigm(
            key = "lui",
            category = "重读/强式代词",
            description = "介词后或独立使用的重读代词按阴阳单复区分。",
            aliases = listOf("moi", "toi", "elle", "eux", "elles", "nous", "vous"),
            forms = listOf(
                AgreementForm("moi", "通性单", "", "C'est moi.", "是我。"),
                AgreementForm("toi", "通性单", "", "Je pense à toi.", "我想你。"),
                AgreementForm("lui", "阳单", "", "Je pense à lui.", "我想他。"),
                AgreementForm("elle", "阴单", "", "Je pense à elle.", "我想她。"),
                AgreementForm("nous", "通性单复", "", "C'est nous.", "是我们。"),
                AgreementForm("vous", "通性单复", "", "Merci à vous.", "谢谢你们。"),
                AgreementForm("eux", "阳复", "", "Je pense à eux.", "我想他们。"),
                AgreementForm("elles", "阴复", "", "Je pense à elles.", "我想她们。")
            )
        ),
        // —— 指示代词 ——
        AgreementParadigm(
            key = "celui",
            category = "指示代词",
            description = "中性/指示代词按阴阳单复区分；celui-ci/là 表示远近。",
            aliases = listOf("celle", "ceux", "celles", "celui-ci", "celui-là"),
            forms = listOf(
                AgreementForm("celui", "阳单", "", "Celui-ci est au chocolat.", "这个是巧克力味的。"),
                AgreementForm("celle", "阴单", "", "Celle que je préfère est rouge.", "我更喜欢的是那条红的。"),
                AgreementForm("ceux", "阳复", "", "Ceux qui travaillent réussiront.", "努力工作的人会成功。"),
                AgreementForm("celles", "阴复", "", "Celles de la table sont à moi.", "桌上的那些是我的。")
            )
        ),
        // —— 疑问/关系代词 lequel ——
        AgreementParadigm(
            key = "lequel",
            category = "疑问/关系代词",
            description = "选择性疑问代词与介词后关系代词按阴阳单复区分。",
            aliases = listOf("laquelle", "lesquels", "lesquelles"),
            forms = listOf(
                AgreementForm("lequel", "阳单", "", "Lequel de ces films as-tu vu ?", "这些电影你看了哪一部？"),
                AgreementForm("laquelle", "阴单", "", "Laquelle de ces voitures est à toi ?", "这些车中哪一辆是你的？"),
                AgreementForm("lesquels", "阳复", "", "Lesquels veux-tu garder ?", "你想留哪几个？"),
                AgreementForm("lesquelles", "阴复", "", "Lesquelles sont les plus chères ?", "哪些是最贵的？")
            )
        ),
        AgreementParadigm(
            key = "auquel",
            category = "疑问/关系代词",
            description = "à + lequel 的缩合形式按阴阳单复区分。",
            aliases = listOf("à laquelle", "auxquels", "auxquelles"),
            forms = listOf(
                AgreementForm("auquel", "阳单", "à + lequel", "Le problème auquel je pense est difficile.", "我想到的那个问题很难。"),
                AgreementForm("à laquelle", "阴单", "à + laquelle", "La réunion à laquelle j'ai assisté était longue.", "我参加的那个会议很长。"),
                AgreementForm("auxquels", "阳复", "à + lesquels", "Les amis auxquels j'écris habitent à Paris.", "我写信的那些朋友住在巴黎。"),
                AgreementForm("auxquelles", "阴复", "à + lesquelles", "Les questions auxquelles tu réponds sont simples.", "你回答的那些问题很简单。")
            )
        ),
        AgreementParadigm(
            key = "duquel",
            category = "疑问/关系代词",
            description = "de + lequel 的缩合形式按阴阳单复区分。",
            aliases = listOf("de laquelle", "desquels", "desquelles"),
            forms = listOf(
                AgreementForm("duquel", "阳单", "de + lequel", "Le pays duquel il vient est loin.", "他来自的那个国家很远。"),
                AgreementForm("de laquelle", "阴单", "de + laquelle", "La ville de laquelle elle parle est belle.", "她谈到的那座城市很漂亮。"),
                AgreementForm("desquels", "阳复", "de + lesquels", "Les livres desquels je te parle sont neufs.", "我跟你说起的那几本书是新的。"),
                AgreementForm("desquelles", "阴复", "de + lesquelles", "Les raisons desquelles il est parti sont connues.", "他离开的那些原因已经众所周知。")
            )
        ),
        // —— 泛指代词/形容词 ——
        AgreementParadigm(
            key = "chacun",
            category = "泛指代词",
            description = "泛指代词按阴阳单复区分。",
            aliases = listOf("chacune"),
            forms = listOf(
                AgreementForm("chacun", "阳单", "", "Chacun a son opinion.", "每个人都有各自的看法。"),
                AgreementForm("chacune", "阴单", "", "Chacune des fleurs est belle.", "每一朵花都很美。")
            )
        ),
        AgreementParadigm(
            key = "tout",
            category = "泛指代词/形容词",
            description = "tout 的阴阳单复区分意义：全部、每一个。",
            aliases = listOf("toute", "tous", "toutes"),
            forms = listOf(
                AgreementForm("tout", "阳单", "", "Tout va bien.", "一切顺利。"),
                AgreementForm("toute", "阴单", "", "Toute la ville le sait.", "全城都知道这件事。"),
                AgreementForm("tous", "阳复", "", "Tous les élèves sont là.", "所有学生都在场。"),
                AgreementForm("toutes", "阴复", "", "Toutes les fleurs sont fanées.", "所有的花都谢了。")
            )
        ),
        AgreementParadigm(
            key = "quelqu'un",
            category = "泛指代词",
            description = "某人/某位，按阴阳区分。",
            aliases = listOf("quelqu'une"),
            forms = listOf(
                AgreementForm("quelqu'un", "阳单", "", "Quelqu'un t'a appelé.", "有人给你打过电话。"),
                AgreementForm("quelqu'une", "阴单", "", "Quelqu'une de mes amies est médecin.", "我的某位女性朋友是医生。")
            )
        ),
        AgreementParadigm(
            key = "certains",
            category = "泛指代词/形容词",
            description = "某些，按阴阳复区分。",
            aliases = listOf("certain", "certaine", "certaines"),
            forms = listOf(
                AgreementForm("certain", "阳单", "", "Un certain monsieur vous attend.", "有位先生等你。"),
                AgreementForm("certaine", "阴单", "", "Une certaine idée me vient.", "我有了某个想法。"),
                AgreementForm("certains", "阳复", "", "Certains aiment le café.", "有些人喜欢咖啡。"),
                AgreementForm("certaines", "阴复", "", "Certaines personnes pensent autrement.", "有些人想法不同。")
            )
        ),
        AgreementParadigm(
            key = "autre",
            category = "泛指代词/形容词",
            description = "另一个/另一些，单复数区分。",
            aliases = listOf("autres"),
            forms = listOf(
                AgreementForm("autre", "通性单", "", "Donne-moi un autre exemple.", "再给我一个例子。"),
                AgreementForm("autres", "通性复", "", "Les autres sont déjà partis.", "其他人已经走了。")
            )
        ),
        AgreementParadigm(
            key = "même",
            category = "泛指代词/形容词",
            description = "同样的/甚至，单复数区分。",
            aliases = listOf("mêmes"),
            forms = listOf(
                AgreementForm("même", "通性单", "", "C'est le même livre.", "是同一本书。"),
                AgreementForm("mêmes", "通性复", "", "Ils ont les mêmes goûts.", "他们有相同的爱好。")
            )
        ),
        // —— 中性/副代词 ——
        AgreementParadigm(
            key = "cela",
            category = "中性代词",
            description = "中性代词无阴阳单复变化。",
            aliases = listOf("ça", "ce", "c'", "cela", "ceci"),
            forms = listOf(
                AgreementForm("cela", "中性", "", "Cela ne me plaît pas.", "这个我不喜欢。"),
                AgreementForm("ça", "中性", "口语", "Ça va ?", "你好吗？"),
                AgreementForm("ce", "中性", "", "C'est vrai.", "这是真的。")
            )
        ),
        AgreementParadigm(
            key = "en",
            category = "副代词",
            description = "副代词 en 无性数变化。",
            forms = listOf(
                AgreementForm("en", "不变", "de + 名词", "J'en ai deux.", "我有两个。")
            )
        ),
        AgreementParadigm(
            key = "y",
            category = "副代词",
            description = "副代词 y 无性数变化。",
            forms = listOf(
                AgreementForm("y", "不变", "à/dans/sur + 地点", "J'y vais demain.", "我明天去那里。")
            )
        ),
        // —— 主有代词 ——
        AgreementParadigm(
            key = "le mien",
            category = "主有代词",
            description = "第一人称主有代词按阴阳单复区分。",
            aliases = listOf("la mienne", "les miens", "les miennes", "mien", "mienne", "miens", "miennes"),
            forms = listOf(
                AgreementForm("le mien", "阳单", "", "Ce stylo est le mien.", "这支笔是我的。"),
                AgreementForm("la mienne", "阴单", "", "Cette idée est la mienne.", "这个主意是我的。"),
                AgreementForm("les miens", "阳复", "", "Tes livres sont neufs, les miens sont usés.", "你的书是新的，我的旧了。"),
                AgreementForm("les miennes", "阴复", "", "Ses chaussures et les miennes sont identiques.", "她的鞋和我的是一模一样的。")
            )
        ),
        AgreementParadigm(
            key = "le tien",
            category = "主有代词",
            description = "第二人称主有代词按阴阳单复区分。",
            aliases = listOf("la tienne", "les tiens", "les tiennes", "tien", "tienne", "tiens", "tiennes"),
            forms = listOf(
                AgreementForm("le tien", "阳单", "", "Mon sac est noir, le tien est rouge.", "我的包是黑色的，你的是红色的。"),
                AgreementForm("la tienne", "阴单", "", "La mienne est petite, la tienne est grande.", "我的（房间）小，你的大。"),
                AgreementForm("les tiens", "阳复", "", "Les tiens sont meilleurs que les miens.", "你的（那些）比我的好。"),
                AgreementForm("les tiennes", "阴复", "", "J'ai lu les tiennes, elles sont drôles.", "我读过你的那些，很有趣。")
            )
        ),
        AgreementParadigm(
            key = "le sien",
            category = "主有代词",
            description = "第三人称主有代词按阴阳单复区分。",
            aliases = listOf("la sienne", "les siens", "les siennes", "sien", "sienne", "siens", "siennes"),
            forms = listOf(
                AgreementForm("le sien", "阳单", "", "Je connais ton avis et le sien.", "我知道你的意见和他的。"),
                AgreementForm("la sienne", "阴单", "", "Sa réponse est claire, la sienne aussi.", "他的回答很清楚，她的也是。"),
                AgreementForm("les siens", "阳复", "", "Il a retrouvé les siens.", "他重新找到了他的家人。"),
                AgreementForm("les siennes", "阴复", "", "Elle a pris les siennes.", "她拿走了她的那些。")
            )
        ),
        AgreementParadigm(
            key = "le nôtre",
            category = "主有代词",
            description = "我们的，按阴阳单复区分。",
            aliases = listOf("la nôtre", "les nôtres", "nôtre", "nôtres"),
            forms = listOf(
                AgreementForm("le nôtre", "阳单", "", "Ce vélo est le nôtre.", "这辆自行车是我们的。"),
                AgreementForm("la nôtre", "阴单", "", "Cette école est la nôtre.", "这所学校是我们的。"),
                AgreementForm("les nôtres", "通性复", "", "Ces amis sont les nôtres.", "这些朋友是我们的。")
            )
        ),
        AgreementParadigm(
            key = "le vôtre",
            category = "主有代词",
            description = "你们的/您的，按阴阳单复区分。",
            aliases = listOf("la vôtre", "les vôtres", "vôtre", "vôtres"),
            forms = listOf(
                AgreementForm("le vôtre", "阳单", "", "Ce parapluie est le vôtre ?", "这把伞是您的吗？"),
                AgreementForm("la vôtre", "阴单", "", "Cette valise est la vôtre.", "这个行李箱是您的。"),
                AgreementForm("les vôtres", "通性复", "", "Nos voisins et les vôtres se connaissent.", "我们的邻居和你们的彼此认识。")
            )
        ),
        AgreementParadigm(
            key = "le leur",
            category = "主有代词",
            description = "他们的/她们的，按阴阳单复区分。",
            aliases = listOf("la leur", "les leurs", "leur", "leurs"),
            forms = listOf(
                AgreementForm("le leur", "阳单", "", "Ce chien est le leur.", "这只狗是他们的。"),
                AgreementForm("la leur", "阴单", "", "Cette maison est la leur.", "这座房子是他们的。"),
                AgreementForm("les leurs", "通性复", "", "Les leurs sont arrivés en premier.", "他们的人先到了。")
            )
        ),
        // —— 主有形容词 ——
        AgreementParadigm(
            key = "mon",
            category = "主有形容词",
            description = "第一人称主有形容词；元音/哑音 h 前用 mon（省音）。",
            aliases = listOf("ma", "mes"),
            forms = listOf(
                AgreementForm("mon", "阳单/元音前", "", "mon père, mon amie", "我的父亲 / 我的朋友（女）。"),
                AgreementForm("ma", "阴单", "", "ma mère", "我的母亲。"),
                AgreementForm("mes", "通性复", "", "mes parents", "我的父母。")
            )
        ),
        AgreementParadigm(
            key = "ton",
            category = "主有形容词",
            description = "第二人称主有形容词；元音/哑音 h 前用 ton。",
            aliases = listOf("ta", "tes"),
            forms = listOf(
                AgreementForm("ton", "阳单/元音前", "", "ton frère, ton amie", "你的兄弟 / 你的女性朋友。"),
                AgreementForm("ta", "阴单", "", "ta sœur", "你的姐妹。"),
                AgreementForm("tes", "通性复", "", "tes cousins", "你的表兄弟们。")
            )
        ),
        AgreementParadigm(
            key = "son",
            category = "主有形容词",
            description = "第三人称主有形容词；元音/哑音 h 前用 son。",
            aliases = listOf("sa", "ses"),
            forms = listOf(
                AgreementForm("son", "阳单/元音前", "", "son fils, son école", "他的儿子 / 他的学校。"),
                AgreementForm("sa", "阴单", "", "sa fille", "她的女儿。"),
                AgreementForm("ses", "通性复", "", "ses enfants", "他的孩子们。")
            )
        ),
        AgreementParadigm(
            key = "notre",
            category = "主有形容词",
            description = "我们的，单数不区分阴阳。",
            aliases = listOf("nos"),
            forms = listOf(
                AgreementForm("notre", "通性单", "", "notre maison", "我们的房子。"),
                AgreementForm("nos", "通性复", "", "nos amis", "我们的朋友们。")
            )
        ),
        AgreementParadigm(
            key = "votre",
            category = "主有形容词",
            description = "你们的/您的，单数不区分阴阳。",
            aliases = listOf("vos"),
            forms = listOf(
                AgreementForm("votre", "通性单", "", "votre avis", "您的意见。"),
                AgreementForm("vos", "通性复", "", "vos idées", "你们的想法。")
            )
        ),
        AgreementParadigm(
            key = "leur",
            category = "主有形容词/宾语代词",
            description = "leur 作主有形容词（他们的，复数为 leurs）；作间接宾语代词时不变。",
            aliases = listOf("leurs"),
            forms = listOf(
                AgreementForm("leur", "通性单", "主有", "Leur maison est belle.", "他们的房子很漂亮。"),
                AgreementForm("leurs", "通性复", "主有", "Leurs enfants sont sages.", "他们的孩子很乖。"),
                AgreementForm("leur", "不变", "COI 宾语", "Je leur parle.", "我跟他们说话。")
            )
        ),
        // —— 指示形容词 ——
        AgreementParadigm(
            key = "ce",
            category = "指示形容词",
            description = "这个/那个，按阴阳单复区分；元音前用 cet。",
            aliases = listOf("cet", "cette", "ces"),
            forms = listOf(
                AgreementForm("ce", "阳单", "辅音前", "ce livre", "这本书。"),
                AgreementForm("cet", "阳单", "元音/h 前", "cet ami", "这位朋友。"),
                AgreementForm("cette", "阴单", "", "cette maison", "这座房子。"),
                AgreementForm("ces", "通性复", "", "ces enfants", "这些孩子。")
            )
        ),
        // —— 疑问形容词 ——
        AgreementParadigm(
            key = "quel",
            category = "疑问/感叹形容词",
            description = "哪个/多么，按阴阳单复区分。",
            aliases = listOf("quelle", "quels", "quelles"),
            forms = listOf(
                AgreementForm("quel", "阳单", "", "Quel âge as-tu ?", "你多大了？"),
                AgreementForm("quelle", "阴单", "", "Quelle heure est-il ?", "几点了？"),
                AgreementForm("quels", "阳复", "", "Quels livres veux-tu ?", "你想要哪些书？"),
                AgreementForm("quelles", "阴复", "", "Quelles couleurs aimes-tu ?", "你喜欢哪些颜色？")
            )
        ),
        // —— 定冠词 ——
        AgreementParadigm(
            key = "le",
            category = "定冠词",
            description = "定冠词按阴阳单复区分；元音/哑音 h 前省音为 l'。",
            aliases = listOf("la", "les", "l'", "l"),
            forms = listOf(
                AgreementForm("le", "阳单", "", "Le chat dort.", "猫在睡觉。"),
                AgreementForm("la", "阴单", "", "La table est grande.", "桌子很大。"),
                AgreementForm("l'", "省音", "元音/h 前", "l'école", "学校。"),
                AgreementForm("les", "通性复", "", "Les enfants jouent.", "孩子们在玩。")
            )
        ),
        // —— 不定冠词 ——
        AgreementParadigm(
            key = "un",
            category = "不定冠词",
            description = "不定冠词按阴阳单复区分；阴性 une，复数 des。",
            aliases = listOf("une", "des"),
            forms = listOf(
                AgreementForm("un", "阳单", "", "un garçon", "一个男孩。"),
                AgreementForm("une", "阴单", "", "une fille", "一个女孩。"),
                AgreementForm("des", "通性复", "", "des livres", "一些书。")
            )
        ),
        // —— 不规则形容词 ——
        AgreementParadigm(
            key = "beau",
            category = "形容词（不规则）",
            description = "美丽的；元音/哑音 h 前用 bel。",
            aliases = listOf("bel", "belle", "beaux", "belles"),
            forms = listOf(
                AgreementForm("beau", "阳单", "辅音前", "Un beau garçon.", "一个英俊的男孩。"),
                AgreementForm("bel", "阳单", "元音/h 前", "Un bel arbre.", "一棵漂亮的树。"),
                AgreementForm("belle", "阴单", "", "Une belle femme.", "一位美丽的女士。"),
                AgreementForm("beaux", "阳复", "", "De beaux jours nous attendent.", "美好的日子在等着我们。"),
                AgreementForm("belles", "阴复", "", "De belles fleurs.", "美丽的花。")
            )
        ),
        AgreementParadigm(
            key = "nouveau",
            category = "形容词（不规则）",
            description = "新的；元音/哑音 h 前用 nouvel。",
            aliases = listOf("nouvel", "nouvelle", "nouveaux", "nouvelles"),
            forms = listOf(
                AgreementForm("nouveau", "阳单", "辅音前", "Un nouveau téléphone.", "一部新手机。"),
                AgreementForm("nouvel", "阳单", "元音/h 前", "Le nouvel an.", "新年。"),
                AgreementForm("nouvelle", "阴单", "", "Une nouvelle idée.", "一个新想法。"),
                AgreementForm("nouveaux", "阳复", "", "De nouveaux voisins.", "新邻居们。"),
                AgreementForm("nouvelles", "阴复", "", "De nouvelles règles.", "新规则。")
            )
        ),
        AgreementParadigm(
            key = "vieux",
            category = "形容词（不规则）",
            description = "旧的/年老的；阳单元音前用 vieil，阳复仍为 vieux。",
            aliases = listOf("vieil", "vieille", "vieilles"),
            forms = listOf(
                AgreementForm("vieux", "阳单", "辅音前", "Un vieux monsieur.", "一位老先生。"),
                AgreementForm("vieil", "阳单", "元音/h 前", "Un vieil ami.", "一位老朋友。"),
                AgreementForm("vieille", "阴单", "", "Une vieille maison.", "一座老房子。"),
                AgreementForm("vieux", "阳复", "", "De vieux livres.", "一些旧书。"),
                AgreementForm("vieilles", "阴复", "", "De vieilles chansons.", "一些老歌。")
            )
        ),
        AgreementParadigm(
            key = "bon",
            category = "形容词（不规则）",
            description = "好的；规则变阴性/复数。",
            aliases = listOf("bonne", "bons", "bonnes"),
            forms = listOf(
                AgreementForm("bon", "阳单", "", "Un bon repas.", "一顿好饭。"),
                AgreementForm("bonne", "阴单", "", "Une bonne nouvelle.", "一个好消息。"),
                AgreementForm("bons", "阳复", "", "De bons amis.", "好朋友。"),
                AgreementForm("bonnes", "阴复", "", "De bonnes idées.", "好主意。")
            )
        ),
        AgreementParadigm(
            key = "petit",
            category = "形容词（规则）",
            description = "小的；规则变阴性/复数。",
            aliases = listOf("petite", "petits", "petites"),
            forms = listOf(
                AgreementForm("petit", "阳单", "", "Un petit chien.", "一只小狗。"),
                AgreementForm("petite", "阴单", "", "Une petite fille.", "一个小女孩。"),
                AgreementForm("petits", "阳复", "", "De petits gâteaux.", "一些小蛋糕。"),
                AgreementForm("petites", "阴复", "", "De petites maisons.", "一些小房子。")
            )
        ),
        AgreementParadigm(
            key = "gros",
            category = "形容词（不规则）",
            description = "大的/胖的；阴性 grosse，阳复仍为 gros。",
            aliases = listOf("grosse", "grosses"),
            forms = listOf(
                AgreementForm("gros", "阳单", "", "Un gros problème.", "一个大问题。"),
                AgreementForm("grosse", "阴单", "", "Une grosse pierre.", "一块大石头。"),
                AgreementForm("gros", "阳复", "", "De gros efforts.", "巨大的努力。"),
                AgreementForm("grosses", "阴复", "", "De grosses dépenses.", "大笔的开支。")
            )
        ),
        AgreementParadigm(
            key = "blanc",
            category = "形容词（不规则）",
            description = "白色的；阴性 blanche。",
            aliases = listOf("blanche", "blancs", "blanches"),
            forms = listOf(
                AgreementForm("blanc", "阳单", "", "Un chat blanc.", "一只白猫。"),
                AgreementForm("blanche", "阴单", "", "Une robe blanche.", "一条白色连衣裙。"),
                AgreementForm("blancs", "阳复", "", "Des murs blancs.", "白色的墙。"),
                AgreementForm("blanches", "阴复", "", "Des chemises blanches.", "白色的衬衫。")
            )
        ),
        AgreementParadigm(
            key = "gentil",
            category = "形容词（不规则）",
            description = "友善的；阴性 gentille。",
            aliases = listOf("gentille", "gentils", "gentilles"),
            forms = listOf(
                AgreementForm("gentil", "阳单", "", "Un garçon gentil.", "一个友善的男孩。"),
                AgreementForm("gentille", "阴单", "", "Une fille gentille.", "一个友善的女孩。"),
                AgreementForm("gentils", "阳复", "", "Des voisins gentils.", "友善的邻居们。"),
                AgreementForm("gentilles", "阴复", "", "Des personnes gentilles.", "友善的人们。")
            )
        ),
        AgreementParadigm(
            key = "long",
            category = "形容词（不规则）",
            description = "长的；阴性 longue。",
            aliases = listOf("longue", "longs", "longues"),
            forms = listOf(
                AgreementForm("long", "阳单", "", "Un long voyage.", "一趟长途旅行。"),
                AgreementForm("longue", "阴单", "", "Une longue lettre.", "一封长信。"),
                AgreementForm("longs", "阳复", "", "Des cheveux longs.", "长发。"),
                AgreementForm("longues", "阴复", "", "De longues heures.", "漫长的时间。")
            )
        ),
        AgreementParadigm(
            key = "doux",
            category = "形容词（不规则）",
            description = "软的/甜的/温和的；阴性 douce。",
            aliases = listOf("douce", "douces"),
            forms = listOf(
                AgreementForm("doux", "阳单", "", "Un vent doux.", "一阵温和的风。"),
                AgreementForm("douce", "阴单", "", "Une musique douce.", "轻柔的音乐。"),
                AgreementForm("doux", "阳复", "", "Des mots doux.", "温柔的话语。"),
                AgreementForm("douces", "阴复", "", "Des couleurs douces.", "柔和的颜色。")
            )
        ),
        AgreementParadigm(
            key = "faux",
            category = "形容词（不规则）",
            description = "假的/错误的；阴性 fausse。",
            aliases = listOf("fausse", "faux", "fausses"),
            forms = listOf(
                AgreementForm("faux", "阳单", "", "Un faux document.", "一份假文件。"),
                AgreementForm("fausse", "阴单", "", "Une fausse alerte.", "一次假警报。"),
                AgreementForm("faux", "阳复", "", "Des faux billets.", "假钞。"),
                AgreementForm("fausses", "阴复", "", "Des fausses nouvelles.", "假新闻。")
            )
        ),
        AgreementParadigm(
            key = "heureux",
            category = "形容词（规则）",
            description = "幸福的/幸运的；阴性 heureuse。",
            aliases = listOf("heureuse", "heureux", "heureuses"),
            forms = listOf(
                AgreementForm("heureux", "阳单", "", "Il est heureux.", "他很幸福。"),
                AgreementForm("heureuse", "阴单", "", "Elle est heureuse.", "她很幸福。"),
                AgreementForm("heureux", "阳复", "", "Ils sont heureux.", "他们很幸福。"),
                AgreementForm("heureuses", "阴复", "", "Elles sont heureuses.", "她们很幸福。")
            )
        ),
        AgreementParadigm(
            key = "cher",
            category = "形容词（规则）",
            description = "亲爱的/昂贵的；阴性 chère。",
            aliases = listOf("chère", "chers", "chères"),
            forms = listOf(
                AgreementForm("cher", "阳单", "", "Mon cher ami.", "我亲爱的朋友。"),
                AgreementForm("chère", "阴单", "", "Ma chère amie.", "我亲爱的朋友（女）。"),
                AgreementForm("chers", "阳复", "", "Mes chers collègues.", "我亲爱的同事们。"),
                AgreementForm("chères", "阴复", "", "Mes chères étudiantes.", "我亲爱的女学生们。")
            )
        ),
        AgreementParadigm(
            key = "premier",
            category = "形容词（不规则）",
            description = "第一的；阴性 première。",
            aliases = listOf("première", "premiers", "premières"),
            forms = listOf(
                AgreementForm("premier", "阳单", "", "Le premier étage.", "第一层。"),
                AgreementForm("première", "阴单", "", "La première fois.", "第一次。"),
                AgreementForm("premiers", "阳复", "", "Les premiers jours.", "最初的几天。"),
                AgreementForm("premières", "阴复", "", "Les premières années.", "最初的几年。")
            )
        ),
        // —— 不规则复数名词 ——
        AgreementParadigm(
            key = "cheval",
            category = "名词（不规则复数）",
            description = "阳性名词；复数 chevaux。",
            aliases = listOf("chevaux"),
            forms = listOf(
                AgreementForm("le cheval", "阳单", "", "Je monte à cheval.", "我骑马。"),
                AgreementForm("les chevaux", "阳复", "", "Les chevaux courent vite.", "马跑得快。")
            )
        ),
        AgreementParadigm(
            key = "œil",
            category = "名词（不规则复数）",
            description = "阳性名词；复数 yeux。",
            aliases = listOf("oeil", "yeux"),
            forms = listOf(
                AgreementForm("l'œil", "阳单", "", "Il a un œil bleu.", "他有一只蓝眼睛。"),
                AgreementForm("les yeux", "阳复", "", "Elle a les yeux verts.", "她有一双绿眼睛。")
            )
        ),
        AgreementParadigm(
            key = "travail",
            category = "名词（不规则复数）",
            description = "阳性名词；复数 travaux。",
            aliases = listOf("travaux"),
            forms = listOf(
                AgreementForm("le travail", "阳单", "", "Le travail est fini.", "工作完成了。"),
                AgreementForm("les travaux", "阳复", "", "Les travaux commencent lundi.", "工程周一开始。")
            )
        ),
        AgreementParadigm(
            key = "ciel",
            category = "名词（不规则复数）",
            description = "阳性名词；复数 cieux（文学/宗教色彩）。",
            aliases = listOf("cieux"),
            forms = listOf(
                AgreementForm("le ciel", "阳单", "", "Le ciel est bleu.", "天空是蓝的。"),
                AgreementForm("les cieux", "阳复", "文学", "Les cieux étoilés.", "繁星点点的天空。")
            )
        ),
        AgreementParadigm(
            key = "animal",
            category = "名词（不规则复数）",
            description = "阳性名词；复数 animaux。",
            aliases = listOf("animaux"),
            forms = listOf(
                AgreementForm("l'animal", "阳单", "", "L'animal dort.", "动物在睡觉。"),
                AgreementForm("les animaux", "阳复", "", "Les animaux sont sauvages.", "这些动物是野生的。")
            )
        ),
        AgreementParadigm(
            key = "bijou",
            category = "名词（不规则复数）",
            description = "阳性名词；复数 bijoux。",
            aliases = listOf("bijoux"),
            forms = listOf(
                AgreementForm("le bijou", "阳单", "", "Ce bijou est précieux.", "这件珠宝很贵重。"),
                AgreementForm("les bijoux", "阳复", "", "Elle adore les bijoux.", "她喜欢珠宝。")
            )
        ),
        AgreementParadigm(
            key = "genou",
            category = "名词（不规则复数）",
            description = "阳性名词；复数 genoux。",
            aliases = listOf("genoux"),
            forms = listOf(
                AgreementForm("le genou", "阳单", "", "Il a mal au genou.", "他膝盖疼。"),
                AgreementForm("les genoux", "阳复", "", "Elle tombe à genoux.", "她跪了下来。")
            )
        ),
        AgreementParadigm(
            key = "monsieur",
            category = "名词（不规则复数）",
            description = "阳性名词；复数 messieurs。",
            aliases = listOf("messieurs"),
            forms = listOf(
                AgreementForm("monsieur", "阳单", "", "Monsieur Dupont est là.", "杜邦先生来了。"),
                AgreementForm("messieurs", "阳复", "", "Messieurs, bienvenue !", "先生们，欢迎！")
            )
        ),
        AgreementParadigm(
            key = "madame",
            category = "名词（不规则复数）",
            description = "阴性名词；复数 mesdames。",
            aliases = listOf("mesdames"),
            forms = listOf(
                AgreementForm("madame", "阴单", "", "Madame Martin est absente.", "马丁夫人不在。"),
                AgreementForm("mesdames", "阴复", "", "Mesdames et messieurs !", "女士们，先生们！")
            )
        )
    )

    // 常见名词性别表：仅用于词典未标性别时兜底（阳/阴）。
    private val knownNounGender = mapOf(
        "chat" to 'm', "chien" to 'm', "ami" to 'm', "père" to 'm', "frère" to 'm',
        "livre" to 'm', "garçon" to 'm', "homme" to 'm', "arbre" to 'm', "vent" to 'm',
        "jour" to 'm', "temps" to 'm', "café" to 'm', "vin" to 'm', "pain" to 'm',
        "train" to 'm', "avion" to 'm', "soleil" to 'm', "monde" to 'm', "cœur" to 'm',
        "table" to 'f', "maison" to 'f', "femme" to 'f', "mère" to 'f', "sœur" to 'f',
        "fille" to 'f', "fleur" to 'f', "voiture" to 'f', "rue" to 'f', "ville" to 'f',
        "école" to 'f', "chaise" to 'f', "porte" to 'f', "fenêtre" to 'f', "lune" to 'f',
        "nuit" to 'f', "mer" to 'f', "terre" to 'f', "eau" to 'f', "heure" to 'f'
    )

    /** 名词阴性/复数不规则表。 */
    private val feminineIrregular = mapOf(
        "beau" to "belle", "nouveau" to "nouvelle", "vieux" to "vieille", "vieil" to "vieille",
        "bon" to "bonne", "blanc" to "blanche", "gros" to "grosse", "doux" to "douce",
        "faux" to "fausse", "long" to "longue", "gentil" to "gentille", "sec" to "sèche",
        "frais" to "fraîche", "mou" to "molle", "nul" to "nulle", "favori" to "favorite",
        "malin" to "maligne", "public" to "publique", "grec" to "grecque", "turc" to "turque",
        "tiers" to "tierce", "traitre" to "traîtresse", "épais" to "épaisse", "gras" to "grasse",
        "bas" to "basse", "exprès" to "expresse", "jumeau" to "jumelle", "paysan" to "paysanne"
    )

    private val pluralIrregular = mapOf(
        "cheval" to "chevaux", "œil" to "yeux", "oeil" to "yeux", "travail" to "travaux",
        "ciel" to "cieux", "animal" to "animaux", "journal" to "journaux", "bijou" to "bijoux",
        "caillou" to "cailloux", "chou" to "choux", "genou" to "genoux", "hibou" to "hiboux",
        "joujou" to "joujoux", "pou" to "poux", "corail" to "coraux", "émail" to "émaux",
        "vitrail" to "vitraux", "bail" to "baux", "soupirail" to "soupiraux",
        "madame" to "mesdames", "monsieur" to "messieurs", "mademoiselle" to "mesdemoiselles",
        "bal" to "bals", "carnaval" to "carnavals", "festival" to "festivals",
        "récital" to "récitals", "chacal" to "chacals", "travail" to "travaux"
    )

    /** 建索引：归一化词形/别名 → 范式。 */
    private val curatedIndex: Map<String, AgreementParadigm> by lazy {
        buildMap {
            for (p in curated) {
                val all = listOf(p.key) + p.aliases + p.forms.map { it.form }
                for (a in all) {
                    val n = normalize(a)
                    if (n.isNotEmpty()) put(n, p)
                }
            }
        }
    }

    /** 精确匹配手工范式（按任意词形/别名）。 */
    fun findCurated(input: String): AgreementParadigm? {
        val n = normalize(input)
        if (n.isEmpty()) return null
        return curatedIndex[n]
    }

    /** 名词复数规则（含不规则表）。 */
    fun pluralOf(word: String): String {
        val w = word.trim()
        if (w.isEmpty()) return w
        pluralIrregular[normalize(w)]?.let { return it }
        return when {
            w.endsWith("s") || w.endsWith("x") || w.endsWith("z") -> w
            w.endsWith("eau") || w.endsWith("au") || w.endsWith("eu") -> w + "x"
            w.endsWith("al") && w.length > 3 -> w.dropLast(2) + "aux"
            else -> w + "s"
        }
    }

    /** 形容词阴性规则（含不规则表）。 */
    fun feminineOf(word: String): String {
        val w = word.trim()
        if (w.isEmpty()) return w
        feminineIrregular[normalize(w)]?.let { return it }
        return when {
            w.endsWith("e") -> w
            w.endsWith("er") -> w.dropLast(2) + "ère"
            w.endsWith("ier") -> w.dropLast(3) + "ière"
            w.endsWith("eux") -> w.dropLast(3) + "euse"
            w.endsWith("f") -> w.dropLast(1) + "ve"
            w.endsWith("if") -> w.dropLast(2) + "ive"
            w.endsWith("el") -> w + "le"
            w.endsWith("en") -> w + "ne"
            w.endsWith("on") -> w + "ne"
            w.endsWith("ien") -> w + "ne"
            w.endsWith("et") && w.length > 3 -> w + "te"
            w.endsWith("il") && w.length > 3 -> w + "le"
            w.endsWith("c") && w.length > 3 -> w.dropLast(1) + "che"
            w.endsWith("g") && w.length > 3 -> w + "ue"
            w.endsWith("as") || w.endsWith("os") -> w + "se"
            else -> w + "e"
        }
    }

    private fun elidable(c: Char?): Boolean = c != null && c.lowercaseChar() in "aeiouyâàëéêèïîôùûüœh"

    /** 由词典词条生成名词范式（带冠词的单复数）。性别未知时只给复数。 */
    fun nounParadigm(word: String, entry: DictEntry?): AgreementParadigm? {
        val w = word.trim()
        if (w.isEmpty()) return null
        val g = genderOfEntry(entry) ?: knownNounGender[normalize(w)]
        val plural = pluralOf(w)
        val forms = mutableListOf<AgreementForm>()
        if (g != null) {
            val art = when {
                elidable(w.firstOrNull()) -> "l'"
                g == 'm' -> "le "
                else -> "la "
            }
            forms += AgreementForm(
                form = "$art$w",
                label = if (g == 'm') "阳单" else "阴单",
                note = if (elidable(w.firstOrNull())) "省音" else ""
            )
            forms += AgreementForm(form = "les $plural", label = "复")
        } else {
            forms += AgreementForm(
                form = "les $plural",
                label = "复",
                note = "词典未标注性别，无法给出单数定冠词"
            )
        }
        return AgreementParadigm(
            key = w,
            category = "名词",
            description = if (g != null) "词典标注：${if (g == 'm') "阳性" else "阴性"}名词。" else "词典未标注性别。",
            forms = forms
        )
    }

    /** 由词典词条生成形容词范式（规则阴性/复数）。 */
    fun adjectiveParadigm(word: String, entry: DictEntry?): AgreementParadigm? {
        val w = word.trim()
        if (w.isEmpty()) return null
        val fem = feminineOf(w)
        val mpl = pluralOf(w)
        val fpl = if (fem != w) pluralOf(fem) else mpl
        val forms = mutableListOf<AgreementForm>()
        forms += AgreementForm(w, "阳单")
        if (fem != w) {
            forms += AgreementForm(fem, "阴单")
        }
        forms += AgreementForm(if (mpl != w) mpl else w + "s", "阳复")
        if (fpl != mpl) forms += AgreementForm(fpl, "阴复")
        return AgreementParadigm(
            key = w,
            category = "形容词",
            description = "规则变化：阴性 ${if (fem != w) fem else "同阳单"}，复数加 s。",
            forms = forms
        )
    }

    /** 从词典词条（pos + zh）判断名词/形容词词性，返回 null 表示非名词非形容词。 */
    fun posKind(entry: DictEntry?): String? {
        val pos = entry?.pos?.trim()?.lowercase() ?: return null
        return when {
            pos.startsWith("n.") || pos.startsWith("noun") -> "noun"
            pos.startsWith("adj") || pos.startsWith("a.") -> "adj"
            pos.startsWith("v") || pos.startsWith("verb") -> "verb"
            else -> null
        }
    }

    /** 从 pos 与 zh 提取名词性别（m/f/null）。 */
    private fun genderOfEntry(entry: DictEntry?): Char? {
        val pos = entry?.pos?.trim()?.lowercase() ?: return null
        if (pos.startsWith("n.m")) return 'm'
        if (pos.startsWith("n.f")) return 'f'
        val zh = entry.zh
        val low = zh.lowercase()
        val f = Regex("n[.\\s]*f\\.|\\bf\\.\\b").containsMatchIn(low)
        val m = Regex("n[.\\s]*m\\.|\\bm\\.\\b").containsMatchIn(low)
        return when {
            f && !m -> 'f'
            m && !f -> 'm'
            else -> null
        }
    }
}
