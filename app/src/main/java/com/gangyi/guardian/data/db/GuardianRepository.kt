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

    suspend fun addApp(pkg: String) { appDao.insert(MonitoredApp(pkg)) }
    suspend fun removeApp(pkg: String) { appDao.delete(pkg) }

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
}
