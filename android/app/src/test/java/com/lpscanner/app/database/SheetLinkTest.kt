package com.lpscanner.app.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SheetLinkTest {
    private val id = "1AbC_dEf-GhIjKlMnOpQrStUvWxYz0123456789"

    @Test
    fun `reads the id from a copied link`() {
        assertEquals(id, SheetLink.idFrom("https://docs.google.com/spreadsheets/d/$id/edit?usp=sharing"))
        assertEquals(id, SheetLink.idFrom("  https://docs.google.com/spreadsheets/d/$id/edit#gid=0\n"))
        assertEquals(id, SheetLink.idFrom("docs.google.com/spreadsheets/u/1/d/$id"))
    }

    @Test
    fun `accepts a bare id`() {
        assertEquals(id, SheetLink.idFrom(id))
    }

    @Test
    fun `rejects anything else`() {
        assertNull(SheetLink.idFrom("Garage cars"))
        assertNull(SheetLink.idFrom("https://docs.google.com/document/d/$id/edit"))
        assertNull(SheetLink.idFrom(""))
    }
}
