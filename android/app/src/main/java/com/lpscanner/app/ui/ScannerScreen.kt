package com.lpscanner.app.ui

import androidx.compose.ui.res.stringResource
import com.lpscanner.app.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lpscanner.app.ui.database.PlatesViewModel
import com.lpscanner.app.ui.theme.AppColors
import com.lpscanner.app.ui.theme.AppIcons
import com.lpscanner.app.ui.theme.PlateChip
import com.lpscanner.app.ui.theme.PlateSize

/**
 * The Scanner tab (design: "Scanner – camera / manual"): live camera with shutter and Gallery,
 * or type the plate. Every check opens the full-screen result.
 */
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = viewModel(),
    plates: PlatesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onImagePicked)
    }
    val working = state.scan == ScanState.Working

    Column(Modifier.fillMaxSize().background(AppColors.Background).imePadding()) {
        CameraPanel(
            busy = working,
            onCaptured = viewModel::onPhotoCaptured,
            onCaptureFailed = viewModel::onCaptureFailed,
            onGallery = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        ManualPanel(
            state = state,
            working = working,
            onCheck = viewModel::checkTyped,
            onDismissNote = viewModel::closeResult,
        )
    }

    state.lookup?.let { lookup ->
        ScanResultScreen(lookup, state.source, state.busy, snapshot, viewModel, plates)
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text(stringResource(R.string.ok)) } },
            text = { Text(message) },
        )
    }
}

@Composable
private fun ManualPanel(
    state: ScannerUiState,
    working: Boolean,
    onCheck: (String) -> Unit,
    onDismissNote: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val check = {
        focus.clearFocus()
        onCheck(typed)
        typed = ""
    }

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScanNote(state, onDismissNote)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = AppColors.Divider)
            Text(stringResource(R.string.scan_or_type), fontSize = 13.sp, color = AppColors.Muted)
            HorizontalDivider(Modifier.weight(1f), color = AppColors.Divider)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.license_plate)) },
                placeholder = { Text("12-345-67", style = plateTextStyle(TextStyle(fontFamily = FontFamily.Monospace)), modifier = Modifier.fillMaxWidth()) },
                singleLine = true,
                textStyle = plateTextStyle(TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 1.sp)),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (typed.isNotBlank()) check() }),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = check,
                enabled = typed.isNotBlank() && !working,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.padding(top = 8.dp).height(52.dp),
            ) {
                if (working && state.source == PlateSource.Typed) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = AppColors.OnPrimary, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.scan_check), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (state.recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.scan_recent), fontSize = 13.sp, color = AppColors.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.recent.forEach { plate ->
                        val again = stringResource(R.string.scan_check_again, plate)
                        PlateChip(
                            plate,
                            size = PlateSize.Small,
                            width = null,
                            modifier = Modifier.clickable(enabled = !working, onClickLabel = again) { onCheck(plate) },
                        )
                    }
                }
            }
        }
    }
}

/** One line about the last photo when it didn't lead to a result screen. */
@Composable
private fun ScanNote(state: ScannerUiState, onDismiss: () -> Unit) {
    val text = when (val scan = state.scan) {
        is ScanState.NoPlate -> stringResource(R.string.scan_no_plate)
        is ScanState.Failed -> stringResource(R.string.scan_read_failed, scan.message)
        ScanState.Working -> if (state.source == PlateSource.Photo) stringResource(R.string.scan_reading) else null
        ScanState.Idle -> null
    } ?: return
    Row(
        Modifier.fillMaxWidth().background(AppColors.WarningBg, RoundedCornerShape(8.dp)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 14.sp, color = AppColors.WarningText, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        if (state.scan != ScanState.Working) {
            IconButton(onClick = onDismiss) {
                Icon(AppIcons.Close, contentDescription = stringResource(R.string.dismiss), tint = AppColors.WarningText, modifier = Modifier.size(18.dp))
            }
        } else {
            Box(Modifier.padding(12.dp)) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
        }
    }
}
