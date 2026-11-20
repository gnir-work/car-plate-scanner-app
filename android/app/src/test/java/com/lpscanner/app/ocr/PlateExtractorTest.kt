package com.lpscanner.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateExtractorTest {

    /** A line of text whose box is roughly as wide as the text would be at this height. */
    private fun line(text: String, height: Int, x: Int = 0, y: Int = 0, width: Int = text.length * height / 2) =
        OcrLine(text, left = x, top = y, right = x + width, bottom = y + height)

    private fun top(vararg lines: OcrLine) = PlateExtractor.extract(lines.toList()).firstOrNull()?.text

    @Test fun `extracts 7 digit plate`() {
        assertEquals("1234567", top(line("12-345-67", 80)))
    }

    @Test fun `extracts 8 digit plate`() {
        assertEquals("12345678", top(line("123-45-678", 80)))
    }

    @Test fun `strips IL country code`() {
        assertEquals("12345678", top(line("IL 123-45-678", 80)))
        assertEquals("1234567", top(line("IL12-345-67", 80)))
    }

    @Test fun `accepts plate with separators missing`() {
        assertEquals("12345678", top(line("12345678", 80)))
    }

    @Test fun `prefers the largest text`() {
        assertEquals(
            "1234567",
            top(
                line("Call 555-1234", 20, y = 300),
                line("12-345-67", 90, y = 0),
            ),
        )
    }

    @Test fun `prefers Israeli grouping over other groupings of the same size`() {
        assertEquals(
            "7654321",
            top(
                line("123 4567", 80, y = 0),
                line("76-543-21", 80, y = 200),
            ),
        )
    }

    @Test fun `fixes digit-shaped letter misreads`() {
        assertEquals("1230567", top(line("12-3O5-67", 80)))
    }

    /** resources/real-world-example.jpeg: the IL strip was read as a "B" glued to the number. */
    @Test fun `ignores stray letter glued to the start of the plate`() {
        assertEquals("9654321", top(line("B96:543-21", 80)))
    }

    @Test fun `still fixes a misread digit at the edge`() {
        assertEquals("8654321", top(line("B6-543-21", 80)))
    }

    @Test fun `does not turn letter-heavy junk into a number`() {
        // Seen in an enhanced pass over a plate frame's dealer sticker.
        assertTrue(PlateExtractor.extract(listOf(line("O 0O0 003", 80))).isEmpty())
    }

    @Test fun `flags exact Israeli format`() {
        val exact = PlateExtractor.extract(listOf(line("12-345-67", 80))).single()
        val ungrouped = PlateExtractor.extract(listOf(line("1234567", 80))).single()
        assertTrue(exact.exactFormat)
        assertTrue(!ungrouped.exactFormat)
    }

    @Test fun `vote prefers the number read in more passes`() {
        fun c(text: String, score: Double) = PlateCandidate(text, text, score)
        val result = PlateExtractor.vote(
            listOf(
                listOf(c("1111111", 0.95)), // one confident misread
                listOf(c("3456789", 0.7)),
                listOf(c("3456789", 0.8), c("1111111", 0.5)),
                listOf(c("3456789", 0.6)),
            ),
        )
        assertEquals("3456789", result.first().text)
        assertEquals(3, result.first().votes)
        assertEquals(0.8, result.first().score, 1e-9)
    }

    @Test fun `joins a row that OCR split into separate lines`() {
        assertEquals(
            "1234567",
            top(
                line("12-345", 80, x = 0, width = 200),
                line("-67", 80, x = 220, width = 60),
            ),
        )
    }

    @Test fun `rejects non-Israeli text`() {
        val result = PlateExtractor.extract(
            listOf(
                line("HELLO WORLD", 50, y = 0),
                line("AB-123-CD", 50, y = 100),
                line("2019", 50, y = 200),
                line("054-555-1234", 50, y = 300),
            ),
        )
        assertTrue(result.toString(), result.isEmpty())
    }

    /**
     * Modeled on resources/simple-license.jpg: OCR may split the row
     * at a dash and read the small triangle emblem as a stray letter on its own line.
     */
    @Test fun `finds the plate in a realistic layout`() {
        assertEquals(
            "2345678",
            top(
                line("23-", 110, x = 80, y = 100, width = 140),
                line("456-78", 110, x = 230, y = 100, width = 300),
                line("A", 25, x = 190, y = 160, width = 25),
            ),
        )
    }
}
