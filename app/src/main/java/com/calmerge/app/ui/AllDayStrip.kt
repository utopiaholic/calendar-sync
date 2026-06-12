package com.calmerge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calmerge.app.data.db.MergedEvent
import com.calmerge.app.ui.theme.ConflictRed
import com.calmerge.app.ui.theme.SlateDark3
import java.time.LocalDate

@Composable
internal fun AllDayStrip(
    slots: List<TimeGridLayout.AllDaySlot>,
    dates: List<LocalDate>,
    eventById: Map<String, MergedEvent>,
    copiesById: Map<String, List<MergedEvent>>,
    conflictedIds: Set<String>,
    onEventClick: (MergedEvent, List<MergedEvent>) -> Unit,
) {
    val maxRow = (slots.maxOfOrNull { it.row } ?: 0) + 1
    val rowHeight = 22.dp

    Row(modifier = Modifier.fillMaxWidth().height(rowHeight * maxRow + 4.dp)) {
        Spacer(modifier = Modifier.width(GUTTER_WIDTH))

        dates.forEach { date ->
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Only show label rows for events that start on this date or span from before.
                val daySlots = slots.filter { slot ->
                    slot.startDate <= date && slot.endDateInclusive >= date
                }
                daySlots.forEach { slot ->
                    val event = eventById[slot.eventId] ?: return@forEach
                    val copies = copiesById[slot.eventId] ?: listOf(event)
                    val inConflict = copies.any { it.event.id in conflictedIds }
                    val color = Color(event.accountColor)
                    val eventShape = RoundedCornerShape(4.dp)
                    val eventBorder = if (inConflict) {
                        ConflictRed.copy(alpha = 0.9f)
                    } else {
                        color.copy(alpha = 0.5f)
                    }
                    val yOffset = rowHeight * slot.row + 2.dp
                    // Show the label pill on the event's start date OR on the first visible
                    // date when the event started before the visible range (fix #4).
                    val labelDate = maxOf(slot.startDate, dates.first())
                    if (date == labelDate) {
                        Box(
                            modifier = Modifier
                                .offset(y = yOffset)
                                .fillMaxWidth()
                                .height(rowHeight - 2.dp)
                                .padding(horizontal = 1.dp)
                                .background(SlateDark3.copy(alpha = 0.96f), eventShape)
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
                                event.event.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(
                                        start = 7.dp,
                                        end = 3.dp,
                                    )
                                    .align(Alignment.CenterStart),
                            )
                        }
                    } else {
                        // Continuation bar — still tappable so user can see the detail sheet.
                        Box(
                            modifier = Modifier
                                .offset(y = yOffset)
                                .fillMaxWidth()
                                .height(rowHeight - 2.dp)
                                .padding(horizontal = 1.dp)
                                .background(
                                    if (inConflict) ConflictRed.copy(alpha = 0.18f) else color.copy(alpha = 0.4f),
                                    RoundedCornerShape(3.dp),
                                )
                                .clickable { onEventClick(event, copies) },
                        )
                    }
                }
            }
        }
    }
}
