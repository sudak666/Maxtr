package ua.rytm.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Ported from index.html's --text-* / --weight-* scale (ANDROID_MIGRATION.md
// §4). Font family stays system default until brand fonts (if any) are
// vendored — the PWA itself uses the platform default stack too.

private val WeightBody = FontWeight.Medium // --weight-body: 500
private val WeightStrong = FontWeight.Bold // --weight-strong: 700
private val WeightDisplay = FontWeight.ExtraBold // --weight-display: 800
private val WeightMega = FontWeight.Black // --weight-mega: 900

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = WeightMega, fontSize = 36.sp), // --text-2xl
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = WeightDisplay, fontSize = 30.sp), // --text-xl
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = WeightStrong, fontSize = 22.sp), // --text-lg
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = WeightStrong, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = WeightStrong, fontSize = 16.sp), // --text-md
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = WeightStrong, fontSize = 14.sp), // --text-base
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = WeightBody, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = WeightBody, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = WeightBody, fontSize = 13.sp), // --text-sm
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = WeightStrong, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = WeightBody, fontSize = 11.5.sp), // --text-xs
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = WeightBody, fontSize = 10.5.sp), // --text-2xs
)
