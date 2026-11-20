package com.lpscanner.app.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import kotlin.math.hypot

/**
 * Cuts a (possibly tilted, perspective-skewed) OCR line out of a photo and warps it into a
 * level rectangle at a fixed text height, so a second OCR pass gets straight, big glyphs.
 */
object LineWarper {
    data class Variant(
        /** Extra margin along the text direction, as a fraction of the line width. */
        val padX: Float,
        /** Extra margin above and below, as a fraction of the line height. */
        val padY: Float,
        /** Line height in the output, in pixels. ML Kit reads best at ~100+ px glyphs. */
        val textHeightPx: Int,
    )

    /**
     * @param corners the OCR line's corner points, clockwise from top-left.
     * @return the warped crop, or null if the corners are degenerate.
     */
    fun warp(source: Bitmap, corners: Array<Point>, variant: Variant): Bitmap? {
        if (corners.size != 4) return null
        val (tl, tr, br, bl) = corners
        val lineW = (dist(tl, tr) + dist(bl, br)) / 2
        val lineH = (dist(tl, bl) + dist(tr, br)) / 2
        if (lineW < 4f || lineH < 4f) return null

        // Unit vectors along the text (u) and down across it (v), to grow the quad on its own axes.
        val ux = (tr.x - tl.x) / dist(tl, tr)
        val uy = (tr.y - tl.y) / dist(tl, tr)
        val vx = (bl.x - tl.x) / dist(tl, bl)
        val vy = (bl.y - tl.y) / dist(tl, bl)
        val ex = lineW * variant.padX
        val ey = lineH * variant.padY
        val src = floatArrayOf(
            tl.x - ux * ex - vx * ey, tl.y - uy * ex - vy * ey,
            tr.x + ux * ex - vx * ey, tr.y + uy * ex - vy * ey,
            br.x + ux * ex + vx * ey, br.y + uy * ex + vy * ey,
            bl.x - ux * ex + vx * ey, bl.y - uy * ex + vy * ey,
        )

        val scale = variant.textHeightPx / lineH
        val width = ((lineW + 2 * ex) * scale).toInt().coerceIn(1, MAX_SIDE)
        val height = ((lineH + 2 * ey) * scale).toInt().coerceIn(1, MAX_SIDE)
        val dst = floatArrayOf(0f, 0f, width.toFloat(), 0f, width.toFloat(), height.toFloat(), 0f, height.toFloat())
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return null

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE) // areas outside the photo
            drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    private const val MAX_SIDE = 4096

    private fun dist(a: Point, b: Point) = hypot((a.x - b.x).toFloat(), (a.y - b.y).toFloat())
}
