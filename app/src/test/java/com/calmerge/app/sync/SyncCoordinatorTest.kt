package com.calmerge.app.sync

import com.calmerge.app.data.db.AccountEntity
import com.calmerge.app.data.db.AccountStatus
import com.calmerge.app.data.db.AccountType
import com.calmerge.app.data.db.CalendarSourceEntity
import com.calmerge.app.data.db.EventInstanceEntity
import com.calmerge.app.data.db.ResponseStatus
import com.calmerge.app.data.db.ShowAs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Tests for the provider-neutral SyncCoordinator using a fake in-memory DatabasePort.
 * No Room, no OkHttp, no Android — pure JVM.
 *
 * The seam test proves adding a third engine type requires zero coordinator changes.
 */
class SyncCoordinatorTest {

    // -----------------------------------------------------------------------
    // Fake infrastructure
    // -----------------------------------------------------------------------

    private val fakeDb = FakeDb()

    private fun account(
        id: String,
        type: AccountType,
        status: AccountStatus = AccountStatus.ACTIVE,
    ) = AccountEntity(
        id = id,
        type = type,
        displayName = "Account $id",
        email = null,
        color = 0xFF0000.toInt(),
        status = status,
    )

    private fun source(id: String, accountId: String, included: Boolean = true) =
        CalendarSourceEntity(id = id, accountId = accountId, providerCalendarId = id, name = id, included = included)

    private fun event(id: String, sourceId: String) = EventInstanceEntity(
        id = id,
        calendarSourceId = sourceId,
        providerEventId = "p-$id",
        iCalUId = null,
        title = "Event $id",
        startUtc = 1_000_000L,
        endUtc = 2_000_000L,
        isAllDay = false,
        startDate = null,
        endDate = null,
        showAs = ShowAs.BUSY,
        responseStatus = ResponseStatus.ACCEPTED,
        location = null,
        organizer = null,
        lastModifiedUtc = null,
    )

    private inner class FakeEngine(
        override val accountType: AccountType,
        private val result: Map<String, List<EventInstanceEntity>> = emptyMap(),
        private val throws: Exception? = null,
    ) : CalendarSourceEngine {
        var fetchCallCount = 0
        override suspend fun fetch(
            account: AccountEntity,
            includedSources: List<CalendarSourceEntity>,
            window: SyncWindow,
        ): Map<String, List<EventInstanceEntity>> {
            fetchCallCount++
            if (throws != null) throw throws
            return result
        }
    }

    private fun coordinator(vararg engines: CalendarSourceEngine) = SyncCoordinator(
        db = fakeDb,
        engines = engines.associateBy { it.accountType },
    )

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    fun `fetch results are persisted per source`() {
        val acc = account("acc-1", AccountType.ICS)
        val src = source("src-1", "acc-1")
        fakeDb.accounts += acc
        fakeDb.sources += src
        val events = listOf(event("evt-1", "src-1"), event("evt-2", "src-1"))
        val engine = FakeEngine(AccountType.ICS, result = mapOf("src-1" to events))

        runBlocking { coordinator(engine).syncAll(Instant.now()) }

        assertEquals(events, fakeDb.eventsBySource["src-1"])
    }

    @Test
    fun `CalendarPermissionException maps to NEEDS_REAUTH`() {
        val acc = account("acc-local", AccountType.LOCAL)
        fakeDb.accounts += acc
        val engine = FakeEngine(
            accountType = AccountType.LOCAL,
            throws = CalendarPermissionException("Calendar permission revoked"),
        )

        runBlocking { coordinator(engine).syncAll(Instant.now()) }

        assertEquals(AccountStatus.NEEDS_REAUTH, fakeDb.statusById["acc-local"])
    }

    @Test
    fun `generic exception maps to ERROR status`() {
        val acc = account("acc-1", AccountType.ICS)
        fakeDb.accounts += acc
        val engine = FakeEngine(AccountType.ICS, throws = RuntimeException("feed 500"))

        runBlocking { coordinator(engine).syncAll(Instant.now()) }

        assertEquals(AccountStatus.ERROR, fakeDb.statusById["acc-1"])
    }

    @Test
    fun `error in one account does not block others`() {
        val accFailing = account("acc-fail", AccountType.ICS)
        val accOk = account("acc-ok", AccountType.LOCAL)
        val srcOk = source("src-ok", "acc-ok")
        fakeDb.accounts += accFailing
        fakeDb.accounts += accOk
        fakeDb.sources += srcOk
        val events = listOf(event("evt-ok", "src-ok"))

        val engineFailing = FakeEngine(AccountType.ICS, throws = RuntimeException("feed 500"))
        val engineOk = FakeEngine(AccountType.LOCAL, result = mapOf("src-ok" to events))

        runBlocking { coordinator(engineFailing, engineOk).syncAll(Instant.now()) }

        assertEquals(AccountStatus.ERROR, fakeDb.statusById["acc-fail"])
        assertEquals(events, fakeDb.eventsBySource["src-ok"])
    }

    @Test
    fun `accounts with no matching engine are skipped without crashing`() {
        val acc = account("acc-1", AccountType.LOCAL)
        fakeDb.accounts += acc
        val engine = FakeEngine(AccountType.ICS)

        var threw = false
        try {
            runBlocking { coordinator(engine).syncAll(Instant.now()) }
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("Coordinator must not throw for unknown account type", !threw)
        assertEquals(0, engine.fetchCallCount)
    }

    @Test
    fun `adding a third engine type requires no coordinator changes (seam test)`() {
        // Simulates a future OAuth engine — just use LOCAL as a stand-in to avoid
        // touching the real enum, but the wiring pattern is identical.
        val futureType = AccountType.LOCAL
        val acc = account("acc-future", futureType)
        val src = source("src-future", "acc-future")
        fakeDb.accounts += acc
        fakeDb.sources += src
        val events = listOf(event("evt-future", "src-future"))
        val futureEngine = FakeEngine(futureType, result = mapOf("src-future" to events))
        val icsEngine = FakeEngine(AccountType.ICS)

        runBlocking { coordinator(icsEngine, futureEngine).syncAll(Instant.now()) }

        assertEquals(events, fakeDb.eventsBySource["src-future"])
        assertEquals(0, icsEngine.fetchCallCount)
    }

    // -----------------------------------------------------------------------
    // Minimal fake DatabasePort — no Room, no Android
    // -----------------------------------------------------------------------

    private class FakeDb : SyncCoordinator.DatabasePort {
        val accounts = mutableListOf<AccountEntity>()
        val sources = mutableListOf<CalendarSourceEntity>()
        val eventsBySource = mutableMapOf<String, List<EventInstanceEntity>>()
        val statusById = mutableMapOf<String, AccountStatus>()

        override suspend fun getAllAccounts() = accounts.toList()

        override suspend fun getIncludedSources(accountId: String) =
            sources.filter { it.accountId == accountId && it.included }

        override suspend fun replaceAllForSource(sourceId: String, events: List<EventInstanceEntity>) {
            eventsBySource[sourceId] = events
        }

        override suspend fun markSyncSuccess(id: String, syncedAtUtc: Long) {
            statusById[id] = AccountStatus.ACTIVE
        }

        override suspend fun markSyncFailure(id: String, error: String?, status: AccountStatus) {
            statusById[id] = status
        }

        override suspend fun insertIgnoreSources(sources: List<CalendarSourceEntity>) {
            this.sources.addAll(sources.filter { s -> this.sources.none { it.id == s.id } })
        }
    }
}
