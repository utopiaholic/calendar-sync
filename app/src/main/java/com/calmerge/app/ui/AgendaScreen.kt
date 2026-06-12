package com.calmerge.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calmerge.app.data.db.MergedEvent
import com.calmerge.app.ui.theme.OnSlateSecondary
import com.calmerge.app.ui.theme.SlateDark3
import com.calmerge.app.ui.theme.DefaultAccountColor
import com.calmerge.app.ui.theme.glassSurface
import java.time.ZoneId

/**
 * FR-19: chronological unified agenda, color-coded per feed, glowing badge on
 * conflicting events. Defaults to the current week for easy scanning.
 */
@Composable
fun AgendaScreen(viewModel: MainViewModel) {
    val events by viewModel.mergedEvents.collectAsState()
    val conflictedIds by viewModel.conflictedEventIds.collectAsState()
    val filter by viewModel.agendaFilter.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val zone = ZoneId.systemDefault()
    var selectedEvent by remember { mutableStateOf<EventDetailModel?>(null) }

    val icsHostMap = remember(accounts) { icsHostsByAccountId(accounts) }

    val filtered = remember(events, filter) {
        val collapsed = EventUi.collapseDuplicates(events)
        val todayStartMs = EventUi.startOfTodayMs(zone)
        when (filter) {
            AgendaFilter.UPCOMING ->
                collapsed
                    .filter { (rep, _) -> !EventUi.isPast(rep, zone, todayStartMs) }
                    .sortedBy { (rep, _) -> EventUi.sortKeyMs(rep, zone) }
            AgendaFilter.PAST ->
                collapsed
                    .filter { (rep, _) -> EventUi.isPast(rep, zone, todayStartMs) }
                    .sortedByDescending { (rep, _) -> EventUi.sortKeyMs(rep, zone) }
        }
    }

    selectedEvent?.let { event ->
        EventDetailSheet(event = event, onDismiss = { selectedEvent = null })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Thin sync progress indicator
        if (syncing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        CenteredSegmentedTabs(
            options = listOf("Upcoming", "Past"),
            selectedIndex = if (filter == AgendaFilter.UPCOMING) 0 else 1,
            onSelect = { viewModel.agendaFilter.value = if (it == 0) AgendaFilter.UPCOMING else AgendaFilter.PAST },
        )

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No events",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (filter == AgendaFilter.UPCOMING) "Nothing upcoming" else "No past events",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSlateSecondary,
                    )
                }
            }
            return@Column
        }

        val grouped = remember(filtered) { filtered.groupBy { (rep, _) -> EventUi.agendaDate(rep, zone) } }

        @OptIn(ExperimentalFoundationApi::class)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            grouped.forEach { (date, dayEvents) ->
                stickyHeader(key = "header-$date") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(alpha = 0.92f)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            EventUi.dayHeaderFormat.format(date),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(dayEvents, key = { it.first.event.id }) { (rep, copies) ->
                    EventCard(
                        rep = rep,
                        copies = copies,
                        inConflict = copies.any { it.event.id in conflictedIds },
                        zone = zone,
                        onClick = {
                            selectedEvent = rep.toDetailModel(copies, icsHostMap)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    rep: MergedEvent,
    copies: List<MergedEvent>,
    inConflict: Boolean,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val primaryColor = Color(copies.firstOrNull()?.accountColor ?: DefaultAccountColor)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        // Colored left-accent strip
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(primaryColor),
        )
        Spacer(Modifier.width(12.dp))

        // Card surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SlateDark3)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        rep.event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            EventUi.timeRangeText(rep, zone),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSlateSecondary,
                        )
                        copies.distinctBy { it.accountId }.forEach { copy ->
                            Spacer(
                                Modifier
                                    .size(6.dp)
                                    .background(Color(copy.accountColor), CircleShape),
                            )
                        }
                    }
                }

                if (inConflict) {
                    // 48dp touch target for haptic tap
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Has scheduling conflict" }
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onClick()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        ConflictBadge()
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
