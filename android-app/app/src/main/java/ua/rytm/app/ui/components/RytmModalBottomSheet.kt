package ua.rytm.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Use this instead of ModalBottomSheet. M3 1.4 pads the content by the status
// bar minus the sheet's current offset, so near the top the sheet's height
// depends on its own position: when the content is ~screen-tall, every frame
// moves the Expanded anchor and the sheet bounces forever after a swipe up
// (seen on the A51, 2026-10-14). A fixed statusBarsPadding on the sheet plus
// zero content insets keeps the height constant.
// The nav bar padding sits outside the content so lists stop above the system
// buttons instead of scrolling under them. It consumes the inset, so a
// navigationBarsPadding() inside a sheet adds nothing; imePadding() still works.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RytmModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    tonalElevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.statusBarsPadding().then(modifier),
        sheetState = sheetState,
        shape = shape,
        tonalElevation = tonalElevation,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(Modifier.navigationBarsPadding(), content = content)
    }
}
