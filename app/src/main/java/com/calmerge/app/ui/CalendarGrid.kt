package com.calmerge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calmerge.app.data.db.MergedEvent
import com.calmerge.app.ui.theme.ConflictRed
import java.time.LocalDate

internal val HOUR_HEIGHT: Dp = 56.dp
internal val GUTTER_WIDTH: Dp = 40.dp
internal val TOTAL_HEIGHT: Dp = HOUR_HEIGHT * 24

@Composable
internal fun HourGutter() {
    // Extra top padding matches half a label height so 00:00 is fully visible.
    Box(modifier = Modifier.width(GUTTER_WIDTH).height(TOTAL_HEIGHT + 8.dp)) {
        for (h in 0..23) {
            val yDp = HOUR_HEIGHT * h + 8.dp   // shift down so 00:00 is not clipped
            Text(
                text = "%02d:00".format(h),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .offset(y = yDp - 7.dp)
                    .padding(end = 4.dp)
                    .align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
internal fun DayColumn(
    date: LocalDate,
    slots: List<TimeGridLayout.TimedSlot>,
    eventById: Map<String, MergedEvent>,
    copiesById: Map<String, List<MergedEvent>>,
    conflictedIds: Set<String>,
    isToday: Boolean,
    maxVisibleColumns: Int,
    onEventClick: (MergedEvent, List<MergedEvent>) -> Unit,
    onOverlapMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Top offset matches HourGutter's 8.dp shift so hour lines align with labels.
    BoxWithConstraints(modifier = modifier.height(TOTAL_HEIGHT + 8.dp).padding(top = 8.dp)) {
        val colWidthDp = maxWidth

        // Hour lines
        for (h in 0..23) {
            HorizontalDivider(
                modifier = Modifier.offset(y = HOUR_HEIGHT * h),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }

        // Today highlight
        if (isToday) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
            )
        }

        // Event blocks — use BoxWithConstraints width for correct x-offset.
        slots.forEach { slot ->
            val visibleColumnCount = minOf(slot.columnCount, maxVisibleColumns)
            if (slot.column >= visibleColumnCount) return@forEach

            val event = eventById[slot.eventId] ?: return@forEach
            val copies = copiesById[slot.eventId] ?: listOf(event)
            val inConflict = copies.any { it.event.id in conflictedIds }
            val color = Color(event.accountColor)
            val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
            val eventBackground = if (isLightTheme) {
                color.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
            }
            val eventTextColor = if (isLightTheme) {
                MaterialTheme.colorScheme.onSurface
            } else {
                Color.White
            }
            val eventShape = RoundedCornerShape(4.dp)
            val eventBorder = if (inConflict) {
                ConflictRed.copy(alpha = 0.9f)
            } else {
                color.copy(alpha = if (isLightTheme) 0.72f else 0.5f)
            }

            val slotWidthDp = colWidthDp / visibleColumnCount
            val xOffsetDp = slotWidthDp * slot.column
            val yOffsetDp = TOTAL_HEIGHT * slot.startFraction
            val heightDp = maxOf(14.dp, TOTAL_HEIGHT * (slot.endFraction - slot.startFraction))

            Box(
                modifier = Modifier
                    .width(slotWidthDp)
                    .height(heightDp)
                    .offset(x = xOffsetDp, y = yOffsetDp)
                    .padding(horizontal = 1.dp, vertical = 1.dp)
                    .background(eventBackground, eventShape)
                    .border(
                        width = if (inConflict) 2.dp else 1.dp,
                        color = eventBorder,
                        shape = eventShape,
                    )
                    .clickable { onEventClick(event, copies) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(color, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)),
                )
                Text(
                    text = event.event.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = eventTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = 7.dp,
                        top = 2.dp,
                        end = 3.dp,
                        bottom = 2.dp,
                    ),
                )
            }
        }

        denseOverlapSummaries(
            slots = slots,
            maxVisibleColumns = maxVisibleColumns,
        ).forEach { summary ->
            val yOffsetDp = TOTAL_HEIGHT * summary.startFraction
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = yOffsetDp + 4.dp)
                    .padding(end = 3.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f), RoundedCornerShape(999.dp))
                    .border(1.dp, ConflictRed.copy(alpha = 0.75f), RoundedCornerShape(999.dp))
                    .clickable(onClick = onOverlapMoreClick)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    "+${summary.hiddenCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ConflictRed,
                )
            }
        }
    }
}

private data class DenseOverlapSummary(
    val startFraction: Float,
    val hiddenCount: Int,
)

private fun denseOverlapSummaries(
    slots: List<TimeGridLayout.TimedSlot>,
    maxVisibleColumns: Int,
): List<DenseOverlapSummary> {
    if (maxVisibleColumns == Int.MAX_VALUE) return emptyList()
    if (slots.none { it.columnCount > maxVisibleColumns }) return emptyList()

    val sorted = slots.sortedBy { it.startFraction }
    val summaries = mutableListOf<DenseOverlapSummary>()
    var current = mutableListOf<TimeGridLayout.TimedSlot>()
    var currentEnd = 0f

    fun flush() {
        if (current.isEmpty()) return
        val hidden = current.count { it.column >= maxVisibleColumns }
        if (hidden > 0) {
            summaries += DenseOverlapSummary(
                startFraction = current.minOf { it.startFraction },
                hiddenCount = hidden,
            )
        }
        current = mutableListOf()
        currentEnd = 0f
    }

    sorted.forEach { slot ->
        if (current.isEmpty() || slot.startFraction < currentEnd) {
            current += slot
            currentEnd = maxOf(currentEnd, slot.endFraction)
        } else {
            flush()
            current += slot
            currentEnd = slot.endFraction
        }
    }
    flush()

    return summaries
}

internal fun MergedEvent.toInputEvent() = TimeGridLayout.InputEvent(
    id = event.id,
    startUtc = event.startUtc,
    endUtc = event.endUtc,
    isAllDay = event.isAllDay,
    startDate = event.startDate,
    endDate = event.endDate,
)
