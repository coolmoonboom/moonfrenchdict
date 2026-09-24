package com.coolmoonfrench.dict

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 日历和数字：星期 / 月份 / 相对日期说法 + 法语数字系统 / 数学符号读法。
 * 每条法语均可点按发音（espeak 本地合成，无网络依赖）。
 */
@Composable
fun CalendarNumbersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    BackHandler { onBack() }

    val speak: (String) -> Unit = { text ->
        Espeak.speakWithFeedback(context, text, deterministic = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("日历和数字", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("日历") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("数字系统") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (tab == 0) calendarItems(speak) else numbersItems(speak)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.calendarItems(
    speak: (String) -> Unit
) {
    item { SectionTitle("星期（jours de la semaine）") }
    item { TipCard("阳性名词；说「星期几」用介词 à：lundi（周一）… dimanche（周日）。") }
    DAYS.forEach { (fr, zh) ->
        item { WordRow(fr, zh, speak) }
    }
    item { TipCard("la semaine（阴性）= 一周；dimanche 常为休息日，lundi 为一周首日（工作日历）。") }

    item { SectionTitle("月份（mois）") }
    item { TipCard("阳性名词；月份前一般不带冠词，日期用 le + 数字：le 3 mai（5 月 3 日）。") }
    MONTHS.forEach { (fr, zh) ->
        item { WordRow(fr, zh, speak) }
    }

    item { SectionTitle("相对日期（hier ↔ demain）") }
    DATE_CARDS.forEach { card ->
        item { PhraseCard(card.zh, card.fr, card.example, speak) }
    }

    item { SectionTitle("说日期") }
    item { PhraseCard("今天是二零二六年九月二十四日", "Nous sommes le 24 septembre 2026.", "Le cours commence le 24 septembre.", speak) }
    item { PhraseCard("每月一号用 premier（唯一用序数的日子）", "le 1er janvier = le premier janvier", "Je suis arrivé le 1er août.", speak) }
    item { PhraseCard("年份读法：1998 = dix-neuf quatre-vingt-dix-huit（两位两位读）", "1998 → dix-neuf quarante-huit（口语）", "Elle est née en 1998.", speak) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.numbersItems(
    speak: (String) -> Unit
) {
    item { SectionTitle("0 – 16（各自独立，必须整体记忆）") }
    item { ChipGroup(NUMS_0_16, speak) }

    item { SectionTitle("17 – 69（组合规则）") }
    item { TipCard("dix-sept (17)…vingt (20)…trente (30)、quarante (40)、cinquante (50)、soixante (60)；个位用连字符拼接：trente-deux (32)。仅「X 十 一」加 et：vingt et un (21)、trente et un (31)；quatre-vingt et un 除外。") }

    item { SectionTitle("70 – 99（法语核心难点：以 60 / 80 为基）") }
    SIXTY_BLOCK.forEach { (fr, zh) ->
        item { WordRow(fr, zh, speak) }
    }
    item { TipCard("比利时、瑞士用 septante (70) / nonante (90)；瑞士还用 huitante (80) 或 octante。") }

    item { SectionTitle("百、千、百万") }
    BIG_NUMS.forEach { (fr, zh) ->
        item { WordRow(fr, zh, speak) }
    }
    item { TipCard("cent 前有多个整数才加 s（deux cents），后面还有数字又不加（deux cent un）；mille 永远不变；million 是普通阴性名词，接 de：deux millions d'euros。") }

    item { SectionTitle("序数与分数") }
    ORD_FRACTIONS.forEach { (fr, zh) ->
        item { WordRow(fr, zh, speak) }
    }
    item { TipCard("分数 = 分子 + 分母序数（-ième 复数加 s）；专用词：demi / tiers / quart。½ une demie、⅓ un tiers、¼ un quart、⅖ deux cinquièmes。") }

    item { SectionTitle("数学符号怎么念（高中数学）") }
    MATH_ROWS.forEach { (symbol, fr, zh) ->
        item { MathRow(symbol, fr, zh, speak) }
    }
    item { TipCard("代数式按符号逐个连读：3x − 2 = 5 → « trois x moins deux égale cinq »；两边同单位时省略单位；单位要读两次：3 m × 4 m = 12 m²。") }
}

// ---------------- 内容数据 ----------------

private data class DateCard(val zh: String, val fr: String, val example: String)

private val DAYS = listOf(
    "lundi" to "星期一",
    "mardi" to "星期二",
    "mercredi" to "星期三",
    "jeudi" to "星期四",
    "vendredi" to "星期五",
    "samedi" to "星期六",
    "dimanche" to "星期日"
)

private val MONTHS = listOf(
    "janvier" to "一月",
    "février" to "二月",
    "mars" to "三月",
    "avril" to "四月",
    "mai" to "五月",
    "juin" to "六月",
    "juillet" to "七月",
    "août" to "八月",
    "septembre" to "九月",
    "octobre" to "十月",
    "novembre" to "十一月",
    "décembre" to "十二月"
)

private val DATE_CARDS = listOf(
    DateCard("今天", "aujourd'hui", "Aujourd'hui, il fait beau."),
    DateCard("明天", "demain", "Demain, je pars à Paris."),
    DateCard("后天", "après-demain", "Après-demain, c'est vendredi."),
    DateCard("昨天", "hier", "Hier, j'ai travaillé toute la journée."),
    DateCard("前天", "avant-hier", "Avant-hier, nous avons visité le musée."),
    DateCard("大前天", "il y a trois jours", "J'ai fini ce travail il y a trois jours."),
    DateCard("N 天前（il y a + 数量）", "il y a cinq jours", "Il y a cinq jours, il pleuvait tout le temps."),
    DateCard("这周 / 上周 / 下周", "cette semaine / la semaine dernière / la semaine prochaine", "On se voit la semaine prochaine."),
    DateCard("N 个星期前", "il y a deux semaines", "Elle est partie il y a trois semaines."),
    DateCard("N 个星期后（dans + 数量，表将来）", "dans deux semaines", "Nous reviendrons dans deux semaines."),
    DateCard("这个月 / 上个月 / 下个月", "ce mois-ci / le mois dernier / le mois prochain", "Le loyer arrive le mois prochain."),
    DateCard("N 个月前", "il y a trois mois", "Il y a six mois, j'habitais encore à Lyon."),
    DateCard("今年 / 去年 / 明年", "cette année / l'année dernière / l'année prochaine", "L'année dernière, j'ai appris le français."),
    DateCard("N 年前", "il y a deux ans", "Nous avons emménagé ici il y a deux ans.")
)

private val NUMS_0_16 = listOf(
    "zéro 0", "un 1", "deux 2", "trois 3", "quatre 4", "cinq 5", "six 6", "sept 7",
    "huit 8", "neuf 9", "dix 10", "onze 11", "douze 12", "treize 13", "quatorze 14",
    "quinze 15", "seize 16"
)

private val SIXTY_BLOCK = listOf(
    "soixante-dix (70-79)" to "60 + 10~19：soixante-dix, soixante et onze, soixante-douze… soixante-dix-neuf",
    "quatre-vingts (80)" to "四个二十 = 80；单独出现加 s",
    "quatre-vingt-un (81)" to "81 起去掉 s，继续八十零几：quatre-vingt-deux…",
    "quatre-vingt-dix (90-99)" to "80 + 10~19：quatre-vingt-dix, quatre-vingt-onze… quatre-vingt-dix-neuf"
)

private val BIG_NUMS = listOf(
    "cent / deux cents / deux cent un" to "100 / 200 / 201（cents 的 s 规则）",
    "deux cent trente-huit" to "238（百十连字符拼接）",
    "mille / deux mille" to "1000 / 2000（mille 无复数）",
    "un million / un milliard" to "10⁶ / 10⁹（是名词，后接 de）"
)

private val ORD_FRACTIONS = listOf(
    "premier (1er) / première" to "第一（唯一用在日期的序数）",
    "deuxième / troisième" to "第二 / 第三",
    "un demi(e) · ½" to "一半",
    "un tiers · ⅓" to "三分之一",
    "un quart · ¼" to "四分之一",
    "trois quarts · ¾" to "四分之三",
    "deux cinquièmes · ⅖" to "五分之二（分母用序数复数）"
)

private val MATH_ROWS = listOf(
    Triple("a + b", "a plus b", "加法"),
    Triple("a − b", "a moins b", "减法"),
    Triple("a × b", "a fois b", "乘法"),
    Triple("a ÷ b", "a divisé par b", "除法"),
    Triple("a = b", "a est égal à b / a égale b", "等于"),
    Triple("a ≠ b", "a est différent de b", "不等于"),
    Triple("a > b / a < b", "a est plus grand que b / plus petit que b", "大于 / 小于"),
    Triple("a ≥ b / a ≤ b", "a est supérieur(eur) ou égal à b / inférieur(eur) ou égal à b", "大于等于 / 小于等于"),
    Triple("x²", "x au carré (à la puissance deux)", "平方"),
    Triple("x³", "x au cube (à la puissance trois)", "立方"),
    Triple("aⁿ", "a à la puissance n", "n 次幂"),
    Triple("√a", "la racine carrée de a", "平方根"),
    Triple("∛a", "la racine cubique de a", "立方根"),
    Triple("ⁿ√a", "la racine n-ième de a", "n 次方根"),
    Triple("ln x", "le logarithme népérien de x", "自然对数"),
    Triple("log x", "le logarithme (décimal) de x", "常用对数"),
    Triple("π", "pi", "圆周率"),
    Triple("+∞ / −∞", "plus l'infini / moins l'infini", "正负无穷"),
    Triple("(a + b)", "entre parenthèses / a ouvert… fermé", "括号"),
    Triple("3x − 2 = 5", "trois x moins deux égale cinq", "含系数方程整读示例"),
    Triple("(a + b)²", "a plus b, le tout au carré", "整体平方"),
    Triple("a/b (fraction)", "a sur b", "分数线（书写/计算时）")
)

// ---------------- 通用组件 ----------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun TipCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
private fun WordRow(fr: String, zh: String, speak: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { speak(fr) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(fr, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (zh.isNotBlank()) {
                Text(zh, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SpeakIcon()
    }
}

@Composable
private fun MathRow(symbol: String, fr: String, zh: String, speak: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { speak(fr) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(symbol, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(fr, fontSize = 14.sp)
            Text(zh, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpeakIcon()
    }
}

@Composable
private fun PhraseCard(zh: String, fr: String, example: String, speak: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { speak(fr) }
                .padding(12.dp)
        ) {
            Text(zh, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(fr, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { speak(example) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    example,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                SpeakIcon()
            }
        }
    }
}

@Composable
private fun ChipGroup(items: List<String>, speak: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            items.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    row.forEach { item ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { speak(item.substringBeforeLast(' ')) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(item, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakIcon() {
    Icon(
        Icons.AutoMirrored.Filled.VolumeUp,
        contentDescription = "发音",
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}

