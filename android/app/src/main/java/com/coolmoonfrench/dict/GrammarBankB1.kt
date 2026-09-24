package com.coolmoonfrench.dict

/** B1 · 时态配合：passé composé 与 imparfait */
val b1TenseContrast: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Il ___ quand le téléphone a sonné.",
        listOf("dormait", "a dormi", "dormais", "dormira"),
        "dormait",
        "背景性、持续性的动作用 imparfait：il dormait（dorm- + -ait）。"
    ),
    QuizQuestion(
        "Hier soir, nous ___ au restaurant avec des amis.",
        listOf("avons dîné", "dînions", "dînons", "dînerons"),
        "avons dîné",
        "明确完成的一次性动作用 passé composé：nous avons dîné。"
    ),
    QuizQuestion(
        "Quand j'étais petit, je ___ à la campagne tous les étés.",
        listOf("allais", "suis allé", "irai", "aille"),
        "allais",
        "过去反复的习惯用 imparfait：j'allais。"
    ),
    QuizQuestion(
        "Elle lisait un livre quand la lumière ___.",
        listOf("s'est éteinte", "éteignait", "éteindrait", "éteindra"),
        "s'est éteinte",
        "imparfait 作背景，突然完成的动作（teindre）用 passé composé。"
    ),
    QuizQuestion(
        "À 8 heures ce matin, il ___ encore dans son lit.",
        listOf("était", "a été", "sera", "soit"),
        "était",
        "某时刻持续的状态描写用 imparfait。"
    ),
    QuizQuestion(
        "Nous ___ trois fois à Paris l'année dernière.",
        listOf("sommes allés", "allions", "allons", "irons"),
        "sommes allés",
        "有明确次数的已完成动作用 passé composé；aller 用 être 作助动词要配合。"
    ),
    QuizQuestion(
        "Autrefois, les gens ___ plus lentement.",
        listOf("voyageaient", "ont voyagé", "voyageront", "voyagent"),
        "voyageaient",
        "autrefois 提示过去的习惯描写：imparfait。"
    ),
    QuizQuestion(
        "Quand il ___ la porte, il a vu le courrier par terre.",
        listOf("a ouvert", "ouvrait", "ouvre", "ouvrira"),
        "a ouvert",
        "连续发生的两个完成动作都用 passé composé：a ouvert ... a vu。"
    )
)

/** B1 · 虚拟式现在时 */
val b1Subjonctif: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Il faut que tu ___ tes devoirs.",
        listOf("fasses", "fais", "feras", "fasse"),
        "fasses",
        "il faut que + 虚拟式：faire → que tu fasses。"
    ),
    QuizQuestion(
        "Je veux que vous ___ la vérité.",
        listOf("disiez", "dites", "dirent", "diriez"),
        "disiez",
        "vouloir que + 虚拟式：dire → que vous disiez。"
    ),
    QuizQuestion(
        "Bien qu'il ___, il travaille beaucoup.",
        listOf("soit malade", "est malade", "sera malade", "était malade"),
        "soit malade",
        "bien que 永远接虚拟式：être → qu'il soit。"
    ),
    QuizQuestion(
        "Je ne pense pas qu'elle ___.",
        listOf("ait raison", "a raison", "aura raison", "aurais raison"),
        "ait raison",
        "否定形式的 penser que 接虚拟式：avoir → qu'elle ait。"
    ),
    QuizQuestion(
        "Il est possible que nous ___ en retard.",
        listOf("soyons", "sommes", "serons", "étions"),
        "soyons",
        "无人称的可能类表达后接虚拟式：être → que nous soyons。"
    ),
    QuizQuestion(
        "Avant que tu ne ___, appelle-moi.",
        listOf("partes", "partiras", "partis", "partirais"),
        "partes",
        "avant que 接虚拟式（书写中带赘虚 ne）：partir → que tu partes。"
    ),
    QuizQuestion(
        "Je cherche un appartement qui ___ au calme.",
        listOf("soit", "est", "était", "serait"),
        "soit",
        "先行词是否存在尚不确定时，关系从句用虚拟式：qui soit。"
    ),
    QuizQuestion(
        "Pourvu qu'il ___.（但愿他能来）",
        listOf("vienne", "vient", "viendra", "venait"),
        "vienne",
        "pourvu que 接虚拟式：venir → qu'il vienne。"
    )
)

/** B1 · 副代词 en / y */
val b1EnY: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Tu veux du café ? — Non, je n'___ veux pas.",
        listOf("en", "y", "le", "lui"),
        "en",
        "en 替代 de/du/des 引入的名词：je n'en veux pas（= du café）。"
    ),
    QuizQuestion(
        "Vous allez à Paris ? — Oui, nous ___ allons demain.",
        listOf("y", "en", "la", "lui"),
        "y",
        "y 替代 à + 地点：nous y allons（= à Paris）。"
    ),
    QuizQuestion(
        "Il pense souvent à ses vacances. — Oui, il ___ pense souvent.",
        listOf("y", "en", "les", "leur"),
        "y",
        "penser à + 事物 → y：il y pense。"
    ),
    QuizQuestion(
        "Elle a trois frères ? — Oui, elle ___ a trois.",
        listOf("en", "les", "y", "leur"),
        "en",
        "en 与数量连用替代 de + 名词：elle en a trois（= de frères）。"
    ),
    QuizQuestion(
        "Tu parles de ce problème ? — Non, je n'___ parle plus.",
        listOf("en", "y", "le", "lui"),
        "en",
        "parler de + 事物 → en：je n'en parle plus。"
    ),
    QuizQuestion(
        "Tu réponds à cette lettre ? — Oui, j'___ réponds demain.",
        listOf("y", "la", "lui", "en"),
        "y",
        "répondre à + 事物用 y；répondre à + 人用 lui。此处代替 à cette lettre 用 y。"
    ),
    QuizQuestion(
        "Nous rêvons de voyager. — Oui, nous ___ rêvons depuis des années.",
        listOf("en", "y", "le", "de"),
        "en",
        "rêver de + 补语 → en：nous en rêvons。"
    ),
    QuizQuestion(
        "Mets les plats sur la table ! — Oui, j'___ mets tout de suite.",
        listOf("les y", "y les", "en y", "les en"),
        "les y",
        "双代词顺序：me/te/se/le/la/les 在前，lui/leur、y、en 依次在后：je les y mets。"
    )
)

/** B1 · 被动语态与代动词 */
val b1PassivePronominal: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Ce château ___ au Xe siècle.（建于十世纪）",
        listOf("a été construit", "a construit", "est construite", "construit"),
        "a été construit",
        "被动语态的 passé composé：être 的 avoir 式 + 过去分词，与主语阳性单数配合。"
    ),
    QuizQuestion(
        "La lettre ___ hier soir.（信昨晚被读过了）",
        listOf("a été lue", "a lu", "a lue", "est lue"),
        "a été lue",
        "hier soir 排除现在时；被动分词与阴性主语配合 lue。"
    ),
    QuizQuestion(
        "Ils se ___ tous les matins à 7 heures.（他们每天七点起床）",
        listOf("lèvent", "lève", "élèvent", "levant"),
        "lèvent",
        "se lever：ils se lèvent（é 在闭音节读 è 的拼写）。"
    ),
    QuizQuestion(
        "Nous nous ___ de nos erreurs.（我们从错误中吸取教训）",
        listOf("souvenons", "rappelons", "souviens", "rappelle"),
        "souvenons",
        "se souvenir de qqch（带 de）；se rappeler 直接带宾语不加 de；nous 主语的变位 souvenons。"
    ),
    QuizQuestion(
        "Elle ___ le matin avant le petit-déjeuner.（她早上洗漱）",
        listOf("se lave", "lave", "laver", "se lavant"),
        "se lave",
        "代动词的自反代词放在变位动词之前：elle se lave。"
    ),
    QuizQuestion(
        "Ça se ___ facilement.（这很容易办到）",
        listOf("fait", "fais", "fasse", "fera"),
        "fait",
        "ça se faire 表「某事得以做成」：ça se fait（faire 第三人称单数现在时）。"
    ),
    QuizQuestion(
        "Ils se sont ___ la main.（他们握了手）",
        listOf("serré", "serrés", "serrée", "serrées"),
        "serré",
        "serrer la main à qqn → se 为间接宾语、直接宾语 la main 后置，过去分词不配合。"
    ),
    QuizQuestion(
        "Elles se sont ___ des cadeaux.（她们互赠了礼物）",
        listOf("offert", "offerts", "offerte", "offertes"),
        "offert",
        "offrir qqch à qqn → se 是间接宾语，直接宾语 cadeaux 后置，分词不配合。"
    )
)

/** B1 · 间接引语 */
val b1DiscoursRapporte: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Il dit : « Je suis fatigué. » → Il dit qu'il ___ fatigué.",
        listOf("est", "était", "sera", "soit"),
        "est",
        "主句为现在时（il dit）时，间接引语时态保持不变。"
    ),
    QuizQuestion(
        "Elle a dit : « Je pars demain. » → Elle a dit qu'elle partait ___.",
        listOf("le lendemain", "demain", "la veille", "l'avant-veille"),
        "le lendemain",
        "主句为过去时间：demain → le lendemain（时间状语同步后退）。"
    ),
    QuizQuestion(
        "« Je l'ai vu hier » → Il a dit qu'il l'avait vu ___.",
        listOf("la veille", "hier", "le lendemain", "ce jour-là"),
        "la veille",
        "hier → la veille（前一天）。"
    ),
    QuizQuestion(
        "Elle demande : « Viendras-tu ? » → Elle demande ___ je viendrai.",
        listOf("si", "que", "ce que", "quand"),
        "si",
        "是非问句（est-ce que/倒装）变间接引语用 si 引导。"
    ),
    QuizQuestion(
        "« Où habites-tu ? » → Il me demande où j___.",
        listOf("habite", "habites", "habitais", "habiterai"),
        "habite",
        "主句现在时时态不变；人称由 tu 改为 j'： où j'habite。"
    ),
    QuizQuestion(
        "« Finis tes devoirs ! » → Elle lui a dit de ___ ses devoirs.",
        listOf("finir", "finit", "finisse", "finissait"),
        "finir",
        "命令句转间接引语用 dire de + 动词原形。"
    ),
    QuizQuestion(
        "Elle pensait qu'il ___ la vérité.（她以为他当时说了实话）",
        listOf("avait dit", "dit", "dira", "aura dit"),
        "avait dit",
        "间接引语的时态后退：passé composé → plus-que-parfait。"
    ),
    QuizQuestion(
        "Il a demandé quand nous ___.（他问我们多久能到）",
        listOf("arriverions", "arrivons", "arrivions", "arriverons"),
        "arriverions",
        "主句为过去时时的间接引语：futur simple 变为 conditionnel présent。"
    )
)
