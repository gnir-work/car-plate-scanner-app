package com.lpscanner.app.google

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Google Sheets v4 REST calls. https://developers.google.com/sheets/api/reference/rest */
class SheetsApi(
    private val client: GoogleApiClient,
    private val baseUrl: HttpUrl = "https://sheets.googleapis.com/v4/spreadsheets".toHttpUrl(),
) {
    @Serializable
    data class SheetProperties(val sheetId: Int, val title: String)

    @Serializable
    data class Sheet(val properties: SheetProperties)

    @Serializable
    data class SpreadsheetProperties(val title: String)

    @Serializable
    data class Spreadsheet(
        val spreadsheetId: String,
        val properties: SpreadsheetProperties,
        val sheets: List<Sheet> = emptyList(),
    )

    @Serializable
    data class ValueRange(val range: String? = null, val values: List<List<String>> = emptyList())

    @Serializable
    data class BatchGetResponse(val valueRanges: List<ValueRange> = emptyList())

    @Serializable
    data class BatchUpdateResponse(val replies: List<JsonObject> = emptyList())

    /** Creates a spreadsheet in the user's Drive. New spreadsheets are private to their owner. */
    suspend fun create(body: JsonObject): Spreadsheet = client.post(baseUrl, body, Spreadsheet.serializer())

    suspend fun get(spreadsheetId: String): Spreadsheet {
        val url = baseUrl.newBuilder()
            .addPathSegment(spreadsheetId)
            .addQueryParameter("fields", "spreadsheetId,properties.title,sheets.properties(sheetId,title)")
            .build()
        return client.get(url, Spreadsheet.serializer())
    }

    /** Structural/formatting changes. Each request is one entry like {"addSheet": {...}}. */
    suspend fun batchUpdate(spreadsheetId: String, requests: List<JsonObject>): BatchUpdateResponse {
        val url = baseUrl.newBuilder().addPathSegment("$spreadsheetId:batchUpdate").build()
        val body = buildJsonObject { putJsonArray("requests") { requests.forEach { add(it) } } }
        return client.post(url, body, BatchUpdateResponse.serializer())
    }

    /**
     * Reads cells, e.g. range "plates!A1:D1". Values come back unformatted, so they don't depend
     * on cell formatting or locale: dates as serial numbers (days since 1899-12-30), numbers as
     * numbers. Everything is delivered as strings.
     */
    suspend fun getValues(spreadsheetId: String, range: String): ValueRange {
        val url = baseUrl.newBuilder()
            .addPathSegment(spreadsheetId)
            .addPathSegment("values")
            .addPathSegment(range)
            .unformatted()
            .build()
        return client.get(url, ValueRange.serializer())
    }

    /** Reads several ranges in one request; results come back in the same order. */
    suspend fun getValues(spreadsheetId: String, ranges: List<String>): List<ValueRange> {
        val url = baseUrl.newBuilder()
            .addPathSegment(spreadsheetId)
            .addPathSegment("values:batchGet")
            .apply { ranges.forEach { addQueryParameter("ranges", it) } }
            .unformatted()
            .build()
        return client.get(url, BatchGetResponse.serializer()).valueRanges
    }

    /**
     * Adds rows after the last row of the table in [range] (e.g. "plates!A:D"), parsing values as
     * if typed. Writes into the existing empty rows (OVERWRITE) so they keep the column formatting
     * and dropdowns; inserting fresh rows would copy the header row's formatting instead.
     */
    suspend fun appendValues(spreadsheetId: String, range: String, rows: List<List<String>>) {
        val url = baseUrl.newBuilder()
            .addPathSegment(spreadsheetId)
            .addPathSegment("values")
            .addPathSegment("$range:append")
            .addQueryParameter("valueInputOption", "USER_ENTERED")
            .addQueryParameter("insertDataOption", "OVERWRITE")
            .build()
        val body = buildJsonObject {
            putJsonArray("values") { rows.forEach { row -> addJsonArray { row.forEach { add(it) } } } }
        }
        client.post(url, body)
    }

    private fun HttpUrl.Builder.unformatted() = this
        .addQueryParameter("valueRenderOption", "UNFORMATTED_VALUE")
        .addQueryParameter("dateTimeRenderOption", "SERIAL_NUMBER")

    /**
     * Writes several ranges at once.
     * @param userEntered true to let Sheets parse values as if typed (dates, numbers); false stores raw text.
     */
    suspend fun updateValues(spreadsheetId: String, data: List<ValueRange>, userEntered: Boolean = false) {
        val url = baseUrl.newBuilder().addPathSegment(spreadsheetId).addPathSegment("values:batchUpdate").build()
        val body = buildJsonObject {
            put("valueInputOption", if (userEntered) "USER_ENTERED" else "RAW")
            putJsonArray("data") {
                data.forEach { range ->
                    addJsonObject {
                        put("range", range.range)
                        putJsonArray("values") { range.values.forEach { row -> addJsonArray { row.forEach { add(it) } } } }
                    }
                }
            }
        }
        client.post(url, body)
    }
}
