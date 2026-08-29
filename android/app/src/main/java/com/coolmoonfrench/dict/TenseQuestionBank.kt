package com.coolmoonfrench.dict

val tenseQuestions: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Je ___ allé à Paris hier.",
        listOf("suis", "ai", "es", "est"),
        "suis",
        "aller 用 être 作助动词，je 用 suis。"
    ),
    QuizQuestion(
        "Elle ___ mangé une pomme.",
        listOf("suis", "as", "a", "est"),
        "a",
        "avoir 的直陈式现在时，elle 用 a。"
    ),
    QuizQuestion(
        "Nous ___ fini nos devoirs.",
        listOf("sommes", "avons", "êtes", "aura"),
        "avons",
        "finir 用 avoir 作助动词，nous 用 avons。"
    ),
    QuizQuestion(
        "Les enfants ___ partis ce matin.",
        listOf("ont", "sont", "avons", "aller"),
        "sont",
        "partir 表示位移，用 être 作助动词，ils/elles 用 sont。"
    ),
    QuizQuestion(
        "Quand j'étais petit, je ___ souvent au parc.",
        listOf("joue", "jouais", "jouer", "joué"),
        "jouais",
        "未完成过去时（imparfait）表示过去的习惯性动作。"
    ),
    QuizQuestion(
        "Tu ___ très fatigué ce matin.",
        listOf("es", "as", "était", "être"),
        "es",
        "être 的直陈式现在时，tu 用 es。"
    ),
    QuizQuestion(
        "Nous ___ regarder un film ce soir. (futur proche)",
        listOf("allons", "alliez", "irons", "vont"),
        "allons",
        "最近将来时 = aller（现在时）+ 不定式，nous 用 allons。"
    ),
    QuizQuestion(
        "Demain, il ___ plus tard.",
        listOf("travaille", "travaillera", "travailler", "travaillait"),
        "travaillera",
        "简单将来时（futur simple）表示将来的动作。"
    ),
    QuizQuestion(
        "Je ___ de finir mes devoirs. (passé récent)",
        listOf("suis", "viens", "viendrais", "vais"),
        "viens",
        "最近过去时 = venir（现在时）+ de + 不定式，表示刚刚完成的动作。"
    ),
    QuizQuestion(
        "Je ___ bien un café, s'il vous plaît. (conditionnel)",
        listOf("veux", "voudrais", "voudrai", "voudrez"),
        "voudrais",
        "条件式现在时（conditionnel présent）用于礼貌请求。"
    ),
    QuizQuestion(
        "Il faut que tu ___ maintenant. (subjonctif)",
        listOf("viens", "viennes", "viendras", "es venu"),
        "viennes",
        "il faut que 后接虚拟式（subjonctif），venir 的虚拟式为 viennes。"
    ),
    QuizQuestion(
        "Quand tu es arrivé, j'___ déjà mangé.",
        listOf("avais", "ai", "suis", "aurai"),
        "avais",
        "愈过去时（plus-que-parfait）表示过去某个动作之前已完成的动作。"
    ),
    QuizQuestion(
        "___ ta chambre ! (impératif, nettoyer)",
        listOf("Nettoyez", "Nettoie", "Nettoyer", "Nettoyant"),
        "Nettoie",
        "命令式的 tu 形式用直陈式现在时去 s：nettoyer → nettoie。"
    ),
    QuizQuestion(
        "Quand il ___, nous partirons. (futur antérieur)",
        listOf("arrivera", "sera arrivé", "arrivait", "arrive"),
        "sera arrivé",
        "先将来时（futur antérieur）= être/avoir 将来时 + 过去分词。"
    ),
    QuizQuestion(
        "« Je parlais » est quel temps ?",
        listOf("Présent", "Imparfait", "Futur simple", "Passé composé"),
        "Imparfait",
        "parlais 是 imparfait（未完成过去时）过去词尾 -ais。"
    ),
    QuizQuestion(
        "« Nous avons mangé » est quel temps ?",
        listOf("Passé composé", "Présent", "Plus-que-parfait", "Futur simple"),
        "Passé composé",
        "avoir 现在时 + 过去分词 = passé composé（复合过去时）。"
    ),
    QuizQuestion(
        "« Elle va étudier » est quel temps ?",
        listOf("Futur proche", "Futur simple", "Présent", "Conditionnel"),
        "Futur proche",
        "aller 现在时 + 不定式 = 最近将来时（futur proche）。"
    ),
    QuizQuestion(
        "Je ___ un café si j'avais le temps. (vouloir, conditionnel)",
        listOf("voulais", "voudrais", "veux", "voudrai"),
        "voudrais",
        "条件式现在时 = 将来时词干 + 未完成过去时词尾，vouloir → voudrais。"
    ),
    QuizQuestion(
        "Nous ___ beaucoup de livres à l'école. (avoir, imparfait)",
        listOf("avons", "avions", "aurons", "ayons"),
        "avions",
        "未完成过去时词尾 -ions，avoir → avions。"
    ),
    QuizQuestion(
        "Ils ___ contents de ta visite. (être, passé composé)",
        listOf("ont été", "sont été", "étaient", "être"),
        "ont été",
        "平时的 être 在复合时态中本身作助动词要用 avoir：ont été。"
    ),
    QuizQuestion(
        "Je ___ mes devoirs. (faire, présent)",
        listOf("fais", "faise", "fait", "faites"),
        "fais",
        "faire 的直陈式现在时：je fais。"
    ),
    QuizQuestion(
        "Tu ___ à Madrid l'année prochaine. (aller, futur simple)",
        listOf("alleras", "iras", "va", "irais"),
        "iras",
        "aller 的简单将来时：j'irai, tu iras。"
    ),
    QuizQuestion(
        "Elle ___ parler trois langues. (pouvoir, présent)",
        listOf("poux", "peut", "peux", "puisse"),
        "peut",
        "pouvoir 现在时：je peux, tu peux, il/elle peut。"
    ),
    QuizQuestion(
        "Nous ___ finir avant midi. (devoir, présent)",
        listOf("devons", "dû", "doit", "devrons"),
        "devons",
        "devoir 现在时：nous devons。"
    ),
    QuizQuestion(
        "Je ___ cette réponse. (savoir, imparfait)",
        listOf("savais", "sais", "saurai", "sus"),
        "savais",
        "savoir 的未完成过去时：je savais。"
    ),
    QuizQuestion(
        "Ils ___ de France hier. (venir, passé composé)",
        listOf("ont venu", "sont venus", "viennent", "viendront"),
        "sont venus",
        "venir 用 être 作助动词，变位的过去分词要与主语配合：venus。"
    ),
    QuizQuestion(
        "Nous ___ au restaurant. (manger, présent)",
        listOf("mangeons", "mangons", "manges", "mangez"),
        "mangeons",
        "-ger 结尾动词在 nous 前加 e 保持 /ʒ/ 音：mangeons。"
    ),
    QuizQuestion(
        "Nous ___ le cours. (commencer, présent)",
        listOf("commençons", "commencess", "commencent", "commencions"),
        "commençons",
        "-cer 结尾动词在 nous 时用 ç 保持 /s/ 音：commençons。"
    ),
    QuizQuestion(
        "Le participe passé de « prendre » est ___.",
        listOf("pris", "prisé", "prenant", "prenu"),
        "pris",
        "prendre 的过去分词是 pris（不规则）。"
    ),
    QuizQuestion(
        "Le participe passé de « voir » est ___.",
        listOf("voit", "vu", "vus", "voyant"),
        "vu",
        "voir 的过去分词是 vu。"
    ),
    QuizQuestion(
        "Le participe passé de « dire » est ___.",
        listOf("dit", "dis", "dirai", "disant"),
        "dit",
        "dire 的过去分词是 dit。"
    ),
    QuizQuestion(
        "« Demain » 对应的时态通常用 ___。",
        listOf("futur", "passé composé", "imparfait", "passé simple"),
        "futur",
        "demain（明天）提示将来时态。"
    ),
    QuizQuestion(
        "« Hier » 对应的时态通常用 ___。",
        listOf("passé", "futur proche", "futur simple", "présent"),
        "passé",
        "hier（昨天）提示过去时态。"
    ),
    QuizQuestion(
        "Elle ___ en train d'étudier. (présent continu)",
        listOf("est", "a", "va", "fait"),
        "est",
        "现在进行时 = être + en train de + 不定式，elle 用 est。"
    ),
    QuizQuestion(
        "Nous ___ de partir quand il est arrivé. (futur proche du passé)",
        listOf("allions", "allons", "irons", "allerons"),
        "allions",
        "aller 的未完成过去时 + 不定式表示过去语境下的最近将来。"
    )
)