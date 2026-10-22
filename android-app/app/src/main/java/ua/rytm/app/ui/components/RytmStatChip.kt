package ua.rytm.app.ui.components

import ua.rytm.app.ui.theme.RytmSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.tabularNums

/**
 * The one chip-stat, ported from the PWA's single `.chip-stat` rule.
 *
 * Three separate copies existed (Shifts, Shopping, Debt) and the third had
 * drifted on shape, background, badge size and type scale. Two of them also
 * used a fixed `Row` of `weight(1f)` cells, which squeezes Ukrainian labels
 * at 360dp / fontScale 1.3 — [RytmStatChipRow] uses a LazyRow, matching what
 * the PWA does.
 */
@Composable
fun RytmStatChip(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        shape = RoundedCornerShape(RytmRadii.Pill),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(horizontal = RytmSpacing.md, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(RytmDimens.IconBadge)
                    // Soft tint, like the category badges (design pass 2): a solid purple
                    // gradient disc in every chip was the loudest thing on the screen.
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(RytmDimens.IconBadgeIcon))
            }
            Column(Modifier.padding(start = 9.dp)) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall.tabularNums(),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Horizontally scrollable chip strip — the PWA's `.chip-stat-row`. */
@Composable
fun RytmStatChipRow(
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    // CenterHorizontally here only centers the group when it's short enough
    // to fit without scrolling (e.g. Shifts' fixed 3 chips) — when content
    // is long enough to need scrolling (long localized labels, large font
    // scale), there's no leftover space left to center into and this has no
    // effect, so the scrollable-row protection this component exists for
    // (see the doc comment above) is untouched. Fixes a real live report: 3
    // short chips sat flush-left with dead space on the right, reading as
    // "off-center" rather than a deliberately left-aligned group.
    val fadeState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyRow(
        modifier.fillMaxWidth().fadingEdges(fadeState),
        state = fadeState,
        horizontalArrangement = FillOrScrollArrangement,
        content = content,
    )
}

/**
 * P5: centering left the short Shifts row with ~64dp side margins, off the
 * 16dp grid every other block uses, while flush-left left dead space on the
 * right (the earlier live report). When the chips fit, the outer chips now sit
 * on the content edges and the leftover space is shared between them; when they
 * don't fit, it's a plain 8dp-spaced scrolling row as before.
 */
private val FillOrScrollArrangement = object : Arrangement.Horizontal {
    override val spacing = RytmSpacing.sm

    override fun androidx.compose.ui.unit.Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        outPositions: IntArray,
    ) {
        val gap = spacing.roundToPx()
        val used = sizes.sum() + gap * (sizes.size - 1).coerceAtLeast(0)
        if (used >= totalSize || sizes.size < 2) {
            with(Arrangement.spacedBy(spacing)) { arrange(totalSize, sizes, layoutDirection, outPositions) }
        } else {
            with(Arrangement.SpaceBetween) { arrange(totalSize, sizes, layoutDirection, outPositions) }
        }
    }
}
