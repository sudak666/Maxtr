package ua.rytm.app.ui.screens.finance
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import ua.rytm.app.R
import ua.rytm.app.ui.localizedDomainText
import kotlinx.coroutines.launch
import ua.rytm.app.data.AutoRulesSyncRepository
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.local.AutoRuleEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoRulesManagerSheet(repository: FinanceRepository, sync: AutoRulesSyncRepository, uid: String, profileId: String, onDismiss: () -> Unit) {
    val rules by repository.autoRules.collectAsState(initial = emptyList())
    val categories by repository.categoriesByType.collectAsState(initial = emptyMap())
    val scope = rememberCoroutineScope()
    var expandedId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var errorVisible by remember { mutableStateOf(false) }
    fun persist(block: suspend () -> Unit) { if (!saving) scope.launch { saving = true; runCatching { block(); sync.save(uid, profileId) }.onFailure { errorVisible = true }; saving = false } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.auto_rules_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.auto_rules_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (errorVisible) Text(stringResource(R.string.auto_rules_save_failed), color = MaterialTheme.colorScheme.error)
            if (rules.isEmpty()) Text(stringResource(R.string.auto_rules_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            rules.forEach { rule ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (rule.keyword.isEmpty()) stringResource(R.string.auto_rules_example) else stringResource(R.string.auto_rules_summary, rule.keyword, localizedDomainText(rule.category)), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(if (rule.type == "income") R.string.tx_income else R.string.tx_expense), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { expandedId = if (expandedId == rule.id) null else rule.id }, enabled = !saving) { Icon(Icons.Filled.Edit, stringResource(R.string.action_edit)) }
                        IconButton(onClick = { pendingDelete = rule.id }, enabled = !saving) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                    }
                    if (expandedId == rule.id) RuleEditor(rule, categories, onUpdate = { persist { repository.updateAutoRule(it) } })
                }
                }
            }
            Button(onClick = { persist { expandedId = repository.addAutoRule().id } }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null); Text(stringResource(R.string.auto_rules_add))
            }
            TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
        }
    }
    pendingDelete?.let { id -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text(stringResource(R.string.auto_rules_delete_title)) }, text = { Text(stringResource(R.string.auto_rules_delete_body)) }, confirmButton = { TextButton(onClick = { persist { repository.deleteAutoRule(id) }; pendingDelete = null }, enabled = !saving) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditor(rule: AutoRuleEntity, categories: Map<TxType, List<String>>, onUpdate: (AutoRuleEntity) -> Unit) {
    val type = if (rule.type == "income") TxType.INCOME else TxType.EXPENSE
    RuleDropdown(R.string.tx_type, listOf("expense", "income"), rule.type, true) { newType ->
        val txType = if (newType == "income") TxType.INCOME else TxType.EXPENSE
        onUpdate(rule.copy(type = newType, category = categories[txType]?.firstOrNull().orEmpty()))
    }
    OutlinedTextField(rule.keyword, { onUpdate(rule.copy(keyword = it)) }, label = { Text(stringResource(R.string.auto_rules_keyword)) }, placeholder = { Text(stringResource(R.string.auto_rules_example)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    RuleDropdown(R.string.tx_category, categories[type].orEmpty(), rule.category, false) { onUpdate(rule.copy(category = it)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDropdown(@StringRes labelRes: Int, options: List<String>, selected: String, typeValues: Boolean, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        val selectedText = if (typeValues) stringResource(if (selected == "income") R.string.tx_income else R.string.tx_expense) else localizedDomainText(selected)
        OutlinedTextField(selectedText, {}, readOnly = true, label = { Text(stringResource(labelRes)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(open, { open = false }) { options.forEach { value -> DropdownMenuItem(text = { Text(if (typeValues) stringResource(if (value == "income") R.string.tx_income else R.string.tx_expense) else localizedDomainText(value)) }, onClick = { onSelect(value); open = false }) } }
    }
}
