package com.gangyi.guardian.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.ui.components.GuardianChip
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianAccentSoft
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianBorder
import com.gangyi.guardian.ui.theme.GuardianDanger
import com.gangyi.guardian.ui.theme.GuardianDangerSoft
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import kotlinx.coroutines.delay

val DEFAULT_REMINDERS = listOf(
    "你真的需要现在打开它吗？",
    "深呼吸，想想你本来要做什么。",
    "这一刻的冲动，3 分钟后就会消退。",
    "你是自己时间的主人。",
    "停一停，问问自己：这值得吗？",
    "你的专注比一次刷屏更有价值。",
    "30 秒后再决定，急什么？"
)

/**
 * 停顿弹窗内容。
 *
 * 视觉核心是那个呼吸光点：外层 radialGradient 做光晕呼吸，
 * 中层 drawArc 画环形倒计时进度，内层实心圆定住视觉中心。
 * 三层共用一个 Canvas，比堆 Box 更省层级也更好控。
 */
@Composable
fun InterventionContent(
    reminder: String,
    countdownSeconds: Int = 0,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // countdownSeconds > 0 表示调用方已算好（递增模式）；否则回退到设置里的固定值
    val countdownTotal = remember(countdownSeconds) {
        if (countdownSeconds > 0) countdownSeconds.coerceIn(1, 3600)
        else MonitorPrefs(context).overlayCountdownSeconds.coerceIn(1, 180)
    }
    var countdown by remember(countdownTotal) { mutableIntStateOf(countdownTotal) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
    }

    val breath = rememberInfiniteTransition(label = "breath")
    val glow by breath.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val scale by breath.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // 倒计时进度：1 → 0，环形进度条随之收缩
    val progress = if (countdownTotal <= 0) 0f else countdown.toFloat() / countdownTotal

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(28.dp))
                .background(GuardianSurface)
                .border(1.dp, GuardianBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 连击时把等级显示出来：让人直观意识到"越挣扎、等得越久"，
            // 这份自觉本身就是摩擦力的一部分
            if (countdownTotal > MonitorPrefs(context).overlayCountdownSeconds) {
                GuardianChip("停顿点 · 已加长", GuardianDanger, GuardianDangerSoft)
            } else {
                GuardianChip("停顿点", GuardianAccent, GuardianAccentSoft)
            }

            Spacer(Modifier.height(24.dp))

            // 光晕 + 环形倒计时 + 呼吸圆点
            Box(
                modifier = Modifier.size(132.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val outerR = size.minDimension / 2f

                    // 外层光晕：跟着呼吸变浓淡
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GuardianAccent.copy(alpha = glow * 0.42f),
                                Color.Transparent
                            ),
                            center = c,
                            radius = outerR
                        ),
                        radius = outerR
                    )

                    // 环形轨道 + 倒计时进度弧
                    val ringR = outerR * 0.72f
                    val strokeW = 3.dp.toPx()
                    val arcSize = Size(ringR * 2, ringR * 2)
                    val arcTopLeft = Offset(c.x - ringR, c.y - ringR)
                    drawArc(
                        color = GuardianAccent.copy(alpha = 0.16f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    if (progress > 0f) {
                        drawArc(
                            color = GuardianAccent,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }
                }

                // 内层实心呼吸圆点
                Box(
                    modifier = Modifier
                        .size((44 * scale).dp)
                        .clip(CircleShape)
                        .background(GuardianAccent)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = reminder,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = GuardianText,
                textAlign = TextAlign.Center,
                lineHeight = 29.sp
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onDismiss,
                enabled = countdown == 0,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GuardianAccent,
                    disabledContainerColor = GuardianBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (countdown > 0) "冷静一下… $countdown" else "我清醒了",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (countdown > 0) GuardianTextDim else Color.Black
                )
            }
        }
    }
}
