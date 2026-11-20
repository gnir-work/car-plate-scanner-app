package com.lpscanner.app.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PlateRecordsTest {
    @Test fun `formats Israeli plates and keys ignore separators`() {
        assertEquals("23-456-78", PlateFormat.display("2345678"))
        assertEquals("123-45-678", PlateFormat.display(" 12345678 "))
        assertEquals("AB 123", PlateFormat.display("ab 123"))
        assertEquals(PlateFormat.key("23-456-78"), PlateFormat.key("23 456 78"))
    }

    private val columns = PlateColumns.from(PlateSheetSchema.NEW_PLATE_HEADERS)!!

    @Test fun `parse keeps sheet row numbers and skips blank rows`() {
        val records = PlateRows.parse(
            listOf(
                listOf("23-456-78", "Red", "Suzuki", "2026-11-20 10:00"),
                listOf(),
                listOf("96-543-21", "White"), // trailing cells trimmed by Sheets
            ),
            columns,
        )
        assertEquals(listOf(2, 4), records.map { it.row })
        assertEquals(PlateRecord(4, "96-543-21", "White", "", lastSeen = null), records[1]) // empty last_seen = null
    }

    @Test fun `rowOf finds a plate regardless of formatting`() {
        val rows = listOf(listOf("11-111-11"), listOf(), listOf("23-456-78"))
        assertEquals(4, PlateRows.rowOf("2345678", rows, columns))
        assertNull(PlateRows.rowOf("1234567", rows, columns))
    }

    @Test fun `new rows quote text but not dates, and follow the column order`() {
        assertEquals(
            listOf("'23-456-78", "'Red", "'Suzuki", "2026-11-20 10:00", "2026-11-20 10:00"),
            PlateRows.newRow(columns, PlateInput("2345678", " Red ", "Suzuki"), "2026-11-20 10:00", "2026-11-20 10:00"),
        )
        val shuffled = PlateColumns.from(listOf("car_company", "notes", "license_plate", "last_seen", "car_color"))!!
        assertEquals(
            listOf("'Suzuki", "", "'23-456-78", "", "'Red"),
            PlateRows.newRow(shuffled, PlateInput("2345678", "Red", "Suzuki"), lastSeen = null, firstSeen = "ignored: no column"),
        )
    }

    @Test fun `columns come from the header, any order and case`() {
        val found = PlateColumns.from(listOf("Notes", " LAST_SEEN ", "license_plate", "car_company", "car_color"))!!
        assertEquals(PlateColumns(plate = 2, color = 4, company = 3, lastSeen = 1, firstSeen = null), found)
        assertNull(PlateColumns.from(listOf("license_plate", "car_color", "car_company")))
        assertEquals(listOf("last_seen"), PlateSheetSchema.missingHeaders(listOf("license_plate", "car_color", "car_company")))
    }

    @Test fun `last seen serial numbers become date-times`() {
        assertEquals("2026-11-20 13:38", LastSeen.fromCell("46346.56806"))
        assertEquals("2026-11-20 00:00", LastSeen.fromCell("46346"))
        assertEquals("", LastSeen.fromCell(""))
        assertEquals("sometime", LastSeen.fromCell("sometime"))
        assertEquals(
            "2026-11-20 13:38",
            PlateRows.parse(listOf(listOf("76-238-32", "Black", "Suzuki", "46346.56806")), columns).single().lastSeen,
        )
    }

    @Test fun `how long ago`() {
        val now = LocalDateTime.of(2026, 11, 20, 12, 0)
        assertEquals(LastSeen.Ago.JustNow, LastSeen.ago("2026-11-20 12:00", now))
        assertEquals(LastSeen.Ago.Minutes(15), LastSeen.ago("2026-11-20 11:45", now))
        assertEquals(LastSeen.Ago.Hours(3), LastSeen.ago("2026-11-20 09:00", now))
        assertEquals(LastSeen.Ago.Yesterday, LastSeen.ago("2026-11-19 09:00", now))
        assertEquals(LastSeen.Ago.Days(5), LastSeen.ago("2026-11-15 12:00", now))
        assertEquals(LastSeen.Ago.On(LocalDate.of(2026, 1, 1)), LastSeen.ago("2026-01-01 08:00", now))
        assertEquals(LastSeen.Ago.Text("not a date"), LastSeen.ago("not a date", now))
    }

    @Test fun `moment for detail screens`() {
        val now = LocalDateTime.of(2026, 11, 20, 14, 32)
        assertEquals(LastSeen.Moment.Today(LocalTime.of(8, 5)), LastSeen.moment("2026-11-20 08:05", now))
        assertEquals(LastSeen.Moment.Yesterday(LocalTime.of(23, 59)), LastSeen.moment("2026-11-19 23:59", now))
        assertEquals(LastSeen.Moment.ThisYear(LocalDateTime.of(2026, 11, 17, 8, 15)), LastSeen.moment("2026-11-17 08:15", now))
        assertEquals(LastSeen.Moment.Older(LocalDate.of(2025, 3, 3)), LastSeen.moment("2025-03-03 10:00", now))
        assertEquals(LastSeen.Moment.Text("not a date"), LastSeen.moment("not a date", now))
    }
}
