package com.gangyi.guardian.overlay

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
fun StudyBlockCardContent(
    appLabel: String,
    startTime: String,
    endTime: String,
    remainingMinutes: Int,
    onExit: () -> Unit
) {
    val pulseAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
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
            // 脉冲蓝点
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(GuardianAccent, CircleShape)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "现在是学习时间",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = GuardianText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "「$appLabel」在学习时段内禁止使用",
                fontSize = 14.sp,
                color = GuardianTextDim,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // 时段信息卡片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, GuardianAccent.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .background(GuardianBg)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "学习时段",
                        fontSize = 12.sp,
                        color = GuardianTextFaint
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$startTime — $endTime",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = GuardianText
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "距离学习时段结束还有 $remainingMinutes 分钟",
                        fontSize = 13.sp,
                        color = GuardianTextDim
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "坚持住，学习结束后就可以放松一下了",
                fontSize = 12.sp,
                color = GuardianTextFaint,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // 回桌面按钮
            Button(
                onClick = onExit,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GuardianAccent),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(
                    text = "回去学习",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "学习时段内所有被监控 App 均不可用",
                fontSize = 11.sp,
                color = GuardianTextFaint,
                textAlign = TextAlign.Center
            )
        }
    }
}
