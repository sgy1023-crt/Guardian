package com.gangyi.guardian.data.db

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch

class GuardianRepository(context: Context) {

    private val db = GuardianDatabase.get(context)
    val appDao = db.monitoredAppDao()
    val keywordDao = db.keywordDao()
    val reminderDao = db.reminderDao()
    val logDao = db.triggerLogDao()
    val sessionDao = db.intentSessionDao()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch { migrateFromPrefs(context) }
    }

    private suspend fun migrateFromPrefs(context: Context) {
        val sp = context.applicationContext
            .getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
        val migrated = sp.getBoolean("db_migrated", false)
        if (migrated) return

        val pkgs = sp.getStringSet("monitored_packages", emptySet()) ?: emptySet()
        pkgs.forEach { appDao.insert(MonitoredApp(it)) }
        val kws = sp.getStringSet("keywords", emptySet()) ?: emptySet()
        kws.forEach { keywordDao.insert(Keyword(it)) }
        sp.edit().putBoolean("db_migrated", true).apply()
    }

    // ---- App ----
    val monitoredPackages: Flow<List<String>>
        get() = appDao.observeAll().let { flow ->
            object : Flow<List<String>> {
                override suspend fun collect(collector: FlowCollector<List<String>>) {
                    flow.collect { collector.emit(it.map { a -> a.packageName }) }
                }
            }
        }

    suspend fun listMonitoredPackages(): List<String> = appDao.listPackages()

    suspend fun listMonitoredApps(): List<MonitoredApp> = appDao.getAll()

    suspend fun getMonitoredApp(pkg: String): MonitoredApp? = appDao.getByPackage(pkg)

    suspend fun addApp(pkg: String) { appDao.insert(MonitoredApp(pkg)) }
    suspend fun removeApp(pkg: String) { appDao.delete(pkg) }

    /** 获取某 App 的独立每日限额（分钟），0 = 不限 */
    suspend fun getAppDailyLimit(pkg: String): Int =
        appDao.getByPackage(pkg)?.dailyLimitMinutes ?: 0

    /** 设置某 App 的独立每日限额（分钟） */
    suspend fun setAppDailyLimit(pkg: String, minutes: Int) {
        appDao.updateDailyLimit(pkg, minutes)
    }

    // ---- Keyword ----
    val keywords: Flow<List<String>>
        get() = keywordDao.observeAll().let { flow ->
            object : Flow<List<String>> {
                override suspend fun collect(collector: FlowCollector<List<String>>) {
                    flow.collect { collector.emit(it.map { k -> k.text }) }
                }
            }
        }

    suspend fun listKeywords(): List<String> = keywordDao.listTexts()
    suspend fun addKeyword(text: String) { keywordDao.insert(Keyword(text)) }
    suspend fun removeKeyword(text: String) { keywordDao.delete(text) }

    // ---- Reminder ----
    val reminders: Flow<List<Reminder>> get() = reminderDao.observeAll()

    suspend fun listReminders(): List<String> = reminderDao.listTexts()
    fun listRemindersSync(): List<String> = reminderDao.listTextsSync()
    fun getReminderByIdSync(id: Long): Reminder? = reminderDao.getByIdSync(id)
    suspend fun addReminder(text: String) { reminderDao.insert(Reminder(text = text)) }
    suspend fun updateCustomReminder(id: Long, text: String) { reminderDao.updateCustom(id, text) }
    suspend fun deleteCustomReminder(id: Long) { reminderDao.deleteCustom(id) }

    // ---- TriggerLog ----
    suspend fun logTrigger(
        packageName: String?,
        triggerType: String,
        keyword: String? = null
    ): Long = logDao.insert(TriggerLog(packageName = packageName, triggerType = triggerType, keyword = keyword))

    fun observeTopApps(startMs: Long, limit: Int = 5): Flow<List<AppCount>> =
        logDao.topApps(startMs, limit)

    suspend fun listLogsSince(startMs: Long): List<TriggerLog> = logDao.listSince(startMs)
    suspend fun allLogs(): List<TriggerLog> = logDao.listAll()
    suspend fun lastTimestamp(): Long? = logDao.lastTimestamp()

    // ---- IntentSession ----

    /** 创建新会话（用户填完意图声明卡并点了"开始使用"）。返回会话 ID 供后续更新。 */
    suspend fun startSession(pkg: String, reason: String, timeLimitSeconds: Int): Long =
        sessionDao.insert(IntentSession(packageName = pkg, reason = reason, timeLimitSeconds = timeLimitSeconds))

    /** 结束会话（用户到时后选了"退出"或在意图声明卡点了"放弃"）。 */
    suspend fun finishSession(id: Long) {
        sessionDao.updateStatus(id, IntentSession.STATUS_COMPLETED)
    }

    /** 取消会话（用户在意图声明阶段放弃，从未真正进入 App）。 */
    suspend fun cancelSession(id: Long) {
        sessionDao.updateStatus(id, IntentSession.STATUS_CANCELLED)
    }

    /** 续时会话：增加 extraSeconds 秒，extensionCount + 1。 */
    suspend fun extendSession(id: Long, extraSeconds: Int) {
        sessionDao.extendSession(id, extraSeconds)
    }

    /** 获取当前进行中的会话（同一包名），没有返回 null。 */
    suspend fun getActiveSession(pkg: String): IntentSession? =
        sessionDao.getActive(pkg)

    /** 清掉所有残留的进行中会话（服务重启时兜底）。 */
    suspend fun expireAllActiveSessions() {
        sessionDao.expireAllActive()
    }

    /** 今天某个 App 的意图声明次数。 */
    suspend fun countTodaySessions(pkg: String, todayStart: Long): Int =
        sessionDao.countTodayByPackage(pkg, todayStart)

    /** 今天某个 App 累计使用秒数。 */
    suspend fun totalSecondsToday(pkg: String, todayStart: Long): Int =
        sessionDao.totalSecondsTodayByPackage(pkg, todayStart)
}
