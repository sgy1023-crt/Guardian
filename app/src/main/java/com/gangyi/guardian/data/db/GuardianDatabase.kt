package com.gangyi.guardian.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gangyi.guardian.overlay.DEFAULT_REMINDERS

@Database(
    entities = [
        MonitoredApp::class, Keyword::class, Reminder::class,
        TriggerLog::class, IntentSession::class
    ],
    version = 2,
    exportSchema = false
)
abstract class GuardianDatabase : RoomDatabase() {

    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun keywordDao(): KeywordDao
    abstract fun reminderDao(): ReminderDao
    abstract fun triggerLogDao(): TriggerLogDao
    abstract fun intentSessionDao(): IntentSessionDao

    companion object {
        @Volatile private var instance: GuardianDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS intent_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        timeLimitSeconds INTEGER NOT NULL,
                        startTime INTEGER NOT NULL,
                        endedAt INTEGER,
                        extensionCount INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'ACTIVE'
                    )
                """.trimIndent())
            }
        }

        fun get(context: Context): GuardianDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GuardianDatabase::class.java,
                    "guardian.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(InitCallback())
                    .build()
                    .also { instance = it }
            }
        }
    }

    private class InitCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            DEFAULT_REMINDERS.forEach { text ->
                db.execSQL(
                    "INSERT OR IGNORE INTO reminders (text, builtin) VALUES (?, 1)",
                    arrayOf(text)
                )
            }
        }
    }
}
