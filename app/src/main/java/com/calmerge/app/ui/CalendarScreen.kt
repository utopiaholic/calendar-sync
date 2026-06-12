package com.calmerge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmerge.app.data.db.MergedEvent
import com.calmerge.app.ui.theme.ConflictRed
import com.calmerge.app.ui.theme.SlateDark3
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HOUR_HEIGHT: Dp = 56.dp
private val GUTTER_WIDTH: Dp = 40.dp
private val TOTAL_HEIGHT: Dp = HOUR_HEIGHT * 24
private const val MAX_WEEK_OVERLAP_COLUMNS = 2

enum class CalendarMode { DAY, WEEK }

/**
 * FR-20: Day and Week calendar views.
 * Shows a time grid with account-colored event blocks, conflict badges,
 * and an all-day strip above the grid.
 */
@Composable
fun CalendarScreen(
    viewModel: MainViewModel,
    onOpenConflicts: () -> Unit = {},
) {
    val mergedEvents by viewModel.mergedEvents.collectAsState()
    val conflictedIds by viewModel.conflictedEventIds.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val zone = ZoneId.systemDefault()

    val icsHostMap = remember(accounts) { icsHostsByAccountId(accounts) }

    var mode by remember { mutableStateOf(CalendarMode.WEEK) }
    var anchorDate by remember { mutableStateOf(LocalDate.now(zone)) }
    var selectedEvent by remember { mutableStateOf<EventDetailModel?>(null) }

    val dates by remember(mode, anchorDate) {
        derivedStateOf {
            when (mode) {
                CalendarMode.DAY -> listOf(anchorDate)
                CalendarMode.WEEK -> {
                    val monday = anchorDate.minusDays((anchorDate.dayOfWeek.value - 1).toLong())
                    (0..6).map { monday.plusDays(it.toLong()) }
                }
            }
        }
    }

    // Collapse dedupe groups (FR-13): one representative per logical event in the grid.
    // copiesById retains all copies for account badges.
    val collapsed by remember(mergedEvents) {
        derivedStateOf { EventUi.collapseDuplicates(mergedEvents) }
    }
    val inputEvents by remember(collapsed) {
        derivedStateOf { collapsed.map { (rep, _) -> rep.toInputEvent() } }
    }
    val eventById by remember(collapsed) {
        derivedStateOf { collapsed.associate { (rep, _) -> rep.event.id to rep } }
    }
    val copiesById by remember(collapsed) {
        derivedStateOf { collapsed.associate { (rep, copies) -> rep.event.id to copies } }
    }

    val layout by remember(inputEvents, dates, zone) {
        derivedStateOf { TimeGridLayout.layout(inputEvents, dates, zone) }
    }

    selectedEvent?.let {
        EventDetailSheet(event = it, onDismiss = { selectedEvent = null })
    }

    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    // Scroll to 8 am on first composition.
    LaunchedEffect(Unit) {
        val px = with(density) { (HOUR_HEIGHT * 8).roundToPx() }
        scrollState.scrollTo(px)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- Navigation header ----
        CalendarNavHeader(
            mode = mode,
            onModeChange = { mode = it },
            dates = dates,
            zone = zone,
            onPrev = {
                anchorDate = if (mode == CalendarMode.DAY) anchorDate.minusDays(1) else anchorDate.minusWeeks(1)
            },
            onNext = {
                anchorDate = if (mode == CalendarMode.DAY) anchorDate.plusDays(1) else anchorDate.plusWeeks(1)
            },
            onToday = { anchorDate = LocalDate.now(zone) },
        )

        HorizontalDivider()

        // ---- All-day strip ----
        if (layout.allDaySlots.isNotEmpty()) {
            AllDayStrip(
                slots = layout.allDaySlots,
                dates = dates,
                eventById = eventById,
                copiesById = copiesById,
                conflictedIds = conflictedIds,
                onEventClick = { event, copies -> selectedEvent = event.toDetailModel(copies, icsHostMap) },
            )
            HorizontalDivider()
        }

        // ---- Day-of-week header row (week view only) ----
        if (mode == CalendarMode.WEEK) {
            DayHeaderRow(dates = dates, today = LocalDate.now(zone))
            HorizontalDivider()
        }

        // ---- Scrollable time grid ----
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            // Hour gutter
            HourGutter()

            // Day columns
            dates.forEach { date ->
                val daySlots = layout.timedSlots.filter { it.date == date }
                DayColumn(
                    date = date,
                    slots = daySlots,
                    eventById = eventById,
                    copiesById = copiesById,
                    conflictedIds = conflictedIds,
                    isToday = date == LocalDate.now(zone),
                    maxVisibleColumns = if (mode == CalendarMode.WEEK) MAX_WEEK_OVERLAP_COLUMNS else Int.MAX_VALUE,
                    onEventClick = { event, copies -> selectedEvent = event.toDetailModel(copies, icsHostMap) },
                    onOverlapMoreClick = onOpenConflicts,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CalendarNavHeader(
    mode: CalendarMode,
    onModeChange: (CalendarMode) -> Unit,
    dates: List<LocalDate>,
    zone: ZoneId,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val label = when (mode) {
        CalendarMode.DAY -> {
            val d = dates.first()
            d.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))
        }
        CalendarMode.WEEK -> {
            val start = dates.first()
            val end = dates.last()
            if (start.month == end.month) {
                "${start.format(DateTimeFormatter.ofPattern("MMM d"))}–${end.dayOfMonth}, ${start.year}"
            } else {
                "${start.format(DateTimeFormatter.ofPattern("MMM d"))} – ${end.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        IconButton(onClick = onPrev) { Text("‹", fontSize = 20.sp) }
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onToday) { Text("Today") }
        IconButton(onClick = onNext) { Text("›", fontSize = 20.sp) }

        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = mode == CalendarMode.DAY,
                onClick = { onModeChange(CalendarMode.DAY) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Day") }
            SegmentedButton(
                selected = mode == CalendarMode.WEEK,
                onClick = { onModeChange(CalendarMode.WEEK) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Week") }
        }
    }
}

@Composable
private fun DayHeaderRow(dates: List<LocalDate>, today: LocalDate) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(GUTTER_WIDTH))
        dates.forEach { date ->
            val isToday = date == today
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            ) {
                Text(
                    text = date.dayOfWeek.name.take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun HourGutter() {
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
private fun DayColumn(
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
            val eventShape = RoundedCornerShape(4.dp)
            val eventBorder = if (inConflict) {
                ConflictRed.copy(alpha = 0.9f)
            } else {
                color.copy(alpha = 0.5f)
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
                    text = event.event.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
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
                    .background(SlateDark3.copy(alpha = 0.96f), RoundedCornerShape(999.dp))
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

@Composable
private fun AllDayStrip(
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

// ---- Helpers ----

private fun MergedEvent.toInputEvent() = TimeGridLayout.InputEvent(
    id = event.id,
    startUtc = event.startUtc,
    endUtc = event.endUtc,
    isAllDay = event.isAllDay,
    startDate = event.startDate,
    endDate = event.endDate,
)
