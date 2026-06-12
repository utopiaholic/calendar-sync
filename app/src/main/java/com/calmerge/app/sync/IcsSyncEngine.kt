package com.calmerge.app.sync

import com.calmerge.app.data.db.AccountEntity
import com.calmerge.app.data.db.AppDatabase
import com.calmerge.app.data.db.CalendarSourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.UUID

/**
 * FR-3/FR-8: ICS feed accounts — degraded source type with full re-download
 * and re-parse on every refresh (no incremental support).
 */
class IcsSyncEngine(
    private val okHttp: OkHttpClient,
    private val db: AppDatabase,
) {

    suspend fun sync(account: AccountEntity, window: SyncWindow) {
        val url = requireNotNull(account.icsUrl) { "ICS account missing feed URL" }
        val source = ensureSource(account)
        if (!source.included) return

        val icsText = withContext(Dispatchers.IO) { download(url) }
        val events = IcsParser.parse(icsText, source.id, window)
        db.eventInstanceDao().replaceAllForSource(source.id, events)
    }

    /** An ICS account has exactly one implicit calendar. */
    private suspend fun ensureSource(account: AccountEntity): CalendarSourceEntity {
        db.calendarSourceDao().getByAccount(account.id).firstOrNull()?.let { return it }
        val source = CalendarSourceEntity(
            id = UUID.randomUUID().toString(),
            accountId = account.id,
            providerCalendarId = "ics",
            name = account.displayName,
        )
        db.calendarSourceDao().insertIgnore(listOf(source))
        return source
    }

    private fun download(url: String): String {
        val request = Request.Builder().url(url).build()
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ICS feed returned HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty ICS response")
        }
    }
}
