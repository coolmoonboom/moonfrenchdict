package com.coolmoonfrench.dict

/**
 * 法语动词用法库：句型搭配 + 例句。
 * 每个搭配用 ~...~ 标记需要高亮的介词/连词（à/de/que 等），
 * 界面渲染时高亮显示，帮助学习者抓住动词接续介词这一关键点。
 *
 * 以不定式（不带 se）为键；代动词用法（se souvenir de 等）也挂在其不定式下。
 */

data class VerbExample(val fr: String, val zh: String)

data class UsagePattern(
    val pattern: String,          // 句型，~xxx~ 高亮段
    val note: String,             // 中文说明
    val examples: List<VerbExample> = emptyList()
)

object VerbUsages {

    private val dataSet: Map<String, List<UsagePattern>> = buildMap {

        // ---- 用户在意的两个示例 ----
        put("faire", listOf(
            UsagePattern("faire **qch**", "做某事", listOf(
                VerbExample("Je fais mes devoirs après le dîner.", "我晚饭后做作业。")
            )),
            UsagePattern("faire **qch** ~à~ **qqn**", "给某人做某物；对某人做出某事", listOf(
                VerbExample("Elle me fait un beau cadeau.", "她送了我一件漂亮的礼物。"),
                VerbExample("Ce bruit me fait mal aux oreilles.", "这噪音让我耳朵疼。")
            )),
            UsagePattern("faire faire **qch** ~à~ **qqn**（使役）", "让某人做某事", listOf(
                VerbExample("Je fais réparer ma voiture au garagiste.", "我让修理工修我的车。")
            )),
            UsagePattern("faire en sorte ~que~ + 虚拟式", "设法使……", listOf(
                VerbExample("Fais en sorte qu'il soit là à huit heures.", "设法让他在八点到场。")
            )),
            UsagePattern("faire du / de la / des + 名词", "从事（运动/活动）", listOf(
                VerbExample("Je fais du sport trois fois par semaine.", "我每周做三次运动。")
            ))
        ))

        put("oublier", listOf(
            UsagePattern("oublier **qch / qqn**", "忘记某人/某物", listOf(
                VerbExample("J'ai oublié mon parapluie au bureau.", "我把伞忘在办公室了。")
            )),
            UsagePattern("oublier ~de~ **faire qch**", "忘记做某事", listOf(
                VerbExample("J'ai oublié de fermer la porte.", "我忘记关门了。")
            )),
            UsagePattern("oublier ~que~ + 陈述式", "忘记（某个事实）", listOf(
                VerbExample("Tu oublies qu'il est encore jeune.", "你忘了他还年轻。")
            ))
        ))

        // ---- 接 à + 不定式 ----
        put("commencer", listOf(
            UsagePattern("commencer ~à~ **faire qch**", "开始做某事", listOf(
                VerbExample("Il commence à comprendre la phrase.", "他开始理解这个句子了。")
            )),
            UsagePattern("commencer par **faire qch**", "从做某事开始", listOf(
                VerbExample("Commence par lire la consigne.", "先从读说明开始。")
            ))
        ))
        put("continuer", listOf(
            UsagePattern("continuer ~à~ / ~de~ **faire qch**", "继续做某事", listOf(
                VerbExample("Continue à parler, je t'écoute.", "继续说吧，我在听。")
            ))
        ))
        put("hésiter", listOf(
            UsagePattern("hésiter ~à~ **faire qch**", "犹豫做某事", listOf(
                VerbExample("Elle hésite à lui dire la vérité.", "她犹豫要不要告诉他真相。")
            ))
        ))
        put("réussir", listOf(
            UsagePattern("réussir ~à~ **faire qch**", "成功做成某事", listOf(
                VerbExample("Il a réussi à passer son examen.", "他成功通过了考试。")
            )),
            UsagePattern("réussir **qch**", "通过/考取", listOf(
                VerbExample("Elle a réussi le concours.", "她通过了选拔考试。")
            ))
        ))
        put("parvenir", listOf(
            UsagePattern("parvenir ~à~ **faire qch**", "好不容易做到某事", listOf(
                VerbExample("J'ai enfin parvenu à le joindre.", "我终于联系上他了。")
            ))
        ))
        put("arriver", listOf(
            UsagePattern("arriver ~à~ **faire qch**", "能做到某事", listOf(
                VerbExample("Je n'arrive pas à dormir.", "我睡不着。")
            )),
            UsagePattern("arriver ~à~ / ~dans~ / ~en~ + 地点", "到达某地", listOf(
                VerbExample("Nous arrivons à Paris à midi.", "我们中午到达巴黎。")
            ))
        ))
        put("chercher", listOf(
            UsagePattern("chercher **qch / qqn**", "找某物/某人", listOf(
                VerbExample("Je cherche mes clés.", "我在找我的钥匙。")
            )),
            UsagePattern("chercher ~à~ **faire qch**", "力求做某事", listOf(
                VerbExample("Il cherche à nous convaincre.", "他力图说服我们。")
            ))
        ))
        put("s'attendre", listOf(
            UsagePattern("s'attendre ~à~ **faire qch**", "预期/料到做某事", listOf(
                VerbExample("Je ne m'attendais pas à te voir ici.", "我没料到会在这里见到你。")
            ))
        ))
        put("s'habituer", listOf(
            UsagePattern("s'habituer ~à~ **qch / faire**", "习惯于某物/做某事", listOf(
                VerbExample("Je me suis habitué au bruit de la ville.", "我已经习惯了城市的噪音。")
            ))
        ))
        put("renoncer", listOf(
            UsagePattern("renoncer ~à~ **qch / faire**", "放弃某物/做某事", listOf(
                VerbExample("Il a renoncé à partir.", "他放弃了离开。")
            ))
        ))
        put("obéir", listOf(
            UsagePattern("obéir ~à~ **qqn**", "服从某人", listOf(
                VerbExample("L'enfant obéit à ses parents.", "孩子听父母的话。")
            ))
        ))
        put("réfléchir", listOf(
            UsagePattern("réfléchir ~à~ **qch**", "思考某事物", listOf(
                VerbExample("J'ai beaucoup réfléchi à ta question.", "我对你的问题思考了很多。")
            ))
        ))
        put("penser", listOf(
            UsagePattern("penser ~à~ **qch / qqn**", "想到/想念某人某物", listOf(
                VerbExample("Je pense souvent à toi.", "我经常想你。")
            )),
            UsagePattern("penser ~que~ + 陈述式", "认为……", listOf(
                VerbExample("Je pense que c'est une bonne idée.", "我觉得这是个好主意。")
            )),
            UsagePattern("penser faire qch", "打算做某事", listOf(
                VerbExample("Nous pensons déménager en juillet.", "我们打算七月搬家。")
            ))
        ))
        put("songer", listOf(
            UsagePattern("songer ~à~ **qch / faire**", "考虑/想到某事物、做某事", listOf(
                VerbExample("Il songe à changer de métier.", "他考虑换工作。")
            ))
        ))
        put("ressembler", listOf(
            UsagePattern("ressembler ~à~ **qqn / qch**", "像某人/某物", listOf(
                VerbExample("Tu ressembles beaucoup à ton père.", "你很像你父亲。")
            ))
        ))
        put("plaire", listOf(
            UsagePattern("plaire ~à~ **qqn**", "使某人喜欢", listOf(
                VerbExample("Ce film plaît beaucoup au public.", "这部电影很受观众欢迎。")
            ))
        ))
        put("nuire", listOf(
            UsagePattern("nuire ~à~ **qqn / qch**", "损害某人/某事", listOf(
                VerbExample("Le tabac nuit à la santé.", "吸烟有害健康。")
            ))
        ))
        put("téléphoner", listOf(
            UsagePattern("téléphoner ~à~ **qqn**", "给某人打电话", listOf(
                VerbExample("Je téléphonerai à ma grand-mère ce soir.", "我今晚会给奶奶打电话。")
            ))
        ))
        put("répondre", listOf(
            UsagePattern("répondre ~à~ **qqn / qch**", "回答某人/回应某事", listOf(
                VerbExample("Il a répondu à toutes mes questions.", "他回答了我所有的问题。")
            ))
        ))
        put("appartenir", listOf(
            UsagePattern("appartenir ~à~ **qqn**", "属于某人", listOf(
                VerbExample("Ce livre appartient à Marie.", "这本书是玛丽 的。")
            ))
        ))
        put("tenir", listOf(
            UsagePattern("tenir ~à~ **faire qch**", "坚持要做某事", listOf(
                VerbExample("Elle tient à vous remercier personnellement.", "她坚持要当面感谢您。")
            )),
            UsagePattern("tenir **qch**", "拿着/保持", listOf(
                VerbExample("Tiens ce paquet une minute.", "帮我拿一下这个包。")
            ))
        ))
        put("servir", listOf(
            UsagePattern("servir ~à~ **faire qch**", "用于做某事", listOf(
                VerbExample("Ce bouton sert à allumer la machine.", "这个按钮是用来开机的。")
            )),
            UsagePattern("servir **qch** ~à~ **qqn**", "给某人端上/提供", listOf(
                VerbExample("Le serveur nous sert le plat principal.", "服务员给我们端上主菜。")
            ))
        ))
        put("s'intéresser", listOf(
            UsagePattern("s'intéresser ~à~ **qch / qqn**", "对……感兴趣", listOf(
                VerbExample("Je m'intéresse beaucoup à la musique.", "我对音乐很感兴趣。")
            ))
        ))
        put("jouer", listOf(
            UsagePattern("jouer ~à~ **qch**（体育运动/游戏）", "玩（运动/游戏）", listOf(
                VerbExample("Les enfants jouent au football.", "孩子们在踢足球。")
            )),
            UsagePattern("jouer ~de~ **qch**（乐器）", "演奏（乐器）", listOf(
                VerbExample("Elle joue du piano depuis cinq ans.", "她弹钢琴五年了。")
            ))
        ))
        put("demander", listOf(
            UsagePattern("demander **qch** ~à~ **qqn**", "向某人要某物", listOf(
                VerbExample("Je demande un café au serveur.", "我向服务员要一杯咖啡。")
            )),
            UsagePattern("demander ~à~ **qqn** ~de~ **faire qch**", "请某人做某事", listOf(
                VerbExample("Elle me demande d'attendre un moment.", "她请我等一会儿。")
            ))
        ))
        put("permettre", listOf(
            UsagePattern("permettre à qqn ~de~ **faire qch**", "允许某人做某事", listOf(
                VerbExample("Le professeur nous permet de sortir.", "老师允许我们出去。")
            ))
        ))
        put("promettre", listOf(
            UsagePattern("promettre à qqn ~de~ **faire qch**", "向某人承诺做某事", listOf(
                VerbExample("Je te promets de venir demain.", "我答应你明天来。")
            ))
        ))
        put("interdire", listOf(
            UsagePattern("interdire à qqn ~de~ **faire qch**", "禁止某人做某事", listOf(
                VerbExample("On lui interdit de fumer ici.", "人们禁止他在这里吸烟。")
            ))
        ))
        put("défendre", listOf(
            UsagePattern("défendre à qqn ~de~ **faire qch**", "禁止某人做某事", listOf(
                VerbExample("Mes parents me défendent de rentrer tard.", "父母不许我晚归。")
            )),
            UsagePattern("défendre **qch / qqn**", "保卫/捍卫", listOf(
                VerbExample("Il défend son pays.", "他保卫自己的国家。")
            ))
        ))
        put("ordonner", listOf(
            UsagePattern("ordonner à qqn ~de~ **faire qch**", "命令某人做某事", listOf(
                VerbExample("Le médecin lui ordonne de rester au lit.", "医生命令他卧床休息。")
            ))
        ))
        put("conseiller", listOf(
            UsagePattern("conseiller à qqn ~de~ **faire qch**", "建议某人做某事", listOf(
                VerbExample("Je te conseille de prendre le train.", "我建议你坐火车。")
            ))
        ))
        put("expliquer", listOf(
            UsagePattern("expliquer **qch** ~à~ **qqn**", "向某人解释某事", listOf(
                VerbExample("Le professeur nous explique la leçon.", "老师给我们讲解课文。")
            ))
        ))
        put("montrer", listOf(
            UsagePattern("montrer **qch** ~à~ **qqn**", "给某人看某物", listOf(
                VerbExample("Montre-moi ta photo.", "给我看看你的照片。")
            ))
        ))
        put("raconter", listOf(
            UsagePattern("raconter **qch** ~à~ **qqn**", "给某人讲述某事", listOf(
                VerbExample("Grand-mère nous raconte une histoire.", "奶奶给我们讲故事。")
            ))
        ))
        put("annoncer", listOf(
            UsagePattern("annoncer **qch** ~à~ **qqn**", "向某人宣布某事", listOf(
                VerbExample("Il m'a annoncé son départ.", "他向我宣布了他的离开。")
            ))
        ))
        put("envoyer", listOf(
            UsagePattern("envoyer **qch** ~à~ **qqn**", "给某人寄/发某物", listOf(
                VerbExample("J'envoie un message à Marie.", "我给玛丽发一条信息。")
            ))
        ))
        put("écrire", listOf(
            UsagePattern("écrire **qch** ~à~ **qqn**", "给某人写信/写东西", listOf(
                VerbExample("J'écris une lettre à ma mère.", "我给妈妈写一封信。")
            )),
            UsagePattern("écrire **qch** sur ~qch~", "在……上写", listOf(
                VerbExample("Ne pas écrire sur le livre.", "别在书上写字。")
            ))
        ))
        put("donner", listOf(
            UsagePattern("donner **qch** ~à~ **qqn**", "给某人某物", listOf(
                VerbExample("Je donne ce livre à Paul.", "我把这本书送给保罗。")
            )),
            UsagePattern("donner ~envie~ / ~peur~ ~à~ **qqn** ~de~ **faire**", "使某人有……的欲望/恐惧去做", listOf(
                VerbExample("Cette nouvelle me donne envie de voyager.", "这个消息让我想去旅行。")
            ))
        ))
        put("offrir", listOf(
            UsagePattern("offrir **qch** ~à~ **qqn**", "赠送给某人某物", listOf(
                VerbExample("Il m'offre des fleurs.", "他送我花。")
            )),
            UsagePattern("offrir ~de~ **faire qch**", "主动提出做某事", listOf(
                VerbExample("Il s'est offert de m'aider.", "他主动提出帮我。")
            ))
        ))
        put("dire", listOf(
            UsagePattern("dire **qch** ~à~ **qqn**", "告诉某人某事", listOf(
                VerbExample("Je lui dis la vérité.", "我告诉他真相。")
            )),
            UsagePattern("dire ~à~ **qqn** ~de~ **faire qch**", "叫某人做某事", listOf(
                VerbExample("Elle me dit de venir tôt.", "她叫我早点儿来。")
            )),
            UsagePattern("dire ~que~ + 陈述式", "说……", listOf(
                VerbExample("Il dit qu'il est fatigué.", "他说他累了。")
            ))
        ))
        put("apprendre", listOf(
            UsagePattern("apprendre ~à~ **faire qch**", "学会做某事", listOf(
                VerbExample("J'apprends à nager cet été.", "这个夏天我在学游泳。")
            )),
            UsagePattern("apprendre **qch** ~à~ **qqn**", "教某人某事", listOf(
                VerbExample("Il m'apprend le français.", "他教我法语。")
            )),
            UsagePattern("apprendre ~que~ + 陈述式", "得知……", listOf(
                VerbExample("J'ai appris qu'elle était partie.", "我得知她已经离开了。")
            ))
        ))
        put("décider", listOf(
            UsagePattern("décider ~de~ **faire qch**", "决定做某事", listOf(
                VerbExample("Nous avons décidé de partir demain.", "我们决定明天出发。")
            )),
            UsagePattern("se décider ~à~ **faire qch**", "下定决心做某事", listOf(
                VerbExample("Il s'est enfin décidé à consulter un médecin.", "他终于决定去看医生。")
            ))
        ))
        put("essayer", listOf(
            UsagePattern("essayer ~de~ **faire qch**", "试着做某事", listOf(
                VerbExample("Essaie de venir plus tôt la prochaine fois.", "下次尽量早点来。")
            )),
            UsagePattern("essayer **qch**", "试穿/试用", listOf(
                VerbExample("Je voudrais essayer cette robe.", "我想试试这条裙子。")
            ))
        ))
        put("tenter", listOf(
            UsagePattern("tenter ~de~ **faire qch**", "尝试做某事", listOf(
                VerbExample("Je tenterai de le convaincre.", "我会试着说服他。")
            ))
        ))
        put("finir", listOf(
            UsagePattern("finir ~de~ **faire qch**", "做完某事", listOf(
                VerbExample("J'ai fini de manger.", "我吃完饭了。")
            )),
            UsagePattern("finir par **faire qch**", "最终做了某事", listOf(
                VerbExample("Il a fini par accepter.", "他最终接受了。")
            ))
        ))
        put("arrêter", listOf(
            UsagePattern("arrêter ~de~ **faire qch**", "停止做某事", listOf(
                VerbExample("Arrête de faire du bruit !", "别吵了！")
            )),
            UsagePattern("s'arrêter ~de~ **faire qch**", "停下来不再做某事", listOf(
                VerbExample("La pluie s'est arrêtée de tomber.", "雨停了。")
            ))
        ))
        put("souvenir", listOf(
            UsagePattern("se souvenir ~de~ **qch / qqn / faire**", "记得某事/某人/做过某事", listOf(
                VerbExample("Je me souviens de notre première rencontre.", "我记得我们的第一次见面。")
            ))
        ))
        put("dépêcher", listOf(
            UsagePattern("se dépêcher ~de~ **faire qch**", "赶快做某事", listOf(
                VerbExample("Dépêche-toi de finir ton travail.", "快点完成你的工作。")
            ))
        ))
        put("rêver", listOf(
            UsagePattern("rêver ~de~ **faire qch / qch**", "梦想做某事/梦想某物", listOf(
                VerbExample("Elle rêve de devenir médecin.", "她梦想成为一名医生。")
            ))
        ))
        put("profiter", listOf(
            UsagePattern("profiter ~de~ **qch**", "利用/享受某事", listOf(
                VerbExample("Profite bien de tes vacances !", "好好享受你的假期！")
            ))
        ))
        put("avoir", listOf(
            UsagePattern("avoir besoin ~de~ **qch / faire**", "需要某物/做某事", listOf(
                VerbExample("J'ai besoin d'un peu de repos.", "我需要休息一下。")
            )),
            UsagePattern("avoir envie ~de~ **faire qch**", "想做某事", listOf(
                VerbExample("J'ai envie d'un café.", "我想来杯咖啡。")
            )),
            UsagePattern("avoir peur ~de~ **qch / faire**", "害怕某事/做某事", listOf(
                VerbExample("Elle a peur de parler en public.", "她害怕在公开场合讲话。")
            )),
            UsagePattern("avoir l'habitude ~de~ **faire qch**", "习惯于做某事", listOf(
                VerbExample("J'ai l'habitude de me lever tôt.", "我习惯早起。")
            ))
        ))
        put("être", listOf(
            UsagePattern("être ~en~ / ~à~ / ~dans~ + 地点", "在（地点）", listOf(
                VerbExample("Je suis à la maison.", "我在家。")
            )),
            UsagePattern("être ~sur~ / ~sous~ / ~devant~ + 位置", "在……上面/下面/前面", listOf(
                VerbExample("Le chat est sur le canapé.", "猫在沙发上。")
            ))
        ))
        put("aller", listOf(
            UsagePattern("aller ~à~ / ~chez~ / ~en~ / ~au~ + 地点", "去某地", listOf(
                VerbExample("Je vais chez le dentiste.", "我去看牙医。")
            )),
            UsagePattern("aller + 不定式（最近将来时）", "将要去做某事", listOf(
                VerbExample("Je vais partir dans cinq minutes.", "我五分钟后出发。")
            ))
        ))
        put("venir", listOf(
            UsagePattern("venir ~de~ **faire qch**（最近过去时）", "刚刚做完某事", listOf(
                VerbExample("Je viens de manger.", "我刚吃完饭。")
            )),
            UsagePattern("venir ~à~ / ~chez~ + 地点", "来到某地", listOf(
                VerbExample("Il vient à l'école à pied.", "他步行来上学。")
            ))
        ))
        put("partir", listOf(
            UsagePattern("partir ~pour~ / ~en~ + 地点", "动身去某地", listOf(
                VerbExample("Nous partons pour Lyon demain.", "我们明天动身去里昂。")
            )),
            UsagePattern("partir ~de~ **qch**", "从某地出发", listOf(
                VerbExample("Le train part de Paris à 9h.", "火车九点从巴黎出发。")
            ))
        ))
        put("devoir", listOf(
            UsagePattern("devoir **faire qch**", "必须/应该做某事", listOf(
                VerbExample("Je dois partir maintenant.", "我必须现在走了。")
            )),
            UsagePattern("devoir **qch** ~à~ **qqn**", "欠某人某物", listOf(
                VerbExample("Je te dois 10 euros.", "我欠你十欧元。")
            ))
        ))
        put("pouvoir", listOf(
            UsagePattern("pouvoir **faire qch**", "能够/可以（permission/possibilité）", listOf(
                VerbExample("Est-ce que je peux entrer ?", "我可以进来吗？")
            ))
        ))
        put("vouloir", listOf(
            UsagePattern("vouloir **faire qch**", "想要做某事", listOf(
                VerbExample("Je veux apprendre l'espagnol.", "我想学西班牙语。")
            )),
            UsagePattern("en vouloir ~à~ **qqn**", "对某人心存怨恨", listOf(
                VerbExample("Ne m'en veux pas !", "别怪我！")
            ))
        ))
        put("savoir", listOf(
            UsagePattern("savoir **faire qch**", "会做某事", listOf(
                VerbExample("Elle sait nager depuis cinq ans.", "她游泳已经五年了。")
            )),
            UsagePattern("savoir ~que~ + 陈述式", "知道……", listOf(
                VerbExample("Je sais qu'il viendra.", "我知道他会来。")
            ))
        ))
        put("croire", listOf(
            UsagePattern("croire ~à~ **qch**", "相信（某事存在/正确）", listOf(
                VerbExample("Il croit aux fantômes.", "他相信有鬼。")
            )),
            UsagePattern("croire ~en~ **qch / qqn**", "信仰某物；信任某人", listOf(
                VerbExample("Je crois en l'avenir.", "我对未来有信心。")
            )),
            UsagePattern("croire ~que~ + 陈述式", "认为……", listOf(
                VerbExample("Je crois qu'il a raison.", "我认为他是对的。")
            ))
        ))
        put("voir", listOf(
            UsagePattern("voir **qch / qqn**", "看见某物/某人", listOf(
                VerbExample("J'ai vu un film hier soir.", "我昨晚看了一部电影。")
            )),
            UsagePattern("voir **qqn** faire qch", "看见某人做某事", listOf(
                VerbExample("Je l'ai vu traverser la rue.", "我看见他过马路了。")
            ))
        ))
        put("prendre", listOf(
            UsagePattern("prendre **qch**", "拿/取/乘坐", listOf(
                VerbExample("Je prends le bus pour aller à l'école.", "我坐公交车上学。")
            )),
            UsagePattern("prendre ~le temps~ ~de~ **faire qch**", "花时间慢慢做某事", listOf(
                VerbExample("Prends le temps de réfléchir.", "慢慢考虑，别着急。")
            )),
            UsagePattern("prendre **qqn** ~pour~ **qch/qqn**", "把某人当作……", listOf(
                VerbExample("On me prend pour un professeur.", "别人把我当成老师。")
            ))
        ))
        put("mettre", listOf(
            UsagePattern("mettre **qch** ~sur~ / ~dans~ / ~à~ + 位置", "把某物放在……", listOf(
                VerbExample("Mets la clé sur la table.", "把钥匙放在桌上。")
            )),
            UsagePattern("mettre du temps ~à~ **faire qch**", "花时间做某事", listOf(
                VerbExample("Il met dix minutes à aller au travail.", "他花十分钟去上班。")
            ))
        ))
        put("rendre", listOf(
            UsagePattern("rendre **qch** ~à~ **qqn**", "归还某物给某人", listOf(
                VerbExample("Je te rends ton livre demain.", "我明天把你的书还给你。")
            )),
            UsagePattern("rendre visite ~à~ **qqn**", "拜访某人", listOf(
                VerbExample("Nous rendons visite à nos grands-parents.", "我们去拜访祖父母。")
            )),
            UsagePattern("rendre **qqn** + 形容词", "使某人变得……", listOf(
                VerbExample("Cette musique me rend triste.", "这音乐让我难过。")
            ))
        ))
        }

    /** 获取某动词的用法列表（未收录返回空）。输入可为原形或 se/s' 代动词形式。 */
    fun patternsOf(infinitive: String): List<UsagePattern> {
        val key = infinitive.trim().lowercase()
        dataSet[key]?.let { return it }
        // 代动词别名：se souvenir → souvenir；s'attendre → attendre
        if (key.startsWith("se ")) {
            dataSet[key.removePrefix("se ")]?.let { return it }
        }
        if (key.startsWith("s'")) {
            dataSet[key.removePrefix("s'")]?.let { return it }
        }
        return emptyList()
    }

    /** 关键词索引：动词 → 中文说明（用于提示） */
    fun describe(infinitive: String): String? = patternsOf(infinitive).firstOrNull()?.note
}