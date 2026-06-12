package com.calmerge.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        CalendarSourceEntity::class,
        EventInstanceEntity::class,
        ConflictClusterEntity::class,
        ConflictMemberEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun calendarSourceDao(): CalendarSourceDao
    abstract fun eventInstanceDao(): EventInstanceDao
    abstract fun conflictDao(): ConflictDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 → v2: conflict cache tables (M3). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conflict_clusters` (" +
                        "`id` TEXT NOT NULL, `computedAtUtc` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conflict_members` (" +
                        "`clusterId` TEXT NOT NULL, `eventInstanceId` TEXT NOT NULL, " +
                        "PRIMARY KEY(`clusterId`, `eventInstanceId`), " +
                        "FOREIGN KEY(`clusterId`) REFERENCES `conflict_clusters`(`id`) ON DELETE CASCADE, " +
                        "FOREIGN KEY(`eventInstanceId`) REFERENCES `event_instances`(`id`) ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conflict_members_eventInstanceId` " +
                        "ON `conflict_members` (`eventInstanceId`)",
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calmerge.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
