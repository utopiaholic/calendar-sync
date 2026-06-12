package com.calmerge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calmerge.app.ui.theme.ConflictRed
import com.calmerge.app.ui.theme.SlateDark

@Composable
fun ConflictBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val badgeSize = if (compact) 20.dp else null

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (compact && badgeSize != null) {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(ConflictRed)
                    .border(1.dp, SlateDark.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "!",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(ConflictRed)
                    .border(1.dp, SlateDark.copy(alpha = 0.85f), MaterialTheme.shapes.small)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    "!",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}
