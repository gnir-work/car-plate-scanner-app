package com.lpscanner.app.database

import com.lpscanner.app.database.PlateSheetSchema.OPTIONS_TAB
import com.lpscanner.app.database.PlateSheetSchema.PLATES_TAB
import com.lpscanner.app.google.SheetsApi

/** Creates plate database spreadsheets, and checks/prepares existing ones. */
class PlateSheetSetup(private val sheets: SheetsApi) {

    sealed interface Inspection {
        val name: String

        data class Ready(override val name: String) : Inspection

        /** Usable after adding the missing pieces; ask the user before changing their sheet. */
        data class NeedsSetup(
            override val name: String,
            val addPlatesTab: Boolean,
            val addOptionsTab: Boolean,
            val writePlatesHeader: Boolean,
        ) : Inspection

        /** The "plates" tab exists but lacks some of our columns ([missing] headers). */
        data class Incompatible(override val name: String, val missing: List<String>) : Inspection
    }

    /** Creates a new, private, fully set-up database. Returns its spreadsheet ID. */
    suspend fun create(title: String): String {
        val created = sheets.create(PlateSheetSchema.newSpreadsheet(title))
        val platesId = created.sheetId(PLATES_TAB)
        val optionsId = created.sheetId(OPTIONS_TAB)
        sheets.batchUpdate(
            created.spreadsheetId,
            PlateSheetSchema.platesFormatting(platesId) + PlateSheetSchema.optionsFormatting(optionsId),
        )
        sheets.updateValues(created.spreadsheetId, listOf(platesHeader(), optionsValues()))
        return created.spreadsheetId
    }

    suspend fun inspect(spreadsheetId: String): Inspection {
        val sheet = sheets.get(spreadsheetId)
        val name = sheet.properties.title
        val titles = sheet.sheets.map { it.properties.title }
        val hasPlates = PLATES_TAB in titles
        val hasOptions = OPTIONS_TAB in titles

        var headerEmpty = !hasPlates
        if (hasPlates) {
            val header = sheets.getValues(spreadsheetId, "$PLATES_TAB!A1:Z1").values.firstOrNull().orEmpty()
            when {
                header.all { it.isBlank() } -> headerEmpty = true
                PlateSheetSchema.missingHeaders(header).isNotEmpty() ->
                    return Inspection.Incompatible(name, PlateSheetSchema.missingHeaders(header))
            }
        }
        return if (hasPlates && hasOptions && !headerEmpty) {
            Inspection.Ready(name)
        } else {
            Inspection.NeedsSetup(name, addPlatesTab = !hasPlates, addOptionsTab = !hasOptions, writePlatesHeader = headerEmpty)
        }
    }

    /** Adds whatever [needs] says is missing, without touching existing data. */
    suspend fun setUp(spreadsheetId: String, needs: Inspection.NeedsSetup) {
        val addTabs = buildList {
            if (needs.addPlatesTab) add(PlateSheetSchema.addTab(PLATES_TAB))
            if (needs.addOptionsTab) add(PlateSheetSchema.addTab(OPTIONS_TAB))
        }
        if (addTabs.isNotEmpty()) sheets.batchUpdate(spreadsheetId, addTabs)

        val sheet = sheets.get(spreadsheetId)
        val formatting = buildList {
            if (needs.writePlatesHeader) addAll(PlateSheetSchema.platesFormatting(sheet.sheetId(PLATES_TAB)))
            if (needs.addOptionsTab) addAll(PlateSheetSchema.optionsFormatting(sheet.sheetId(OPTIONS_TAB)))
        }
        if (formatting.isNotEmpty()) sheets.batchUpdate(spreadsheetId, formatting)

        val values = buildList {
            if (needs.writePlatesHeader) add(platesHeader())
            if (needs.addOptionsTab) add(optionsValues())
        }
        if (values.isNotEmpty()) sheets.updateValues(spreadsheetId, values)
    }

    private fun platesHeader() = SheetsApi.ValueRange("$PLATES_TAB!A1:E1", listOf(PlateSheetSchema.NEW_PLATE_HEADERS))

    private fun optionsValues() = PlateSheetSchema.optionRows().let { rows ->
        SheetsApi.ValueRange("$OPTIONS_TAB!A1:B${rows.size}", rows)
    }

    private fun SheetsApi.Spreadsheet.sheetId(title: String): Int =
        sheets.first { it.properties.title == title }.properties.sheetId
}
