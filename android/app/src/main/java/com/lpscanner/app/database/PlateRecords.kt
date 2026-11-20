package com.lpscanner.app.database

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

/** One row of the plates tab. [row] is the 1-based sheet row it was read from. */
data class PlateRecord(
    val row: Int,
    val plate: String,
    val color: String,
    val company: String,
    /** "2026-11-20 13:45" (or text typed into the sheet); null when the cell is empty. */
    val lastSeen: String?,
    /** When the car was added; null when empty (rows from before the column existed). */
    val firstSeen: String? = null,
) {
    val key: String get() = PlateFormat.key(plate)
}

/** What the user enters when adding/editing. */
data class PlateInput(val plate: String, val color: String, val company: String)

data class PlateSnapshot(
    val records: List<PlateRecord>,
    val colors: List<String>,
    val companies: List<String>,
    /** When this was read from the sheet. */
    val loadedAt: LocalDateTime = LocalDateTime.now(),
) {
    fun find(plate: String): PlateRecord? = PlateFormat.key(plate).let { key -> records.firstOrNull { it.key == key } }
}

object PlateFormat {
    /** Comparison key: letters/digits only, uppercase. "23-456-78" and "2345678" match. */
    fun key(plate: String): String = plate.filter { it.isLetterOrDigit() }.uppercase()

    /** How plates are stored: Israeli layout when it fits (12-345-67 / 123-45-678), otherwise as typed. */
    fun display(plate: String): String {
        val key = key(plate)
        if (key.all { it.isDigit() }) {
            when (key.length) {
                7 -> return "${key.take(2)}-${key.substring(2, 5)}-${key.takeLast(2)}"
                8 -> return "${key.take(3)}-${key.substring(3, 5)}-${key.takeLast(3)}"
            }
        }
        return plate.trim().uppercase()
    }
}

/** Converting between sheet cell values and records, with columns found by header ([PlateColumns]). */
object PlateRows {
    /**
     * Rows of the plates tab below the header (Sheets trims trailing empty cells, and may
     * return blank rows). [firstRow] is the sheet row of `values[0]`.
     */
    fun parse(values: List<List<String>>, columns: PlateColumns, firstRow: Int = 2): List<PlateRecord> =
        values.mapIndexedNotNull { index, cells ->
            fun cell(column: Int) = cells.getOrElse(column) { "" }.trim()
            val plate = cell(columns.plate)
            if (plate.isEmpty()) {
                null
            } else {
                PlateRecord(
                    row = firstRow + index,
                    plate = plate,
                    color = cell(columns.color),
                    company = cell(columns.company),
                    lastSeen = dateCell(cell(columns.lastSeen)),
                    firstSeen = columns.firstSeen?.let { dateCell(cell(it)) },
                )
            }
        }

    private fun dateCell(cell: String): String? = cell.takeIf(String::isNotEmpty)?.let(LastSeen::fromCell)

    /** Sheet row of [plate] among [rows] (the tab's rows from row 2 on), or null. */
    fun rowOf(plate: String, rows: List<List<String>>, columns: PlateColumns): Int? {
        val key = PlateFormat.key(plate)
        if (key.isEmpty()) return null
        val index = rows.indexOfFirst { PlateFormat.key(it.getOrElse(columns.plate) { "" }) == key }
        return if (index < 0) null else index + 2
    }

    /**
     * Cell values to write with USER_ENTERED, by column. Text gets a leading apostrophe so
     * Sheets keeps it as typed (otherwise "2345678" becomes a number); dates stay unquoted so
     * they're real dates. An empty string clears a date.
     */
    fun cells(columns: PlateColumns, input: PlateInput, lastSeen: String?): Map<Int, String> = mapOf(
        columns.plate to "'" + PlateFormat.display(input.plate),
        columns.color to "'" + input.color.trim(),
        columns.company to "'" + input.company.trim(),
        columns.lastSeen to lastSeen.orEmpty(),
    )

    /** A whole new row, as wide as our rightmost column; other columns left empty. */
    fun newRow(columns: PlateColumns, input: PlateInput, lastSeen: String?, firstSeen: String?): List<String> {
        val cells = cells(columns, input, lastSeen) + listOfNotNull(columns.firstSeen?.let { it to firstSeen.orEmpty() })
        return List(columns.last + 1) { cells[it].orEmpty() }
    }

    /** Options column values below the header, blanks dropped. */
    fun optionColumn(values: List<List<String>>, column: Int): List<String> =
        values.mapNotNull { it.getOrNull(column)?.trim()?.takeIf(String::isNotEmpty) }
}

object LastSeen {
    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** Sheets' day zero: serial number 0 = 1899-12-30 00:00. */
    private val sheetsEpoch = LocalDateTime.of(1899, 12, 30, 0, 0)

    /**
     * Normalizes a last_seen cell to "yyyy-MM-dd HH:mm". Cells are read unformatted, so real
     * dates arrive as serial numbers (46346.56806 = 2026-11-20 13:38); text is kept as is.
     */
    fun fromCell(cell: String): String {
        val serial = cell.toDoubleOrNull() ?: return cell
        val seconds = Math.round(serial * 86_400)
        return sheetsEpoch.plusSeconds(seconds).format(format)
    }

    /** Value to write: Sheets parses it as a date-time in the spreadsheet's time zone. */
    fun now(clock: () -> LocalDateTime = LocalDateTime::now): String = clock().format(format)

    /** Parses a normalized last_seen value; null for empty or free text. */
    fun parse(value: String?): LocalDateTime? = if (value == null) null else runCatching { LocalDateTime.parse(value, format) }.getOrNull()

    /** How long ago, for lists ("5 min ago"); the UI turns it into words in the user's language. */
    sealed interface Ago {
        data object JustNow : Ago
        data class Minutes(val count: Int) : Ago
        data class Hours(val count: Int) : Ago
        data object Yesterday : Ago
        data class Days(val count: Int) : Ago
        data class On(val date: LocalDate) : Ago
        /** Not a date we wrote (typed into the sheet by hand): show as is. */
        data class Text(val value: String) : Ago
    }

    fun ago(value: String, now: LocalDateTime = LocalDateTime.now()): Ago {
        val time = parse(value) ?: return Ago.Text(value)
        val elapsed = Duration.between(time, now)
        return when {
            elapsed.isNegative -> Ago.On(time.toLocalDate())
            elapsed.toMinutes() < 1 -> Ago.JustNow
            elapsed.toHours() < 1 -> Ago.Minutes(elapsed.toMinutes().toInt())
            elapsed.toDays() < 1 -> Ago.Hours(elapsed.toHours().toInt())
            elapsed.toDays() < 2 -> Ago.Yesterday
            elapsed.toDays() < 30 -> Ago.Days(elapsed.toDays().toInt())
            else -> Ago.On(time.toLocalDate())
        }
    }

    /** A point in time for detail screens ("Today 14:32", "17 Nov, 08:15"). */
    sealed interface Moment {
        data class Today(val time: LocalTime) : Moment
        data class Yesterday(val time: LocalTime) : Moment
        data class ThisYear(val dateTime: LocalDateTime) : Moment
        data class Older(val date: LocalDate) : Moment
        data class Text(val value: String) : Moment
    }

    fun moment(value: String, now: LocalDateTime = LocalDateTime.now()): Moment {
        val time = parse(value) ?: return Moment.Text(value)
        val days = ChronoUnit.DAYS.between(time.toLocalDate(), now.toLocalDate())
        return when {
            days == 0L -> Moment.Today(time.toLocalTime())
            days == 1L -> Moment.Yesterday(time.toLocalTime())
            time.year == now.year -> Moment.ThisYear(time)
            else -> Moment.Older(time.toLocalDate())
        }
    }
}
