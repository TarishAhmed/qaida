package `in`.qaida.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import `in`.qaida.app.domain.Mastery

// One accent colour only. Everything else is neutral.
private val Accent = Color(0xFF6FA8DC)
private val AccentDark = Color(0xFF2F6DA8)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0B1A28),
    // Secondary and tertiary roles reuse the accent so Material defaults never add a second hue.
    secondary = Accent,
    onSecondary = Color(0xFF0B1A28),
    secondaryContainer = Color(0xFF1C2A37),
    onSecondaryContainer = Accent,
    tertiary = Accent,
    primaryContainer = Color(0xFF1C2A37),
    onPrimaryContainer = Accent,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB4B4B4),
    outline = Color(0xFF3A3A3A),
)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    secondary = AccentDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8F4),
    onSecondaryContainer = AccentDark,
    tertiary = AccentDark,
    primaryContainer = Color(0xFFDCE8F4),
    onPrimaryContainer = AccentDark,
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFAFAF8),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEDEDEA),
    onSurfaceVariant = Color(0xFF555555),
    outline = Color(0xFFCFCFCB),
)

/** Answer feedback and mastery buckets: the only non-accent colours, each tuned per theme for contrast. */
@Immutable
data class StatusColors(
    val correct: Color,
    val new: Color,
    val red: Color,
    val amber: Color,
    val green: Color,
    val gold: Color,
) {
    fun of(m: Mastery) = when (m) {
        Mastery.NEW -> new
        Mastery.RED -> red
        Mastery.AMBER -> amber
        Mastery.GREEN -> green
        Mastery.GOLD -> gold
    }
}

private val DarkStatus = StatusColors(
    correct = Color(0xFF3E8E5A), new = Color(0xFF2A2A2A), red = Color(0xFF8E3B3B),
    amber = Color(0xFF9A6B1F), green = Color(0xFF2F7A4B), gold = Color(0xFFB8952E),
)
private val LightStatus = StatusColors(
    correct = Color(0xFF4CAF6E), new = Color(0xFFE4E4E0), red = Color(0xFFE59A9A),
    amber = Color(0xFFF0C470), green = Color(0xFF8FD0A5), gold = Color(0xFFE6C55A),
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

@Composable
fun QaidaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
