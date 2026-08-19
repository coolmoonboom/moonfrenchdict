package com.moonfrench.dict

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF546E7A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFD8DC),
    onPrimaryContainer = Color(0xFF1C313A),
    secondary = Color(0xFF6D8A96),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0EFF5),
    onSecondaryContainer = Color(0xFF1E3A45),
    tertiary = Color(0xFF7CB342),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8F5D6),
    onTertiaryContainer = Color(0xFF2E4A15),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90A4AE),
    onPrimary = Color(0xFF1C313A),
    primaryContainer = Color(0xFF37474F),
    onPrimaryContainer = Color(0xFFCFD8DC),
    secondary = Color(0xFF90B4C0),
    onSecondary = Color(0xFF1E3A45),
    secondaryContainer = Color(0xFF2D4F5C),
    onSecondaryContainer = Color(0xFFE0EFF5),
    tertiary = Color(0xFF9CCC65),
    onTertiary = Color(0xFF2E4A15),
    tertiaryContainer = Color(0xFF43691E),
    onTertiaryContainer = Color(0xFFE8F5D6),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)

@Composable
fun FrenchDictTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}