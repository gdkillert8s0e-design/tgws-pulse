package com.tgwsmobile.proxy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tgwsmobile.proxy.R

/**
 * Two dark styles: the classic green one and the neon pink one. [neon] is observable
 * state, and every getter reads it, so switching style recomposes everything that
 * uses a colour. Set it from settings with SideEffect, never from a getter.
 */
object Palette {
    var neon by mutableStateOf(false)

    private class Colors(
        val background: Color,
        val surface: Color,
        val surfaceRaised: Color,
        val outline: Color,
        val textPrimary: Color,
        val textMuted: Color,
        val online: Color,
        val connecting: Color,
        val error: Color,
        val idle: Color,
    )

    private val classic = Colors(
        background = Color(0xFF06080C), surface = Color(0xFF0D121A), surfaceRaised = Color(0xFF141B26),
        outline = Color(0xFF223042), textPrimary = Color(0xFFEAF0F8), textMuted = Color(0xFF7C889E),
        online = Color(0xFF5BF0C4), connecting = Color(0xFF7FA9FF), error = Color(0xFFFF6F7D), idle = Color(0xFF56637A),
    )

    // Estimated from the neon reference; refine when the exact photo is provided.
    private val neonColors = Colors(
        background = Color(0xFF09060B), surface = Color(0xFF13090F), surfaceRaised = Color(0xFF1B0E17),
        outline = Color(0xFF3A1B2D), textPrimary = Color(0xFFFFEEF5), textMuted = Color(0xFFA18694),
        online = Color(0xFFFF4FA0), connecting = Color(0xFFFF8AC4), error = Color(0xFFFF5C7A), idle = Color(0xFF5C4555),
    )

    private val current: Colors get() = if (neon) neonColors else classic

    val Background: Color get() = current.background
    val Surface: Color get() = current.surface
    val SurfaceRaised: Color get() = current.surfaceRaised
    val Outline: Color get() = current.outline
    val TextPrimary: Color get() = current.textPrimary
    val TextMuted: Color get() = current.textMuted
    val Online: Color get() = current.online
    val Connecting: Color get() = current.connecting
    val Error: Color get() = current.error
    val Idle: Color get() = current.idle
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

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelSmall = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.1.sp),
)

@Composable
fun TgWsTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
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
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
