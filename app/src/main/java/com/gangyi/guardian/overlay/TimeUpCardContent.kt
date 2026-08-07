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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint

@Composable
fun TimeUpCardContent(
    appLabel: String,
    reason: String,
    remainingExtensions: Int,
    extensionSeconds: Int,
    onDone: () -> Unit,
    onExtend: () -> Unit,
    onExit: () -> Unit
) {
    val canExtend = remainingExtensions > 0

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE8000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(GuardianSurface, RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 呼吸圆点（红色调——时间到了）
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .scale(scale)
                    .background(Color(0xFFE05555), CircleShape)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "时间到了",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = GuardianText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "「$appLabel」的使用时间已用完",
                fontSize = 14.sp,
                color = GuardianTextDim,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // 回顾理由区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GuardianBg, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "你打开时说：",
                    fontSize = 12.sp,
                    color = GuardianTextFaint
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "「$reason」",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = GuardianAccent,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "现在做到了吗？",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = GuardianText
            )

            Spacer(Modifier.height(20.dp))

            // 三选一按钮
            Button(
                onClick = onDone,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GuardianAccent),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(
                    "✓ 做到了，退出",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(Modifier.height(10.dp))

            if (canExtend) {
                OutlinedButton(
                    onClick = onExtend,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text(
                        "再续 ${extensionSeconds / 60} 分钟（还能续 $remainingExtensions 次）",
                        fontSize = 14.sp,
                        color = GuardianTextDim
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            OutlinedButton(
                onClick = onExit,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text(
                    if (canExtend) "直接退出（回桌面）" else "退出（回桌面）",
                    fontSize = 14.sp,
                    color = GuardianTextDim
                )
            }
        }
    }
}
