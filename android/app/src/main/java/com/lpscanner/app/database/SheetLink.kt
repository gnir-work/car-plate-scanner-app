package com.lpscanner.app.database

/** Reads a spreadsheet ID from what the user pasted: a Google Sheets link or a bare ID. */
object SheetLink {
    private val inUrl = Regex("""/spreadsheets/(?:u/\d+/)?d/([A-Za-z0-9_-]{20,})""")
    private val bare = Regex("""[A-Za-z0-9_-]{25,}""")

    fun idFrom(text: String): String? {
        val trimmed = text.trim()
        inUrl.find(trimmed)?.let { return it.groupValues[1] }
        return trimmed.takeIf { bare.matches(it) }
    }
}
