package com.lpscanner.app.ocr

import kotlin.math.abs

/** One line of OCR output with its bounding box in image pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top
    val centerY: Double get() = (top + bottom) / 2.0
}

data class PlateCandidate(
    /** Normalized plate number, digits only, e.g. "1234567". */
    val text: String,
    /** The text as OCR read it, e.g. "12-345-67". */
    val rawText: String,
    /** 0..1, higher is more plate-like. Relative ranking only, not a probability. */
    val score: Double,
    /** Digit grouping exactly matches an Israeli plate (12-345-67 / 123-45-678). */
    val exactFormat: Boolean = false,
    /** How many OCR passes produced this number (see [PlateExtractor.vote]). */
    val votes: Int = 1,
)

/**
 * Picks an Israeli license plate number out of general OCR output.
 *
 * Israeli private plates are digits only:
 *  - 7 digits printed NN-NNN-NN (older plates)
 *  - 8 digits printed NNN-NN-NNN (issued since 2017)
 * usually with a blue "IL" strip on the left.
 *
 * Candidates are 1..4 consecutive words of an OCR line, or of a row of lines that OCR split
 * apart, with separators removed. They're ranked by text size (the plate is usually the
 * biggest text on a car) and by how well the digit grouping matches the Israeli layout.
 */
object PlateExtractor {
    private val SEPARATORS = Regex("[\\s\\-.·•:_|]+")
    private val WHITESPACE = Regex("\\s+")
    private val PLATE = Regex("^\\d{7,8}$")
    private val ISRAELI_GROUPINGS = setOf(listOf(2, 3, 2), listOf(3, 2, 3))
    private val LEADING_LETTERS = Regex("^[A-Za-z]+")
    private val TRAILING_LETTERS = Regex("[A-Za-z]+$")
    private const val MAX_WORDS = 4

    /** Letters OCR commonly returns in place of digits. */
    private val LETTER_TO_DIGIT = mapOf(
        'O' to '0', 'Q' to '0', 'D' to '0', 'I' to '1', 'Z' to '2', 'S' to '5', 'B' to '8', 'G' to '6',
    )

    fun extract(lines: List<OcrLine>): List<PlateCandidate> {
        val maxHeight = lines.maxOfOrNull { it.height }?.coerceAtLeast(1) ?: return emptyList()
        val best = LinkedHashMap<String, PlateCandidate>()

        for (line in lines + mergeRows(lines)) {
            val words = line.text.trim().split(WHITESPACE).filter { it.isNotEmpty() }
            val relativeHeight = line.height.toDouble() / maxHeight
            for (start in words.indices) {
                for (end in start until minOf(words.size, start + MAX_WORDS)) {
                    val window = words.subList(start, end + 1).joinToString(" ")
                    for (raw in edgeTrimmedVariants(window)) {
                        val candidate = score(raw, relativeHeight) ?: continue
                        val existing = best[candidate.text]
                        if (existing == null || candidate.score > existing.score) {
                            best[candidate.text] = candidate
                        }
                    }
                }
            }
        }
        return best.values.sortedByDescending { it.score }
    }

    /**
     * Combines candidates from several OCR passes over the same plate. A number read in more
     * passes wins; ties go to the higher single-pass score. One-off misreads lose to the
     * reading that keeps coming back.
     */
    fun vote(passes: List<List<PlateCandidate>>): List<PlateCandidate> =
        passes
            .flatMap { pass -> pass.distinctBy { it.text } }
            .groupBy { it.text }
            .map { (_, hits) -> hits.maxBy { it.score }.copy(votes = hits.size) }
            .sortedWith(compareByDescending<PlateCandidate> { it.votes }.thenByDescending { it.score })

    internal fun normalize(raw: String): String {
        val compact = stripCountryCode(raw.uppercase()).replace(SEPARATORS, "")
        val digits = compact.count { it.isDigit() }
        val letters = compact.filter { it.isLetter() }
        // A mostly-numeric string with a single stray, digit-shaped letter is almost
        // certainly a misread ("12-3O5-67" -> "1230567"). Kept strict so junk like
        // "O 0O0 003" doesn't turn into a number.
        val looksLikeMisread = digits >= 6 && letters.length == 1 && letters[0] in LETTER_TO_DIGIT
        return if (looksLikeMisread) compact.map { LETTER_TO_DIGIT[it] ?: it }.joinToString("") else compact
    }

    /**
     * OCR often splits one row of text into several lines (e.g. at a dash, or around a logo),
     * so join lines of similar height that sit side by side, left to right.
     */
    internal fun mergeRows(lines: List<OcrLine>): List<OcrLine> {
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in lines.filter { it.height > 0 }.sortedBy { it.left }) {
            val row = rows.firstOrNull { sameRow(it.last(), line) }
            if (row != null) row += line else rows += mutableListOf(line)
        }
        return rows.filter { it.size > 1 }.map { row ->
            OcrLine(
                text = row.joinToString(" ") { it.text },
                left = row.minOf { it.left },
                top = row.minOf { it.top },
                right = row.maxOf { it.right },
                bottom = row.maxOf { it.bottom },
            )
        }
    }

    /** [b] is assumed to start at or to the right of [a]. */
    private fun sameRow(a: OcrLine, b: OcrLine): Boolean {
        val minHeight = minOf(a.height, b.height).toDouble()
        val maxHeight = maxOf(a.height, b.height)
        val gap = b.left - a.right
        return maxHeight <= 1.5 * minHeight &&
            abs(a.centerY - b.centerY) < 0.5 * minHeight &&
            gap < 2 * minHeight
    }

    /**
     * Letters glued to either end of a number are usually not part of it: the blue "IL"
     * strip or the plate frame read as "B", "I", "L"... ("B96:543-21"). Try the text with
     * and without them; the Israeli-grouping score decides which reading wins, so a real
     * misread at the edge ("B7-787-12" -> 87-787-12) can still win.
     */
    private fun edgeTrimmedVariants(raw: String): Set<String> {
        val noLeading = raw.replace(LEADING_LETTERS, "")
        val noTrailing = raw.replace(TRAILING_LETTERS, "")
        val neither = noLeading.replace(TRAILING_LETTERS, "")
        return linkedSetOf(raw, noLeading, noTrailing, neither).filterTo(LinkedHashSet()) { it.isNotBlank() }
    }

    private fun stripCountryCode(upper: String): String {
        val trimmed = upper.trim()
        // The blue "IL" strip is often read as part of the number ("IL 12-345-67", "IL12-345-67").
        return if (trimmed.startsWith("IL") && trimmed.drop(2).trimStart().firstOrNull()?.isDigit() == true) {
            trimmed.drop(2)
        } else {
            trimmed
        }
    }

    private fun score(raw: String, relativeHeight: Double): PlateCandidate? {
        val text = normalize(raw)
        if (!PLATE.matches(text)) return null

        val groups = stripCountryCode(raw.uppercase()).split(SEPARATORS).filter { it.isNotEmpty() }.map { it.length }
        val exactFormat = groups in ISRAELI_GROUPINGS
        val formatScore = when {
            exactFormat -> 1.0 // 12-345-67 / 123-45-678
            groups.size == 1 -> 0.7 // separators lost by OCR: 1234567
            else -> 0.4 // right length, unusual grouping
        }
        val score = 0.6 * relativeHeight + 0.4 * formatScore
        return PlateCandidate(text = text, rawText = raw, score = score, exactFormat = exactFormat)
    }
}
