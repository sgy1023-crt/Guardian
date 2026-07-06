package com.gangyi.guardian.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.data.db.AppCount
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
import com.gangyi.guardian.util.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { GuardianRepository(context) }

    var todayCount by remember { mutableIntStateOf(0) }
    var dailyCounts by remember { mutableStateOf(listOf<Pair<String, Int>>()) }
    var topApps by remember { mutableStateOf(listOf<AppCount>()) }
    var streak by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val todayStart = cal.timeInMillis
            val sevenDaysAgo = todayStart - 7 * TimeUnit.DAYS.toMillis(1)

            val logs = repo.listLogsSince(sevenDaysAgo)

            // today count
            todayCount = logs.count { it.timestamp >= todayStart }

            // daily counts for last 7 days
            val fmt = SimpleDateFormat("E", Locale.CHINESE)
            val counts = mutableMapOf<Long, Int>()
            for (i in 0 until 7) {
                counts[sevenDaysAgo + i * TimeUnit.DAYS.toMillis(1)] = 0
            }
            logs.forEach { log ->
                cal.timeInMillis = log.timestamp
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val dayStart = cal.timeInMillis
                counts[dayStart] = (counts[dayStart] ?: 0) + 1
            }
            dailyCounts = counts.entries.sortedBy { it.key }.map { (ms, c) ->
                Pair(fmt.format(Date(ms)), c)
            }

            // top apps
            topApps = repo.observeTopApps(sevenDaysAgo, 5).first()

            // streak
            streak = computeStreak(repo)
        }
        loaded = true
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize().background(GuardianBg), contentAlignment = Alignment.Center) {
            Text("加载中…", color = GuardianTextDim, fontSize = 14.sp)
        }
        return
    }

    val scrollState = rememberScrollState()
    val appLabels = remember(topApps) {
        val apps = InstalledApps.load(context)
        topApps.associate { it.packageName to (apps.find { a -> a.packageName == it.packageName }?.label ?: it.packageName) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianBg)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                tint = GuardianText, modifier = Modifier.size(28.dp).clickable { onBack() }
            )
            Spacer(Modifier.weight(1f))
            Text("统计", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GuardianText)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(28.dp))
        }
        Spacer(Modifier.height(28.dp))

        // 今日大数字
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("今日提醒", fontSize = 14.sp, color = GuardianTextDim)
                Spacer(Modifier.height(8.dp))
                Text(
                    "$todayCount",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = GuardianAccent
                )
                Spacer(Modifier.height(4.dp))
                Text("次", fontSize = 14.sp, color = GuardianTextDim)
            }
        }

        Spacer(Modifier.height(32.dp))

        // 7 日趋势
        Text("近 7 日趋势", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(GuardianSurface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            val maxCount = dailyCounts.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width / 7
                val barW = w * 0.5f
                val topPad = 20f
                val bottomPad = 24f
                val chartH = size.height - topPad - bottomPad

                dailyCounts.forEachIndexed { i, (_, count) ->
                    val barH = (count.toFloat() / maxCount) * chartH
                    val x = i * w + (w - barW) / 2
                    val y = size.height - bottomPad - barH
                    drawRect(GuardianAccent, Offset(x, y), Size(barW, barH))
                }
            }
            // day labels — 跟柱子对齐：每个 label 占一列宽度，居中
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            ) {
                dailyCounts.forEach { (label, _) ->
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = GuardianTextDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Top 5 App 排行
        Text("高频 App Top ${topApps.size}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(12.dp))
        if (topApps.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().background(GuardianSurface, RoundedCornerShape(16.dp)).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无数据", fontSize = 14.sp, color = GuardianTextDim)
            }
        } else {
            // 整体一个圆角容器，每行平铺，行间细分隔线
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GuardianSurface)
            ) {
                topApps.forEachIndexed { i, app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${i + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GuardianAccent)
                        Spacer(Modifier.width(12.dp))
                        Text(appLabels[app.packageName] ?: app.packageName,
                            fontSize = 14.sp, color = GuardianText, modifier = Modifier.weight(1f))
                        Text("${app.count} 次", fontSize = 13.sp, color = GuardianTextDim)
                    }
                    if (i < topApps.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(GuardianBg))
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // 连续清醒
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("连续清醒", fontSize = 14.sp, color = GuardianTextDim)
                Spacer(Modifier.height(6.dp))
                Text("$streak 天", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GuardianText)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

private suspend fun computeStreak(repo: GuardianRepository): Int {
    val firstTs = repo.firstTimestamp() ?: return 0
    val logs = repo.listLogsSince(firstTs)
    val daysWithTrigger = mutableSetOf<String>()
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    val cal = java.util.Calendar.getInstance()
    logs.forEach { log ->
        cal.timeInMillis = log.timestamp
        daysWithTrigger.add(fmt.format(cal.time))
    }
    var streak = 0
    cal.timeInMillis = System.currentTimeMillis()
    while (true) {
        cal.add(java.util.Calendar.DAY_OF_YEAR, -streak)
        val day = fmt.format(cal.time)
        if (day !in daysWithTrigger) break
        streak++
        cal.timeInMillis = System.currentTimeMillis()
    }
    return streak
}
