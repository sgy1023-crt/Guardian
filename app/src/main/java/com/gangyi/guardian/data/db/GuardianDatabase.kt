package com.gangyi.guardian.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gangyi.guardian.overlay.DEFAULT_REMINDERS

@Database(
    entities = [MonitoredApp::class, Keyword::class, Reminder::class, TriggerLog::class],
    version = 2,
    exportSchema = false
)
abstract class GuardianDatabase : RoomDatabase() {

    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun keywordDao(): KeywordDao
    abstract fun reminderDao(): ReminderDao
    abstract fun triggerLogDao(): TriggerLogDao

    companion object {
        @Volatile private var instance: GuardianDatabase? = null

        /** v1 → v2：触发记录加 decision 列（用户在弹窗上的决定），老数据保持 null。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trigger_logs ADD COLUMN decision TEXT")
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
