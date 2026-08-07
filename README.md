# 守卫 Guardian

> Android 自律辅助工具。不锁机，而是让你在打开分心 App 之前停下来想清楚——写理由、限时长，时间到了自己退。

## 功能

### 核心机制：意图声明 + 限时使用

打开被监控 App 时不再是简单的"停顿一下"，而是弹出一张**意图声明卡**：

- **写理由**：为什么现在要打开？（至少 3 个字，对自己诚实）
- **选时长**：5 / 10 / 15 / 30 分钟预设，或自定义分钟数
- **开始使用**：填写完毕进入限时会话，到时自动提醒
- **还是算了**：放弃打开，直接回到桌面
- **冷却递增**：同一天内打开同一 App 次数越多，强制冷静等待越久（0→10→30→60→180 秒），给冲动降温

### 到时回顾卡

限时会话时间到了之后弹出回顾卡，三个选择：

- **退出应用**：回到桌面，会话结束
- **续时**：再续一段（时长和最大续时次数在设置中可调）
- **完成了**：标记会话完成，退出应用

### 每日限额（Per-App 独立设置）

每个被监控 App 可独立设置每日使用时长上限（0~180 分钟）：

- 在 App 管理页每个已勾选的 App 下拖动滑块即可
- 当日配额用完后再打开该 App 直接弹出"额度耗尽"卡，只能回到桌面
- 不再是一刀切的全局限制

### 学习时段封锁（多时段）

学习时间内**完全禁止**打开任何被监控 App：

- 支持最多 **5 个独立时段**（上午/下午/晚上分别设置）
- 支持**跨夜时段**（如 22:00 ~ 06:00）
- 弹窗显示当前时段起止时间和剩余分钟数
- 学习时段优先级最高——即使有活跃会话也会被拦截

### App 触发提醒（旧版关键词模式）

- **关键词触发**：设置关键词（"游戏"、"无聊"、"再刷一会儿"……），屏幕出现即弹倒计时提醒窗
- **关键词加密模式**：列表遮罩显示，弹窗不显示原文，删除需 4~6 位数字密码——适合你想"忘掉"的关键词
- **提醒语模式**：随机抽 / 指定固定一条，随意切换
- **弹窗倒计时**：3~180 秒可调，倒计时结束才能点"我清醒了"

### 统计 & 数据

- 今日提醒次数、7 日趋势柱状图、Top App、连续清醒天数
- 触发记录一键导出 JSON 分享
- 所有数据 Room 数据库 + SharedPreferences，不联网不上传

### 保活 & 权限

- 前台服务 + WorkManager + 开机自启，进程被杀自动拉起
- 屏幕关闭时自动降低轮询频率省电
- 权限引导页（5 步），首次安装逐步引导

## 截图

| 意图声明卡 · 填理由 | 意图声明卡 · 选时长 | 到时回顾卡 |
|:---:|:---:|:---:|
| <img src="screenshots/intent-card.jpg" width="260"/> | <img src="screenshots/intent-time.jpg" width="260"/> | <img src="screenshots/timeup-card.jpg" width="260"/> |

| 每日限额耗尽 | 学习时段封锁 | 停顿弹窗 · 旧版关键词 |
|:---:|:---:|:---:|
| <img src="screenshots/dailylimit-card.jpg" width="260"/> | <img src="screenshots/studyblock-card.jpg" width="260"/> | <img src="screenshots/overlay-popup.jpg" width="260"/> |

| 提醒语 · 随机模式 | 关键词 · 加密遮罩 | 设置 |
|:---:|:---:|:---:|
| <img src="screenshots/reminders-random.jpg" width="260"/> | <img src="screenshots/keywords-encrypted.jpg" width="260"/> | <img src="screenshots/settings.jpg" width="260"/> |

## 下载安装

### 方式一：直接下载 APK

去 [Releases](../../releases) 下载最新 `app-debug.apk`，手机允许"未知来源"后点击安装。

### 方式二：自己编译

```bash
git clone https://github.com/houliabc/Guardian.git
cd Guardian
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 小米被 INSTALL_FAILED_USER_RESTRICTED 拒绝时（USB 安装开关未开）：
# adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/g.apk && adb shell pm install -r /data/local/tmp/g.apk
```

要求：JDK 17、Android SDK（compileSdk 35、minSdk 26）、Android 8.0+ 真机。

## 权限说明

| 权限 | 用途 |
|------|------|
| `PACKAGE_USAGE_STATS` | 检测当前前台 App（需手动授权） |
| `SYSTEM_ALERT_WINDOW` | 在其他 App 之上弹悬浮窗 |
| `BIND_ACCESSIBILITY_SERVICE` | 扫描屏幕文字匹配关键词 |
| `FOREGROUND_SERVICE` + `_SPECIAL_USE` | 常驻后台检测 |
| `POST_NOTIFICATIONS` | 前台服务通知（Android 13+） |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 关闭电池优化保活 |
| `QUERY_ALL_PACKAGES` | 列出已安装 App 供勾选 |

所有数据只在本地（Room 数据库 + SharedPreferences），不联网不上传。

## 首次使用

1. 安装后打开，走完 5 步权限引导
2. 进 **监控列表** 勾选要监督的应用（可为每个 App 单独设每日限额）
3. 进 **关键词管理** 加你想监督的词（可选）
4. 进 **提醒语管理** 选"随机"或"指定"模式
5. 进 **设置** 调整默认时长、续时次数、学习时段等
6. 回主页打开总开关

## 小米/HyperOS 注意事项

> 小米系统的两个坑，按这个顺序处理才能正常工作：

1. **覆盖安装失败**：如果之前装过老版本且无障碍绑定没清，`adb install` 会显示 "Success" 但实际没装进去。**先在桌面长按图标卸载老版本，再装新版。**
2. **无障碍绑定状态不对齐**：装完进 系统设置 → 无障碍 → 守卫，**先关掉，等 2 秒，再打开**。HyperOS 已知 bug，开关显示"开"但实际未绑定。

## 技术栈

- Kotlin 2.0.21
- Jetpack Compose（Material3，Compose BOM 2024.12.01）
- Room 2.6.1
- AGP 8.13.2 / Gradle 8.13
- minSdk 26 / targetSdk 35

## 架构

```
引擎 1：前台 App 检测 → 意图声明 + 限时使用

  MonitorService.kt · 1s 轮询 UsageStatsManager
  命中 monitored_apps 表 →
    ├─ 学习时段？ → StudyBlockCard（只能退出）
    ├─ 每日额度用完了？ → DailyLimitCard（只能退出）
    ├─ 无活跃会话 → IntentCard（填理由 + 选时长 → 开始会话）
    ├─ 会话进行中未到时 → 放行
    └─ 会话时间到了 → TimeUpCard（退出 / 续时 / 完成）

引擎 2：屏幕文字扫描 → 倒计时提醒窗

  ClipboardWatcherService.kt · 按设置间隔轮询 rootInActiveWindow
  递归遍历整棵节点树收集 text/contentDescription
  命中 keywords 表 → 弹倒计时提醒窗（旧版 InterventionContent）
  加密模式开启时：弹窗不显示关键词原文

数据层：
  - Room：MonitoredApp / Keyword / Reminder / TriggerLog / IntentSession
  - SharedPreferences：MonitorPrefs（开关、秒数、加密密码 hash、学习时段 JSON）
  - GuardianRepository：统一 DAO 层

保活：ForegroundService + WorkManager + BootReceiver
弹窗：WindowManager TYPE_APPLICATION_OVERLAY + ComposeView
密码：SHA-256 hash 存储
```

## 路线图

- [x] App 触发提醒（关键词模式）
- [x] 关键词触发提醒（全屏扫描）
- [x] 提醒语随机/指定模式
- [x] 弹窗倒计时可调（3~180 秒）
- [x] 关键词加密模式（遮罩 + 密码 + 弹窗不显示原文）
- [x] 冷却可调（5~300 秒）
- [x] 扫描灵敏度可调（1~10 秒）
- [x] 统计 + 数据导出
- [x] 开机自启 + 进程被杀自动拉起
- [x] **意图声明 + 限时使用**（打开 App 前写理由、选时长）
- [x] **到时自动退出 + 续时机制**（时间到了回顾、可选续时）
- [x] **冷却递增强制等待**（越频繁打开等越久）
- [x] **Per-App 每日限额**（每个 App 独立设置每日时长上限）
- [x] **学习时段多段封锁**（上午/下午/晚上独立时段，支持跨夜）
- [ ] 提醒语分类（工作时间用 A 池，睡前用 B 池）
- [ ] 云同步（用户可选）

## License

MIT — 见 [LICENSE](LICENSE)

## 致谢

- 灵感来自 [one sec](https://one-sec.app/)。提醒语默认池里几条参考了正念冥想常见话术。
- 感谢 [LINUX DO](https://linux.do) 社区提供的交流氛围与知识分享，让我在学习和实践过程中受益良多。
