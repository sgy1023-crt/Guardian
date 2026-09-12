package com.gangyi.guardian.ui.theme

import androidx.compose.ui.graphics.Color

// 底色三层：页面底 / 卡片 / 卡片内分区。靠亮度层级而不是阴影表达深度。
val GuardianBg = Color(0xFF0B0B14)
val GuardianSurface = Color(0xFF161626)
val GuardianSurface2 = Color(0xFF1F1F33)
val GuardianSurface3 = Color(0xFF2A2A44)

// 琥珀橙：唯一的暖光，只在关键处亮起
val GuardianAccent = Color(0xFFF5A623)
val GuardianAccentLight = Color(0xFFFFC65C)
val GuardianAccentDim = Color(0xFF5C4A24)

val GuardianText = Color(0xFFF2F2F5)
val GuardianTextDim = Color(0xFF9A9AB0)
val GuardianTextFaint = Color(0xFF5E5E78)

val GuardianSuccess = Color(0xFF4ADE80)

// 危险色系：封锁专用，跟普通提醒的橙色区分开
val GuardianDanger = Color(0xFFE5484D)
val GuardianDangerDim = Color(0xFF4A1D20)

// 中性信息色：通行证、提示类状态
val GuardianInfo = Color(0xFF60A5FA)

// 半透明标签底色：胶囊标签用，叠在 Surface 上出柔和色块
val GuardianDangerSoft = Color(0x1AE5484D)
val GuardianAccentSoft = Color(0x1AF5A623)
val GuardianSuccessSoft = Color(0x1A4ADE80)
val GuardianInfoSoft = Color(0x1A60A5FA)

// 卡片描边：介于 Surface2 与 Bg 之间，给平铺的卡片加一层轮廓
val GuardianBorder = Color(0xFF262640)
