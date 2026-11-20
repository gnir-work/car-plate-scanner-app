package com.lpscanner.app.database

import com.lpscanner.app.database.PlateSheetSetup.Inspection
import com.lpscanner.app.google.GoogleApiClient
import com.lpscanner.app.google.SheetsApi
import com.lpscanner.app.google.TokenProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlateSheetSetupTest {
    private val server = MockWebServer()
    private lateinit var setup: PlateSheetSetup

    private object StaticToken : TokenProvider {
        override suspend fun token() = "t"
        override suspend fun invalidate(token: String) = Unit
    }

    @Before fun setUp() {
        server.start()
        setup = PlateSheetSetup(SheetsApi(GoogleApiClient(StaticToken), baseUrl = server.url("/v4/spreadsheets")))
    }

    @After fun tearDown() = server.close()

    private fun respond(body: String) = server.enqueue(MockResponse.Builder().code(200).body(body).build())

    private fun spreadsheet(vararg tabs: Pair<String, Int>) =
        """{"spreadsheetId":"S1","properties":{"title":"My plates"},"sheets":[""" +
            tabs.joinToString(",") { (t, id) -> """{"properties":{"sheetId":$id,"title":"$t"}}""" } + "]}"

    private fun RecordedRequest.json() = Json.parseToJsonElement(body!!.utf8()).jsonObject

    @Test fun `create makes both tabs, formats them and writes headers and options`() = runTest {
        respond(spreadsheet("plates" to 11, "options" to 22)) // create
        respond("""{"replies":[]}""") // batchUpdate formatting
        respond("{}") // values:batchUpdate

        assertEquals("S1", setup.create("My plates"))

        val create = server.takeRequest()
        assertEquals("/v4/spreadsheets", create.url.encodedPath)
        val tabs = create.json()["sheets"]!!.jsonArray.map { it.jsonObject["properties"]!!.jsonObject["title"]!!.jsonPrimitive.content }
        assertEquals(listOf("plates", "options"), tabs)

        val format = server.takeRequest()
        assertEquals("/v4/spreadsheets/S1:batchUpdate", format.url.encodedPath)
        val formatBody = format.body!!.utf8()
        assertTrue("dropdowns use the options tab", "=options!A2:A" in formatBody && "=options!B2:B" in formatBody)
        assertTrue("formats the plates tab by its id", "\"sheetId\":11" in formatBody)

        val values = server.takeRequest()
        assertEquals("/v4/spreadsheets/S1/values:batchUpdate", values.url.encodedPath)
        val ranges = values.json()["data"]!!.jsonArray.map { it.jsonObject["range"]!!.jsonPrimitive.content }
        assertEquals("plates!A1:E1", ranges[0]) // with first_seen
        assertTrue(ranges[1].startsWith("options!A1:B"))
    }

    @Test fun `inspect accepts a sheet with our header`() = runTest {
        respond(spreadsheet("plates" to 1, "options" to 2))
        respond("""{"values":[["license_plate","car_color","car_company","last_seen"]]}""")

        assertEquals(Inspection.Ready("My plates"), setup.inspect("S1"))
        assertEquals(2, server.requestCount)
    }

    @Test fun `inspect offers to set up a sheet without our tabs`() = runTest {
        respond(spreadsheet("Sheet1" to 0))

        assertEquals(
            Inspection.NeedsSetup("My plates", addPlatesTab = true, addOptionsTab = true, writePlatesHeader = true),
            setup.inspect("S1"),
        )
    }

    @Test fun `inspect offers headers for an empty plates tab`() = runTest {
        respond(spreadsheet("plates" to 1, "options" to 2))
        respond("""{"range":"plates!A1:Z1"}""") // no values

        assertEquals(
            Inspection.NeedsSetup("My plates", addPlatesTab = false, addOptionsTab = false, writePlatesHeader = true),
            setup.inspect("S1"),
        )
    }

    @Test fun `inspect refuses a plates tab with other data`() = runTest {
        respond(spreadsheet("plates" to 1, "options" to 2))
        respond("""{"values":[["Name","Phone"]]}""")

        assertTrue(setup.inspect("S1") is Inspection.Incompatible)
    }

    @Test fun `setUp adds only what is missing`() = runTest {
        respond("""{"replies":[]}""") // addSheet options
        respond(spreadsheet("plates" to 1, "options" to 2)) // re-read ids
        respond("""{"replies":[]}""") // formatting
        respond("{}") // values

        setup.setUp("S1", Inspection.NeedsSetup("My plates", addPlatesTab = false, addOptionsTab = true, writePlatesHeader = false))

        val addTabs = server.takeRequest().body!!.utf8()
        assertTrue("\"title\":\"options\"" in addTabs)
        assertTrue("must not touch plates", "\"title\":\"plates\"" !in addTabs)
        server.takeRequest()
        val formatting = server.takeRequest().body!!.utf8()
        assertTrue("no plates dropdowns when plates already set up", "setDataValidation" !in formatting)
        val ranges = server.takeRequest().json()["data"]!!.jsonArray.map { it.jsonObject["range"]!!.jsonPrimitive.content }
        assertEquals(1, ranges.size)
        assertTrue(ranges[0].startsWith("options!"))
    }

    @Test fun `numeric cells decode as strings`() = runTest {
        respond(spreadsheet("plates" to 1, "options" to 2))
        respond("""{"values":[["license_plate","car_color","car_company","last_seen"],[2345678,"Red","Kia",46346.56806]]}""")
        // Only the header row matters to inspect(); the point is that numbers don't break decoding.
        assertEquals(Inspection.Ready("My plates"), setup.inspect("S1"))
        val read = server.takeRequest().let { server.takeRequest() }
        assertEquals("UNFORMATTED_VALUE", read.url.queryParameter("valueRenderOption"))
        assertEquals("SERIAL_NUMBER", read.url.queryParameter("dateTimeRenderOption"))
    }

    @Test fun `option rows pad the shorter column`() {
        val rows = PlateSheetSchema.optionRows(colors = listOf("Red"), companies = listOf("Kia", "BMW"))
        assertEquals(listOf(listOf("colors", "companies"), listOf("Red", "Kia"), listOf("", "BMW")), rows)
    }

    @Test fun `header check ignores case, spaces, order and extra columns`() {
        assertTrue(PlateSheetSchema.missingHeaders(listOf("notes", "last_seen", " License_Plate", "CAR_COMPANY", "car_color")).isEmpty())
        assertEquals(listOf("car_company", "last_seen"), PlateSheetSchema.missingHeaders(listOf("license_plate", "car_color")))
    }
}
