package com.tgwsmobile.proxy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tgwsmobile.proxy.R

/** Name-to-enum helper used to read stored preference strings safely. */
inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
    enumValues<T>().firstOrNull { it.name == this }

/** The eight looks of the app. Each is a full palette, not a recolour of one another. */
enum class ThemeId {
    Neon, Midnight, Aurora, Cyberpunk, Ocean, Emerald, Minimal, Glass;

    val labelRes: Int get() = when (this) {
        Neon -> R.string.theme_neon
        Midnight -> R.string.theme_midnight
        Aurora -> R.string.theme_aurora
        Cyberpunk -> R.string.theme_cyberpunk
        Ocean -> R.string.theme_ocean
        Emerald -> R.string.theme_emerald
        Minimal -> R.string.theme_minimal
        Glass -> R.string.theme_glass
    }
}

/** How containers are drawn. */
enum class CardStyle { Soft, Raised, Sharp, Glass;

    val labelRes: Int get() = when (this) {
        Soft -> R.string.card_soft
        Raised -> R.string.card_raised
        Sharp -> R.string.card_sharp
        Glass -> R.string.card_glass
    }
}

/** How buttons are drawn. */
enum class ButtonStyle { Pill, Rounded, Outline, Glow;

    val labelRes: Int get() = when (this) {
        Pill -> R.string.btn_pill
        Rounded -> R.string.btn_rounded
        Outline -> R.string.btn_outline
        Glow -> R.string.btn_glow
    }
}

/** Opening animation. [Off] skips it completely. */
enum class LaunchAnim { Fade, Reveal, NeonPulse, Scan, Stagger, Quick, Off;

    val labelRes: Int get() = when (this) {
        Fade -> R.string.anim_fade
        Reveal -> R.string.anim_reveal
        NeonPulse -> R.string.anim_neon_pulse
        Scan -> R.string.anim_scan
        Stagger -> R.string.anim_stagger
        Quick -> R.string.anim_quick
        Off -> R.string.anim_off
    }
}

/** Animation intensity multiplier. */
enum class AnimLevel(val scale: Float) { Calm(1.35f), Normal(1f), Brisk(0.65f), Off(0f);

    val labelRes: Int get() = when (this) {
        Calm -> R.string.anim_calm
        Normal -> R.string.anim_normal
        Brisk -> R.string.anim_brisk
        Off -> R.string.anim_level_off
    }
}

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
    /** Second background colour for gradient themes; null means a flat background. */
    val backgroundAlt: Color? = null,
    /** Surfaces let the background bleed through (glassmorphism). */
    val glass: Boolean = false,
)

private val classic = Colors(
    background = Color(0xFF06080C), surface = Color(0xFF0D121A), surfaceRaised = Color(0xFF141B26),
    outline = Color(0xFF223042), textPrimary = Color(0xFFEAF0F8), textMuted = Color(0xFF7C889E),
    online = Color(0xFF5BF0C4), connecting = Color(0xFF7FA9FF), error = Color(0xFFFF6F7D), idle = Color(0xFF56637A),
)

private val neonColors = Colors(
    background = Color(0xFF09060B), surface = Color(0xFF13090F), surfaceRaised = Color(0xFF1B0E17),
    outline = Color(0xFF3A1B2D), textPrimary = Color(0xFFFFEEF5), textMuted = Color(0xFFA18694),
    online = Color(0xFFFF4FA0), connecting = Color(0xFFFF8AC4), error = Color(0xFFFF5C7A), idle = Color(0xFF5C4555),
    backgroundAlt = Color(0xFF170A1E),
)

private val midnightColors = Colors(
    background = Color(0xFF000000), surface = Color(0xFF0A0A0C), surfaceRaised = Color(0xFF131316),
    outline = Color(0xFF232328), textPrimary = Color(0xFFE8E8EA), textMuted = Color(0xFF6E6E76),
    online = Color(0xFFE8E8EA), connecting = Color(0xFF9A9AA2), error = Color(0xFFFF5C5C), idle = Color(0xFF3C3C42),
)

private val auroraColors = Colors(
    background = Color(0xFF041018), backgroundAlt = Color(0xFF0A1E2C),
    surface = Color(0xFF0B1B26), surfaceRaised = Color(0xFF102534),
    outline = Color(0xFF1E3D50), textPrimary = Color(0xFFE4F6FF), textMuted = Color(0xFF7FA0B2),
    online = Color(0xFF66F0D6), connecting = Color(0xFF7AB8FF), error = Color(0xFFFF6F7D), idle = Color(0xFF4A6577),
)

private val cyberpunkColors = Colors(
    background = Color(0xFF0B0414), backgroundAlt = Color(0xFF160A24),
    surface = Color(0xFF140922), surfaceRaised = Color(0xFF1D0F31),
    outline = Color(0xFF3D1E63), textPrimary = Color(0xFFF6E9FF), textMuted = Color(0xFF9E82BC),
    online = Color(0xFFB44FFF), connecting = Color(0xFFFF5CD0), error = Color(0xFFFF4F6E), idle = Color(0xFF594070),
)

private val oceanColors = Colors(
    background = Color(0xFF030B14), backgroundAlt = Color(0xFF06182B),
    surface = Color(0xFF081521), surfaceRaised = Color(0xFF0C1F31),
    outline = Color(0xFF17364E), textPrimary = Color(0xFFE2F1FB), textMuted = Color(0xFF7C99AD),
    online = Color(0xFF41B6F0), connecting = Color(0xFF6ED0F5), error = Color(0xFFFF6F7D), idle = Color(0xFF48627A),
)

private val emeraldColors = Colors(
    background = Color(0xFF050B08), surface = Color(0xFF0A1410), surfaceRaised = Color(0xFF0F1D17),
    outline = Color(0xFF1C3629), textPrimary = Color(0xFFE6F6EC), textMuted = Color(0xFF7FA38E),
    online = Color(0xFF34D399), connecting = Color(0xFF7BD8B4), error = Color(0xFFFF6F7D), idle = Color(0xFF4A6656),
)

private val minimalColors = Colors(
    background = Color(0xFF0E0E10), surface = Color(0xFF161618), surfaceRaised = Color(0xFF1E1E21),
    outline = Color(0xFF2B2B2F), textPrimary = Color(0xFFECECEE), textMuted = Color(0xFF808086),
    online = Color(0xFFD8D8DC), connecting = Color(0xFF9C9CA4), error = Color(0xFFFF5C5C), idle = Color(0xFF44444A),
)

private val glassColors = Colors(
    background = Color(0xFF0A0E1A), backgroundAlt = Color(0xFF141B33),
    surface = Color(0xCC121A2E), surfaceRaised = Color(0xB31B2540),
    outline = Color(0x59FFFFFF), textPrimary = Color(0xFFF0F4FF), textMuted = Color(0xFF97A3C0),
    online = Color(0xFF7FA9FF), connecting = Color(0xFFA9C6FF), error = Color(0xFFFF6F7D), idle = Color(0xFF5C6B8C),
    glass = true,
)

private val palettes: Map<ThemeId, Colors> = mapOf(
    ThemeId.Neon to neonColors,
    ThemeId.Midnight to midnightColors,
    ThemeId.Aurora to auroraColors,
    ThemeId.Cyberpunk to cyberpunkColors,
    ThemeId.Ocean to oceanColors,
    ThemeId.Emerald to emeraldColors,
    ThemeId.Minimal to minimalColors,
    ThemeId.Glass to glassColors,
)

/**
 * Observable style state. [theme] and the customization values are set once from
 * MainActivity with SideEffect; every getter reads them, so a change recomposes
 * everything that draws a colour or a shape. Never set them from a getter.
 */
object Palette {
    var theme by mutableStateOf(ThemeId.Emerald)
    var glow by mutableFloatStateOf(0.5f)
    var cardStyle by mutableStateOf(CardStyle.Soft)
    var buttonStyle by mutableStateOf(ButtonStyle.Pill)
    var elementOpacity by mutableFloatStateOf(1f)
    var bgEffects by mutableStateOf(true)

    /** Backwards-compatible alias: the old neon toggle. */
    val neon: Boolean get() = theme == ThemeId.Neon
    private val current: Colors get() = palettes.getValue(theme)

    val Background: Color get() = current.background
    val BackgroundAlt: Color get() = current.backgroundAlt ?: current.background
    val Surface: Color get() = if (current.glass) current.surface.copy(alpha = current.surface.alpha * elementOpacity) else current.surface.copy(alpha = elementOpacity)
    val SurfaceOpaque: Color get() = current.surface
    val SurfaceRaised: Color get() = if (current.glass) current.surfaceRaised.copy(alpha = current.surfaceRaised.alpha * elementOpacity) else current.surfaceRaised.copy(alpha = elementOpacity)
    val Outline: Color get() = current.outline
    val TextPrimary: Color get() = current.textPrimary
    val TextMuted: Color get() = current.textMuted
    val Online: Color get() = current.online
    val Connecting: Color get() = current.connecting
    val Error: Color get() = current.error
    val Idle: Color get() = current.idle

    /** True when surfaces are translucent and the background gradient shows through. */
    val isGlass: Boolean get() = current.glass

    /** Swatch colours for the theme preview chips. */
    fun swatch(t: ThemeId): List<Color> = palettes.getValue(t).let {
        listOf(it.background, it.surfaceRaised, it.online, it.connecting)
    }
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
        surface = Palette.SurfaceOpaque,
        surfaceVariant = Palette.SurfaceOpaque,
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
