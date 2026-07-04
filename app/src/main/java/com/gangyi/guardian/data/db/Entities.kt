package com.gangyi.guardian.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_apps")
data class MonitoredApp(
    @PrimaryKey val packageName: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "keywords")
data class Keyword(
    @PrimaryKey val text: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val builtin: Boolean = false
)

@Entity(tableName = "trigger_logs")
data class TriggerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String?,
    val triggerType: String,
    val keyword: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val dismissedAt: Long? = null
) {
    companion object {
        const val TYPE_APP = "APP"
        const val TYPE_CLIPBOARD = "CLIPBOARD"
    }
}
