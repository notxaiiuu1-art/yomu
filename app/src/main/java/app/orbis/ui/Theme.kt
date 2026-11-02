package app.orbis.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbis.data.ThemeMode

/** Accent palettes: each one is a two-stop gradient used for the balance card and highlights. */
data class Accent(val name: String, val a: Color, val b: Color)

val Accents = listOf(
    Accent("Nebula", Color(0xFF7C5CFF), Color(0xFF2DD4BF)),
    Accent("Sunset", Color(0xFFFF5F6D), Color(0xFFFFC371)),
    Accent("Ocean", Color(0xFF2563EB), Color(0xFF06B6D4)),
    Accent("Forest", Color(0xFF059669), Color(0xFFA3E635)),
    Accent("Candy", Color(0xFFEC4899), Color(0xFF8B5CF6)),
    Accent("Gold", Color(0xFFF59E0B), Color(0xFFEF4444)),
)

/** Colors for avatars, goals and accounts. */
val Swatches = listOf(
    Color(0xFF7C5CFF), Color(0xFF2DD4BF), Color(0xFFFF8A4C), Color(0xFFEC4899),
    Color(0xFF3B82F6), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFEF4444),
)

fun swatch(i: Int) = Swatches[((i % Swatches.size) + Swatches.size) % Swatches.size]

@Immutable
data class OrbisColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val accent: Accent,
) {
    val gradient: Brush get() = Brush.linearGradient(listOf(accent.a, accent.b))
}

val LocalOrbis = staticCompositionLocalOf {
    OrbisColors(
        true, Color.Black, Color.DarkGray, Color.Gray, Color.Gray, Color.White, Color.LightGray,
        Color.Green, Color.Red, Color.Yellow, Accents[0],
    )
}

object O {
    val c: OrbisColors @Composable get() = LocalOrbis.current
}

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun OrbisTheme(mode: ThemeMode, accentIndex: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val accent = Accents[accentIndex.coerceIn(0, Accents.lastIndex)]
    val colors = when {
        mode == ThemeMode.AMOLED -> OrbisColors(
            dark = true, bg = Color.Black, surface = Color(0xFF0C0C10), surface2 = Color(0xFF16161D),
            border = Color(0xFF22222C), text = Color(0xFFF5F5FA), muted = Color(0xFF8E8EA0),
            positive = Color(0xFF34D399), negative = Color(0xFFFB7185), warning = Color(0xFFFBBF24), accent = accent,
        )
        dark -> OrbisColors(
            dark = true, bg = Color(0xFF0B0B14), surface = Color(0xFF151522), surface2 = Color(0xFF1E1E2E),
            border = Color(0xFF2A2A3D), text = Color(0xFFF5F5FA), muted = Color(0xFF9393A8),
            positive = Color(0xFF34D399), negative = Color(0xFFFB7185), warning = Color(0xFFFBBF24), accent = accent,
        )
        else -> OrbisColors(
            dark = false, bg = Color(0xFFF4F4F9), surface = Color.White, surface2 = Color(0xFFEDEDF4),
            border = Color(0xFFE0E0EA), text = Color(0xFF12121C), muted = Color(0xFF6B6B80),
            positive = Color(0xFF059669), negative = Color(0xFFE11D48), warning = Color(0xFFD97706), accent = accent,
        )
    }
    val scheme = if (colors.dark) {
        darkColorScheme(
            primary = accent.a, onPrimary = Color.White, secondary = accent.b,
            background = colors.bg, surface = colors.surface, surfaceVariant = colors.surface2,
            onBackground = colors.text, onSurface = colors.text, onSurfaceVariant = colors.muted,
            outline = colors.border, error = colors.negative,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface2,
            surfaceContainerLow = colors.surface, surfaceContainerHighest = colors.surface2,
        )
    } else {
        lightColorScheme(
            primary = accent.a, onPrimary = Color.White, secondary = accent.b,
            background = colors.bg, surface = colors.surface, surfaceVariant = colors.surface2,
            onBackground = colors.text, onSurface = colors.text, onSurfaceVariant = colors.muted,
            outline = colors.border, error = colors.negative,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface2,
            surfaceContainerLow = colors.surface, surfaceContainerHighest = colors.surface2,
        )
    }
    CompositionLocalProvider(LocalOrbis provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(26.dp),
            ),
            content = content,
        )
    }
}
