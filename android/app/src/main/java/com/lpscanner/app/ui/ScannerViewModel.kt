package com.lpscanner.app.ui

import com.lpscanner.app.database.PlateGoneException
import com.lpscanner.app.database.MissingColumnsException
import com.lpscanner.app.R
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lpscanner.app.PlateScannerApp
import com.lpscanner.app.database.DuplicatePlateException
import com.lpscanner.app.database.LastSeen
import com.lpscanner.app.database.OptionKind
import com.lpscanner.app.database.PlateFormat
import com.lpscanner.app.database.PlateInput
import com.lpscanner.app.database.PlateMatch
import com.lpscanner.app.database.PlateMatcher
import com.lpscanner.app.database.PlateRecord
import com.lpscanner.app.database.PlateSnapshot
import com.lpscanner.app.google.ConsentRequiredException
import com.lpscanner.app.google.GoogleApiException
import com.lpscanner.app.ocr.PlateRecognizer
import com.lpscanner.app.ocr.ScanResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the plate came from: read from a photo, or typed by the user. */
enum class PlateSource { Photo, Typed }

sealed interface ScanState {
    data object Idle : ScanState

    /** Reading the photo, or checking the plate against the database. */
    data object Working : ScanState

    /** The photo had nothing that looks like a plate. */
    data class NoPlate(val result: ScanResult) : ScanState

    data class Failed(val message: String) : ScanState
}

/** Automatic last_seen update after a match. */
sealed interface SeenUpdate {
    data object Updating : SeenUpdate
    data object Updated : SeenUpdate
    data object Undone : SeenUpdate
    data class Failed(val message: String) : SeenUpdate
}

/** Result of checking a plate against the plate database; non-null opens the result screen. */
sealed interface Lookup {
    /** @param previousSeen last_seen before this scan (null if never), restored by Undo. */
    data class Found(
        val match: PlateMatch,
        val previousSeen: String?,
        val seen: SeenUpdate,
        val offline: Boolean,
    ) : Lookup

    data class NotFound(val plate: String, val offline: Boolean) : Lookup

    /** Just added from the not-found screen; last_seen is now. */
    data class Added(val record: PlateRecord) : Lookup

    data class Failed(val plate: String, val message: String) : Lookup
}

data class ScannerUiState(
    /** The photo being scanned: a picked image's Uri or a camera capture's Bitmap. */
    val image: Any? = null,
    val source: PlateSource = PlateSource.Photo,
    val scan: ScanState = ScanState.Idle,
    /** The plate readings being checked, best first (one when typed). Kept for Retry. */
    val readings: List<String> = emptyList(),
    val lookup: Lookup? = null,
    /** Plates checked this session, newest first, for the "Recent scans" shortcuts. */
    val recent: List<String> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

/** The Scanner tab: camera/gallery/typed plate → database check → result screen. */
class ScannerViewModel(app: Application) : AndroidViewModel(app) {
    private val recognizer = PlateRecognizer(app)
    private val repository = (app as PlateScannerApp).container.plates
    val snapshot: StateFlow<PlateSnapshot?> = repository.snapshot

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()
    private var job: Job? = null

    var onConsentRequired: ((ConsentRequiredException) -> Unit)? = null

    fun onImagePicked(uri: Uri) = scan(uri) { recognizer.recognize(uri) }

    /** A photo from the in-app camera, already upright. */
    fun onPhotoCaptured(photo: Bitmap) = scan(photo) { recognizer.recognize(photo) }

    fun onCaptureFailed(message: String) = _state.update { it.copy(message = text(R.string.scan_capture_failed, message)) }

    /** The "or type it" field and the recent-scan shortcuts. */
    fun checkTyped(text: String) {
        if (PlateFormat.key(text).length < MIN_PLATE_CHARS) {
            _state.update { it.copy(message = text(R.string.scan_type_whole)) }
            return
        }
        val plate = PlateFormat.display(text)
        start(ScannerUiState(source = PlateSource.Typed, scan = ScanState.Working, readings = listOf(plate))) {
            lookUp(listOf(plate))
        }
    }

    fun retryLookup() {
        val readings = _state.value.readings.ifEmpty { return }
        job?.cancel()
        _state.update { it.copy(lookup = null, scan = ScanState.Working) }
        job = viewModelScope.launch { lookUp(readings) }
    }

    /** Leaves the result screen (or clears a "no plate" note) and goes back to the camera. */
    fun closeResult() {
        job?.cancel()
        _state.update { ScannerUiState(recent = it.recent) }
    }

    fun undoSeen() {
        val found = _state.value.lookup as? Lookup.Found ?: return
        if (found.seen != SeenUpdate.Updated) return
        run {
            repository.setLastSeen(found.match.record.plate, found.previousSeen)
            setLookup(found.copy(seen = SeenUpdate.Undone))
        }
    }

    fun saveNew(input: PlateInput) = run {
        val snapshot = try {
            repository.add(input)
        } catch (e: DuplicatePlateException) {
            // Someone added it since the check: show it as found instead.
            setLookup(Lookup.Found(PlateMatch(e.existing, viaBestReading = true), e.existing.lastSeen, SeenUpdate.Undone, offline = false))
            return@run
        }
        snapshot.find(input.plate)?.let { setLookup(Lookup.Added(it)) }
    }

    fun addOption(kind: OptionKind, value: String, onAdded: (String) -> Unit) = run { onAdded(repository.addOption(kind, value)) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun scan(image: Any, recognize: suspend () -> ScanResult) =
        start(ScannerUiState(image = image, source = PlateSource.Photo, scan = ScanState.Working)) {
            val result = try {
                recognize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(scan = ScanState.Failed(e.message ?: e.javaClass.simpleName)) }
                return@start
            }
            val readings = result.candidates.map { it.text }
            if (readings.isEmpty()) {
                _state.update { it.copy(scan = ScanState.NoPlate(result)) }
                return@start
            }
            _state.update { it.copy(readings = readings) }
            lookUp(readings)
        }

    /** Starts a new scan/check, replacing any in progress; recent scans carry over. */
    private fun start(initial: ScannerUiState, block: suspend () -> Unit) {
        job?.cancel()
        _state.update { initial.copy(recent = it.recent) }
        job = viewModelScope.launch { block() }
    }

    private suspend fun lookUp(readings: List<String>) {
        // Fresh data, so a plate a teammate just added counts. Offline: fall back to the cache.
        val (snapshot, offline) = try {
            repository.refresh() to false
        } catch (e: CancellationException) {
            throw e
        } catch (e: ConsentRequiredException) {
            onConsentRequired?.invoke(e)
            return setLookup(Lookup.Failed(readings.first(), text(R.string.scan_sign_in_again)))
        } catch (e: MissingColumnsException) {
            return setLookup(Lookup.Failed(readings.first(), messageFor(e))) // not an offline problem
        } catch (e: Exception) {
            val cached = repository.snapshot.value ?: return setLookup(Lookup.Failed(readings.first(), messageFor(e)))
            cached to true
        }

        val match = PlateMatcher.match(snapshot, readings)
            ?: return setLookup(Lookup.NotFound(readings.first(), offline))
        val found = Lookup.Found(match, previousSeen = match.record.lastSeen, seen = SeenUpdate.Updating, offline = offline)
        setLookup(found)
        try {
            val previous = repository.setLastSeen(match.record.plate, LastSeen.now())
            setLookup(found.copy(previousSeen = previous, seen = SeenUpdate.Updated))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setLookup(found.copy(seen = SeenUpdate.Failed(messageFor(e))))
        }
    }

    private fun setLookup(lookup: Lookup) = _state.update {
        val plate = when (lookup) {
            is Lookup.Found -> lookup.match.record.plate
            is Lookup.Added -> lookup.record.plate
            is Lookup.NotFound -> PlateFormat.display(lookup.plate)
            is Lookup.Failed -> null
        }
        it.copy(lookup = lookup, scan = ScanState.Idle, recent = plate?.let { p -> remember(it.recent, p) } ?: it.recent)
    }

    private fun remember(recent: List<String>, plate: String): List<String> =
        (listOf(plate) + recent.filter { PlateFormat.key(it) != PlateFormat.key(plate) }).take(MAX_RECENT)

    private fun run(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConsentRequiredException) {
                onConsentRequired?.invoke(e)
            } catch (e: Exception) {
                _state.update { it.copy(message = messageFor(e)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun messageFor(e: Exception): String = when (e) {
        is DuplicatePlateException -> text(R.string.error_duplicate, e.existing.plate)
        is PlateGoneException -> text(R.string.error_plate_gone, e.plate)
        is MissingColumnsException -> text(R.string.error_missing_columns, e.missing.joinToString(", "))
        is GoogleApiException -> text(R.string.error_google_sheets, e.code, e.googleMessage)
        is IOException -> text(R.string.error_network)
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().inAppLanguage().getString(id, *args)

    override fun onCleared() {
        recognizer.close()
    }

    private companion object {
        const val MIN_PLATE_CHARS = 4
        const val MAX_RECENT = 6
    }
}
