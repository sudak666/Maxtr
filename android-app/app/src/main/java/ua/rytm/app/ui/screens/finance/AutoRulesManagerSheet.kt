package ua.rytm.app.ui.screens.finance

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
    fun persist(block: suspend () -> Unit) { scope.launch { block(); sync.save(uid, profileId) } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Автоматичні правила", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Якщо коментар містить ключове слово — категорія підставляється сама, поки ти вводиш операцію.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (rules.isEmpty()) Text("Немає правил", color = MaterialTheme.colorScheme.onSurfaceVariant)
            rules.forEach { rule ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (rule.keyword.isEmpty()) "напр. Сільпо" else "\"${rule.keyword}\" → ${rule.category}", fontWeight = FontWeight.SemiBold)
                            Text(if (rule.type == "income") "Дохід" else "Витрата", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { expandedId = if (expandedId == rule.id) null else rule.id }) { Icon(Icons.Filled.Edit, "Редагувати") }
                        IconButton(onClick = { pendingDelete = rule.id }) { Icon(Icons.Filled.Delete, "Видалити") }
                    }
                    if (expandedId == rule.id) RuleEditor(rule, categories, onUpdate = { persist { repository.updateAutoRule(it) } })
                }
            }
            Button(onClick = { scope.launch { expandedId = repository.addAutoRule().id; sync.save(uid, profileId) } }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null); Text("Додати правило")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
        }
    }
    pendingDelete?.let { id -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Видалити правило") }, text = { Text("Видалити це правило?") }, confirmButton = { TextButton(onClick = { persist { repository.deleteAutoRule(id) }; pendingDelete = null }) { Text("Видалити", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Скасувати") } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditor(rule: AutoRuleEntity, categories: Map<TxType, List<String>>, onUpdate: (AutoRuleEntity) -> Unit) {
    val type = if (rule.type == "income") TxType.INCOME else TxType.EXPENSE
    RuleDropdown("Тип", listOf("expense", "income"), rule.type) { newType ->
        val txType = if (newType == "income") TxType.INCOME else TxType.EXPENSE
        onUpdate(rule.copy(type = newType, category = categories[txType]?.firstOrNull().orEmpty()))
    }
    OutlinedTextField(rule.keyword, { onUpdate(rule.copy(keyword = it)) }, label = { Text("Слово в коментарі") }, placeholder = { Text("напр. Сільпо") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    RuleDropdown("Категорія", categories[type].orEmpty(), rule.category) { onUpdate(rule.copy(category = it)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(if (label == "Тип") (if (selected == "income") "Дохід" else "Витрата") else selected, {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(open, { open = false }) { options.forEach { value -> DropdownMenuItem(text = { Text(if (label == "Тип") (if (value == "income") "Дохід" else "Витрата") else value) }, onClick = { onSelect(value); open = false }) } }
    }
}
