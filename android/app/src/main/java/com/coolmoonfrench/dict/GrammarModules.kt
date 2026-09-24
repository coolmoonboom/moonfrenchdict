package com.coolmoonfrench.dict

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.random.Random

/** 生成器依赖的本地资产 */
class GrammarGenCtx(val book: VocabBook, val conjugator: VerbConjugator) {
    fun words(levelId: String): List<VocabEntry> = book.pool(false, levelId)
    fun verbs(levelId: String): List<VocabEntry> = book.pool(true, levelId)
    fun wordsOf(vararg levelIds: String): List<VocabEntry> =
        levelIds.flatMap { book.pool(false, it) }
}

/** 语法练习模块：静态题库或本地资产即时生成 */
data class GrammarModule(
    val id: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val build: (GrammarGenCtx) -> List<QuizQuestion>
)

data class GrammarLevel(
    val id: String,
    val label: String,
    val modules: List<GrammarModule>
)

fun GrammarGenCtx.conj(
    levelId: String,
    label: String,
    field: (Conjugation) -> List<String>?,
    personLabels: List<String> = GrammarGen.PERSONS,
    extras: List<(Conjugation) -> List<String>?> = emptyList()
): List<QuizQuestion> = GrammarGen.conjugation(
    pool = verbs(levelId),
    conjugator = conjugator,
    tenseLabel = label,
    field = field,
    personLabels = personLabels,
    extraFields = extras,
    rnd = Random.Default
)

/** 语法级别 → 板块清单（书面阶段；听力口语类暂缓，见 .monkeycode/docs/grammar-practice-roadmap.md） */
object GrammarCatalog {

    val levels: List<GrammarLevel> = listOf(
        GrammarLevel("A1", "A1", listOf(
            GrammarModule("a1-article", "名词与冠词", "le / la / l' / les：词书实时出题，答案由名词词性判定", Icons.Filled.CollectionsBookmark) {
                GrammarGen.articles(it.words("A1"), Random.Default)
            },
            GrammarModule("a1-conj-present", "动词变位 · 现在时", "直陈式 présent：变位引擎出题，干扰项为真实变位形式", Icons.Filled.AccessTime) {
                it.conj("A1", "直陈式现在时 (présent)", { c -> c.present })
            },
            GrammarModule("a1-tenses", "时态初识", "最近将来 / 正在进行 / 最近过去 / 简单时态辨认", Icons.Filled.Book) { a1Tenses },
            GrammarModule("a1-pronoun", "代词基础", "主语人称、省音 j'、重读人称与泛指 on", Icons.Filled.TextFields) { a1Pronouns },
            GrammarModule("a1-det", "限定词", "主有形容词与指示形容词（mon amie / cet hôtel）", Icons.Filled.Link) { a1Determinants },
            GrammarModule("a1-neg", "否定结构", "ne…pas / jamais / aucun / que 与否定中的 de", Icons.Filled.Spellcheck) { a1Negation },
            GrammarModule("a1-word", "词汇巩固 A1", "法语→中文释义：A1 词表随机出题", Icons.Filled.Translate) {
                GrammarGen.wordMeaning(it.words("A1"), frToFront = true, rnd = Random.Default)
            }
        )),
        GrammarLevel("A2", "A2", listOf(
            GrammarModule("a2-conj-imparfait", "动词变位 · 未完成过去时", "imparfait：词书 A2 动词 + 变位引擎", Icons.Filled.AccessTime) {
                it.conj("A2", "未完成过去时 (imparfait)", { c -> c.imparfait },
                    extras = listOf({ c -> c.present }, { c -> c.futurSimple }))
            },
            GrammarModule("a2-conj-futur", "动词变位 · 简单将来时", "futur simple", Icons.Filled.AccessTime) {
                it.conj("A2", "简单将来时 (futur simple)", { c -> c.futurSimple },
                    extras = listOf({ c -> c.present }, { c -> c.conditionnel }))
            },
            GrammarModule("a2-conj-imperatif", "命令式", "impératif：tu / nous / vous 三式", Icons.Filled.Language) {
                it.conj("A2", "命令式 (impératif)", { c -> c.imperatif },
                    personLabels = listOf("tu", "nous", "vous"),
                    extras = listOf({ c -> c.present }))
            },
            GrammarModule("a2-agreement", "性数配合", "形容词与过去分词的配合", Icons.Filled.CollectionsBookmark) { a2Agreement },
            GrammarModule("a2-pronoun", "宾语人称代词", "le / la / lui / leur 与代词位置", Icons.Filled.TextFields) { a2PronounObjects },
            GrammarModule("a2-compare", "比较级与最高级", "plus / moins / aussi…que 与 bon-bien 特殊级", Icons.Filled.Functions) { a2Comparison },
            GrammarModule("a2-relative", "关系代词", "qui / que / dont / où", Icons.Filled.MenuBook) { a2Relative },
            GrammarModule("a2-word", "词汇巩固 A2", "法语→中文释义：A2 词表随机出题", Icons.Filled.Translate) {
                GrammarGen.wordMeaning(it.words("A2"), frToFront = true, rnd = Random.Default)
            }
        )),
        GrammarLevel("B1", "B1", listOf(
            GrammarModule("b1-conj-cond", "动词变位 · 条件式现在", "conditionnel présent：词书 B1 动词", Icons.Filled.AccessTime) {
                it.conj("B1", "条件式现在时 (conditionnel présent)", { c -> c.conditionnel },
                    extras = listOf({ c -> c.futurSimple }, { c -> c.present }))
            },
            GrammarModule("b1-tense", "时态配合", "passé composé 与 imparfait 的分工", Icons.Filled.Book) { b1TenseContrast },
            GrammarModule("b1-subjonctif", "虚拟式现在时", "il faut que / bien que / pourvu que", Icons.Filled.School) { b1Subjonctif },
            GrammarModule("b1-eny", "副代词 en / y", "替代 de / à 补语与双代词顺序", Icons.Filled.Link) { b1EnY },
            GrammarModule("b1-passive", "被动与代动词", "被动态配合、代动词分词配合规则", Icons.Filled.Science) { b1PassivePronominal },
            GrammarModule("b1-discours", "间接引语", "时态后退、时间词变化、命令句转换", Icons.Filled.MenuBook) { b1DiscoursRapporte },
            GrammarModule("b1-word", "词汇巩固 B1", "法语→中文释义：B1 词表随机出题", Icons.Filled.Translate) {
                GrammarGen.wordMeaning(it.words("B1"), frToFront = true, rnd = Random.Default)
            }
        )),
        GrammarLevel("B2", "B2", listOf(
            GrammarModule("b2-subjonctif", "虚拟式过去与用法", "虚拟式先时性、espérer 之辨", Icons.Filled.School) { b2Subjonctif },
            GrammarModule("b2-condpasse", "条件式过去时", "与过去相反的假设、委婉与信息式", Icons.Filled.AccessTime) { b2ConditionnelPasse },
            GrammarModule("b2-participe", "分词与副动词", "现在分词、绝对分词句、复合不定式", Icons.Filled.TextFields) { b2ParticipeGerondif },
            GrammarModule("b2-relative", "复合关系代词", " lequel / auquel / pour laquelle…", Icons.Filled.Link) { b2RelativeComplexes },
            GrammarModule("b2-registre", "语域与文体", "口语 / 书面 / 赘虚 ne / 信函套语", Icons.Filled.Translate) { b2Registres },
            GrammarModule("b2-conj-past", "动词变位 · 复合过去", "avoir 助动词 + 过去分词（变位引擎）", Icons.Filled.Calculate) {
                it.conj("B2", "复合过去时 (passé composé)", { c ->
                    if (c.auxiliary != "avoir") null
                    else c.present.map { aux -> "$aux ${c.participePasse}" }
                }, extras = listOf({ c -> c.present }, { c -> c.imparfait }))
            },
            GrammarModule("b2-word", "词汇巩固 B2", "法语→中文释义：B2 词表随机出题", Icons.Filled.Translate) {
                GrammarGen.wordMeaning(it.words("B2"), frToFront = true, rnd = Random.Default)
            }
        )),
        GrammarLevel("C1", "C1", listOf(
            GrammarModule("c1-subjonctif-imp", "动词变位 · 虚拟式未完成", "fût / eût：文学语体变位（变位引擎）", Icons.Filled.Book) {
                it.conj("TFS4", "虚拟式未完成过去时 (subjonctif imparfait)", { c -> c.subjonctifImparfait },
                    extras = listOf({ c -> c.subjonctifPresent }, { c -> c.present }))
            },
            GrammarModule("c1-relief", "强调与倒装", "c'est…qui、Jamais n'ai-je…、fût-il", Icons.Filled.MenuBook) { c1MiseEnRelief },
            GrammarModule("c1-litteraire", "文学时态", "passé simple / antérieur、叙事现在时", Icons.Filled.AccessTime) { c1TempsLitteraires },
            GrammarModule("c1-nominal", "名词化", "标题式名词句、分词独立结构", Icons.Filled.Edit) { c1Nominalisation },
            GrammarModule("c1-nuance", "高阶辨析", "élire/choisir、repousser/prolonger 等", Icons.Filled.Spellcheck) { c1Nuances },
            GrammarModule("c1-word", "词汇拓展", "法语→中文释义：专四级词表随机出题", Icons.Filled.Translate) {
                GrammarGen.wordMeaning(it.words("TFS4"), frToFront = true, rnd = Random.Default)
            }
        )),
        GrammarLevel("TFS8", "专八附加", listOf(
            GrammarModule("tfs8-cloze", "完形填空", "两篇语篇、语法与搭配驱动的空格", Icons.Filled.MenuBook) { tfs8Cloze },
            GrammarModule("tfs8-reading", "阅读理解", "文化短文 + 细节推断", Icons.Filled.Book) { tfs8Reading },
            GrammarModule("tfs8-frzh", "法译汉", "惯用语与句型（ne…qu'à、finir par…）", Icons.Filled.Translate) { tfs8FrToZh },
            GrammarModule("tfs8-zhfr", "汉译法", "双重比较、固定结构与谚语", Icons.Filled.Translate) { tfs8ZhToFr },
            GrammarModule("tfs8-synonym", "同义词替换", "adjurer、s'octroyer、dénouer…", Icons.Filled.Link) { tfs8Synonyms },
            GrammarModule("tfs8-error", "句子改错", "分词配合、fait+inf、cent 复数", Icons.Filled.Spellcheck) { tfs8ErrorSpot },
            GrammarModule("tfs8-culture", "文学文化常识", "作家、作品、典故与纪年", Icons.Filled.Public) { tfs8Culture },
            GrammarModule("tfs8-redaction", "写作手法", "议论文结构、连接词与结尾套语", Icons.Filled.Edit) { tfs8Redaction },
            GrammarModule("tfs8-word", "高频词巩固", "法语→中文释义：专八 / 高级词表随机出题", Icons.Filled.Translate) {
                GrammarGen.wordMeaning(it.wordsOf("TFS8", "ADV"), frToFront = true, rnd = Random.Default)
            }
        ))
    )

    fun level(id: String): GrammarLevel = levels.first { it.id == id }
}
