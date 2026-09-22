package com.gangyi.guardian.guard

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 封锁 / 通行证 / 退出宽限的持久化。
 *
 * **为什么必须持久化**：如果封锁状态只存在内存里，用户从最近任务划掉守卫、
 * 或者重启手机，封锁就没了——"杀进程"直接成了绕过后门。
 *
 * **为什么用 SharedPreferences 而不是 Room**：
 * - 数据量极小（几个包名 × 几个时间戳）
 * - 轮询循环每秒要读封锁状态，SharedPreferences 内存缓存读取是同步的
 * - 独立文件 guardian_escalation，不污染 guardian_prefs（那边是用户设置项，这边是运行时状态）
 *
 * 记的都是"用户做出的决定"，不是弹窗次数：
 * - continues：窗口内点"继续"的时间戳列表（升级封锁 + 递增停顿都按它算）
 * - passes：点"继续"换来的通行证到期时间
 * - exitGrace：点"退出"但无法送回桌面时，给用户自己离开的宽限到期时间
 * - locks / strikes / lastLockAt：封锁到期时间、连续被封次数（递进加重）、上次被封时间
 *
 * 线程安全：所有读写都在 synchronized 块里，内存 Map 为准，改动即写穿透到磁盘。
 */
class EscalationStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("guardian_escalation", Context.MODE_PRIVATE)

    private val locks = HashMap<String, Long>()
    private val continues = HashMap<String, MutableList<Long>>()
    private val strikes = HashMap<String, Int>()
    private val lastLockAt = HashMap<String, Long>()
    private val passes = HashMap<String, Long>()
    private val exitGrace = HashMap<String, Long>()

    init {
        load()
    }

    private fun load() {
        // JSON 损坏时宁可丢状态也不能让服务崩，整体包 runCatching
        runCatching {
            readLongMap(KEY_LOCKS, locks)
            readLongMap(KEY_PASSES, passes)
            readLongMap(KEY_EXIT_GRACE, exitGrace)
            readLongMap(KEY_LAST_LOCK, lastLockAt)
            JSONObject(sp.getString(KEY_STRIKES, "{}") ?: "{}").let { obj ->
                obj.keys().forEach { k -> strikes[k] = obj.optInt(k, 0) }
            }
            JSONObject(sp.getString(KEY_CONTINUES, "{}") ?: "{}").let { obj ->
                obj.keys().forEach { k ->
                    val arr = obj.optJSONArray(k) ?: return@forEach
                    val list = ArrayList<Long>(arr.length())
                    for (i in 0 until arr.length()) list.add(arr.optLong(i, 0L))
                    continues[k] = list
                }
            }
        }.onFailure {
            Log.w(TAG, "状态文件损坏，重置", it)
            locks.clear(); continues.clear(); strikes.clear()
            lastLockAt.clear(); passes.clear(); exitGrace.clear()
        }
    }

    private fun readLongMap(key: String, into: MutableMap<String, Long>) {
        val obj = JSONObject(sp.getString(key, "{}") ?: "{}")
        obj.keys().forEach { k -> into[k] = obj.optLong(k, 0L) }
    }

    private fun persist() {
        runCatching {
            val continuesJson = JSONObject()
            continues.forEach { (k, v) ->
                continuesJson.put(k, JSONArray().also { a -> v.forEach { t -> a.put(t) } })
            }
            sp.edit()
                .putString(KEY_LOCKS, JSONObject(locks as Map<*, *>).toString())
                .putString(KEY_PASSES, JSONObject(passes as Map<*, *>).toString())
                .putString(KEY_EXIT_GRACE, JSONObject(exitGrace as Map<*, *>).toString())
                .putString(KEY_LAST_LOCK, JSONObject(lastLockAt as Map<*, *>).toString())
                .putString(KEY_STRIKES, JSONObject(strikes as Map<*, *>).toString())
                .putString(KEY_CONTINUES, continuesJson.toString())
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
        // 封锁生效即清空本轮计数和通行/宽限，解封后重新给满次机会
        continues.remove(pkg)
        passes.remove(pkg)
        exitGrace.remove(pkg)
        persist()
    }

    /** 提前解除某个包的封锁（紧急出口用）。strike 记忆保留，下次再封照样加重。 */
    @Synchronized
    fun clearLock(pkg: String) {
        if (locks.remove(pkg) != null) persist()
    }

    /** 清掉所有已到期的封锁 / 通行证，返回是否有变化。 */
    @Synchronized
    fun pruneExpired(now: Long): Boolean {
        var changed = false
        val li = locks.entries.iterator()
        while (li.hasNext()) if (li.next().value <= now) { li.remove(); changed = true }
        val pi = passes.entries.iterator()
        while (pi.hasNext()) if (pi.next().value <= now) { pi.remove(); changed = true }
        if (changed) persist()
        return changed
    }

    @Synchronized
    fun activeLocks(now: Long): Map<String, Long> = locks.filterValues { it > now }

    @Synchronized
    fun strikeCount(pkg: String): Int = strikes[pkg] ?: 0

    @Synchronized
    fun lastLockTime(pkg: String): Long = lastLockAt[pkg] ?: 0L

    // ---- "继续"计数 ----

    /** 记一次"继续"，返回剔除过期后该包在窗口内的次数。 */
    @Synchronized
    fun addContinue(pkg: String, now: Long, windowMs: Long): Int {
        val list = continues.getOrPut(pkg) { ArrayList(4) }
        list.removeAll { now - it > windowMs }
        list.add(now)
        persist()
        return list.size
    }

    /** 窗口内的"继续"次数（只读，不写盘）。 */
    @Synchronized
    fun continuesInWindow(pkg: String, now: Long, windowMs: Long): Int =
        continues[pkg]?.count { now - it <= windowMs } ?: 0

    // ---- 通行证 ----

    @Synchronized
    fun passUntil(pkg: String): Long = passes[pkg] ?: 0L

    @Synchronized
    fun setPass(pkg: String, until: Long) {
        passes[pkg] = until
        exitGrace.remove(pkg)
        persist()
    }

    // ---- 退出宽限 ----

    @Synchronized
    fun graceUntil(pkg: String): Long = exitGrace[pkg] ?: 0L

    @Synchronized
    fun setGrace(pkg: String, until: Long) {
        exitGrace[pkg] = until
        persist()
    }

    @Synchronized
    fun clearGrace(pkg: String) {
        if (exitGrace.remove(pkg) != null) persist()
    }

    /** 清掉除 keep 之外所有包的宽限（人已经离开那个 App = 守信）。 */
    @Synchronized
    fun clearGraceExcept(keep: String?) {
        if (exitGrace.isEmpty()) return
        val it = exitGrace.keys.iterator()
        var changed = false
        while (it.hasNext()) if (it.next() != keep) { it.remove(); changed = true }
        if (changed) persist()
    }

    private companion object {
        const val KEY_LOCKS = "locks"
        const val KEY_CONTINUES = "continues"
        const val KEY_STRIKES = "strikes"
        const val KEY_LAST_LOCK = "last_lock"
        const val KEY_PASSES = "passes"
        const val KEY_EXIT_GRACE = "exit_grace"
        const val TAG = "GuardianEscalation"
    }
}
