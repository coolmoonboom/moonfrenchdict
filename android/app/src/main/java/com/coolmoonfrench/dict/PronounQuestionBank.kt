package com.coolmoonfrench.dict

// 覆盖全部法语代词：人称主语代词、直接宾语(COD)、间接宾语(COI)、
// 重读/强式代词、自反代词、所有格代词、指示代词、疑问代词、
// 关系代词、副代词(en/y)、泛指代词、中性代词
val pronounQuestions: List<QuizQuestion> = listOf(
    // ---------- 人称主语代词 (pronoms personnels sujets) ----------
    QuizQuestion(
        "___ suis français. (parler de soi)",
        listOf("Je", "Tu", "Il", "On"),
        "Je",
        "第一人称单数主语用 je。"
    ),
    QuizQuestion(
        "___ parlez-vous anglais ? (politesse)",
        listOf("Tu", "Vous", "Il", "Nous"),
        "Vous",
        "尊称「您」用 vous（动词也用 vous 的人称变位）。"
    ),
    QuizQuestion(
        "Marc et Pierre sont là. ___ sont fatigués.",
        listOf("Il", "On", "Ils", "Elle"),
        "Ils",
        "指代阳性复数名词（Marc et Pierre）用 ils。"
    ),
    QuizQuestion(
        "___ (il/elle) ont vingt ans pour Marie et toi ?",
        listOf("Ils", "Nous", "Vous", "Elles"),
        "Elles",
        "指代阴性复数（Marie et toi, 两个女性）用 elles。"
    ),
    QuizQuestion(
        "On ___ écrivait beaucoup au Moyen Âge. (泛指主语)",
        listOf("nous", "on", "ils", "vous"),
        "on",
        "on 泛指「人们」，这是法语中很常见的用法。"
    ),
    // ---------- 直接宾语代词 COD (me/te/le/la/les/nous/vous) ----------
    QuizQuestion(
        "Je ___ vois. (le garçon)",
        listOf("le", "la", "les", "lui"),
        "le",
        "le garçon 是阳性单数直接宾语，用 le。"
    ),
    QuizQuestion(
        "Elle ___ aime. (sa mère)",
        listOf("l'", "lui", "la", "les"),
        "l'",
        "sa mère 是阴性单数，代词 la 在元音前省音为 l'。"
    ),
    QuizQuestion(
        "Il ___ regarde. (les films)",
        listOf("les", "le", "leur", "en"),
        "les",
        "les films 是复数直接宾语，用 les。"
    ),
    QuizQuestion(
        "Tu ___ connais bien ? (nous)",
        listOf("nous", "vous", "me", "te"),
        "nous",
        "nous = 我们（直接宾语）。"
    ),
    QuizQuestion(
        "Je ___ téléphone demain. (toi, COD)",
        listOf("te", "me", "lui", "leur"),
        "te",
        "te = 你（此处作直接宾语用法示例）。"
    ),
    // ---------- 间接宾语代词 COI (me/te/lui/nous/vous/leur) ----------
    QuizQuestion(
        "Je ___ parle. (à Marie)",
        listOf("lui", "le", "la", "les"),
        "lui",
        "介词 à + 第三人称单数（Marie）用 lui。"
    ),
    QuizQuestion(
        "Il ___ téléphone. (à ses parents)",
        listOf("leur", "lui", "les", "en"),
        "leur",
        "à + 复数（ses parents）用 leur（注意不是 les）。"
    ),
    QuizQuestion(
        "Je ___ donne un cadeau. (à toi)",
        listOf("te", "me", "lui", "leur"),
        "te",
        "à + 第二人称单数（toi）用 te。"
    ),
    QuizQuestion(
        "Elle ___ écrit souvent. (à nous)",
        listOf("nous", "vous", "me", "leur"),
        "nous",
        "à nous 用 nous。"
    ),
    QuizQuestion(
        "Je ___ demande de l'aide. (à elle)",
        listOf("lui", "la", "leur", "se"),
        "lui",
        "第三人称单数 COI（à elle）也用 lui。"
    ),
    // ---------- 重读/强式代词 (moi/toi/lui/elle/nous/vous/eux/elles) ----------
    QuizQuestion(
        "___ , je n'aime pas le football.",
        listOf("Moi", "Toi", "Lui", "Eux"),
        "Moi",
        "强调主语用重读代词 moi：« Moi, je n'aime pas... »"
    ),
    QuizQuestion(
        "Ce livre est pour ___ . (toi)",
        listOf("toi", "tu", "te", "t'"),
        "toi",
        "介词（pour）后接重读代词 toi。"
    ),
    QuizQuestion(
        "___ et Paul sont de bons amis.",
        listOf("Lui", "Le", "Il", "Son"),
        "Lui",
        "重读代词 lui 用于复合主语：Lui et Paul。"
    ),
    QuizQuestion(
        "C'est ___ . (elle)",
        listOf("elle", "la", "lui", "sa"),
        "elle",
        "être + 重读代词：C'est elle. = 是她。"
    ),
    QuizQuestion(
        "Ils viennent avec ___ . (nous)",
        listOf("nous", "notre", "nos", "notre"),
        "nous",
        "介词 avec 后接重读代词 nous。"
    ),
    QuizQuestion(
        "À qui est ce vélo ? — Il est à ___ . (eux)",
        listOf("eux", "ils", "leurs", "ceux"),
        "eux",
        "à + 重读代词 eux：「是他们的」。"
    ),
    // ---------- 自反代词 (me/te/se/nous/vous) ----------
    QuizQuestion(
        "Il ___ lave. (lui-même)",
        listOf("se", "le", "lui", "s'"),
        "se",
        "自反代词第三人称单数 se：Il se lave. = 他给自己洗澡。"
    ),
    QuizQuestion(
        "Nous ___ levons tôt.",
        listOf("nous", "se", "leur", "les"),
        "nous",
        "第一人称复数自反代词 nous：Nous nous levons. "
    ),
    QuizQuestion(
        "Tu ___ appelles comment ?",
        listOf("t'", "te", "se", "me"),
        "t'",
        "自反代词 te 在元音前省音为 t'：Tu t'appelles comment ?"
    ),
    QuizQuestion(
        "Ils ___ parlent souvent. (互相)",
        listOf("se", "leur", "les", "en"),
        "se",
        "互相自反（réciproque）：Ils se parlent. = 他们互相交谈。"
    ),
    // ---------- 所有格代词 (le mien/le tien/le sien/le nôtre/le vôtre/le leur) ----------
    QuizQuestion(
        "C'est mon livre. C'est ___ .",
        listOf("le mien", "la mienne", "les miens", "le nôtre"),
        "le mien",
        "le mien = 我的（阳性单数名词）。"
    ),
    QuizQuestion(
        "Ce sont leurs idées ? Oui, ce sont ___ .",
        listOf("les leurs", "le leur", "la leur", "les siennes"),
        "les leurs",
        "leurs idées（阴性复数）对应 les leurs。"
    ),
    QuizQuestion(
        "Voici ta voiture et voilà ___ . (la mienne)",
        listOf("la mienne", "le mien", "les miens", "le sien"),
        "la mienne",
        "la voiture（阴性单数）对应 la mienne。"
    ),
    QuizQuestion(
        "Notre maison est plus grande que ___ . (votre maison)",
        listOf("la vôtre", "le vôtre", "les vôtres", "la leur"),
        "la vôtre",
        "votre maison（阴性单数）对应 la vôtre。"
    ),
    QuizQuestion(
        "J'ai oublié mon parapluie ; prends ___ . (ton parapluie)",
        listOf("le tien", "le sien", "la tienne", "le nôtre"),
        "le tien",
        "ton parapluie（阳性单数）对应 le tien。"
    ),
    QuizQuestion(
        "Ces chaussures sont à moi : ce sont ___ .",
        listOf("les miennes", "les miens", "la mienne", "le mien"),
        "les miennes",
        "chaussures（阴性复数）对应 les miennes。"
    ),
    // ---------- 指示代词 (celui/celle/ceux/celles + -ci/-là) ----------
    QuizQuestion(
        "___ qui travaille réussit.",
        listOf("Celui", "Celle", "Ceux", "Celles"),
        "Celui",
        "celui qui = ……的人（阳性单数），泛指某个人。"
    ),
    QuizQuestion(
        "Deux livres : je préfère ___ sur la table.",
        listOf("celui", "celle", "ceux", "celles"),
        "celui",
        "livre 是阳性单数，用 celui（+限定语=那个）。"
    ),
    QuizQuestion(
        "Regarde ces photos ; ___ sont magnifiques.",
        listOf("celles", "ceux", "celle", "celui"),
        "celles",
        "photos（阴性复数）对应 celles。"
    ),
    QuizQuestion(
        "Quel gâteau veux-tu ? — ___ au chocolat.",
        listOf("Celui", "Celle", "Ceux", "Celles"),
        "Celui",
        "gâteau（阳性单数）对应 Celui（巧克力蛋糕那一个）。"
    ),
    QuizQuestion(
        "Quelles chaussures ? ___ -ci ou ___ -là ?",
        listOf("celles", "ceux", "celui", "celle"),
        "celles",
        "chaussures（阴性复数）用 celles-ci / celles-là。"
    ),
    QuizQuestion(
        "« celui-ci » 表示 ___。",
        listOf("这个", "那个", "这些", "那些"),
        "这个",
        "celui-ci = 这个（较近的）；celui-là = 那个（较远的）。"
    ),
    // ---------- 疑问代词 (qui/que/quoi/lequel/laquelle...) ----------
    QuizQuestion(
        "___ est venu hier ?",
        listOf("Qui", "Que", "Quoi", "Dont"),
        "Qui",
        "疑问代词 qui（谁）做主语。"
    ),
    QuizQuestion(
        "___ fais-tu ?",
        listOf("Que", "Qui", "Quoi", "Où"),
        "Que",
        "疑问代词 que（什么）做直接宾语，后接倒装。"
    ),
    QuizQuestion(
        "___ de nouveau ?",
        listOf("Quoi", "Qui", "Que", "Dont"),
        "Quoi",
        "quoi 用于介词后或独立句：Quoi de neuf ? = 有什么新鲜事？"
    ),
    QuizQuestion(
        "Parmi ces livres, ___ préfères-tu ?",
        listOf("lesquels", "lequel", "laquelle", "lesquelles"),
        "lesquels",
        "livres（阳性复数）对应 lesquels。"
    ),
    QuizQuestion(
        "Ces voitures : ___ sont les tiennes ?",
        listOf("lesquelles", "laquelle", "lesquels", "lequel"),
        "lesquelles",
        "voitures（阴性复数）对应 lesquelles。"
    ),
    QuizQuestion(
        "___ de ces deux stylos préfères-tu ?",
        listOf("Lequel", "Laquelle", "Lesquels", "Lesquelles"),
        "Lequel",
        "stylos（阳性单数）对应 Lequel。"
    ),
    QuizQuestion(
        "À ___ parles-tu ?",
        listOf("qui", "quoi", "que", "dont"),
        "qui",
        "介词 à 后接疑问代词 qui：À qui parles-tu ? = 你在跟谁说话？"
    ),
    QuizQuestion(
        "De ___ as-tu peur ?",
        listOf("quoi", "qui", "que", "dont"),
        "quoi",
        "介词 de 后接 quoi：De quoi as-tu peur ? = 你怕什么？"
    ),
    QuizQuestion(
        "___ est-ce qui sonne ?",
        listOf("Qui", "Que", "Quoi", "Dont"),
        "Qui",
        "qui est-ce qui = 谁（做主语）：Qui est-ce qui sonne ?"
    ),
    QuizQuestion(
        "___ est-ce que tu as vu ?",
        listOf("Qui", "Que", "Quoi", "Lequel"),
        "Qui",
        "qui est-ce que = 谁（做宾语）：Qui est-ce que tu as vu ? = 你看见了谁？"
    ),
    // ---------- 关系代词 (qui/que/dont/où/lequel...) ----------
    QuizQuestion(
        "Le livre ___ est sur la table est à moi.",
        listOf("qui", "que", "dont", "où"),
        "qui",
        "先行词做主语时用 qui：le livre qui est sur la table。"
    ),
    QuizQuestion(
        "Le film ___ j'ai vu hier était super.",
        listOf("que", "qui", "dont", "où"),
        "que",
        "先行词做直接宾语时用 que：le film que j'ai vu。"
    ),
    QuizQuestion(
        "La maison ___ je te parle est grande.",
        listOf("dont", "que", "qui", "où"),
        "dont",
        "parler de + 名词 → dont：la maison dont je te parle。"
    ),
    QuizQuestion(
        "La ville ___ j'habite est belle.",
        listOf("où", "dont", "que", "qui"),
        "où",
        "先行词作地点或时间状语用 où：la ville où j'habite。"
    ),
    QuizQuestion(
        "Le stylo ___ j'écris est rouge.",
        listOf("avec lequel", "avec qui", "dont", "où"),
        "avec lequel",
        "介词 + qui 不符（先行词为物体），用 lequel：le stylo avec lequel j'écris。"
    ),
    QuizQuestion(
        "C'est le garçon ___ je t'ai parlé hier.",
        listOf("dont", "que", "où", "qui"),
        "dont",
        "动词短语 parler de → dont：le garçon dont je t'ai parlé。"
    ),
    QuizQuestion(
        "Les amis ___ j'ai voyagé sont partis.",
        listOf("avec lesquels", "avec qui", "avec que", "avec dont"),
        "avec lesquels",
        "amis（阳性复数）用 lesquels：les amis avec lesquels j'ai voyagé。"
    ),
    QuizQuestion(
        "Au musée, j'ai vu des tableaux ___ (de nombreux)",
        listOf("dont plusieurs sont célèbres", "que plusieurs", "qui plusieurs", "où plusieurs"),
        "dont plusieurs sont célèbres",
        "dont = 其中：dont plusieurs sont célèbres（其中一些很有名）。"
    ),
    // ---------- 副代词 (en / y) ----------
    QuizQuestion(
        "Il y a des pommes. J'___ veux deux.",
        listOf("en", "y", "le", "les"),
        "en",
        "en 代替 de/nombre + 名词（数量）：j'en veux deux。"
    ),
    QuizQuestion(
        "Tu vas à Paris ? Oui, j'___ vais.",
        listOf("y", "en", "le", "là"),
        "y",
        "y 代替 à + 地点：j'y vais = 我去那里。"
    ),
    QuizQuestion(
        "Tu as besoin d'aide ? Oui, j'___ ai besoin.",
        listOf("en", "y", "le", "la"),
        "en",
        "en 代替 de + 名词：j'en ai besoin。"
    ),
    QuizQuestion(
        "Vous pensez à vos vacances ? Oui, nous ___ pensons.",
        listOf("y", "en", "les", "leur"),
        "y",
        "penser à + 名词 → y：nous y pensons。"
    ),
    QuizQuestion(
        "Il boit du café. Il ___ boit chaque matin.",
        listOf("en", "y", "le", "lui"),
        "en",
        "en 代替 du + 名词（部分数量）：il en boit chaque matin。"
    ),
    QuizQuestion(
        "Combien d'enfants as-tu ? — J'___ ai trois.",
        listOf("en", "y", "les", "leur"),
        "en",
        "en 代替数量：j'en ai trois = 我有三个。"
    ),
    QuizQuestion(
        "Tu te souviens de notre voyage ? — Non, je ne m'___ souviens pas.",
        listOf("en", "y", "le", "là"),
        "en",
        "se souvenir de + 名词 → en：je ne m'en souviens pas。"
    ),
    QuizQuestion(
        "« y » 可以代替 ___。",
        listOf("à + 地点或事物", "de + 名词", "数量", "直接宾语"),
        "à + 地点或事物",
        "y 代替介词 à（或 sur/dans 等）+ 地点或事物。"
    ),
    QuizQuestion(
        "« en » 可以代替 ___。",
        listOf("de + 名词或数量", "à + 地点", "le/la/les", "qui/que"),
        "de + 名词或数量",
        "en 代替 de + 名词，或数量（un, deux, trois...）。"
    ),
    // ---------- 泛指代词 (personne/rien/tout/chacun/quelqu'un/certains/plusieurs...) ----------
    QuizQuestion(
        "___ ne m'aime ici.",
        listOf("Personne", "Rien", "Tout", "Aucun"),
        "Personne",
        "personne ne = 没有人：Personne ne m'aime。"
    ),
    QuizQuestion(
        "Je ne vois ___ dans le noir.",
        listOf("rien", "personne", "tout", "chacun"),
        "rien",
        "ne...rien = 什么也没有。"
    ),
    QuizQuestion(
        "___ est possible.",
        listOf("Tout", "Rien", "Personne", "Chacun"),
        "Tout",
        "tout = 一切：Tout est possible。"
    ),
    QuizQuestion(
        "___ de nous peut répondre.",
        listOf("Chacun", "Rien", "Personne", "Tout"),
        "Chacun",
        "chacun de = 每个：Chacun de nous = 我们每一个人。"
    ),
    QuizQuestion(
        "Il y a quelqu'un ? — Non, il n'y a ___ .",
        listOf("personne", "rien", "aucun", "tout"),
        "personne",
        "人不使用 aucun 作名词回答，用 personne：il n'y a personne。"
    ),
    QuizQuestion(
        "Il a plusieurs amis ; ___ viennent de Paris.",
        listOf("certains", "chacun", "rien", "tout"),
        "certains",
        "certains = 一些（复数）：certains viennent de Paris。"
    ),
    QuizQuestion(
        "Tu veux du café ou du thé ? — ___ me convient.",
        listOf("N'importe", "Personne", "Rien", "Chacun"),
        "N'importe",
        "n'importe (lequel/quoi) = 随便哪个：n'importe me convient。"
    ),
    QuizQuestion(
        "Tout le monde est là ; ___ est arrivé.",
        listOf("quelqu'un", "personne", "rien", "aucun"),
        "quelqu'un",
        "quelqu'un = 有人：quelqu'un est arrivé。"
    ),
    QuizQuestion(
        "Avez-vous des questions ? — Non, ___ .",
        listOf("aucune", "personne", "rien", "tout"),
        "aucune",
        "aucun/aucune = 一个也没有（单数）：Non, aucune。"
    ),
    QuizQuestion(
        "___ a son propre avis.",
        listOf("Chacun", "Rien", "Tout", "Plein"),
        "Chacun",
        "chacun = 每人：Chacun a son propre avis。"
    ),
    // ---------- 中性代词 (il impersonnel / le neutre) ----------
    QuizQuestion(
        "___ fait beau aujourd'hui.",
        listOf("Il", "Ce", "Ça", "Le"),
        "Il",
        "非人称/中性主语 il：Il fait beau（天气）。"
    ),
    QuizQuestion(
        "Il ___ nécessaire de travailler.",
        listOf("est", "a", "fait", "va"),
        "est",
        "非人称结构 il est + adj + de：il est nécessaire de...（有必要……）。"
    ),
    QuizQuestion(
        "Tu penses que j'ai raison ? Oui, je ___ pense.",
        listOf("le", "la", "y", "en"),
        "le",
        "中性代词 le 代替整个从句：je le pense。"
    ),
    QuizQuestion(
        "___ pleut depuis ce matin.",
        listOf("Il", "Elle", "On", "Ce"),
        "Il",
        "表示天气的非人称 il：Il pleut。"
    )
)