package app.yomu.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.yomu.Yomu
import app.yomu.core.ThemeMode

private val Accent = Color(0xFFFF6B4A)

@Composable
fun YomuTheme(content: @Composable () -> Unit) {
    val prefs = Yomu.prefs
    val mode by prefs.themeMode.flow.collectAsState()
    val amoled by prefs.amoledBlack.flow.collectAsState()
    val dynamic by prefs.dynamicColor.flow.collectAsState()
    val context = LocalContext.current

    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    var scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Accent, secondary = Accent)
        else -> lightColorScheme(primary = Accent, secondary = Accent)
    }
    // The Pixel 8a has an OLED panel: true black saves battery and looks better at night.
    if (dark && amoled) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0B0B),
            surfaceContainer = Color(0xFF121212),
        )
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
