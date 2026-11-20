package com.lpscanner.app.ocr

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real ML Kit OCR + plate extraction on sample photos.
 *
 * Images come from the repo-level `resources/` folder, which is wired in as this
 * test APK's assets (see app/build.gradle.kts). Only simple-license.jpg is committed; other
 * photos show real cars and are kept locally, with their plates in the (also local)
 * `resources/expected-plates.properties`. Their tests are skipped when either is missing.
 * Needs a running emulator or device:
 *   ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class PlateRecognizerImageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun scan(assetName: String): ScanResult {
        val bitmap = instrumentation.context.assets.open(assetName).use { BitmapFactory.decodeStream(it) }
            ?: error("Couldn't decode $assetName")
        return runBlocking {
            PlateRecognizer(instrumentation.targetContext).use { it.recognize(bitmap) }
        }
    }

    /**
     * @param fastPath true if this photo must be solved by the plain first pass (keeps the
     *   happy case fast), false if it's expected to need the enhanced pass, null to not care.
     */
    private fun assertPlate(assetName: String, expected: String, fastPath: Boolean? = null) {
        assumeTrue("$assetName not in resources/, skipping", assetName in instrumentation.context.assets.list("").orEmpty())
        val result = scan(assetName)
        val details = "Candidates: ${result.candidates}\nEnhanced passes: ${result.enhancedPasses}, " +
            "${result.durationMs} ms\nRaw OCR text:\n${result.rawText}\n"
        assertEquals("Wrong plate for $assetName.\n$details", expected, result.candidates.firstOrNull()?.text)
        if (fastPath != null) {
            assertEquals("Fast path for $assetName.\n$details", fastPath, result.enhancedPasses == 0)
        }
    }

    /** Like [assertPlate], for a local-only photo whose plate is in expected-plates.properties. */
    private fun assertLocalPlate(assetName: String, fastPath: Boolean? = null) {
        val expected = localPlates.getProperty(assetName)
        assumeTrue("No plate for $assetName in resources/$LOCAL_PLATES, skipping", expected != null)
        assertPlate(assetName, expected!!, fastPath)
    }

    private val localPlates: Properties by lazy {
        Properties().apply {
            if (LOCAL_PLATES in instrumentation.context.assets.list("").orEmpty()) {
                instrumentation.context.assets.open(LOCAL_PLATES).use { load(it) }
            }
        }
    }

    @Test fun simpleLicense() = assertPlate("simple-license.jpg", "5938620", fastPath = true)

    @Test fun realWorldExample() = assertLocalPlate("real-world-example.jpeg", fastPath = true)

    /**
     * Shot from above, plate tilted ~40°. The first pass misreads most of the digits; only the
     * straightened, enlarged pass gets it. The frame also has a phone number in it.
     */
    @Test fun cutFromTheTop() = assertLocalPlate("cut from the top.jpeg", fastPath = false)

    /** Night, flash-lit, plate tilted down. */
    @Test fun nightWeirdAngle() = assertLocalPlate("night weird angle.jpeg", fastPath = true)

    /** Blurry photo of a monitor playing night video. The first pass splits the digits in the wrong place, so it goes enhanced. */
    @Test fun nightPhotoFromPcScreen() = assertLocalPlate("night photo from a pc screen video.jpeg", fastPath = false)

    private companion object {
        const val LOCAL_PLATES = "expected-plates.properties"
    }
}
