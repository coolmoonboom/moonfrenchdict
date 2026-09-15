package com.coolmoonfrench.dict

/** 语法学习静态知识库。词法 / 动词 / 句法三大板块，每板块含若干主题，主题由若干讲解段落构成。 */

data class GrammarExample(
    val fr: String,
    val zh: String
)

data class GrammarSection(
    val heading: String? = null,
    val text: String? = null,
    val bullets: List<String> = emptyList(),
    val examples: List<GrammarExample> = emptyList(),
    val note: String? = null
)

data class GrammarTopic(
    val title: String,
    val fr: String,
    val summary: String,
    val sections: List<GrammarSection>
)

data class GrammarCategory(
    val title: String,
    val fr: String,
    val intro: String,
    val topics: List<GrammarTopic>
)

object GrammarContent {

    val categories: List<GrammarCategory> = listOf(
        GrammarCategory(
            title = "词法",
            fr = "La morphologie",
            intro = "研究词的构成、词形变化与十大词类：名词、冠词、形容词、代词、动词、副词、介词、连词、数词、感叹词。",
            topics = listOf(
                GrammarTopic(
                    title = "名词的性与数",
                    fr = "Le nom : genre et nombre",
                    summary = "法语名词分阴阳性，有单复数变化；掌握常见词尾规律可帮助判断。",
                    sections = listOf(
                        GrammarSection(
                            heading = "阴阳性",
                            text = "法语每个名词都有固定的语法性别，冠词和形容词都要与之配合。",
                            bullets = listOf(
                                "常见阴性词尾：-tion, -sion, -té, -ité, -ette, -ance, -ence",
                                "常见阳性词尾：-ment, -age, -eau, -isme, -ier, -oir",
                                "以 -e 结尾的名词不一定是阴性（le livre, le musée）",
                                "表示职业、国籍的名词阴阳性常成对出现"
                            ),
                            examples = listOf(
                                GrammarExample("la nation, la liberté, la maison", "民族、自由、房子（阴性）"),
                                GrammarExample("le gouvernement, le voyage, le bureau", "政府、旅行、办公桌（阳性）")
                            )
                        ),
                        GrammarSection(
                            heading = "复数构成",
                            bullets = listOf(
                                "一般规则：词尾加 -s（un livre → des livres）",
                                "以 -s, -x, -z 结尾：单复数同形（le pays → les pays）",
                                "-al → -aux（un journal → des journaux）",
                                "-eau, -au, -eu → 加 -x（un bateau → des bateaux）",
                                "-ou 一般加 -s，但有 7 个词加 -x：bijou, caillou, chou, genou, hibou, joujou, pou"
                            ),
                            examples = listOf(
                                GrammarExample("un cheval → des chevaux", "一匹马 → 一些马"),
                                GrammarExample("un genou → des genoux", "一个膝盖 → 一些膝盖")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "冠词",
                    fr = "Les articles",
                    summary = "定冠词、不定冠词与部分冠词，均需与名词的性、数一致。",
                    sections = listOf(
                        GrammarSection(
                            heading = "定冠词",
                            text = "指已知、特指或泛指整体的事物。",
                            bullets = listOf(
                                "阳性单数 le，阴性单数 la，元音或哑音 h 前用 l'，复数 les",
                                "与介词 à 缩合：à + le = au，à + les = aux",
                                "与介词 de 缩合：de + le = du，de + les = des"
                            ),
                            examples = listOf(
                                GrammarExample("Le livre est sur la table.", "书在桌子上。"),
                                GrammarExample("Je parle aux étudiants.", "我和学生们说话。")
                            )
                        ),
                        GrammarSection(
                            heading = "不定冠词与部分冠词",
                            bullets = listOf(
                                "不定冠词：un（阳）、une（阴）、des（复），指不确指的可数名词",
                                "部分冠词：du / de la / de l'，用于不可数名词，表示部分数量",
                                "否定句中，不定冠词与部分冠词通常变为 de"
                            ),
                            examples = listOf(
                                GrammarExample("J'ai un chat et une chienne.", "我有一只公猫和一只母狗。"),
                                GrammarExample("Je bois du café et de l'eau.", "我喝咖啡和水。"),
                                GrammarExample("Je n'ai pas de café.", "我没有咖啡。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "形容词的性数配合与位置",
                    fr = "L'adjectif",
                    summary = "形容词必须与所修饰名词的性和数一致，且位置有前后之分。",
                    sections = listOf(
                        GrammarSection(
                            heading = "性数配合",
                            bullets = listOf(
                                "一般：阴性加 -e，复数加 -s（petit → petite → petits → petites）",
                                "-eux → -euse；-f → -ve；-er → -ère；-ien → -ienne",
                                "特殊：beau/belle, nouveau/nouvelle, vieux/vieille, blanc/blanche"
                            ),
                            examples = listOf(
                                GrammarExample("un garçon intelligent / une fille intelligente", "一个聪明的男孩 / 女孩"),
                                GrammarExample("un vieux monsieur / une vieille dame", "一位老先生 / 老太太")
                            )
                        ),
                        GrammarSection(
                            heading = "位置",
                            bullets = listOf(
                                "多数形容词置于名词后：une voiture rouge",
                                "少数常用短形容词前置：beau, joli, jeune, vieux, grand, petit, nouveau, bon, mauvais, premier, dernier",
                                "形容词前置时，des 在元音/哑音 h 前变为 de：de beaux arbres"
                            ),
                            examples = listOf(
                                GrammarExample("une belle maison blanche", "一栋漂亮的白色房子"),
                                GrammarExample("un petit problème difficile", "一个小而难的问题")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "比较级与最高级",
                    fr = "Le comparatif et le superlatif",
                    summary = "表示比较与最高程度，形容词和副词各有规则形式与不规则形式。",
                    sections = listOf(
                        GrammarSection(
                            heading = "比较级",
                            bullets = listOf(
                                "较高：plus + 形容词/副词 + que",
                                "较低：moins + 形容词/副词 + que",
                                "相等：aussi + 形容词/副词 + que",
                                "不规则：bon → meilleur，mauvais → pire，bien → mieux"
                            ),
                            examples = listOf(
                                GrammarExample("Il est plus grand que moi.", "他比我高。"),
                                GrammarExample("Ce vin est meilleur que l'autre.", "这瓶酒比另一瓶好。")
                            )
                        ),
                        GrammarSection(
                            heading = "最高级",
                            bullets = listOf(
                                "定冠词 + plus / moins + 形容词：le/la/les plus grand(e)(s)",
                                "副词最高级用 le plus / le moins，不随性数变化",
                                "不规则：le meilleur（最好的），le mieux（最好地）"
                            ),
                            examples = listOf(
                                GrammarExample("C'est le meilleur film de l'année.", "这是今年最好的电影。"),
                                GrammarExample("Elle travaille le mieux.", "她工作得最好。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "副词",
                    fr = "L'adverbe",
                    summary = "副词修饰动词、形容词或其他副词，多数由形容词加 -ment 构成。",
                    sections = listOf(
                        GrammarSection(
                            heading = "构成",
                            bullets = listOf(
                                "阳性形容词变阴性后加 -ment：lent → lente → lentement",
                                "以元音结尾的形容词直接加 -ment：vrai → vraiment",
                                "-ant → -amment：courant → couramment",
                                "-ent → -emment：récent → récemment"
                            ),
                            examples = listOf(
                                GrammarExample("Il parle lentement et clairement.", "他讲得又慢又清楚。"),
                                GrammarExample("Elle répond poliment.", "她礼貌地回答。")
                            )
                        ),
                        GrammarSection(
                            heading = "常见副词与位置",
                            bullets = listOf(
                                "时间：hier, aujourd'hui, demain, souvent, toujours, déjà, encore",
                                "地点：ici, là, partout, dehors, loin, près",
                                "方式：bien, mal, vite, ensemble",
                                "程度：très, trop, assez, beaucoup, peu, presque",
                                "简单时态置于变位动词后；复合时态置于助动词与过去分词之间"
                            ),
                            examples = listOf(
                                GrammarExample("Je vais souvent au cinéma.", "我常去电影院。"),
                                GrammarExample("Il a déjà fini.", "他已经完成了。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "介词",
                    fr = "La préposition",
                    summary = "介词连接词与词，表示地点、时间、方式、所属等关系，搭配需记忆。",
                    sections = listOf(
                        GrammarSection(
                            heading = "地点介词",
                            bullets = listOf(
                                "城市用 à：à Paris；阴性国家/大洲用 en：en France, en Asie",
                                "阳性国家用 au：au Japon；复数国家用 aux：aux États-Unis",
                                "dans 表示在某空间内，sur 表示在表面，chez 表示在某人处"
                            ),
                            examples = listOf(
                                GrammarExample("Je vais en France et au Canada.", "我去法国和加拿大。"),
                                GrammarExample("Il habite chez ses parents.", "他住在父母家。")
                            )
                        ),
                        GrammarSection(
                            heading = "常用动词搭配",
                            bullets = listOf(
                                "penser à / parler de / dépendre de / s'intéresser à",
                                "jouer à + 运动，jouer de + 乐器",
                                "commencer à + 不定式，essayer de + 不定式"
                            ),
                            examples = listOf(
                                GrammarExample("Je pense souvent à toi.", "我常常想起你。"),
                                GrammarExample("Elle joue du piano.", "她弹钢琴。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "连词",
                    fr = "La conjonction",
                    summary = "连词连接词、短语或句子，分并列连词与从属连词两大类。",
                    sections = listOf(
                        GrammarSection(
                            heading = "并列连词",
                            text = "连接地位平等的成分：et（和）、ou（或）、mais（但是）、donc（因此）、or（然而）、ni（也不）、car（因为）。",
                            examples = listOf(
                                GrammarExample("Il est fatigué mais il continue.", "他累了，但仍在继续。"),
                                GrammarExample("Tu veux du thé ou du café ?", "你想要茶还是咖啡？")
                            )
                        ),
                        GrammarSection(
                            heading = "从属连词",
                            bullets = listOf(
                                "原因：parce que, puisque, comme, car",
                                "时间：quand, lorsque, pendant que, avant que, après que",
                                "目的与结果：pour que, afin que, si bien que",
                                "让步：bien que, quoique, même si",
                                "条件：si, à condition que"
                            ),
                            examples = listOf(
                                GrammarExample("Je reste parce qu'il pleut.", "因为下雨，我留下。"),
                                GrammarExample("Bien qu'il soit tard, il travaille.", "尽管很晚了，他还在工作。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "数词",
                    fr = "Les nombres",
                    summary = "基数词与序数词，注意 70、80、90 的特殊表达与 -s 变化。",
                    sections = listOf(
                        GrammarSection(
                            heading = "基数词要点",
                            bullets = listOf(
                                "1-16 独立成词，17-19 为 dix-sept, dix-huit, dix-neuf",
                                "70 soixante-dix，71 soixante et onze，80 quatre-vingts，90 quatre-vingt-dix",
                                "81 quatre-vingt-un（不加 et），91 quatre-vingt-onze",
                                "quatre-vingts 后接其他数词时去掉 -s：quatre-vingt-cinq"
                            ),
                            examples = listOf(
                                GrammarExample("soixante-douze, quatre-vingt-trois, quatre-vingt-dix-neuf", "72、83、99"),
                                GrammarExample("J'ai quatre-vingts euros.", "我有 80 欧元。")
                            )
                        ),
                        GrammarSection(
                            heading = "序数词",
                            text = "一般由基数词加 -ième 构成，注意以 -e 结尾时去 e，以及特殊形式。",
                            examples = listOf(
                                GrammarExample("premier/première, deuxième, cinquième, neuvième", "第一、第二、第五、第九"),
                                GrammarExample("C'est le premier jour du mois.", "这是这个月的第一天。")
                            )
                        )
                    )
                )
            )
        ),
        GrammarCategory(
            title = "动词",
            fr = "Le verbe",
            intro = "动词是法语句子的核心，需掌握分组、时态、语式、助动词与语态配合。",
            topics = listOf(
                GrammarTopic(
                    title = "动词分组与规则变位",
                    fr = "Les trois groupes",
                    summary = "法语动词按不定式词尾分为三组，前两组变位规则，第三组需个别记忆。",
                    sections = listOf(
                        GrammarSection(
                            heading = "三组动词",
                            bullets = listOf(
                                "第一组：词尾 -er（除 aller），占绝大多数，变位最规则",
                                "第二组：词尾 -ir 且现在分词为 -issant（finir → finissant）",
                                "第三组：其余不规则动词，包括 aller, être, avoir, faire, prendre 等"
                            ),
                            examples = listOf(
                                GrammarExample("parler, finir, prendre", "第一组、第二组、第三组示例")
                            )
                        ),
                        GrammarSection(
                            heading = "现在时词尾",
                            bullets = listOf(
                                "第一组：-e, -es, -e, -ons, -ez, -ent（parler → je parle, nous parlons）",
                                "第二组：-is, -is, -it, -issons, -issez, -issent（finir → je finis, nous finissons）",
                                "注意 -er 动词词干变化：appeler → j'appelle，commencer → je commence"
                            ),
                            examples = listOf(
                                GrammarExample("Je parle français. / Nous finissons le travail.", "我说法语。/ 我们完成工作。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "现在时",
                    fr = "Le présent",
                    summary = "描述当前正在发生、习惯性动作、普遍真理，也可表示近期将来。",
                    sections = listOf(
                        GrammarSection(
                            heading = "用法",
                            bullets = listOf(
                                "当前正在发生的动作：Il mange une pomme.",
                                "习惯或反复的动作：Je vais au travail à vélo.",
                                "普遍真理：La Terre tourne autour du Soleil.",
                                "近期将来（口语）：Je pars demain matin."
                            ),
                            examples = listOf(
                                GrammarExample("En ce moment, elle lit un roman.", "此刻她正在读一本小说。"),
                                GrammarExample("Nous dînons à sept heures.", "我们七点吃晚饭。")
                            )
                        ),
                        GrammarSection(
                            heading = "常见不规则变位",
                            bullets = listOf(
                                "être：je suis, tu es, il est, nous sommes, vous êtes, ils sont",
                                "avoir：j'ai, tu as, il a, nous avons, vous avez, ils ont",
                                "aller：je vais, tu vas, il va, nous allons, vous allez, ils vont",
                                "faire：je fais, tu fais, il fait, nous faisons, vous faites, ils font"
                            ),
                            examples = listOf(
                                GrammarExample("J'ai un rendez-vous et je vais à la gare.", "我有个约会，然后我去火车站。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "复合过去时",
                    fr = "Le passé composé",
                    summary = "最常用的过去时，由助动词现在时加过去分词构成，表示已完成的动作。",
                    sections = listOf(
                        GrammarSection(
                            heading = "构成与助动词选择",
                            bullets = listOf(
                                "结构：avoir / être 的现在时 + 过去分词",
                                "多数动词用 avoir；代动词和一小类表示位移或状态变化的动词用 être",
                                "用 être 的常见动词：aller, venir, arriver, partir, entrer, sortir, monter, descendre, naître, mourir, rester, tomber"
                            ),
                            examples = listOf(
                                GrammarExample("J'ai mangé une pomme.", "我吃了一个苹果。"),
                                GrammarExample("Elle est partie hier soir.", "她昨晚离开了。")
                            )
                        ),
                        GrammarSection(
                            heading = "过去分词与性数配合",
                            bullets = listOf(
                                "规则分词：-er → -é，-ir → -i，-re → -u（parlé, fini, vendu）",
                                "用 être 时，过去分词与主语性数一致：elle est partie, ils sont partis",
                                "用 avoir 时，若直接宾语在动词前，分词与该宾语性数一致：les fleurs que j'ai cueillies"
                            ),
                            examples = listOf(
                                GrammarExample("Nous sommes arrivés à midi.", "我们中午到达。"),
                                GrammarExample("La lettre que j'ai écrite est partie.", "我写的那封信已寄出。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "未完成过去时",
                    fr = "L'imparfait",
                    summary = "表示过去持续、重复或描写性的动作，常与复合过去时对比使用。",
                    sections = listOf(
                        GrammarSection(
                            heading = "构成",
                            bullets = listOf(
                                "以 nous 现在时词干加词尾：-ais, -ais, -ait, -ions, -iez, -aient",
                                "être 是唯一不规则：j'étais, tu étais, il était, nous étions...",
                                "词干以 -g 结尾时保留 e 以保持发音：nous mangions"
                            ),
                            examples = listOf(
                                GrammarExample("Quand j'étais petit, je jouais au foot.", "我小时候常踢足球。")
                            )
                        ),
                        GrammarSection(
                            heading = "与复合过去时的对比",
                            bullets = listOf(
                                "未完成过去时：背景、描写、习惯、正在进行的动作",
                                "复合过去时：一次性的、完成并切断过去的动作",
                                "叙事中常配合：Il pleuvait（背景） quand je suis sorti（事件）"
                            ),
                            examples = listOf(
                                GrammarExample("Je lisais quand le téléphone a sonné.", "电话响时我正在看书。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "将来时",
                    fr = "Le futur",
                    summary = "最近将来时表示即将发生，简单将来时表示较远的将来。",
                    sections = listOf(
                        GrammarSection(
                            heading = "最近将来时",
                            text = "aller 的现在时 + 动词不定式，强调即将或马上发生。",
                            examples = listOf(
                                GrammarExample("Je vais partir dans cinq minutes.", "我五分钟后就走。")
                            )
                        ),
                        GrammarSection(
                            heading = "简单将来时",
                            bullets = listOf(
                                "构成：不定式 + -ai, -as, -a, -ons, -ez, -ont",
                                "-re 结尾动词去掉词尾 e 再加词尾：vendre → je vendrai",
                                "不规则词干：être → ser-, avoir → aur-, aller → ir-, faire → fer-, venir → viendr-, pouvoir → pourr-"
                            ),
                            examples = listOf(
                                GrammarExample("Demain, il fera beau.", "明天天气会很好。"),
                                GrammarExample("Nous serons là à huit heures.", "我们八点到那儿。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "条件式",
                    fr = "Le conditionnel",
                    summary = "用于礼貌表达、假设结果和未证实的消息，词干同简单将来时。",
                    sections = listOf(
                        GrammarSection(
                            heading = "构成",
                            text = "简单将来时的词干 + 未完成过去时的词尾（-ais, -ais, -ait, -ions, -iez, -aient），不规则词干与将来时相同。",
                            examples = listOf(
                                GrammarExample("Je voudrais un café, s'il vous plaît.", "我想要一杯咖啡，谢谢。")
                            )
                        ),
                        GrammarSection(
                            heading = "用法",
                            bullets = listOf(
                                "礼貌请求：Pourriez-vous m'aider ?",
                                "假设结果（与 si + 未完成过去时搭配）：Si j'avais le temps, je voyagerais.",
                                "未证实消息：Il serait malade.（据说他病了）"
                            ),
                            examples = listOf(
                                GrammarExample("Si elle venait, nous serions contents.", "如果她来，我们会很高兴。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "虚拟式",
                    fr = "Le subjonctif",
                    summary = "用于从句中表达愿望、情感、必要、怀疑等主观语气，由 que 引导。",
                    sections = listOf(
                        GrammarSection(
                            heading = "构成",
                            bullets = listOf(
                                "以 ils 现在时词干加词尾：-e, -es, -e, -ions, -iez, -ent",
                                "nous / vous 形式通常沿用未完成过去时的词干",
                                "不规则：être → que je sois, que nous soyons；avoir → que j'aie, que nous ayons",
                                "faire → que je fasse；aller → que j'aille, que nous allions"
                            ),
                            examples = listOf(
                                GrammarExample("Il faut que tu sois à l'heure.", "你必须准时。")
                            )
                        ),
                        GrammarSection(
                            heading = "常见触发结构",
                            bullets = listOf(
                                "必要：il faut que, il est nécessaire que",
                                "愿望与情感：je veux que, je suis content que, je regrette que",
                                "怀疑与否定：je doute que, je ne pense pas que",
                                "连词：bien que, avant que, pour que, à condition que, quoique"
                            ),
                            examples = listOf(
                                GrammarExample("Je suis content que vous veniez.", "我很高兴你们能来。"),
                                GrammarExample("Bien qu'il pleuve, elle sort.", "尽管下雨，她还是出门。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "助动词与过去分词",
                    fr = "Avoir / Être et le participe passé",
                    summary = "助动词 avoir 与 être 用于构成复合时态，过去分词有规则与不规则形式。",
                    sections = listOf(
                        GrammarSection(
                            heading = "助动词现在时",
                            bullets = listOf(
                                "avoir：j'ai, tu as, il a, nous avons, vous avez, ils ont",
                                "être：je suis, tu es, il est, nous sommes, vous êtes, ils sont",
                                "复合时态由助动词承载人称与时间，过去分词承载动作"
                            ),
                            examples = listOf(
                                GrammarExample("J'ai vu ce film. / Je suis allé au cinéma.", "我看过这部电影。/ 我去过电影院。")
                            )
                        ),
                        GrammarSection(
                            heading = "过去分词构成",
                            bullets = listOf(
                                "第一组：-er → -é（parler → parlé）",
                                "第二组：-ir → -i（finir → fini）",
                                "第三组：-re → -u（vendre → vendu），其余不规则",
                                "常见不规则：fait, dit, écrit, pris, mis, vu, eu, été, eu, né, mort"
                            ),
                            examples = listOf(
                                GrammarExample("Elle a fait ses devoirs.", "她做完了作业。"),
                                GrammarExample("Il est né en 2000.", "他出生于 2000 年。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "代词式动词",
                    fr = "Les verbes pronominaux",
                    summary = "带自反代词的动词，表示动作反作用于主语，需与主语人称配合。",
                    sections = listOf(
                        GrammarSection(
                            heading = "构成与变位",
                            bullets = listOf(
                                "自反代词：me, te, se, nous, vous, se（遇元音缩写为 m', t', s'）",
                                "如 se laver：je me lave, tu te laves, il se lave, nous nous lavons...",
                                "否定式：ne + 自反代词 + 动词 + pas（Je ne me lève pas tôt.）",
                                "复合过去时一律用 être 并配合：Elle s'est levée."
                            ),
                            examples = listOf(
                                GrammarExample("Je m'appelle Marie.", "我叫玛丽。"),
                                GrammarExample("Nous nous sommes couchés tard.", "我们很晚才睡。")
                            )
                        ),
                        GrammarSection(
                            heading = "常见类型",
                            bullets = listOf(
                                "反身：se laver, s'habiller（动作作用于自身）",
                                "相互：se parler, s'aimer（相互之间）",
                                "被动：Ça se vend bien.（这东西好卖）",
                                "绝对：se souvenir de, s'occuper de（必须带自反代词）"
                            ),
                            examples = listOf(
                                GrammarExample("Ils se téléphonent tous les jours.", "他们每天通电话。"),
                                GrammarExample("Je me souviens de ce jour.", "我记得那一天。")
                            )
                        )
                    )
                )
            )
        ),
        GrammarCategory(
            title = "句法",
            fr = "La syntaxe",
            intro = "研究句子的成分、语序以及各类句型：否定、疑问、关系从句、假设、引语与时态配合。",
            topics = listOf(
                GrammarTopic(
                    title = "句子成分与基本语序",
                    fr = "Les fonctions et l'ordre des mots",
                    summary = "陈述句基本语序为主语—谓语—宾语，代词位置与否定结构有固定规则。",
                    sections = listOf(
                        GrammarSection(
                            heading = "基本语序",
                            bullets = listOf(
                                "陈述句：主语 + 变位动词 + 宾语/补语（Je mange une pomme.）",
                                "形容词一般置于名词后，前置形容词与 des 变 de",
                                "时间、地点状语位置灵活，常置于句首或句末"
                            ),
                            examples = listOf(
                                GrammarExample("Hier, Marie a acheté une belle robe au marché.", "昨天玛丽在市场买了一条漂亮的裙子。")
                            )
                        ),
                        GrammarSection(
                            heading = "宾语代词的位置",
                            bullets = listOf(
                                "直接/间接宾语代词置于变位动词之前：Je le vois. / Je lui parle.",
                                "复合时态中置于助动词之前：Je l'ai vu.",
                                "肯定命令式中置于动词之后并用连字符：Regarde-le ! Parle-lui !"
                            ),
                            examples = listOf(
                                GrammarExample("Cette histoire, je la connais.", "这个故事，我知道。"),
                                GrammarExample("Donne-les-moi.", "把它们给我。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "否定句",
                    fr = "La négation",
                    summary = "否定一般由 ne ... pas 等否定短语构成，需注意冠词变化。",
                    sections = listOf(
                        GrammarSection(
                            heading = "否定结构",
                            bullets = listOf(
                                "基本：ne + 变位动词 + pas（Je ne comprends pas.）",
                                "复合时态：ne + 助动词 + pas + 过去分词（Je n'ai pas fini.）",
                                "不定式否定：ne pas + 不定式（Il m'a dit de ne pas partir.）"
                            ),
                            examples = listOf(
                                GrammarExample("Il ne travaille pas le dimanche.", "他星期天不工作。")
                            )
                        ),
                        GrammarSection(
                            heading = "其他否定词与冠词变化",
                            bullets = listOf(
                                "ne ... plus（不再）、ne ... jamais（从不）、ne ... rien（什么都没有）",
                                "ne ... personne（没有人）、ne ... aucun（没有任何）、ne ... ni ... ni（既不…也不）",
                                "否定句中 un/une/des 及部分冠词 du/de la 变为 de"
                            ),
                            examples = listOf(
                                GrammarExample("Je n'ai plus d'argent.", "我没有钱了。"),
                                GrammarExample("Personne n'est venu.", "没有人来。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "疑问句",
                    fr = "L'interrogation",
                    summary = "有三种疑问形式：语调、est-ce que 与倒装，书面语多用倒装。",
                    sections = listOf(
                        GrammarSection(
                            heading = "三种形式",
                            bullets = listOf(
                                "语调问句（口语）：Tu viens ?（语序不变，句末升调）",
                                "est-ce que 问句：Est-ce que tu viens ?（最通用）",
                                "倒装问句（正式）：Viens-tu ?（动词与主语用连字符连接）",
                                "倒装时第三人称单数动词以元音结尾需加 -t-：Aime-t-il le café ?"
                            ),
                            examples = listOf(
                                GrammarExample("Vous parlez français ?", "您说法语吗？"),
                                GrammarExample("Est-ce qu'il est là ?", "他在吗？"),
                                GrammarExample("Parle-t-elle anglais ?", "她会说英语吗？")
                            )
                        ),
                        GrammarSection(
                            heading = "疑问词",
                            bullets = listOf(
                                "qui（谁）、que / qu'est-ce que（什么）、où（哪里）、quand（何时）",
                                "comment（如何）、pourquoi（为什么）、combien（多少）",
                                "quel / quelle / quels / quelles（哪个），与名词性数配合"
                            ),
                            examples = listOf(
                                GrammarExample("Où habites-tu ?", "你住在哪里？"),
                                GrammarExample("Quelle heure est-il ?", "现在几点？")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "关系从句",
                    fr = "La proposition relative",
                    summary = "由关系代词引导，修饰先行词，常见关系代词有 qui, que, dont, où。",
                    sections = listOf(
                        GrammarSection(
                            heading = "关系代词",
                            bullets = listOf(
                                "qui：作从句主语（L'homme qui parle est mon voisin.）",
                                "que：作从句直接宾语（Le livre que je lis est intéressant.）",
                                "dont：代替 de + 先行词（Le film dont je parle est célèbre.）",
                                "où：表示地点或时间（La ville où je suis né / Le jour où...）",
                                "lequel 等：用于介词后（la personne à laquelle je pense）"
                            ),
                            examples = listOf(
                                GrammarExample("Voici la maison que nous avons achetée.", "这就是我们买下的房子。"),
                                GrammarExample("C'est un ami dont je suis fier.", "这是我为之自豪的朋友。")
                            )
                        ),
                        GrammarSection(
                            heading = "ce qui / ce que",
                            text = "当先行词是笼统的「事情/东西」时，用 ce qui（主语）、ce que（宾语）、ce dont（de）。",
                            examples = listOf(
                                GrammarExample("Ce qui m'intéresse, c'est la musique.", "让我感兴趣的是音乐。"),
                                GrammarExample("Je ne sais pas ce que tu veux.", "我不知道你想要什么。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "假设句",
                    fr = "L'expression de l'hypothèse",
                    summary = "由 si 引导的条件句，三个层级的时态搭配表达真实、可能与未实现的假设。",
                    sections = listOf(
                        GrammarSection(
                            heading = "三种时态搭配",
                            bullets = listOf(
                                "真实假设：si + 现在时 → 现在时 / 将来时 / 命令式",
                                "可能假设：si + 未完成过去时 → 条件式现在时",
                                "未实现假设：si + 愈过去时 → 条件式过去时"
                            ),
                            examples = listOf(
                                GrammarExample("Si tu viens, je serai content.", "如果你来，我会很高兴。"),
                                GrammarExample("Si j'avais de l'argent, je voyagerais.", "如果我有钱，我就去旅行。"),
                                GrammarExample("Si j'avais su, je serais venu.", "要是我早知道，我就来了。")
                            )
                        ),
                        GrammarSection(
                            heading = "注意",
                            note = "si 引导的条件从句中，不能使用将来时或条件式，要用现在时或未完成过去时代替。"
                        )
                    )
                ),
                GrammarTopic(
                    title = "直接引语与间接引语",
                    fr = "Le discours rapporté",
                    summary = "转述他人话语时，人称、时间表达和时态都要相应调整。",
                    sections = listOf(
                        GrammarSection(
                            heading = "时态后移规则",
                            bullets = listOf(
                                "主句为现在时/将来时：从句时态不后移",
                                "主句为过去时：现在时 → 未完成过去时，复合过去时 → 愈过去时",
                                "简单将来时 → 条件式现在时，简单过去时 → 愈过去时",
                                "命令式 → de + 不定式"
                            ),
                            examples = listOf(
                                GrammarExample("Il dit : « Je suis malade. » → Il dit qu'il est malade.", "他说：「我病了。」→ 他说他病了。"),
                                GrammarExample("Il a dit : « Je viendrai. » → Il a dit qu'il viendrait.", "他说：「我会来。」→ 他说他会来。")
                            )
                        ),
                        GrammarSection(
                            heading = "时间与指示词调整",
                            bullets = listOf(
                                "aujourd'hui → ce jour-là；hier → la veille；demain → le lendemain",
                                "maintenant → alors；ici → là"
                            ),
                            examples = listOf(
                                GrammarExample("Elle a dit : « Je pars demain. » → Elle a dit qu'elle partait le lendemain.", "她说：「我明天走。」→ 她说她第二天走。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "强调句",
                    fr = "La mise en relief",
                    summary = "用 c'est ... qui/que 等结构突出句子成分，是法语常见的强调手段。",
                    sections = listOf(
                        GrammarSection(
                            heading = "c'est ... qui / que",
                            bullets = listOf(
                                "强调主语：C'est Paul qui a appelé.（是保罗打的电话）",
                                "强调其他成分：C'est demain que nous partons.",
                                "复数用 ce sont：Ce sont eux qui ont gagné."
                            ),
                            examples = listOf(
                                GrammarExample("C'est ce livre que je cherche.", "我找的正是这本书。")
                            )
                        ),
                        GrammarSection(
                            heading = "ce qui / ce que ... c'est",
                            bullets = listOf(
                                "强调宾语：Ce que je veux, c'est un café.",
                                "强调主语：Ce qui est important, c'est la santé."
                            ),
                            examples = listOf(
                                GrammarExample("Ce qui me plaît, c'est son humour.", "让我喜欢的是他的幽默。")
                            )
                        )
                    )
                ),
                GrammarTopic(
                    title = "时态配合",
                    fr = "La concordance des temps",
                    summary = "主句与从句的时态需相互协调，尤其主句为过去时时从句要相应后移。",
                    sections = listOf(
                        GrammarSection(
                            heading = "主句为过去时",
                            bullets = listOf(
                                "从句表示同时：用未完成过去时（Il a dit qu'il était malade.）",
                                "从句表示先于：用愈过去时（Il a dit qu'il avait fini.）",
                                "从句表示后于：用条件式（Il a dit qu'il viendrait.）"
                            ),
                            examples = listOf(
                                GrammarExample("Elle pensait que tu étais déjà parti.", "她以为你已经走了。"),
                                GrammarExample("Nous savions qu'il pleuvrait.", "我们知道会下雨。")
                            )
                        ),
                        GrammarSection(
                            heading = "主句为现在时/将来时",
                            text = "从句时态不受主句影响，按实际时间选择现在时、复合过去时或将来时。",
                            examples = listOf(
                                GrammarExample("Je sais qu'il est là.", "我知道他在。"),
                                GrammarExample("Je pense qu'il a raison.", "我认为他是对的。")
                            )
                        )
                    )
                )
            )
        )
    )
}
