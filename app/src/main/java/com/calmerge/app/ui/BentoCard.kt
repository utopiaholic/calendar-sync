package com.calmerge.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calmerge.app.ui.theme.glassCard

@Composable
internal fun BentoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    minHeight: Dp = 120.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .glassCard(cornerRadius = 16.dp, fillAlpha = 0.08f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp)
            .heightIn(min = minHeight),
    ) {
        Column { content() }
    }
}
