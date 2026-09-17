package com.coolmoonfrench.dict

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
        // === 完全无规律 ===
        VerbFamily(
            "助动词", "完全无规律，最核心的两个助动词，用于构成复合时态。",
            listOf("être" to "是；存在", "avoir" to "有；拥有"),
            "je suis / j'ai"
        ),
        VerbFamily(
            "aller 族（-er 不规则）", "以 -er 结尾但属第三组，完全无规律：je vais / nous allons / j'irai。",
            listOf("aller" to "去；走", "s'en aller" to "离开"),
            "je vais / nous allons / j'irai"
        ),
        VerbFamily(
            "envoyer（-er 不规则）", "以 -er 结尾但属第三组：现在时词干 envoi-，将来时 enverr-。",
            listOf("envoyer" to "发送；寄"),
            "j'envoie / j'enverrai"
        ),
        VerbFamily(
            "faire 族", "je fais / nous faisons / j'ai fait，vous 形式 faites 需注意。",
            listOf("faire" to "做；制造", "refaire" to "重做"),
            "je fais / nous faisons / j'ai fait"
        ),

        // === -oir 类 ===
        VerbFamily(
            "-oir 情态动词类", "单数人称词干元音变化（je peux / je veux / je dois / je sais），复数保留词干 + ons/ez。",
            listOf(
                "pouvoir" to "能够；可以", "vouloir" to "想要；希望",
                "devoir" to "必须；欠", "savoir" to "知道；会"
            ),
            "je peux / je veux / je dois / je sais"
        ),
        VerbFamily(
            "-oir 词干变化型（voir / recevoir）", "单数 reçois/vois，复数 recevons/voyons，词干在 oi 与 y/ç 间变化。",
            listOf("voir" to "看见；看", "recevoir" to "收到；接待", "apercevoir" to "察觉；瞥见"),
            "je vois / nous voyons；je reçois / nous recevons"
        ),
        VerbFamily(
            "-enir 族（venir / tenir 型）", "词干单复数交替：je viens / nous venons / ils viennent；tenir 同型。venir 族用 être 作助动词。",
            listOf(
                "venir" to "来；到来", "devenir" to "变成；成为",
                "revenir" to "回来；回复", "souvenir" to "记得；想起（se souvenir de）",
                "tenir" to "拿着；保持"
            ),
            "je viens / nous venons / ils viennent；je tiens / nous tenons"
        ),
        VerbFamily(
            "-endre 特殊型（prendre 族）", "词干 pren-，单数加 -ds：je prends，复数 prenons，第三人称复数 prennent。",
            listOf(
                "prendre" to "拿；取；乘坐", "comprendre" to "理解；包括",
                "apprendre" to "学习；得知"
            ),
            "je prends / nous prenons / ils prennent"
        ),

        // === -ttre / -tir/-mir/-vir / -vrir 型 ===
        VerbFamily(
            "-ttre 型（mettre / battre 族）", "单数去一个 t：je mets / je bats，复数双写 t：nous mettons / nous battons。",
            listOf(
                "mettre" to "放；穿；花费", "permettre" to "允许；准许",
                "promettre" to "许诺；保证", "battre" to "打；敲"
            ),
            "je mets / nous mettons；je bats / nous battons"
        ),
        VerbFamily(
            "-tir / -mir / -vir 型（partir 族）", "单数去掉词干尾辅音：je pars / je dors / je sers，复数保留完整词干。",
            listOf(
                "partir" to "离开；出发", "sortir" to "出去；拿出", "dormir" to "睡觉",
                "sentir" to "感觉；闻到", "servir" to "服务；有用", "mentir" to "撒谎"
            ),
            "je pars / nous partons；je dors / nous dormons"
        ),
        VerbFamily(
            "-vrir / -frir 型（ouvrir 族）", "现在时变位同第一组 -er：j'ouvre / nous ouvrons，但过去分词特殊（-ert）。",
            listOf(
                "ouvrir" to "打开", "offrir" to "赠送；提供", "souffrir" to "受苦；忍受",
                "couvrir" to "覆盖；遮盖", "découvrir" to "发现；揭开"
            ),
            "j'ouvre / nous ouvrons / j'ai ouvert"
        ),

        // === -indre / -uire / -aître 型 ===
        VerbFamily(
            "-aindre / -eindre / -oindre 族", "单数去 -dre：je crains，复数词干加 -gn-：nous craignons。",
            listOf(
                "craindre" to "害怕；担心", "peindre" to "画；粉刷",
                "éteindre" to "熄灭；关（灯）", "joindre" to "连接；加上",
                "plaindre" to "同情；抱怨"
            ),
            "je crains / nous craignons / j'ai craint"
        ),
        VerbFamily(
            "-uire 族（conduire 型）", "单数 je conduis / tu conduis / il conduit，复数 nous conduisons，过去分词 -uit。",
            listOf(
                "conduire" to "驾驶；带领", "produire" to "生产；产生",
                "construire" to "建造；建立", "traduire" to "翻译",
                "réduire" to "减少；缩小", "détruire" to "摧毁；破坏"
            ),
            "je conduis / nous conduisons / j'ai conduit"
        ),
        VerbFamily(
            "-aître 族（connaître / naître 型）", "复数词干加 -ss-：je connais / nous connaissons；naître 以 être 作助动词。",
            listOf(
                "connaître" to "认识；知道", "reconnaître" to "认出；承认",
                "paraître" to "出现；显得", "apparaître" to "出现；显现",
                "naître" to "出生；诞生"
            ),
            "je connais / nous connaissons；je suis né"
        ),

        // === -ire 类 ===
        VerbFamily(
            "-ire 特殊型（dire / lire / écrire）", "单数 je dis / je lis / j'écris（-is/-is/-it），复数词干 + ons/ez/ent；注意 dire 的 vous dites。",
            listOf(
                "dire" to "说；告诉", "lire" to "读；阅读",
                "écrire" to "写；书写", "décrire" to "描述；描写"
            ),
            "je dis / nous disons / vous dites；je lis / j'écris"
        ),
        VerbFamily(
            "-re 规则型（vendre 型）", "规则 -re 变位：je vends / nous vendons，过去分词 -u。",
            listOf(
                "vendre" to "卖；出售", "perdre" to "失去；输", "attendre" to "等待",
                "entendre" to "听见；听懂", "répondre" to "回答；回复", "rendre" to "归还；使…成为",
                "descendre" to "下来；下降", "défendre" to "保卫；禁止", "rompre" to "折断；断绝"
            ),
            "je vends / nous vendons / j'ai vendu"
        ),
        VerbFamily(
            "-re 词干变化型（vivre / suivre / rire / boire / croire）", "单复数词干不同：je vis / nous vivons；je bois / nous buvons；je crois / nous croyons。",
            listOf(
                "vivre" to "生活；活着", "survivre" to "幸存；活下来",
                "suivre" to "跟随；沿着", "poursuivre" to "继续；追捕",
                "rire" to "笑", "sourire" to "微笑",
                "boire" to "喝；饮", "croire" to "相信；认为"
            ),
            "je vis / nous vivons；je bois / nous buvons"
        ),

        // === 单独不规则 ===
        VerbFamily(
            "vaincre", "c/qu 交替：je vaincs / nous vainquons / j'ai vaincu。",
            listOf("vaincre" to "战胜；克服"),
            "je vaincs / nous vainquons / j'ai vaincu"
        ),
        VerbFamily(
            "courir / mourir / fuir", "三种不同不规则型：je cours / je meurs / je fuis。mourir 用 être 作助动词。",
            listOf("courir" to "跑；奔跑", "mourir" to "死；去世", "fuir" to "逃跑；逃避"),
            "je cours / je meurs / je fuis"
        ),
        VerbFamily(
            "valoir / pleuvoir / falloir", "valoir 为不规则（je vaux / nous valons）；pleuvoir 与 falloir 为无人称动词（只变 il 形式）。",
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
