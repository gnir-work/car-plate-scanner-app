package com.lpscanner.app.ui.database

import com.lpscanner.app.R
import android.app.Application
import com.lpscanner.app.ui.inAppLanguage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lpscanner.app.PlateScannerApp
import com.lpscanner.app.database.DuplicatePlateException
import com.lpscanner.app.database.LastSeen
import com.lpscanner.app.database.OptionKind
import com.lpscanner.app.database.PlateGoneException
import com.lpscanner.app.database.MissingColumnsException
import com.lpscanner.app.database.PlateInput
import com.lpscanner.app.database.PlateRecord
import com.lpscanner.app.database.PlateSnapshot
import com.lpscanner.app.google.ConsentRequiredException
import com.lpscanner.app.google.GoogleApiException
import com.lpscanner.app.google.PermissionsMissingException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Add/edit form state. [original] is null when adding. */
data class EditorState(val original: PlateRecord?, val initial: PlateInput)

data class PlatesUiState(
    val query: String = "",
    val busy: Boolean = false,
    val refreshing: Boolean = false,
    val editor: EditorState? = null,
    val confirmDelete: PlateRecord? = null,
    val message: String? = null,
)

/** The plates table: search, add, edit, delete, and adding colors/companies. */
class PlatesViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as PlateScannerApp).container.plates
    val snapshot: StateFlow<PlateSnapshot?> = repository.snapshot

    private val _state = MutableStateFlow(PlatesUiState())
    val state: StateFlow<PlatesUiState> = _state.asStateFlow()

    var onConsentRequired: ((ConsentRequiredException) -> Unit)? = null

    /** Raised when Google no longer grants a permission the app needs. */
    var onPermissionsMissing: (() -> Unit)? = null

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        launch(onDone = { _state.update { it.copy(refreshing = false) } }) { repository.refresh() }
    }

    fun startAdd(plate: String = "") =
        _state.update { it.copy(editor = EditorState(null, PlateInput(plate, color = "", company = ""))) }

    fun startEdit(record: PlateRecord) =
        _state.update { it.copy(editor = EditorState(record, PlateInput(record.plate, record.color, record.company))) }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    /** @param markSeen when editing, also set last_seen to now. Adding always sets it. */
    fun save(input: PlateInput, markSeen: Boolean) {
        val original = _state.value.editor?.original
        launch {
            if (original == null) {
                repository.add(input)
            } else {
                repository.update(original, input, lastSeen = if (markSeen) LastSeen.now() else original.lastSeen)
            }
            closeEditor()
        }
    }

    fun requestDelete(record: PlateRecord) = _state.update { it.copy(confirmDelete = record, editor = null) }

    fun cancelDelete() = _state.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val record = _state.value.confirmDelete ?: return
        _state.update { it.copy(confirmDelete = null) }
        launch { repository.delete(record) }
    }

    /** Adds a new color/company to the shared lists, then hands back the stored name. */
    fun addOption(kind: OptionKind, value: String, onAdded: (String) -> Unit) =
        launch { onAdded(repository.addOption(kind, value)) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun launch(onDone: () -> Unit = {}, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
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
                    _state.update { it.copy(message = messageFor(e)) }
                }
            } finally {
                _state.update { it.copy(busy = false) }
                onDone()
            }
        }
    }

    private fun messageFor(e: Exception): String = when (e) {
        is DuplicatePlateException -> text(R.string.error_duplicate, e.existing.plate)
        is PlateGoneException -> text(R.string.error_plate_gone, e.plate)
        is MissingColumnsException -> text(R.string.error_missing_columns, e.missing.joinToString(", "))
        is GoogleApiException -> text(R.string.error_google_sheets, e.code, e.googleMessage)
        is IOException -> text(R.string.error_network)
        else -> text(R.string.error_generic, e.message.orEmpty())
    }

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().inAppLanguage().getString(id, *args)
}
