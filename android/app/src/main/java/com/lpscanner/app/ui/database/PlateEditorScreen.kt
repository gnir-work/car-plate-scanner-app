package com.lpscanner.app.ui.database

import androidx.compose.ui.res.stringResource
import com.lpscanner.app.R
import com.lpscanner.app.ui.momentText
import com.lpscanner.app.ui.plateTextStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lpscanner.app.database.OptionKind
import com.lpscanner.app.database.PlateFormat
import com.lpscanner.app.database.PlateInput
import com.lpscanner.app.database.PlateRecord
import com.lpscanner.app.ui.theme.AppColors
import com.lpscanner.app.ui.theme.AppIcons
import com.lpscanner.app.ui.theme.DialogSystemBars
import com.lpscanner.app.ui.theme.ColorDot

/**
 * Full-screen add/edit form for one car (design screen "Edit car"). [original] is null when adding.
 * Saving and deleting write straight to the sheet.
 */
@Composable
fun PlateEditorScreen(
    original: PlateRecord?,
    initial: PlateInput,
    colors: List<String>,
    companies: List<String>,
    busy: Boolean,
    onAddOption: (OptionKind, String, (String) -> Unit) -> Unit,
    onSave: (PlateInput, markSeen: Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var plate by rememberSaveable { mutableStateOf(initial.plate) }
    var color by rememberSaveable { mutableStateOf(initial.color) }
    var company by rememberSaveable { mutableStateOf(initial.company) }
    var markSeen by rememberSaveable { mutableStateOf(false) }
    var addingColor by remember { mutableStateOf(false) }
    val plateValid = PlateFormat.key(plate).length >= 4
    val editing = original != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogSystemBars()
        Surface(Modifier.fillMaxSize(), color = AppColors.Surface) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                Row(
                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = onDismiss) { Icon(AppIcons.Close, contentDescription = stringResource(R.string.close)) }
                    Text(stringResource(if (editing) R.string.db_edit_car else R.string.db_add_car), fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Button(
                        onClick = { onSave(PlateInput(plate, color, company), markSeen) },
                        enabled = plateValid && !busy,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(end = 8.dp).height(40.dp),
                    ) { Text(stringResource(R.string.save), fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
                HorizontalDivider(color = AppColors.Divider)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    OutlinedTextField(
                        value = plate,
                        onValueChange = { plate = it },
                        label = { Text(stringResource(R.string.license_plate)) },
                        singleLine = true,
                        isError = plate.isNotEmpty() && !plateValid,
                        supportingText = { if (plateValid) Text(stringResource(R.string.plate_saved_as, PlateFormat.display(plate))) },
                        textStyle = plateTextStyle(TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 1.sp)),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CompanyField(company, companies, busy, { company = it }) { name, done ->
                        onAddOption(OptionKind.Company, name, done)
                    }
                    ColorPicker(
                        selected = color,
                        options = colors,
                        enabled = !busy,
                        onSelect = { color = if (color == it) "" else it },
                        onAddNew = { addingColor = true },
                    )
                    LastSeenBox(original, markSeen, onMarkSeen = { markSeen = it })
                }

                HorizontalDivider(color = AppColors.Divider)
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.editor_footer), fontSize = 12.sp, color = AppColors.Muted, modifier = Modifier.weight(1f))
                    if (editing) {
                        TextButton(onClick = onDelete, enabled = !busy) {
                            Icon(AppIcons.Trash, contentDescription = null, tint = AppColors.Danger, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.editor_delete), color = AppColors.Danger, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    if (addingColor) {
        AddOptionDialog(
            title = stringResource(R.string.editor_new_color),
            busy = busy,
            onAdd = { name -> onAddOption(OptionKind.Color, name) { stored -> color = stored; addingColor = false } },
            onDismiss = { addingColor = false },
        )
    }
}

@Composable
private fun LastSeenBox(original: PlateRecord?, markSeen: Boolean, onMarkSeen: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(AppColors.Background, RoundedCornerShape(6.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (original != null) {
            Text(stringResource(R.string.first_seen), fontSize = 12.sp, color = AppColors.Muted)
            Text(momentText(original.firstSeen, empty = R.string.unknown), fontSize = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
        }
        Text(stringResource(R.string.editor_last_seen), fontSize = 12.sp, color = AppColors.Muted)
        if (original == null) {
            Text(stringResource(R.string.editor_set_on_save), fontSize = 15.sp)
        } else {
            Text(momentText(original.lastSeen), fontSize = 15.sp)
            Row(
                Modifier.fillMaxWidth().clickable { onMarkSeen(!markSeen) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = markSeen, onCheckedChange = onMarkSeen)
                Text(stringResource(R.string.editor_mark_seen), fontSize = 14.sp)
            }
        }
    }
}
