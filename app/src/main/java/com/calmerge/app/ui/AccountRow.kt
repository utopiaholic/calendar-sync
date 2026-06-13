package com.calmerge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.calmerge.app.data.db.AccountEntity
import com.calmerge.app.data.db.AccountStatus
import com.calmerge.app.data.db.AccountType

@Composable
internal fun AccountRow(account: AccountEntity, now: Long, onRemove: () -> Unit, onColorClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        // FR-19: tapping the color dot opens the palette picker. 48dp touch target wraps the 22dp dot.
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics { role = Role.Button; contentDescription = "Change color for ${account.displayName}" }
                .clickable(onClick = onColorClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(account.color)),
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    account.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                SourceTypeChip(account.type)
            }
            val status = when (account.status) {
                AccountStatus.ACTIVE -> account.lastSyncUtc?.let { "Last synced: ${relativeMinutes(it, now)}" } ?: "Never synced"
                AccountStatus.NEEDS_REAUTH -> "Feed auth problem"
                AccountStatus.ERROR -> "Sync error: ${account.lastSyncError}"
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (account.status == AccountStatus.ACTIVE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

@Composable
private fun SourceTypeChip(type: AccountType) {
    val label = when (type) {
        AccountType.LOCAL -> "Phone"
        AccountType.ICS -> "ICS"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

private fun relativeMinutes(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - epochMs) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ${minutes % 60} min ago"
    }
}
