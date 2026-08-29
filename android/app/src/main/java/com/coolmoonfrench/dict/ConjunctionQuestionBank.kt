package com.coolmoonfrench.dict

val conjunctionQuestions: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Je vais au cinéma ___ au théâtre.",
        listOf("et", "ou", "mais", "car"),
        "ou",
        "ou = 或者，表示选择关系。"
    ),
    QuizQuestion(
        "Il est gentil ___ intelligent.",
        listOf("mais", "ou", "et", "donc"),
        "et",
        "et = 和，并列关系。"
    ),
    QuizQuestion(
        "Je veux venir, ___ je suis fatigué.",
        listOf("et", "mais", "ou", "car"),
        "mais",
        "mais = 但是，表示转折。"
    ),
    QuizQuestion(
        "___ il pleut, nous restons à la maison.",
        listOf("Comme", "Mais", "Ou", "Et"),
        "Comme",
        "comme + 从句表原因：「因为下雨……」"
    ),
    QuizQuestion(
        "Je pense ___ tu as raison.",
        listOf("si", "que", "quand", "comme"),
        "que",
        "penser que = 认为……，que 引导宾语从句。"
    ),
    QuizQuestion(
        "Je viendrai ___ il fait beau.",
        listOf("que", "si", "mais", "car"),
        "si",
        "si + 现在时表示条件：「如果天气好」。"
    ),
    QuizQuestion(
        "Il est content ___ il a terminé.",
        listOf("parce que", "donc", "mais", "que"),
        "parce que",
        "parce que = 因为，表示原因。"
    ),
    QuizQuestion(
        "Elle travaille ___ elle a besoin d'argent.",
        listOf("donc", "car", "si", "que"),
        "car",
        "car = 因为（位于两个分句之间，语气较正式）。"
    ),
    QuizQuestion(
        "___ je suis malade, je vais travailler.",
        listOf("Bien que", "Parce que", "Si", "Quand"),
        "Bien que",
        "bien que + 虚拟式 = 虽然。"
    ),
    QuizQuestion(
        "Je me suis levé tôt ___ voir le soleil se lever.",
        listOf("pour que", "pour", "parce que", "si"),
        "pour",
        "pour + 不定式表示目的：「为了看日出」。"
    ),
    QuizQuestion(
        "Il parle fort ___ tout le monde l'entende.",
        listOf("afin que", "parce que", "pour", "si"),
        "afin que",
        "afin que + 虚拟式 = 为了，引导目的从句。"
    ),
    QuizQuestion(
        "Je téléphonerai ___ j'arrive.",
        listOf("dès que", "si", "que", "car"),
        "dès que",
        "dès que = 一……就……。"
    ),
    QuizQuestion(
        "Restez ___ il revienne.",
        listOf("jusqu'à ce que", "parce que", "pour que", "si"),
        "jusqu'à ce que",
        "jusqu'à ce que + 虚拟式 = 直到。"
    ),
    QuizQuestion(
        "___ je sortais, il pleuvait.",
        listOf("Quand", "Si", "Pour que", "Car"),
        "Quand",
        "quand + 未完成过去时描述过去背景：「当我出门时」。"
    ),
    QuizQuestion(
        "Il est parti ___ tu étais absent.",
        listOf("pendant que", "pour que", "afin que", "si"),
        "pendant que",
        "pendant que = 当……的时候（两动作同时）。"
    ),
    QuizQuestion(
        "Travaille dur ___ tu réussisses.",
        listOf("pour que", "parce que", "si", "quand"),
        "pour que",
        "pour que + 虚拟式 = 为了（目的）。"
    ),
    QuizQuestion(
        "Ni Pierre ___ Marie ne sont là.",
        listOf("et", "ni", "ou", "mais"),
        "ni",
        "ni...ni... = 既不……也不……。"
    ),
    QuizQuestion(
        "Il est arrivé tard ___ il avait raté son train.",
        listOf("donc", "car", "et", "ou"),
        "car",
        "car = 因为：解释迟到的原因。"
    ),
    QuizQuestion(
        "Prête-moi ton stylo, ___ je dois écrire.",
        listOf("car", "si", "que", "mais"),
        "car",
        "car = 因为：说明借笔的原因。"
    ),
    QuizQuestion(
        "Je ne sais pas ___ il viendra demain.",
        listOf("si", "que", "car", "donc"),
        "si",
        "si 引导间接疑问句：「是否」。"
    ),
    QuizQuestion(
        "Il a plu, ___ nous restons chez nous.",
        listOf("donc", "car", "si", "que"),
        "donc",
        "donc = 因此、所以。"
    ),
    QuizQuestion(
        "Il est triste ___ sa femme est malade.",
        listOf("parce que", "pour que", "quoi que", "si"),
        "parce que",
        "parce que = 因为：解释原因。"
    ),
    QuizQuestion(
        "___ tu viennes ou non, le spectacle commence.",
        listOf("Que", "Si", "Quand", "Comme"),
        "Que",
        "que + 虚拟式表示让步：「无论你来不来」。"
    ),
    QuizQuestion(
        "Elle veut ___ tu l'aides.",
        listOf("si", "que", "car", "mais"),
        "que",
        "vouloir que + 虚拟式：「她想让你帮她」。"
    ),
    QuizQuestion(
        "Il est grand ___ fort.",
        listOf("et", "ou", "mais", "donc"),
        "et",
        "et = 和，连接两个并列的形容词。"
    ),
    QuizQuestion(
        "___ il est malade, il ne viendra pas.",
        listOf("Puisque", "Pour que", "Afin que", "Si"),
        "Puisque",
        "puisque = 既然（原因已经很明确）。"
    ),
    QuizQuestion(
        "___ nous avons le temps, visitons le musée.",
        listOf("Puisque", "Bien que", "Pour que", "Quand"),
        "Puisque",
        "puisque = 既然：「既然我们有时间」。"
    ),
    QuizQuestion(
        "Il court ___ il veut gagner.",
        listOf("parce que", "pour que", "dès que", "quoique"),
        "parce que",
        "parce que = 因为：解释跑步的原因。"
    ),
    QuizQuestion(
        "___ qu'il vienne, je suis content.",
        listOf("Quoiqu'", "Puisqu'", "Dès qu'", "Avant qu'"),
        "Quoiqu'",
        "quoique + 虚拟式 = 尽管：quoiqu'il vienne。"
    ),
    QuizQuestion(
        "« ainsi » 是表示 ___ 关系的连接词。",
        listOf("因果", "转折", "选择", "时间"),
        "因果",
        "ainsi = 这样、因此，表示结果/因果。"
    )
)