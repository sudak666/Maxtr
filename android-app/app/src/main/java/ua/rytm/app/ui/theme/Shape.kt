package ua.rytm.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Shared radii for compact rows, cards, modals and transaction surfaces.
val RowRadius = RytmRadii.Row
val CardRadius = RytmRadii.Card

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(RytmRadii.Control),
    small = RoundedCornerShape(RowRadius),
    medium = RoundedCornerShape(RowRadius),
    large = RoundedCornerShape(CardRadius),
    extraLarge = RoundedCornerShape(RytmRadii.Sheet),
)
