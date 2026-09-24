package com.coolmoonfrench.dict

/** A2 · 形容词与过去分词的性数配合 */
val a2Agreement: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Sa sœur est très ___.（漂亮的）",
        listOf("beau", "belle", "beaux", "belles"),
        "belle",
        "阴性单数主语的表语形容词：beau → belle。"
    ),
    QuizQuestion(
        "Les fleurs sont ___.（白色的）",
        listOf("blanc", "blanche", "blancs", "blanches"),
        "blanches",
        "fleurs 为阴性复数，blanc → blanche → blanches。"
    ),
    QuizQuestion(
        "Elle est ___ à Paris.（她到过巴黎）",
        listOf("allé", "allée", "allés", "allées"),
        "allée",
        "以 être 作助动词，过去分词与主语性数配合：阴性单数加 e。"
    ),
    QuizQuestion(
        "Ils sont ___ ce matin.（他们出发了）",
        listOf("parti", "partie", "partis", "parties"),
        "partis",
        "partir 用 être 作助动词，与阳性复数主语配合加 s。"
    ),
    QuizQuestion(
        "Ma mère a ___ une robe ___.（买了一条黑色的裙子）",
        listOf("acheté / noir", "achetée / noire", "acheté / noire", "achetées / noires"),
        "acheté / noire",
        "avoir 作助动词时过去分词不与主语配合；noire 与阴性名词 robe 配合。"
    ),
    QuizQuestion(
        "Ce sont des vacances ___.（令人放松的假期）",
        listOf("reposant", "reposante", "reposants", "reposantes"),
        "reposantes",
        "vacances 为阴性复数，现在分词作形容词要配合。"
    ),
    QuizQuestion(
        "Elle est ___ et ___.（她又高又漂亮）",
        listOf("grand / beau", "grande / belle", "grande / beau", "grandes / belles"),
        "grande / belle",
        "两个表语形容词都要与阴性单数主语配合。"
    ),
    QuizQuestion(
        "Nous avons ___ nos vieilles amies.（我们见到了老朋友）",
        listOf("vu", "vus", "vues", "vue"),
        "vu",
        "avoir 作助动词，直接宾语 nos amies 位于过去分词之后时，过去分词不配合：avons vu。"
    )
)

/** A2 · 直接宾语与间接宾语人称代词 */
val a2PronounObjects: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Tu aimes ce film ? — Oui, je ___ regarde souvent.",
        listOf("le", "la", "lui", "y"),
        "le",
        "ce film（阳性单数事物）作直接宾语，用 COD 代词 le。"
    ),
    QuizQuestion(
        "Tu connais cette chanson ? — Oui, je ___ connais.",
        listOf("la", "le", "lui", "en"),
        "la",
        "cette chanson 阴性单数直接宾语 → la；元音前才变 l'。"
    ),
    QuizQuestion(
        "Je ___ téléphone ce soir.（我今晚给她打电话）",
        listOf("la", "le", "lui", "lui/la"),
        "lui",
        "téléphoner à qqn 是间接动词，第三人称间接宾语用 lui（男女同形）。"
    ),
    QuizQuestion(
        "Tu parles à tes parents ? — Oui, je ___ parle tous les jours.",
        listOf("les", "leur", "leur/eux", "y"),
        "leur",
        "parler à qqn（复数）间接宾语用 leur，注意与定冠词 les 区分。"
    ),
    QuizQuestion(
        "Il m'___ son frère.（他给我介绍了他哥哥）",
        listOf("a présenté", "a présenté à", "présenté", "est présenté"),
        "a présenté",
        "présenter qqn à qqn；me 已承担间接宾语，结构为 il m'a présenté son frère。"
    ),
    QuizQuestion(
        "Vous voudriez parler à ___ ?（您想和他谈谈吗——强调「他本人」）",
        listOf("il", "lui", "le", "on"),
        "lui",
        "介词 à 之后指人要用重读人称代词：parler à lui（强调「他本人」）；句中普通间接宾语也用 lui。"
    ),
    QuizQuestion(
        "Ne ___ oublie pas, cette adresse !（别忘了这个地址）",
        listOf("la", "le", "l'", "lui"),
        "l'",
        "oublier qqn/qqch 直接宾语 la，省音在元音前：ne l'oublie pas。"
    ),
    QuizQuestion(
        "C'est à moi ? — Oui, c'est à ___.（是你的）",
        listOf("toi", "tu", "te", "ton"),
        "toi",
        "介词 à 后用重读人称代词 toi。"
    )
)

/** A2 · 比较级与最高级 */
val a2Comparison: List<QuizQuestion> = listOf(
    QuizQuestion(
        "Paris est ___ grande que Lyon.（巴黎比里昂大）",
        listOf("plus", "moins", "aussi", "très"),
        "plus",
        "比较级：plus + 形容词 + que 表示「比…更」。"
    ),
    QuizQuestion(
        "Ce film est ___ intéressant que le livre.（电影不如书有趣）",
        listOf("moins", "plus", "aussi", "au moins"),
        "moins",
        "moins + adj + que 表示「不如…」；也可说 moins intéressant。"
    ),
    QuizQuestion(
        "Marie est ___ intelligente ___ sa sœur.（和姐姐一样聪明）",
        listOf("aussi / que", "autant / que", "si / que", "tellement / que"),
        "aussi / que",
        "形容词同比较用 aussi + adj + que（autant 修饰动词）。"
    ),
    QuizQuestion(
        "C'est le ___ film de l'année.（今年最好的电影）",
        listOf("meilleur", "mieux", "plus bon", "bon"),
        "meilleur",
        "bon 的比较级特殊：bon → meilleur；最高级 le meilleur。"
    ),
    QuizQuestion(
        "Il travaille ___ que toi.（他工作比你努力——用副词）",
        listOf("plus dur", "plus difficile", "plus dur que", "le plus"),
        "plus dur",
        "副词 dur 构成比较 plus dur que。"
    ),
    QuizQuestion(
        "C'est la ___ belle ville de France.（这是法国最美丽的城市）",
        listOf("plus", "la plus", "moins", "très"),
        "plus",
        "最高级 le/la + plus + 形容词；le 已在空格外：la ___ belle → plus。"
    ),
    QuizQuestion(
        "Elle chante ___ bien de toutes les élèves.（她全场唱得最好）",
        listOf("le mieux", "le plus bien", "le plus mieux", "mieux"),
        "le mieux",
        "bien 的比较最高级特殊形式：bien → mieux → le mieux。"
    ),
    QuizQuestion(
        "Autant d'étudiants ___ l'an dernier.（和去年一样多的学生）",
        listOf("que", "comme", "aussi", "de"),
        "que",
        "autant de + 名词 + que 表示数量同样多。"
    )
)

/** A2 · 关系代词 qui / que / dont / où */
val a2Relative: List<QuizQuestion> = listOf(
    QuizQuestion(
        "C'est l'étudiant ___ a réussi l'examen.",
        listOf("qui", "que", "dont", "où"),
        "qui",
        "关系代词作主语用 qui：l'étudiant qui a réussi。"
    ),
    QuizQuestion(
        "Le film ___ nous avons vu est intéressant.",
        listOf("qui", "que", "dont", "où"),
        "que",
        "关系代词在从句中作直接宾语用 que：Le film que nous avons vu。"
    ),
    QuizQuestion(
        "C'est la professeure ___ je parle souvent.",
        listOf("qui", "que", "dont", "à qui"),
        "à qui",
        "parler à qqn：先行词是人且带 à 时用 à qui（物则用 dont）。"
    ),
    QuizQuestion(
        "Le livre ___ je t'ai parlé est épuisé.",
        listOf("qui", "que", "dont", "où"),
        "dont",
        "parler de qch → dont 代替 de + 先行词。"
    ),
    QuizQuestion(
        "Voici la gare ___ le train arrive.",
        listOf("qui", "que", "dont", "où"),
        "où",
        "先行词表示地点且在从句中作地点状语时用 où。"
    ),
    QuizQuestion(
        "Le jour ___ nous partons sera ensoleillé.",
        listOf("où", "que", "qui", "dont"),
        "où",
        "时间先行词在从句中作时间状语也用 où。"
    ),
    QuizQuestion(
        "C'est un ami ___ je peux tout dire.（什么都能跟他说）",
        listOf("qui", "que", "dont", "à qui"),
        "à qui",
        "dire qqch à qqn：先行词为人且间接宾语时用 à qui。"
    ),
    QuizQuestion(
        "La raison ___ il est en retard est simple.（他迟到的原因）",
        listOf("pour laquelle", "que", "qui", "dont"),
        "pour laquelle",
        "la raison pour laquelle（为…的原因），复合关系代词与先行词性数配合。"
    )
)
