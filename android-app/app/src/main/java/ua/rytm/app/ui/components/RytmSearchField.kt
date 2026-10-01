package ua.rytm.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import ua.rytm.app.ui.LocalReducedMotion
import ua.rytm.app.ui.icons.Clear
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Search
import ua.rytm.app.ui.theme.RytmRadii

/**
 * The one search bar used everywhere (Finance history, Settings): filled pill,
 * no outline, "<prefix> <hint>" placeholder whose hint word slides through
 * [hints] while the field is empty and unfocused (static under reduced motion).
 */
@Composable
fun RytmSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    prefix: String,
    hints: List<String>,
    clearDescription: String,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    var hintIndex by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val animateHint = value.isEmpty() && !focused && !reducedMotion && hints.size > 1
    LaunchedEffect(animateHint) {
        while (animateHint) {
            delay(2600)
            hintIndex = (hintIndex + 1) % hints.size
        }
    }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(RytmRadii.Pill),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        placeholder = {
            Row {
                Text("$prefix ", maxLines = 1)
                AnimatedContent(
                    targetState = hintIndex % hints.size.coerceAtLeast(1),
                    transitionSpec = {
                        (slideInVertically(tween(420)) { it } + fadeIn(tween(420)))
                            .togetherWith(slideOutVertically(tween(420)) { -it } + fadeOut(tween(300)))
                    },
                    label = "searchHint",
                ) { i -> Text(hints.getOrElse(i) { "" }, maxLines = 1) }
            }
        },
        leadingIcon = { Icon(RytmIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) { Icon(RytmIcons.Clear, contentDescription = clearDescription) }
            }
        },
        singleLine = true,
    )
}
