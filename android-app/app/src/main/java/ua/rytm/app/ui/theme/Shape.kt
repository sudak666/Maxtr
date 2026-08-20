package ua.rytm.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// --radius-row (16px, compact list rows) and --radius-card (22px, hero
// surfaces/modals/tx rows) from index.html — ANDROID_MIGRATION.md §4.
val RowRadius = 16.dp
val CardRadius = 22.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(RowRadius),
    large = RoundedCornerShape(CardRadius),
    extraLarge = RoundedCornerShape(28.dp),
)
