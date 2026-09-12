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
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Lock
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
import com.gangyi.guardian.ui.theme.GuardianDangerSoft
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
import kotlinx.coroutines.delay

/**
 * 封锁窗。跟停顿弹窗（橙）刻意区分——红色，让人一眼知道"这次不一样了"。
 *
 * 两种形态：
 * - kicked = true：人已经被送回桌面，这只是一张告知卡，几秒后自动消失，也可以直接点掉。
 * - kicked = false：没能送回桌面（无障碍没开 / ROM 拦了），人还在那个 App 里。
 *   这时它就是一堵墙：没有倒计时等待、没有"我明白了"，只有剩余时间和「回到桌面」。
 *   人自己走了，墙就跟着收（窗绑定了包名）。旧版在这里强制等 60 秒然后 3 秒后再弹，
 *   按钮写着"我明白了"却什么都没解决，等于把人按在原地反复羞辱——去掉了。
 */
@Composable
fun LockdownContent(
    appLabel: String,
    remainingMs: Long,
    kicked: Boolean,
    onDismiss: () -> Unit,
    onHome: () -> Unit
) {
    var remaining by remember { mutableLongStateOf(remainingMs) }

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000L)
            remaining = (remaining - 1000L).coerceAtLeast(0L)
        }
        // 到点自动解锁，墙自己消失
        if (!kicked) onDismiss()
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
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(28.dp))
                .background(GuardianSurface)
                .border(1.dp, GuardianBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GuardianChip(
                if (kicked) "已送回桌面" else "封锁中",
                GuardianDanger, GuardianDangerSoft, Icons.Rounded.Lock
            )

            Spacer(Modifier.height(22.dp))

            Box(
                modifier = Modifier.size(112.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(GuardianDanger.copy(alpha = glow * 0.5f), Color.Transparent),
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
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = GuardianDanger,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                if (kicked) "已经拦下你了" else "$appLabel 暂时打不开",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = GuardianText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                if (kicked) "$appLabel 已锁定。短时间内反复选择继续，说明现在需要的不是再一次提醒。"
                else "短时间内反复选择继续，说明现在需要的不是再一次提醒。回桌面做点别的，倒计时结束自动解锁。",
                fontSize = 13.sp,
                color = GuardianTextDim,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(22.dp))

            Text(
                formatDuration(remaining),
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                color = GuardianDanger
            )
            Spacer(Modifier.height(2.dp))
            Text("后自动解锁", fontSize = 12.sp, color = GuardianTextFaint)

            Spacer(Modifier.height(26.dp))

            Button(
                onClick = if (kicked) onDismiss else onHome,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GuardianDanger),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    if (kicked) "知道了" else "回到桌面",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (!kicked) {
                Spacer(Modifier.height(14.dp))
                OverlayHintRow(
                    Icons.Rounded.Accessibility,
                    "开启无障碍权限后，封锁会自动把你送回桌面",
                    GuardianTextFaint
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
