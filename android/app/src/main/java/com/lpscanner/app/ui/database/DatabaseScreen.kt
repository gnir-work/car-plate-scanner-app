package com.lpscanner.app.ui.database

import com.lpscanner.app.ui.displayNamer
import com.lpscanner.app.ui.displayName
import com.lpscanner.app.ui.agoText
import com.lpscanner.app.database.OptionKind
import com.lpscanner.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import com.lpscanner.app.ui.AppLanguage
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lpscanner.app.database.LastSeen
import com.lpscanner.app.database.PlateFormat
import com.lpscanner.app.database.PlateRecord
import com.lpscanner.app.database.PlateSnapshot
import com.lpscanner.app.database.Sharing
import com.lpscanner.app.ui.theme.AppColors
import com.lpscanner.app.ui.theme.AppIcons
import com.lpscanner.app.ui.theme.ColorDot
import com.lpscanner.app.ui.theme.PlateChip
import java.time.format.DateTimeFormatter

/** The car list of the selected sheet (design screen "Database – car list"), with search, add, edit and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(
    details: SheetDetails,
    accountViewModel: AccountViewModel = viewModel(),
    sheetViewModel: SheetViewModel = viewModel(),
    platesViewModel: PlatesViewModel = viewModel(),
) {
    val sheetState by sheetViewModel.state.collectAsState()
    val platesState by platesViewModel.state.collectAsState()
    val snapshot by platesViewModel.snapshot.collectAsState()
    val namer = displayNamer()
    val visible = remember(snapshot, platesState.query, namer) {
        snapshot?.records?.let { filter(it, platesState.query, namer) }.orEmpty().sortedByDescending { it.lastSeen }
    }

    Scaffold(
        containerColor = AppColors.Background,
        floatingActionButton = {
            if (snapshot != null) {
                ExtendedFloatingActionButton(
                    onClick = { platesViewModel.startAdd() },
                    icon = { Icon(AppIcons.Plus, contentDescription = null) },
                    text = { Text(stringResource(R.string.db_add_car), fontSize = 15.sp, fontWeight = FontWeight.Medium) },
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.OnPrimary,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Header(
                details = details,
                snapshot = snapshot,
                query = platesState.query,
                pulling = sheetState.busy,
                onQuery = platesViewModel::setQuery,
                onPull = sheetViewModel::refreshSelected,
                onSwitchSheet = sheetViewModel::switchSheet,
                onSignOut = accountViewModel::signOut,
            )
            if (sheetState.busy || platesState.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)) {
                Text(countLabel(snapshot, visible.size, platesState.query), fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.db_sorted), fontSize = 13.sp, color = AppColors.Muted)
            }
            PullToRefreshBox(
                isRefreshing = false, // the progress bar above shows the pull
                onRefresh = sheetViewModel::refreshSelected,
                modifier = Modifier.weight(1f).fillMaxWidth().background(AppColors.Surface),
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    item { HorizontalDivider(color = AppColors.Divider) }
                    when {
                        snapshot == null -> Unit
                        snapshot!!.records.isEmpty() -> item { EmptyNote(stringResource(R.string.db_empty)) }
                        visible.isEmpty() -> item { EmptyNote(stringResource(R.string.db_no_match, platesState.query.trim())) }
                        else -> items(visible, key = { it.row }) { record ->
                            CarRow(record) { platesViewModel.startEdit(record) }
                        }
                    }
                }
            }
        }
    }

    platesState.editor?.let { editor ->
        PlateEditorScreen(
            original = editor.original,
            initial = editor.initial,
            colors = snapshot?.colors.orEmpty(),
            companies = snapshot?.companies.orEmpty(),
            busy = platesState.busy,
            onAddOption = platesViewModel::addOption,
            onSave = platesViewModel::save,
            onDelete = { editor.original?.let(platesViewModel::requestDelete) },
            onDismiss = platesViewModel::closeEditor,
        )
    }
    platesState.confirmDelete?.let { record ->
        AlertDialog(
            onDismissRequest = platesViewModel::cancelDelete,
            title = { Text(stringResource(R.string.delete_title, record.plate)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = platesViewModel::confirmDelete) { Text(stringResource(R.string.delete), color = AppColors.Danger) }
            },
            dismissButton = { TextButton(onClick = platesViewModel::cancelDelete) { Text(stringResource(R.string.cancel)) } },
        )
    }
    platesState.message?.let { message ->
        AlertDialog(
            onDismissRequest = platesViewModel::dismissMessage,
            confirmButton = { TextButton(onClick = platesViewModel::dismissMessage) { Text(stringResource(R.string.ok)) } },
            text = { Text(message) },
        )
    }
}

@Composable
private fun Header(
    details: SheetDetails,
    snapshot: PlateSnapshot?,
    query: String,
    pulling: Boolean,
    onQuery: (String) -> Unit,
    onPull: () -> Unit,
    onSwitchSheet: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(AppColors.Surface)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.db_title), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    Row(
                        Modifier.heightIn(min = 32.dp).clickable(onClickLabel = stringResource(R.string.db_switch_sheet), onClick = onSwitchSheet),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(AppIcons.Sheet, contentDescription = null, tint = AppColors.PrimaryText, modifier = Modifier.size(16.dp))
                        Text(details.sheet.name, fontSize = 13.sp, color = AppColors.PrimaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(AppIcons.ChevronDown, contentDescription = null, tint = AppColors.PrimaryText, modifier = Modifier.size(16.dp))
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(AppIcons.More, contentDescription = stringResource(R.string.db_more)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.db_open_in_sheets)) },
                            onClick = {
                                menuOpen = false
                                val url = "https://docs.google.com/spreadsheets/d/${details.sheet.id}/edit"
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.db_switch_sheet)) }, onClick = { menuOpen = false; onSwitchSheet() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.language)) }, onClick = { menuOpen = false; choosingLanguage = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.sign_out)) }, onClick = { menuOpen = false; onSignOut() })
                    }
                }
                if (choosingLanguage) LanguageDialog(onDismiss = { choosingLanguage = false })
            }
            SyncBanner(snapshot, pulling, onPull)
            SharingNotice(details.sharing)
            SearchField(query, onQuery)
        }
        HorizontalDivider(color = AppColors.Divider)
    }
}

private val pulledFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun SyncBanner(snapshot: PlateSnapshot?, pulling: Boolean, onPull: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.fillMaxWidth().background(AppColors.InfoBg, shape).border(1.dp, AppColors.InfoBorder, shape)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(AppIcons.CheckCircle, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.db_synced), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                stringResource(R.string.db_push_now) +
                    (snapshot?.let { stringResource(R.string.db_pulled_at, it.loadedAt.format(pulledFormat)) } ?: ""),
                fontSize = 12.sp,
                color = AppColors.Muted,
            )
        }
        OutlinedButton(
            onClick = onPull,
            enabled = !pulling,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(40.dp),
        ) {
            Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.db_pull), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * Who can open the sheet. Hidden when it's private (the normal case); a red warning when anyone
 * with the link can, since the car list must not be public.
 */
@Composable
private fun SharingNotice(sharing: Sharing) {
    val text = when (sharing) {
        Sharing.Private -> return
        is Sharing.People -> pluralStringResource(R.plurals.sharing_people, sharing.count, sharing.count)
        is Sharing.Domain -> stringResource(R.string.sharing_domain, sharing.domain)
        Sharing.Unknown -> stringResource(R.string.sharing_unknown)
        Sharing.Hidden -> stringResource(R.string.sharing_hidden)
        is Sharing.Public ->
            stringResource(if (sharing.discoverable) R.string.sharing_public_web else R.string.sharing_public_link) + " " +
                stringResource(R.string.sharing_fix)
    }
    val public = sharing is Sharing.Public
    Row(
        Modifier.fillMaxWidth()
            .then(if (public) Modifier.background(AppColors.Danger.copy(alpha = 0.08f), RoundedCornerShape(6.dp)).padding(10.dp) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (public) AppIcons.Alert else AppIcons.Users,
            contentDescription = null,
            tint = if (public) AppColors.Danger else AppColors.Muted,
            modifier = Modifier.size(16.dp),
        )
        Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = if (public) AppColors.Danger else AppColors.Muted)
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val state = rememberTextFieldState(query)
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect(onQuery) }
    BasicTextField(
        state = state,
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = TextStyle(fontSize = 15.sp, color = AppColors.Ink),
        cursorBrush = SolidColor(AppColors.Primary),
        decorator = { field ->
            Row(
                Modifier.fillMaxWidth().height(44.dp).background(AppColors.Field, RoundedCornerShape(22.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(AppIcons.Search, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (state.text.isEmpty()) Text(stringResource(R.string.db_search), fontSize = 15.sp, color = AppColors.Muted)
                    field()
                }
                if (state.text.isNotEmpty()) {
                    IconButton(onClick = { state.clearText() }, modifier = Modifier.size(36.dp)) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.db_clear_search), tint = AppColors.Muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun CarRow(record: PlateRecord, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.db_edit_car), onClick = onClick).heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PlateChip(record.plate)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (record.company.isBlank()) stringResource(R.string.unknown_company) else displayName(OptionKind.Company, record.company),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (record.company.isBlank()) AppColors.Muted else AppColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (record.color.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ColorDot(record.color, 12.dp)
                        Text(displayName(OptionKind.Color, record.color), fontSize = 13.sp, color = AppColors.Muted, maxLines = 1)
                    }
                }
            }
            if (!record.lastSeen.isNullOrBlank()) {
                Text(agoText(record.lastSeen), fontSize = 13.sp, color = AppColors.Muted)
            }
        }
        HorizontalDivider(color = AppColors.RowDivider)
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, color = AppColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
}

@Composable
private fun countLabel(snapshot: PlateSnapshot?, shown: Int, query: String): String {
    val total = snapshot?.records?.size ?: return stringResource(R.string.loading)
    val cars = pluralStringResource(R.plurals.db_cars, total, total)
    return if (query.isBlank()) cars else stringResource(R.string.db_shown_of, shown, cars)
}

/**
 * Free-text search over every column: plate (ignoring dashes and spaces), color and company,
 * by their stored English name or the name shown (Hebrew).
 */
private fun filter(records: List<PlateRecord>, query: String, namer: (OptionKind, String) -> String): List<PlateRecord> {
    val q = query.trim()
    if (q.isEmpty()) return records
    val key = PlateFormat.key(q)
    fun matches(kind: OptionKind, stored: String) =
        stored.contains(q, ignoreCase = true) || namer(kind, stored).contains(q, ignoreCase = true)
    return records.filter { r ->
        (key.isNotEmpty() && key in r.key) || matches(OptionKind.Color, r.color) || matches(OptionKind.Company, r.company)
    }
}

/** System / עברית / English. Picking one restarts the screen in that language. */
@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    val current = remember { AppLanguage.current() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                AppLanguage.entries.forEach { language ->
                    val label = when (language) {
                        AppLanguage.System -> stringResource(R.string.language_system)
                        AppLanguage.Hebrew -> "עברית" // each language in its own name
                        AppLanguage.English -> "English"
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = language == current, role = Role.RadioButton) {
                                onDismiss()
                                if (language != current) AppLanguage.set(language)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = language == current, onClick = null)
                        Text(label, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
