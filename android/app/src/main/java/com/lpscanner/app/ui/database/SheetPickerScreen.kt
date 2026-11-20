package com.lpscanner.app.ui.database

import androidx.compose.ui.res.stringResource
import com.lpscanner.app.R
import com.lpscanner.app.ui.agoText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lpscanner.app.google.DriveFile
import com.lpscanner.app.google.GoogleUser
import com.lpscanner.app.ui.theme.AppColors
import com.lpscanner.app.ui.theme.AppIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Choose or create the sheet that holds the car list. Shown full screen after sign-in when no
 * sheet is selected yet, and from the Database screen to switch (then Back returns to it).
 */
@Composable
fun SheetPickerScreen(
    user: GoogleUser,
    mode: SheetMode.Picking,
    busy: Boolean,
    onCreate: (String) -> Unit,
    onPick: (DriveFile) -> Unit,
    onOpenLink: (String) -> Boolean,
    onRefresh: () -> Unit,
    onBack: (() -> Unit)?,
    onSwitchAccount: () -> Unit,
) {
    if (onBack != null) BackHandler(onBack = onBack)
    var selectedId by rememberSaveable { mutableStateOf(mode.current?.sheet?.id) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val files = mode.files
    val visible = remember(files, query) { files?.filter { it.name.contains(query.trim(), ignoreCase = true) } }
    val selected = files?.firstOrNull { it.id == selectedId }

    Column(Modifier.fillMaxSize().background(AppColors.Background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).background(AppColors.Surface).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = stringResource(R.string.back)) }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.picker_title), fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        HorizontalDivider(color = AppColors.Divider)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

        PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { AccountCard(user, enabled = !busy, onSwitchAccount) }
                item { CreateCard(enabled = !busy, onCreate) }
                item { LinkCard(enabled = !busy, onOpenLink) }
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.picker_your_sheets), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.Muted, modifier = Modifier.weight(1f))
                        TextButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (searching) R.string.picker_close_search else R.string.picker_search), fontSize = 13.sp)
                        }
                    }
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.picker_sheet_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
                item {
                    SheetList(visible, selectedId, enabled = !busy, onSelect = { selectedId = it.id })
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(AppColors.Surface)) {
            HorizontalDivider(color = AppColors.Divider)
            Button(
                onClick = { selected?.let(onPick) },
                enabled = selected != null && !busy,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp).height(48.dp),
            ) { Text(stringResource(R.string.picker_use), fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun AccountCard(user: GoogleUser, enabled: Boolean, onSwitchAccount: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(AppColors.Surface, RoundedCornerShape(6.dp))
            .border(1.dp, AppColors.Divider, RoundedCornerShape(6.dp))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).background(AppColors.PrimaryTintLight, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                (user.displayName ?: user.emailAddress).take(1).uppercase(),
                color = AppColors.PrimaryText,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.picker_signed_in), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(user.emailAddress, fontSize = 13.sp, color = AppColors.Muted, maxLines = 1)
        }
        TextButton(onClick = onSwitchAccount, enabled = enabled) { Text(stringResource(R.string.picker_switch), fontSize = 14.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun CreateCard(enabled: Boolean, onCreate: (String) -> Unit) {
    val defaultName = stringResource(R.string.default_sheet_name) // the file name follows the UI language; the columns stay English
    var name by rememberSaveable { mutableStateOf(defaultName) }
    Column(
        Modifier.fillMaxWidth().background(AppColors.Surface, RoundedCornerShape(6.dp))
            .border(1.dp, AppColors.Divider, RoundedCornerShape(6.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(AppIcons.Plus, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.picker_create_title), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.picker_sheet_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.picker_create_note),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = AppColors.Muted,
        )
        OutlinedButton(
            onClick = { onCreate(name) },
            enabled = enabled && name.isNotBlank(),
            border = BorderStroke(1.dp, AppColors.Primary),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(40.dp),
        ) { Text(stringResource(R.string.picker_create_button), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.PrimaryText) }
    }
}

/** Opens a sheet the app didn't create: its Drive permission can't list those. */
@Composable
private fun LinkCard(enabled: Boolean, onOpenLink: (String) -> Boolean) {
    var link by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val open = { if (onOpenLink(link)) link = "" else invalid = true }
    Column(
        Modifier.fillMaxWidth().background(AppColors.Surface, RoundedCornerShape(6.dp))
            .border(1.dp, AppColors.Divider, RoundedCornerShape(6.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(AppIcons.Link, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.picker_link_title), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        OutlinedTextField(
            value = link,
            onValueChange = { link = it; invalid = false },
            label = { Text(stringResource(R.string.picker_link_label)) },
            placeholder = { Text("https://docs.google.com/spreadsheets/d/…", maxLines = 1) },
            singleLine = true,
            isError = invalid,
            supportingText = if (invalid) {
                { Text(stringResource(R.string.picker_link_invalid)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (link.isNotBlank() && enabled) open() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.picker_link_note),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = AppColors.Muted,
        )
        OutlinedButton(
            onClick = open,
            enabled = enabled && link.isNotBlank(),
            border = BorderStroke(1.dp, AppColors.Primary),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(40.dp),
        ) { Text(stringResource(R.string.picker_link_button), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.PrimaryText) }
    }
}

@Composable
private fun SheetList(files: List<DriveFile>?, selectedId: String?, enabled: Boolean, onSelect: (DriveFile) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Column(Modifier.fillMaxWidth().background(AppColors.Surface, shape).border(1.dp, AppColors.Divider, shape)) {
        when {
            files == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }
            files.isEmpty() -> Text(
                stringResource(R.string.picker_none),
                color = AppColors.Muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp),
            )
            else -> files.forEachIndexed { index, file ->
                if (index > 0) HorizontalDivider(color = AppColors.RowDivider)
                SheetRow(file, selected = file.id == selectedId, enabled = enabled) { onSelect(file) }
            }
        }
    }
}

@Composable
private fun SheetRow(file: DriveFile, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) AppColors.PrimaryTintLight else AppColors.Surface)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(AppIcons.Sheet, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(meta(file), fontSize = 13.sp, color = AppColors.Muted)
        }
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
    }
}

@Composable
private fun meta(file: DriveFile): String {
    if (file.openedByLink) return stringResource(R.string.picker_from_link)
    val owner = if (file.ownedByMe) {
        stringResource(R.string.picker_yours)
    } else {
        val who = file.owners.firstOrNull()?.let { it.displayName ?: it.emailAddress } ?: stringResource(R.string.picker_someone)
        stringResource(R.string.picker_shared_by, who)
    }
    val edited = file.modifiedTime?.let(::localTime)?.let { stringResource(R.string.picker_edited, agoText(it)) }
    return listOfNotNull(owner, edited).joinToString(" · ")
}

private val sheetTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** Drive's RFC 3339 time as a local "yyyy-MM-dd HH:mm", the format [agoText] reads. */
private fun localTime(rfc3339: String): String? = runCatching {
    Instant.parse(rfc3339).atZone(ZoneId.systemDefault()).toLocalDateTime().format(sheetTimeFormat)
}.getOrNull()
