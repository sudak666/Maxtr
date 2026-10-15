package ua.rytm.app.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * A one-line label that shrinks (down to [minSize]) instead of breaking a
 * word in half at a large system font — month names, metric labels, segment
 * titles. Below [minSize] it ellipsizes.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    minSize: TextUnit = 11.sp,
) {
    val resolved = style.merge(color = if (color != Color.Unspecified) color else style.color.takeOrElse { LocalContentColor.current }, fontWeight = fontWeight)
    BasicText(
        text = text,
        modifier = modifier,
        style = resolved,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = if (style.fontSize.isSpecified) style.fontSize else 16.sp),
    )
}

private inline fun Color.takeOrElse(block: () -> Color): Color = if (this != Color.Unspecified) this else block()
