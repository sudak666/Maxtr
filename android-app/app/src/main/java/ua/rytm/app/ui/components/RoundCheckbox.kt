package ua.rytm.app.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ua.rytm.app.ui.icons.Check
import ua.rytm.app.ui.icons.RytmIcons

/**
 * A circular checkmark indicator matching the app's pill/round visual
 * language (RytmRadii.Pill everywhere else) instead of Material's default
 * square Checkbox. Purely decorative — the caller owns the actual
 * toggle/semantics (see ShiftSelectionRow's `.toggleable(role = Role.Checkbox)`
 * on the parent Row), so this never takes its own onCheckedChange.
 */
@Composable
fun RoundCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    size: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val fill by animateColorAsState(
        if (checked) accent else Color.Transparent,
        animationSpec = tween(120),
        label = "round-checkbox-fill",
    )
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, if (checked) accent else accent.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                RytmIcons.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.64f),
            )
        }
    }
}
