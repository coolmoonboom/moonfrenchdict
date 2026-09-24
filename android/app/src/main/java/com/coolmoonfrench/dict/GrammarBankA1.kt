package com.coolmoonfrench.dict

/** A1 · 限定词：主有形容词与指示形容词 */
val a1Determinants: List<QuizQuestion> = listOf(
    QuizQuestion(
        "___ amie s'appelle Sophie.（我的女性朋友）",
        listOf("Ma", "Mon", "Mes", "Le"),
        "Mon",
        "主有形容词 ma 在元音开头的阴性名词前要用 mon：mon amie。"
    ),
    QuizQuestion(
        "___ maison est grande.（这栋房子）",
        listOf("Ce", "Cet", "Cette", "Ces"),
        "Cette",
        "maison 是阴性单数，指示形容词用 cette。"
    ),
    QuizQuestion(
        "___ hôtel est magnifique.（这家旅馆）",
        listOf("Ce", "Cet", "Cette", "Ces"),
        "Cet",
        "阳性单数名词以元音或哑音 h 开头时，ce 要用 cet：cet hôtel。"
    ),
    QuizQuestion(
        "Voici ___ livres que j'ai achetés.（这些书）",
        listOf("Ce", "Cet", "Cette", "Ces"),
        "Ces",
        "复数指示形容词一律用 ces。"
    ),
    QuizQuestion(
        "___ parents habitent à Lyon.（他们的父母）",
        listOf("Leur", "Leurs", "La", "Les"),
        "Leurs",
        "主有形容词复数形式用 leurs（修饰复数名词 parents）。"
    ),
    QuizQuestion(
        "Tu as fini ___ devoirs ?（你的作业）",
        listOf("ton", "ta", "tes", "le"),
        "tes",
        "devoirs 为复数，主有形容词用 tes。"
    ),
    QuizQuestion(
        "Madame, où est ___ passeport ?（您的护照）",
        listOf("ton", "ta", "votre", "vos"),
        "votre",
        "尊称 vous 对应的主有形容词是 votre（单数名词）。"
    ),
    QuizQuestion(
        "Je préfère ce film à ___ série.（那部电视剧）",
        listOf("ce", "cet", "cette", "ces"),
        "cette",
        "série 是阴性单数，用 cette。"
    )
)

/** A1 · 代词：人称主语与重读人称 */
val a1Pronouns: List<QuizQuestion> = listOf(
    QuizQuestion(
        "___ suis étudiant à Paris.",
        listOf("J'", "Je", "Tu", "Il"),
        "Je",
        "suis 以辅音开头，主语人称用 Je，不必省音。"
    ),
    QuizQuestion(
        "___ ai un chat.（我有一只猫）",
        listOf("Je", "J'", "Tu", "Il"),
        "J'",
        "je 在元音开头的动词前省音为 j'：j'ai un chat。"
    ),
    QuizQuestion(
        "Qui a cassé le vase ? — C'est ___ !（是我！）",
        listOf("je", "me", "moi", "my"),
        "moi",
        "强调或作表语时用重读人称代词 moi。"
    ),
    QuizQuestion(
        "Marie et moi, ___ allons au cinéma ce soir.",
        listOf("nous", "vous", "ils", "on"),
        "nous",
        "「玛丽和我」合起来是第一人称复数 nous。"
    ),
    QuizQuestion(
        "Toi et lui, ___ partez demain.",
        listOf("nous", "vous", "ils", "on"),
        "vous",
        "「你和他」是第二人称复数 vous（和动词 partez 一致）。"
    ),
    QuizQuestion(
        "Madame Dupont est médecin. C'est ___ qui soigne mon père.",
        listOf("il", "elle", "ce", "on"),
        "elle",
        "指代 Madame Dupont（女性），用 elle。"
    ),
    QuizQuestion(
        "En France, ___ mange avec un couteau et une fourchette.",
        listOf("il", "elle", "on", "nous"),
        "on",
        "泛指「人们」用泛指代词 on，动词用第三人称单数。"
    ),
    QuizQuestion(
        "Le livre ? ___ est sur la table.",
        listOf("Il", "Elle", "Ce", "En"),
        "Il",
        "指代阳性名词 le livre 用主格人称代词 il。"
    )
)

/** A1 · 否定结构 */
val a1Negation: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Je ___ parle pas anglais.",
        listOf("ne", "pas", "plus", "rien"),
        "ne",
        "标准否定为 ne...pas，ne 置于变位动词之前。"
    ),
    QuizQuestion(
        "Je ___ aime pas ce film.",
        listOf("ne", "n'", "je", "jamais"),
        "n'",
        "ne 在元音开头动词前省音为 n'：je n'aime pas。"
    ),
    QuizQuestion(
        "Elle n'a ___ frère.（她没有兄弟）",
        listOf("aucun", "pas", "personne", "rien"),
        "aucun",
        "ne...aucun + 单数名词，表示「一个也没有」。"
    ),
    QuizQuestion(
        "Nous ne mangeons ___ de viande.（我们从不吃……）",
        listOf("jamais", "aucun", "personne", "que"),
        "jamais",
        "ne...jamais 表示「从不」。aucun 需接名词，personne/rien 指人或事物。"
    ),
    QuizQuestion(
        "Tu ne manges ___ légumes.（你一点蔬菜都不吃）",
        listOf("pas de", "pas des", "des", "du"),
        "pas de",
        "绝对否定中部分冠词/不定冠词 des、du 变为 de：ne...pas de légumes。"
    ),
    QuizQuestion(
        "Il ne boit ___ de l'eau.（他只喝水）",
        listOf("que", "plus", "rien", "pas"),
        "que",
        "ne...que 表示「仅仅」，后面不加 pas。"
    ),
    QuizQuestion(
        "___ ne vient à la fête.（没有人来）",
        listOf("Personne", "Quelqu'un", "Chacun", "Tout"),
        "Personne",
        "作主语的「没有人」用 Personne + ne。"
    ),
    QuizQuestion(
        "___ parle pas !（别说话！）",
        listOf("Ne", "N'", "Pas", "Rien"),
        "Ne",
        "命令式的否定仍用 Ne...pas，parle 以辅音开头不用省音。"
    )
)

/** A1 · 时态初识：最近将来、正在进行、最近过去、简单时态辨认 */
val a1Tenses: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Demain, nous ___ aller au cinéma.",
        listOf("allons", "allez", "allions", "irons"),
        "allons",
        "最近将来时 = aller（现在时）+ 动词原形：nous allons aller。主语 nous 排除 allez。"
    ),
    QuizQuestion(
        "Maintenant, je ___ manger.（我正在吃）",
        listOf("suis en train de", "vais", "ai", "viens de"),
        "suis en train de",
        "be en train de + 原形表示「正在做」；vais 是最近将来；viens de 是最近过去。"
    ),
    QuizQuestion(
        "Nous ___ de rentrer chez nous.（我们刚回到家）",
        listOf("venons", "allons", "finissons", "essayons"),
        "venons",
        "venir de + 原形表示「刚刚做过」。"
    ),
    QuizQuestion(
        "« Hier, elle a travaillé. » 使用的时态是：",
        listOf("passé composé", "présent", "imparfait", "futur simple"),
        "passé composé",
        "avoir 的现在时 a + 过去分词 travaillé，构成复合过去时。"
    ),
    QuizQuestion(
        "« Regarde ! Il pleut. » 使用的时态是：",
        listOf("présent", "imparfait", "passé composé", "futur proche"),
        "présent",
        "pleut 是 pleuvoir 的现在时第三人称单数（无人称动词）。"
    ),
    QuizQuestion(
        "« Ils sont partis. » 使用的时态是：",
        listOf("présent", "passé composé", "imparfait", "futur simple"),
        "passé composé",
        "être 的现在时 sont + 过去分词 partis，构成复合过去时（ partis 用 être 作助动词）。"
    ),
    QuizQuestion(
        "« Nous finirons le projet demain. » 使用的时态是：",
        listOf("futur simple", "passé composé", "imparfait", "présent"),
        "futur simple",
        "finir + 人称词尾 -ons，是简单将来时第一人称复数。"
    ),
    QuizQuestion(
        "___ allemand, j'habite à Berlin.（我是德国人，现在住在柏林）",
        listOf("Je suis", "J'étais", "Je serai", "Je sois"),
        "Je suis",
        "当前的身份用 être 的现在时：Je suis allemand（suis 以辅音开头，je 不省音）。"
    )
)
