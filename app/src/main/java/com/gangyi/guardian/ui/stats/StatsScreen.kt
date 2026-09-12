package com.gangyi.guardian.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.data.db.TriggerLog
import com.gangyi.guardian.ui.components.EmptyState
import com.gangyi.guardian.ui.components.GuardianCard
import com.gangyi.guardian.ui.components.RowDivider
import com.gangyi.guardian.ui.components.ScreenHeader
import com.gangyi.guardian.ui.components.SectionLabel
import com.gangyi.guardian.ui.components.SegmentedControl
import com.gangyi.guardian.ui.components.StatTile
import com.gangyi.guardian.ui.home.computeStreak
import com.gangyi.guardian.ui.home.startOfToday
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianDanger
import com.gangyi.guardian.ui.theme.GuardianSuccess
import com.gangyi.guardian.ui.theme.GuardianSurface2
import com.gangyi.guardian.ui.theme.GuardianSurface3
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
import com.gangyi.guardian.util.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private data class DayBucket(val label: String, val total: Int, val blocked: Int)

/**
 * 统计页。最重要的数字不是"弹了几次"，是"弹了之后你多少次没继续"——拦下率。
 * 柱状图每根柱子分两段：下面绿色是拦下的，上面橙色是继续的，一眼看出趋势。
 */
@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val repo = remember { GuardianRepository(context) }

    var range by remember { mutableIntStateOf(0) }   // 0 = 7 天, 1 = 30 天
    val days = if (range == 0) 7 else 30
    val todayStart = remember { startOfToday() }
    val rangeStart = todayStart - (days - 1) * TimeUnit.DAYS.toMillis(1)
    val logs by repo.observeLogsSince(rangeStart).collectAsState(initial = emptyList())

    var streak by remember { mutableIntStateOf(0) }
    LaunchedEffect(logs.size) {
        streak = withContext(Dispatchers.IO) { computeStreak(repo, todayStart) }
    }

    val total = logs.size
    val decided = logs.count { it.decision != null }
    val blocked = logs.count { TriggerLog.isBlocked(it.decision) }
    val continued = logs.count { it.decision == TriggerLog.DECISION_CONTINUE }
    val locked = logs.count { it.decision == TriggerLog.DECISION_LOCKED }
    val blockRate = if (decided == 0) null else blocked * 100 / decided

    val buckets = remember(logs, days) { bucketByDay(logs, rangeStart, days) }

    val topApps = remember(logs) {
        logs.filter { it.packageName != null }
            .groupBy { it.packageName!! }
            .map { (pkg, list) ->
                Triple(pkg, list.size, list.count { TriggerLog.isBlocked(it.decision) })
            }
            .sortedByDescending { it.second }
            .take(5)
    }
    var appLabels by remember { mutableStateOf(mapOf<String, String>()) }
    LaunchedEffect(topApps) {
        appLabels = withContext(Dispatchers.IO) {
            topApps.associate { it.first to InstalledApps.label(context, it.first) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        ScreenHeader("统计", "看看停顿点有没有真的帮到你")
        Spacer(Modifier.height(16.dp))

        SegmentedControl(listOf("近 7 天", "近 30 天"), range, onSelect = { range = it })
        Spacer(Modifier.height(16.dp))

        // ---- 拦下率主卡
        GuardianCard(glowColor = if (blockRate != null && blockRate >= 50) GuardianSuccess else GuardianAccent) {
            Text("拦下率", fontSize = 13.sp, color = GuardianTextDim)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (blockRate == null) "—" else "$blockRate",
                    fontSize = 52.sp, fontWeight = FontWeight.Bold,
                    color = when {
                        blockRate == null -> GuardianTextFaint
                        blockRate >= 50 -> GuardianSuccess
                        else -> GuardianAccent
                    },
                    lineHeight = 56.sp
                )
                if (blockRate != null) {
                    Spacer(Modifier.width(4.dp))
                    Text("%", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GuardianTextDim, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            Text(
                when {
                    blockRate == null -> "还没有做过决定的停顿。弹出停顿点后选「退出」或直接离开，就算拦下一次。"
                    blockRate >= 70 -> "停顿点弹出后，十次里有七次以上你选择了离开。这就是它存在的意义。"
                    blockRate >= 50 -> "一半以上的冲动被你拦下了。继续保持。"
                    else -> "多数时候你还是选择了继续。可以试试把强度调高一档。"
                },
                fontSize = 13.sp, color = GuardianTextDim, lineHeight = 19.sp
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("停顿", "$total", Modifier.weight(1f), unit = "次")
                StatTile("拦下", "$blocked", Modifier.weight(1f), unit = "次", accent = GuardianSuccess)
                StatTile("继续", "$continued", Modifier.weight(1f), unit = "次", accent = GuardianAccent)
                StatTile("封锁", "$locked", Modifier.weight(1f), unit = "次", accent = if (locked > 0) GuardianDanger else GuardianText)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- 趋势
        SectionLabel("每日趋势")
        GuardianCard {
            if (total == 0) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("这段时间没有停顿记录", fontSize = 13.sp, color = GuardianTextFaint)
                }
            } else {
                StackedBarChart(buckets, showEveryLabel = days <= 7)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(GuardianSuccess, "拦下")
                    LegendDot(GuardianAccent, "继续 / 封锁")
                    LegendDot(GuardianSurface3, "未决定")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- 高频应用
        SectionLabel("最常触发")
        if (topApps.isEmpty()) {
            GuardianCard {
                EmptyState(Icons.Rounded.BarChart, "暂无数据", "有停顿记录后这里会列出最常触发的应用", Modifier.padding(vertical = 0.dp))
            }
        } else {
            GuardianCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                val max = topApps.maxOf { it.second }.coerceAtLeast(1)
                topApps.forEachIndexed { i, (pkg, count, blockedCount) ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(24.dp).clip(CircleShape).background(GuardianSurface2),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GuardianAccent)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                appLabels[pkg] ?: pkg,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianText,
                                modifier = Modifier.weight(1f)
                            )
                            Text("$count 次", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GuardianText)
                            Spacer(Modifier.width(6.dp))
                            Text("拦下 $blockedCount", fontSize = 11.sp, color = GuardianTextFaint)
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 36.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(GuardianSurface2)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(count.toFloat() / max)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(GuardianAccent)
                            )
                        }
                    }
                    if (i < topApps.lastIndex) RowDivider(16.dp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- 连续清醒
        GuardianCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("连续清醒", fontSize = 13.sp, color = GuardianTextDim)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (streak == 0) "今天有过停顿，明天再来" else "连续 $streak 天没有触发任何停顿",
                        fontSize = 13.sp, color = GuardianTextFaint
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$streak", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = GuardianAccent, lineHeight = 40.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("天", fontSize = 14.sp, color = GuardianTextDim, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = GuardianTextFaint)
    }
}

/** 堆叠柱状图：下段拦下（绿），上段继续/封锁（橙），未决定的灰。 */
@Composable
private fun StackedBarChart(buckets: List<DayBucket>, showEveryLabel: Boolean) {
    val maxCount = buckets.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    Box(Modifier.fillMaxWidth().height(150.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val n = buckets.size
            val w = size.width / n
            val barW = (w * if (n > 7) 0.62f else 0.5f)
            val topPad = 8f
            val bottomPad = 26f
            val chartH = size.height - topPad - bottomPad
            val radius = CornerRadius(barW / 2.6f, barW / 2.6f)

            buckets.forEachIndexed { i, b ->
                val x = i * w + (w - barW) / 2
                if (b.total == 0) {
                    drawRoundRect(
                        color = GuardianSurface2,
                        topLeft = Offset(x, size.height - bottomPad - 3f),
                        size = Size(barW, 3f),
                        cornerRadius = radius
                    )
                    return@forEachIndexed
                }
                val unit = chartH / maxCount
                val blockedH = b.blocked * unit
                val restH = (b.total - b.blocked) * unit
                val bottom = size.height - bottomPad
                // 上段（继续/未决定）
                if (restH > 0f) {
                    drawRoundRect(
                        color = GuardianAccent,
                        topLeft = Offset(x, bottom - blockedH - restH),
                        size = Size(barW, restH + if (blockedH > 0f) radius.y else 0f),
                        cornerRadius = radius
                    )
                }
                // 下段（拦下）
                if (blockedH > 0f) {
                    drawRoundRect(
                        color = GuardianSuccess,
                        topLeft = Offset(x, bottom - blockedH),
                        size = Size(barW, blockedH),
                        cornerRadius = radius
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            buckets.forEachIndexed { i, b ->
                val show = showEveryLabel || i == 0 || i == buckets.lastIndex || i % 7 == 6
                Text(
                    if (show) b.label else "",
                    fontSize = 10.sp,
                    color = GuardianTextFaint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun bucketByDay(logs: List<TriggerLog>, rangeStart: Long, days: Int): List<DayBucket> {
    val dayMs = TimeUnit.DAYS.toMillis(1)
    val totals = IntArray(days)
    val blockedArr = IntArray(days)
    val cal = Calendar.getInstance()
    logs.forEach { log ->
        cal.timeInMillis = log.timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val idx = ((cal.timeInMillis - rangeStart) / dayMs).toInt()
        if (idx in 0 until days) {
            totals[idx]++
            if (TriggerLog.isBlocked(log.decision)) blockedArr[idx]++
        }
    }
    val fmt = if (days <= 7) SimpleDateFormat("E", Locale.CHINESE) else SimpleDateFormat("M/d", Locale.CHINESE)
    return (0 until days).map { i ->
        DayBucket(fmt.format(Date(rangeStart + i * dayMs)), totals[i], blockedArr[i])
    }
}
