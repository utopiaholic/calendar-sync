package dev.albert.calmerge.sync

import android.util.Log
import androidx.room.withTransaction
import dev.albert.calmerge.data.db.AccountStatus
import dev.albert.calmerge.data.db.AccountType
import dev.albert.calmerge.data.db.AppDatabase
import dev.albert.calmerge.data.db.ConflictClusterEntity
import dev.albert.calmerge.data.db.ConflictMemberEntity
import dev.albert.calmerge.settings.SettingsStore
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Runs a one-way pull sync for every account, isolating failures per account
 * (FR-24: one feed erroring must not block the others), then recomputes
 * cross-account dedupe groups (FR-13).
 *
 * ICS-only build per pre-flight outcome: Graph consent is blocked in both work
 * tenants, so all three calendars are ICS feeds (spec §3 fallback).
 */
class SyncCoordinator(
    private val db: AppDatabase,
    private val icsEngine: IcsSyncEngine,
    private val settings: SettingsStore? = null,
) {

    suspend fun syncAll(now: Instant = Instant.now()) {
        val window = SyncWindow.around(now)
        for (account in db.accountDao().getAll()) {
            try {
                when (account.type) {
                    AccountType.ICS -> icsEngine.sync(account, window)
                    AccountType.MS, AccountType.GOOGLE ->
                        error("OAuth account types are not supported in the ICS-only build")
                }
                db.accountDao().markSyncSuccess(account.id, now.toEpochMilli())
            } catch (e: Exception) {
                db.accountDao().markSyncFailure(account.id, e.message ?: e.javaClass.simpleName, AccountStatus.ERROR)
            }
        }
        try {
            recomputeDerivedState(now)
        } catch (e: Exception) {
            Log.e("SyncCoordinator", "recomputeDerivedState failed", e)
        }
    }

    /**
     * Recomputes dedupe groups (FR-13) and the conflict cache (FR-17) in a single
     * Room transaction so readers never see a partially-updated state.
     *
     * Dedupe groups are computed over ALL events (dedupeGroupId is also consumed
     * by the agenda which filters by included separately). Conflict detection runs
     * only over included events (FR-5).
     */
    suspend fun recomputeDerivedState(now: Instant = Instant.now()) {
        db.withTransaction {
            recomputeDedupe()
            recomputeConflicts(now)
        }
    }

    private suspend fun recomputeDedupe() {
        val dao = db.eventInstanceDao()
        dao.clearDedupeGroups()
        for (group in Deduper.computeGroups(dao.getAll())) {
            dao.setDedupeGroup(UUID.randomUUID().toString(), group.eventIds)
        }
    }

    /** FR-17: conflict computation runs after every sync; results cached in SQLite. */
    private suspend fun recomputeConflicts(now: Instant) {
        // FR-5: only included events feed into conflict detection.
        val events = db.eventInstanceDao().getAllIncluded()
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
        db.conflictDao().replaceAll(clusterEntities, memberEntities)
    }
}
