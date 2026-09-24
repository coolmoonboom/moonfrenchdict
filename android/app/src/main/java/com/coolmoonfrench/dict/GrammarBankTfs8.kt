package com.coolmoonfrench.dict

// ================= 专八附加 · 书面专项（AI 预生成，人工校对） =================

/**
 * 完形填空：每题携带全文与一个空格，专八附加 · 完形填空
 */
private val clozeA = """Depuis la pandémie, le télétravail (1)___ profondément transformé l'organisation du travail. Ceux qui le défendent mettent (2)___ avant le gain de temps dans les transports ; en (3)___, certains salariés déplorent un manque de liens avec leurs collègues. Les entreprises essaient donc de trouver un rythme (4)___ : deux jours à domicile par semaine, par (5)___ . L'important est de préserver l'équilibre entre vie professionnelle et vie privée."""

private val clozeB = """Aujourd'hui, de plus en plus de citadins choisissent le vélo (1)___ moyen de transport quotidien. Les pistes cyclables (2)___ ont été aménagées dans le centre ont transformé les déplacements. Le vélo, (3)___ pratique qu'il puisse paraître, est parfois dangereux sous la pluie. Il réduit la pollution et permet de garder la forme (4)___ coût, ce qui n'est pas (5)___ ."""

private fun cloze(
    text: String,
    blank: Int,
    options: List<String>,
    correct: String,
    explain: String
) = QuizQuestion(
    question = "$text\n\n请选择第 ($blank) 空的正确选项：",
    options = options,
    correct = correct,
    explanation = explain
)

val tfs8Cloze: List<QuizQuestion> = listOf(
    cloze(clozeA, 1, listOf("a", "ont", "est", "avais"), "a",
        "主语 le télétravail 第三人称单数，passé composé 用 a + transformé。"),
    cloze(clozeA, 2, listOf("en", "à", "sur", "par"), "en",
        "固定搭配 mettre en avant（强调、突出）。"),
    cloze(clozeA, 3, listOf("revanche", "effet", "général", "conséquence"), "revanche",
        "前后句意对立，用 en revanche（相反）；en effet 表因果确认。"),
    cloze(clozeA, 4, listOf("équilibré", "équilibre", "équilibrée", "équilibrant"), "équilibré",
        "过去分词作形容词修饰阳性单数 rythme，用 équilibré。"),
    cloze(clozeA, 5, listOf("exemple", "conséquence", "fin", "sens"), "exemple",
        "par exemple 固定短语（例如）。"),
    cloze(clozeB, 1, listOf("comme", "en", "à", "pour"), "comme",
        "表示「作为」用 comme：comme moyen de transport。"),
    cloze(clozeB, 2, listOf("qui", "que", "dont", "où"), "qui",
        "关系代词在从句中作主语（ont été aménagées 的主语）用 qui。"),
    cloze(clozeB, 3, listOf("aussi", "tel", "comme", "si"), "aussi",
        "aussi + 形容词 + que + 虚拟式 = 无论多么…（让步）。paraître 用了虚拟式 puisse paraître。"),
    cloze(clozeB, 4, listOf("sans", "avec", "à", "en"), "sans",
        "sans coût = 零成本；上下文强调自行车的好处。"),
    cloze(clozeB, 5, listOf("négligeable", "négligeant", "négligé", "négligemment"), "négligeable",
        "固定表达 ce qui n'est pas négligeable（这绝非小事/不容小觑）。")
)

/** 阅读理解 */
private val lecture1 = """L'Académie française, fondée en 1634 par le cardinal de Richelieu, compte quarante membres, surnommés les « Immortels ». Sa mission principale est de publier le dictionnaire de la langue française : sa première édition date de 1694 ; on en est aujourd'hui à la neuvième, toujours inachevée, dont plus de la moitié des articles a été publiée depuis 1986. L'Académie veille aussi sur la langue en recommandant des termes français pour remplacer les anglicismes."""

private val lecture2 = """La 110e édition du Tour de France a démarré samedi de Copenhague avec un contre-la-montre individuel remporté par un coureur slovène. Le peloton traversera vingt-quatre villes avant l'arrivée traditionnelle sur les Champs-Élysées, prévue fin juillet. Comme chaque année, plusieurs étapes de montagne pourraient décider du sort du maillot jaune, ce classement distinctif revenant chaque soir au leader du classement général."""

val tfs8Reading: List<QuizQuestion> = listOf(
    QuizQuestion(
        question = lecture1 + "\n\n问题：L'Académie française a été fondée en ?",
        options = listOf("1634", "1694", "1789", "1986"),
        correct = "1634",
        "1694 是第一版词典，1986 是第九版开始出版；创建于 1634 年。"
    ),
    QuizQuestion(
        question = lecture1 + "\n\n问题：Les membres de l'Académie sont au nombre de :",
        options = listOf("quarante", "vingt-quatre", "cent", "soixante"),
        correct = "quarante",
        "固定名额 40 人，绰号 les Immortels。"
    ),
    QuizQuestion(
        question = lecture1 + "\n\n问题：La neuvième édition du dictionnaire est :",
        options = listOf("尚未完稿，已出版过半", "早已出齐", "刚刚动笔", "只有网上版"),
        correct = "尚未完稿，已出版过半",
        "toujours inachevée… dont plus de la moitié des articles a été publiée depuis 1986。"
    ),
    QuizQuestion(
        question = lecture2 + "\n\n问题：La 110e édition a commencé par :",
        options = listOf("un contre-la-montre individuel", "une étape de plaine", "une étape de montagne", "un critérium nocturne"),
        correct = "un contre-la-montre individuel",
        "démarré… avec un contre-la-montre individuel remporté par un coureur slovène。"
    ),
    QuizQuestion(
        question = lecture2 + "\n\n问题：Le maillot jaune désigne :",
        options = listOf("每赛段冠军", "总成绩领先者", "爬坡积分王", "最佳年轻车手"),
        correct = "总成绩领先者",
        "…ce classement distinctif revenant chaque soir au leader du classement général。"
    ),
    QuizQuestion(
        question = lecture2 + "\n\n问题：L'arrivée est traditionnellement prévue :",
        options = listOf("sur les Champs-Élysées", "au Stade de France", "au Bois de Boulogne", "à Copenhague"),
        correct = "sur les Champs-Élysées",
        "l'arrivée traditionnelle sur les Champs-Élysées。"
    )
)

/** 法译汉 */
val tfs8FrToZh: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Il ne demande qu'à partir.",
        listOf("他只希望能离开。", "他根本不想走。", "他没被请求离开。", "他很难开口请求离开。"),
        "他只希望能离开。",
        "ne demander qu'à + 原形 = 巴不得、只求…（qu'= seulement）。"
    ),
    QuizQuestion(
        "C'est un homme de parole.",
        listOf("他说话算数。", "他爱发表演说。", "他靠嘴吃饭。", "他从不多言。"),
        "他说话算数。",
        "un homme de parole = 守信用的人（parole→承诺）。"
    ),
    QuizQuestion(
        "Il a fini par accepter.",
        listOf("他最终接受了。", "他最终拒绝了。", "他很快就同意了。", "他始终没同意。"),
        "他最终接受了。",
        "finir par + 原形 = 最终…（经过犹豫或拖延）。"
    ),
    QuizQuestion(
        "Plus on est de fous, plus on rit.",
        listOf("人越多越热闹。", "疯子越多越危险。", "笑一笑，十年少。", "独行快，众行远。"),
        "人越多越热闹。",
        "plus… plus… 双重比较句型；谚语：人多欢乐多。"
    ),
    QuizQuestion(
        "Il court après une promotion.",
        listOf("他一心谋求晋升。", "他追不上那辆促销的广告车。", "他被升职赶出来了。", "他晋升得毫不费力。"),
        "他一心谋求晋升。",
        "courir après qqch = 苦苦追求/追逐。"
    )
)

/** 汉译法 */
val tfs8ZhToFr: List<QuizQuestion> = listOf(
    QuizQuestion(
        "越有钱越不满足。",
        listOf("Plus on est riche, moins on est satisfait.", "Moins on est riche, moins on est satisfait.", "Tel est riche, tel est satisfait.", "Plus on est riche, plus on est satisfait."),
        "Plus on est riche, moins on est satisfait.",
        "「越…越不…」 = plus…, moins… 的双重比较结构。"
    ),
    QuizQuestion(
        "这部小说值得一读。",
        listOf("Ce roman vaut la peine d'être lu.", "Ce roman vaut la lire.", "Ce roman vaut peine être lu.", "Ce roman vaudra la peine lire."),
        "Ce roman vaut la peine d'être lu.",
        "valoir la peine de + 不定式；此处被动 être lu。"
    ),
    QuizQuestion(
        "你越是给予，越是收获不到。",
        listOf("Plus tu donnes, moins tu reçois.", "Si tu donnes, tu ne reçois pas.", "Tu donnes autant que tu reçois.", "Moins tu donnes, moins tu reçois."),
        "Plus tu donnes, moins tu reçois.",
        "plus…, moins… 越…越不…。"
    ),
    QuizQuestion(
        "别把时间都耗在看电视上。",
        listOf("Ne perds pas ton temps à regarder la télévision.", "Ne perds pas ton temps de regarder la télévision.", "Ne perds pas ton temps pour regarder la télévision.", "Ne perds pas ton temps regarder la télévision."),
        "Ne perds pas ton temps à regarder la télévision.",
        "perdre son temps à + 原形（把时间耗在做…上）。"
    ),
    QuizQuestion(
        "今日事今日毕（不要把今天能做的事拖到明天）。",
        listOf("Ne remettez pas au lendemain ce que vous pouvez faire le jour même.", "Ne faites pas le jour ce que vous pouvez remettre au lendemain.", "Faites demain ce que vous ne pouvez faire aujourd'hui.", "Le lendemain ne remet jamais le jour."),
        "Ne remettez pas au lendemain ce que vous pouvez faire le jour même.",
        "经典谚语：remettre à demain = 拖延到明天；关系代词 ce que。"
    )
)

/** 同义词替换 */
val tfs8Synonyms: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Il m'a adjuré de l'aider.（adjuré = ）",
        listOf("supplié", "menacé", "oublié", "remplacé"),
        "supplié",
        "adjurer qqn de = 苦苦哀求 ≈ supplier。"
    ),
    QuizQuestion(
        "Elle s'est octroyé trois jours de vacances.（s'est octroyé = ）",
        listOf("s'est accordé", "s'est interdit", "a perdu", "a offert"),
        "s'est accordé",
        "s'octroyer qqch = 自授、擅自给自己 ≈ s'accorder。"
    ),
    QuizQuestion(
        "Il a fini par dénouer l'affaire.（dénouer = ）",
        listOf("résoudre", "compliquer", "ouvrir", "oublier"),
        "résoudre",
        "dénouer（解开、化解）≈ résoudre；反义 embrouiller。"
    ),
    QuizQuestion(
        "un bon à rien（= ）",
        listOf("un incapable", "un généreux", "un travailleur", "un expert"),
        "un incapable",
        "un bon à rien = 一无是处的人。"
    ),
    QuizQuestion(
        "Cette montre m'a coûté les yeux de la tête.（= ）",
        listOf("extrêmement cher", "un peu cher", "presque rien", "moins que prévu"),
        "extrêmement cher",
        "coûter les yeux de la tête（口语夸张）= 贵得离谱。"
    )
)

/** 句子改错（选出正确形式） */
val tfs8ErrorSpot: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Les fleurs que j'ai ___ sont déjà fanées.",
        listOf("cueillies", "cueilli", "cueillie", "cueillis"),
        "cueillies",
        "avoir + 前置直接宾语 que（= fleurs 阴复）时分词配合：que j'ai cueillies。"
    ),
    QuizQuestion(
        "La douleur qu'il a ___ était insupportable.",
        listOf("endurée", "enduré", "endurés", "endurées"),
        "endurée",
        "qu'= la douleur（阴性单数）前置 → endurée。"
    ),
    QuizQuestion(
        "Combien de livres as-tu ___ ?",
        listOf("achetés", "acheté", "achetée", "achetées"),
        "achetés",
        "combien de + 名词 结构的分词传统上与 livres（阳复）配合。"
    ),
    QuizQuestion(
        "Les maisons qu'ils ont ___ construire sont modernes.",
        listOf("fait", "faite", "faites", "fais"),
        "fait",
        "faire + 不定式 结构中的 fait 永远不变（即使宾语前置）。"
    ),
    QuizQuestion(
        "Elle a acheté ___ roses.",
        listOf("deux cents", "deux cent", "deux cents de", "cent deux"),
        "deux cents",
        "cent 被倍数且后面不接另一数词时加 s：deux cents roses。"
    )
)

/** 法国文学与文化常识 */
val tfs8Culture: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Qui a écrit « Les Misérables » ?",
        listOf("Victor Hugo", "Émile Zola", "Honoré de Balzac", "Stendhal"),
        "Victor Hugo",
        "《悲惨世界》1862 年问世，作者雨果。"
    ),
    QuizQuestion(
        "L'auteur du « Petit Prince » était aussi :",
        listOf("aviateur", "médecin", "avocat", "professeur"),
        "aviateur",
        "圣埃克苏佩里同时是飞行员，作品取材其飞行经历。"
    ),
    QuizQuestion(
        "La fête nationale française commémore :",
        listOf("1789 年 7 月 14 日攻克巴士底狱", "1792 年共和国成立", "1848 年革命", "1804 年称帝"),
        "1789 年 7 月 14 日攻克巴士底狱",
        "7 月 14 日国庆日纪念攻占巴士底狱。"
    ),
    QuizQuestion(
        "Qui a reçu le prix Nobel de littérature en 1957 ?",
        listOf("Albert Camus", "Jean-Paul Sartre", "Marcel Proust", "André Malraux"),
        "Albert Camus",
        "加缪 1957 年获诺贝尔文学奖（萨特 1964 年拒绝领奖）。"
    ),
    QuizQuestion(
        "Le vrai nom de Molière est :",
        listOf("Jean-Baptiste Poquelin", "François-Marie Arouet", "Honoré de Balzac", "Edmond Rostand"),
        "Jean-Baptiste Poquelin",
        "莫里哀（1622-1673）本名让-巴蒂斯特·波克兰。"
    ),
    QuizQuestion(
        "« La Marseillaise » est devenue l'hymne national en :",
        listOf("1795 年", "1789 年", "1830 年", "1875 年"),
        "1795 年",
        "1795 年（芽月 26 日法令）被定为国歌。"
    )
)

/** 写作手法 */
val tfs8Redaction: List<QuizQuestion> = listOf(
    QuizQuestion(
        "议论文（dissertation）开头段应当完成的任务不包括：",
        listOf("完整陈述全部论据", "amener le sujet（引入话题）", "poser une problématique（提出问题）", "annoncer le plan（预告结构）"),
        "完整陈述全部论据",
        "intro = 引入 + 问题 + 预告结构；论据留在发展段。"
    ),
    QuizQuestion(
        "« En revanche » 用来表达：",
        listOf("对立与对比", "因果关系", "递进补充", "举例说明"),
        "对立与对比",
        "en revanche 表对立；因此因果要用 donc/c'est pourquoi。"
    ),
    QuizQuestion(
        "标准论证段落的合理结构是：",
        listOf("主题句 – 论据/例子 – 小结或过渡", "例子 – 例子 – 例子", "结论 – 结论 – 结论", "过渡 – 过渡 – 过渡"),
        "主题句 – 论据/例子 – 小结或过渡",
        "段落三件套：annonce / développement / chute-transition。"
    ),
    QuizQuestion(
        "求职信末句 « Je vous prie d'agréer, Madame, l'expression de mes sentiments distingués. » 的作用是：",
        listOf("正式礼貌的结尾套语", "提出录用请求", "重申求职动机", "约定面试地点"),
        "正式礼貌的结尾套语",
        "信函结尾敬语，agréez 类套语为公函规范收尾。"
    )
)
