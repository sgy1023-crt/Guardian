package com.gangyi.guardian.guard

/**
 * 递增停顿的入口（保留这个名字是为了让"越挣扎、等越久"这件事有个明确的落点）。
 *
 * 实际计算在 [EscalationTracker.nextCountdownSeconds]：按滑动窗口内"继续"的次数翻倍。
 * 旧版按"两次弹窗相隔多久"记连击，而弹窗又是定时重弹的，人不动也会连击，
 * 关键词场景直接死循环——所以连击的定义改成了"用户真的点了继续"。
 */
object CountdownEscalator {

    /** 这次弹窗该停顿多少秒。 */
    fun nextCountdown(pkg: String?): Int = EscalationTracker.nextCountdownSeconds(pkg)
}
