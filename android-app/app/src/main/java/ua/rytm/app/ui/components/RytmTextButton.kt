package ua.rytm.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import ua.rytm.app.ui.theme.RytmSemantic

/**
 * TextButton whose default content color is [RytmSemantic.link], not
 * `primary`: in dark theme `primary` text measured 2.78:1 on cards. Use this
 * instead of the raw M3 TextButton; pass `colors` only to override on purpose.
 */
@Composable
fun RytmTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(contentColor = RytmSemantic.link),
    content: @Composable RowScope.() -> Unit,
) = TextButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors, content = content)
