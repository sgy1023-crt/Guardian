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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.ui.components.GuardianChip
import com.gangyi.guardian.ui.theme.GuardianBorder
import com.gangyi.guardian.ui.theme.GuardianDanger
import com.gangyi.guardian.ui.theme.GuardianDangerDim
import com.gangyi.guardian.ui.theme.GuardianDangerSoft
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
import kotlinx.coroutines.delay

/**
 * 封锁告知窗。
 *
 * 跟普通停顿弹窗（橙色）刻意区分开——这里是红色，让人一眼知道"这次不一样了"。
 *
 * 交互设计的取舍：
 * - 已成功踢回桌面时（kicked = true），按钮可以直接点掉。因为人已经被赶出来了，
 *   真正的约束是"再进去就再被踢"，没必要把人按在这个告知窗前面干等。
 * - 没踢成功时（无障碍未绑定），人还留在那个 App 里，所以退化成"强制冷静"：
 *   倒计时走完才能关，否则等于什么都没拦住。
 */
@Composable
fun LockdownContent(
    remainingMs: Long,
    kicked: Boolean,
    onDismiss: () -> Unit
) {
    // 冷静期上限 60 秒——没踢成功时用它做强制等待，不然按封锁全长（可能几分钟）干等太反人类
    val forcedWaitSec = remember {
        if (kicked) 0 else (remainingMs / 1000).coerceIn(5L, 60L).toInt()
    }
    var waitLeft by remember { mutableLongStateOf(forcedWaitSec.toLong()) }
    var remaining by remember { mutableLongStateOf(remainingMs) }

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000L)
            remaining = (remaining - 1000L).coerceAtLeast(0L)
            if (waitLeft > 0) waitLeft -= 1
        }
    }

    val pulse = rememberInfiniteTransition(label = "lockPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lockGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000)),
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
            GuardianChip("已封锁", GuardianDanger, GuardianDangerSoft, Icons.Filled.Lock)

            Spacer(Modifier.height(24.dp))

            // 光晕锁图标：外层 radialGradient 呼吸，内层实心圆托住图标
            Box(
                modifier = Modifier.size(112.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GuardianDanger.copy(alpha = glow * 0.5f),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension / 2f
                        ),
                        radius = size.minDimension / 2f
                    )
                }
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(50))
                        .background(GuardianDanger.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = GuardianDanger,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                if (kicked) "已经拦下你了" else "停下来",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = GuardianText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "短时间内反复触发提醒，说明现在的你需要的不是再一次提醒。" +
                    if (kicked) "这个应用已被暂时锁住。" else "先冷静一下。",
                fontSize = 14.sp,
                color = GuardianTextDim,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(24.dp))

            // 剩余时间大字
            Text(
                formatDuration(remaining),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = GuardianDanger
            )
            Spacer(Modifier.height(4.dp))
            Text("解锁倒计时", fontSize = 12.sp, color = GuardianTextFaint)

            Spacer(Modifier.height(28.dp))

            val canClose = waitLeft <= 0L
            Button(
                onClick = onDismiss,
                enabled = canClose,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GuardianDanger,
                    disabledContainerColor = GuardianDangerDim
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    if (canClose) "我明白了" else "冷静一下… $waitLeft",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canClose) Color.White else GuardianTextDim
                )
            }

            if (!kicked) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "提示：开启无障碍权限后，封锁会自动把你送回桌面",
                    fontSize = 11.sp,
                    color = GuardianTextFaint,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** 毫秒转 mm:ss。 */
private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
