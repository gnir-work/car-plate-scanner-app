package com.lpscanner.app.database

import com.lpscanner.app.database.PlateSheetSchema.OPTIONS_TAB
import com.lpscanner.app.database.PlateSheetSchema.PLATES_TAB
import com.lpscanner.app.database.PlateSheetSchema.columnLetter
import com.lpscanner.app.google.SheetsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class DuplicatePlateException(val existing: PlateRecord) :
    Exception("${existing.plate} is already in the database")

class PlateGoneException(val plate: String) :
    Exception("$plate isn't in the sheet any more")

/** Someone renamed or deleted one of the plates tab's required columns. */
class MissingColumnsException(val missing: List<String>) :
    Exception("The plates tab is missing the columns ${missing.joinToString()}")

enum class OptionKind(val column: Int, val letter: String) { Color(0, "A"), Company(1, "B") }

/**
 * The plate database for the selected sheet, cached in memory and shared by all screens.
 *
 * Columns are found by their header, not their position, so people can reorder them in
 * Google Sheets. Every change re-reads the plates tab first (header and rows: a teammate may
 * have moved columns or inserted/deleted rows meanwhile), writes, then reloads the snapshot.
 */
class PlateRepository(private val sheets: SheetsApi) {
    private val mutex = Mutex()
    private var sheetId: String? = null
    private var platesTabId: Int? = null

    private val _attached = MutableStateFlow<String?>(null)

    /** Spreadsheet ID of the connected database, or null when none is selected. */
    val attached: StateFlow<String?> = _attached.asStateFlow()

    private val _snapshot = MutableStateFlow<PlateSnapshot?>(null)

    /** Latest loaded data, or null while nothing is loaded/selected. */
    val snapshot: StateFlow<PlateSnapshot?> = _snapshot.asStateFlow()

    suspend fun attach(spreadsheetId: String?) = mutex.withLock {
        if (spreadsheetId == sheetId) return@withLock
        sheetId = spreadsheetId
        _attached.value = spreadsheetId
        platesTabId = null
        _snapshot.value = null
    }

    suspend fun refresh(): PlateSnapshot = mutex.withLock { reload() }

    /** Adds a car; first_seen is when it's added (adding that column if the sheet lacks it). */
    suspend fun add(
        input: PlateInput,
        lastSeen: String? = LastSeen.now(),
        firstSeen: String = LastSeen.now(),
    ): PlateSnapshot = mutex.withLock {
        val id = requireSheet()
        val tab = withFirstSeenColumn(id, readPlates(id))
        tab.rowOf(input.plate)?.let { row -> throw DuplicatePlateException(tab.recordAt(row)) }
        val row = PlateRows.newRow(tab.columns, inEnglish(input), lastSeen, firstSeen)
        sheets.appendValues(id, "$PLATES_TAB!A:${columnLetter(row.size - 1)}", listOf(row))
        reload()
    }

    /** Edits plate/color/company (and last_seen); first_seen and the user's own columns stay as they are. */
    suspend fun update(original: PlateRecord, input: PlateInput, lastSeen: String? = original.lastSeen): PlateSnapshot =
        mutex.withLock {
            val id = requireSheet()
            val tab = withFirstSeenColumn(id, readPlates(id))
            val row = tab.rowOf(original.plate) ?: throw PlateGoneException(original.plate)
            tab.rowOf(input.plate)?.takeIf { it != row }?.let { throw DuplicatePlateException(tab.recordAt(it)) }
            writeCells(id, row, PlateRows.cells(tab.columns, inEnglish(input), lastSeen))
            reload()
        }

    /**
     * Sets last_seen of [plate] (e.g. to [LastSeen.now], or back to an old value for Undo; null
     * empties it). @return the value it had before, for undoing.
     */
    suspend fun setLastSeen(plate: String, value: String?): String? = mutex.withLock {
        val id = requireSheet()
        val tab = withFirstSeenColumn(id, readPlates(id))
        val row = tab.rowOf(plate) ?: throw PlateGoneException(plate)
        val previous = tab.recordAt(row).lastSeen
        writeCells(id, row, mapOf(tab.columns.lastSeen to value.orEmpty()))
        reload()
        previous
    }

    suspend fun delete(record: PlateRecord): PlateSnapshot = mutex.withLock {
        val id = requireSheet()
        val row = readPlates(id).rowOf(record.plate) ?: throw PlateGoneException(record.plate)
        val request = buildJsonObject {
            putJsonObject("deleteDimension") {
                putJsonObject("range") {
                    put("sheetId", platesTabId(id))
                    put("dimension", "ROWS")
                    put("startIndex", row - 1) // 0-based, inclusive
                    put("endIndex", row)
                }
            }
        }
        sheets.batchUpdate(id, listOf(request))
        reload()
    }

    /** Adds a color/company to the shared lists, in English when known (no-op if it's already there). Returns the stored spelling. */
    suspend fun addOption(kind: OptionKind, value: String): String = mutex.withLock {
        val id = requireSheet()
        val name = CarVocabulary.toEnglish(kind, value)
        require(name.isNotEmpty()) { "Empty name" }
        val existing = PlateRows.optionColumn(sheets.getValues(id, "$OPTIONS_TAB!A2:B").values, kind.column)
        existing.firstOrNull { it.equals(name, ignoreCase = true) }?.let { return@withLock it }
        val column = sheets.getValues(id, "$OPTIONS_TAB!${kind.letter}2:${kind.letter}").values
        val row = column.size + 2 // first row after the column's last value (blank gaps are rare)
        sheets.updateValues(id, listOf(SheetsApi.ValueRange("$OPTIONS_TAB!${kind.letter}$row", listOf(listOf(name)))))
        reload()
        name
    }

    /** The sheet is always in English: known Hebrew names are translated, custom ones kept as typed. */
    private fun inEnglish(input: PlateInput) = input.copy(
        color = CarVocabulary.toEnglish(OptionKind.Color, input.color),
        company = CarVocabulary.toEnglish(OptionKind.Company, input.company),
    )

    /** The plates tab as it is now: where each column is, and the rows below the header. */
    private class PlatesTab(val header: List<String>, val columns: PlateColumns, val rows: List<List<String>>) {
        fun rowOf(plate: String): Int? = PlateRows.rowOf(plate, rows, columns)

        fun recordAt(row: Int): PlateRecord = PlateRows.parse(listOf(rows[row - 2]), columns, firstRow = row).first()
    }

    private suspend fun readPlates(id: String): PlatesTab = platesTab(sheets.getValues(id, PLATES_RANGE).values)

    private fun platesTab(values: List<List<String>>): PlatesTab {
        val header = values.firstOrNull().orEmpty()
        val columns = PlateColumns.from(header) ?: throw MissingColumnsException(PlateSheetSchema.missingHeaders(header))
        return PlatesTab(header, columns, values.drop(1))
    }

    /**
     * Adds the first_seen header (bold, dates formatted) after the last column if the sheet
     * was made before it existed. Called before every save, so old sheets upgrade on their own.
     */
    private suspend fun withFirstSeenColumn(id: String, tab: PlatesTab): PlatesTab {
        if (tab.columns.firstSeen != null) return tab
        val column = tab.header.size // first column after the last header, ours or the user's
        sheets.updateValues(id, listOf(SheetsApi.ValueRange("$PLATES_TAB!${columnLetter(column)}1", listOf(listOf(PlateSheetSchema.FIRST_SEEN_HEADER)))))
        val tabId = platesTabId(id)
        sheets.batchUpdate(id, listOf(PlateSheetSchema.boldHeaderCell(tabId, column), PlateSheetSchema.dateTimeColumn(tabId, column)))
        return PlatesTab(tab.header + PlateSheetSchema.FIRST_SEEN_HEADER, tab.columns.copy(firstSeen = column), tab.rows)
    }

    /** Writes single cells of [row]: plate data is spread over columns that may not be next to each other. */
    private suspend fun writeCells(id: String, row: Int, cells: Map<Int, String>) {
        sheets.updateValues(
            id,
            cells.map { (column, value) -> SheetsApi.ValueRange("$PLATES_TAB!${columnLetter(column)}$row", listOf(listOf(value))) },
            userEntered = true,
        )
    }

    private suspend fun reload(): PlateSnapshot {
        val id = requireSheet()
        val (plates, options) = sheets.getValues(id, listOf(PLATES_RANGE, "$OPTIONS_TAB!A2:B"))
        val tab = platesTab(plates.values)
        return PlateSnapshot(
            records = PlateRows.parse(tab.rows, tab.columns),
            colors = PlateRows.optionColumn(options.values, OptionKind.Color.column),
            companies = PlateRows.optionColumn(options.values, OptionKind.Company.column),
        ).also { if (sheetId == id) _snapshot.value = it }
    }

    private suspend fun platesTabId(id: String): Int =
        platesTabId ?: sheets.get(id).sheets.first { it.properties.title == PLATES_TAB }.properties.sheetId
            .also { platesTabId = it }

    private fun requireSheet(): String = checkNotNull(sheetId) { "No plate database selected" }

    private companion object {
        /** The whole plates tab, header included; wide enough for columns users add themselves. */
        const val PLATES_RANGE = "$PLATES_TAB!A1:AZ"
    }
}
