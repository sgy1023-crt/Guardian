package com.gangyi.guardian.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gangyi.guardian.overlay.DEFAULT_REMINDERS

@Database(
    entities = [MonitoredApp::class, Keyword::class, Reminder::class, TriggerLog::class],
    version = 1,
    exportSchema = false
)
abstract class GuardianDatabase : RoomDatabase() {

    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun keywordDao(): KeywordDao
    abstract fun reminderDao(): ReminderDao
    abstract fun triggerLogDao(): TriggerLogDao

    companion object {
        @Volatile private var instance: GuardianDatabase? = null

        fun get(context: Context): GuardianDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GuardianDatabase::class.java,
                    "guardian.db"
                )
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
