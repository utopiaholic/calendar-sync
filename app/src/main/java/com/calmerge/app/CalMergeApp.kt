package com.calmerge.app

import android.app.Application
import androidx.work.Configuration
import com.calmerge.app.data.db.AppDatabase
import com.calmerge.app.settings.SettingsStore
import com.calmerge.app.sync.IcsSyncEngine
import com.calmerge.app.sync.SyncCoordinator
import com.calmerge.app.work.SyncScheduler
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CalMergeApp : Application(), Configuration.Provider {

    val db: AppDatabase by lazy { AppDatabase.get(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    /** NFR-1: the only network traffic is HTTPS GETs to the user-supplied ICS hosts. */
    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator(db = db, icsEngine = IcsSyncEngine(okHttp, db), settings = settings)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        val intervalMinutes = settings.syncIntervalMinutes.value
        if (intervalMinutes > 0) {
            SyncScheduler.ensurePeriodicSync(this, intervalMinutes)
        }
    }
}
