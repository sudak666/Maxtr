package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.data.FinanceRepository

// Mirrors js/settings-managers.js's categories-modal, including the
// toggleSubcatPanel()/addSubcategory()/deleteSubcategory() subcategory panel
// (icons/budgets still out of scope — see CategoriesManagerViewModel's doc comment).
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun CategoriesManagerSheet(
    repository: FinanceRepository,
    onDismiss: () -> Unit,
    viewModel: CategoriesManagerViewModel = viewModel(factory = CategoriesManagerViewModel.factory(repository)),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newName by remember { mutableStateOf("") }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var newSubName by remember { mutableStateOf("") }
    var pendingDeleteSub by remember { mutableStateOf<Pair<String, String>?>(null) } // (categoryName, subName)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Категорії", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = viewModel.activeType == TxType.EXPENSE, onClick = { viewModel.setType(TxType.EXPENSE) }, label = { Text("Витрата") })
                FilterChip(selected = viewModel.activeType == TxType.INCOME, onClick = { viewModel.setType(TxType.INCOME) }, label = { Text("Дохід") })
            }

            viewModel.errorMessage?.let { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = viewModel::consumeError) { Icon(Icons.Filled.Close, contentDescription = null) }
                }
            }

            viewModel.categories.forEach { (id, name) ->
                val expanded = viewModel.expandedCategoryId == id
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CategoryIconBadge(
                            name,
                            iconOverride = viewModel.categoryIcons[name],
                            size = 32.dp,
                            modifier = Modifier.clickable { viewModel.openIconPicker(name) },
                        )
                        Spacer(Modifier.padding(4.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.toggleExpanded(id) }) {
                            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = "Підкатегорії")
                        }
                        IconButton(onClick = { pendingDeleteId = id }) { Icon(Icons.Filled.Delete, contentDescription = "Видалити") }
                    }
                    if (expanded) {
                        val subs = viewModel.subcategoriesFor(name)
                        if (subs.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)) {
                                items(subs) { sub ->
                                    InputChip(
                                        selected = false,
                                        onClick = { pendingDeleteSub = name to sub },
                                        label = { Text(sub) },
                                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Видалити підкатегорію") },
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = newSubName,
                                onValueChange = { newSubName = it },
                                label = { Text("Підкатегорія") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            TextButton(onClick = { viewModel.addSubcategory(name, newSubName); newSubName = "" }) { Text("Додати") }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Назва категорії") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(onClick = { viewModel.addCategory(newName); newName = "" }) { Text("Додати") }
            }
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Видалити категорію") },
            text = { Text("Видалити категорію? Старі операції збережуть свою назву.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCategory(id); pendingDeleteId = null }) {
                    Text("Видалити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Скасувати") } },
        )
    }

    pendingDeleteSub?.let { (categoryName, subName) ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSub = null },
            title = { Text("Видалити підкатегорію") },
            text = { Text("Видалити підкатегорію «$subName»?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSubcategory(categoryName, subName); pendingDeleteSub = null }) {
                    Text("Видалити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteSub = null }) { Text("Скасувати") } },
        )
    }

    if (viewModel.iconPickerCategory != null) {
        CategoryIconPickerSheet(onDismiss = viewModel::closeIconPicker, onSelect = viewModel::selectIcon)
    }
}

// Mirrors js/settings-managers.js's category-icon-modal
// (renderCategoryIconGrid()/selectCategoryIcon()) — offers every name in
// PICKER_ICONS (window.ICON_NAMES's own set, see that map's doc comment),
// stacked as a second sheet on top of CategoriesManagerSheet the same way
// SettingsScreen already stacks independent `if (xOpen) XSheet(...)` sheets.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CategoryIconPickerSheet(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val icons = PICKER_ICONS

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Іконка категорії", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxWidth().height(320.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItems(icons.entries.toList()) { (name, icon) ->
                    Icon(
                        icon,
                        contentDescription = name,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { onSelect(name) }
                            .padding(10.dp),
                    )
                }
            }
        }
    }
}
