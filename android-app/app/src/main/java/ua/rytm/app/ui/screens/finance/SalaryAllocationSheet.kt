package ua.rytm.app.ui.screens.finance

import ua.rytm.app.ui.theme.RytmSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.ui.components.RytmModalBottomSheet
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii

/** Payday: move suggested amounts from the pay's wallet to each goal's wallet, as transfers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryAllocationSheet(plan: SalaryAllocation, wallets: List<Wallet>, onApply: (List<SalaryAutopilot.GoalShare>) -> Unit, onDismiss: () -> Unit) {
    val amounts = remember(plan) {
        mutableStateMapOf<String, String>().apply { plan.shares.forEach { put(it.goalId, java.math.BigDecimal.valueOf(it.amount).stripTrailingZeros().toPlainString()) } }
    }
    val selected = remember(plan) { mutableStateMapOf<String, Boolean>().apply { plan.shares.forEach { put(it.goalId, true) } } }
    val chosen = plan.shares.filter { selected[it.goalId] == true }
        .mapNotNull { s -> parseMoneyInput(amounts[s.goalId].orEmpty())?.takeIf { it > 0 }?.let { s.copy(amount = it) } }
    val symbol = currencySymbol(plan.currency)
    RytmModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = RytmSpacing.xl, vertical = RytmSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RytmSpacing.md),
        ) {
            RytmSheetTitle(stringResource(R.string.salary_allocation_title))
            Text(stringResource(R.string.salary_allocation_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            plan.shares.forEach { share ->
                val wallet = wallets.firstOrNull { it.id == share.walletId }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                    Checkbox(checked = selected[share.goalId] == true, onCheckedChange = { selected[share.goalId] = it })
                    Text(
                        wallet?.let { localizedDomainText(it.name) } ?: "—",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = amounts[share.goalId].orEmpty(),
                        onValueChange = { amounts[share.goalId] = it },
                        singleLine = true,
                        suffix = { Text(symbol) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(RytmRadii.Input),
                        modifier = Modifier.weight(0.8f),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm, Alignment.End)) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = RytmDimens.TouchTarget)) { Text(stringResource(R.string.salary_allocation_skip)) }
                Button(
                    onClick = { onApply(chosen) },
                    enabled = chosen.isNotEmpty(),
                    shape = RoundedCornerShape(RytmRadii.Row),
                    modifier = Modifier.heightIn(min = RytmDimens.TouchTarget),
                ) { Text(stringResource(R.string.salary_allocation_apply, "${formatMoney(chosen.sumOf { it.amount })} $symbol")) }
            }
        }
    }
}
