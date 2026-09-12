package com.gangyi.guardian.data.db

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        get() = appDao.observeAll().map { list -> list.map { it.packageName } }

    suspend fun listMonitoredPackages(): List<String> = appDao.listPackages()

    suspend fun addApp(pkg: String) { appDao.insert(MonitoredApp(pkg)) }
    suspend fun removeApp(pkg: String) { appDao.delete(pkg) }

    // ---- Keyword ----
    val keywords: Flow<List<String>>
        get() = keywordDao.observeAll().map { list -> list.map { it.text } }

    suspend fun listKeywords(): List<String> = keywordDao.listTexts()
    suspend fun addKeyword(text: String) { keywordDao.insert(Keyword(text)) }
    suspend fun removeKeyword(text: String) { keywordDao.delete(text) }

    // ---- Reminder ----
    val reminders: Flow<List<Reminder>> get() = reminderDao.observeAll()

    suspend fun addReminder(text: String) { reminderDao.insert(Reminder(text = text)) }
    suspend fun updateCustomReminder(id: Long, text: String) { reminderDao.updateCustom(id, text) }
    suspend fun deleteCustomReminder(id: Long) { reminderDao.deleteCustom(id) }

    // ---- TriggerLog ----
    suspend fun logTrigger(
        packageName: String?,
        triggerType: String,
        keyword: String? = null
    ): Long = logDao.insert(TriggerLog(packageName = packageName, triggerType = triggerType, keyword = keyword))

    suspend fun markDecision(id: Long, decision: String) {
        logDao.setDecision(id, decision, System.currentTimeMillis())
    }

    fun observeLogsSince(startMs: Long): Flow<List<TriggerLog>> = logDao.observeSince(startMs)
    suspend fun allLogs(): List<TriggerLog> = logDao.listAll()
    suspend fun lastTimestamp(): Long? = logDao.lastTimestamp()

    /** 只保留最近 90 天的记录，别让触发日志无限长。 */
    suspend fun pruneOldLogs() {
        logDao.deleteBefore(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)
    }
}
