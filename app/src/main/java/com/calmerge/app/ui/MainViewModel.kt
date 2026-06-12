package com.calmerge.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calmerge.app.CalMergeApp
import com.calmerge.app.data.db.AccountEntity
import com.calmerge.app.data.db.AccountType
import com.calmerge.app.data.db.CalendarSourceEntity
import com.calmerge.app.data.db.ConflictMemberRow
import com.calmerge.app.data.db.MergedEvent
import com.calmerge.app.work.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import kotlinx.coroutines.launch
import java.util.UUID

enum class AgendaFilter { UPCOMING, PAST }

private const val TAG = "MainViewModel"

/** One conflict cluster prepared for display, soonest first (FR-21). */
data class ConflictClusterUi(
    val clusterId: String,
    /** Members collapsed by dedupe group: representative + all copies. */
    val members: List<Pair<ConflictMemberRow, List<ConflictMemberRow>>>,
    val sortKeyMs: Long,
    /** Latest end of any member event; used for past/upcoming partitioning. */
    val endKeyMs: Long,
)

class MainViewModel(private val app: CalMergeApp) : ViewModel() {

    /** Distinct badge colors assigned to feeds in connect order; cycles by usage count only when palette exhausted. */
    val palette = listOf(
        0xFF1A73E8.toInt(), // blue
        0xFFD93025.toInt(), // red
        0xFF188038.toInt(), // green
        0xFFF9AB00.toInt(), // amber
        0xFF9334E6.toInt(), // purple
        0xFF12A4AF.toInt(), // teal
        0xFFE8710A.toInt(), // orange
        0xFFB80672.toInt(), // magenta
        0xFF5F6368.toInt(), // grey
        0xFF7CB342.toInt(), // light green
    )

    val accounts: StateFlow<List<AccountEntity>> = app.db.accountDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sources: StateFlow<List<CalendarSourceEntity>> = app.db.calendarSourceDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mergedEvents: StateFlow<List<MergedEvent>> = app.db.eventInstanceDao().observeMerged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Event ids carrying a red conflict badge in the agenda (FR-19). */
    val conflictedEventIds: StateFlow<Set<String>> = app.db.conflictDao().observeConflictedEventIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val conflictClusters: StateFlow<List<ConflictClusterUi>> = app.db.conflictDao().observeConflictMembers()
        .map { rows -> ConflictClusterMapper.toClusterUi(rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Clusters from today onward, ascending — drives the Conflicts tab's Upcoming view. */
    val upcomingConflictClusters: StateFlow<List<ConflictClusterUi>> = conflictClusters
        .map { clusters ->
            val startOfToday = EventUi.startOfTodayMs(ZoneId.systemDefault())
            clusters.filter { it.endKeyMs > startOfToday }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Past clusters, most recent first — drives the Conflicts tab's Past view. */
    val pastConflictClusters: StateFlow<List<ConflictClusterUi>> = conflictClusters
        .map { clusters ->
            val startOfToday = EventUi.startOfTodayMs(ZoneId.systemDefault())
            clusters.filter { it.endKeyMs <= startOfToday }.reversed()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Clusters within the user-configured lookahead window — drives home screen counts. */
    val windowedConflictClusters: StateFlow<List<ConflictClusterUi>> = conflictClusters
        .combine(app.settings.conflictLookaheadDays) { clusters, days ->
            val startOfToday = EventUi.startOfTodayMs(ZoneId.systemDefault())
            val windowEnd = startOfToday + days.toLong() * 86_400_000L
            clusters.filter { it.endKeyMs > startOfToday && it.sortKeyMs < windowEnd }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Default UPCOMING: shows events from today onward for easy scanning. */
    val agendaFilter = MutableStateFlow(AgendaFilter.UPCOMING)

    val syncing = MutableStateFlow(false)

    /**
     * Non-null when addIcsAccount rejects a duplicate URL (FR-3).
     * Cleared by the dialog on dismiss or when url input changes.
     */
    val addFeedError = MutableStateFlow<String?>(null)

    /**
     * Flips to true momentarily when an account is successfully inserted so the
     * dialog can close itself. Reset to false after consumption.
     */
    val addFeedSuccess = MutableStateFlow(false)

    fun addIcsAccount(name: String, url: String) {
        viewModelScope.launch {
            val normalized = normalizeUrlOrNull(url)
            val duplicate = normalized != null && accounts.value.any {
                normalizeUrlOrNull(it.icsUrl ?: "") == normalized
            }
            if (duplicate) {
                addFeedError.value = "This feed is already added"
                return@launch
            }
            addFeedError.value = null
            app.db.accountDao().upsert(
                AccountEntity(
                    id = UUID.randomUUID().toString(),
                    type = AccountType.ICS,
                    displayName = name.ifBlank { "ICS feed" },
                    email = null,
                    color = nextColor(),
                    icsUrl = url.trim(),
                ),
            )
            addFeedSuccess.value = true
            syncNow()
        }
    }

    fun clearAddFeedError() {
        addFeedError.value = null
    }

    fun consumeAddFeedSuccess() {
        addFeedSuccess.value = false
    }

    fun syncNow() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            try {
                app.syncCoordinator.syncAll()
            } finally {
                syncing.value = false
            }
        }
    }

    fun setSourceIncluded(sourceId: String, included: Boolean) {
        viewModelScope.launch {
            app.db.calendarSourceDao().setIncluded(sourceId, included)
            recomputeDerived("setSourceIncluded")
        }
    }

    // ---- Settings delegation ----

    val syncIntervalMinutes: StateFlow<Long> = app.settings.syncIntervalMinutes
    val includeTentative: StateFlow<Boolean> = app.settings.includeTentative
    val includeOof: StateFlow<Boolean> = app.settings.includeOof
    val allDayConflictsWithTimed: StateFlow<Boolean> = app.settings.allDayConflictsWithTimed
    val conflictLookaheadDays: StateFlow<Int> = app.settings.conflictLookaheadDays

    fun setSyncInterval(minutes: Long) {
        app.settings.setSyncInterval(minutes)
        if (minutes > 0) {
            SyncScheduler.ensurePeriodicSync(app, minutes)
        } else {
            // Cancel periodic work when user selects "Manual only".
            SyncScheduler.cancelPeriodicSync(app)
        }
    }

    fun setIncludeTentative(v: Boolean) {
        app.settings.setIncludeTentative(v)
        viewModelScope.launch { recomputeDerived("setIncludeTentative") }
    }

    fun setIncludeOof(v: Boolean) {
        app.settings.setIncludeOof(v)
        viewModelScope.launch { recomputeDerived("setIncludeOof") }
    }

    fun setAllDayConflictsWithTimed(v: Boolean) {
        app.settings.setAllDayConflictsWithTimed(v)
        viewModelScope.launch { recomputeDerived("setAllDayConflictsWithTimed") }
    }

    fun setConflictLookaheadDays(days: Int) {
        app.settings.setConflictLookaheadDays(days)
    }

    /** NFR-6: wipe all accounts and events (cascade). */
    fun wipeAllData() {
        viewModelScope.launch {
            // Query inside the coroutine — accounts.value can be empty if no collector
            // has subscribed (e.g. after process-death restore to Settings tab).
            app.db.accountDao().getAll().forEach { account ->
                app.db.accountDao().delete(account.id)
            }
            recomputeDerived("wipeAllData")
        }
    }

    /** FR-19: user-pickable account color. Targeted update avoids clobbering a
     *  concurrently-written sync status (lastSyncUtc / lastSyncError). */
    fun updateAccountColor(accountId: String, color: Int) {
        viewModelScope.launch {
            app.db.accountDao().updateColor(accountId, color)
        }
    }

    /** NFR-6: disconnecting removes the feed URL and (via FK cascade) all cached events. */
    fun removeAccount(account: AccountEntity) {
        viewModelScope.launch {
            app.db.accountDao().delete(account.id)
            recomputeDerived("removeAccount")
        }
    }

    /** Pick the first palette color not currently used; cycle by usage count only when all are taken. */
    private fun nextColor(): Int {
        val usedColors = accounts.value.map { it.color }.toSet()
        val unused = palette.firstOrNull { it !in usedColors }
        if (unused != null) return unused
        // All palette colors are in use — cycle by least-used.
        val usageCounts = palette.associateWith { color -> accounts.value.count { it.color == color } }
        return palette.minByOrNull { usageCounts[it] ?: 0 } ?: palette[0]
    }

    private suspend fun recomputeDerived(reason: String) {
        try {
            app.syncCoordinator.recomputeDerivedState()
        } catch (e: Exception) {
            Log.e(TAG, "recomputeDerivedState failed after $reason", e)
        }
    }

    companion object {
        fun factory(app: CalMergeApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app) as T
        }
    }
}
