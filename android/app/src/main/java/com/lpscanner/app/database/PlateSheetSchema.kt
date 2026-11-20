package com.lpscanner.app.database

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject

/**
 * Layout of a plate database spreadsheet:
 *
 *  - `plates` tab:  license_plate | car_color | car_company | last_seen | first_seen   (row 1 = header)
 *    Columns are found by their header, so people can reorder them or add their own in
 *    between. first_seen came later: sheets made before it get the column (at the end) on
 *    their next save; until then, and for rows added before it, it's empty.
 *  - `options` tab: colors | companies — the dropdown lists, shared by everyone using the sheet.
 *
 * Color/company cells get dropdowns backed by the options tab, so editing in Google Sheets
 * offers the same lists as the app.
 */
object PlateSheetSchema {
    const val PLATES_TAB = "plates"
    const val OPTIONS_TAB = "options"
    val PLATE_HEADERS = listOf("license_plate", "car_color", "car_company", "last_seen")
    const val FIRST_SEEN_HEADER = "first_seen"

    /** Header row of a new sheet (later reordering by people is fine). */
    val NEW_PLATE_HEADERS = PLATE_HEADERS + FIRST_SEEN_HEADER
    val OPTION_HEADERS = listOf("colors", "companies")

    val DEFAULT_COLORS = listOf(
        "White", "Black", "Silver", "Gray", "Red", "Blue", "Green", "Yellow",
        "Orange", "Brown", "Beige", "Gold", "Purple",
    )
    val DEFAULT_COMPANIES = listOf(
        "Toyota", "Hyundai", "Kia", "Mazda", "Skoda", "Suzuki", "Mitsubishi", "Nissan", "Volkswagen",
        "Chevrolet", "Honda", "Ford", "Renault", "Peugeot", "Citroen", "Seat", "Subaru", "Mercedes",
        "BMW", "Audi", "Volvo", "Opel", "Jeep", "Lexus", "Dacia", "Tesla", "BYD", "Geely", "MG", "Chery",
    )

    /** 0-based column whose header is [name] (case/whitespace-insensitive), or null. */
    fun columnOf(header: List<String>, name: String): Int? =
        header.indexOfFirst { it.trim().equals(name, ignoreCase = true) }.takeIf { it >= 0 }

    /** Required headers [header] lacks; empty when it's a plate database. */
    fun missingHeaders(header: List<String>): List<String> = PLATE_HEADERS.filter { columnOf(header, it) == null }

    /** Column letter for a 0-based index: 0 → A, 4 → E, 26 → AA. */
    fun columnLetter(index: Int): String =
        if (index < 26) ('A' + index).toString() else columnLetter(index / 26 - 1) + ('A' + index % 26)


    /** Body for spreadsheets.create: both tabs, header rows frozen. */
    fun newSpreadsheet(title: String): JsonObject = buildJsonObject {
        putJsonObject("properties") { put("title", title) }
        putJsonArray("sheets") {
            listOf(PLATES_TAB, OPTIONS_TAB).forEach { tab -> addJsonObject { put("properties", tabProperties(tab)) } }
        }
    }

    fun addTab(title: String): JsonObject = buildJsonObject {
        putJsonObject("addSheet") { put("properties", tabProperties(title)) }
    }

    /** Header row + one row per option; the two columns can have different lengths. */
    fun optionRows(colors: List<String> = DEFAULT_COLORS, companies: List<String> = DEFAULT_COMPANIES): List<List<String>> =
        listOf(OPTION_HEADERS) + List(maxOf(colors.size, companies.size)) { i ->
            listOf(colors.getOrElse(i) { "" }, companies.getOrElse(i) { "" })
        }

    /** Bold header, date-time format for last_seen and first_seen, dropdowns for color/company. */
    fun platesFormatting(platesSheetId: Int): List<JsonObject> = listOf(
        repeatCell(
            platesSheetId, startRow = 0, endRow = 1, startColumn = null, endColumn = null,
            format = buildJsonObject { putJsonObject("textFormat") { put("bold", true) } },
            fields = "userEnteredFormat.textFormat.bold",
        ),
        dateTimeColumn(platesSheetId, column = NEW_PLATE_HEADERS.indexOf("last_seen")),
        dateTimeColumn(platesSheetId, column = NEW_PLATE_HEADERS.indexOf(FIRST_SEEN_HEADER)),
        dropdown(platesSheetId, column = 1, source = "=$OPTIONS_TAB!A2:A"),
        dropdown(platesSheetId, column = 2, source = "=$OPTIONS_TAB!B2:B"),
    )

    /** Shows a column's values (below the header) as "2026-11-20 13:45". */
    fun dateTimeColumn(sheetId: Int, column: Int): JsonObject = repeatCell(
        sheetId, startRow = 1, endRow = null, startColumn = column, endColumn = column + 1,
        format = buildJsonObject {
            putJsonObject("numberFormat") {
                put("type", "DATE_TIME")
                put("pattern", "yyyy-mm-dd hh:mm")
            }
        },
        fields = "userEnteredFormat.numberFormat",
    )

    /** Bold, for a header cell added later (first_seen). */
    fun boldHeaderCell(sheetId: Int, column: Int): JsonObject = repeatCell(
        sheetId, startRow = 0, endRow = 1, startColumn = column, endColumn = column + 1,
        format = buildJsonObject { putJsonObject("textFormat") { put("bold", true) } },
        fields = "userEnteredFormat.textFormat.bold",
    )

    fun optionsFormatting(optionsSheetId: Int): List<JsonObject> = listOf(
        repeatCell(
            optionsSheetId, startRow = 0, endRow = 1, startColumn = null, endColumn = null,
            format = buildJsonObject { putJsonObject("textFormat") { put("bold", true) } },
            fields = "userEnteredFormat.textFormat.bold",
        ),
    )

    private fun tabProperties(title: String) = buildJsonObject {
        put("title", title)
        putJsonObject("gridProperties") { put("frozenRowCount", 1) }
    }

    /** Dropdown on a whole column below the header. Not strict: other values get a warning, not a rejection. */
    private fun dropdown(sheetId: Int, column: Int, source: String) = buildJsonObject {
        putJsonObject("setDataValidation") {
            put("range", gridRange(sheetId, startRow = 1, endRow = null, startColumn = column, endColumn = column + 1))
            putJsonObject("rule") {
                putJsonObject("condition") {
                    put("type", "ONE_OF_RANGE")
                    putJsonArray("values") { addJsonObject { put("userEnteredValue", source) } }
                }
                put("strict", false)
                put("showCustomUi", true)
            }
        }
    }

    private fun repeatCell(
        sheetId: Int, startRow: Int, endRow: Int?, startColumn: Int?, endColumn: Int?,
        format: JsonObject, fields: String,
    ) = buildJsonObject {
        putJsonObject("repeatCell") {
            put("range", gridRange(sheetId, startRow, endRow, startColumn, endColumn))
            putJsonObject("cell") { put("userEnteredFormat", format) }
            put("fields", fields)
        }
    }

    /** Omitted end indexes mean "to the end of the sheet". */
    private fun gridRange(sheetId: Int, startRow: Int, endRow: Int?, startColumn: Int?, endColumn: Int?) = buildJsonObject {
        put("sheetId", sheetId)
        put("startRowIndex", startRow)
        endRow?.let { put("endRowIndex", it) }
        startColumn?.let { put("startColumnIndex", it) }
        endColumn?.let { put("endColumnIndex", it) }
    }
}

/**
 * Where each field is in a plates tab, read from its header row (0-based column indexes).
 * Columns of the user's own are ignored.
 */
data class PlateColumns(
    val plate: Int,
    val color: Int,
    val company: Int,
    val lastSeen: Int,
    /** Null until the sheet has a first_seen column. */
    val firstSeen: Int?,
) {
    /** Rightmost of our columns: how wide a new row has to be. */
    val last: Int get() = maxOf(plate, color, company, lastSeen, firstSeen ?: 0)

    companion object {
        /** Null when a required header is missing (see [PlateSheetSchema.missingHeaders]). */
        fun from(header: List<String>): PlateColumns? {
            fun of(name: String) = PlateSheetSchema.columnOf(header, name)
            return PlateColumns(
                plate = of("license_plate") ?: return null,
                color = of("car_color") ?: return null,
                company = of("car_company") ?: return null,
                lastSeen = of("last_seen") ?: return null,
                firstSeen = of(PlateSheetSchema.FIRST_SEEN_HEADER),
            )
        }
    }
}
