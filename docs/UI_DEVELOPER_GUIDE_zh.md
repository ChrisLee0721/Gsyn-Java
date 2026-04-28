# Gsyn-Java UI 层开发者文档

> **包根路径：** `com.opensynaptic.gsynjava.ui`  
> **开发语言：** Java  
> **UI 工具包：** Material Design 3（Material Components for Android）+ AndroidX AppCompat  
> **架构模式：** 单 Activity + 多 Fragment，手动导航（不使用 Jetpack Navigation 组件）

---

## 目录

1. [包结构总览](#1-包结构总览)
2. [Activity 层](#2-activity-层)
   - 2.1 [MainActivity](#21-mainactivity)
   - 2.2 [SecondaryActivity](#22-secondaryactivity)
3. [Fragment 层](#3-fragment-层)
   - 3.1 [alerts — AlertsFragment](#31-alerts--alertsfragment)
   - 3.2 [common — CardRowAdapter 与 UiFormatters](#32-common--cardrowadapter-与-uiformatters)
   - 3.3 [dashboard — DashboardFragment](#33-dashboard--dashboardfragment)
   - 3.4 [dashboard — DashboardCardAdapter](#34-dashboard--dashboardcardadapter)
   - 3.5 [dashboard — DashboardCardConfig](#35-dashboard--dashboardcardconfig)
   - 3.6 [dashboard — DashboardCardItem](#36-dashboard--dashboardcarditem)
   - 3.7 [devices — DevicesFragment](#37-devices--devicesfragment)
   - 3.8 [mirror — HealthMirrorFragment](#38-mirror--healthmirrorfragment)
   - 3.9 [mirror — HistoryMirrorFragment](#39-mirror--historymirrorfragment)
   - 3.10 [mirror — MapMirrorFragment](#310-mirror--mapmirrorfragment)
   - 3.11 [mirror — RulesMirrorFragment](#311-mirror--rulesmirrorfragment)
   - 3.12 [send — SendFragment](#312-send--sendfragment)
   - 3.13 [settings — SettingsFragment](#313-settings--settingsfragment)
4. [自定义视图层](#4-自定义视图层)
   - 4.1 [widget — MiniTrendChartView](#41-widget--minitrendchartview)
5. [导航架构](#5-导航架构)
6. [主题与样式体系](#6-主题与样式体系)
7. [UI 层数据流](#7-ui-层数据流)
8. [命名规范与编码风格](#8-命名规范与编码风格)
9. [扩展 Dashboard 卡片](#9-扩展-dashboard-卡片)
10. [常见坑与 FAQ](#10-常见坑与-faq)

---

## 1. 包结构总览

```
ui/
├── MainActivity.java               ← 单一主 Activity 宿主
├── SecondaryActivity.java          ← 轻量级详情宿主 Activity
├── alerts/
│   └── AlertsFragment.java         ← 告警列表、筛选、确认操作
├── common/
│   ├── CardRowAdapter.java         ← 可复用 ListView 适配器（标题/副标题/元信息/徽章）
│   └── UiFormatters.java           ← 纯静态格式化工具类
├── dashboard/
│   ├── DashboardFragment.java      ← 主看板宿主 + 实时数据编排
│   ├── DashboardCardAdapter.java   ← 可配置卡片列表的 RecyclerView 适配器
│   ├── DashboardCardConfig.java    ← 卡片顺序与可见性的持久化层
│   └── DashboardCardItem.java      ← 单张卡片槽位的数据模型
├── devices/
│   └── DevicesFragment.java        ← 设备列表，支持实时搜索与详情弹窗
├── mirror/
│   ├── HealthMirrorFragment.java   ← 系统健康/传输统计 + 数据库裁剪
│   ├── HistoryMirrorFragment.java  ← 传感器历史数据（近 24 h）+ CSV 导出
│   ├── MapMirrorFragment.java      ← Google Maps 设备位置地图，自动刷新
│   └── RulesMirrorFragment.java    ← 自动化规则增删改查 + 操作日志
├── send/
│   └── SendFragment.java           ← 报文构建器 / 手动协议测试台
├── settings/
│   └── SettingsFragment.java       ← 应用设置：传输、主题、语言、看板
└── widget/
    └── MiniTrendChartView.java     ← 基于 Canvas 绘制的轻量级趋势迷你图
```

**设计哲学：**  
所有 Fragment 均自包含——通过 `AppController.get(context)` 单例直接获取依赖（`repository()` 和 `transport()`），不引入任何依赖注入框架。这样可保持构建简单、启动迅速，非常适合物联网边缘侧使用场景。

---

## 2. Activity 层

### 2.1 `MainActivity`

**文件：** `ui/MainActivity.java`  
**父类：** `AppCompatActivity`  
**实现接口：** `NavigationView.OnNavigationItemSelectedListener`

#### 职责
应用的唯一主屏幕，承载以下元素：
- 顶部 **Toolbar**（标题 + 副标题），与 **DrawerLayout** 联动。
- **NavigationView**（侧滑抽屉），用于次级/高级导航。
- **BottomNavigationView**，用于主选项卡导航。
- **FrameLayout**（`R.id.fragment_container`），所有 Fragment 事务提交于此。

#### 生命周期与主题注入
```java
// 关键：主题覆盖层必须在 super.onCreate() 之前应用
getTheme().applyStyle(AppThemeConfig.getAccentOverlayRes(...), true);
getTheme().applyStyle(AppThemeConfig.getBgOverlayRes(...), true);
super.onCreate(savedInstanceState);
```
`AppThemeConfig.applyBgToWindow(getWindow(), this)` 在 `setContentView()` **之后**调用，以同步状态栏和导航栏颜色至所选背景预设。

#### 字段说明

| 字段 | 类型 | 描述 |
|------|------|------|
| `binding` | `ActivityMainBinding` | 由 `activity_main.xml` 生成的视图绑定对象 |
| `drawerToggle` | `ActionBarDrawerToggle` | 管理汉堡图标 ↔ 返回箭头的抽屉动画 |

#### 导航映射关系

| 底部导航项 ID | 抽屉项 ID | 加载的 Fragment |
|--------------|-----------|----------------|
| `R.id.nav_dashboard` | `R.id.nav_main_dashboard` | `DashboardFragment` |
| `R.id.nav_devices` | `R.id.nav_main_devices` | `DevicesFragment` |
| `R.id.nav_alerts` | `R.id.nav_main_alerts` | `AlertsFragment` |
| `R.id.nav_send` | `R.id.nav_main_send` | `SendFragment` |
| `R.id.nav_settings` | `R.id.nav_main_settings` | `SettingsFragment` |
| *(仅抽屉)* | `R.id.nav_drawer_map` | `MapMirrorFragment` |
| *(仅抽屉)* | `R.id.nav_drawer_history` | `HistoryMirrorFragment` |
| *(仅抽屉)* | `R.id.nav_drawer_rules` | `RulesMirrorFragment` |
| *(仅抽屉)* | `R.id.nav_drawer_health` | `HealthMirrorFragment` |

#### 关键方法

| 方法 | 可见性 | 说明 |
|------|--------|------|
| `onCreate(Bundle)` | `protected` | 完整 Activity 初始化；主题、绑定、抽屉、底部导航 |
| `onBottomNavSelected(MenuItem)` | `private` | 返回 `boolean`；分发到 `showFragment()` |
| `onNavigationItemSelected(MenuItem)` | `public` | 侧滑抽屉处理；镜像 Tab 或加载抽屉专用 Fragment |
| `showFragment(Fragment, String, String)` | `private` | 设置 Toolbar 标题/副标题，提交 `replace()` 事务 |

#### 抽屉与底部导航的同步
当用户点击底部导航项时，通过 `binding.navView.setCheckedItem()` 勾选对应的抽屉项。当用户点击**仅抽屉**专属项（地图、历史、规则、健康）时，所有底部导航项通过切换 group-checkable 状态临时取消勾选：
```java
binding.bottomNav.getMenu().setGroupCheckable(0, true, false); // 允许手动取消选中
for (int i = 0; i < binding.bottomNav.getMenu().size(); i++) {
    binding.bottomNav.getMenu().getItem(i).setChecked(false);
}
binding.bottomNav.getMenu().setGroupCheckable(0, true, true);  // 恢复
```

---

### 2.2 `SecondaryActivity`

**文件：** `ui/SecondaryActivity.java`  
**父类：** `AppCompatActivity`

#### 职责
四个 Mirror Fragment 的轻量级"详情"容器，适用于独立运行的场景（如从通知深链接或外部 Intent 启动）。与 `MainActivity` 不同，它**没有底部导航栏和抽屉**——仅有带返回箭头的 Toolbar。

#### 常量（Intent Extra 键）

| 常量 | 值 | 含义 |
|------|----|------|
| `EXTRA_MODE` | `"mode"` | 选择宿主 Fragment |
| `MODE_HISTORY` | `"history"` | 宿主 `HistoryMirrorFragment` |
| `MODE_MAP` | `"map"` | 宿主 `MapMirrorFragment` |
| `MODE_RULES` | `"rules"` | 宿主 `RulesMirrorFragment` |
| `MODE_HEALTH` | `"health"` | 宿主 `HealthMirrorFragment` |

#### 工厂方法
```java
Intent intent = SecondaryActivity.intent(context, SecondaryActivity.MODE_MAP, R.string.title_map);
startActivity(intent);
```
静态 `intent(Context, String, int)` 工厂方法构建包含 mode 和本地化标题字符串的 Intent，避免在调用处重复定义 Extra 键。

#### Fragment 解析
`fragmentForMode(String mode)` 和 `subtitleFor(String mode)` 均采用相同的 switch-like 逻辑，默认分支使用 `HistoryMirrorFragment`。

---

## 3. Fragment 层

### 3.1 `alerts` — `AlertsFragment`

**文件：** `ui/alerts/AlertsFragment.java`  
**布局：** `fragment_alerts.xml`  
**依赖：** `AppRepository`、`CardRowAdapter`、`UiFormatters`

#### 职责
展示本地数据库中存储的所有告警，提供：
- 实时**摘要行**（Critical / Warning / Info 计数 + 未确认总数）。
- **Spinner 筛选器**：显示全部 / Critical / Warning / Info。
- **SwipeRefreshLayout**：手动刷新。
- **ListView** 使用 `CardRowAdapter` 渲染行；点击未确认告警时调用 `repository.acknowledgeAlert(id)`。

#### 字段说明

| 字段 | 类型 | 描述 |
|------|------|------|
| `binding` | `FragmentAlertsBinding` | 视图绑定；在 `onDestroyView()` 中置空 |
| `repository` | `AppRepository` | 数据访问；从 `AppController` 获取 |
| `currentAlerts` | `List<Models.AlertItem>` | 当前显示的快照（用于点击事件下标映射） |
| `adapter` | `CardRowAdapter` | ListView 适配器 |

#### 严重级别映射

| Spinner 位置 | Level 整数值 | 标签 |
|-------------|-------------|------|
| 0 | `null`（全部） | 全部 |
| 1 | `2` | Critical（严重） |
| 2 | `1` | Warning（警告） |
| 3 | `0` | Info（信息） |

Repository 方法 `getAlerts(Integer level, int limit)` 传入 `null` 表示获取所有级别。

#### 徽章颜色映射
```java
int color = ctx.getColor(
    a.level == 2 ? R.color.gsyn_danger :
    a.level == 1 ? R.color.gsyn_warning :
                   R.color.gsyn_info);
```

#### 生命周期钩子
- `onCreateView` — 填充布局，绑定适配器、Spinner、下拉刷新。
- `onStart` — 触发首次 `load()`。
- `onDestroyView` — 置空 `binding` 防止内存泄漏。

---

### 3.2 `common` — `CardRowAdapter` 与 `UiFormatters`

#### `CardRowAdapter`

**文件：** `ui/common/CardRowAdapter.java`  
**父类：** `BaseAdapter`（经典 ListView 适配器，**非** RecyclerView）

##### 职责
泛型、可复用的 ListView 适配器，渲染 `item_card_row.xml` 列表项。应用中每一个展示列表（告警、设备、历史、健康、规则）都复用同一个适配器，唯一对外接口是 `Row` 数据类。

##### `Row` 数据类

| 字段 | 类型 | 说明 |
|------|------|------|
| `title` | `String` | 主文本，始终可见 |
| `subtitle` | `String` | 次要文本；为 null 或空时隐藏 |
| `meta` | `String` | 第三行文本（底部）；为 null 或空时隐藏 |
| `badge` | `String` | 标签徽章文本（右上角）；为 null 或空时隐藏 |
| `badgeColor` | `@ColorInt int` | 徽章背景色调 |
| `badgeTextColor` | `@ColorInt int` | 徽章内文字颜色 |

##### ViewHolder 模式
```java
private static class ViewHolder {
    final MaterialCardView card;
    final TextView title, subtitle, meta, badge;
}
```
遵循经典 ViewHolder 模式（RecyclerView 之前的做法）。`convertView.setTag(holder)` 缓存 holder，避免每次滚动都调用 `findViewById`。

##### 徽章渲染
`bindBadge()` 构建一个 `GradientDrawable`：
- 填充：`ColorUtils.setAlphaComponent(bgColor, 50)` — 半透明填充。
- 描边：`ColorUtils.setAlphaComponent(bgColor, 130)` — 稍不透明的边框，宽度 = max(1, 1dp)。
- 圆角半径：`14dp × density`。

卡片边框也会着色：`holder.card.setStrokeColor(ColorUtils.setAlphaComponent(row.badgeColor, 110))`。

---

#### `UiFormatters`

**文件：** `ui/common/UiFormatters.java`  
**类型：** `final` 工具类，私有构造器（不可实例化）

所有方法均为 `public static`，除特别说明外线程安全。

| 方法 | 签名 | 行为 |
|------|------|------|
| `formatDateTime` | `(long ms) → String` | 返回本地化日期时间字符串；`ms ≤ 0` 时返回 `"N/A"`。在静态 `DateFormat` 实例上同步。 |
| `formatRelativeTime` | `(long ms) → String` | 委托 `DateUtils.getRelativeTimeSpanString`，分钟粒度，缩写相对格式。`ms ≤ 0` 返回 `"—"`。 |
| `formatSensorSummary` | `(List<SensorData>) → String` | 格式化最多 3 个传感器读数为 `"sensorId value unit  ·  …"`；超出部分追加 `"+N more"`。 |
| `trimNumber` | `(double) → String` | 小数部分 `< 0.0001` 返回整数字符串，否则返回两位小数。 |
| `safe` | `(String) → String` | null 安全 trim；永不返回 `null`。 |
| `upperOrFallback` | `(String, String) → String` | 非空时大写字符串，否则返回 `fallback`。 |

> **重要：** `formatDateTime` 在静态 `DateFormat` 上同步，因为 `DateFormat` 非线程安全。UI 线程调用天然满足此条件，`synchronized` 块作为安全防护。

---

### 3.3 `dashboard` — `DashboardFragment`

**文件：** `ui/dashboard/DashboardFragment.java`  
**布局：** `fragment_dashboard.xml`  
**实现接口：** `TransportManager.MessageListener`、`TransportManager.StatsListener`

#### 职责
应用的主屏幕，承载**可配置、可拖拽排序**的 RecyclerView 卡片列表，以及可选的**单设备全屏视图**。实时接收传输层的更新。

#### 字段说明

| 字段 | 类型 | 用途 |
|------|------|------|
| `binding` | `FragmentDashboardBinding` | 视图绑定 |
| `repository` | `AppRepository` | 数据读取 |
| `transportManager` | `TransportManager` | 实时消息与统计信息订阅 |
| `lastMsgPerSecond` | `int` | 最近一次统计回调中缓存的吞吐量 |
| `cardAdapter` | `DashboardCardAdapter` | RecyclerView 适配器 |
| `cardConfig` | `DashboardCardConfig` | 持久化的卡片顺序/可见性 |
| `touchHelper` | `ItemTouchHelper` | 拖拽排序支持 |

#### 生命周期流程

```
onCreateView()
  ├─ setupRecyclerView()   → DashboardCardConfig.load()，绑定 ItemTouchHelper
  ├─ setupFab()            → 绑定 FAB 到 showAddSensorDialog()
  └─ return binding.root

onStart()
  ├─ transportManager.addMessageListener(this)
  ├─ transportManager.addStatsListener(this)
  └─ refresh()

onStop()
  ├─ transportManager.removeMessageListener(this)
  └─ transportManager.removeStatsListener(this)

onDestroyView()
  └─ binding = null
```

#### 拖拽排序（`ItemTouchHelper.Callback`）
- 仅允许垂直拖拽（`UP | DOWN`），无侧滑删除。
- 位置 0（`HEADER` 卡片）**受保护**：`getMovementFlags()` 对其返回 `0`。
- 拖拽中：item alpha = 0.85，elevation = 16 dp（视觉浮起效果）。
- 拖拽放下时（`clearView`）：`persistOrderFromAdapter()` 将新顺序写入 `DashboardCardConfig`。

#### FAB — 添加自定义传感器卡片
`showAddSensorDialog()` 弹出 `MaterialAlertDialogBuilder`，含两个 `EditText`：
1. **传感器 ID**（自动转大写）：对应协议传感器标识符。
2. **标签**（可选）：展示名称；为空时退回到使用传感器 ID。

确认后：`cardConfig.addCustomSensor(sid, label)` → `cardConfig.save()` → `cardAdapter.setItems(cardConfig.visibleCards())` → `refresh()`。

#### 单设备模式
通过偏好设置键 `"single_device_mode"`（boolean）切换。激活时：
- `rvDashboard` 和 `fabAddCard` 变为 `GONE`。
- `singleDeviceScroll` 变为 `VISIBLE`。
- `refreshSingleDeviceView(device, snapshot)` 填充主图区域、状态徽章、实时仪表盘、温度趋势图，以及动态生成的传感器网格。

传感器网格将读数两两配对成行，每格为包含四个 `TextView` 的 `MaterialCardView`：传感器 ID（label small）、数值（display small，加粗）、单位（body medium）、相对时间（label small）。

#### `refresh()` — 数据快照构建

```
1. 查询最近 24 h 传感器数据（限 50 条）
2. 遍历行：
   a. latestBySensorId.putIfAbsent(sensorId, item)   [最新在前，DESC 顺序]
   b. trendBySensorId：每个 sensorId 最多积累 12 个浮点值
   c. 通过模式匹配识别 TEMP/HUM/PRES/LEVEL 字段
3. 构建 snap.subtitle（格式字符串）、snap.syncStatus、snap.transportStatus
4. 构建 latestReadingsText（项目符号列表）、recentAlertsSummary（最近 3 条）、opsSummary（最近 3 条）
5. cardAdapter.setSnapshot(snap)  → 触发完整重绑定
6. 如果 singleModeEnabled：refreshSingleDeviceView()
```

传感器 ID 模式匹配（`toUpperCase(ROOT)` 后不区分大小写）：
| SensorId 模式 | 赋值目标 |
|--------------|---------|
| 包含 `"TEMP"` 或 `"TMP"` 或等于 `"T1"` | `latestTemp`、`tempTrend` |
| 包含 `"HUM"` 或等于 `"H1"` | `latestHum`、`humTrend` |
| 包含 `"PRES"` 或 `"BAR"` 或等于 `"P1"` | `latestPressure` |
| 包含 `"LEVEL"` 或 `"LVL"` 或等于 `"L1"` | `latestLevel` |

#### TransportManager 监听器回调
两个回调均在传输层线程执行，因此路由到 `getActivity().runOnUiThread(this::refresh)`。

---

### 3.4 `dashboard` — `DashboardCardAdapter`

**文件：** `ui/dashboard/DashboardCardAdapter.java`  
**父类：** `RecyclerView.Adapter<RecyclerView.ViewHolder>`

#### 职责
多视图类型 RecyclerView 适配器，渲染可配置的看板卡片列表。支持 **8 种内置卡片类型** 加**无限 CUSTOM_SENSOR** 卡片。

#### 视图类型常量

| 常量 | 整数值 | 卡片 | 布局资源 |
|------|--------|------|---------|
| `TYPE_HEADER` | 0 | 状态标题 | `item_dashboard_header` |
| `TYPE_KPI_ROW1` | 1 | 总设备数 / 在线率 | `item_dashboard_kpi_row` |
| `TYPE_KPI_ROW2` | 2 | 活跃告警 / 吞吐量 | `item_dashboard_kpi_row` |
| `TYPE_KPI_ROW3` | 3 | 活跃规则 / 累计流量 | `item_dashboard_kpi_row` |
| `TYPE_GAUGES` | 4 | 实时指标 + 进度条 | `item_dashboard_gauges` |
| `TYPE_CHARTS` | 5 | 温度 + 湿度趋势图 | `item_dashboard_charts` |
| `TYPE_ACTIVITY` | 6 | 近期告警 + 操作日志 | `item_dashboard_activity` |
| `TYPE_LATEST_READINGS` | 7 | 原始最新读数文本 | `item_dashboard_readings` |
| `TYPE_CUSTOM_SENSOR` | 8 | 用户自定义传感器卡片 | `item_dashboard_custom_sensor` |

#### `Snapshot` — 实时数据容器
```java
public static class Snapshot {
    int totalDevices, online, alerts, rules;
    long totalMessages;
    int throughput;
    double latestTemp, latestHum, latestPressure, latestLevel;
    int readingCount;
    long latestSampleMs;
    String subtitle, syncStatus, transportStatus;
    String recentAlertsSummary, opsSummary, latestReadingsText;
    boolean singleModeEnabled;
    List<Float> tempTrend, humTrend;
    Map<String, Models.SensorData> latestBySensorId;
    Map<String, List<Float>> trendBySensorId;
}
```
每次 `DashboardFragment.refresh()` 都会完整重建 `Snapshot`，并通过 `setSnapshot(Snapshot)` 传给适配器（内部调用 `notifyDataSetChanged()`）。

#### `Listener` 接口
```java
public interface Listener {
    void onToggleSingleMode();               // HEADER 卡片的切换按钮被点击
    void onItemMoved(int fromPos, int toPos); // 拖拽排序通知
    void onRemoveCustomCard(int adapterPosition, DashboardCardItem item);
}
```

#### ViewHolder 类（内部/静态）

| VH 类 | 绑定字段 |
|-------|---------|
| `HeaderVH` | `tvSubtitle`、`tvSyncStatus`、`tvTransportStatus`、`btnToggle` |
| `KpiRowVH` | `tvLabelLeft`、`tvValueLeft`、`tvLabelRight`、`tvValueRight` |
| `GaugesVH` | `tvLiveMetrics`、`progressWater`、`progressHumidity` |
| `ChartsVH` | `chartTemp`、`chartHumidity`（均为 `MiniTrendChartView`）|
| `ActivityVH` | `tvRecentAlerts`、`tvOpsSummary` |
| `ReadingsVH` | `tvLatestReadings` |
| `CustomSensorVH` | `tvSensorLabel`、`tvSensorId`、`tvSensorValue`、`tvSensorMeta`、`chartSensor` |

`CustomSensorVH` 是**内部类**（非静态），因为其点击监听器需要引用外部适配器的 `items` 列表。其余 VH 均为 `static`。

#### KPI 行绑定逻辑
三行 KPI 共享一个布局，通过视图类型加以区分：
- **Row1：** `totalDevices` + 在线率百分比 `"%.1f%%"`
- **Row2：** `alerts` + 吞吐量 `"%d msg/s"`
- **Row3：** `rules` + 累计消息数 `totalMessages`

#### 自定义传感器卡片 — 数据查找
传感器 ID 不区分大小写：
```java
data = s.latestBySensorId.get(item.sensorId.toUpperCase()); // 优先尝试大写
if (data == null) data = s.latestBySensorId.get(item.sensorId); // 回退到原始大小写
```

---

### 3.5 `dashboard` — `DashboardCardConfig`

**文件：** `ui/dashboard/DashboardCardConfig.java`  
**类型：** `final` 类，私有构造器

#### 职责
看板卡片列表的持久化层。将 `DashboardCardItem` 列表序列化/反序列化到 `SharedPreferences`，以 JSON 数组形式存储于键 `"cards_json_v2"` 下。

#### SharedPreferences 详情
- 文件名：`"dashboard_card_prefs"`（`Context.MODE_PRIVATE`）
- 键：`"cards_json_v2"`

#### JSON 模式（每张卡片对象）
```json
{
  "type":     "KPI_ROW1",
  "visible":  true,
  "order":    1,
  "sensorId": "",
  "label":    ""
}
```
- `type` 是枚举名（如 `"CUSTOM_SENSOR"`）。
- 未知的 `type` 值会被**静默跳过**（防止降级后崩溃）。
- 缺失字段使用安全默认值（`visible=true`、`order=arrayIndex`、`sensorId=""`、`label=sensorId`）。

#### `defaultCards()` — 工厂方法
返回 8 张预设顺序的内置卡片（HEADER 到 LATEST_READINGS），顺序索引为 0–7。

#### 公共 API

| 方法 | 描述 |
|------|------|
| `load(Context)` | 静态工厂；从偏好设置加载，不存在或损坏则返回默认配置 |
| `save(Context)` | 保存当前 `cards` 列表；写入前重新索引 `order` 字段 |
| `visibleCards()` | 返回按 `order` 排序的可见卡片列表 |
| `isVisible(Type)` | 查询特定卡片类型的可见性 |
| `setVisible(Type, boolean)` | 修改可见性但不保存（调用方需手动调用 `save`）|
| `moveCard(int, int)` | 在列表内移动卡片；拖拽排序后使用 |
| `addCustomSensor(String, String)` | 在末尾追加一张新的 `CUSTOM_SENSOR` 卡片 |
| `removeCard(DashboardCardItem)` | 按引用删除卡片 |

#### `ensureHeader()` — 不变量维护
`load()` 解析 JSON 后始终调用 `ensureHeader(list)`，确保 `HEADER` 卡片存在且位于索引 0，即使用户手动编辑了 SharedPreferences 导致数据格式异常。

---

### 3.6 `dashboard` — `DashboardCardItem`

**文件：** `ui/dashboard/DashboardCardItem.java`

#### 字段说明

| 字段 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `type` | `Type`（枚举）| — | 卡片类别 |
| `visible` | `boolean` | `true` | 是否包含在 `visibleCards()` 中 |
| `order` | `int` | — | 排序优先级 |
| `sensorId` | `String` | `""` | 传感器标识符；仅 `CUSTOM_SENSOR` 有意义 |
| `label` | `String` | `""` | 显示名称；仅 `CUSTOM_SENSOR` 有意义 |

#### `Type` 枚举

```
HEADER          固定标题行 — 始终位于第 0 位，不可拖拽
KPI_ROW1        总设备数 + 在线率
KPI_ROW2        活跃告警 + 吞吐量
KPI_ROW3        活跃规则 + 累计流量
GAUGES          实时传感器指标 + 进度条
CHARTS          温度 + 湿度趋势图
ACTIVITY        近期告警 + 操作日志信息流
LATEST_READINGS 最新原始传感器读数文本块
CUSTOM_SENSOR   用户自定义传感器（特定 sensorId）
```

#### `isDraggable()`
仅对 `HEADER` 返回 `false`；其余所有类型（包括 `CUSTOM_SENSOR`）均可重新排序。

#### `customSensor()` 工厂方法
```java
DashboardCardItem.customSensor("CO2", "二氧化碳", nextOrder);
```
`visible` 默认为 `true`；`label` 为空时退回到使用 `sensorId`。

---

### 3.7 `devices` — `DevicesFragment`

**文件：** `ui/devices/DevicesFragment.java`  
**布局：** `fragment_devices.xml`

#### 职责
展示所有已知物联网设备，提供持续**实时搜索/筛选**栏、在线/离线计数，以及点击弹出的**设备详情对话框**。

#### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `binding` | `FragmentDevicesBinding` | 视图绑定 |
| `repository` | `AppRepository` | 数据来源 |
| `allDevices` | `List<Models.Device>` | 从 DB 加载的完整列表 |
| `visibleDevices` | `List<Models.Device>` | 筛选后的子集（用于点击事件下标映射）|
| `adapter` | `CardRowAdapter` | ListView 适配器 |

#### 搜索逻辑
`filter()` 对 `device.aid`（转字符串）和 `device.name` 均进行不区分大小写的子串匹配。空查询显示全部设备。通过 `TextWatcher.onTextChanged` 在每次文本变化时触发过滤。

#### 在线判断启发式规则
```java
boolean online = "online".equalsIgnoreCase(d.status) ||
                 System.currentTimeMillis() - d.lastSeenMs < 5 * 60_000L;
```
设备的 status 字段为 `"online"` **或** 最近 5 分钟（300 000 ms）内有活跃记录，则视为在线。这种双重检查可容忍因设备重连未发送显式状态更新导致的过期状态字符串。

#### 设备详情对话框
`showDeviceDetails(Device)` 获取该设备的最新传感器读数，并在格式化的 `AlertDialog` 中展示：
- AID、名称、类型、状态、传输类型
- GPS 坐标（lat/lng）
- 最后活跃时间（绝对时间 + 相对时间）
- 传感器读数列表（项目符号格式：`• sensorId = value unit · datetime`）

#### `toRows()` — 徽章颜色
```java
online ? R.color.gsyn_online : R.color.gsyn_warning
```
离线设备徽章为警告橙色；在线设备为绿色。

---

### 3.8 `mirror` — `HealthMirrorFragment`

**文件：** `ui/mirror/HealthMirrorFragment.java`  
**布局：** `fragment_secondary_panel.xml`（所有 Mirror Fragment 共享）

#### 职责
传输层和本地数据库的系统健康看板，展示：
- **摘要：** 设备数量、每秒消息数、总消息数。
- **详情：** UDP 状态、MQTT 连接状态、数据库大小（KB）。
- **列表：** 每个设备作为 `CardRowAdapter.Row`，带在线/离线徽章。
- **操作按钮：** "清理旧数据" — 调用 `repository.pruneOldData(7)`（删除 7 天前的记录）。

#### 共享布局 — `fragment_secondary_panel.xml`
四个 Mirror Fragment 均使用此布局，视图 ID：
- `R.id.tvSectionLabel` — 分区标题
- `R.id.tvSummary` — 主要统计行
- `R.id.tvDetail` — 次要详情文本
- `R.id.tvEmpty` — 列表为空时显示
- `R.id.list` — `ListView`
- `R.id.btnAction` — `MaterialButton`（各 Fragment 自行设置文本和点击监听）

---

### 3.9 `mirror` — `HistoryMirrorFragment`

**文件：** `ui/mirror/HistoryMirrorFragment.java`  
**布局：** `fragment_secondary_panel.xml`

#### 职责
展示最近 24 小时的传感器数据读数（最多 500 条），并提供 **CSV 导出**操作。

#### 数据查询
```java
long now = System.currentTimeMillis();
List<Models.SensorData> rows =
    repository.querySensorData(now - 24L * 3600L * 1000L, now, 500);
```

#### 卡片行映射
| `CardRowAdapter.Row` 字段 | 值 |
|--------------------------|-----|
| `title` | `"sensorId · value unit"` |
| `subtitle` | 设备 AID 行 |
| `meta` | 格式化绝对日期时间 |
| `badge` | `sensorId.toUpperCase()` 或 `"DATA"` |
| `badgeColor` | `R.color.gsyn_info` |

#### CSV 导出
`exportCsv()` 完全委托给 `repository.exportHistoryCsv()`，返回一个 `File` 对象。通过长 `Toast` 显示成功/失败信息。

---

### 3.10 `mirror` — `MapMirrorFragment`

**文件：** `ui/mirror/MapMirrorFragment.java`  
**布局：** `fragment_map_mirror.xml`  
**实现接口：** `OnMapReadyCallback`

#### 职责
交互式 Google Maps 视图，将所有带有效 GPS 坐标的设备显示为彩色标记。支持**地图类型切换**（普通/卫星/混合）和 **10 秒自动刷新**。

#### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `repository` | `AppRepository` | 设备数据来源 |
| `googleMap` | `GoogleMap` | `onMapReady()` 之前为 null |
| `tvMapSummary` | `TextView` | 地图上方摘要行 |
| `currentMarkers` | `List<Marker>` | 当前显示的标记 |
| `autoRefreshHandler` | `Handler`（主线程 Looper）| 驱动 10 s 轮询 |
| `autoRefreshRunnable` | `Runnable` | `loadMarkers()` 后重新延迟投递自身 |

#### 地图初始化
`SupportMapFragment` 以固定 Tag `"MAP_FRAG"` 作为**子 Fragment** 管理：
```java
SupportMapFragment mapFrag =
    (SupportMapFragment) getChildFragmentManager().findFragmentByTag("MAP_FRAG");
if (mapFrag == null) { /* 新建 */ }
mapFrag.getMapAsync(this);
```
使用 `commitNow()` 确保地图 Fragment 在调用 `getMapAsync()` 前同步附加。

#### 深色地图样式
当 `AppThemeConfig.BgPreset.isLight == false` 时，Fragment 加载 `R.raw.map_style_dark`（自定义 Google Maps JSON 样式），以匹配应用的深色背景。

#### 默认相机位置
```java
googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(35.0, 105.0), 4f));
```
以中国为中心，缩放级别 4 — 即使没有设备数据也可见。

#### 标记逻辑
- `|lat| < 1e-7 AND |lng| < 1e-7` 的设备被视为**无 GPS** 数据，不在地图上绘制。
- 在线设备：`HUE_GREEN` 标记；离线：`HUE_ORANGE`。
- 恰好 1 个映射设备：以其为中心缩放至 14 级。
- 超过 1 个：以 120 dp 内边距自适应视野边界。

#### 自动刷新
```
onStart()      → autoRefreshHandler.post(autoRefreshRunnable)
onStop()       → autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
onDestroyView() → removeCallbacks（防止 Runnable 泄漏）
```
`autoRefreshRunnable` 调用 `loadMarkers()` 后以 10 000 ms 延迟重新投递自身。

---

### 3.11 `mirror` — `RulesMirrorFragment`

**文件：** `ui/mirror/RulesMirrorFragment.java`  
**布局：** `fragment_secondary_panel.xml`

#### 职责
自动化规则的完整增删改查界面，同时展示操作日志。支持：
- **查看**所有规则 + 最近 30 条操作日志。
- **切换**规则启用/禁用状态（点击列表行）。
- **删除**规则（长按列表行）。
- **创建**新阈值规则（对话框）。

#### 字段
- `cachedRules`（`List<Models.Rule>`）— 用于将列表位置映射到规则对象的快照。

#### 规则 → `CardRowAdapter.Row` 映射
| 字段 | 值 |
|------|-----|
| `title` | `rule.name` |
| `subtitle` | `"sensorIdFilter operator threshold → actionType"` |
| `meta` | `"AID ALL/deviceAidFilter · cooldown Xs"` |
| `badge` | `"ENABLED"` 或 `"DISABLED"` |
| `badgeColor` | `gsyn_online`（绿色）或 `gsyn_warning`（橙色）|

操作日志条目追加在规则**之后**，徽章为 `"LOG"`，颜色为 `gsyn_info`。

#### 单击 / 长按处理
仅前 `cachedRules.size()` 行为规则行，其余为日志行。两个监听器均防止下标越界。

#### 创建规则对话框
含两个 `EditText` 的最简对话框：
- 传感器 ID（默认：`"TEMP"`）
- 阈值（默认：`"50"`）

创建的规则固定使用运算符 `">"` 和动作 `"create_alert"`。每次成功创建后写入操作日志 `"CREATE_RULE"`。

---

### 3.12 `send` — `SendFragment`

**文件：** `ui/send/SendFragment.java`  
**布局：** `fragment_send.xml`  
**依赖：** `PacketBuilder`、`ProtocolConstants`、`TransportManager`

#### 职责
**协议测试台**和手动报文发送工具，无需硬件即可构建并注入任意协议帧，供开发者 / QA 工程师使用。以三个选项卡组织。

#### `MultiSensorRow` 内部类
保存多传感器行中动态创建视图的引用：
```java
final View root;
final Spinner spSid, spUnit, spState;
final TextInputEditText etVal;
```

#### 选项卡结构

| 选项卡索引 | 标签 | 可见性 ID | 内容 |
|----------|------|-----------|------|
| 0 | 控制 | `binding.tabControl` | 7 个控制报文按钮（PING、PONG、ID_REQUEST、ID_ASSIGN、TIME_REQUEST、HS_ACK、HS_NACK、SECURE_DICT_READY）|
| 1 | 数据 | `binding.tabData` | 单传感器表单 + 多传感器动态行 |
| 2 | 原始 | `binding.tabRaw` | 自由格式十六进制输入 |

#### 路由参数
每个报文的路由：
- **AID**（`binding.etAid`）：目标设备地址 ID
- **TID**（`binding.etTid`）：传输会话 ID
- **SEQ**（`binding.etSeq`）：序列号
- **IP**（`binding.etIp`）：目标主机
- **Port**（`binding.etPort`）：UDP 端口（默认 9876）

`updateRouteSummary()` 在任意变化时构建当前路由的展示字符串。

#### 单传感器数据选项卡
- 传感器 ID Spinner（`ProtocolConstants.OS_SENSOR_IDS`）
- 单位 Spinner（`ProtocolConstants.OS_UNITS`）— 通过 `ProtocolConstants.defaultUnitFor(sid)` 根据传感器 ID 自动选择
- 状态 Spinner（`ProtocolConstants.OS_STATES`）
- 数值 `EditText`
- 预览标签，展示编码后的报文体结构

#### 多传感器选项卡
`addMultiSensorRow()` 动态填充一个水平 `LinearLayout`，包含：
- SID Spinner（weight=1）、Unit Spinner（weight=1）、State Spinner（weight=0.6）
- 数值 `TextInputEditText`（weight=1）
- 移除按钮（`"✕"`）

最大行数：无限制（受硬件约束）。

#### 原始十六进制选项卡
接受十六进制字符串，通过 `PacketBuilder.buildRawHex(hex)` 构建字节，然后发送。

#### `sendAndLog(String label, byte[] frame)`
核心发送方法：
1. 从表单字段读取 AID、host、port。
2. 调用 `transport().sendCommand(frame, aid, host, port)`。
3. 向 `repository.logOperation("SEND_CMD", ...)` 记录操作。
4. 追加到内存中的 `logs` 列表（上限 20 条）。
5. 更新 `tvLastResult` 和 `tvLog`。
6. 显示成功/失败的 `Toast`。

#### 命令参考表
`setupCmdRef()` 将所有协议命令码（0x01–0x25）的静态文本块（含十六进制字节和描述）设置到 `tvCmdRef`。内容直接用 Java 硬编码，而非字符串资源，以便与发送代码集中在同一处维护。

---

### 3.13 `settings` — `SettingsFragment`

**文件：** `ui/settings/SettingsFragment.java`  
**布局：** `fragment_settings.xml`

#### 职责
应用配置界面，涵盖：
1. **语言**（英文 / 中文 / 跟随系统）
2. **强调色**主题选择
3. **背景**预设选择
4. **看板卡片可见性**开关
5. **单设备模式**开关
6. **传输设置**：UDP 主机/端口/开关，MQTT 代理/端口/主题/开关
7. **瓦片 URL**（地图瓦片来源）
8. **传输状态展示**（运行时只读摘要）

#### 语言选择器
`loadLanguagePref()` 从 `LocaleHelper.current()` 读取当前语言，勾选 `binding.rgLanguage` 中对应的 `RadioButton`。切换时调用 `LocaleHelper.applyAndSave(lang)` — AppCompat 自动处理 Activity 重建。

语言常量：`LocaleHelper.LANG_EN = "en"`、`LANG_ZH = "zh"`、`LANG_SYSTEM = ""`。

#### 强调色 Chip 组 — `buildAccentChips()`
为每个 `AppThemeConfig.ThemePreset` 枚举值动态生成一个 `Chip`，每个 Chip：
- 通过 `View.generateViewId()` 获取唯一 ID。
- 以 `preset.color()` 着色的 40×40 椭圆 `GradientDrawable` 作为 Chip 图标。
- 使用 `setOnClickListener`（而非 `OnCheckedChangeListener`）避免重复触发事件。
- 当前激活预设的 Chip 预先勾选。

#### 背景 Chip 组 — `buildBgChips()`
与强调色 Chip 模式相同，但遍历 `AppThemeConfig.BgPreset`，并额外为背景色圆点添加 2dp 灰色描边（确保在浅色背景上可见）。

#### 看板卡片可见性
`loadCardConfig()` 读取 `DashboardCardConfig` 并设置 7 个拨动开关的初始状态。单设备模式开关具有**即时**监听器（无需按保存即生效）。其他卡片可见性开关仅在按"保存"时生效。

#### 保存流程 — `savePrefs()`
```
1. saveCardConfig()          → DashboardCardConfig.save()
2. SharedPreferences.edit()  → 应用传输及其他偏好设置
3. TransportManager:         启动/停止 UDP，连接/断开 MQTT
4. refreshTransportStatus()  → 更新状态 TextView
5. requireActivity().recreate() → 应用主题变更
```
每次保存后强制调用 `recreate()`，确保强调色或背景变更立即生效，无需手动重启应用。

#### `refreshTransportStatus()`
从 repository 和 TransportManager 获取实时数据，填充 `tvTransportStatus`、`tvDeviceCount`、`tvAlertCount`、`tvRuleCount`、`tvDbSize` 和 `tvRuntimeHint`。

#### `formatBytes(long)`
私有辅助方法：将字节数格式化为 `"X B"`、`"X.X KB"` 或 `"X.XX MB"`。

---

## 4. 自定义视图层

### 4.1 `widget` — `MiniTrendChartView`

**文件：** `ui/widget/MiniTrendChartView.java`  
**父类：** `android.view.View`

#### 职责
基于 Canvas 绘制的轻量级自包含迷你图。使用场景：
- `DashboardCardAdapter.ChartsVH`（温度 + 湿度趋势）
- `DashboardCardAdapter.CustomSensorVH`（单传感器趋势）
- `DashboardFragment.refreshSingleDeviceView()`（单设备模式温度图）

#### Paint 对象

| Paint | 样式 | 颜色 | 用途 |
|-------|------|------|------|
| `linePaint` | STROKE，3dp 圆角端头/连接 | `#5AC8FA`（默认）| 折线路径 |
| `fillPaint` | FILL | `rgba(90,200,250,0.16)` | 折线下方填充区域 |
| `gridPaint` | STROKE，1dp | `rgba(255,255,255,0.2)` | 4 条水平网格线 |
| `pointPaint` | FILL | 同 linePaint | 数据点圆形 |
| `textPaint` | TEXT，12sp | `rgba(255,255,255,0.86)` | 标题文本 |

#### `onDraw` 内的布局区域
```
left  = 16dp
right = 宽度 - 16dp
top   = 32dp   （标题下方）
bottom = 高度 - 16dp
titleBaseline = 18dp
```

#### `onDraw` 算法
1. 在 `(left, titleBaseline)` 处绘制标题文字。
2. 在 `top` 和 `bottom` 之间均匀绘制 4 条水平网格线。
3. 若 `points` 为空，提前返回。
4. 计算数据的 `min` 和 `max`；若 `|max - min| < 0.0001`，扩展 ±1 避免除以零。
5. 遍历数据点，计算 `x` 和 `y`：
   - `x = left + (right - left) * i / (n - 1)`
   - `y = bottom - normalized * (bottom - top)`，其中 `normalized = (value - min) / (max - min)`
6. 同步构建 `Path line` 和 `Path fill`。
7. 先绘制填充区域，再绘制折线，然后在每个数据点绘制半径 2.5dp 的圆形。
8. 在最后一个点（最新值）绘制更大的半径 4dp 圆形，以突出强调。

#### 公共 API

| 方法 | 描述 |
|------|------|
| `setSeries(List<Float>)` | 替换数据并调用 `invalidate()` |
| `setChartColor(int color)` | 设置折线、填充（alpha 40）和数据点颜色；调用 `invalidate()` |
| `setTitle(String)` | 设置标题文本和内容描述；调用 `invalidate()` |

#### XML 使用示例
```xml
<com.opensynaptic.gsynjava.ui.widget.MiniTrendChartView
    android:id="@+id/chartTemp"
    android:layout_width="match_parent"
    android:layout_height="80dp" />
```

---

## 5. 导航架构

### 整体结构
```
MainActivity
├── BottomNavigationView（主导航）
│   ├── Dashboard     → DashboardFragment
│   ├── Devices       → DevicesFragment
│   ├── Alerts        → AlertsFragment
│   ├── Send          → SendFragment
│   └── Settings      → SettingsFragment
│
└── NavigationView / DrawerLayout（次级导航）
    ├── （镜像上方底部导航项）
    ├── Map           → MapMirrorFragment
    ├── History       → HistoryMirrorFragment
    ├── Rules         → RulesMirrorFragment
    └── Health        → HealthMirrorFragment

SecondaryActivity（独立入口）
├── MODE_MAP     → MapMirrorFragment
├── MODE_HISTORY → HistoryMirrorFragment
├── MODE_RULES   → RulesMirrorFragment
└── MODE_HEALTH  → HistoryMirrorFragment（默认）
```

### Fragment 事务策略
所有事务均使用 `replace()` 提交到单一容器（`R.id.fragment_container`），**不维护返回栈**——按返回键将退出 Activity。这对物联网看板来说是刻意的设计，因为此类应用通常采用单向导航模式。

### 状态保持
- `savedInstanceState == null` 守卫确保 Fragment 只在首次启动时提交，而非在旋转/恢复时重复提交。
- Fragment 级状态（如 `AlertsFragment` 的滚动位置）不显式保存；`onStart()` 的 `load()` 调用始终从数据库刷新数据。

---

## 6. 主题与样式体系

应用采用**基于覆盖层（Overlay）的动态主题**方案：

1. **强调色** — `AppThemeConfig.ThemePreset` 枚举（如 Teal、Purple、Orange 等）。每个预设映射到一个样式覆盖层资源。在 `super.onCreate()` **之前**通过 `getTheme().applyStyle(overlayRes, true)` 运行时应用。

2. **背景预设** — `AppThemeConfig.BgPreset` 枚举（如 Dark、Light、Amoled 等）。同样在 `super.onCreate()` 之前通过覆盖层应用。`setContentView()` 后调用 `applyBgToWindow(window, context)` 同步状态栏/导航栏颜色。Fragment 中调用 `AppThemeConfig.applyBgToRoot(view, context)` 设置根视图背景色。

3. **地图深色样式** — `MapMirrorFragment` 检查 `BgPreset.isLight`，条件性加载 `R.raw.map_style_dark`。

4. **语言** — `LocaleHelper` 持久化用户语言偏好，并通过 `AppCompatDelegate.setApplicationLocales()` 应用。

---

## 7. UI 层数据流

```
TransportManager（UDP/MQTT 接收线程）
        │
        │ onMessage(DeviceMessage)     （通过 MessageListener）
        │ onStats(TransportStats)      （通过 StatsListener）
        ▼
DashboardFragment.refresh()           （通过 runOnUiThread 切换到 UI 线程）
        │
        ├─ repository.querySensorData(...)   ─── SQLite（内存/文件）
        ├─ repository.getTotalDeviceCount()
        ├─ repository.getOnlineDeviceCount()
        ├─ repository.getUnacknowledgedAlertCount()
        ├─ repository.getAlerts(null, 3)
        ├─ repository.getOperationLogs(3)
        │
        └─ DashboardCardAdapter.setSnapshot(snap)
                │
                └─ notifyDataSetChanged() → onBindViewHolder() 每张卡片
                        │
                        └─ MiniTrendChartView.setSeries(List<Float>) → invalidate()
```

其他 Fragment（`AlertsFragment`、`DevicesFragment` 等）仅在 `onStart()`（以及用户下拉刷新）时轮询 repository，**不订阅**传输回调——其数据源是数据库快照。

---

## 8. 命名规范与编码风格

| 元素 | 规范 | 示例 |
|------|------|------|
| Activity 字段 | camelCase，视图绑定用 `binding` | `binding`、`drawerToggle` |
| Fragment 字段 | camelCase，`binding` 在 `onDestroyView` 中置空 | `binding`、`repository`、`adapter` |
| XML 视图 ID | `tvXxx`（TextView）、`etXxx`（EditText）、`btnXxx`（Button）、`rvXxx`（RecyclerView）、`ivXxx`（ImageView）、`switchXxx`（Switch）| `tvSummary`、`etAid`、`btnSave` |
| 常量 | `SCREAMING_SNAKE_CASE` | `EXTRA_MODE`、`MODE_MAP` |
| 颜色常量 | `gsyn_` 前缀 | `gsyn_danger`、`gsyn_online` |
| SharedPrefs 键 | 小写 snake_case | `"udp_host"`、`"single_device_mode"` |
| 持久化资源键 | `KEY_` 前缀 | `KEY_CARDS` |

---

## 9. 扩展 Dashboard 卡片

### 添加新的内置卡片类型
1. 在 `DashboardCardItem.Type` 中添加枚举值。
2. 在 `DashboardCardAdapter` 中添加 `TYPE_XXX` 整型常量。
3. 新建 `layout/item_dashboard_xxx.xml` 布局。
4. 在 `DashboardCardAdapter` 中添加新的 `XxxVH` ViewHolder 类。
5. 在 `getItemViewType()`、`onCreateViewHolder()`、`onBindViewHolder()` 中连接处理逻辑。
6. 在 `DashboardCardConfig.defaultCards()` 中添加 `DashboardCardItem(Type.XXX, n)` 条目。
7. 在 `DashboardCardAdapter.Snapshot` 中暴露所需新数据字段，并在 `DashboardFragment.refresh()` 中填充。

### 以编程方式预置自定义传感器
无需修改代码——用户可通过 `DashboardFragment` 中的 FAB 对话框自行添加。若要以编程方式预置：
```java
DashboardCardConfig cfg = DashboardCardConfig.load(context);
cfg.addCustomSensor("CO2", "二氧化碳");
cfg.save(context);
```

### 添加新的 Mirror Fragment
1. 创建 `XxxMirrorFragment extends Fragment`，填充 `fragment_secondary_panel.xml`。
2. 在 `SecondaryActivity` 中添加 `MODE_XXX` 常量，并在 `fragmentForMode()` / `subtitleFor()` 中添加对应 case。
3. 在 `drawer_nav.xml` 中添加抽屉菜单项。
4. 在 `MainActivity.onNavigationItemSelected()` 中处理该菜单项 ID。

---

## 10. 常见坑与 FAQ

### Q：Fragment 旋转后抛出 NPE。
**A：** 在任何访问 `binding` 之前进行 null 检查（尤其是在异步回调中）。使用以下模式：
```java
if (binding == null) return;
```
视图绑定在 `onDestroyView()` 中被置空；任何在此之后触发的延迟回调都会遇到空 binding。

### Q：Dashboard 在新数据到来时不更新。
**A：** `DashboardFragment` 仅在 `onStart()` 到 `onStop()` 之间订阅 `TransportManager` 监听器。在此窗口之外（例如正在显示 Mirror Fragment 时），不会自动刷新。其他 Fragment 需要手动下拉刷新或离开后重新进入。

### Q：主题变更没有立即生效。
**A：** 覆盖层系统需要重建 Activity。`SettingsFragment.savePrefs()` 会调用 `requireActivity().recreate()`。切勿在生命周期的其他节点应用覆盖层——主题必须在 `super.onCreate()` 之前完全设置好。

### Q：设备明明在发送数据，但显示为离线。
**A：** `DevicesFragment.toRows()` 和 `HealthMirrorFragment.load()` 中的在线判断同时检查 `device.status`（字符串）和 `lastSeenMs` 新鲜度（< 5 分钟）。如果设备状态写入有延迟，5 分钟窗口会作为活跃度的兜底。

### Q：地图没有显示标记，但设备有 GPS。
**A：** `MapMirrorFragment.loadMarkers()` 跳过 `|lat| < 1e-7` 且 `|lng| < 1e-7` 的设备。坐标为 `(0, 0)`（默认/未初始化）的设备被视为没有 GPS 数据。

### Q：如何添加新语言？
**A：** 为语言代码新建 `res/values-xx/strings.xml`，在 `fragment_settings.xml` 中添加 `RadioButton`，在 `LocaleHelper` 中添加常量，并在 `SettingsFragment.loadLanguagePref()` 中处理新常量。

### Q：`MiniTrendChartView` 滚动时闪烁。
**A：** 该视图绘制开销中等。可考虑在数据稳定后调用 `setLayerType(LAYER_TYPE_HARDWARE, null)` 启用硬件加速层，或通过仅在 ≥ 2 个数据点时显示图表来减少可见图表数量（单设备模式的温度趋势图已采用此策略）。

### Q：想在 Dashboard 上同时显示超过 8 张卡片怎么办？
**A：** `DashboardCardConfig` 支持无限 `CUSTOM_SENSOR` 卡片，每种内置 `Type` 每种只能出现一次。若需要同一类型的多个实例，最简单的方式是添加新的 `Type` 枚举值，参见"扩展 Dashboard 卡片"部分。

### Q：如何重置 Dashboard 到出厂状态？
**A：** 清除 SharedPreferences 文件 `"dashboard_card_prefs"` 中的键 `"cards_json_v2"`，或调用：
```java
context.getSharedPreferences("dashboard_card_prefs", Context.MODE_PRIVATE)
    .edit().remove("cards_json_v2").apply();
```
下次 `DashboardCardConfig.load()` 时会自动回退到 `defaultCards()`。

