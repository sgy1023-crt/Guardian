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

    @Query("SELECT packageName FROM monitored_apps")
    suspend fun listPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: MonitoredApp)

    @Query("DELETE FROM monitored_apps WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
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

data class AppCount(val packageName: String, val count: Int)
