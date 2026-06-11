package dev.albert.calmerge.sync

import dev.albert.calmerge.data.db.AccountStatus
import dev.albert.calmerge.data.db.AccountType
import dev.albert.calmerge.data.db.AppDatabase
import java.time.Instant
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
        recomputeDedupe()
    }

    private suspend fun recomputeDedupe() {
        val dao = db.eventInstanceDao()
        dao.clearDedupeGroups()
        for (group in Deduper.computeGroups(dao.getAll())) {
            dao.setDedupeGroup(UUID.randomUUID().toString(), group.eventIds)
        }
    }
}
