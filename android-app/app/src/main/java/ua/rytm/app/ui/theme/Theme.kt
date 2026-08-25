package ua.rytm.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Fixed brand palette — dynamicColor (Material You wallpaper theming) is
// deliberately NOT used, same as the PWA never adapts to OS accent color.
// See ANDROID_MIGRATION.md §4.

private val DarkColors = darkColorScheme(
    primary = PurpleDark,
    // White on #8B5CF6 is 4.23:1 — AA for the >=14sp bold labels this pair is
    // actually used for (WCAG large-text threshold), and deliberately not
    // "fixed" by darkening `primary`: that would push primary-tinted text on
    // the dark background the other way (3.66:1 -> 2.55:1).
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
    // Not white: white on #EF4444 measures 3.76:1, below AA for the bold
    // labels/icons actually drawn on it. Near-black gets 4.53:1.
    onError = LightText,
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
