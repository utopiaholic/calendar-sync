package com.calmerge.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Faux-glass surface: translucent fill + thin gradient border, no blur dep.
@Composable
fun Modifier.glassCard(
    cornerRadius: Dp = 16.dp,
    fillAlpha: Float = 0.12f,
    fillColor: Color = MaterialTheme.colorScheme.surface,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(fillColor.copy(alpha = fillAlpha))
        .border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
            shape = shape,
        )
}

// Lightweight surface for sticky headers.
@Composable
fun Modifier.glassSurface(alpha: Float = 0.85f): Modifier =
    this.background(MaterialTheme.colorScheme.surface.copy(alpha = alpha))
