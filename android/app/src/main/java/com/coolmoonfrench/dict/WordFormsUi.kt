package com.coolmoonfrench.dict

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 取得某个词的「带冠词 / 名词化」形式：
 * 命中 [FormsService] 缓存则即时返回；否则先用 [WordForms] 离线兜底显示，AI 结果返回后自动替换。
 */
@Composable
fun rememberWordForms(
    word: String,
    pos: String,
    aiPrefs: AIPreferences?,
    conjugator: VerbConjugator? = null
): List<WordForm> {
    val w = word.trim()
    val p = pos.trim()
    if (w.isEmpty()) return emptyList()
    val config = aiPrefs?.modelConfig
    val configured = IpaService.isConfigured(config)
    var forms by remember(w, p) { mutableStateOf(FormsService.cached(w, p)) }
    LaunchedEffect(w, p) {
        if (forms != null || !configured) return@LaunchedEffect
        val res = FormsService.lookup(config!!, w, p)
        if (res.isNotEmpty()) forms = res
    }
    return forms ?: WordForms.generate(w, p, conjugator)
}

/** 渲染「冠词 / 名词化」形式列表：每条形式一行，下面/右侧跟带连诵的音标。 */
@Composable
fun FormsList(forms: List<WordForm>, textColor: Color, modifier: Modifier = Modifier) {
    if (forms.isEmpty()) return
    Column(modifier = modifier) {
        Text("冠词 / 名词化", fontSize = 11.sp, color = textColor.copy(alpha = 0.7f))
        Spacer(Modifier.height(2.dp))
        forms.forEach { f ->
            Row(
                modifier = Modifier.padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(f.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                if (f.ipa.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text("/${f.ipa}/", fontSize = 12.sp, color = textColor.copy(alpha = 0.7f))
                }
            }
        }
    }
}
