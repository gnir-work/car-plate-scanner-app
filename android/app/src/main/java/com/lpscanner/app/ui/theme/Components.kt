package com.lpscanner.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PlateSize(val font: TextUnit, val border: Dp, val padX: Dp, val padY: Dp) {
    Small(13.sp, 1.5.dp, 8.dp, 2.dp),
    Row(15.sp, 1.5.dp, 0.dp, 3.dp),
    Large(32.sp, 2.dp, 20.dp, 8.dp),
}

/**
 * A plate drawn like the real thing: black on yellow, monospaced. [width] fixes the width so list
 * columns line up (the Row size); null wraps the text.
 */
@Composable
fun PlateChip(plate: String, modifier: Modifier = Modifier, size: PlateSize = PlateSize.Row, width: Dp? = 118.dp) {
    val shape = RoundedCornerShape(if (size == PlateSize.Large) 6.dp else 4.dp)
    Box(
        modifier
            .then(if (width != null && size == PlateSize.Row) Modifier.width(width) else Modifier)
            .background(AppColors.PlateYellow, shape)
            .border(size.border, Color(0xFF1A1A1A), shape)
            .padding(horizontal = size.padX, vertical = size.padY),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            plate,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = size.font,
            letterSpacing = if (size == PlateSize.Large) 2.sp else 0.5.sp,
            color = AppColors.PlateInk,
            style = TextStyle(textDirection = TextDirection.Ltr), // plates read left to right in Hebrew too
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Round swatch for a car color name. Unknown names get a rainbow ring so they read as "other". */
@Composable
fun ColorDot(name: String, size: Dp, modifier: Modifier = Modifier) {
    val fill = carColor(name)
    val base = modifier.size(size).border(1.dp, Color(0xFF8592A6), CircleShape)
    Box(
        if (fill != null) {
            base.background(fill, CircleShape)
        } else {
            base.background(
                Brush.sweepGradient(listOf(Color(0xFFC8322B), Color(0xFFF9D43A), Color(0xFF3E7D4F), Color(0xFF2F5FB3), Color(0xFFC8322B))),
                CircleShape,
            )
        },
    )
}

/** Swatch color for the color names people use (the sheet's default list plus common spellings). */
fun carColor(name: String): Color? = when (name.trim().lowercase()) {
    "white" -> Color(0xFFFFFFFF)
    "black" -> Color(0xFF1A1A1A)
    "silver" -> Color(0xFFC7CCD3)
    "gray", "grey" -> Color(0xFF7A808A)
    "red" -> Color(0xFFC8322B)
    "blue" -> Color(0xFF2F5FB3)
    "light blue" -> Color(0xFF8DB4E2)
    "green" -> Color(0xFF3E7D4F)
    "yellow" -> Color(0xFFF2C230)
    "orange" -> Color(0xFFE47B2A)
    "brown" -> Color(0xFF7A4E2D)
    "beige" -> Color(0xFFD9C6A0)
    "gold" -> Color(0xFFC9A43B)
    "purple" -> Color(0xFF6E4A9E)
    "bordeaux", "maroon", "burgundy" -> Color(0xFF6D1F2B)
    else -> null
}
