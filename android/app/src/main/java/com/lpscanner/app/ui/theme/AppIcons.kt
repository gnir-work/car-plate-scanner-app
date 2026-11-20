package com.lpscanner.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The design's 24×24 line icons (2px round stroke), drawn from SVG path data. Tint with Icon(tint=…). */
object AppIcons {
    val Sheet by lazy { icon("sheet", rect(3f, 3f, 18f, 18f, 2f), "M3 9h18", "M3 15h18", "M9 3v18") }
    val Camera by lazy {
        icon(
            "camera",
            "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
            circle(12f, 13f, 3f),
        )
    }
    val Gallery by lazy {
        icon("gallery", rect(3f, 3f, 18f, 18f, 2f), circle(9f, 9f, 2f), "m21 15-3.1-3.1a2 2 0 0 0-2.8 0L6 21")
    }
    val Flash by lazy {
        icon("flash", "M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z")
    }
    val FlashOff by lazy {
        icon(
            "flash-off",
            "M10.513 4.856 13.12 2.17a.5.5 0 0 1 .86.46l-1.377 4.317",
            "M15.656 10H20a1 1 0 0 1 .78 1.63l-1.72 1.773",
            "M16.273 16.273 10.88 21.83a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14H4a1 1 0 0 1-.78-1.63l4.507-4.643",
            "m2 2 20 20",
        )
    }
    val Clock by lazy { icon("clock", circle(12f, 12f, 10f), "M12 6v6l4 2") }
    val Edit by lazy { icon("edit", "M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z") }
    val Back by lazy { icon("back", "m12 19-7-7 7-7", "M19 12H5", mirror = true) }
    val Close by lazy { icon("close", "M18 6 6 18", "m6 6 12 12") }
    val Plus by lazy { icon("plus", "M5 12h14", "M12 5v14") }
    val Link by lazy {
        icon(
            "link",
            "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
            "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
        )
    }
    val Search by lazy { icon("search", circle(11f, 11f, 8f), "m21 21-4.3-4.3") }
    val ChevronDown by lazy { icon("chevron-down", "m6 9 6 6 6-6") }
    val More by lazy { icon("more", circle(12f, 5f, 1f), circle(12f, 12f, 1f), circle(12f, 19f, 1f)) }
    val CheckCircle by lazy { icon("check-circle", circle(12f, 12f, 10f), "m9 12 2 2 4-4") }
    val Alert by lazy { icon("alert", circle(12f, 12f, 10f), "M12 8v4", "M12 16h.01") }
    val Download by lazy {
        icon("download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5", "M12 15V3")
    }
    val Trash by lazy {
        icon("trash", "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2")
    }
    val Users by lazy {
        icon(
            "users",
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            circle(9f, 7f, 4f),
            "M22 21v-2a4 4 0 0 0-3-3.87",
            "M16 3.13a4 4 0 0 1 0 7.75",
        )
    }
    val List by lazy { icon("list", "M3 5h.01", "M3 12h.01", "M3 19h.01", "M8 5h13", "M8 12h13", "M8 19h13", mirror = true) }
    val Scan by lazy {
        icon(
            "scan",
            "M3 7V5a2 2 0 0 1 2-2h2",
            "M17 3h2a2 2 0 0 1 2 2v2",
            "M21 17v2a2 2 0 0 1-2 2h-2",
            "M7 21H5a2 2 0 0 1-2-2v-2",
            "M7 12h10",
        )
    }

    /** @param mirror flip in right-to-left layouts (Hebrew), for icons that point or read in a direction. */
    private fun icon(name: String, vararg paths: String, mirror: Boolean = false): ImageVector {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror)
        paths.forEach { data ->
            builder.addPath(
                pathData = addPathNodes(data),
                fill = null,
                stroke = SolidColor(Color.Black), // Icon() replaces it with its tint
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}" +
            "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"
}
