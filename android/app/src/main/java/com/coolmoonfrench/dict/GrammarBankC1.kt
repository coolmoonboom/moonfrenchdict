package com.coolmoonfrench.dict

/** C1 · 强调结构与倒装 */
val c1MiseEnRelief: List<QuizQuestion> = listOf(
    QuizQuestion(
        "用强调句突出主语「Paul」（Paul a téléphoné.）：",
        listOf("C'est Paul qui a téléphoné.", "C'est Paul que a téléphoné.", "Ce Paul a téléphoné.", "Paul que a téléphoné."),
        "C'est Paul qui a téléphoné.",
        "强调人称主语：c'est + 强调成分 + qui + 从句。"
    ),
    QuizQuestion(
        "___ me plaît dans ce tableau, c'est la lumière.（这幅画我喜欢的是光线）",
        listOf("Ce qui", "Ce que", "Que", "Qui"),
        "Ce qui",
        "中性关系代词在从句中作主语（me plaît）用 ce qui。"
    ),
    QuizQuestion(
        "Ce que je reproche ___ ce collaborateur, c'est son retard.（我责备他的地方）",
        listOf("à", "de", "pour", "—"),
        "à",
        "reprocher qqch à qqn：je lui reproche son retard → à ce collaborateur。"
    ),
    QuizQuestion(
        "___-il roi, je ne lui céderais pas.（即便他是国王…）",
        listOf("Fût", "Fut", "Soit", "Serait"),
        "Fût",
        "虚拟式未完成过去时倒装 fût-il = même s'il était，让步的文学句式。"
    ),
    QuizQuestion(
        "Jamais ___ vu un tel spectacle.（文学倒装）",
        listOf("n'ai-je", "je n'ai", "ai-je", "n'ai"),
        "n'ai-je",
        "否定副词 jamais 提到句首时，主谓必须倒装且保留 ne：Jamais n'ai-je vu…。"
    ),
    QuizQuestion(
        "À peine ___ dans la salle que le film commença.",
        listOf("fus-je entré", "j'étais entré", "je fus entré", "étais entré"),
        "fus-je entré",
        "à peine 位于句首要倒装；与简单过去时 commença 配合，先时性用先过去时 passé antérieur。"
    )
)

/** C1 · 文学时态与书面时序 */
val c1TempsLitteraires: List<QuizQuestion> = listOf(
    QuizQuestion(
        "prendre 的简单过去时第三人称单数是：",
        listOf("prit", "prenait", "a pris", "prendra"),
        "prit",
        "prendre 的 passé simple：il prit。"
    ),
    QuizQuestion(
        "Dès qu'il ___ son travail, il sortit.（一完成工作他就出去了——文学时态配合）",
        listOf("eut fini", "avait fini", "a fini", "finit"),
        "eut fini",
        "简单过叙事中，dès que 的先时性用先过去时（passé antérieur）：il eut fini。"
    ),
    QuizQuestion(
        "Quoiqu'il ___ las, il continua.（文学让步从句）",
        listOf("fût", "est", "était", "fut"),
        "fût",
        "quoique 接虚拟式，书面语中用虚拟式未完成过去时 fût（与直陈式简单过去 fut 区分）。"
    ),
    QuizQuestion(
        "venir 的简单过去时第三人称复数是：",
        listOf("vinrent", "venaient", "sont venus", "viendraient"),
        "vinrent",
        "venir 的 passé simple：ils vinrent。"
    ),
    QuizQuestion(
        "历史叙事中使用「现在时」（présent de narration）的主要效果是：",
        listOf("使过去场景如在眼前、增强生动性", "单纯标记过去时间", "表示将来计划", "委婉语气"),
        "使过去场景如在眼前、增强生动性",
        "narratif 现在时在历史语篇中把事件画面化（vivid present）。"
    )
)

/** C1 · 名词化与名词句 */
val c1Nominalisation: List<QuizQuestion> = listOf(
    QuizQuestion(
        "把 « Le gouvernement augmente les impôts. » 改写为新闻标题式名词句：",
        listOf("Hausse des impôts", "Le gouvernement augmente les impôts", "Augmenter les impôts maintenant", "Les impôts augmentent vite"),
        "Hausse des impôts",
        "新闻标题名词化：动词句压缩为「名词 + 补语」的名名词组，无人称与时态。"
    ),
    QuizQuestion(
        "construire 的抽象名词是：",
        listOf("la construction", "le constructeur", "construirement", "la constructrice"),
        "la construction",
        "动作抽象名词：construire → la construction（-teur 形式指施动者）。"
    ),
    QuizQuestion(
        "analyser 的名词化结果是：",
        listOf("l'analyse", "l'analysement", "la analysation", "l'analyser"),
        "l'analyse",
        "-iser 动词常对应 -ise/-yse 名词：analyser → analyse。"
    ),
    QuizQuestion(
        "把 « Après que le vent se fut levé, … » 换成名词化从句（分词独立结构）：",
        listOf("Le vent s'étant levé, …", "Après le vent, …", "Que le vent levait, …", "Le vent qu'il se lève, …"),
        "Le vent s'étant levé, …",
        "先时性从句可用复合分词独立结构表达：le vent s'étant levé（= 风起来之后）。"
    )
)

/** C1 · 高阶词语辨析 */
val c1Nuances: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Le Parlement ___ le président.（议会经投票选出总统）",
        listOf("élit", "choisit", "opte", "trouve"),
        "élit",
        "élire 专指投票选举（élit 为现在时）；choisir 泛指选择，opte 需接 pour，trouve 语体不符。"
    ),
    QuizQuestion(
        "Nous avons ___ la réunion à vendredi.（推迟到周五）",
        listOf("repoussé", "prolongé", "allongé", "étendu"),
        "repoussé",
        "repousser = 延期（推迟时间）；prolonger 是延长（持续时间）；其余搭配不成立的选项排除。"
    ),
    QuizQuestion(
        "Avant l'examen, les étudiants ___ leurs leçons.",
        listOf("révisent", "revisitent", "refont", "redoublent"),
        "révisent",
        "réviser ses leçons = 复习功课；revisiter 是重游，refaire 是重做。"
    ),
    QuizQuestion(
        "dénicher un village perdu 里的 dénicher 意思是：",
        listOf("找到（费心找到的）", "建造", "破坏", "忘记"),
        "找到（费心找到的）",
        "dénicher（口语色彩）= 经过搜寻而找到。"
    ),
    QuizQuestion(
        "Il a ___ de nous accompagner.（他同意跟我们一起去）",
        listOf("accepté", "consenti", "refusé", "permis"),
        "accepté",
        "accepter de + 原形；consentir 要求 à（consenti à…），本句介词为 de。"
    )
)
