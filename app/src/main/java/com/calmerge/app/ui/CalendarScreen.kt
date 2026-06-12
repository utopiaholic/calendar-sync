package com.calmerge.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
