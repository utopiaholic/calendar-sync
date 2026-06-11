package dev.albert.calmerge

import android.app.Application
import androidx.work.Configuration
import dev.albert.calmerge.data.db.AppDatabase
import dev.albert.calmerge.sync.IcsSyncEngine
import dev.albert.calmerge.sync.SyncCoordinator
import dev.albert.calmerge.work.SyncScheduler
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CalMergeApp : Application(), Configuration.Provider {

    val db: AppDatabase by lazy { AppDatabase.get(this) }

    /** NFR-1: the only network traffic is HTTPS GETs to the user-supplied ICS hosts. */
    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator(db = db, icsEngine = IcsSyncEngine(okHttp, db))
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        SyncScheduler.ensurePeriodicSync(this)
    }
}
