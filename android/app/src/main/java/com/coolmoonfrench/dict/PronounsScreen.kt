package com.coolmoonfrench.dict

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class PronounGroup(
    val title: String,
    val subtitle: String,
    val rows: List<Pair<String, String>>
)

private val pronounGroups = listOf(
    PronounGroup(
        "人称主语代词", "Pronoms personnels sujets",
        listOf(
            "je / j'" to "我",
            "tu" to "你",
            "il / elle / on" to "他 / 她 / 人们",
            "nous" to "我们",
            "vous" to "你们 / 您",
            "ils / elles" to "他们 / 她们"
        )
    ),
    PronounGroup(
        "直接宾语代词 COD", "me / m', te / t', le / la / l', nous, vous, les",
        listOf(
            "me (m')" to "我",
            "te (t')" to "你",
            "le (l') / la (l')" to "他 / 她 / 它",
            "nous" to "我们",
            "vous" to "你们 / 您",
            "les" to "他们 / 她们 / 它们"
        )
    ),
    PronounGroup(
        "间接宾语代词 COI", "me / te / lui / nous / vous / leur",
        listOf(
            "me (m')" to "给我（à moi）",
            "te (t')" to "给你（à toi）",
            "lui" to "给他 / 她（à lui/elle）",
            "nous" to "给我们（à nous）",
            "vous" to "给你们 / 您（à vous）",
            "leur" to "给他们 / 她们（à eux/elles）"
        )
    ),
    PronounGroup(
        "重读 / 强式代词", "Pronoms toniques",
        listOf(
            "moi" to "我",
            "toi" to "你",
            "lui / elle" to "他 / 她",
            "nous" to "我们",
            "vous" to "你们 / 您",
            "eux / elles" to "他们 / 她们"
        )
    ),
    PronounGroup(
        "自反代词", "Pronoms réfléchis",
        listOf(
            "me (m')" to "我自己",
            "te (t')" to "你自己",
            "se (s')" to "他自己 / 她自己 / 他们自己",
            "nous" to "我们自己",
            "vous" to "你们自己",
            "se (s')" to "他们 / 她们自己"
        )
    ),
    PronounGroup(
        "所有格代词", "Pronoms possessifs",
        listOf(
            "le mien / la mienne / les miens / les miennes" to "我的",
            "le tien / la tienne / les tiens / les tiennes" to "你的",
            "le sien / la sienne / les siens / les siennes" to "他 / 她的",
            "le nôtre / la nôtre / les nôtres" to "我们的",
            "le vôtre / la vôtre / les vôtres" to "你们的 / 您的",
            "le leur / la leur / les leurs" to "他们的 / 她们的"
        )
    ),
    PronounGroup(
        "指示代词", "Pronoms démonstratifs",
        listOf(
            "celui" to "这个（阳性单数）",
            "celle" to "这个（阴性单数）",
            "ceux" to "这些（阳性复数）",
            "celles" to "这些（阴性复数）",
            "celui-ci / celui-là" to "这个 / 那个（较近 / 较远）",
            "celle-ci / celle-là" to "这个 / 那个（阴性）"
        )
    ),
    PronounGroup(
        "疑问代词", "Pronoms interrogatifs",
        listOf(
            "qui" to "谁（指人）",
            "que / quoi" to "什么（指物）",
            "lequel / laquelle / lesquels / lesquelles" to "哪一个（选择性）",
            "qui est-ce qui / qu'est-ce qui" to "谁（作主语）",
            "qui est-ce que / qu'est-ce que" to "谁 / 什么（作宾语）",
            "à qui / de quoi / avec qui" to "对谁 / 关于什么 / 和谁（介词后）"
        )
    ),
    PronounGroup(
        "关系代词", "Pronoms relatifs",
        listOf(
            "qui" to "……的人 / 物（作主语）",
            "que (qu')" to "……的人 / 物（作宾语）",
            "dont" to "……的（de + 先行词）",
            "où" to "在……的地方 / 时（地点 / 时间）",
            "lequel / laquelle / lesquels / lesquelles" to "介词后的关系代词",
            "auquel / à laquelle / duquel / desquels" to "à / de + lequel 缩合形式"
        )
    ),
    PronounGroup(
        "副代词", "Pronoms adverbiaux",
        listOf(
            "en" to "替代 de + 名词；数量（un, deux, beaucoup…）",
            "y" to "替代 à / dans / sur + 地点或事物",
            "en 示例" to "Il a des livres. J'en ai trois.",
            "y 示例" to "Tu vas à Paris ? Oui, j'y vais."
        )
    ),
    PronounGroup(
        "泛指代词", "Pronoms indéfinis",
        listOf(
            "personne" to "没有人（ne…personne）",
            "rien" to "没有东西（ne…rien）",
            "tout" to "一切、所有",
            "chacun / chacune" to "每一个",
            "quelqu'un / quelqu'une" to "某人、有人",
            "certains / plusieurs / quelques-uns" to "某些 / 几个"
        )
    ),
    PronounGroup(
        "中性代词", "Pronoms neutres",
        listOf(
            "il（无人称）" to "天气 / 时间：Il fait beau. Il pleut.",
            "le（中性）" to "代替整个从句：Je le sais. = 我知道这件事。",
            "ce / ça" to "这、那：C'est vrai. Ça va ?",
            "on" to "人们、大家（泛指主语）"
        )
    )
)

@Composable
fun PronounsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // 预热 Mimic 法语 TTS（幂等，非阻塞）
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
            Text("所有代词", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "共 ${pronounGroups.size} 类",
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
                "全部法语代词一览：人称、直接宾语(COD)、间接宾语(COI)、重读、自反、所有格、指示、疑问、关系、副代词(en/y)、泛指、中性代词。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            pronounGroups.forEach { group ->
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
                        Text(
                            group.subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        group.rows.forEachIndexed { index, (pronoun, meaning) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    pronoun,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    meaning,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        Espeak.ensureInitialized(context)
                                        Espeak.speakWithFeedback(context, pronoun)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "朗读 $pronoun",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (index < group.rows.size - 1) {
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