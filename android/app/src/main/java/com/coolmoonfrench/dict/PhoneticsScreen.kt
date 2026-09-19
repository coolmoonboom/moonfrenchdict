package com.coolmoonfrench.dict

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TAB_TITLES = listOf("字母", "元音", "辅音")
private const val GRID_COLUMNS = 5

private data class PhonemeItem(
    val display: String,
    val example: String,
    val meaning: String,
    val speak: String = example
)

private data class PhonemeGroup(
    val title: String,
    val items: List<PhonemeItem>
)

private val alphabetItems = listOf(
    PhonemeItem("A", "avion", "飞机", speak = "a"),
    PhonemeItem("B", "bonjour", "你好", speak = "bé"),
    PhonemeItem("C", "café", "咖啡", speak = "cé"),
    PhonemeItem("D", "dame", "女士", speak = "dé"),
    PhonemeItem("E", "le", "定冠词", speak = "e"),
    PhonemeItem("F", "femme", "女人", speak = "effe"),
    PhonemeItem("G", "gare", "车站", speak = "gé"),
    PhonemeItem("H", "homme", "男人", speak = "ache"),
    PhonemeItem("I", "île", "岛", speak = "i"),
    PhonemeItem("J", "je", "我", speak = "ji"),
    PhonemeItem("K", "kilo", "千克", speak = "ka"),
    PhonemeItem("L", "lune", "月亮", speak = "elle"),
    PhonemeItem("M", "mère", "母亲", speak = "emme"),
    PhonemeItem("N", "nous", "我们", speak = "enne"),
    PhonemeItem("O", "orange", "橙子", speak = "o"),
    PhonemeItem("P", "père", "父亲", speak = "pé"),
    PhonemeItem("Q", "quatre", "四", speak = "ku"),
    PhonemeItem("R", "rue", "街道", speak = "erre"),
    PhonemeItem("S", "selle", "马鞍", speak = "esse"),
    PhonemeItem("T", "table", "桌子", speak = "té"),
    PhonemeItem("U", "tu", "你", speak = "u"),
    PhonemeItem("V", "vous", "你们", speak = "vé"),
    PhonemeItem("W", "week-end", "周末", speak = "double vé"),
    PhonemeItem("X", "taxi", "出租车", speak = "ixe"),
    PhonemeItem("Y", "stylo", "笔", speak = "i grec"),
    PhonemeItem("Z", "zéro", "零", speak = "zède")
)

private val vowelGroups = listOf(
    PhonemeGroup(
        "口腔元音",
        listOf(
            PhonemeItem("/i/", "si", "如果"),
            PhonemeItem("/e/", "été", "夏天"),
            PhonemeItem("/ɛ/", "mère", "母亲"),
            PhonemeItem("/a/", "patte", "爪子"),
            PhonemeItem("/ɑ/", "pâte", "面团"),
            PhonemeItem("/ɔ/", "port", "港口"),
            PhonemeItem("/o/", "beau", "美的"),
            PhonemeItem("/u/", "rouge", "红色"),
            PhonemeItem("/y/", "tu", "你"),
            PhonemeItem("/ø/", "peu", "少"),
            PhonemeItem("/œ/", "peur", "害怕"),
            PhonemeItem("/ə/", "le", "定冠词")
        )
    ),
    PhonemeGroup(
        "鼻化元音",
        listOf(
            PhonemeItem("/ɑ̃/", "grand", "大的"),
            PhonemeItem("/ɛ̃/", "pain", "面包"),
            PhonemeItem("/ɔ̃/", "bon", "好的"),
            PhonemeItem("/œ̃/", "brun", "棕色的")
        )
    ),
    PhonemeGroup(
        "半元音 / 滑音",
        listOf(
            PhonemeItem("/j/", "fille", "女孩"),
            PhonemeItem("/w/", "oui", "是"),
            PhonemeItem("/ɥ/", "huit", "八")
        )
    )
)

private val consonantGroup = PhonemeGroup(
    "法语辅音表",
    listOf(
        PhonemeItem("/p/", "père", "父亲"),
        PhonemeItem("/b/", "bon", "好的"),
        PhonemeItem("/t/", "table", "桌子"),
        PhonemeItem("/d/", "dame", "女士"),
        PhonemeItem("/k/", "café", "咖啡"),
        PhonemeItem("/g/", "gare", "车站"),
        PhonemeItem("/f/", "femme", "女人"),
        PhonemeItem("/v/", "vous", "你们"),
        PhonemeItem("/s/", "selle", "马鞍"),
        PhonemeItem("/z/", "zéro", "零"),
        PhonemeItem("/ʃ/", "chat", "猫"),
        PhonemeItem("/ʒ/", "je", "我"),
        PhonemeItem("/m/", "mère", "母亲"),
        PhonemeItem("/n/", "nous", "我们"),
        PhonemeItem("/ɲ/", "agneau", "小羊"),
        PhonemeItem("/ŋ/", "camping", "露营"),
        PhonemeItem("/l/", "lune", "月亮"),
        PhonemeItem("/ʁ/", "rue", "街道"),
        PhonemeItem("/ks/", "taxi", "出租车"),
        PhonemeItem("/gz/", "examen", "考试"),
        PhonemeItem("/h/", "homme", "男人")
    )
)

/**
 * 法语字母与音标表：顶部三档切换「字母 / 元音 / 辅音」，
 * 每张表按每行 5 个的网格排布，每一项都带喇叭按钮与示例单词。
 */
@Composable
fun PhoneticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        Espeak.ensureInitialized(context)
    }
    LaunchedEffect(selectedTab) {
        scrollState.scrollTo(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("字母音标表", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            TAB_TITLES.forEachIndexed { index, title ->
                SegmentedButton(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TAB_TITLES.size),
                    icon = {}
                ) {
                    Text(title, fontSize = 14.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                when (selectedTab) {
                    0 -> "点击字母旁的喇叭听法语字母读音，字母下方为示例单词。"
                    1 -> "法语元音音标及例词，点击喇叭朗读例词。"
                    else -> "法语辅音音标及例词，点击喇叭朗读例词。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            when (selectedTab) {
                0 -> {
                    PhonemeGroupCard(
                        context = context,
                        group = PhonemeGroup("法语字母表", alphabetItems)
                    )
                }
                1 -> {
                    vowelGroups.forEach { group ->
                        PhonemeGroupCard(context = context, group = group)
                    }
                }
                else -> {
                    PhonemeGroupCard(context = context, group = consonantGroup)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PhonemeGroupCard(context: Context, group: PhonemeGroup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                group.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                group.items.chunked(GRID_COLUMNS).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { item ->
                            PhonemeCell(
                                context = context,
                                item = item,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(GRID_COLUMNS - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhonemeCell(context: Context, item: PhonemeItem, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.display,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = {
                    Espeak.ensureInitialized(context)
                    Espeak.speakWithFeedback(context, item.speak)
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "朗读 ${item.display}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            item.example,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            item.meaning,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
