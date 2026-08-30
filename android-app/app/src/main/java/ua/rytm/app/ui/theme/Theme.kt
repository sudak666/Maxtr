package ua.rytm.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
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
    // surfaceContainerLow was never part of this app's own deliberate
    // 3-tier surface system (surface/surfaceContainer/surfaceContainerHigh
    // below) -- left to M3's default tonal-palette derivation, which reads
    // as barely distinguishable from `background` in dark theme specifically
    // (reported live, screenshot: Finance's quick-action cards nearly
    // invisible). Pinned to the same tone as surfaceContainer, which is
    // already proven visible everywhere else it's used.
    surfaceContainerLow = DarkBg2,
    surfaceContainer = DarkBg2,
    surfaceContainerHigh = DarkBg3,
    // Was DarkBorder (#403F45) -- barely distinguishable from surface/
    // surfaceContainerHigh in practice, the same near-1:1-contrast gap
    // patched six separate times this session (Switch thumb, budget field,
    // Shopping checkbox, quick-action/converter cards, salary goal field)
    // before finally fixing it here instead of at each call site. This is
    // the color M3 defaults every OutlinedTextField's unfocused border and
    // many hairline Card borders to app-wide, so this one change covers
    // every remaining/future instance of the same bug.
    // Was then bumped all the way to DarkMuted2 (#98979E) to guarantee
    // visibility -- but that's the exact same brightness as onSurfaceVariant
    // (muted body text), so every field/border using it read as a bold,
    // assertive line rather than a quiet structural hairline (flagged live:
    // "мені здається треба щоб вони були трішки не такі різкі"). Top-tier
    // competitor fintech UIs (Revolut, Monobank, Wise) keep real borders
    // thin/quiet and reserve strong contrast for content, not chrome.
    // DarkHairline sits at the midpoint between this and outlineVariant --
    // still comfortably visible against every surface tier, just no longer
    // as loud as body text. outlineVariant itself (deliberately fainter,
    // used for secondary distinctions like the Switch's own
    // unchecked-border and calendar out-of-month cells) is untouched.
    outline = DarkHairline,
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
    surfaceContainerLow = LightBg2,
    surfaceContainer = LightBg2,
    surfaceContainerHigh = LightBg3,
    // See DarkColors' `outline` doc comment -- same softened-hairline reasoning.
    outline = LightHairline,
    outlineVariant = LightBorder2,
    error = RedLight2,
    onError = LightBg1,
)

/**
 * Every `Switch` in the app needs this — M3's own default unchecked-thumb
 * color is `colorScheme.outline`, but this app's `outline` is tuned for
 * hairline card borders (`LightBorder` = #E4E4E9, barely darker than
 * `surfaceContainerHigh`/`LightBg3` = #E2E0DD it sits on), not a control
 * that needs to read as a real filled circle — an OFF switch rendered as a
 * near-invisible pale oval with no visible thumb at all.
 *
 * This exact bug, exact root cause, and exact fix already existed once in
 * `SettingsScreen.kt`'s `SettingsRow` (its own `SwitchDefaults.colors(...)`
 * override, live-verified there) — it just never got extracted into
 * something the other 4 `Switch` call sites (WidgetsManagerSheet,
 * NotificationSettingsSheet, ShiftsScreen, PinSettingsSheet) could reuse,
 * so they kept the invisible-thumb bug `SettingsScreen` had already fixed
 * for itself. This is that fix, pulled out once so it can't drift again;
 * `SettingsScreen` additionally overrides its *checked*-state colors on
 * top of this for its own reasons and keeps its own `SwitchDefaults.colors`
 * call for that, but uses these same three unchecked-state values.
 */
@Composable
fun rytmSwitchColors(): SwitchColors = SwitchDefaults.colors(
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
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
