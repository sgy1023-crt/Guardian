package com.gangyi.guardian.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
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

@Composable
fun InterventionContent(reminder: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val countdownTotal = remember { MonitorPrefs(context).overlayCountdownSeconds.coerceIn(1, 180) }
    var countdown by remember { mutableIntStateOf(countdownTotal) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xE6000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(GuardianSurface, RoundedCornerShape(24.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 呼吸圆点
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(scale)
                    .background(GuardianAccent, CircleShape)
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = reminder,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = GuardianText,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
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
                modifier = Modifier.fillMaxWidth().height(52.dp)
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
