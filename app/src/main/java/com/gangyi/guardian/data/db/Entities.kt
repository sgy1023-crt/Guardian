package com.gangyi.guardian.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_apps")
data class MonitoredApp(
    @PrimaryKey val packageName: String,
    val addedAt: Long = System.currentTimeMillis(),
    /** 该 App 独立每日使用时长上限（分钟），0 = 不限制，默认 0 */
    val dailyLimitMinutes: Int = 0,
    /** 该 App 自定义学习时段（JSON 数组），null = 使用全局设置 */
    val studyBlocksJson: String? = null,
    /** 是否使用自定义学习时段而非全局设置 */
    val useCustomStudyBlocks: Boolean = false
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

/** 意图声明 + 限时使用会话。打开被监控 App 时用户填理由、选时长，到时自动退出。 */
@Entity(tableName = "intent_sessions")
data class IntentSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 被监控的 App 包名 */
    val packageName: String,
    /** 用户自己写的打开理由 */
    val reason: String,
    /** 本次允许使用的秒数（用户选的预设或自定义） */
    val timeLimitSeconds: Int,
    /** 会话开始时间戳 */
    val startTime: Long = System.currentTimeMillis(),
    /** 会话结束时间戳（正常到时/主动退出/被拦截），null 表示进行中 */
    val endedAt: Long? = null,
    /** 本次会话续时次数 */
    val extensionCount: Int = 0,
    /** 会话状态 */
    val status: String = STATUS_ACTIVE
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_COMPLETED = "COMPLETED"   // 时间到，用户选了退出
        const val STATUS_EXTENDED = "EXTENDED"      // 用户续时了（仍在进行）
        const val STATUS_CANCELLED = "CANCELLED"    // 用户在意图声明阶段放弃了
        const val STATUS_EXPIRED = "EXPIRED"        // 会话超时未操作自动结束
    }
}
