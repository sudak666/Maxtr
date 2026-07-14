package ua.zminka.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = ZminkaPurple,
    secondary = ZminkaPurple2,
    tertiary = ZminkaPurple3,
    background = ZminkaBgDark,
    surface = ZminkaBg1Dark,
    surfaceVariant = ZminkaBg2Dark,
    onBackground = ZminkaTextDark,
    onSurface = ZminkaTextDark,
)

private val LightColors = lightColorScheme(
    primary = ZminkaPurple,
    secondary = ZminkaPurple2,
    tertiary = ZminkaPurple3,
    background = ZminkaBgLight,
    surface = ZminkaBg1Light,
    surfaceVariant = ZminkaBg2Light,
    onBackground = ZminkaTextLight,
    onSurface = ZminkaTextLight,
)

@Composable
fun ZminkaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = ZminkaTypography,
        content = content,
    )
}
