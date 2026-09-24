package com.coolmoonfrench.dict

/** B2 · 虚拟式过去时与虚拟式用法 */
val b2Subjonctif: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Je suis content que tu ___ réussi ton examen.",
        listOf("aies réussi", "as réussi", "avais réussi", "réussisses"),
        "aies réussi",
        "对已发生之事表达情感用虚拟式过去时：avoir 的虚拟式 aies + 过去分词。"
    ),
    QuizQuestion(
        "Il faut que nous ___ fini avant midi.",
        listOf("ayons fini", "avons fini", "aurons fini", "finissions"),
        "ayons fini",
        "先时性：il faut que + 虚拟式过去时，表示「到某刻之前已经完成」。"
    ),
    QuizQuestion(
        "Bien que la séance ___ longue, tout le monde est resté.",
        listOf("ait été", "a été", "était", "serait"),
        "ait été",
        "bien que + 虚拟式；已完成用虚拟式过去时 ait été。"
    ),
    QuizQuestion(
        "Je range la cuisine avant que mon père ___ du travail.",
        listOf("rentre", "rentrera", "est rentré", "rentrerait"),
        "rentre",
        "avant que 只接虚拟式：将来意义也用虚拟式现在时 rentre（futur 不可用）。"
    ),
    QuizQuestion(
        "下列哪个表达后面的从句必须用虚拟式？",
        listOf("Il est dommage que", "Il est certain que", "Parce que", "Étant donné que"),
        "Il est dommage que",
        "情感/惋惜类无人称表达接虚拟式；certain、causal 连词接直陈式。"
    ),
    QuizQuestion(
        "espérer que 在肯定句中后面的从句使用：",
        listOf("l'indicatif", "le subjonctif", "l'infinitif", "le conditionnel"),
        "l'indicatif",
        "espérer que 表确定预期，接直陈式（只有否定/疑问时才常见虚拟式）。"
    )
)

/** B2 · 条件式过去时 */
val b2ConditionnelPasse: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Si tu m'avais prévenu, je ___ venu.",
        listOf("serais", "étais", "suis", "aurais"),
        "serais",
        "与过去事实相反的假设：si + plus-que-parfait → conditionnel passé；venir 用 être：je serais venu。"
    ),
    QuizQuestion(
        "Elle ___ ce voyage si elle avait eu le temps.",
        listOf("aurait fait", "a fait", "avait fait", "fera"),
        "aurait fait",
        "主句用 conditionnel passé：avoir + fait，表示未曾实现的过去。"
    ),
    QuizQuestion(
        "Si j'avais su, je te l'___ dit.",
        listOf("aurais", "avais", "ai", "aurai"),
        "aurais",
        "与过去相反的假设：si + plus-que-parfait → conditionnel passé：je te l'aurais dit。"
    ),
    QuizQuestion(
        "下列哪句最客气委婉？",
        listOf("J'aurais voulu vous parler.", "Je veux vous parler.", "Il faut que je vous parle.", "Parlez-moi !"),
        "J'aurais voulu vous parler.",
        "conditionnel passé 表过去未实现的愿望，用作极致委婉的请求。"
    ),
    QuizQuestion(
        "Il paraît qu'il ___ quitter le pays.（据说他要离开这个国家）",
        listOf("serait", "est", "sera", "fût"),
        "serait",
        "conditionnel de l'information non confirmée：转述未经证实的消息用条件式。"
    ),
    QuizQuestion(
        "« Tu aurais vu sa tête ! » 这句话表达：",
        listOf("对未能目击之事的感叹", "对未来的推测", "礼貌请求", "习惯性动作"),
        "对未能目击之事的感叹",
        "conditionnel passé 用于感叹：可惜你没看到他的表情。"
    )
)

/** B2 · 现在分词 / 副动词 / 复合不定式 */
val b2ParticipeGerondif: List<QuizQuestion> = listOf(
    QuizQuestion(
        "avoir 的现在分词是：",
        listOf("ayant", "aient", "ait", "ayante"),
        "ayant",
        "avoir 的现在分词为 ayant；aient 是虚拟式现在时第三人称复数。"
    ),
    QuizQuestion(
        "Elle est partie sans ___ un mot.",
        listOf("dire", "disant", "dit", "dont dire"),
        "dire",
        "sans / avant de / après 等介词结构后接动词原形：sans dire un mot。"
    ),
    QuizQuestion(
        "Il a réussi ___ travaillant dur toute l'année.",
        listOf("en", "à", "de", "pour"),
        "en",
        "副动词 en + 现在分词表方式手段：en travaillant。"
    ),
    QuizQuestion(
        "Après ___ son repas, il prend un café.",
        listOf("avoir fini", "finissant", "finit", "fini"),
        "avoir fini",
        "après + 复合不定式表示先时：après avoir fini（être 类动词用 après être parti）。"
    ),
    QuizQuestion(
        "Il marcha toute la nuit, la pluie ___ sans cesse.（雨不停地下——分词独立结构）",
        listOf("tombant", "tombait", "en tomber", "tombe"),
        "tombant",
        "绝对分词句（逻辑主语与主句不同）：la pluie tombant sans cesse。"
    ),
    QuizQuestion(
        "___ en sachant qu'il était fatigué, il continua.（虽然知道自己累了）",
        listOf("Tout", "Très", "Bien", "Mal"),
        "Tout",
        "tout en + 副动词可表让步：tout en sachant que…。"
    )
)

/** B2 · 复合关系代词 */
val b2RelativeComplexes: List<QuizQuestion> = listOf(
    QuizQuestion(
        "La collègue ___ je t'ai parlé arrive demain.",
        listOf("dont", "lequel", "qui", "à qui"),
        "dont",
        "parler de qqn → dont，先行词是人也可用 dont（谈论过的人）。"
    ),
    QuizQuestion(
        "C'est un ami ___ je tiens beaucoup.",
        listOf("à qui", "dont", "que", "lequel"),
        "à qui",
        "tenir à qqn（人）→ à qui；若先行词是物则用 auquel（à + lequel）。"
    ),
    QuizQuestion(
        "L'outil ___ je travaille est cassé.",
        listOf("avec lequel", "dont", "que", "qui"),
        "avec lequel",
        "travailler avec un outil：事物先行词带介词用介词 + lequel：avec lequel。"
    ),
    QuizQuestion(
        "La raison ___ je pars aujourd'hui est simple.",
        listOf("pour laquelle", "à laquelle", "de laquelle", "sur laquelle"),
        "pour laquelle",
        "la raison pour laquelle（…的原因），复合关系代词与先行词阴性单数配合。"
    ),
    QuizQuestion(
        "C'est cette rivière ___ les bords sont fleuris.（河岸开满花）",
        listOf("dont", "où", "qui", "que"),
        "dont",
        "dont 可表所属（= les bords de cette rivière），代替 de + 先行词作定语。"
    ),
    QuizQuestion(
        "Ce sont des questions ___ je n'ai pas trouvé de réponse.",
        listOf("auxquelles", "auxquels", "desquelles", "dont"),
        "auxquelles",
        "trouver une réponse à une question：à + lesquelles（阴性复数）→ auxquelles。"
    )
)

/** B2 · 语域与文体 */
val b2Registres: List<QuizQuestion> = listOf(
    QuizQuestion(
        "下列哪句属于文学/书面语体（passé simple）？",
        listOf("Il partit sans un mot.", "Il est parti sans un mot.", "Il part sans un mot.", "Il va partir."),
        "Il partit sans un mot.",
        "简单过去时 partit 只用于书面叙事语体；口语与媒体用 passé composé。"
    ),
    QuizQuestion(
        "« Je crains qu'il ne vienne. » 中的 ne 是：（我担心他会来——此 ne 不表否定）",
        listOf("le ne explétif", "une négation", "un pronom", "une particule d'interrogation"),
        "le ne explétif",
        "avant que、de peur que、craindre que 等后的赘虚 ne（expletive）不带否定意义，是正式文体标志。"
    ),
    QuizQuestion(
        "下列哪句是典型口语（familier）？",
        listOf("Tu veux pas venir ?", "Tu ne veux pas venir ?", "Ne veux-tu pas venir ?", "Voudrais-tu venir ?"),
        "Tu veux pas venir ?",
        "口语省略否定词 ne，且语序不加倒装，是口语标志。"
    ),
    QuizQuestion(
        "口语词 bouffer 的意思是：",
        listOf("manger", "dormir", "rire", "courir"),
        "manger",
        "bouffer 为口语（familier）用词的「吃」。"
    ),
    QuizQuestion(
        "信函开头 « J'ai l'honneur de ___ informer… » 应填：",
        listOf("vous", "te", "le", "lui"),
        "vous",
        "正式信函固定套语：j'ai l'honneur de vous informer。"
    ),
    QuizQuestion(
        "___ ses efforts, il n'a pas réussi.（尽管他很努力）",
        listOf("Malgré", "Bien que", "Alors que", "Parce que"),
        "Malgré",
        "malgré + 名词短语；bien que + 虚拟式从句，后接名词只能选 malgré。"
    )
)
