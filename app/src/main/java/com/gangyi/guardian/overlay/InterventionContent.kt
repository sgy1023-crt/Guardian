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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.gangyi.guardian.ui.components.GuardianChip
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianAccentSoft
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianBorder
import com.gangyi.guardian.ui.theme.GuardianDanger
import com.gangyi.guardian.ui.theme.GuardianDangerSoft
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianSurface2
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
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
 * 停顿弹窗。产品的灵魂：一次停顿，一个真实的选择。
 *
 * - 「退出」永远可点、永远免费——走人不该被惩罚，也不该被延迟。
 * - 「继续」要等停顿结束才能点，点了记一次、换一段通行时间；
 *   次数快用完时按钮变红并写明"将封锁"，绝不埋伏用户。
 *
 * 视觉核心是那个呼吸光点：外层 radialGradient 做光晕呼吸，
 * 中层 drawArc 画环形倒计时进度，内层实心圆定住视觉中心。
 */
@Composable
fun InterventionContent(session: InterventionSession) {
    val total = session.countdownSeconds.coerceIn(1, 3600)
    var countdown by remember { mutableIntStateOf(total) }

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

    val progress = countdown.toFloat() / total
    val escalated = session.continuesInWindow > 0
    val danger = session.willLockOnContinue

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE8000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(28.dp))
                .background(GuardianSurface)
                .border(1.dp, GuardianBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 顶部：为什么弹 + 本轮状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GuardianChip(
                    text = if (session.keywordHint != null) "关键词「${session.keywordHint}」"
                    else session.appLabel,
                    color = GuardianTextDim,
                    softColor = GuardianSurface2
                )
                when {
                    danger -> GuardianChip("再继续将封锁", GuardianDanger, GuardianDangerSoft, Icons.Rounded.Lock)
                    escalated -> GuardianChip(
                        "本轮已继续 ${session.continuesInWindow} 次",
                        GuardianAccent, GuardianAccentSoft
                    )
                    else -> GuardianChip("停顿点", GuardianAccent, GuardianAccentSoft)
                }
            }

            Spacer(Modifier.height(26.dp))

            // 光晕 + 环形倒计时 + 呼吸圆点
            val ringColor = if (danger) GuardianDanger else GuardianAccent
            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val outerR = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ringColor.copy(alpha = glow * 0.42f), Color.Transparent),
                            center = c,
                            radius = outerR
                        ),
                        radius = outerR
                    )
                    val ringR = outerR * 0.72f
                    val strokeW = 3.dp.toPx()
                    val arcSize = Size(ringR * 2, ringR * 2)
                    val arcTopLeft = Offset(c.x - ringR, c.y - ringR)
                    drawArc(
                        color = ringColor.copy(alpha = 0.16f),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        topLeft = arcTopLeft, size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    if (progress > 0f) {
                        drawArc(
                            color = ringColor,
                            startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                            topLeft = arcTopLeft, size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size((44 * scale).dp)
                        .clip(CircleShape)
                        .background(ringColor)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = session.reminder,
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                color = GuardianText,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp
            )

            if (session.brokenPromise) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GuardianDangerSoft)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        "你刚才选了退出，却还留在这里。这次已按「继续」记了一次。",
                        fontSize = 12.sp, color = GuardianDanger, lineHeight = 18.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // 退出：永远可点。填充色主按钮，这是我们希望你按的那个。
            Button(
                onClick = session.onExit,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GuardianAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text("退出，不看了", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(Modifier.height(10.dp))

            // 继续：停顿结束才能点。快封锁时整颗按钮变红，把后果写在脸上。
            val canContinue = countdown == 0
            val continueColor = if (danger) GuardianDanger else GuardianTextDim
            OutlinedButton(
                onClick = session.onContinue,
                enabled = canContinue,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (canContinue) continueColor.copy(alpha = 0.6f) else GuardianBorder
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = continueColor,
                    disabledContentColor = GuardianTextFaint,
                    containerColor = Color.Transparent,
                    disabledContainerColor = GuardianBg.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = when {
                        !canContinue -> "冷静一下… $countdown"
                        danger -> "仍要继续 · 将封锁 ${session.lockMinutes} 分钟"
                        else -> "继续使用 ${session.passMinutes} 分钟"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = when {
                    session.lockEnabled ->
                        "${session.windowMinutes} 分钟内第 ${session.threshold} 次继续会封锁 ${session.lockMinutes} 分钟" +
                            if (escalated) "，本轮已 ${session.continuesInWindow} 次" else ""
                    escalated -> "越继续，下一次停顿越久"
                    else -> "退出永远免费，继续会记一次"
                },
                fontSize = 11.sp,
                color = GuardianTextFaint,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

/** 停顿弹窗和封锁窗共用的小行：图标 + 文案 */
@Composable
internal fun OverlayHintRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 11.sp, color = color)
    }
}
