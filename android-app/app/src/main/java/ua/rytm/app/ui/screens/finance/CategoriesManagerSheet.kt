package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ua.rytm.app.R
import ua.rytm.app.ui.localizedDomainText
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.CategoriesSyncRepository
import ua.rytm.app.ui.theme.RytmDimens

// Mirrors js/settings-managers.js's categories-modal, including the
// toggleSubcatPanel()/addSubcategory()/deleteSubcategory() subcategory panel
// (icons/budgets still out of scope — see CategoriesManagerViewModel's doc comment).
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun CategoriesManagerSheet(
    repository: FinanceRepository,
    syncRepository: CategoriesSyncRepository,
    uid: String,
    profileId: String,
    onDismiss: () -> Unit,
    viewModel: CategoriesManagerViewModel = viewModel(factory = CategoriesManagerViewModel.factory(repository, syncRepository, uid, profileId)),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newName by remember { mutableStateOf("") }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var newSubName by remember { mutableStateOf("") }
    var pendingDeleteSub by remember { mutableStateOf<Pair<String, String>?>(null) } // (categoryName, subName)
    var pendingRename by remember { mutableStateOf<Pair<String, String>?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.categories_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = viewModel.activeType == TxType.EXPENSE, onClick = { viewModel.setType(TxType.EXPENSE) }, label = { Text(stringResource(R.string.tx_expense)) })
                FilterChip(selected = viewModel.activeType == TxType.INCOME, onClick = { viewModel.setType(TxType.INCOME) }, label = { Text(stringResource(R.string.tx_income)) })
            }

            viewModel.errorMessageRes?.let { messageRes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = viewModel::consumeError) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_dismiss)) }
                }
            }

            viewModel.categories.forEach { (id, name) ->
                val expanded = viewModel.expandedCategoryId == id
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val iconAction = stringResource(R.string.category_change_icon, localizedDomainText(name))
                        Box(
                            Modifier
                                .size(RytmDimens.TouchTarget)
                                .clickable(role = Role.Button) { viewModel.openIconPicker(name) }
                                .semantics { contentDescription = iconAction },
                            contentAlignment = Alignment.Center,
                        ) {
                            CategoryIconBadge(name, iconOverride = viewModel.categoryIcons[name], size = 32.dp)
                        }
                        Spacer(Modifier.padding(4.dp))
                        Text(localizedDomainText(name), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.toggleExpanded(id) }) {
                            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = stringResource(R.string.subcategories_title))
                        }
                        IconButton(onClick = { pendingDeleteId = id }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
                    }
                    if (expanded) {
                        TextButton(onClick = { pendingRename = id to name }) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Text(stringResource(R.string.action_rename), modifier = Modifier.padding(start = 6.dp))
                        }
                        val subs = viewModel.subcategoriesFor(name)
                        if (subs.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)) {
                                items(subs) { sub ->
                                    InputChip(
                                        selected = false,
                                        onClick = { pendingDeleteSub = name to sub },
                                        label = { Text(sub) },
                                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.subcategory_delete_title)) },
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
                                label = { Text(stringResource(R.string.subcategory_label)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            TextButton(onClick = { viewModel.addSubcategory(name, newSubName); newSubName = "" }) { Text(stringResource(R.string.action_add)) }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(onClick = { viewModel.addCategory(newName); newName = "" }) { Text(stringResource(R.string.action_add)) }
            }
        }
    }

    pendingRename?.let { (id, initialName) ->
        var editedName by remember(id) { mutableStateOf(initialName) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text(stringResource(R.string.category_rename_title)) },
            text = { OutlinedTextField(value = editedName, onValueChange = { editedName = it }, singleLine = true, label = { Text(stringResource(R.string.field_name)) }) },
            confirmButton = {
                TextButton(onClick = { viewModel.renameCategory(id, editedName); pendingRename = null }, enabled = editedName.trim().isNotEmpty()) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { pendingRename = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.category_delete_title)) },
            text = { Text(stringResource(R.string.category_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCategory(id); pendingDeleteId = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    pendingDeleteSub?.let { (categoryName, subName) ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSub = null },
            title = { Text(stringResource(R.string.subcategory_delete_title)) },
            text = { Text(stringResource(R.string.subcategory_delete_body, subName)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSubcategory(categoryName, subName); pendingDeleteSub = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteSub = null }) { Text(stringResource(R.string.action_cancel)) } },
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
            Text(stringResource(R.string.category_icon_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                            .size(RytmDimens.TouchTarget)
                            .clip(CircleShape)
                            .clickable { onSelect(name) }
                            .padding(10.dp),
                    )
                }
            }
        }
    }
}
