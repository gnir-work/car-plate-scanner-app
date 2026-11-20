package com.lpscanner.app.ui.database

import com.lpscanner.app.database.MissingColumnsException
import com.lpscanner.app.R
import android.app.Application
import com.lpscanner.app.ui.inAppLanguage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lpscanner.app.PlateScannerApp
import com.lpscanner.app.database.PlateSheetSetup.Inspection
import com.lpscanner.app.database.SelectedSheet
import com.lpscanner.app.database.SheetLink
import com.lpscanner.app.database.Sharing
import com.lpscanner.app.google.ConsentRequiredException
import com.lpscanner.app.google.DriveFile
import com.lpscanner.app.google.GoogleApiException
import com.lpscanner.app.google.PermissionsMissingException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SheetDetails(
    val sheet: SelectedSheet,
    val sharing: Sharing,
)

sealed interface SheetMode {
    data object Loading : SheetMode

    /**
     * Choosing a sheet. [files] is null while the list loads. [current] is the sheet in use
     * when the user opened the picker to switch; going back returns to it.
     */
    data class Picking(val files: List<DriveFile>?, val current: SheetDetails? = null) : SheetMode

    data class Selected(val details: SheetDetails) : SheetMode

    /** Opening the saved sheet failed (e.g. offline at startup); the UI offers Retry. */
    data class Failed(val message: String) : SheetMode
}

data class SheetUiState(
    val mode: SheetMode = SheetMode.Loading,
    val busy: Boolean = false,
    /** A picked sheet that needs tabs added; the UI asks before changing it. */
    val pendingSetup: Pair<DriveFile, Inspection.NeedsSetup>? = null,
    val message: String? = null,
)

/** Choosing/creating the plate database sheet for the signed-in account. */
class SheetViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as PlateScannerApp).container
    private val _state = MutableStateFlow(SheetUiState())
    val state: StateFlow<SheetUiState> = _state.asStateFlow()

    /** Raised when Google needs the consent screen again (e.g. weekly in Testing mode). */
    var onConsentRequired: ((ConsentRequiredException) -> Unit)? = null

    /** Raised when Google no longer grants a permission the app needs. */
    var onPermissionsMissing: (() -> Unit)? = null

    private var email: String? = null
    private var job: Job? = null

    fun onAccount(email: String?) {
        if (email == this.email) return
        this.email = email
        job?.cancel()
        _state.value = SheetUiState()
        viewModelScope.launch { container.plates.attach(null) }
        if (email != null) run(showBusy = false) { restoreOrPick(email) }
    }

    fun retry() {
        val email = email ?: return
        _state.update { it.copy(mode = SheetMode.Loading) }
        run(showBusy = false) { restoreOrPick(email) }
    }

    fun refreshList() = run { showPicker() }

    fun refreshSelected() = run {
        val details = (_state.value.mode as? SheetMode.Selected)?.details ?: return@run
        loadSelected(details.sheet)
    }

    fun create(title: String) = run {
        val name = title.trim().ifEmpty { text(R.string.default_sheet_name) }
        val id = container.sheetSetup.create(name)
        select(DriveFile(id = id, name = name), name)
    }

    fun pick(file: DriveFile) = run { open(file) }

    /** "Open from a link": a pasted Google Sheets link (or ID). Returns false if it isn't one. */
    fun openLink(text: String): Boolean {
        val id = SheetLink.idFrom(text) ?: return false
        run {
            try {
                open(DriveFile(id = id, name = "", openedByLink = true))
            } catch (e: GoogleApiException) {
                if (e.isNotFound || e.code == 403 && !e.isMissingPermission) {
                    _state.update { it.copy(message = text(R.string.picker_link_cant_open, email.orEmpty())) }
                } else {
                    throw e
                }
            }
        }
        return true
    }

    private suspend fun open(file: DriveFile) {
        when (val inspection = container.sheetSetup.inspect(file.id)) {
            is Inspection.Ready -> select(file, inspection.name)
            is Inspection.NeedsSetup -> _state.update { it.copy(pendingSetup = file to inspection) }
            is Inspection.Incompatible -> _state.update {
                it.copy(
                    message = text(
                        R.string.sheet_incompatible,
                        inspection.name,
                        inspection.missing.joinToString(", "),
                    ),
                )
            }
        }
    }

    fun confirmSetup() = run {
        val (file, needs) = _state.value.pendingSetup ?: return@run
        _state.update { it.copy(pendingSetup = null) }
        container.sheetSetup.setUp(file.id, needs)
        select(file, needs.name)
    }

    fun cancelSetup() = _state.update { it.copy(pendingSetup = null) }

    /** Opens the picker; the current sheet stays in use until another one is chosen. */
    fun switchSheet() = run {
        val current = (_state.value.mode as? SheetMode.Selected)?.details
        _state.update { it.copy(mode = SheetMode.Picking(files = null, current = current)) }
        showPicker()
    }

    /** Leaves the picker without changing sheets. */
    fun cancelSwitch() {
        val current = (_state.value.mode as? SheetMode.Picking)?.current ?: return
        job?.cancel()
        _state.update { it.copy(mode = SheetMode.Selected(current), busy = false) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private suspend fun restoreOrPick(email: String) {
        val saved = container.selectedSheet.get(email)
        if (saved == null) showPicker() else loadSelected(saved)
    }

    private suspend fun select(file: DriveFile, name: String) {
        val sheet = SelectedSheet(file.id, name)
        email?.let {
            container.selectedSheet.set(it, sheet)
            if (file.openedByLink) container.selectedSheet.addLinked(it, sheet)
        }
        loadSelected(sheet)
    }

    private suspend fun loadSelected(sheet: SelectedSheet) {
        try {
            val inspection = container.sheetSetup.inspect(sheet.id)
            if (inspection !is Inspection.Ready) {
                forget(sheet, text(R.string.sheet_not_database_anymore, inspection.name))
                return
            }
            val current = sheet.copy(name = inspection.name) // follows renames
            val sharing = sharingOf(sheet.id)
            container.plates.attach(sheet.id)
            container.plates.refresh()
            email?.let { container.selectedSheet.set(it, current) }
            _state.update { it.copy(mode = SheetMode.Selected(SheetDetails(current, sharing))) }
        } catch (e: GoogleApiException) {
            if (e.isNotFound || e.code == 403 && !e.isMissingPermission) {
                forget(sheet, text(R.string.sheet_cant_open, sheet.name))
            } else {
                throw e
            }
        }
    }

    /** Sheets opened from a link aren't visible to the app's Drive permission: 404. */
    private suspend fun sharingOf(id: String): Sharing = try {
        Sharing.from(container.drive.permissions(id))
    } catch (e: GoogleApiException) {
        if (e.isNotFound) Sharing.Hidden else throw e
    }

    private suspend fun forget(sheet: SelectedSheet, message: String) {
        email?.let {
            container.selectedSheet.clear(it)
            container.selectedSheet.removeLinked(it, sheet.id)
        }
        container.plates.attach(null)
        _state.update { it.copy(mode = SheetMode.Picking(files = null), message = message) }
        showPicker()
    }

    private suspend fun showPicker() {
        _state.update {
            val picking = it.mode as? SheetMode.Picking
            it.copy(mode = SheetMode.Picking(files = picking?.files, current = picking?.current))
        }
        // Drive only lists the sheets this app created; add the ones opened from a link.
        val created = container.drive.listSpreadsheets()
        val linked = email?.let { container.selectedSheet.linked(it) }.orEmpty()
            .filter { sheet -> created.none { it.id == sheet.id } }
            .map { DriveFile(id = it.id, name = it.name, openedByLink = true) }
        val files = created + linked
        _state.update {
            val current = (it.mode as? SheetMode.Picking)?.current
            it.copy(mode = SheetMode.Picking(files, current))
        }
    }

    private fun run(showBusy: Boolean = true, block: suspend () -> Unit) {
        if (_state.value.busy) return
        if (showBusy) _state.update { it.copy(busy = true) }
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConsentRequiredException) {
                onConsentRequired?.invoke(e)
            } catch (e: PermissionsMissingException) {
                onPermissionsMissing?.invoke()
            } catch (e: Exception) {
                if (e is GoogleApiException && e.isMissingPermission) {
                    onPermissionsMissing?.invoke()
                } else {
                    _state.update {
                        when (val mode = it.mode) {
                            // Don't leave the screen on a spinner: offer a retry instead.
                            SheetMode.Loading -> it.copy(mode = SheetMode.Failed(friendly(e)))
                            is SheetMode.Picking -> it.copy(mode = mode.copy(files = mode.files ?: emptyList()), message = friendly(e))
                            else -> it.copy(message = friendly(e))
                        }
                    }
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun friendly(e: Exception) = when (e) {
        is IOException -> text(R.string.error_network)
        is MissingColumnsException -> text(R.string.error_missing_columns, e.missing.joinToString(", "))
        is GoogleApiException -> text(R.string.error_google_sheets, e.code, e.googleMessage)
        else -> text(R.string.error_generic, e.message.orEmpty())
    }

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().inAppLanguage().getString(id, *args)
}
