package com.lpscanner.app.ui.database

import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import com.lpscanner.app.R
import com.lpscanner.app.database.CarVocabulary
import com.lpscanner.app.database.OptionKind
import com.lpscanner.app.ui.displayName
import com.lpscanner.app.ui.displayNamer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lpscanner.app.ui.theme.AppColors
import com.lpscanner.app.ui.theme.AppIcons
import com.lpscanner.app.ui.theme.ColorDot

// Form fields shared by the car editor (Database) and the add-car screen (Scanner).

/** Company dropdown of the shared list, plus "Add new…" which turns into a text field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompanyField(
    value: String,
    options: List<String>,
    busy: Boolean,
    onSelect: (String) -> Unit,
    onAddNew: (String, (String) -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    if (adding) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(stringResource(R.string.field_new_company)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    onAddNew(newName) { stored ->
                        onSelect(stored)
                        adding = false
                        newName = ""
                    }
                },
                enabled = newName.isNotBlank() && !busy,
            ) { Text(stringResource(R.string.add)) }
            IconButton(onClick = { adding = false }) {
                Icon(AppIcons.Close, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(18.dp))
            }
        }
        return
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayName(OptionKind.Company, value),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.company)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.field_add_new_company), color = MaterialTheme.colorScheme.primary) },
                onClick = {
                    expanded = false
                    adding = true
                },
            )
            if (value.isNotEmpty() && value !in options) {
                DropdownMenuItem(text = { Text(displayName(OptionKind.Company, value)) }, onClick = { expanded = false })
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(displayName(OptionKind.Company, option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 4-column grid of swatches from the sheet's color list, plus "Add new". */
@Composable
internal fun ColorPicker(selected: String, options: List<String>, enabled: Boolean, onSelect: (String) -> Unit, onAddNew: () -> Unit) {
    // A color typed straight into the sheet may not be in the list; still show it as picked.
    val names = if (selected.isNotEmpty() && options.none { it.equals(selected, ignoreCase = true) }) options + selected else options
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.color), fontSize = 13.sp, color = AppColors.Muted)
        val addNewColor = stringResource(R.string.field_add_new_color)
        val namer = displayNamer()
        val tiles: List<String?> = names + null // null = the "Add new" tile
        tiles.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { name ->
                    val on = name != null && name.equals(selected, ignoreCase = true)
                    val shape = RoundedCornerShape(6.dp)
                    Column(
                        Modifier.weight(1f).height(64.dp)
                            .background(if (on) AppColors.PrimaryTintLight else AppColors.Surface, shape)
                            .border(if (on) 2.dp else 1.dp, if (on) AppColors.Primary else AppColors.Divider, shape)
                            .clickable(enabled = enabled, role = Role.RadioButton) { if (name == null) onAddNew() else onSelect(name) }
                            .semantics {
                                this.selected = on
                                contentDescription = name?.let { namer(OptionKind.Color, it) } ?: addNewColor
                            }
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    ) {
                        if (name == null) {
                            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                Icon(AppIcons.Plus, contentDescription = null, tint = AppColors.PrimaryText, modifier = Modifier.size(20.dp))
                            }
                            Text(stringResource(R.string.field_add_new), fontSize = 12.sp, color = AppColors.PrimaryText)
                        } else {
                            ColorDot(name, 22.dp)
                            Text(namer(OptionKind.Color, name), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun AddOptionDialog(title: String, busy: Boolean, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = { TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank() && !busy) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Company as tappable chips (design: "Not in database, add car"): the most used companies,
 * then "Other…" to search the whole list or add a new one.
 */
@Composable
internal fun CompanyChips(
    selected: String,
    popular: List<String>,
    all: List<String>,
    busy: Boolean,
    onSelect: (String) -> Unit,
    onAddNew: (String, (String) -> Unit) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    // Keep the picked one visible even when it isn't among the popular ones.
    val shown = if (selected.isNotEmpty() && popular.none { it.equals(selected, ignoreCase = true) }) popular + selected else popular
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.company), fontSize = 13.sp, color = AppColors.Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { name ->
                Chip(displayName(OptionKind.Company, name), on = name.equals(selected, ignoreCase = true), enabled = !busy) {
                    onSelect(if (name.equals(selected, ignoreCase = true)) "" else name)
                }
            }
            Chip(stringResource(R.string.field_other), on = false, enabled = !busy) { picking = true }
        }
    }
    if (picking) {
        CompanySearchDialog(
            all = all,
            busy = busy,
            onPick = { onSelect(it); picking = false },
            onAddNew = { name -> onAddNew(name) { stored -> onSelect(stored); picking = false } },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun Chip(label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.height(40.dp)
            .background(if (on) AppColors.PrimaryTint else AppColors.Surface, shape)
            .border(if (on) 2.dp else 1.dp, if (on) AppColors.Primary else AppColors.Border, shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (on) AppColors.PrimaryText else AppColors.Ink)
    }
}

/** Search the whole company list; the typed name can be added when it isn't there. */
@Composable
private fun CompanySearchDialog(
    all: List<String>,
    busy: Boolean,
    onPick: (String) -> Unit,
    onAddNew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val namer = displayNamer()
    val q = query.trim()
    val matches = all.filter { it.contains(q, ignoreCase = true) || namer(OptionKind.Company, it).contains(q, ignoreCase = true) }
    // Typing "טויוטה" when "Toyota" is in the list is the same company, not a new one.
    val english = CarVocabulary.toEnglish(OptionKind.Company, q)
    val exact = all.any { it.equals(english, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.company)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.field_company_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    if (q.isNotEmpty() && !exact) {
                        item {
                            Text(
                                stringResource(R.string.field_add_named, q),
                                color = AppColors.PrimaryText,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onAddNew(q) }.padding(vertical = 12.dp),
                            )
                        }
                    }
                    items(matches) { name ->
                        Text(namer(OptionKind.Company, name), modifier = Modifier.fillMaxWidth().clickable { onPick(name) }.padding(vertical = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
