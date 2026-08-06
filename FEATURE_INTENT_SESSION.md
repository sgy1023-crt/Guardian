# 意图声明 + 限时使用 + 到时自动退出

> **P0 核心功能**：将 Guardian 从"纯提醒型"升级为"管控型"——打开被监控 App 时必须声明意图并设定使用时长，到时自动回桌面。

## 核心流程

```
用户打开被监控 App（如抖音）
  ↓
Guardian 全屏拦截 → 弹出「意图声明卡」
  ├─ 输入打开理由（最少 3 字）
  ├─ 选择使用时长（5 / 10 / 15 / 30 分钟 / 自定义）
  ├─ [开始使用] → 放行，通知栏显示剩余时间
  └─ [还是算了] → 关闭弹窗，重新拦截
  ↓
限时使用中 —— 通知栏："「抖音」还剩 8 分钟"
  ↓
时间到 → 弹出「到时回顾卡」
  ├─ 展示用户最初写的打开理由
  ├─ [✓ 做到了，退出] → 回到桌面，会话结束
  ├─ [再续 N 分钟（还能续 X 次）] → 延长使用时间
  └─ [退出（回桌面）] → 回到桌面，会话结束
```

## 涉及的数据库变更

### 新增表 `intent_sessions`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK AUTO | 会话 ID |
| `packageName` | TEXT | 被监控的 App 包名 |
| `reason` | TEXT | 用户填写的打开理由 |
| `timeLimitSeconds` | INTEGER | 本次允许使用的秒数 |
| `startTime` | INTEGER | 会话开始时间戳（毫秒） |
| `endedAt` | INTEGER? | 结束时间戳，null 表示进行中 |
| `extensionCount` | INTEGER | 续时次数，默认 0 |
| `status` | TEXT | ACTIVE / COMPLETED / EXTENDED / CANCELLED / EXPIRED |

数据库版本：`1 → 2`，包含自动迁移脚本。

## 新增/修改的文件

### 新文件

| 文件 | 用途 |
|------|------|
| `overlay/IntentCardContent.kt` | 意图声明卡 Compose UI：理由输入、时长预设、自定义时长 |
| `overlay/TimeUpCardContent.kt` | 到时回顾卡 Compose UI：回顾理由、三选一操作 |

### 修改文件

| 文件 | 变更内容 |
|------|---------|
| `data/db/Entities.kt` | 新增 `IntentSession` 实体及状态常量 |
| `data/db/Daos.kt` | 新增 `IntentSessionDao`——创建/更新/续时/查询/过期清理/日统计 |
| `data/db/GuardianDatabase.kt` | 版本 `1→2`，`MIGRATION_1_2` 建表，暴露 `intentSessionDao()` |
| `data/db/GuardianRepository.kt` | 新增 7 个会话管理方法 |
| `data/MonitorPrefs.kt` | 新增 3 个设置项：`defaultTimeLimitSeconds`、`extensionSeconds`、`maxExtensionCount` |
| `overlay/OverlayController.kt` | 新增 `OverlayContent` 密封类（`Reminder` / `IntentCard` / `TimeUpCard`），`show()` 按类型分派不同 UI |
| `service/MonitorService.kt` | **核心改造**：轮询循环改为会话驱动——无会话弹意图卡、有会话放行、到时弹回顾卡、退出回桌面 |
| `service/ClipboardWatcherService.kt` | 关键词触发适配新 API（`OverlayContent.Reminder`） |
| `ui/settings/SettingsScreen.kt` | 新增"限时使用"设置区：默认时长 / 续时时长 / 最大续时次数 |

## 关键设计决策

### 1. 为什么用 `Intent(ACTION_MAIN + CATEGORY_HOME)` 回桌面而不是强杀进程

Android 不允许第三方应用杀死其他应用进程。使用 Home Intent 是系统允许的标准做法，且用户退到桌面后如果立刻又打开被监控 App，Guardian 每 1 秒轮询一次，会立刻重新拦截——冷却递增机制（后续 PR 实现）会让频繁重开的成本越来越高。

### 2. 为什么回调顺序是"先关弹窗，再执行业务逻辑"

`OverlayController.show()` 中，IntentCard/TimeUpCard 的回调包装层先调用 `dismiss(source)` 关掉弹窗，再调用 `content.onStart(...)` 等业务回调。这样：
- 用户立即看到弹窗消失，体验流畅
- 后续的 Room 写操作（创建会话/更新状态）在后台进行，不阻塞 UI
- 下一轮轮询（1 秒后）能正确检测到"已有活跃会话→放行"或"无会话→重新拦截"

### 3. 为什么用内存缓存 `sessionEndMsByPkg` 而不是每秒查 DB

会话结束时需要在轮询循环中做高频判断（每秒一次）。HashMap 查 `sessionEndMsByPkg[pkg]` 是 O(1)、零延迟、不产生 IO。会话创建/续时时写入缓存，服务重启时从 DB 重建。

### 4. 与服务重启的兼容性

`MonitorService.onCreate()` 中会调用 `repo.expireAllActiveSessions()` 清掉所有残留的 `ACTIVE`/`EXTENDED` 状态会话。这样服务被杀后重启时，不会有"幽灵会话"导致拦截失效。

### 5. 与旧版关键词触发的关系

`ClipboardWatcherService` 的关键词检测继续使用 `OverlayContent.Reminder`（旧版简单弹窗），本次改动不影响该路径。

## P1：单 App 全天时长上限（已完成）

每个被监控 App 可设每日配额（0~180 分钟，0 = 不限）。

- `MonitorPrefs.dailyLimitMinutes` 存储设置
- `MonitorService.handleMonitoredApp()` 无会话时先查询 `totalSecondsToday()`，超限则弹 `DailyLimitCard`
- `DailyLimitCardContent` 显示：今日已用 / 每日上限 / 剩余 三项统计 + 进度条 + 脉冲动画
- 仅统计 COMPLETED 和 EXPIRED 状态的会话，ACTIVE 会话不计入（正在进行中）
- 配额日清：`getTodayStartMs()` 计算今日零点，Room 按 `startTime >= todayStart` 过滤
- 零时无行为：仅再次打开该 App 时才触发检查

## P3：冷却递增（已完成）

打开同一 App 越频繁，意图声明卡强制等待越久：

| 今日已打开次数 | 强制等待 |
|--------------|---------|
| 0 次 | 0 秒（直接填） |
| 1 次 | 10 秒 |
| 2 次 | 30 秒 |
| 3 次 | 60 秒 |
| 4 次及以上 | 180 秒（3 分钟） |

实现方式：
- `MonitorService.escalationWaitSeconds()` 根据 `countTodaySessions()` 的返回值查表
- `IntentCardContent` 通过 `forcedWaitSeconds` 参数接收，`LaunchedEffect` 驱动倒计时
- 冷却期间：理由输入框、时长按钮、开始按钮全部 `enabled = false`，显示大号秒数倒计时
- "还是算了"按钮在冷却期间依然可用

## 后续计划

- **P1** 单 App 全天时长上限
- **P2** 学习时段全封锁
- **P4** 意图回顾日志（时间线页面）
- 番茄钟联动
- 每周自律报告
