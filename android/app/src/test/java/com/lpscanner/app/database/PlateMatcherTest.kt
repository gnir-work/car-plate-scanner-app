package com.lpscanner.app.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlateMatcherTest {
    private val red = PlateRecord(2, "23-456-78", "Red", "Suzuki", "")
    private val white = PlateRecord(3, "96-543-21", "White", "Honda", "")
    private val snapshot = PlateSnapshot(listOf(red, white), emptyList(), emptyList())

    @Test fun `best reading wins`() =
        assertEquals(PlateMatch(red, viaBestReading = true), PlateMatcher.match(snapshot, listOf("2345678", "9654321")))

    @Test fun `falls back to a runner-up reading`() =
        assertEquals(PlateMatch(white, viaBestReading = false), PlateMatcher.match(snapshot, listOf("8654321", "9654321")))

    @Test fun `no match`() = assertNull(PlateMatcher.match(snapshot, listOf("1111111")))
}
