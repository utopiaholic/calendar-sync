package com.calmerge.app.sync

import android.util.Log
import androidx.room.withTransaction
import com.calmerge.app.data.db.AccountEntity
import com.calmerge.app.data.db.AccountStatus
import com.calmerge.app.data.db.AccountType
import com.calmerge.app.data.db.AppDatabase
import com.calmerge.app.data.db.CalendarSourceEntity
import com.calmerge.app.data.db.ConflictClusterEntity
import com.calmerge.app.data.db.ConflictMemberEntity
import com.calmerge.app.data.db.EventInstanceEntity
import com.calmerge.app.settings.SettingsStore
import java.net.SocketException
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Provider-neutral sync orchestrator. Owns all persistence; engines are read-only
 * fetchers that never touch the database.
 *
 * Constructor accepts a map of engines keyed by AccountType. To register a new
 * calendar source: add the engine to this map in CalMergeApp — nothing else changes.
 *
 * Failure isolation: one account erroring never blocks the others (FR-24).
 */
class SyncCoordinator(
    private val db: DatabasePort,
    private val engines: Map<AccountType, CalendarSourceEngine>,
    private val settings: SettingsStore? = null,
) {

    /**
     * Minimal persistence surface used by the coordinator.
     * Extracted as an interface so the coordinator is unit-testable without Room.
     * [RoomAdapter] bridges to AppDatabase for production use.
     */
    interface DatabasePort {
        suspend fun getAllAccounts(): List<AccountEntity>
        suspend fun getIncludedSources(accountId: String): List<CalendarSourceEntity>
        suspend fun replaceAllForSource(sourceId: String, events: List<EventInstanceEntity>)
        suspend fun markSyncSuccess(id: String, syncedAtUtc: Long)
        suspend fun markSyncFailure(id: String, error: String?, status: AccountStatus)
        suspend fun insertIgnoreSources(sources: List<CalendarSourceEntity>)
    }

    suspend fun syncAll(now: Instant = Instant.now()) {
        val window = SyncWindow.around(now)
        for (account in db.getAllAccounts()) {
            val engine = engines[account.type] ?: continue
            try {
                val includedSources = db.getIncludedSources(account.id)
                val effectiveSources = if (includedSources.isEmpty() && account.type == AccountType.ICS) {
                    backfillIcsSource(account.id, account.displayName)
                } else {
                    includedSources
                }
                val eventsBySource = engine.fetch(account, effectiveSources, window)
                for ((sourceId, events) in eventsBySource) {
                    db.replaceAllForSource(sourceId, events)
                }
                db.markSyncSuccess(account.id, now.toEpochMilli())
            } catch (e: CalendarPermissionException) {
                db.markSyncFailure(account.id, e.message, AccountStatus.NEEDS_REAUTH)
            } catch (e: Exception) {
                if (isTransientNetworkError(e)) {
                    Log.w(TAG, "Transient network error for ${account.id}, skipping status update", e)
                } else {
                    db.markSyncFailure(account.id, e.message ?: e.javaClass.simpleName, AccountStatus.ERROR)
                }
            }
        }
        try {
            recomputeDerivedState(now)
        } catch (e: Exception) {
            Log.e(TAG, "recomputeDerivedState failed", e)
        }
    }

    /**
     * Recomputes dedupe groups (FR-13) and the conflict cache (FR-17) in a single
     * Room transaction so readers never see partially-updated state.
     * Only callable via the RoomAdapter path; no-ops in tests via the interface.
     */
    suspend fun recomputeDerivedState(now: Instant = Instant.now()) {
        (db as? RoomAdapter)?.recomputeDerivedState(now, settings)
    }

    private suspend fun backfillIcsSource(accountId: String, displayName: String): List<CalendarSourceEntity> {
        val source = CalendarSourceEntity(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            providerCalendarId = "ics",
            name = displayName,
        )
        db.insertIgnoreSources(listOf(source))
        return listOf(source)
    }

    companion object {
        private const val TAG = "SyncCoordinator"

        private fun isTransientNetworkError(e: Throwable): Boolean =
            e is UnknownHostException || e is SocketException ||
                (e.cause != null && isTransientNetworkError(e.cause!!))
    }

    /**
     * Production DatabasePort backed by Room. Holds the AppDatabase directly so
     * it can run transactions and recompute derived state after sync.
     */
    class RoomAdapter(
        private val appDb: AppDatabase,
    ) : DatabasePort {

        override suspend fun getAllAccounts() = appDb.accountDao().getAll()

        override suspend fun getIncludedSources(accountId: String) =
            appDb.calendarSourceDao().getIncludedByAccount(accountId)

        override suspend fun replaceAllForSource(sourceId: String, events: List<EventInstanceEntity>) =
            appDb.eventInstanceDao().replaceAllForSource(sourceId, events)

        override suspend fun markSyncSuccess(id: String, syncedAtUtc: Long) =
            appDb.accountDao().markSyncSuccess(id, syncedAtUtc)

        override suspend fun markSyncFailure(id: String, error: String?, status: AccountStatus) =
            appDb.accountDao().markSyncFailure(id, error, status)

        override suspend fun insertIgnoreSources(sources: List<CalendarSourceEntity>) =
            appDb.calendarSourceDao().insertIgnore(sources)

        suspend fun recomputeDerivedState(now: Instant, settings: SettingsStore?) {
            appDb.withTransaction {
                recomputeDedupe()
                recomputeConflicts(now, settings)
            }
        }

        private suspend fun recomputeDedupe() {
            val dao = appDb.eventInstanceDao()
            dao.clearDedupeGroups()
            for (group in Deduper.computeGroups(dao.getAll())) {
                dao.setDedupeGroup(UUID.randomUUID().toString(), group.eventIds)
            }
        }

        private suspend fun recomputeConflicts(now: Instant, settings: SettingsStore?) {
            val events = appDb.eventInstanceDao().getAllIncluded()
            val config = settings?.let {
                ConflictConfig(
                    includeTentative = it.includeTentative.value,
                    includeOof = it.includeOof.value,
                    allDayConflictsWithTimed = it.allDayConflictsWithTimed.value,
                )
            } ?: ConflictConfig()
            val clusters = ConflictDetector.detect(events, ZoneId.systemDefault(), config)
            val clusterEntities = mutableListOf<ConflictClusterEntity>()
            val memberEntities = mutableListOf<ConflictMemberEntity>()
            for (memberIds in clusters) {
                val clusterId = UUID.randomUUID().toString()
                clusterEntities += ConflictClusterEntity(clusterId, now.toEpochMilli())
                memberIds.forEach { memberEntities += ConflictMemberEntity(clusterId, it) }
            }
            appDb.conflictDao().replaceAll(clusterEntities, memberEntities)
        }
    }
}
