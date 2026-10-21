// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import io.github.mircodhd.mvplayer.fonts.FontLibrary

// Fallback palette (Android 11, no dynamic color): muted crimson on warm dark greys.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A8),
    onPrimary = Color(0xFF561E16),
    primaryContainer = Color(0xFF73342A),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFFE7BDB6),
    onSecondary = Color(0xFF442925),
    secondaryContainer = Color(0xFF5D3F3A),
    onSecondaryContainer = Color(0xFFFFDAD4),
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF1DFDC),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF1DFDC),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BE),
    surfaceContainer = Color(0xFF271D1C),
    surfaceContainerHigh = Color(0xFF322827),
    surfaceContainerHighest = Color(0xFF3D3231),
    outline = Color(0xFFA08C89),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF904A3F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF3A0905),
    secondary = Color(0xFF775651),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD4),
    onSecondaryContainer = Color(0xFF2C1512),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231918),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231918),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
    surfaceContainer = Color(0xFFFCEAE7),
    surfaceContainerHigh = Color(0xFFF7E4E1),
    surfaceContainerHighest = Color(0xFFF1DFDC),
    outline = Color(0xFF857370),
)

/**
 * Material 3 theme; uses the wallpaper-based dynamic palette on Android 12+. [appFont] is
 * a FontLibrary id for every text style, or "system" (or an unknown id) for Android's font.
 */
@Composable
fun MvPlayerTheme(appFont: String = "system", content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val typography = remember(appFont) {
        val family = fontFamily(context, appFont)
        if (family == null) Typography() else Typography().withFont(family)
    }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

private fun fontFamily(context: android.content.Context, id: String): FontFamily? {
    val font = FontLibrary.find(context, id) ?: return null
    return runCatching {
        when {
            font.asset != null -> FontFamily(Font(font.asset, context.assets))
            font.file != null -> FontFamily(Font(font.file))
            else -> null
        }
    }.getOrNull()
}

private fun Typography.withFont(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}
