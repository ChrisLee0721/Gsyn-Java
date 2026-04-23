# Android Java 开发技术文档 — Gsyn Java

> English version: [ANDROID_DEV_GUIDE.md](ANDROID_DEV_GUIDE.md)

本文档面向 Android 开发者，详细说明项目的 UI 层实现、数据层设计、主题系统、
国际化方案和各关键组件的代码级实现细节，适用于技术汇报和二次开发参考。

---

## 目录

1. [项目结构总览](#1-项目结构总览)
2. [构建配置](#2-构建配置)
3. [应用入口与单例装配](#3-应用入口与单例装配)
4. [导航架构 (MainActivity)](#4-导航架构-mainactivity)
5. [数据库设计 (AppDatabaseHelper)](#5-数据库设计-appdatabasehelper)
6. [数据访问层 (AppRepository)](#6-数据访问层-apprepository)
7. [规则引擎 (RulesEngine)](#7-规则引擎-rulesengine)
8. [Dashboard 卡片系统](#8-dashboard-卡片系统)
9. [主题系统 (AppThemeConfig)](#9-主题系统-appthemeconfig)
10. [国际化 (LocaleHelper)](#10-国际化-localehelper)
11. [ViewBinding 使用规范](#11-viewbinding-使用规范)
12. [Fragment 生命周期与监听器管理](#12-fragment-生命周期与监听器管理)
13. [自定义 View — MiniTrendChartView](#13-自定义-view--minitrendchartview)
14. [Google Maps 集成](#14-google-maps-集成)
15. [SharedPreferences 键值完整表](#15-sharedpreferences-键值完整表)

---

## 1. 项目结构总览

```
app/src/main/java/com/opensynaptic/gsynjava/
├── AppController.java              ← 顶层单例，装配所有组件
├── core/
│   ├── AppThemeConfig.java         ← 主题预设枚举 + SharedPreferences 读写
│   ├── AppThresholds.java          ← 传感器告警阈值常量
│   ├── LocaleHelper.java           ← 语言切换（AppCompatDelegate 方案）
│   ├── AppColors.java              ← 运行时颜色工具方法
│   └── protocol/                   ← 纯 Java 协议层（无 Android 依赖）
│       ├── PacketDecoder.java
│       ├── PacketBuilder.java
│       ├── DiffEngine.java
│       ├── BodyParser.java
│       ├── Base62Codec.java
│       ├── OsCmd.java
│       ├── OsCrc.java
│       ├── ProtocolConstants.java
│       └── GeohashDecoder.java
├── data/
│   ├── AppDatabaseHelper.java      ← SQLiteOpenHelper，建表 + 版本管理
│   ├── AppRepository.java          ← 所有数据库读写操作（单例）
│   └── Models.java                 ← 纯 Java 数据模型（无 ORM 注解）
├── rules/
│   └── RulesEngine.java            ← 阈值自动化规则评估与执行
├── transport/
│   └── TransportManager.java       ← UDP + MQTT 收发（单例）
└── ui/
    ├── MainActivity.java           ← DrawerLayout + BottomNav 宿主 Activity
    ├── SecondaryActivity.java      ← 已弃用，保留兼容性
    ├── common/
    │   ├── BaseSecondaryFragment.java  ← 侧边抽屉页 Fragment 基类
    │   └── UiFormatters.java           ← 时间、数字、单位格式化工具
    ├── dashboard/
    │   ├── DashboardFragment.java      ← Dashboard 主页，实现双监听器接口
    │   ├── DashboardCardAdapter.java   ← RecyclerView 多类型适配器 + Snapshot
    │   ├── DashboardCardConfig.java    ← 卡片配置序列化（JSON → SharedPreferences）
    │   └── DashboardCardItem.java      ← 卡片数据类，定义 Type 枚举（9 种）
    ├── devices/                    ← 设备列表 + 详情底部 Sheet
    ├── alerts/                     ← 告警列表 + 确认操作
    ├── send/                       ← 命令构建页（3 个 Tab）
    ├── settings/                   ← UDP/MQTT 配置 + 主题/语言设置
    ├── mirror/
    │   ├── MapMirrorFragment.java      ← Google Maps + 设备标记
    │   ├── HistoryMirrorFragment.java  ← 历史传感器表格 + CSV 导出
    │   ├── RulesMirrorFragment.java    ← 规则 CRUD + 开关
    │   └── HealthMirrorFragment.java   ← 传输状态 + 数据库统计
    └── widget/
        └── MiniTrendChartView.java     ← 纯 Canvas 趋势折线图
```

---

## 2. 构建配置

**`app/build.gradle` 关键配置：**

```groovy
android {
    compileSdk 34
    defaultConfig {
        minSdk 24          // Android 7.0
        targetSdk 34
        // Maps API Key 通过 local.properties 注入，在 AndroidManifest 中用 ${MAPS_API_KEY}
        manifestPlaceholders = [mapsApiKey: MAPS_API_KEY]
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding true   // 启用 ViewBinding，所有 layout 自动生成绑定类
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
    implementation 'com.google.android.gms:play-services-maps:18.2.0'
}
```

**Maps API Key 注入流程：**

```
local.properties
  MAPS_API_KEY=AIzaSy...
        ↓  (build.gradle 读取)
manifestPlaceholders["mapsApiKey"]
        ↓  (AndroidManifest.xml 引用)
<meta-data android:name="com.google.android.geo.API_KEY"
           android:value="${mapsApiKey}" />
```

---

## 3. 应用入口与单例装配

所有单例在 `AppController.get(context)` 首次调用时统一初始化，顺序严格：

```
Activity.onCreate()
  │
  └── AppController.get(this)
        │
        ├── 1. SharedPreferences "gsyn_java_prefs"
        ├── 2. AppRepository.get(context)
        │         └── AppDatabaseHelper(context)  // 触发 SQLite onCreate()
        ├── 3. TransportManager.get(context)
        │         └── ScheduledExecutorService (1秒 stats 定时器)
        ├── 4. RulesEngine(repository, transportManager)
        │         └── lastTriggered HashMap<Long, Long>
        ├── 5. transportManager.addMessageListener(appController)
        └── 6. repository.seedDefaultRuleIfEmpty()
                  └── 若 rules 表为空，插入默认规则 "TEMP > 50 create_alert"
```

**为什么不用 Application 子类？**  
项目刻意避免 `Application.onCreate()` 中的早期初始化，以防在测试环境下产生副作用。
`AppController.get(context)` 是懒初始化单例，首次被 Activity 调用时才构建。

---

## 4. 导航架构 (MainActivity)

### 布局结构

```xml
DrawerLayout (activity_main.xml)
├── CoordinatorLayout
│   ├── MaterialToolbar (binding.toolbar)
│   ├── FrameLayout (binding.fragmentContainer) ← 所有 Fragment 在此加载
│   └── BottomNavigationView (binding.bottomNav)
└── NavigationView (binding.navView)             ← 侧边抽屉
```

### Fragment 替换机制

```java
// showFragment() — 所有导航均调用此方法
getSupportFragmentManager().beginTransaction()
    .replace(R.id.fragment_container, fragment)
    .commit();
```

每次导航都是 `replace()`（非 `add()`），旧 Fragment 会被销毁。
这意味着 Fragment 的 `onStop()` → `onDestroyView()` → `onDestroy()` 会被调用，
监听器在 `onStop()` 中自动注销，不会泄漏。

### 底部导航与侧边抽屉同步

| 操作 | 处理方式 |
|------|---------|
| 点击底部导航 Tab | `onBottomNavSelected()` → `showFragment()` + 同步抽屉选中项 |
| 点击抽屉主菜单 | `onNavigationItemSelected()` → `bottomNav.setSelectedItemId()` → 触发底部导航回调 |
| 点击抽屉扩展页 | 直接 `showFragment()` + 清除底部导航选中状态（通过 `setGroupCheckable` 技巧） |

**清除底部导航选中状态的技巧：**
```java
binding.bottomNav.getMenu().setGroupCheckable(0, true, false); // 关闭互斥选中
for (int i = 0; i < binding.bottomNav.getMenu().size(); i++) {
    binding.bottomNav.getMenu().getItem(i).setChecked(false);
}
binding.bottomNav.getMenu().setGroupCheckable(0, true, true);  // 恢复互斥选中
```

### 主题叠加的时机要求

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    // ① 必须在 super.onCreate() 之前 — Material3 在 super 中读取主题属性
    getTheme().applyStyle(AppThemeConfig.getAccentOverlayRes(...), true);
    getTheme().applyStyle(AppThemeConfig.getBgOverlayRes(...), true);

    super.onCreate(savedInstanceState);    // ← super 在此读取主题

    // ② 必须在 setContentView() 之后 — Window 在 super.onCreate() 中创建
    AppController.get(this);
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    // ③ 状态栏/导航栏颜色在 setContentView() 之后同步
    AppThemeConfig.applyBgToWindow(getWindow(), this);
}
```

---

## 5. 数据库设计 (AppDatabaseHelper)

### 数据库文件

- **文件名**：`gsyn_java.db`（`AppDatabaseHelper.DB_NAME`）
- **版本**：`1`（`AppDatabaseHelper.DB_VERSION`）
- **位置**：Android 内部存储 `data/data/com.opensynaptic.gsynjava/databases/gsyn_java.db`

### 完整建表 SQL

```sql
-- 设备表
CREATE TABLE devices (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    aid             INTEGER UNIQUE NOT NULL,     -- 4字节设备ID（协议层为uint32）
    name            TEXT    NOT NULL DEFAULT '',
    type            TEXT    NOT NULL DEFAULT 'sensor',
    lat             REAL    DEFAULT 0.0,
    lng             REAL    DEFAULT 0.0,
    status          TEXT    NOT NULL DEFAULT 'offline',
    transport_type  TEXT    NOT NULL DEFAULT 'udp',
    last_seen_ms    INTEGER NOT NULL DEFAULT 0
);

-- 传感器时序数据
CREATE TABLE sensor_data (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    device_aid  INTEGER NOT NULL,
    sensor_id   TEXT    NOT NULL,
    unit        TEXT    NOT NULL DEFAULT '',
    value       REAL    NOT NULL,
    raw_b62     TEXT    DEFAULT '',
    timestamp_ms INTEGER NOT NULL
);
CREATE INDEX idx_sensor_data_aid_ts ON sensor_data(device_aid, timestamp_ms);

-- 告警记录
CREATE TABLE alerts (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    device_aid   INTEGER NOT NULL,
    sensor_id    TEXT    NOT NULL DEFAULT '',
    level        INTEGER NOT NULL DEFAULT 0,      -- 0=Info, 1=Warning, 2=Critical
    message      TEXT    NOT NULL DEFAULT '',
    acknowledged INTEGER NOT NULL DEFAULT 0,      -- 0=未确认, 1=已确认
    created_ms   INTEGER NOT NULL
);
CREATE INDEX idx_alerts_aid_level ON alerts(device_aid, level);

-- 自动化规则
CREATE TABLE rules (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             TEXT    NOT NULL DEFAULT '',
    device_aid_filter INTEGER DEFAULT NULL,       -- NULL = 匹配所有设备
    sensor_id_filter TEXT    DEFAULT NULL,        -- NULL = 匹配所有传感器
    operator         TEXT    NOT NULL DEFAULT '>', -- >, <, >=, <=, ==, !=
    threshold        REAL    NOT NULL DEFAULT 0.0,
    action_type      TEXT    NOT NULL DEFAULT 'create_alert',
    action_payload   TEXT    NOT NULL DEFAULT '{}', -- JSON 参数
    enabled          INTEGER NOT NULL DEFAULT 1,
    cooldown_ms      INTEGER NOT NULL DEFAULT 60000
);

-- 操作审计日志
CREATE TABLE operation_logs (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user         TEXT    NOT NULL DEFAULT 'system',
    action       TEXT    NOT NULL DEFAULT '',
    details      TEXT    NOT NULL DEFAULT '',
    timestamp_ms INTEGER NOT NULL
);

-- 用户管理
CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT UNIQUE NOT NULL,
    password_hash TEXT    NOT NULL,    -- SHA-256 十六进制字符串
    role          TEXT    NOT NULL DEFAULT 'viewer',  -- 'admin' | 'viewer'
    created_ms    INTEGER NOT NULL
);

-- Dashboard 布局（保留字段）
CREATE TABLE dashboard_layout (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER,
    layout_json TEXT    NOT NULL DEFAULT '{}'
);

-- 待发命令队列
CREATE TABLE pending_commands (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    device_aid  INTEGER NOT NULL,
    frame_hex   TEXT    NOT NULL,
    created_ms  INTEGER NOT NULL
);
```

### 初始数据

数据库创建时自动插入默认管理员账户：
- **用户名**：`admin`
- **密码哈希**：`8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918`（SHA-256 of `"admin"`）
- **角色**：`admin`

### 索引设计

| 索引名 | 表 | 列 | 用途 |
|--------|----|----|------|
| `idx_sensor_data_aid_ts` | `sensor_data` | `(device_aid, timestamp_ms)` | 按设备+时间范围查询 |
| `idx_alerts_aid_level` | `alerts` | `(device_aid, level)` | 按设备+级别过滤告警 |
| `devices.aid` | `devices` | `aid` | `UNIQUE` 约束，upsert 查询 |

---

## 6. 数据访问层 (AppRepository)

### 并发安全

所有公共方法均标注 `synchronized`，保证多线程（UDP线程、MQTT回调线程、UI线程）
并发访问时的数据一致性。

### 关键方法详解

#### upsertDevice()

```java
// 保留现有坐标的 Upsert 逻辑：
// 仅当新消息携带非零坐标时才更新 lat/lng，防止心跳包覆盖已有位置信息
boolean hasNewCoords = Math.abs(device.lat) > 1e-7 || Math.abs(device.lng) > 1e-7;
values.put("lat", hasNewCoords ? device.lat : existingLat);
values.put("lng", hasNewCoords ? device.lng : existingLng);
```

#### getOnlineDeviceCount()

```java
// "在线"定义：最近 5 分钟内有数据到达
long cutoff = System.currentTimeMillis() - 5 * 60_000L;
SELECT COUNT(*) FROM devices WHERE last_seen_ms > ?
```

#### getLatestReadingsByDevice()

```sql
-- 每个传感器只取最新一条（MAX(id) 等价于最新插入）
SELECT * FROM sensor_data
WHERE id IN (
    SELECT MAX(id) FROM sensor_data
    WHERE device_aid = ?
    GROUP BY sensor_id
)
```

#### exportHistoryCsv()

```java
// 导出最近 24 小时，最多 500 条
// 文件保存到外部存储：getExternalFilesDir(null)/export_{timestamp}.csv
// CSV 格式：timestamp,device_aid,sensor_id,value,unit
```

#### seedDefaultRuleIfEmpty()

```java
// 首次安装时自动创建一条示例规则
rule.name = "TEMP > 50 create_alert";
rule.sensorIdFilter = "TEMP";
rule.operator = ">";
rule.threshold = 50;
rule.actionType = "create_alert";
```

### Repository API 完整参考

| 方法 | 参数 | 返回 | SQL 类型 |
|------|------|------|----------|
| `upsertDevice(device)` | `Models.Device` | void | SELECT + UPDATE/INSERT |
| `getAllDevices()` | — | `List<Device>` | SELECT ORDER BY last_seen_ms DESC |
| `getTotalDeviceCount()` | — | int | COUNT(*) |
| `getOnlineDeviceCount()` | — | int | COUNT(*) WHERE last_seen_ms > cutoff |
| `insertSensorData(data)` | `SensorData` | void | INSERT |
| `getLatestReadingsByDevice(aid)` | int | `List<SensorData>` | MAX(id) GROUP BY sensor_id |
| `querySensorData(from,to,limit)` | long,long,int | `List<SensorData>` | SELECT WHERE ts BETWEEN |
| `insertAlert(alert)` | `AlertItem` | long (row id) | INSERT |
| `getAlerts(level,limit)` | Integer,int | `List<AlertItem>` | SELECT ORDER BY created_ms DESC |
| `getUnacknowledgedAlertCount()` | — | int | COUNT WHERE acknowledged=0 |
| `acknowledgeAlert(id)` | long | void | UPDATE SET acknowledged=1 |
| `getAllRules()` | — | `List<Rule>` | SELECT ORDER BY id ASC |
| `getEnabledRules()` | — | `List<Rule>` | SELECT WHERE enabled=1 |
| `saveRule(rule)` | `Rule` | long (row id) | INSERT 或 UPDATE（按 rule.id > 0 判断） |
| `toggleRule(id,enabled)` | long,boolean | void | UPDATE SET enabled |
| `deleteRule(id)` | long | void | DELETE WHERE id=? |
| `logOperation(action,details)` | String,String | void | INSERT，user 固定为 "system" |
| `getOperationLogs(limit)` | int | `List<OperationLog>` | SELECT ORDER BY ts DESC |
| `getDatabaseSizeBytes()` | — | long | `File.length()` |
| `pruneOldData(retentionDays)` | int | int（删除行数） | DELETE WHERE ts < cutoff |
| `exportHistoryCsv()` | — | `File` | SELECT 24h 最多500条，写 CSV |

---

## 7. 规则引擎 (RulesEngine)

### 冷却机制

冷却状态存储在内存 `HashMap<Long, Long> lastTriggered`（`ruleId → 上次触发时间戳`）。
应用重启后冷却状态重置，不持久化。

### 告警级别计算

```java
// create_alert 动作执行时的级别判断：
alert.level = reading.value > rule.threshold * 1.5 ? 2 : 1;
//            超过阈值 150% → Critical(2)，否则 → Warning(1)
```

### send_command 的 actionPayload 格式

`actionPayload` 是 JSON 字符串，在 `send_command` 动作执行时解析：

```json
{
    "target_aid": 2,        // 目标设备 AID（默认为触发设备自身）
    "sensor_id": "CMD",     // 发送包的传感器 ID
    "unit": "",             // 单位字符串
    "value": 0.0            // 命令值
}
```

构建 `DATA_FULL` 包并调用 `transportManager.sendCommand()`，优先走 MQTT，若未连接则走 UDP。

### 评估流程

```
evaluate(message, udpHost, udpPort):
  1. repository.getEnabledRules()     ← 每次都从数据库读取最新规则
  2. for each reading in message.readings:
       for each rule in enabledRules:
         a. 设备过滤：rule.deviceAidFilter != null && != message.deviceAid → skip
         b. 传感器过滤：rule.sensorIdFilter != null && !equalsIgnoreCase(reading.sensorId) → skip
         c. 阈值评估：rule.evaluate(reading.value) → false → skip
         d. 冷却检查：now - lastTriggered[rule.id] < rule.cooldownMs → skip
         e. 更新 lastTriggered[rule.id] = now
         f. execute(rule, message, reading, udpHost, udpPort)
```

---

## 8. Dashboard 卡片系统

### 卡片类型枚举（DashboardCardItem.Type）

| 枚举值 | 描述 | 可拖动 |
|--------|------|--------|
| `HEADER` | 固定标题卡（始终第一位） | ❌ |
| `KPI_ROW1` | 总设备数 + 在线率 | ✅ |
| `KPI_ROW2` | 活跃告警 + 吞吐量 | ✅ |
| `KPI_ROW3` | 活跃规则数 + 累计消息数 | ✅ |
| `GAUGES` | 液位 + 湿度进度条 | ✅ |
| `CHARTS` | 温度 + 湿度趋势折线图 | ✅ |
| `ACTIVITY` | 近期告警 + 操作日志 | ✅ |
| `LATEST_READINGS` | 最新原始传感器读数 | ✅ |
| `CUSTOM_SENSOR` | 用户自定义传感器卡片 | ✅ |

### Snapshot 数据契约

`DashboardCardAdapter.Snapshot` 是每次 `refresh()` 构建一次的不可变数据快照，
所有 ViewHolder 共享同一个 Snapshot，确保数据库只查询一次：

```java
class Snapshot {
    // KPI
    int totalDevices, online, alerts, rules, totalMessages, throughput;

    // 传感器最新值（传感器ID大写 → SensorData）
    Map<String, SensorData> latestBySensorId;
    // 趋势数据（传感器ID → 最近12个值，时间正序）
    Map<String, List<Float>> trendBySensorId;

    // 内置传感器快捷字段
    double latestTemp, latestHum, latestPressure, latestLevel;
    List<Float> tempTrend = new ArrayList<>();
    List<Float> humTrend  = new ArrayList<>();

    // 文本摘要
    String subtitle, syncStatus, transportStatus;
    String latestReadingsText, recentAlertsSummary, opsSummary;

    long   latestSampleMs;
    int    readingCount;
    boolean singleModeEnabled;
}
```

**为什么用 Snapshot？**
- 数据库每次 refresh 只查询一次（而不是每个 ViewHolder 各自查询）
- ViewHolder 无状态，可以安全回收
- 新增卡片类型只需在 `Snapshot` 中加字段 + 在 `refresh()` 中赋值

### 传感器分配逻辑（refresh() 中）

```java
// 传感器 ID 匹配规则（contains 而非 equals，支持 T1/TEMP2 等变体）
if (sid.contains("TEMP") || sid.contains("TMP") || sid.equals("T1"))  → snap.latestTemp
if (sid.contains("HUM")  || sid.equals("H1"))                         → snap.latestHum
if (sid.contains("PRES") || sid.contains("BAR") || sid.equals("P1"))  → snap.latestPressure
if (sid.contains("LEVEL")|| sid.contains("LVL") || sid.equals("L1"))  → snap.latestLevel
```

历史数据查询范围：**最近 24 小时，最多 50 条**（按 `timestamp_ms DESC`）。

### 拖拽重排序实现

```java
ItemTouchHelper.Callback:
  getMovementFlags(): position==0（HEADER）返回 0，不允许拖动
  onMove(): 调用 cardAdapter.moveItem(from, to) — Collections.swap + notifyItemMoved
  clearView(): 拖动结束后调用 persistOrderFromAdapter()
                → 重建 cardConfig.cards（可见顺序 + 隐藏卡片追加到末尾）
                → cardConfig.save(context) 持久化到 SharedPreferences
```

### 卡片配置持久化（DashboardCardConfig）

卡片顺序和可见性序列化为 JSON 字符串，存储在 SharedPreferences key `"dashboard_cards"`：

```json
[
  {"type":"HEADER","visible":true,"order":0,"sensorId":"","label":""},
  {"type":"KPI_ROW1","visible":true,"order":1,"sensorId":"","label":""},
  {"type":"CUSTOM_SENSOR","visible":true,"order":5,"sensorId":"CO2","label":"二氧化碳"}
]
```

---

## 9. 主题系统 (AppThemeConfig)

### 两层叠加架构

```
Theme.GsynJava (themes.xml)          ← Material3 基础主题
    +
ThemeOverlay_GsynJava_Accent_Xxx     ← 强调色叠加（colorPrimary / colorSecondary）
    +
ThemeOverlay_GsynJava_Bg_Xxx         ← 背景色叠加（colorBackground / colorSurface）
```

两个叠加层在 `Activity.onCreate()` 中用 `getTheme().applyStyle(resId, true)` 顺序应用，
后者覆盖前者的同名属性。

### 8 种强调色预设（ThemePreset）

| 枚举 | 十六进制 | 显示名 |
|------|---------|--------|
| `DEEP_BLUE` | `#1A73E8` | Deep Blue（默认） |
| `TEAL` | `#00897B` | Teal |
| `PURPLE` | `#7B1FA2` | Purple |
| `AMBER` | `#FF8F00` | Amber |
| `RED` | `#D32F2F` | Red |
| `CYAN` | `#0097A7` | Cyan |
| `GREEN` | `#2E7D32` | Green |
| `PINK` | `#C2185B` | Pink |

### 12 种背景预设（BgPreset）

每个预设有 3 个颜色槽（`bgHex` / `surfaceHex` / `cardHex`）和一个亮暗标志（`isLight`）：

**暗色系（6种）**：`DEEP_NAVY`（默认）、`DARK_SLATE`、`CHARCOAL`、`TRUE_BLACK`（AMOLED）、`FOREST_DARK`、`WARM_DARK`

**亮色系（6种）**：`SNOW_WHITE`、`CLOUD_GREY`、`PAPER_CREAM`、`MINT_LIGHT`、`LAVENDER_LIGHT`、`SKY_BLUE`

### 状态栏 / 导航栏颜色同步

```java
applyBgToWindow(Window, Context):
    window.setStatusBarColor(bg.bgColor())
    window.setNavigationBarColor(bg.bgColor())
    // 根据 isLight 切换状态栏图标颜色（深色 / 浅色）：
    if (bg.isLight) flags |= SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    else            flags &= ~SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    // Android 8+ 同步导航栏图标颜色
    if (API >= O) flags |= / &= ~SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
```

### SharedPreferences 存储

- **文件名**：`app_theme_prefs`（独立于主 prefs 文件）
- **键**：`app_theme_preset`（ThemePreset.name()），`app_bg_preset`（BgPreset.name()）

---

## 10. 国际化 (LocaleHelper)

### 实现方案

使用 **AppCompat 1.6+ 的 per-app locale API**，无需重写 `attachBaseContext()`：

```java
// 切换语言（持久化 + 自动触发 Activity recreation）
LocaleHelper.applyAndSave("zh");  // 中文
LocaleHelper.applyAndSave("en");  // 英文
LocaleHelper.applyAndSave("system"); // 跟随系统
```

```java
// 实现细节
AppCompatDelegate.setApplicationLocales(
    LocaleListCompat.forLanguageTags("zh")   // 等价于 BCP 47 语言标签
)
// AppCompat 自动持久化到系统并 recreate() 当前 Activity
```

### Android 13+ 声明要求

`AndroidManifest.xml` 中必须声明：
```xml
<application android:localeConfig="@xml/locale_config" ...>
```

`res/xml/locale_config.xml`：
```xml
<locale-config>
    <locale android:name="en"/>
    <locale android:name="zh"/>
</locale-config>
```

### 语言常量

```java
LocaleHelper.LANG_SYSTEM = "system"  // 跟随系统
LocaleHelper.LANG_EN     = "en"      // 英文
LocaleHelper.LANG_ZH     = "zh"      // 中文
```

### 字符串资源位置

| 路径 | 语言 |
|------|------|
| `res/values/strings.xml` | 英文（基础） |
| `res/values-zh/strings.xml` | 中文（覆盖） |

**所有用户可见文字必须通过 strings.xml 引用**，Java 代码中的硬编码字符串不会响应语言切换。

---

## 11. ViewBinding 使用规范

### 启用

`app/build.gradle`：
```groovy
buildFeatures { viewBinding true }
```

启用后，每个 layout XML 文件自动生成绑定类，命名规则为 Pascal-case + `Binding`后缀：
- `activity_main.xml` → `ActivityMainBinding`
- `fragment_dashboard.xml` → `FragmentDashboardBinding`

### Activity 中使用

```java
private ActivityMainBinding binding;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    // 之后通过 binding.toolbar / binding.bottomNav 等直接访问
}
```

### Fragment 中使用（含内存泄漏防护）

```java
private FragmentDashboardBinding binding;

@Override
public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
    binding = FragmentDashboardBinding.inflate(inflater, container, false);
    return binding.getRoot();
}

@Override
public void onDestroyView() {
    binding = null;  // ← 必须置 null，防止持有已销毁 View 的引用
    super.onDestroyView();
}
```

**注意**：Fragment 的 View 生命周期比 Fragment 本身短（`onDestroyView` 早于 `onDestroy`），
不置 null 会导致内存泄漏。

---

## 12. Fragment 生命周期与监听器管理

### 规范模式（以 DashboardFragment 为例）

```java
@Override
public void onStart() {
    super.onStart();
    // 在 Fragment 可见时注册监听器
    transportManager.addMessageListener(this);
    transportManager.addStatsListener(this);
    refresh(); // 立即刷新一次，避免显示过期数据
}

@Override
public void onStop() {
    // 在 Fragment 不可见时注销监听器，防止后台更新和泄漏
    transportManager.removeMessageListener(this);
    transportManager.removeStatsListener(this);
    super.onStop();
}
```

**选择 onStart/onStop 而非 onResume/onPause 的原因：**
在多窗口模式下，`onPause` 会在窗口失焦时触发（但 Fragment 仍可见）；
`onStop` 才代表 Fragment 真正不可见。

### UI 线程切换

`TransportManager` 的回调在后台线程执行，所有 UI 操作必须切换：

```java
@Override
public void onMessage(Models.DeviceMessage message) {
    if (getActivity() != null) {     // ← 防止 Fragment 已 detach
        getActivity().runOnUiThread(this::refresh);
    }
}
```

---

## 13. 自定义 View — MiniTrendChartView

`MiniTrendChartView` 继承 `View`，使用纯 Canvas API 绘制趋势折线图，无第三方图表库依赖。

### 核心 API

```java
chart.setTitle("Temperature");
chart.setChartColor(0xFFFF7043);         // 线条 + 渐变填充颜色（ARGB）
chart.setSeries(List<Float> values);     // 设置数据，自动触发 invalidate()
```

### 绘制特性

- **渐变填充**：折线下方绘制从线条颜色到透明的垂直渐变（`LinearGradient`）
- **网格线**：水平虚线网格，颜色为前景色 20% 透明度
- **极值标注**：最高点和最低点绘制高亮圆点（半径 4dp）
- **自动 Y 轴范围**：min/max 自动从数据推导，留 10% 上下边距

### 在 Dashboard 中的使用

```java
// 温度趋势卡片（CHARTS 类型）
binding.chartTemp.setTitle(getString(R.string.dashboard_temp_title));
binding.chartTemp.setChartColor(0xFFFF7043);
binding.chartTemp.setSeries(snapshot.tempTrend);  // List<Float>，最多12个点

// 湿度趋势卡片
binding.chartHum.setTitle(getString(R.string.dashboard_hum_title));
binding.chartHum.setChartColor(0xFF42A5F5);
binding.chartHum.setSeries(snapshot.humTrend);
```

---

## 14. Google Maps 集成

### MapMirrorFragment 中的 SupportMapFragment 嵌套

```java
// 必须使用 getChildFragmentManager()（非 getParentFragmentManager()）
// 必须使用 commitNow()（非 commit()），防止异步加载导致的空白地图竞态条件
SupportMapFragment mapFrag = SupportMapFragment.newInstance();
getChildFragmentManager().beginTransaction()
    .add(R.id.mapContainer, mapFrag, "MAP_FRAG")
    .commitNow();    // ← 同步提交，确保 Fragment 附加后再调用 getMapAsync
mapFrag.getMapAsync(this);  // this implements OnMapReadyCallback
```

### 设备标记更新

```java
@Override
public void onMapReady(GoogleMap googleMap) {
    this.googleMap = googleMap;
    // 应用暗色/亮色地图样式
    BgPreset bg = AppThemeConfig.loadBgPreset(requireContext());
    if (!bg.isLight) {
        googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(
            requireContext(), R.raw.map_style_dark));
    }
    refreshMarkers();
}

private void refreshMarkers() {
    // 清除旧标记，为所有有坐标的设备绘制新标记
    googleMap.clear();
    for (Models.Device device : repository.getAllDevices()) {
        if (Math.abs(device.lat) < 1e-7 && Math.abs(device.lng) < 1e-7) continue;
        boolean online = System.currentTimeMillis() - device.lastSeenMs < 5 * 60_000L;
        BitmapDescriptor icon = BitmapDescriptorFactory.defaultMarker(
            online ? BitmapDescriptorFactory.HUE_GREEN
                   : BitmapDescriptorFactory.HUE_RED);
        googleMap.addMarker(new MarkerOptions()
            .position(new LatLng(device.lat, device.lng))
            .title(device.name)
            .icon(icon));
    }
}
```

---

## 15. SharedPreferences 键值完整表

### 主配置文件（`gsyn_java_prefs`）

| 键 | 类型 | 默认值 | 说明 |
|----|------|--------|------|
| `udp_host` | String | `"127.0.0.1"` | UDP 目标主机（用于发送命令） |
| `udp_port` | int | `9876` | UDP 端口（监听 + 发送） |
| `udp_enabled` | boolean | `false` | 启动时自动开启 UDP |
| `mqtt_broker` | String | `""` | MQTT Broker 主机 |
| `mqtt_port` | int | `1883` | MQTT 端口 |
| `mqtt_topic` | String | `""` | MQTT 订阅 topic（空 = 默认 `opensynaptic/#`） |
| `mqtt_user` | String | `""` | MQTT 用户名 |
| `mqtt_pass` | String | `""` | MQTT 密码 |
| `mqtt_enabled` | boolean | `false` | 启动时自动连接 MQTT |
| `single_device_mode` | boolean | `false` | Dashboard 单设备模式 |
| `dashboard_cards` | String | （默认JSON） | 卡片顺序和可见性 JSON |

### 主题配置文件（`app_theme_prefs`）

| 键 | 类型 | 默认值 | 说明 |
|----|------|--------|------|
| `app_theme_preset` | String | `"DEEP_BLUE"` | ThemePreset 枚举名 |
| `app_bg_preset` | String | `"DEEP_NAVY"` | BgPreset 枚举名 |

