package com.gangyi.guardian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianAccentSoft
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianBorder
import com.gangyi.guardian.ui.theme.GuardianSuccess
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint

/**
 * 守卫统一 UI 组件库。
 *
 * 目的：把散在 7 个页面里的重复排版（标题栏、卡片、滑块、分组标题、空状态）收成一处，
 * 保证视觉一致，改一处全局生效。新页面直接拿这些搭。
 */

/** 统一标题栏：返回箭头 + 居中标题。取代各页面手写的 Spacer(weight) 硬居中。 */
@Composable
fun GuardianTopBar(
    title: String,
    onBack: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = GuardianText,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            )
        } else {
            Spacer(Modifier.size(28.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GuardianText)
        Spacer(Modifier.weight(1f))
        // 右侧留等宽占位，标题才是真居中
        Spacer(Modifier.size(28.dp))
    }
}

/** 统一卡片：16dp 圆角 + Surface 底 + 可选描边。clip 在 background 前，点击涟漪不会溢出圆角。 */
@Composable
fun GuardianCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    bordered: Boolean = true,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GuardianSurface)
            .then(if (bordered) Modifier.border(1.dp, GuardianBorder, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(contentPadding),
        content = content
    )
}

/** 分组小标题，统一字号/颜色/下间距。 */
@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = GuardianTextFaint,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/**
 * 统一设置滑块。
 *
 * 关键：**不传 steps**。Material3 的 Slider 一旦给了 steps 就会画出一排刻度点，
 * 步数大时（如 176）直接糊成一条实线，非常难看。连续滑动 + 外部取整就够用。
 */
@Composable
fun GuardianSlider(
    label: String,
    valueText: String,
    hint: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    GuardianCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 15.sp, color = GuardianText, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GuardianAccent)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = GuardianAccent,
                activeTrackColor = GuardianAccent,
                inactiveTrackColor = GuardianBg
            )
        )
        Text(hint, fontSize = 12.sp, color = GuardianTextFaint)
    }
}

/** 空状态：图标 + 主文案 + 提示，取代光秃秃一行小灰字。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(GuardianSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = GuardianTextFaint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = GuardianTextDim)
        Spacer(Modifier.height(6.dp))
        Text(
            hint,
            fontSize = 12.sp,
            color = GuardianTextFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/** 胶囊标签：把"内置""固定提醒语""已封锁"这类状态做成真正的视觉标签，而不是变色小字。 */
@Composable
fun GuardianChip(
    text: String,
    color: Color,
    softColor: Color,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(softColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

/** 主页入口卡：左侧图标徽章 + 标题副标题 + 右箭头。5 张卡靠图标区分，不再千篇一律。 */
@Composable
fun NavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    GuardianCard(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(GuardianAccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = GuardianAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = GuardianText)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = GuardianTextDim)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = GuardianTextFaint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 权限状态行：已授权显示绿勾，未授权可点击跳系统设置。主页和引导页共用。 */
@Composable
fun PermissionRow(
    label: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GuardianSurface)
            .border(1.dp, GuardianBorder, shape)
            .clickable(enabled = !granted) { onRequest() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = GuardianText, modifier = Modifier.weight(1f))
        Text(
            if (granted) "✓ 已授权" else "去授权",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (granted) GuardianSuccess else GuardianAccent
        )
    }
}
