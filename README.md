# Personal Context

Android 上的个人数字世界上下文记录器（Personal Context Runtime 第一版）：
仅用系统 **NotificationListenerService** + **UsageStatsManager** 两个官方能力，
持续记录通知与 App 使用事件，生成按时间排序的每日数字生活时间轴。
为未来的 Personal Agent 提供结构化上下文（本版不接 LLM）。

## 隐私红线（可验证）

- **Manifest 不声明 `INTERNET` 权限**——应用物理上无法联网、上传、埋点
  （可用 `aapt2 dump permissions app-debug.apk` 自行验证）
- 数据链路：系统回调/查询 → Room → 本地 SQLite，仅此而已
- 提供 JSON / CSV 导出与一键全删
- 无 Root / 无 Shizuku / 无 Accessibility / 无自建 FGS / 无持续 wakelock / 无剪贴板与定位
- 最终权限清单（`INTERNET` 缺席，其余为系统库运行所需）：
  - `PACKAGE_USAGE_STATS` — 数据源 B（用户手动授予的 App Ops）
  - `QUERY_ALL_PACKAGES` — 解析任意包名的应用显示名（个人侧载用途）
  - `WAKE_LOCK` / `RECEIVE_BOOT_COMPLETED` / `FOREGROUND_SERVICE` / `ACCESS_NETWORK_STATE` —
    WorkManager 库自身合并引入：重启后恢复任务调度、执行 worker 的瞬态唤醒；
    本应用从不启动前台服务，也不申请任何网络约束

## 架构

```
Android
   │
   ├── NotificationListenerService   事件驱动，通知到达即毫秒级落库
   │
   └── UsageStatsManager
        └── WorkManager 30min 周期 + 打开 App 时 expedited 补一次
                queryEvents(游标-60s, now) → 批量幂等 upsert → 游标前移
   │
   ▼
Event Normalizer（event_hash 唯一索引，SHA-256）
   ▼
Room（timeline_events + sync_state）
   ▼
TimelineReducer（读时去噪：突发合并 / 页内切换 / 会话折叠）
   ▼
TimelineRepository  ←—— 未来 Agent 的接入面（MCP/REST 在此之上叠加）
   ▼
Compose UI（时间轴 / 统计 / 设置）
```

### 关键设计

| 主题 | 方案 |
|---|---|
| 事件时间 ≠ 采集时间 | DB 存系统记录的原始毫秒时间戳，Worker 延迟不破坏时间轴 |
| 幂等去重 | `event_hash` 唯一索引：usage `type\|timestamp\|pkg`；通知 `type\|notificationKey` |
| 通知 update | 同 key 只刷新原行内容（保留首次 postTime），不产生新时间轴事件 |
| safety window | usage 游标回退 60s 重扫，重叠由唯一索引吞掉 |
| App 打开去噪 | 读时折叠：<45s 同包突发合并；PAUSED+同包 RESUMED 视为页内切换 |
| 展示过滤 | 桌面/SystemUI 事件照常入库，仅 UI 不展示 |

## 首次使用（两步授权）

1. 安装 APK 后打开 App →「设置」页
2. 点击「通知访问」chip → 系统设置里启用 *Personal Context*
3. 点击「使用情况访问」chip → 系统设置里允许

之后即使 App 被杀/手机重启：通知监听由系统自动重绑，Usage Worker 由
WorkManager 自动恢复调度，无需任何手动干预。

## 验收脚本（对应产品定义 §24）

1. 授权后正常使用手机：打开微信 → 收一条微信消息 → 打开 Chrome → 打开腾讯会议 → 退出
2. 回到 App「时间轴」页应看到（时间为示例）：
   ```
   09:12  打开 微信
   09:13  微信
         张三：晚上一起吃饭吗？
   09:18  打开 Chrome
   09:26  打开 腾讯会议
   10:31  离开 腾讯会议
   ```
   （App 使用事件最长有 30 分钟采集延迟；打开 App 会触发一次即时同步）
3. 重启手机 → 「设置」页确认两个权限仍为 ON，事件继续累积
4. 功耗：系统电池面板查看本应用后台耗电应接近 0（无前台服务、无后台定位）

## 故障排查：通知一条都没记录（真机）

按设置页「采集器 → 通知监听」的三态定位：

| 状态行显示 | 含义 | 处理 |
|---|---|---|
| 从未连接 | 授权未生效或绑定从未建立 | 见 ①② |
| 已断开 · 时间 | 绑定被 ROM/进程回收断开 | 见 ③ |
| 已连接 · 时间 | 监听活着但没事件 | 见 ④ |

① **MIUI/ColorOS 等国产 ROM 自启动限制**：系统设置 → 应用 → Personal Context → 开启「自启动」，省电策略改「无限制」；MIUI 还需在「通知使用权」白名单里允许本应用。

② **Android 13+ 侧载应用的「受限制的设置」**：安装 APK（非商店渠道）后，系统会禁止开启通知访问这类特殊权限。系统设置 → 应用 → Personal Context → 右上角 ⋮ → 「允许受限制的设置」，然后重新开通知访问。**开关看似打开但实际未生效多半是这个。**

③ **绑定断开自愈**：v0.1.1 起每次打开 App 会主动 `requestRebind()` 修复断开的绑定；日常用只要偶尔打开一次本应用即可自愈。

④ **微信侧没有发系统通知**：消息到达时下拉通知栏——若通知栏本身没有微信通知，是微信内「新消息通知」或系统级微信通知渠道被关，与本应用无关。

## 开发

```bash
./gradlew :app:assembleDebug        # 构建 APK（app/build/outputs/apk/debug/）
./gradlew :app:testDebugUnitTest    # 全部单测（JVM + Robolectric，无需设备）
```

测试覆盖（34 个用例）：

- `TimelineReducerTest` — 去噪折叠全规则：突发合并/页内切换/切包关闭/漏 PAUSED/长间隔重开/尾部开放/迟到 close/重叠重扫幂等/乱序输入
- `EventHashTest` — 唯一性指纹的稳定性与敏感性
- `NotificationLifecycleTest`（Robolectric + 真 Room）— post/update/remove 生命周期：首时间保留、内容刷新、重复 remove 幂等、移除后重发
- `UsageIngestionTest`（注入事件源）— 首次运行不回溯、事件按原始时间戳入库、safety window 重扫去重、Doze 延迟补齐、非 Activity 事件过滤、STOPPED 映射
- `RepositoryScenarioTest` — spec §24 验收场景端到端：时间轴输出顺序/展示噪音过滤/REMOVED 不展示/统计聚合（时长、通知计数、总数）
- `ExporterTest` — CSV 转义（引号/逗号/换行）、JSON 结构可解析

- minSdk 31（Android 12）/ target & compile 35 / Kotlin 2.0 + Compose M3 + Room + WorkManager
- minSdk 31 的直接收益：Dynamic Color 全量可用；expedited Work 不退化 FGS

### 模块

```
app/src/main/java/com/fish/personalcontext/
├── App.kt                     Application + 手动依赖容器 + Worker 调度
├── MainActivity.kt            入口；onStart 触发一次 expedited 同步
├── domain/                    EventType / TimelineEvent / EventHash / TimelineReducer
├── data/
│   ├── db/                    Room：实体 / DAO / AppDatabase / SyncKeys
│   ├── appinfo/               AppInfoCache（PackageManager label 缓存 + 展示噪音过滤）
│   ├── notification/          NotificationCollector（系统绑定服务）+ Ingestor（落库）
│   ├── usage/                 UsageStatsCollector（增量游标）+ UsageSyncWorker（30min）
│   ├── maintenance/           CleanupWorker（每日按保留策略清理）
│   └── repository/            TimelineRepository（Agent 接入面）+ Exporter（JSON/CSV）
├── ui/                        Compose：timeline / statistics / settings
└── util/                      Permissions / TimeFmt
```

## 数据保留

设置 → 数据保留：永久（默认）/ 7 天 / 30 天 / 90 天 / 1 年。
清理由每日 CleanupWorker 执行。系统 UsageStats 本身只有约 7 天历史，
本应用采集后自存，长期时间轴不随系统日志滚动而丢失。
