package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.ui.components.RytmModalBottomSheet
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.icons.Add
import ua.rytm.app.ui.icons.ArrowBack
import ua.rytm.app.ui.icons.ChevronRight
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmSpacing

/** The form's category field: one row that opens [CategoryPickerSheet]. */
@Composable
fun CategoryField(vm: FinanceViewModel, onOpen: () -> Unit) {
    val category = vm.form.category ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RytmRadii.Input))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.tx_category), onClick = onOpen)
            .padding(vertical = RytmSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RytmSpacing.md),
    ) {
        CategoryIconBadge(category, vm.categoryIcons[category], size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.tx_category), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val sub = vm.form.subcategory
            Text(
                localizedDomainText(category) + (sub?.let { " › " + localizedDomainText(it) } ?: ""),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(RytmIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Category, then (when it has any) its subcategory, in one sheet: a category
 * without subcategories is picked with one tap; one with subcategories opens
 * them, «Без підкатегорії» first. New entries can be added in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(vm: FinanceViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val type = vm.form.type
    val categories = vm.categoriesByType[type].orEmpty()
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }

    fun pick(category: String, sub: String?) {
        vm.form.onCategoryChange(category)
        vm.form.onSubcategoryChange(sub)
        onDismiss()
    }

    RytmModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = RytmSpacing.xl, vertical = RytmSpacing.sm)) {
            val parent = opened
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (parent != null) {
                    IconButton(onClick = { opened = null; adding = false; newName = "" }) {
                        Icon(RytmIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
                RytmSheetTitle(
                    if (parent == null) stringResource(R.string.tx_category) else localizedDomainText(parent),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { adding = !adding; newName = "" }) {
                    Icon(RytmIcons.Add, contentDescription = stringResource(if (parent == null) R.string.category_add else R.string.subcategory_add))
                }
            }
            if (adding) {
                fun submit() {
                    if (newName.isBlank()) return
                    vm.createCategoryFromForm(newName, parent)
                    onDismiss()
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(if (parent == null) R.string.category_new_name else R.string.subcategory_new_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        ua.rytm.app.ui.components.RytmTextButton(onClick = { submit() }, enabled = newName.isNotBlank()) { Text(stringResource(R.string.action_add)) }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = RytmSpacing.sm),
                )
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (parent == null) {
                    categories.forEachIndexed { i, cat ->
                        val subs = vm.subcategoriesOf(type, cat)
                        PickerRow(
                            leading = { CategoryIconBadge(cat, vm.categoryIcons[cat], size = 32.dp) },
                            text = localizedDomainText(cat),
                            selected = subs.isEmpty() && vm.form.category == cat,
                            hasChildren = subs.isNotEmpty(),
                            hint = if (subs.isNotEmpty() && vm.form.category == cat) vm.form.subcategory?.let { localizedDomainText(it) } ?: stringResource(R.string.subcategory_none) else null,
                            onClick = { if (subs.isEmpty()) pick(cat, null) else { opened = cat; adding = false } },
                        )
                        if (i < categories.lastIndex) HorizontalDivider()
                    }
                } else {
                    val subs = vm.subcategoriesOf(type, parent)
                    val current = vm.form.category == parent
                    PickerRow(
                        leading = { CategoryIconBadge(parent, vm.categoryIcons[parent], size = 32.dp) },
                        text = stringResource(R.string.subcategory_none),
                        selected = current && vm.form.subcategory == null,
                        onClick = { pick(parent, null) },
                    )
                    subs.forEach { sub ->
                        HorizontalDivider()
                        PickerRow(
                            leading = { Spacer(Modifier.size(32.dp)) },
                            text = localizedDomainText(sub),
                            selected = current && vm.form.subcategory == sub,
                            onClick = { pick(parent, sub) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    leading: @Composable () -> Unit,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    hasChildren: Boolean = false,
    hint: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RytmDimens.TouchTarget + 8.dp)
            .clickable(role = if (hasChildren) Role.Button else Role.RadioButton, onClick = onClick)
            .padding(vertical = RytmSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(RytmSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (hasChildren) Icon(RytmIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else RadioButton(selected = selected, onClick = null)
    }
}
