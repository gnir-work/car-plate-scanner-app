package com.lpscanner.app.database

/** A scanned plate found in the database. */
data class PlateMatch(
    val record: PlateRecord,
    /** False when the best OCR reading wasn't in the database but a runner-up reading was. */
    val viaBestReading: Boolean,
)

object PlateMatcher {
    /**
     * Looks up OCR readings, best first. The best reading wins if it's in the database;
     * otherwise the first runner-up that is (a misread digit can still find the car).
     */
    fun match(snapshot: PlateSnapshot, readings: List<String>): PlateMatch? =
        readings.asSequence()
            .mapIndexedNotNull { index, plate -> snapshot.find(plate)?.let { PlateMatch(it, viaBestReading = index == 0) } }
            .firstOrNull()
}
