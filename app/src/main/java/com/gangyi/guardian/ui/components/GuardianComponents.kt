package com.gangyi.guardian.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianAccentSoft
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianBorder
import com.gangyi.guardian.ui.theme.GuardianDanger
import com.gangyi.guardian.ui.theme.GuardianSuccess
import com.gangyi.guardian.ui.theme.GuardianSuccessSoft
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianSurface2
import com.gangyi.guardian.ui.theme.GuardianSurface3
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint

/**
 * 守卫统一 UI 组件库。
 *
 * 所有页面只用这里的零件搭：标题、卡片、设置行、开关、滑块、分段控件、按钮、对话框。
 * 视觉规则集中在一处：卡片 20dp 圆角 + 1px 描边、按钮 16dp 圆角、琥珀只给主动作和激活态。
 */

// ---------------------------------------------------------------- 标题

/** Tab 页大标题：28sp 粗体 + 一句副标题，右侧可放一个动作。 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GuardianText)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 13.sp, color = GuardianTextDim)
            }
        }
        trailing?.invoke()
    }
}

/** 子页面标题栏：返回箭头 + 居中标题。 */
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GuardianSurface)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = GuardianText,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(Modifier.size(40.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GuardianText)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(40.dp))
    }
}

/** 分组小标题。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = GuardianTextFaint,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp)
    )
}

// ---------------------------------------------------------------- 卡片

/**
 * 统一卡片：20dp 圆角 + Surface 底 + 1px 描边。
 * glowColor 不为空时在右上角铺一层极淡的径向光晕，用来给"主角卡"加一点温度。
 */
@Composable
fun GuardianCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    bordered: Boolean = true,
    glowColor: Color? = null,
    background: Color = GuardianSurface,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(if (bordered) Modifier.border(1.dp, GuardianBorder, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        if (glowColor != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.9f, 0f),
                        radius = size.width * 0.55f
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.9f, 0f)
                )
            }
        }
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** 卡片内的行分隔线。 */
@Composable
fun RowDivider(startIndent: Dp = 0.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(1.dp)
            .background(GuardianBorder)
    )
}

/** 圆角方形图标徽章。 */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color = GuardianAccent,
    background: Color = GuardianAccentSoft,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

// ---------------------------------------------------------------- 设置行

/** 卡片里的一行：图标徽章 + 标题/副标题 + 右侧内容。 */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color = GuardianAccent,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            IconBadge(icon, tint = tint, background = tint.copy(alpha = 0.12f), size = 36.dp, iconSize = 18.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = GuardianText)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = GuardianTextDim, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** 带开关的设置行。 */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    danger: Boolean = false,
    enabled: Boolean = true
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        tint = if (danger) GuardianDanger else GuardianAccent,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null
    ) {
        GuardianSwitch(checked = checked, onCheckedChange = onCheckedChange, danger = danger, enabled = enabled)
    }
}

/** 导航行：点了进别的页面。 */
@Composable
fun NavRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color = GuardianAccent,
    value: String? = null,
    onClick: () -> Unit
) {
    SettingRow(title = title, subtitle = subtitle, icon = icon, tint = tint, onClick = onClick) {
        if (value != null) {
            Text(value, fontSize = 13.sp, color = GuardianTextDim)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = GuardianTextFaint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun GuardianSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val on = if (danger) GuardianDanger else GuardianAccent
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = GuardianBg,
            checkedTrackColor = on,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = GuardianTextDim,
            uncheckedTrackColor = GuardianSurface3,
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = GuardianBg,
            disabledCheckedTrackColor = on.copy(alpha = 0.4f),
            disabledUncheckedThumbColor = GuardianTextFaint,
            disabledUncheckedTrackColor = GuardianSurface2
        )
    )
}

// ---------------------------------------------------------------- 滑块

/**
 * 统一设置滑块。**不传 steps**：Material3 一旦给了 steps 就会画一排刻度点，步数大时糊成实线。
 */
@Composable
fun GuardianSlider(
    label: String,
    valueText: String,
    hint: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianText, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GuardianAccentSoft)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(valueText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GuardianAccent)
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = GuardianAccent,
                activeTrackColor = GuardianAccent,
                inactiveTrackColor = GuardianSurface3
            )
        )
        if (hint != null) {
            Text(hint, fontSize = 12.sp, color = GuardianTextFaint, lineHeight = 17.sp)
        }
    }
}

// ---------------------------------------------------------------- 数据块

/** 数字瓦片：一个大数字 + 一个小标签。放在 Row 里各 weight(1f)。 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    accent: Color = GuardianText
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GuardianSurface2)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = accent, lineHeight = 28.sp)
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, fontSize = 12.sp, color = GuardianTextDim, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = GuardianTextDim)
    }
}

/** 分段控件：几个互斥选项。 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GuardianSurface)
            .border(1.dp, GuardianBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) GuardianAccent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) GuardianBg else GuardianTextDim
                )
            }
        }
    }
}

/** 胶囊标签。 */
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
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

// ---------------------------------------------------------------- 输入

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = GuardianTextFaint, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = GuardianTextDim, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                Icon(
                    Icons.Rounded.Close, contentDescription = "清空", tint = GuardianTextDim,
                    modifier = Modifier.size(18.dp).clickable { onValueChange("") }
                )
            }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = guardianFieldColors(),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun GuardianTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = GuardianTextFaint) },
        singleLine = true,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(12.dp),
        colors = guardianFieldColors(),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun guardianFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = GuardianText,
    unfocusedTextColor = GuardianText,
    cursorColor = GuardianAccent,
    focusedBorderColor = GuardianAccent,
    unfocusedBorderColor = GuardianBorder,
    errorBorderColor = GuardianDanger,
    focusedContainerColor = GuardianBg,
    unfocusedContainerColor = GuardianBg,
    errorContainerColor = GuardianBg,
    errorTextColor = GuardianText
)

// ---------------------------------------------------------------- 按钮

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) GuardianDanger else GuardianAccent,
            contentColor = if (danger) Color.White else Color.Black,
            disabledContainerColor = GuardianSurface2,
            disabledContentColor = GuardianTextFaint
        ),
        modifier = modifier.height(54.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GuardianBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = GuardianText,
            disabledContentColor = GuardianTextFaint
        ),
        modifier = modifier.height(50.dp)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- 对话框

/** 统一对话框：深色底、琥珀确认。danger 时确认按钮变红。 */
@Composable
fun GuardianDialog(
    onDismiss: () -> Unit,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String = "取消",
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    text: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GuardianSurface,
        titleContentColor = GuardianText,
        textContentColor = GuardianTextDim,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = text,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    confirmText,
                    color = if (!confirmEnabled) GuardianTextFaint else if (danger) GuardianDanger else GuardianAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText, color = GuardianTextDim) }
        }
    )
}

// ---------------------------------------------------------------- 状态

/** 空状态：图标 + 主文案 + 提示。 */
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
                .size(72.dp)
                .clip(CircleShape)
                .background(GuardianSurface)
                .border(1.dp, GuardianBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = GuardianTextFaint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GuardianTextDim)
        Spacer(Modifier.height(6.dp))
        Text(
            hint,
            fontSize = 12.sp,
            color = GuardianTextFaint,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/** 权限状态行：图标 + 名称/说明 + 状态。未授权可点击跳系统设置。 */
@Composable
fun PermissionRow(
    icon: ImageVector,
    label: String,
    desc: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    SettingRow(
        title = label,
        subtitle = desc,
        icon = icon,
        tint = if (granted) GuardianSuccess else GuardianAccent,
        onClick = if (granted) null else onRequest
    ) {
        if (granted) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "已授权", tint = GuardianSuccess, modifier = Modifier.size(22.dp))
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(GuardianAccent)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("去开启", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GuardianBg)
            }
        }
    }
}

/** 已授权全绿时的一行摘要。 */
@Composable
fun AllGrantedRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(Icons.Rounded.CheckCircle, tint = GuardianSuccess, background = GuardianSuccessSoft, size = 36.dp, iconSize = 18.dp)
        Spacer(Modifier.width(14.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianText)
    }
}
