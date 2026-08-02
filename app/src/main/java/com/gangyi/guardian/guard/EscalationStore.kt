package com.gangyi.guardian.guard

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 封锁状态的持久化。
 *
 * **为什么必须持久化**：如果封锁状态只存在内存里，用户从最近任务划掉守卫、
 * 或者重启手机，封锁就没了——"杀进程"直接成了绕过后门，
 * 这会击穿整个防自我欺骗的目的。
 *
 * **为什么用 SharedPreferences 而不是 Room**：
 * - 数据量极小（几个包名 × 几个时间戳），上 Room 要加实体+DAO+数据库版本迁移，不划算
 * - 轮询循环每秒要读封锁状态，SharedPreferences 内存缓存读取是同步的，Room 的 suspend 在这条热路径上很别扭
 * - 用独立文件 guardian_escalation，不污染 guardian_prefs（那边放的是"用户设置项"，这边是"运行时状态"）
 *
 * 线程安全：所有读写都在 synchronized 块里，内存 Map 为准，改动即写穿透到磁盘。
 */
class EscalationStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("guardian_escalation", Context.MODE_PRIVATE)

    /** pkg → 解锁时间戳（墙钟毫秒） */
    private val locks = HashMap<String, Long>()

    /** pkg → 本窗口内的触发时间戳列表 */
    private val hits = HashMap<String, MutableList<Long>>()

    /** pkg → 连续被封次数（用于递进加重），以及上次被封时间 */
    private val strikes = HashMap<String, Int>()
    private val lastLockAt = HashMap<String, Long>()

    init {
        load()
    }

    private fun load() {
        // JSON 损坏时宁可丢封锁状态也不能让服务崩，整体包 runCatching
        runCatching {
            JSONObject(sp.getString(KEY_LOCKS, "{}") ?: "{}").let { obj ->
                obj.keys().forEach { k -> locks[k] = obj.optLong(k, 0L) }
            }
            JSONObject(sp.getString(KEY_HITS, "{}") ?: "{}").let { obj ->
                obj.keys().forEach { k ->
                    val arr = obj.optJSONArray(k) ?: return@forEach
                    val list = ArrayList<Long>(arr.length())
                    for (i in 0 until arr.length()) list.add(arr.optLong(i, 0L))
                    hits[k] = list
                }
            }
            JSONObject(sp.getString(KEY_STRIKES, "{}") ?: "{}").let { obj ->
                obj.keys().forEach { k -> strikes[k] = obj.optInt(k, 0) }
            }
            JSONObject(sp.getString(KEY_LAST_LOCK, "{}") ?: "{}").let { obj ->
                obj.keys().forEach { k -> lastLockAt[k] = obj.optLong(k, 0L) }
            }
        }.onFailure {
            Log.w(TAG, "状态文件损坏，重置", it)
            locks.clear(); hits.clear(); strikes.clear(); lastLockAt.clear()
        }
    }

    private fun persist() {
        runCatching {
            val locksJson = JSONObject()
            locks.forEach { (k, v) -> locksJson.put(k, v) }
            val hitsJson = JSONObject()
            hits.forEach { (k, v) -> hitsJson.put(k, JSONArray().also { a -> v.forEach { t -> a.put(t) } }) }
            val strikesJson = JSONObject()
            strikes.forEach { (k, v) -> strikesJson.put(k, v) }
            val lastLockJson = JSONObject()
            lastLockAt.forEach { (k, v) -> lastLockJson.put(k, v) }
            sp.edit()
                .putString(KEY_LOCKS, locksJson.toString())
                .putString(KEY_HITS, hitsJson.toString())
                .putString(KEY_STRIKES, strikesJson.toString())
                .putString(KEY_LAST_LOCK, lastLockJson.toString())
                .apply()
        }.onFailure { Log.w(TAG, "写入状态失败", it) }
    }

    // ---- 封锁 ----

    @Synchronized
    fun lockUntil(pkg: String): Long = locks[pkg] ?: 0L

    @Synchronized
    fun setLock(pkg: String, until: Long, strikeCount: Int, at: Long) {
        locks[pkg] = until
        strikes[pkg] = strikeCount
        lastLockAt[pkg] = at
        hits.remove(pkg)   // 封锁生效即清空计数，解封后重新给满次机会
        persist()
    }

    @Synchronized
    fun clearLock(pkg: String) {
        if (locks.remove(pkg) != null) persist()
    }

    /** 清掉所有已到期的封锁，返回是否有变化。 */
    @Synchronized
    fun pruneExpiredLocks(now: Long): Boolean {
        val it = locks.entries.iterator()
        var changed = false
        while (it.hasNext()) {
            if (it.next().value <= now) { it.remove(); changed = true }
        }
        if (changed) persist()
        return changed
    }

    @Synchronized
    fun hasActiveLock(now: Long): Boolean = locks.values.any { it > now }

    @Synchronized
    fun activeLockCount(now: Long): Int = locks.values.count { it > now }

    // ---- 触发计数 ----

    /** 记一次触发，返回剔除过期后该包在窗口内的触发次数。 */
    @Synchronized
    fun recordHit(pkg: String, now: Long, windowMs: Long): Int {
        val list = hits.getOrPut(pkg) { ArrayList(4) }
        list.removeAll { now - it > windowMs }   // 滑动窗口：超窗的旧记录直接丢
        list.add(now)
        persist()
        return list.size
    }

    @Synchronized
    fun strikeCount(pkg: String): Int = strikes[pkg] ?: 0

    @Synchronized
    fun lastLockTime(pkg: String): Long = lastLockAt[pkg] ?: 0L

    @Synchronized
    fun resetStrike(pkg: String) {
        if (strikes.remove(pkg) != null || lastLockAt.remove(pkg) != null) persist()
    }

    private companion object {
        const val KEY_LOCKS = "locks"
        const val KEY_HITS = "hits"
        const val KEY_STRIKES = "strikes"
        const val KEY_LAST_LOCK = "last_lock"
        const val TAG = "GuardianEscalation"
    }
}
