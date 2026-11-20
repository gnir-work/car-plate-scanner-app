package com.lpscanner.app.ui

import androidx.compose.ui.res.stringResource
import com.lpscanner.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lpscanner.app.database.OptionKind
import com.lpscanner.app.database.PlateFormat
import com.lpscanner.app.database.PlateInput
import com.lpscanner.app.database.PlateRecord
import com.lpscanner.app.database.PlateSnapshot
import com.lpscanner.app.ui.database.AddOptionDialog
import com.lpscanner.app.ui.database.ColorPicker
import com.lpscanner.app.ui.database.CompanyChips
import com.lpscanner.app.ui.database.PlateEditorScreen
import com.lpscanner.app.ui.database.PlatesViewModel
import com.lpscanner.app.ui.theme.AppColors
import com.lpscanner.app.ui.theme.AppIcons
import com.lpscanner.app.ui.theme.DialogSystemBars
import com.lpscanner.app.ui.theme.ColorDot
import com.lpscanner.app.ui.theme.PlateChip
import com.lpscanner.app.ui.theme.PlateSize


/**
 * Full-screen result of a scan (design: "Result – in database" and "Result – not found, add car").
 * Back, Cancel and "Scan next car" return to the camera.
 */
@Composable
fun ScanResultScreen(
    lookup: Lookup,
    source: PlateSource,
    busy: Boolean,
    snapshot: PlateSnapshot?,
    viewModel: ScannerViewModel,
    plates: PlatesViewModel,
) {
    val platesState by plates.state.collectAsState()

    Dialog(
        onDismissRequest = viewModel::closeResult,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogSystemBars()
        Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                Row(
                    Modifier.fillMaxWidth().height(64.dp).background(AppColors.Surface).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = viewModel::closeResult) { Icon(AppIcons.Back, contentDescription = stringResource(R.string.result_back)) }
                    Text(stringResource(R.string.result_title), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }
                HorizontalDivider(color = AppColors.Divider)
                if (busy || platesState.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

                when (lookup) {
                    is Lookup.Found -> FoundContent(lookup, snapshot, busy, viewModel, onEdit = plates::startEdit)
                    is Lookup.Added -> FoundContent(lookup, snapshot, busy, viewModel, onEdit = plates::startEdit)
                    is Lookup.NotFound -> NotFoundContent(lookup, source, snapshot, busy, viewModel)
                    is Lookup.Failed -> FailedContent(lookup, viewModel)
                }
            }
        }
    }

    // "Edit car details" opens the same editor as the Database tab.
    platesState.editor?.let { editor ->
        PlateEditorScreen(
            original = editor.original,
            initial = editor.initial,
            colors = snapshot?.colors.orEmpty(),
            companies = snapshot?.companies.orEmpty(),
            busy = platesState.busy,
            onAddOption = plates::addOption,
            onSave = plates::save,
            onDelete = { editor.original?.let(plates::requestDelete) },
            onDismiss = plates::closeEditor,
        )
    }
    platesState.confirmDelete?.let { record ->
        AlertDialog(
            onDismissRequest = plates::cancelDelete,
            title = { Text(stringResource(R.string.delete_title, record.plate)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    plates.confirmDelete()
                    viewModel.closeResult()
                }) { Text(stringResource(R.string.delete), color = AppColors.Danger) }
            },
            dismissButton = { TextButton(onClick = plates::cancelDelete) { Text(stringResource(R.string.cancel)) } },
        )
    }
    platesState.message?.let { message ->
        AlertDialog(
            onDismissRequest = plates::dismissMessage,
            confirmButton = { TextButton(onClick = plates::dismissMessage) { Text(stringResource(R.string.ok)) } },
            text = { Text(message) },
        )
    }
}

@Composable
private fun ColumnScope.FoundContent(
    lookup: Lookup,
    snapshot: PlateSnapshot?,
    busy: Boolean,
    viewModel: ScannerViewModel,
    onEdit: (PlateRecord) -> Unit,
) {
    val scanned = when (lookup) {
        is Lookup.Found -> lookup.match.record
        is Lookup.Added -> lookup.record
        else -> return
    }
    // Follow edits made from here (or by a teammate, after a pull).
    val record = snapshot?.find(scanned.plate) ?: scanned

    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (lookup) {
            is Lookup.Added -> Banner(AppIcons.CheckCircle, AppColors.Success, AppColors.SuccessBg, AppColors.SuccessBorder, AppColors.SuccessText, stringResource(R.string.result_added), stringResource(R.string.result_set_now))
            is Lookup.Found -> Banner(
                icon = AppIcons.CheckCircle,
                tint = AppColors.Success,
                background = AppColors.SuccessBg,
                border = AppColors.SuccessBorder,
                titleColor = AppColors.SuccessText,
                title = stringResource(R.string.result_in_db),
                subtitle = when (val seen = lookup.seen) {
                    SeenUpdate.Updating -> stringResource(R.string.result_updating)
                    SeenUpdate.Updated -> stringResource(R.string.result_updated)
                    SeenUpdate.Undone -> stringResource(R.string.result_undone)
                    is SeenUpdate.Failed -> stringResource(R.string.result_update_failed, seen.message)
                },
                action = if (lookup.seen == SeenUpdate.Updated) ({ TextButton(onClick = viewModel::undoSeen, enabled = !busy) { Text(stringResource(R.string.undo)) } }) else null,
            )
        }
        if ((lookup as? Lookup.Found)?.offline == true) OfflineNote()

        val shape = RoundedCornerShape(8.dp)
        Column(
            Modifier.fillMaxWidth().background(AppColors.Surface, shape).border(1.dp, AppColors.Divider, shape)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlateChip(record.plate, size = PlateSize.Large, width = null)
            CarSummary(record)
            if ((lookup as? Lookup.Found)?.match?.viaBestReading == false) {
                Text(
                    stringResource(R.string.result_other_reading),
                    fontSize = 13.sp,
                    color = AppColors.WarningText,
                )
            }
            HorizontalDivider(color = AppColors.RowDivider)
            Row(Modifier.fillMaxWidth()) {
                when (lookup) {
                    is Lookup.Found -> {
                        val updated = lookup.seen == SeenUpdate.Updated
                        Fact(stringResource(R.string.result_previously), momentText(lookup.previousSeen), Modifier.weight(1f))
                        Fact(
                            stringResource(if (updated) R.string.result_last_seen_now else R.string.editor_last_seen),
                            momentText(record.lastSeen),
                            Modifier.weight(1f),
                            highlight = updated,
                        )
                    }
                    else -> Fact(stringResource(R.string.result_last_seen_now), momentText(record.lastSeen), Modifier.weight(1f), highlight = true)
                }
            }
            if (lookup is Lookup.Found && record.firstSeen != null) {
                Text(
                    stringResource(R.string.result_first_seen, momentText(record.firstSeen)),
                    fontSize = 13.sp,
                    color = AppColors.Muted,
                )
            }
        }

        TextButton(
            onClick = { onEdit(record) },
            enabled = !busy,
            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 44.dp),
        ) {
            Icon(AppIcons.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.result_edit), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }

    BottomBar {
        Button(
            onClick = viewModel::closeResult,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(AppIcons.Scan, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.result_scan_next), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ColumnScope.NotFoundContent(
    lookup: Lookup.NotFound,
    source: PlateSource,
    snapshot: PlateSnapshot?,
    busy: Boolean,
    viewModel: ScannerViewModel,
) {
    var plate by rememberSaveable(lookup.plate) { mutableStateOf(PlateFormat.display(lookup.plate)) }
    var company by rememberSaveable(lookup.plate) { mutableStateOf("") }
    var color by rememberSaveable(lookup.plate) { mutableStateOf("") }
    var addingColor by remember { mutableStateOf(false) }
    val plateValid = PlateFormat.key(plate).length >= 4
    val popular = remember(snapshot) { popularCompanies(snapshot) }

    Column(
        Modifier.weight(1f).background(AppColors.Surface).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Banner(AppIcons.Alert, AppColors.Warning, AppColors.WarningBg, AppColors.WarningBorder, AppColors.WarningText, stringResource(R.string.result_not_in_db), stringResource(R.string.result_add_hint))
        if (lookup.offline) OfflineNote()
        OutlinedTextField(
            value = plate,
            onValueChange = { plate = it },
            label = { Text(stringResource(R.string.license_plate)) },
            singleLine = true,
            isError = plate.isNotEmpty() && !plateValid,
            supportingText = {
                Text(stringResource(if (source == PlateSource.Photo) R.string.result_from_photo else R.string.result_as_typed))
            },
            textStyle = plateTextStyle(TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 1.sp)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        CompanyChips(
            selected = company,
            popular = popular,
            all = snapshot?.companies.orEmpty(),
            busy = busy,
            onSelect = { company = it },
            onAddNew = { name, done -> viewModel.addOption(OptionKind.Company, name, done) },
        )
        ColorPicker(
            selected = color,
            options = snapshot?.colors.orEmpty(),
            enabled = !busy,
            onSelect = { color = if (color == it) "" else it },
            onAddNew = { addingColor = true },
        )
    }

    BottomBar {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = viewModel::closeResult,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(stringResource(R.string.cancel), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.Ink) }
            Button(
                onClick = { viewModel.saveNew(PlateInput(plate, color, company)) },
                enabled = plateValid && !busy,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.weight(2f).height(52.dp),
            ) {
                Icon(AppIcons.Plus, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.result_add), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (addingColor) {
        AddOptionDialog(
            title = stringResource(R.string.editor_new_color),
            busy = busy,
            onAdd = { name -> viewModel.addOption(OptionKind.Color, name) { stored -> color = stored; addingColor = false } },
            onDismiss = { addingColor = false },
        )
    }
}

@Composable
private fun ColumnScope.FailedContent(lookup: Lookup.Failed, viewModel: ScannerViewModel) {
    Column(
        Modifier.weight(1f).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Banner(AppIcons.Alert, AppColors.Danger, AppColors.DangerBg, AppColors.DangerBorder, AppColors.Danger, stringResource(R.string.result_check_failed), lookup.message)
        PlateChip(PlateFormat.display(lookup.plate), size = PlateSize.Large, width = null)
    }
    BottomBar {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = viewModel::closeResult, shape = RoundedCornerShape(26.dp), modifier = Modifier.weight(1f).height(52.dp)) {
                Text(stringResource(R.string.back), color = AppColors.Ink)
            }
            Button(onClick = viewModel::retryLookup, shape = RoundedCornerShape(26.dp), modifier = Modifier.weight(1f).height(52.dp)) {
                Text(stringResource(R.string.try_again))
            }
        }
    }
}

@Composable
private fun Banner(
    icon: ImageVector,
    tint: Color,
    background: Color,
    border: Color,
    titleColor: Color,
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().background(background, shape).border(1.dp, border, shape)
            .padding(start = 16.dp, end = if (action != null) 4.dp else 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = titleColor)
            Text(subtitle, fontSize = 13.sp, color = AppColors.Ink)
        }
        action?.invoke()
    }
}

@Composable
private fun CarSummary(record: PlateRecord) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (record.company.isBlank()) stringResource(R.string.unknown_company) else displayName(OptionKind.Company, record.company),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
        if (record.color.isNotBlank()) {
            Text("•", color = AppColors.Muted)
            ColorDot(record.color, 14.dp)
            Text(displayName(OptionKind.Color, record.color), fontSize = 17.sp)
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, color = AppColors.Muted)
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = if (highlight) FontWeight.Medium else FontWeight.Normal,
            color = if (highlight) AppColors.PrimaryText else AppColors.Ink,
        )
    }
}

@Composable
private fun BottomBar(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(AppColors.Surface)) {
        HorizontalDivider(color = AppColors.Divider)
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) { content() }
    }
}

@Composable
private fun OfflineNote() = Text(
    stringResource(R.string.result_offline),
    fontSize = 13.sp,
    color = AppColors.Danger,
)

/** The 7 companies most used in the sheet, topped up from the options list. */
private fun popularCompanies(snapshot: PlateSnapshot?): List<String> {
    if (snapshot == null) return emptyList()
    val used = snapshot.records.map { it.company.trim() }.filter(String::isNotEmpty)
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    return (used + snapshot.companies).distinctBy { it.lowercase() }.take(7)
}
