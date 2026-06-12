package com.calmerge.app.sync

import com.calmerge.app.data.db.AccountEntity
import com.calmerge.app.data.db.AccountType
import com.calmerge.app.data.db.CalendarSourceEntity
import com.calmerge.app.data.db.EventInstanceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * FR-3/FR-8: ICS feed accounts — full re-download and re-parse on every refresh.
 * Each ICS account has exactly one implicit source (created at add time, not here).
 */
class IcsSyncEngine(private val okHttp: OkHttpClient) : CalendarSourceEngine {

    override val accountType: AccountType = AccountType.ICS

    override suspend fun fetch(
        account: AccountEntity,
        includedSources: List<CalendarSourceEntity>,
        window: SyncWindow,
    ): Map<String, List<EventInstanceEntity>> {
        val source = includedSources.firstOrNull() ?: return emptyMap()
        val url = requireNotNull(account.icsUrl) { "ICS account ${account.id} missing feed URL" }
        val icsText = withContext(Dispatchers.IO) { download(url) }
        return mapOf(source.id to IcsParser.parse(icsText, source.id, window))
    }

    private fun download(url: String): String {
        val request = Request.Builder().url(url).build()
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ICS feed returned HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty ICS response")
        }
    }
}
