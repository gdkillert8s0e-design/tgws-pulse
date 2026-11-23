package com.tgwsmobile.proxy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tgwsmobile.proxy.R

/** Dark-only palette: deep graphite surfaces with one luminous accent per state. */
object Palette {
    val Background = Color(0xFF06080C)
    val Surface = Color(0xFF0D121A)
    val SurfaceRaised = Color(0xFF141B26)
    val Outline = Color(0xFF223042)
    val TextPrimary = Color(0xFFEAF0F8)
    val TextMuted = Color(0xFF7C889E)
    val Online = Color(0xFF5BF0C4)
    val Connecting = Color(0xFF7FA9FF)
    val Error = Color(0xFFFF6F7D)
    val Idle = Color(0xFF56637A)
}

val DisplayFont = FontFamily(
    Font(R.font.unbounded_semibold, FontWeight.SemiBold),
    Font(R.font.unbounded_bold, FontWeight.Bold),
)

val TextFont = FontFamily(
    Font(R.font.onest_regular, FontWeight.Normal),
    Font(R.font.onest_medium, FontWeight.Medium),
    Font(R.font.onest_semibold, FontWeight.SemiBold),
)

val MonoFont = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

private val Scheme = darkColorScheme(
    background = Palette.Background,
    surface = Palette.Surface,
    surfaceVariant = Palette.SurfaceRaised,
    onBackground = Palette.TextPrimary,
    onSurface = Palette.TextPrimary,
    onSurfaceVariant = Palette.TextMuted,
    primary = Palette.Online,
    onPrimary = Palette.Background,
    outline = Palette.Outline,
    error = Palette.Error,
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelSmall = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp),
)

@Composable
fun TgWsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
