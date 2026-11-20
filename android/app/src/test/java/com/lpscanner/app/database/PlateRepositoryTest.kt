package com.lpscanner.app.database

import com.lpscanner.app.google.GoogleApiClient
import com.lpscanner.app.google.SheetsApi
import com.lpscanner.app.google.TokenProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Runs the repository against a tiny fake of the Sheets API that keeps the plates and options
 * tabs in memory as grids, so tests assert on the resulting sheet rather than on request details.
 */
class PlateRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: PlateRepository

    /** Row 1 of the plates tab: a sheet made before first_seen existed, unless a test changes it. */
    private val header = mutableListOf("license_plate", "car_color", "car_company", "last_seen")
    private val plates = mutableListOf<MutableList<String>>() // rows 2.. of the plates tab
    private val options = mutableListOf(mutableListOf("Red", "Kia"), mutableListOf("White", "")) // rows 2..
    private var formatRequests = 0

    private object StaticToken : TokenProvider {
        override suspend fun token() = "t"
        override suspend fun invalidate(token: String) = Unit
    }

    @Before fun setUp() {
        server.dispatcher = FakeSheets()
        server.start()
        repo = PlateRepository(SheetsApi(GoogleApiClient(StaticToken), baseUrl = server.url("/v4/spreadsheets")))
        runTest { repo.attach("S1") }
    }

    @After fun tearDown() = server.close()

    /** A plates row as {header name → value}, so assertions don't depend on column order. */
    private fun rowAt(index: Int): Map<String, String> =
        header.withIndex().associate { (column, name) -> name to plates[index].getOrElse(column) { "" } }

    @Test fun `add appends a formatted row with first_seen`() = runTest {
        val snapshot = repo.add(PlateInput("2345678", "Red", "Suzuki"), lastSeen = "2026-11-20 10:00", firstSeen = "2026-11-20 10:00")
        assertEquals(listOf("23-456-78", "Red", "Suzuki", "2026-11-20 10:00", "2026-11-20 10:00"), plates.single())
        val record = snapshot.find("23 456 78")!!
        assertEquals("23-456-78", record.plate)
        assertEquals("2026-11-20 10:00", record.firstSeen)
    }

    @Test fun `add refuses a duplicate`() = runTest {
        plates += mutableListOf("23-456-78", "Red", "Suzuki", "")
        try {
            repo.add(PlateInput("2345678", "Blue", "Kia"))
            fail("expected duplicate")
        } catch (e: DuplicatePlateException) {
            assertEquals("Red", e.existing.color)
        }
        assertEquals(1, plates.size)
    }

    @Test fun `update finds the row even if a teammate inserted rows above it`() = runTest {
        plates += mutableListOf("23-456-78", "Red", "Suzuki", "")
        val loaded = repo.refresh().records.single()
        plates.add(0, mutableListOf("11-111-11", "Black", "BMW", "")) // someone else's edit

        repo.update(loaded, PlateInput("23-456-78", "Blue", "Suzuki"))

        assertEquals(listOf("11-111-11", "Black", "BMW", ""), plates[0].take(4))
        assertEquals("Blue", plates[1][1])
    }

    @Test fun `update refuses renaming onto an existing plate`() = runTest {
        plates += mutableListOf("23-456-78", "Red", "Suzuki", "")
        plates += mutableListOf("96-543-21", "White", "Honda", "")
        val first = repo.refresh().records.first()
        try {
            repo.update(first, PlateInput("9654321", "Red", "Suzuki"))
            fail("expected duplicate")
        } catch (e: DuplicatePlateException) {
            assertEquals("96-543-21", e.existing.plate)
        }
    }

    @Test fun `delete removes the right row`() = runTest {
        plates += mutableListOf("11-111-11", "Black", "BMW", "")
        plates += mutableListOf("23-456-78", "Red", "Suzuki", "")
        val target = repo.refresh().records.last()
        plates.add(0, mutableListOf("22-222-22", "Gray", "Kia", ""))

        repo.delete(target)

        assertEquals(listOf("22-222-22", "11-111-11"), plates.map { it[0] })
    }

    @Test fun `delete of a plate someone already removed says so`() = runTest {
        plates += mutableListOf("23-456-78", "Red", "Suzuki", "")
        val target = repo.refresh().records.single()
        plates.clear()
        try {
            repo.delete(target)
            fail("expected PlateGoneException")
        } catch (_: PlateGoneException) {
        }
    }

    @Test fun `setLastSeen writes only that cell and returns the old value for undo`() = runTest {
        plates += mutableListOf("11-111-11", "Black", "BMW", "2026-01-01 08:00")
        plates += mutableListOf("23-456-78", "Red", "Suzuki", "2026-11-01 09:30")

        val previous = repo.setLastSeen("2345678", "2026-11-20 14:00")

        assertEquals("2026-11-01 09:30", previous)
        assertEquals(listOf("23-456-78", "Red", "Suzuki", "2026-11-20 14:00"), plates[1].take(4))
        assertEquals("2026-01-01 08:00", plates[0][3])

        repo.setLastSeen("2345678", previous)
        assertEquals("2026-11-01 09:30", plates[1][3])
    }

    @Test fun `addOption appends below the column and skips existing names`() = runTest {
        assertEquals("Red", repo.addOption(OptionKind.Color, "red"))
        assertEquals(2, options.size)

        assertEquals("Green", repo.addOption(OptionKind.Color, " Green "))
        assertEquals("Green", options[2][0])
        assertEquals(listOf("Red", "White", "Green"), repo.snapshot.value!!.colors)
        assertEquals(listOf("Kia"), repo.snapshot.value!!.companies)
    }

    @Test fun `Hebrew names are saved in English, custom ones as typed`() = runTest {
        repo.add(PlateInput("2345678", "אדומה", "טויוטה"), lastSeen = "2026-11-20 10:00")
        assertEquals(listOf("23-456-78", "Red", "Toyota", "2026-11-20 10:00"), plates.single().take(4))

        repo.update(repo.snapshot.value!!.find("2345678")!!, PlateInput("2345678", "ורוד מטאלי", "ג׳יפ"))
        assertEquals(listOf("23-456-78", "ורוד מטאלי", "Jeep"), plates.single().take(3))
    }

    @Test fun `adding a Hebrew option stores the English one`() = runTest {
        assertEquals("Red", repo.addOption(OptionKind.Color, "אדום")) // already in the list
        assertEquals(2, options.size)
        assertEquals("Mazda", repo.addOption(OptionKind.Company, "מאזדה"))
        assertEquals(listOf("Kia", "Mazda"), repo.snapshot.value!!.companies)
    }

    @Test fun `an old sheet opens without first_seen and gets the column on the next save`() = runTest {
        plates += mutableListOf("11-111-11", "Black", "BMW") // last_seen never set
        val loaded = repo.refresh().records.single()
        assertNull(loaded.lastSeen)
        assertNull(loaded.firstSeen)
        assertEquals(4, header.size) // reading changes nothing

        repo.setLastSeen("1111111", "2026-11-20 14:00")

        assertEquals("first_seen", header[4])
        assertEquals(1, formatRequests) // bold header + date format for the new column
        val after = repo.snapshot.value!!.records.single()
        assertEquals("2026-11-20 14:00", after.lastSeen)
        assertNull(after.firstSeen) // unknown for cars added before the column

        repo.add(PlateInput("2345678", "Red", "Suzuki"), firstSeen = "2026-11-20 15:00")
        assertEquals("2026-11-20 15:00", repo.snapshot.value!!.find("2345678")!!.firstSeen)
        assertEquals(1, formatRequests) // added only once
    }

    @Test fun `first_seen is added after the user's own columns`() = runTest {
        header += "notes"
        plates += mutableListOf("11-111-11", "Black", "BMW", "", "keep me")

        repo.add(PlateInput("2345678", "Red", "Suzuki"), lastSeen = "2026-11-20 10:00", firstSeen = "2026-11-20 10:00")

        assertEquals(listOf("license_plate", "car_color", "car_company", "last_seen", "notes", "first_seen"), header)
        assertEquals("keep me", rowAt(0)["notes"])
        assertEquals("", rowAt(1)["notes"])
        assertEquals("2026-11-20 10:00", rowAt(1)["first_seen"])
    }

    @Test fun `columns are found by header in any order`() = runTest {
        header.clear()
        header += listOf("last_seen", "notes", "Car_Company", "license_plate", "first_seen", " car_color ")
        plates += mutableListOf("2026-11-01 09:30", "blue door", "Suzuki", "23-456-78", "2026-01-05 09:00", "Red")

        val record = repo.refresh().records.single()
        assertEquals(PlateRecord(2, "23-456-78", "Red", "Suzuki", "2026-11-01 09:30", "2026-01-05 09:00"), record)

        repo.update(record, PlateInput("23-456-78", "Blue", "Suzuki"))
        repo.setLastSeen("2345678", "2026-11-20 14:00")
        repo.add(PlateInput("1111111", "Black", "BMW"), lastSeen = "2026-11-20 15:00", firstSeen = "2026-11-20 15:00")

        assertEquals(
            mapOf(
                "last_seen" to "2026-11-20 14:00", "notes" to "blue door", "Car_Company" to "Suzuki",
                "license_plate" to "23-456-78", "first_seen" to "2026-01-05 09:00", " car_color " to "Blue",
            ),
            rowAt(0),
        )
        assertEquals("11-111-11", rowAt(1)["license_plate"])
        assertEquals("Black", rowAt(1)[" car_color "])
        assertEquals("2026-11-20 15:00", rowAt(1)["first_seen"])
        assertEquals("", rowAt(1)["notes"])
    }

    @Test fun `a column moved by a teammate after loading is still written in the right place`() = runTest {
        plates += mutableListOf("23-456-78", "Red", "Suzuki", "2026-11-01 09:30")
        val loaded = repo.refresh().records.single()
        // Someone drags car_company to the front in Google Sheets.
        header.add(0, header.removeAt(2))
        plates[0].add(0, plates[0].removeAt(2))

        repo.update(loaded, PlateInput("23-456-78", "Red", "Kia"))

        assertEquals("Kia", rowAt(0)["car_company"])
        assertEquals("Red", rowAt(0)["car_color"])
        assertEquals("23-456-78", rowAt(0)["license_plate"])
    }

    @Test fun `a renamed required column is reported, not guessed`() = runTest {
        plates += mutableListOf("23-456-78", "Red", "Suzuki", "")
        header[1] = "colour"
        try {
            repo.refresh()
            fail("expected MissingColumnsException")
        } catch (e: MissingColumnsException) {
            assertEquals(listOf("car_color"), e.missing)
        }
    }

    /** Implements just the Sheets endpoints the repository uses, over the in-memory tabs. */
    private inner class FakeSheets : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.url.encodedPath.removePrefix("/v4/spreadsheets/S1")
            val body = request.body?.utf8().orEmpty()
            return when {
                path == "" -> ok("""{"spreadsheetId":"S1","properties":{"title":"T"},"sheets":[{"properties":{"sheetId":7,"title":"plates"}},{"properties":{"sheetId":8,"title":"options"}}]}""")
                path == "/values:batchGet" -> ok(
                    buildJsonObject {
                        put("valueRanges", JsonArray(request.url.queryParameterValues("ranges").map { values(read(it!!)) }))
                    }.toString(),
                )
                path.startsWith("/values/") && path.endsWith(":append") -> {
                    plates += cells(Json.parseToJsonElement(body).jsonObject["values"]!!.jsonArray[0].jsonArray).toMutableList()
                    ok("{}")
                }
                path.startsWith("/values/") -> ok(values(read(request.url.pathSegments.last())).toString())
                path == "/values:batchUpdate" -> {
                    Json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray.forEach { data ->
                        val range = data.jsonObject["range"]!!.jsonPrimitive.content
                        write(range, cells(data.jsonObject["values"]!!.jsonArray[0].jsonArray).single())
                    }
                    ok("{}")
                }
                path == ":batchUpdate" && "repeatCell" in body -> {
                    formatRequests++
                    ok("""{"replies":[{}]}""")
                }
                path == ":batchUpdate" -> {
                    val start = Regex("\"startIndex\":(\\d+)").find(body)!!.groupValues[1].toInt()
                    assertTrue("deletes from the plates tab", "\"sheetId\":7" in body)
                    plates.removeAt(start - 1) // index 1 = sheet row 2 = plates[0]
                    ok("""{"replies":[{}]}""")
                }
                else -> MockResponse.Builder().code(404).body("""{"error":{"message":"no route $path"}}""").build()
            }
        }

        private fun cells(array: JsonArray) = array.map { it.jsonPrimitive.content.removePrefix("'") }

        /** The ranges the repository reads: plates!A1:AZ, options!A2:B, options!X2:X. */
        private fun read(range: String): List<List<String>> {
            val (tab, cells) = range.split("!")
            if (tab == "plates") {
                assertEquals("plates!A1:AZ", range)
                return listOf(header.toList()) + plates
            }
            return when (cells) {
                "A2:B" -> options
                "A2:A" -> options.map { listOf(it[0]) }.dropLastWhile { it[0].isEmpty() }
                "B2:B" -> options.map { listOf(it.getOrElse(1) { "" }) }.dropLastWhile { it[0].isEmpty() }
                else -> error("unexpected range $range")
            }
        }

        /** Single-cell writes, e.g. plates!E1 (a header), plates!B3, options!A5. */
        private fun write(range: String, value: String) {
            val (tab, ref) = range.split("!")
            val (letter, number) = Regex("([A-Z]+)(\\d+)").matchEntire(ref)!!.destructured
            val column = letter.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
            val row = number.toInt()
            val target = when {
                tab == "plates" && row == 1 -> header
                tab == "plates" -> plates[row - 2]
                else -> {
                    while (options.size < row - 1) options += mutableListOf("", "")
                    options[row - 2]
                }
            }
            while (target.size <= column) target += ""
            target[column] = value
        }

        private fun values(rows: List<List<String>>) = buildJsonObject {
            put("values", buildJsonArray { rows.forEach { row -> add(JsonArray(row.map(::JsonPrimitive))) } })
        }

        private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()
    }
}
