package ua.rytm.app.ui.screens.finance

import ua.rytm.app.ui.theme.RytmSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.ui.icons.Repeat
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii

/** "Looks like a subscription" card on Finance: one tap turns it into a recurring payment. */
@Composable
fun SubscriptionSuggestionsCard(
    candidates: List<SubscriptionDetector.Candidate>,
    onAccept: (SubscriptionDetector.Candidate) -> Unit,
    onDismiss: (SubscriptionDetector.Candidate) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RytmRadii.Card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(Modifier.padding(RytmSpacing.lg), verticalArrangement = Arrangement.spacedBy(RytmSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(RytmIcons.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(RytmSpacing.sm))
                Text(stringResource(R.string.subscription_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.subscription_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Three at most; the rest show up once these are answered.
            candidates.take(3).forEach { c ->
                val frequency = stringResource(if (c.frequency == "weekly") R.string.frequency_weekly else R.string.frequency_monthly)
                Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs)) {
                    Text(
                        "${c.comment} · ${ua.rytm.app.ui.maskedAmount("${formatMoney(c.amount)} ${currencySymbol(c.currency)}")}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.subscription_pattern, frequency, androidx.compose.ui.res.pluralStringResource(R.plurals.subscription_times, c.occurrences, c.occurrences)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm, Alignment.End)) {
                        TextButton(onClick = { onDismiss(c) }, modifier = Modifier.heightIn(min = RytmDimens.TouchTarget)) {
                            Text(stringResource(R.string.subscription_no))
                        }
                        Button(onClick = { onAccept(c) }, shape = RoundedCornerShape(RytmRadii.Control), modifier = Modifier.heightIn(min = RytmDimens.TouchTarget)) {
                            Text(stringResource(R.string.subscription_add))
                        }
                    }
                }
            }
        }
    }
}
