package com.gangyi.guardian.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 单个学习封锁时段 */
data class StudyBlock(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    val startTotalMinutes: Int get() = startHour * 60 + startMinute
    val endTotalMinutes: Int get() = endHour * 60 + endMinute

    fun startTimeStr(): String =
        "${startHour.toString().padStart(2, '0')}:${startMinute.toString().padStart(2, '0')}"
    fun endTimeStr(): String =
        "${endHour.toString().padStart(2, '0')}:${endMinute.toString().padStart(2, '0')}"

    companion object {
        fun fromJson(obj: JSONObject): StudyBlock = StudyBlock(
            startHour = obj.optInt("sh", 8),
            startMinute = obj.optInt("sm", 0),
            endHour = obj.optInt("eh", 12),
            endMinute = obj.optInt("em", 0)
        )

        fun toJson(block: StudyBlock): JSONObject = JSONObject().apply {
            put("sh", block.startHour)
            put("sm", block.startMinute)
            put("eh", block.endHour)
            put("em", block.endMinute)
        }
    }
}

/**
 * 轻量配置的本地存储。监控 App / 关键词已迁移到 Room（见 GuardianRepository），
 * 这里只留服务开关、各项秒数、加密与引导标记这类真正的"设置项"。
 */
class MonitorPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)

    /** 监控总开关（用户期望服务是否运行） */
    var serviceEnabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 同一应用触发后的冷却秒数，默认 30 秒 */
    var cooldownSeconds: Int
        get() = sp.getInt(KEY_COOLDOWN, 30)
        set(value) = sp.edit().putInt(KEY_COOLDOWN, value).apply()

    /** 屏幕文字扫描间隔秒数，默认 1.5 秒。越小越灵敏越费电 */
    var screenScanIntervalSeconds: Float
        get() = sp.getFloat(KEY_SCAN_INTERVAL, 1.5f)
        set(value) = sp.edit().putFloat(KEY_SCAN_INTERVAL, value).apply()

    /** 弹窗倒计时秒数，默认 5 秒。倒计时结束才能点"我清醒了" */
    var overlayCountdownSeconds: Int
        get() = sp.getInt(KEY_OVERLAY_COUNTDOWN, 5)
        set(value) = sp.edit().putInt(KEY_OVERLAY_COUNTDOWN, value).apply()

    /** 关键词加密模式开关 */
    var keywordsEncrypted: Boolean
        get() = sp.getBoolean(KEY_KW_ENCRYPTED, false)
        set(value) = sp.edit().putBoolean(KEY_KW_ENCRYPTED, value).apply()

    /** 关键词加密密码的 hash（SHA-256 hex）。空字符串表示未设密码 */
    var keywordPasswordHash: String
        get() = sp.getString(KEY_KW_PASSWORD_HASH, "") ?: ""
        set(value) = sp.edit().putString(KEY_KW_PASSWORD_HASH, value).apply()

    /** 用户是否已经走完一次权限引导（不论是否完整授权，跳过/完成都算） */
    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /** 提醒语模式：MODE_RANDOM = 每次随机抽；MODE_FIXED = 固定用某一条 */
    var reminderMode: String
        get() = sp.getString(KEY_REMINDER_MODE, MODE_RANDOM) ?: MODE_RANDOM
        set(value) = sp.edit().putString(KEY_REMINDER_MODE, value).apply()

    /** 固定提醒语对应的 Reminder id（Room 自增主键）；-1 表示未选 */
    var fixedReminderId: Long
        get() = sp.getLong(KEY_FIXED_REMINDER_ID, -1L)
        set(value) = sp.edit().putLong(KEY_FIXED_REMINDER_ID, value).apply()

    /** 意图声明模式下，每次使用的默认时长（秒），默认 10 分钟 */
    var defaultTimeLimitSeconds: Int
        get() = sp.getInt(KEY_DEFAULT_TIME_LIMIT, 600)
        set(value) = sp.edit().putInt(KEY_DEFAULT_TIME_LIMIT, value).apply()

    /** 到时后每次续时增加多少秒，默认 180 秒（3 分钟） */
    var extensionSeconds: Int
        get() = sp.getInt(KEY_EXTENSION_SECONDS, 180)
        set(value) = sp.edit().putInt(KEY_EXTENSION_SECONDS, value).apply()

    /** 单次会话最多续时次数，默认 2 次 */
    var maxExtensionCount: Int
        get() = sp.getInt(KEY_MAX_EXTENSIONS, 2)
        set(value) = sp.edit().putInt(KEY_MAX_EXTENSIONS, value).apply()

    /** 单 App 每日使用时长上限（分钟），0 = 不限制，默认 0 */
    var dailyLimitMinutes: Int
        get() = sp.getInt(KEY_DAILY_LIMIT_MINUTES, 0)
        set(value) = sp.edit().putInt(KEY_DAILY_LIMIT_MINUTES, value).apply()

    /** 学习时段封锁是否开启 */
    var studyBlockEnabled: Boolean
        get() = sp.getBoolean(KEY_STUDY_BLOCK_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_STUDY_BLOCK_ENABLED, value).apply()

    /** 多时段学习封锁列表（JSON 序列化） */
    var studyBlocks: List<StudyBlock>
        get() {
            val json = sp.getString(KEY_STUDY_BLOCKS, null)
            if (!json.isNullOrBlank()) {
                return try {
                    parseBlocksJson(json)
                } catch (_: Exception) { emptyList() }
            }
            // 迁移旧版单时段数据
            return migrateOldSingleBlock()
        }
        set(value) {
            val json = JSONArray().apply {
                value.forEach { put(StudyBlock.toJson(it)) }
            }.toString()
            sp.edit().putString(KEY_STUDY_BLOCKS, json).apply()
            // 清除旧版字段避免再次触发迁移
            sp.edit().remove(KEY_OLD_STUDY_BLOCK_START_HOUR)
                .remove(KEY_OLD_STUDY_BLOCK_START_MINUTE)
                .remove(KEY_OLD_STUDY_BLOCK_END_HOUR)
                .remove(KEY_OLD_STUDY_BLOCK_END_MINUTE)
                .apply()
        }

    /** 旧版单时段 → 多时段迁移。只执行一次，之后旧 key 被清除。 */
    private fun migrateOldSingleBlock(): List<StudyBlock> {
        val sh = sp.getInt(KEY_OLD_STUDY_BLOCK_START_HOUR, -1)
        if (sh < 0) return emptyList()
        val sm = sp.getInt(KEY_OLD_STUDY_BLOCK_START_MINUTE, 0)
        val eh = sp.getInt(KEY_OLD_STUDY_BLOCK_END_HOUR, -1)
        if (eh < 0) return emptyList()
        val em = sp.getInt(KEY_OLD_STUDY_BLOCK_END_MINUTE, 0)
        val block = StudyBlock(sh, sm, eh, em)
        // 写入新格式并清除旧 key
        studyBlocks = listOf(block)
        return listOf(block)
    }

    private fun parseBlocksJson(json: String): List<StudyBlock> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            StudyBlock.fromJson(arr.getJSONObject(i))
        }
    }

    private companion object {
        const val KEY_ENABLED = "service_enabled"
        const val KEY_COOLDOWN = "cooldown_seconds"
        const val KEY_SCAN_INTERVAL = "screen_scan_interval"
        const val KEY_OVERLAY_COUNTDOWN = "overlay_countdown"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_REMINDER_MODE = "reminder_mode"
        const val KEY_FIXED_REMINDER_ID = "fixed_reminder_id"
        const val KEY_KW_ENCRYPTED = "kw_encrypted"
        const val KEY_KW_PASSWORD_HASH = "kw_password_hash"
        const val KEY_DEFAULT_TIME_LIMIT = "default_time_limit"
        const val KEY_EXTENSION_SECONDS = "extension_seconds"
        const val KEY_MAX_EXTENSIONS = "max_extensions"
        const val KEY_DAILY_LIMIT_MINUTES = "daily_limit_minutes"
        const val KEY_STUDY_BLOCK_ENABLED = "study_block_enabled"
        const val KEY_STUDY_BLOCKS = "study_blocks_json"

        // 旧版单时段 key（只用于一次性迁移，迁移后被清除）
        const val KEY_OLD_STUDY_BLOCK_START_HOUR = "study_block_start_hour"
        const val KEY_OLD_STUDY_BLOCK_START_MINUTE = "study_block_start_minute"
        const val KEY_OLD_STUDY_BLOCK_END_HOUR = "study_block_end_hour"
        const val KEY_OLD_STUDY_BLOCK_END_MINUTE = "study_block_end_minute"
    }
}

const val MODE_RANDOM = "MODE_RANDOM"
const val MODE_FIXED = "MODE_FIXED"
