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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class PhonemeRow(
    val symbol: String,
    val example: String,
    val meaning: String,
    val tip: String = "",
    val speak: String = example
)

private data class PhonemeSection(
    val title: String,
    val subtitle: String,
    val rows: List<PhonemeRow>
)

private val phonemeSections = listOf(
    PhonemeSection(
        "法语字母表", "Alphabet · 26 个字母及其法语名称（点击喇叭朗读字母名）",
        listOf(
            PhonemeRow("A a", "a", "字母名 /a/", "例词：avion 飞机"),
            PhonemeRow("B b", "bé", "字母名 /be/", "例词：bonjour 你好"),
            PhonemeRow("C c", "cé", "字母名 /se/", "例词：café 咖啡"),
            PhonemeRow("D d", "dé", "字母名 /de/", "例词：dame 女士"),
            PhonemeRow("E e", "e", "字母名 /ə/", "例词：le 定冠词"),
            PhonemeRow("F f", "effe", "字母名 /ɛf/", "例词：femme 女人"),
            PhonemeRow("G g", "gé", "字母名 /ʒe/", "例词：gare 车站"),
            PhonemeRow("H h", "ache", "字母名 /aʃ/", "例词：homme 男人"),
            PhonemeRow("I i", "i", "字母名 /i/", "例词：île 岛"),
            PhonemeRow("J j", "ji", "字母名 /ʒi/", "例词：je 我"),
            PhonemeRow("K k", "ka", "字母名 /ka/", "例词：kilo 千克"),
            PhonemeRow("L l", "elle", "字母名 /ɛl/", "例词：lune 月亮"),
            PhonemeRow("M m", "emme", "字母名 /ɛm/", "例词：mère 母亲"),
            PhonemeRow("N n", "enne", "字母名 /ɛn/", "例词：nous 我们"),
            PhonemeRow("O o", "o", "字母名 /o/", "例词：orange 橙子"),
            PhonemeRow("P p", "pé", "字母名 /pe/", "例词：père 父亲"),
            PhonemeRow("Q q", "qu", "字母名 /ky/", "例词：quatre 四", speak = "ku"),
            PhonemeRow("R r", "erre", "字母名 /ɛʁ/", "例词：rue 街道"),
            PhonemeRow("S s", "esse", "字母名 /ɛs/", "例词：selle 马鞍"),
            PhonemeRow("T t", "té", "字母名 /te/", "例词：table 桌子"),
            PhonemeRow("U u", "u", "字母名 /y/", "例词：tu 你"),
            PhonemeRow("V v", "vé", "字母名 /ve/", "例词：vous 你们"),
            PhonemeRow("W w", "double vé", "字母名 /dublə ve/", "例词：week-end 周末"),
            PhonemeRow("X x", "ixe", "字母名 /iks/", "例词：taxi 出租车"),
            PhonemeRow("Y y", "i grec", "字母名 /i ɡʁɛk/", "例词：stylo 笔"),
            PhonemeRow("Z z", "zède", "字母名 /zɛd/", "例词：zéro 零")
        )
    ),
    PhonemeSection(
        "口腔元音", "Voyelles orales · IPA 元音音位",
        listOf(
            PhonemeRow("/i/", "si", "如果", "拼写：i, y, î, ï"),
            PhonemeRow("/e/", "été", "夏天", "拼写：é, -er, -ez, -es"),
            PhonemeRow("/ɛ/", "mère", "母亲", "拼写：è, ê, ai, ei, e + 双辅音"),
            PhonemeRow("/a/", "patte", "爪子", "拼写：a, à"),
            PhonemeRow("/ɑ/", "pâte", "面团", "拼写：â；现代法语多与 /a/ 合并"),
            PhonemeRow("/ɔ/", "port", "港口", "拼写：o 在闭音节、o + 双辅音"),
            PhonemeRow("/o/", "beau", "美的", "拼写：ô, au, eau, 词末开音节 o"),
            PhonemeRow("/u/", "rouge", "红色", "拼写：ou, où, oû"),
            PhonemeRow("/y/", "tu", "你", "拼写：u, û（汉语拼音 ü）"),
            PhonemeRow("/ø/", "peu", "少", "拼写：eu, œu（开音节）"),
            PhonemeRow("/œ/", "peur", "害怕", "拼写：eu, œu（闭音节）"),
            PhonemeRow("/ə/", "le", "定冠词", "拼写：词首或单音节的 e（弱读，可省略）")
        )
    ),
    PhonemeSection(
        "鼻化元音", "Voyelles nasales · 气流同时从口腔与鼻腔出",
        listOf(
            PhonemeRow("/ɑ̃/", "grand", "大的", "拼写：an, am, en, em"),
            PhonemeRow("/ɛ̃/", "pain", "面包", "拼写：in, im, ain, ein, yn, ym"),
            PhonemeRow("/ɔ̃/", "bon", "好的", "拼写：on, om"),
            PhonemeRow("/œ̃/", "brun", "棕色的", "拼写：un, um；现代法语多并入 /ɛ̃/")
        )
    ),
    PhonemeSection(
        "半元音 / 滑音", "Semi-voyelles（双元音式滑音）· 快速滑向元音",
        listOf(
            PhonemeRow("/j/", "fille", "女孩", "拼写：i, y, il, ill；近似汉语拼音 y"),
            PhonemeRow("/w/", "oui", "是", "拼写：ou + 元音、oi；近似 w"),
            PhonemeRow("/ɥ/", "huit", "八", "拼写：u + 元音；介于 y 与 w 之间")
        )
    ),
    PhonemeSection(
        "辅音", "Consonnes · IPA 辅音音位",
        listOf(
            PhonemeRow("/p/", "père", "父亲", "拼写：p, pp"),
            PhonemeRow("/b/", "bon", "好的", "拼写：b, bb"),
            PhonemeRow("/t/", "table", "桌子", "拼写：t, tt, th"),
            PhonemeRow("/d/", "dame", "女士", "拼写：d, dd"),
            PhonemeRow("/k/", "café", "咖啡", "拼写：c, qu, k, cc"),
            PhonemeRow("/g/", "gare", "车站", "拼写：g, gu"),
            PhonemeRow("/f/", "femme", "女人", "拼写：f, ph, ff"),
            PhonemeRow("/v/", "vous", "你们", "拼写：v"),
            PhonemeRow("/s/", "selle", "马鞍", "拼写：s, ss, c, ç, t(i)"),
            PhonemeRow("/z/", "zéro", "零", "拼写：z, 元音之间的 s"),
            PhonemeRow("/ʃ/", "chat", "猫", "拼写：ch, sch"),
            PhonemeRow("/ʒ/", "je", "我", "拼写：j, ge, gi"),
            PhonemeRow("/m/", "mère", "母亲", "拼写：m, mm"),
            PhonemeRow("/n/", "nous", "我们", "拼写：n, nn"),
            PhonemeRow("/ɲ/", "agneau", "小羊", "拼写：gn"),
            PhonemeRow("/ŋ/", "camping", "露营", "拼写：ng（多出现在外来词）"),
            PhonemeRow("/l/", "lune", "月亮", "拼写：l, ll"),
            PhonemeRow("/ʁ/", "rue", "街道", "拼写：r, rr；法语小舌擦音"),
            PhonemeRow("/ks/", "taxi", "出租车", "拼写：x"),
            PhonemeRow("/gz/", "examen", "考试", "拼写：x（ex- 后接元音）"),
            PhonemeRow("/h/", "homme", "男人", "字母 h 不发音；嘘音 h 只影响联诵")
        )
    ),
    PhonemeSection(
        "常见字母组合", "Combinaisons · 一组字母对应一个音",
        listOf(
            PhonemeRow("ch", "chat", "猫", "/ʃ/"),
            PhonemeRow("gn", "agneau", "小羊", "/ɲ/"),
            PhonemeRow("qu", "quatre", "四", "/k/"),
            PhonemeRow("ph", "photo", "照片", "/f/"),
            PhonemeRow("ou", "rouge", "红色", "/u/"),
            PhonemeRow("oi", "moi", "我", "/wa/"),
            PhonemeRow("ai/ei", "neige", "雪", "/ɛ/"),
            PhonemeRow("au/eau", "bateau", "船", "/o/"),
            PhonemeRow("eu/œu", "deux", "二", "/ø/ 或 /œ/"),
            PhonemeRow("an/am", "grand", "大的", "/ɑ̃/（en/em 同）"),
            PhonemeRow("in/ain", "pain", "面包", "/ɛ̃/（im/ein 同）"),
            PhonemeRow("on/om", "bon", "好的", "/ɔ̃/"),
            PhonemeRow("un/um", "brun", "棕色的", "/œ̃/"),
            PhonemeRow("il/ill", "fille", "女孩", "/j/"),
            PhonemeRow("ien", "bien", "好", "/jɛ̃/"),
            PhonemeRow("oin", "loin", "远", "/wɛ̃/"),
            PhonemeRow("-tion", "nation", "国家", "/sjɔ̃/"),
            PhonemeRow("-er/-ez", "parler", "说", "/e/")
        )
    )
)

/**
 * 法语字母与音标表：字母、口腔元音、鼻化元音、半元音/滑音、辅音、常见字母组合。
 * 每行点击喇叭用 TTS 朗读代表例词（IPA 符号无法直接合成，故朗读法语拼写例词）。
 */
@Composable
fun PhoneticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // 预热法语 TTS（幂等，非阻塞）
    LaunchedEffect(Unit) {
        Espeak.ensureInitialized(context)
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
            Text(
                "共 ${phonemeSections.size} 类",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "法语字母与 IPA 音标一览。点击每行右侧喇叭可朗读代表例词，字母行朗读的是字母的法语名称。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            phonemeSections.forEach { section ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            section.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            section.subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        section.rows.forEachIndexed { index, row ->
                            PhonemeRowView(context, row)
                            if (index < section.rows.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PhonemeRowView(context: Context, row: PhonemeRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            row.symbol,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(76.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.example, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(row.meaning, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (row.tip.isNotBlank()) {
                Text(row.tip, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(
            onClick = {
                Espeak.ensureInitialized(context)
                Espeak.speakWithFeedback(context, row.speak)
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "朗读 ${row.example}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
