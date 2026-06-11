package dev.albert.calmerge.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Query("UPDATE accounts SET lastSyncUtc = :syncedAtUtc, lastSyncError = NULL, status = 'ACTIVE' WHERE id = :id")
    suspend fun markSyncSuccess(id: String, syncedAtUtc: Long)

    @Query("UPDATE accounts SET lastSyncError = :error, status = :status WHERE id = :id")
    suspend fun markSyncFailure(id: String, error: String?, status: AccountStatus)

    /** NFR-6: cascades delete all calendar sources and cached events. */
    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CalendarSourceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(sources: List<CalendarSourceEntity>)

    @Query("SELECT * FROM calendar_sources WHERE accountId = :accountId")
    suspend fun getByAccount(accountId: String): List<CalendarSourceEntity>

    @Query("SELECT * FROM calendar_sources")
    fun observeAll(): Flow<List<CalendarSourceEntity>>

    @Query("SELECT * FROM calendar_sources WHERE accountId = :accountId AND included = 1")
    suspend fun getIncludedByAccount(accountId: String): List<CalendarSourceEntity>

    @Query("UPDATE calendar_sources SET included = :included WHERE id = :id")
    suspend fun setIncluded(id: String, included: Boolean)
}

/** Event row joined with its account for the merged debug view. */
data class MergedEvent(
    @Embedded val event: EventInstanceEntity,
    val accountId: String,
    val accountName: String,
    val accountColor: Int,
    val accountType: AccountType,
)

@Dao
interface EventInstanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<EventInstanceEntity>)

    @Query("DELETE FROM event_instances WHERE calendarSourceId = :sourceId AND providerEventId IN (:providerEventIds)")
    suspend fun deleteByProviderIds(sourceId: String, providerEventIds: List<String>)

    /** Full-resync path: replace everything for one calendar. */
    @Query("DELETE FROM event_instances WHERE calendarSourceId = :sourceId")
    suspend fun deleteAllForSource(sourceId: String)

    @Transaction
    suspend fun replaceAllForSource(sourceId: String, events: List<EventInstanceEntity>) {
        deleteAllForSource(sourceId)
        upsertAll(events)
    }

    @Query("SELECT * FROM event_instances")
    suspend fun getAll(): List<EventInstanceEntity>

    @Query(
        """
        SELECT e.*, a.id AS accountId, a.displayName AS accountName, a.color AS accountColor, a.type AS accountType
        FROM event_instances e
        JOIN calendar_sources c ON e.calendarSourceId = c.id
        JOIN accounts a ON c.accountId = a.id
        WHERE c.included = 1
        ORDER BY COALESCE(e.startUtc, 0), e.startDate, e.title
        """
    )
    fun observeMerged(): Flow<List<MergedEvent>>

    @Query("UPDATE event_instances SET dedupeGroupId = NULL")
    suspend fun clearDedupeGroups()

    @Query("UPDATE event_instances SET dedupeGroupId = :groupId WHERE id IN (:eventIds)")
    suspend fun setDedupeGroup(groupId: String, eventIds: List<String>)
}
