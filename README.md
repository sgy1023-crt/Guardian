# 守卫 Guardian

> Android 自律提醒工具。打开指定 App 或屏幕出现关键词时，弹"停顿点"悬浮窗提醒你清醒一下。不锁机、只提醒。对标 [one sec](https://one-sec.app/)。

## 功能

- **App 触发**：勾选要监督的应用（抖音、微博、B 站……），打开即弹窗
- **关键词触发**：设置关键词（"游戏"、"无聊"、"再刷一会儿"……），屏幕出现即弹窗
- **桌宠图标**：小琥珀机器人常驻 App 图标，扫描到关键词就弹出呼吸圆点提醒
- **提醒语模式**：随机抽 / 指定固定一条，随意切换
- **冷却时间**：同一 App 触发后 5~300 秒内不再重复弹（设置页可调）
- **统计**：今日提醒次数、7 日趋势、Top App、连续清醒天数
- **数据导出**：触发记录一键导出 JSON 分享
- **保活**：前台服务 + WorkManager + 开机自启，进程被杀会自动拉起
- **本地运行**：不联网不上传，所有数据只在本地

## 截图

> TODO：装机后补主页 / 弹窗 / 关键词管理 / 统计页截图

## 下载安装

### 方式一：直接下载 APK

去 [Releases](../../releases) 下载 `app-debug.apk`，手机允许"未知来源"后点击安装。

### 方式二：自己编译

```bash
git clone <本仓库地址>
cd 守卫APP
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
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
2. 进 **App 管理** 勾选要监督的应用
3. 进 **关键词管理** 加你想监督的词
4. 进 **提醒语管理** 选"随机"或"指定"模式
5. 回主页打开总开关

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
- 两套独立监控引擎：UsageStatsManager 前台轮询 + AccessibilityService 全屏文本扫描

## 架构

```
引擎 1：前台 App 检测 → 悬浮窗弹窗
  MonitorService.kt · 1s 轮询 UsageStatsManager.queryEvents
  命中 monitored_apps 表 → 弹呼吸圆点 + 5s 倒计时按钮

引擎 2：屏幕文字扫描 → 悬浮窗弹窗
  ClipboardWatcherService.kt · 1.5s 轮询 rootInActiveWindow
  递归遍历整棵节点树收集 text/contentDescription
  命中 keywords 表 → 弹同样悬浮窗

数据层：Room（MonitoredApp / Keyword / Reminder / TriggerLog）
保活：ForegroundService + WorkManager + BootReceiver
弹窗：WindowManager TYPE_APPLICATION_OVERLAY + ComposeView
```

## 路线图

- [x] App 触发提醒
- [x] 关键词触发提醒（全屏扫描）
- [x] 提醒语随机/指定模式
- [x] 冷却可调
- [x] 统计 + 数据导出
- [x] 开机自启 + 进程被杀自动拉起
- [ ] 桌宠形象常驻桌面（不只是图标）
- [ ] 提醒语分类（工作时间用 A 池，睡前用 B 池）
- [ ] 云同步（用户可选）

## License

MIT — 见 [LICENSE](LICENSE)

## 致谢

灵感来自 [one sec](https://one-sec.app/)。提醒语默认池里几条参考了正念冥想常见话术。
