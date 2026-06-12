package dev.albert.calmerge.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import dev.albert.calmerge.data.db.AccountType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class EventDetailModel(
    val title: String,
    val startUtc: Long?,
    val endUtc: Long?,
    val isAllDay: Boolean,
    val startDate: String?,
    val location: String?,
    val organizer: String?,
    val showAs: String,
    val accounts: List<EventDetailAccount>,
)

data class EventDetailAccount(
    val id: String,
    val name: String,
    val type: AccountType,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    event: EventDetailModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()

    val timeMs = event.startUtc ?: event.startDate?.let {
        runCatching { LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    } ?: System.currentTimeMillis()

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

            Button(
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/$timeMs")),
                        )
                    } catch (_: ActivityNotFoundException) {
                        // No calendar app installed — silently ignore.
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open in calendar app")
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

private fun detailTimeText(event: EventDetailModel, zone: ZoneId): String {
    if (event.isAllDay) {
        val date = event.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
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
