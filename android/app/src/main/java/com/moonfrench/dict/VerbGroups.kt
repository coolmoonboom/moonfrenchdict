package com.moonfrench.dict

/**
 * 法语动词三大分组数据（需求3）。
 * 第一/二组：5 个时态规则变位。
 * 第三组：动词族列表（含代表性变位）。
 */

data class VerbFamily(
    val name: String,               // 族名，如 "aller 族"
    val description: String,        // 变位特征说明
    val verbs: List<Pair<String, String>>, // (动词, 中文释义)
    val example: String             // 变位示例描述
)

object VerbGroups {

    data class TensePattern(
        val tense: String,
        val forms: List<Pair<String, String>> // (人称, 词尾/示例)
    )

    val firstGroupPatterns = listOf(
        TensePattern("直陈式现在时", listOf(
            "je" to "-e", "tu" to "-es", "il/elle" to "-e",
            "nous" to "-ons", "vous" to "-ez", "ils/elles" to "-ent"
        )),
        TensePattern("未完成过去时", listOf(
            "je" to "-ais", "tu" to "-ais", "il/elle" to "-ait",
            "nous" to "-ions", "vous" to "-iez", "ils/elles" to "-aient"
        )),
        TensePattern("简单将来时", listOf(
            "je" to "-erai", "tu" to "-eras", "il/elle" to "-era",
            "nous" to "-erons", "vous" to "-erez", "ils/elles" to "-eront"
        )),
        TensePattern("虚拟式现在时", listOf(
            "je" to "-e", "tu" to "-es", "il/elle" to "-e",
            "nous" to "-ions", "vous" to "-iez", "ils/elles" to "-ent"
        )),
        TensePattern("命令式", listOf(
            "(tu)" to "-e", "(nous)" to "-ons", "(vous)" to "-ez"
        ))
    )

    val secondGroupPatterns = listOf(
        TensePattern("直陈式现在时", listOf(
            "je" to "-is", "tu" to "-is", "il/elle" to "-it",
            "nous" to "-issons", "vous" to "-issez", "ils/elles" to "-issent"
        )),
        TensePattern("未完成过去时", listOf(
            "je" to "-issais", "tu" to "-issais", "il/elle" to "-issait",
            "nous" to "-issions", "vous" to "-issiez", "ils/elles" to "-issaient"
        )),
        TensePattern("简单将来时", listOf(
            "je" to "-irai", "tu" to "-iras", "il/elle" to "-ira",
            "nous" to "-irons", "vous" to "-irez", "ils/elles" to "-iront"
        )),
        TensePattern("虚拟式现在时", listOf(
            "je" to "-isse", "tu" to "-isses", "il/elle" to "-isse",
            "nous" to "-issions", "vous" to "-issiez", "ils/elles" to "-issent"
        )),
        TensePattern("命令式", listOf(
            "(tu)" to "-is", "(nous)" to "-issons", "(vous)" to "-issez"
        ))
    )

    val thirdGroupFamilies = listOf(
        VerbFamily(
            "助动词", "最核心的两个助动词，用于构成复合时态。",
            listOf("être" to "是；存在", "avoir" to "有；拥有"),
            "je suis / j'ai"
        ),
        VerbFamily(
            "aller 族", "不规则程度高：je vais / nous allons / j'irai。",
            listOf("aller" to "去；走", "s'en aller" to "离开"),
            "je vais / nous allons / j'irai"
        ),
        VerbFamily(
            "faire 族", "je fais / nous faisons / j'ai fait。",
            listOf("faire" to "做；制造", "refaire" to "重做"),
            "je fais / nous faisons / j'ai fait"
        ),
        VerbFamily(
            "情态动词类", "词干在单数形式中常发生元音变化。",
            listOf(
                "pouvoir" to "能够；可以", "vouloir" to "想要；希望",
                "devoir" to "必须；欠", "savoir" to "知道；会"
            ),
            "je peux / je veux / je dois / je sais"
        ),
        VerbFamily(
            "venir 族", "venir → je viens / nous venons / je suis venu。",
            listOf(
                "venir" to "来；到来", "devenir" to "变成；成为",
                "revenir" to "回来；回复", "souvenir" to "记得；想起（se souvenir de）"
            ),
            "je viens / nous venons / je suis venu"
        ),
        VerbFamily(
            "tenir 族", "与 venir 同型：je tiens / nous tenons。",
            listOf("tenir" to "拿着；保持"),
            "je tiens / nous tenons"
        ),
        VerbFamily(
            "prendre 族", "je prends / nous prenons / j'ai pris。",
            listOf(
                "prendre" to "拿；取；乘坐", "comprendre" to "理解；包括",
                "apprendre" to "学习；得知"
            ),
            "je prends / nous prenons / j'ai pris"
        ),
        VerbFamily(
            "mettre 族", "je mets / nous mettons / j'ai mis。",
            listOf(
                "mettre" to "放；穿；花费", "permettre" to "允许；准许",
                "promettre" to "许诺；保证"
            ),
            "je mets / nous mettons / j'ai mis"
        ),
        VerbFamily(
            "dire 族", "je dis / nous disons，注意 vous dites。",
            listOf("dire" to "说；告诉"),
            "je dis / nous disons / vous dites"
        ),
        VerbFamily(
            "lire 族", "je lis / nous lisons / j'ai lu。",
            listOf("lire" to "读；阅读"),
            "je lis / nous lisons / j'ai lu"
        ),
        VerbFamily(
            "écrire 族", "je écris / nous écrivons / j'ai écrit。",
            listOf("écrire" to "写；书写", "décrire" to "描述；描写"),
            "je écris / nous écrivons / j'ai écrit"
        ),
        VerbFamily(
            "voir 族", "je vois / nous voyons / je verrai。",
            listOf("voir" to "看见；看"),
            "je vois / nous voyons / je verrai"
        ),
        VerbFamily(
            "recevoir 族", "je reçois / nous recevons / j'ai reçu。",
            listOf("recevoir" to "收到；接待", "apercevoir" to "察觉；瞥见"),
            "je reçois / nous recevons / j'ai reçu"
        ),
        VerbFamily(
            "connaître 族", "je connais / nous connaissons，注意 â 在词形中的变化。",
            listOf(
                "connaître" to "认识；知道", "reconnaître" to "认出；承认",
                "paraître" to "出现；显得", "apparaître" to "出现；显现"
            ),
            "je connais / nous connaissons"
        ),
        VerbFamily(
            "naître 族", "以 être 作助动词：je suis né。",
            listOf("naître" to "出生；诞生"),
            "je suis né"
        ),
        VerbFamily(
            "vivre / survivre", "je vis / nous vivons / j'ai vécu。",
            listOf("vivre" to "生活；活着", "survivre" to "幸存；活下来"),
            "je vis / nous vivons / j'ai vécu"
        ),
        VerbFamily(
            "suivre / poursuivre", "je suis / nous suivons / j'ai suivi。",
            listOf("suivre" to "跟随；沿着", "poursuivre" to "继续；追捕"),
            "je suis / nous suivons / j'ai suivi"
        ),
        VerbFamily(
            "rire / sourire", "je ris / nous rions / j'ai ri。",
            listOf("rire" to "笑", "sourire" to "微笑"),
            "je ris / nous rions / j'ai ri"
        ),
        VerbFamily(
            "boire / croire", "词干单复数不同：je bois / nous buvons。",
            listOf("boire" to "喝；饮", "croire" to "相信；认为"),
            "je bois / nous buvons"
        ),
        VerbFamily(
            "-uire 族（conduire 型）", "je conduis / nous conduisons / j'ai conduit。",
            listOf(
                "conduire" to "驾驶；带领", "produire" to "生产；产生",
                "construire" to "建造；建立", "traduire" to "翻译",
                "réduire" to "减少；缩小", "détruire" to "摧毁；破坏"
            ),
            "je conduis / nous conduisons / j'ai conduit"
        ),
        VerbFamily(
            "-aindre / -eindre / -oindre 族", "je crains / nous craignons / j'ai craint。",
            listOf(
                "craindre" to "害怕；担心", "peindre" to "画；粉刷",
                "éteindre" to "熄灭；关（灯）", "joindre" to "连接；加上",
                "plaindre" to "同情；抱怨"
            ),
            "je crains / nous craignons / j'ai craint"
        ),
        VerbFamily(
            "vaincre", "je vaincs / nous vainquons / j'ai vaincu。",
            listOf("vaincre" to "战胜；克服"),
            "je vaincs / nous vainquons / j'ai vaincu"
        ),
        VerbFamily(
            "courir / mourir / fuir", "三种不同不规则型：je cours / je meurs / je fuis。",
            listOf("courir" to "跑；奔跑", "mourir" to "死；去世", "fuir" to "逃跑；逃避"),
            "je cours / je meurs / je fuis"
        ),
        VerbFamily(
            "partir 族（-tir / -mir / -vrir）", "单数去掉词干辅音：je pars / nous partons。",
            listOf(
                "partir" to "离开；出发", "sortir" to "出去；拿出", "dormir" to "睡觉",
                "sentir" to "感觉；闻到", "servir" to "服务；有用", "mentir" to "撒谎"
            ),
            "je pars / nous partons"
        ),
        VerbFamily(
            "ouvrir 族", "变位接近第一组：j'ouvre / nous ouvrons，但过去分词特殊。",
            listOf(
                "ouvrir" to "打开", "offrir" to "赠送；提供", "souffrir" to "受苦；忍受",
                "couvrir" to "覆盖；遮盖", "découvrir" to "发现；揭开"
            ),
            "j'ouvre / nous ouvrons"
        ),
        VerbFamily(
            "-re 规则型（vendre 型）", "je vends / nous vendons / j'ai vendu。",
            listOf(
                "vendre" to "卖；出售", "perdre" to "失去；输", "attendre" to "等待",
                "entendre" to "听见；听懂", "répondre" to "回答；回复", "rendre" to "归还；使…成为",
                "descendre" to "下来；下降", "défendre" to "保卫；禁止", "rompre" to "折断；断绝"
            ),
            "je vends / nous vendons / j'ai vendu"
        ),
        VerbFamily(
            "battre 族", "je bats / nous battons / j'ai battu。",
            listOf("battre" to "打；敲"),
            "je bats / nous battons / j'ai battu"
        ),
        VerbFamily(
            "envoyer", "现在时词干 envoi-，将来时 enverr-。",
            listOf("envoyer" to "发送；寄"),
            "j'envoie / j'enverrai"
        ),
        VerbFamily(
            "valoir / pleuvoir / falloir", "valoir 为不规则；pleuvoir 与 falloir 为无人称动词。",
            listOf(
                "valoir" to "价值；值得", "pleuvoir" to "下雨（无人称）",
                "falloir" to "必须；需要（无人称）"
            ),
            "il vaut / il pleut / il faut"
        ),
        VerbFamily(
            "代动词（pronominal）", "se + 动词，复合时态一律用 être：je me suis lavé。",
            listOf(
                "se laver" to "洗；盥洗", "se lever" to "起床；升起", "se coucher" to "躺下；睡觉",
                "se dépêcher" to "赶快", "se souvenir" to "记得；想起",
                "se sentir" to "感到；觉得", "se taire" to "闭嘴；保持沉默",
                "s'appeler" to "名叫；自称"
            ),
            "je me suis lavé"
        )
    )

    fun familyOf(word: String): VerbFamily? {
        val w = word.lowercase().trim()
        for (f in thirdGroupFamilies) {
            for ((v, _) in f.verbs) {
                if (v == w) return f
            }
        }
        return null
    }
}
