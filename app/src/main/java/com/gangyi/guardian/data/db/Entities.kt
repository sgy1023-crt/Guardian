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

/**
 * 一次停顿弹窗的记录。弹出时插入，用户做出决定后回填 decision + dismissedAt。
 * decision 为 null = 弹窗还挂着，或进程被杀没来得及记。
 */
@Entity(tableName = "trigger_logs")
data class TriggerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String?,
    val triggerType: String,
    val keyword: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val dismissedAt: Long? = null,
    val decision: String? = null
) {
    companion object {
        const val TYPE_APP = "APP"
        const val TYPE_CLIPBOARD = "CLIPBOARD"

        /** 点了"继续"，拿到通行证 */
        const val DECISION_CONTINUE = "CONTINUE"
        /** 点了"退出" */
        const val DECISION_EXIT = "EXIT"
        /** 没做决定就离开了（按 Home / 息屏） */
        const val DECISION_LEFT = "LEFT"
        /** 点"继续"时次数用完，被封锁 */
        const val DECISION_LOCKED = "LOCKED"
        /** 封锁期间紧急解除（要过密码/等待，会被记一笔） */
        const val DECISION_EMERGENCY = "EMERGENCY"

        /** 算"拦下"的决定：退出和离开 */
        fun isBlocked(decision: String?): Boolean =
            decision == DECISION_EXIT || decision == DECISION_LEFT
    }
}
