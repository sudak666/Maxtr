package ua.rytm.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.ui.icons.Delete
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmSpacing

// Last row of an expanded manager card. Delete lives here rather than in the
// card header, where it sat right next to the expand chevron.
@Composable
fun RytmCardDeleteButton(onClick: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.heightIn(min = RytmDimens.TouchTarget),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(RytmIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(RytmSpacing.sm))
            Text(stringResource(R.string.action_delete))
        }
    }
}
