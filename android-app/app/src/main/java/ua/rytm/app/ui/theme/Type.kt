package ua.rytm.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ua.rytm.app.R

// Ported from index.html's --text-* / --weight-* scale (ANDROID_MIGRATION.md
// §4). Font family: Inter (design-audit §5.8's blocked "brand font" decision,
// resolved — the PWA's own font-family stack already names Inter as its
// chosen webfont after the Apple-only SF Pro fallback
// (index.html:189: -apple-system,BlinkMacSystemFont,'SF Pro Display','Inter',
// sans-serif), so this isn't a new arbitrary choice, it's applying the app's
// existing intended brand typeface to the platform (Android/Roboto) that
// never got it. Vendored as static TTFs under res/font/ (OFL, see
// ../../../../../LICENSE-Inter.txt at the android-app root) rather than
// downloadable-fonts API: this is an offline-first finance app, so a
// network-fetched font with FOUT/async-swap risk is the wrong tradeoff.
// The npm-distributed @fontsource/inter package only ships per-script
// (latin/cyrillic/greek/...) CSS-subsetted files meant for unicode-range
// @font-face chaining — unusable for a single Android font resource, which
// needs full glyph coverage in one file. Used inter-ui's unsubsetted "web"
// build instead (full Latin+Cyrillic+Greek+Vietnamese per file), converted
// from its woff2 distribution to ttf (Android's res/font only accepts
// ttf/otf/ttc, not woff2).
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_black, FontWeight.Black),
)
//
// Three things this scale gets right that the first port did not:
//  1. `headline*` are DEFINED. They were left at M3's Roboto defaults while
//     five sheets used headlineSmall for their title and others used
//     titleLarge/titleMedium — three different sizes (24/22/16sp) and two
//     type styles for the same element.
//  2. Every style carries an explicit `lineHeight` (~1.3-1.4x) and
//     `letterSpacing`. Without them Compose falls back to font metrics, which
//     gave an uneven vertical rhythm for Ukrainian diacritics (й, ї, ґ) and
//     for the multi-line descriptions in Settings — already being patched
//     locally with `.copy(fontSize = …, lineHeight = …)` in two places.
//  3. The bottom of the scale is not sub-12sp. `labelSmall` was 10.5sp and
//     `labelMedium` 11.5sp, used for tab labels, transaction dates, chip
//     captions and the calendar's day numbers — while the PWA this is a port
//     of had every sub-12px size raised to 12px and then shifted +1 again
//     (CLAUDE.md, "Mobile UI redesign → Font sizes"), i.e. its smallest real
//     size is 13px. The port had inverted that decision.
private val WeightBody = FontWeight.Medium // --weight-body: 500
private val WeightStrong = FontWeight.Bold // --weight-strong: 700
private val WeightDisplay = FontWeight.ExtraBold // --weight-display: 800
private val WeightMega = FontWeight.Black // --weight-mega: 900

/**
 * Tabular figures. Proportional digits make a right-aligned column of money
 * values jitter horizontally as the digits change; every competitor app this
 * was benchmarked against uses tabular figures for amounts.
 * Apply with `style.copy(fontFeatureSettings = TabularFigures)` or use
 * [tabularNums].
 */
const val TabularFigures = "tnum"

/** [TextStyle] with tabular (fixed-width) digits — use for money values. */
fun TextStyle.tabularNums(): TextStyle = copy(fontFeatureSettings = TabularFigures)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontWeight = WeightMega, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp), // --text-2xl
    displayMedium = TextStyle(fontFamily = Inter, fontWeight = WeightDisplay, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp), // --text-xl
    displaySmall = TextStyle(fontFamily = Inter, fontWeight = WeightStrong, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp), // --text-lg
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = WeightDisplay, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = WeightDisplay, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = WeightStrong, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = WeightStrong, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = WeightStrong, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp), // --text-md
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = WeightStrong, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp), // --text-base
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = WeightBody, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = WeightBody, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = WeightBody, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.15.sp), // --text-sm
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = WeightStrong, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = WeightBody, fontSize = 13.5.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp), // --text-xs
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = WeightBody, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp), // --text-2xs
)
