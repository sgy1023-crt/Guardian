package com.gangyi.guardian.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<MonitoredApp>>

    @Query("SELECT * FROM monitored_apps ORDER BY addedAt DESC")
    suspend fun getAll(): List<MonitoredApp>

    @Query("SELECT * FROM monitored_apps WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): MonitoredApp?

    @Query("SELECT packageName FROM monitored_apps")
    suspend fun listPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: MonitoredApp)

    @Query("DELETE FROM monitored_apps WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("UPDATE monitored_apps SET dailyLimitMinutes = :minutes WHERE packageName = :pkg")
    suspend fun updateDailyLimit(pkg: String, minutes: Int)
}

@Dao
interface KeywordDao {
    @Query("SELECT * FROM keywords ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Keyword>>

    @Query("SELECT text FROM keywords")
    suspend fun listTexts(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(keyword: Keyword)

    @Query("DELETE FROM keywords WHERE text = :text")
    suspend fun delete(text: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY builtin DESC, id ASC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT text FROM reminders")
    suspend fun listTexts(): List<String>

    @Query("SELECT text FROM reminders")
    fun listTextsSync(): List<String>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    fun getByIdSync(id: Long): Reminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder)

    @Query("UPDATE reminders SET text = :newText WHERE id = :id AND builtin = 0")
    suspend fun updateCustom(id: Long, newText: String)

    @Query("DELETE FROM reminders WHERE id = :id AND builtin = 0")
    suspend fun deleteCustom(id: Long)
}

@Dao
interface TriggerLogDao {
    @Insert
    suspend fun insert(log: TriggerLog): Long

    @Query("SELECT * FROM trigger_logs WHERE timestamp >= :startMs ORDER BY timestamp DESC")
    suspend fun listSince(startMs: Long): List<TriggerLog>

    @Query("SELECT * FROM trigger_logs ORDER BY timestamp DESC")
    suspend fun listAll(): List<TriggerLog>

    @Query("SELECT packageName, COUNT(*) AS count FROM trigger_logs WHERE packageName IS NOT NULL AND timestamp >= :startMs GROUP BY packageName ORDER BY count DESC LIMIT :limit")
    fun topApps(startMs: Long, limit: Int): Flow<List<AppCount>>

    @Query("SELECT timestamp FROM trigger_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastTimestamp(): Long?
}

@Dao
interface IntentSessionDao {
    @Insert
    suspend fun insert(session: IntentSession): Long

    @Query("UPDATE intent_sessions SET status = :status, endedAt = :endedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, endedAt: Long = System.currentTimeMillis())

    @Query("UPDATE intent_sessions SET extensionCount = extensionCount + 1, timeLimitSeconds = timeLimitSeconds + :extraSeconds, status = :status WHERE id = :id")
    suspend fun extendSession(id: Long, extraSeconds: Int, status: String = IntentSession.STATUS_EXTENDED)

    @Query("SELECT * FROM intent_sessions WHERE packageName = :packageName AND status = :status ORDER BY startTime DESC LIMIT 1")
    suspend fun getActive(packageName: String, status: String = IntentSession.STATUS_ACTIVE): IntentSession?

    /** 清除所有进行中/续时状态的会话（服务启动时兜底，防止异常残留） */
    @Query("UPDATE intent_sessions SET status = 'EXPIRED', endedAt = :now WHERE status IN ('ACTIVE', 'EXTENDED')")
    suspend fun expireAllActive(now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM intent_sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun listRecent(limit: Int = 50): List<IntentSession>

    /** 今天针对某个 App 已有的意图声明次数 */
    @Query("SELECT COUNT(*) FROM intent_sessions WHERE packageName = :pkg AND startTime >= :todayStart")
    suspend fun countTodayByPackage(pkg: String, todayStart: Long): Int

    /** 今天某个 App 累计使用秒数 */
    @Query("SELECT COALESCE(SUM(timeLimitSeconds), 0) FROM intent_sessions WHERE packageName = :pkg AND startTime >= :todayStart AND status IN ('COMPLETED', 'EXPIRED')")
    suspend fun totalSecondsTodayByPackage(pkg: String, todayStart: Long): Int
}

data class AppCount(val packageName: String, val count: Int)
