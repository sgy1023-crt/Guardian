package com.gangyi.guardian.overlay

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
fun DailyLimitCardContent(
    appLabel: String,
    usedMinutes: Int,
    limitMinutes: Int,
    onExit: () -> Unit
) {
    // 脉冲动画
    val pulseAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE8000000))
            .clickable(enabled = false) { /* 拦截穿透点击 */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(GuardianSurface, RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 脉冲圆点（红色）
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(GuardianAccent, CircleShape)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "今日额度已用完",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = GuardianText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "「$appLabel」的每日使用配额已经用完了",
                fontSize = 14.sp,
                color = GuardianTextDim,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // 用量统计
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(value = "${usedMinutes}min", label = "今日已用")
                StatItem(value = "${limitMinutes}min", label = "每日上限")
                StatItem(value = "0min", label = "剩余")
            }

            Spacer(Modifier.height(12.dp))

            // 进度条
            val progress = (usedMinutes.toFloat() / limitMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(GuardianBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(GuardianAccent)
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "进度 ${(progress * 100).toInt()}%",
                fontSize = 11.sp,
                color = GuardianTextFaint
            )

            Spacer(Modifier.height(24.dp))

            // 操作按钮
            Button(
                onClick = onExit,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GuardianAccent),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(
                    text = "退出（回桌面）",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "明天零点重置配额",
                fontSize = 12.sp,
                color = GuardianTextFaint
            )
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = GuardianText
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = GuardianTextFaint
        )
    }
}
