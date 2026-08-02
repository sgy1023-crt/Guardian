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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.data.db.AppCount
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.ui.components.GuardianTopBar
import com.gangyi.guardian.ui.components.SectionLabel
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianSurface2
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
    var appLabels by remember { mutableStateOf(mapOf<String, String>()) }
    var streak by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val todayStart = cal.timeInMillis
            // 7 日窗口 = 6 天前 ~ 今天（含今天），正好 7 个桶
            val chartStart = todayStart - 6 * TimeUnit.DAYS.toMillis(1)

            val logs = repo.listLogsSince(chartStart)

            // today count
            todayCount = logs.count { it.timestamp >= todayStart }

            // daily counts for last 7 days (incl. today)
            val fmt = SimpleDateFormat("E", Locale.CHINESE)
            val counts = mutableMapOf<Long, Int>()
            for (i in 0 until 7) {
                counts[chartStart + i * TimeUnit.DAYS.toMillis(1)] = 0
            }
            logs.forEach { log ->
                cal.timeInMillis = log.timestamp
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val dayStart = cal.timeInMillis
                if (dayStart in counts) counts[dayStart] = counts[dayStart]!! + 1
            }
            dailyCounts = counts.entries.sortedBy { it.key }.map { (ms, c) ->
                Pair(fmt.format(Date(ms)), c)
            }

            // top apps
            topApps = repo.observeTopApps(chartStart, 5).first()

            // App 标签映射也在 IO 线程算好，别卡主线程
            val apps = InstalledApps.load(context)
            appLabels = topApps.associate { top ->
                top.packageName to (apps.find { it.packageName == top.packageName }?.label ?: top.packageName)
            }

            // streak
            streak = computeStreak(repo, todayStart)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianBg)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        GuardianTopBar("统计", onBack)
        Spacer(Modifier.height(28.dp))

        // 今日大数字：底下垫一层光晕，让这个主角数字不再干巴巴地悬在页面上
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GuardianAccent.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.height / 1.5f
                    ),
                    radius = size.height / 1.5f,
                    center = Offset(size.width / 2f, size.height / 2f)
                )
            }
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
        SectionLabel("近 7 日趋势")
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
                    val x = i * w + (w - barW) / 2
                    // 零值也画一小截底座，否则空白列看起来像渲染坏了
                    val barH = if (count == 0) 3f
                    else ((count.toFloat() / maxCount) * chartH).coerceAtLeast(6f)
                    val y = size.height - bottomPad - barH
                    drawRoundRect(
                        color = if (count == 0) GuardianSurface2 else GuardianAccent,
                        topLeft = Offset(x, y),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2.6f, barW / 2.6f)
                    )
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

/**
 * 连续清醒 = 从今天往回数，连续多少天完全没有触发记录（零触发才算清醒）。
 * 最后一次触发发生在今天 → 0；从未有过记录 → 0（没有历史可言）。
 */
private suspend fun computeStreak(repo: GuardianRepository, todayStart: Long): Int {
    val lastTs = repo.lastTimestamp() ?: return 0
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = lastTs
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    val lastDayStart = cal.timeInMillis
    return ((todayStart - lastDayStart) / TimeUnit.DAYS.toMillis(1)).toInt().coerceAtLeast(0)
}
