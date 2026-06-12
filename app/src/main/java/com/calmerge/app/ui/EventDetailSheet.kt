package com.calmerge.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    event: EventDetailModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()

    val timeMs = event.startUtc
        ?: isoDateToEpochMillisOrNull(event.startDate, zone)
        ?: System.currentTimeMillis()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(event.title, style = MaterialTheme.typography.titleLarge)
            Text(
                detailTimeText(event, zone),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Account: ${event.accounts.joinToString(" + ") { it.name }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Show-as: ${event.showAs}", style = MaterialTheme.typography.bodyMedium)
            if (!event.location.isNullOrBlank()) {
                Text("Location: ${event.location}", style = MaterialTheme.typography.bodyMedium)
            }
            if (!event.organizer.isNullOrBlank()) {
                Text("Organizer: ${event.organizer}", style = MaterialTheme.typography.bodyMedium)
            }

            // FR-22: deep-link to the native calendar app for this event.
            val deepLinkLabel = resolveDeepLinkLabel(event.accounts)
            Button(
                onClick = { openInCalendarApp(context, event, timeMs) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(deepLinkLabel)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

/** Returns a label for the deep-link button based on which calendar app is likely available. */
private fun resolveDeepLinkLabel(accounts: List<EventDetailAccount>): String {
    val hosts = accounts.mapNotNull { it.icsHost }
    return when {
        CalendarProvider.MICROSOFT.matchesAny(hosts) -> "Open in Outlook"
        CalendarProvider.GOOGLE.matchesAny(hosts) -> "Open in Google Calendar"
        else -> "Open in calendar app"
    }
}

private fun openInCalendarApp(
    context: Context,
    event: EventDetailModel,
    timeMs: Long,
) {
    val hosts = event.accounts.mapNotNull { it.icsHost }

    // Try Outlook if host suggests Microsoft.
    if (CalendarProvider.MICROSOFT.matchesAny(hosts)) {
        if (tryStart(context, outlookIntent(timeMs))) return
    }

    // Try Google Calendar if host suggests Google.
    if (CalendarProvider.GOOGLE.matchesAny(hosts)) {
        if (tryStart(context, googleCalendarIntent(timeMs))) return
    }

    // Generic AOSP calendar content URI.
    if (tryStart(context, Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/$timeMs")))) return

    // Nothing handled it — web fallback to Google Calendar day view for the event's date.
    val localDate = Instant.ofEpochMilli(timeMs)
        .atZone(ZoneId.systemDefault()).toLocalDate()
    val webUri = "https://calendar.google.com/calendar/r/day/${localDate.year}/${localDate.monthValue}/${localDate.dayOfMonth}"
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri))
    if (tryStart(context, webIntent)) return

    Toast.makeText(context, "No calendar app found", Toast.LENGTH_SHORT).show()
}

private fun outlookIntent(timeMs: Long): Intent {
    // ms-outlook://events/view?start=<ISO-8601> is the documented Outlook mobile URI.
    val iso = Instant.ofEpochMilli(timeMs)
        .atZone(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    return Intent(Intent.ACTION_VIEW, Uri.parse("ms-outlook://events/view?start=${Uri.encode(iso)}"))
        .setPackage("com.microsoft.office.outlook")
}

private fun googleCalendarIntent(timeMs: Long): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/$timeMs"))
        .setPackage("com.google.android.calendar")

private fun tryStart(context: Context, intent: Intent): Boolean {
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun detailTimeText(event: EventDetailModel, zone: ZoneId): String {
    if (event.isAllDay) {
        val date = parseIsoDateOrNull(event.startDate)
        return if (date != null) {
            "All day · ${date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}"
        } else {
            "All day"
        }
    }
    val start = event.startUtc?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val end = event.endUtc?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val day = start?.toLocalDate()?.format(DateTimeFormatter.ofPattern("EEE, MMM d")) ?: "Unknown day"
    val startText = start?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "?"
    val endText = end?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "?"
    return "$day · $startText-$endText"
}
