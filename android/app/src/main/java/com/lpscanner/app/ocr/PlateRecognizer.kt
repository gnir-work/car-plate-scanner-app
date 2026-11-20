package com.lpscanner.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.hypot

data class ScanResult(
    val candidates: List<PlateCandidate>,
    /** Everything ML Kit read in every pass, for debugging the heuristic. */
    val rawText: String,
    /** 0 when the plain full-image pass was enough (the fast path). */
    val enhancedPasses: Int,
    val durationMs: Long,
)

/**
 * Runs ML Kit on-device text recognition on a photo and extracts plate candidates.
 *
 * Fast path: one OCR pass over the whole photo. If it yields a number in the exact Israeli
 * layout, that's the answer.
 *
 * Slow path, only when the fast path didn't find one: the lines with the most promise are
 * cut out, straightened and enlarged ([LineWarper]), OCR'd again, and the passes vote.
 */
class PlateRecognizer(private val context: Context) : Closeable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(uri: Uri): ScanResult {
        val bitmap = withContext(Dispatchers.IO) { decode(uri) }
        return recognize(bitmap)
    }

    suspend fun recognize(bitmap: Bitmap): ScanResult {
        val start = SystemClock.elapsedRealtime()
        fun elapsed() = SystemClock.elapsedRealtime() - start

        // Fast path.
        val first = ocr(bitmap)
        val firstCandidates = PlateExtractor.extract(first.toOcrLines())
        if (firstCandidates.firstOrNull()?.exactFormat == true) {
            return ScanResult(firstCandidates, first.text, enhancedPasses = 0, durationMs = elapsed())
        }

        // Slow path.
        val passes = mutableListOf(firstCandidates)
        val rawText = StringBuilder(first.text)
        for (line in promisingLines(first)) {
            val corners = line.cornerPoints ?: continue
            for (variant in WARP_VARIANTS) {
                val warped = withContext(Dispatchers.Default) { LineWarper.warp(bitmap, corners, variant) } ?: continue
                val text = ocr(warped)
                passes += PlateExtractor.extract(text.toOcrLines())
                rawText.append("\n--- enhanced: \"${line.text}\" ---\n").append(text.text)
            }
        }
        return ScanResult(
            candidates = PlateExtractor.vote(passes),
            rawText = rawText.toString(),
            enhancedPasses = passes.size - 1,
            durationMs = elapsed(),
        )
    }

    override fun close() = recognizer.close()

    private suspend fun ocr(bitmap: Bitmap): Text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

    /** Lines with a few digits in them, tallest first: where a misread plate most likely is. */
    private fun promisingLines(text: Text): List<Text.Line> =
        text.textBlocks
            .flatMap { it.lines }
            .filter { line -> line.cornerPoints != null && line.text.count { it.isDigit() } >= MIN_DIGITS_TO_RETRY }
            .sortedByDescending { it.cornerPoints!!.let { c -> hypot((c[3].x - c[0].x).toFloat(), (c[3].y - c[0].y).toFloat()) } }
            .take(MAX_LINES_TO_RETRY)

    private fun Text.toOcrLines(): List<OcrLine> =
        textBlocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { OcrLine(line.text, it.left, it.top, it.right, it.bottom) }
        }

    private fun decode(uri: Uri): Bitmap {
        // ImageDecoder applies EXIF rotation. Software allocation so we can draw from it.
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longSide = maxOf(info.size.width, info.size.height)
            if (longSide > MAX_DECODE_SIDE) {
                val f = MAX_DECODE_SIDE.toFloat() / longSide
                decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
            }
        }
    }

    private companion object {
        const val MIN_DIGITS_TO_RETRY = 3
        const val MAX_LINES_TO_RETRY = 3
        /** Cap memory for huge camera photos while keeping enough detail for the zoomed pass. */
        const val MAX_DECODE_SIDE = 3000

        /** Settings that recovered "cut from the top.jpeg" in experiments. */
        val WARP_VARIANTS = listOf(
            LineWarper.Variant(padX = 0.05f, padY = 0.5f, textHeightPx = 128),
            LineWarper.Variant(padX = 0.15f, padY = 0.5f, textHeightPx = 128),
        )
    }
}
