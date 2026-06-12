package dev.albert.calmerge.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Faux-glass surface: translucent fill + thin gradient border, no blur dep.
fun Modifier.glassCard(
    cornerRadius: Dp = 16.dp,
    fillAlpha: Float = 0.12f,
    fillColor: Color = Color.White,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val border = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.18f),
            Color.White.copy(alpha = 0.04f),
            TealAccent.copy(alpha = 0.12f),
        ),
    )
    return this
        .clip(shape)
        .background(fillColor.copy(alpha = fillAlpha))
        .border(width = 1.dp, brush = border, shape = shape)
}

// Lightweight glass surface for sticky headers — darker fill, no border.
fun Modifier.glassSurface(alpha: Float = 0.85f): Modifier =
    this.background(SlateDark2.copy(alpha = alpha))
