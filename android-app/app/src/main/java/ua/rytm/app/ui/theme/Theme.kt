package ua.rytm.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Fixed brand palette; wallpaper-derived dynamic color would break parity.

private val DarkColors = darkColorScheme(
    primary = PurpleDark,
    onPrimary = DarkTextStrong,
    secondary = PurpleDark2,
    tertiary = Purple3,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkBg1,
    onSurface = DarkText,
    surfaceVariant = DarkBg2,
    onSurfaceVariant = DarkMuted2,
    surfaceContainer = DarkBg2,
    surfaceContainerHigh = DarkBg3,
    outline = DarkBorder,
    outlineVariant = DarkBorder2,
    error = RedDark,
    onError = DarkTextStrong,
)

private val LightColors = lightColorScheme(
    primary = PurpleLight2,
    onPrimary = LightBg1,
    secondary = Purple3,
    tertiary = PurpleDark,
    background = LightBg,
    onBackground = LightText,
    surface = LightBg1,
    onSurface = LightText,
    surfaceVariant = LightBg2,
    onSurfaceVariant = LightMuted2,
    surfaceContainer = LightBg2,
    surfaceContainerHigh = LightBg3,
    outline = LightBorder,
    outlineVariant = LightBorder2,
    error = RedLight2,
    onError = LightBg1,
)

@Composable
fun RytmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
