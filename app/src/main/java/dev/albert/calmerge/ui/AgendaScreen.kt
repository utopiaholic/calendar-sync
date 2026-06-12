package dev.albert.calmerge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.albert.calmerge.data.db.MergedEvent
import java.time.ZoneId

/**
 * FR-19: chronological unified agenda, color-coded per feed, red badge on
 * conflicting events. Defaults to the current week for easy scanning.
 */
@Composable
fun AgendaScreen(viewModel: MainViewModel) {
    val events by viewModel.mergedEvents.collectAsState()
    val conflictedIds by viewModel.conflictedEventIds.collectAsState()
    val filter by viewModel.agendaFilter.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val zone = ZoneId.systemDefault()
    var selectedEvent by remember { mutableStateOf<EventDetailModel?>(null) }

    val icsHostById = remember(accounts) {
        accounts.associate { acc ->
            acc.id to acc.icsUrl?.let { runCatching { java.net.URI(it).host }.getOrNull() }
        }
    }

    val collapsed = EventUi.collapseDuplicates(events)
    val filtered = when (filter) {
        AgendaFilter.WEEK -> {
            val (weekStart, weekEnd) = EventUi.currentWeekBounds(zone)
            // Use overlap test so events spanning the Monday boundary remain visible.
            collapsed.filter { (rep, copies) ->
                copies.any { EventUi.overlapsWeek(it, weekStart, weekEnd, zone) } ||
                    EventUi.overlapsWeek(rep, weekStart, weekEnd, zone)
            }
        }
        AgendaFilter.ALL -> collapsed
    }.sortedBy { (rep, _) -> EventUi.sortKeyMs(rep, zone) }

    Column(modifier = Modifier.fillMaxSize()) {
        selectedEvent?.let { event ->
            EventDetailSheet(event = event, onDismiss = { selectedEvent = null })
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = filter == AgendaFilter.WEEK,
                onClick = { viewModel.agendaFilter.value = AgendaFilter.WEEK },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("This week") }
            SegmentedButton(
                selected = filter == AgendaFilter.ALL,
                onClick = { viewModel.agendaFilter.value = AgendaFilter.ALL },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("All (±window)") }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (filter == AgendaFilter.WEEK) "No events this week" else "No events synced",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        // Group by local date with day headers.
        val grouped = filtered.groupBy { (rep, _) -> EventUi.agendaDate(rep, zone) }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            grouped.forEach { (date, dayEvents) ->
                item(key = "header-$date") {
                    Text(
                        EventUi.dayHeaderFormat.format(date),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 2.dp),
                    )
                }
                items(dayEvents, key = { it.first.event.id }) { (rep, copies) ->
                    AgendaRow(
                        rep = rep,
                        copies = copies,
                        inConflict = copies.any { it.event.id in conflictedIds },
                        zone = zone,
                        onClick = {
                            selectedEvent = EventDetailModel(
                                title = rep.event.title,
                                startUtc = rep.event.startUtc,
                                endUtc = rep.event.endUtc,
                                isAllDay = rep.event.isAllDay,
                                startDate = rep.event.startDate,
                                location = rep.event.location,
                                organizer = rep.event.organizer,
                                showAs = rep.event.showAs.name,
                                accounts = copies.distinctBy { it.accountId }.map {
                                    EventDetailAccount(
                                        id = it.accountId,
                                        name = it.accountName,
                                        type = it.accountType,
                                        icsHost = icsHostById[it.accountId],
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(
    rep: MergedEvent,
    copies: List<MergedEvent>,
    inConflict: Boolean,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row {
            copies.distinctBy { it.accountId }.forEach {
                Spacer(Modifier.size(10.dp).background(Color(it.accountColor), CircleShape))
                Spacer(Modifier.width(2.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(rep.event.title, style = MaterialTheme.typography.bodyMedium)
            val badge = copies.distinctBy { it.accountId }.joinToString(" + ") { it.accountName }
            Text(
                "${EventUi.timeRangeText(rep, zone)} · $badge",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (inConflict) {
            Text(
                "CONFLICT",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .background(Color(0xFFD93025), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
