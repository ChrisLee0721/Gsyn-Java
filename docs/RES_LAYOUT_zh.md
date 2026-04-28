# Gsyn-Java `res/layout` 目录开发者文档

> 路径：`app/src/main/res/layout/`  
> 本目录包含应用的所有 Activity 布局、Fragment 布局及 RecyclerView item 布局，共 18 个 XML 文件。  
> 所有布局均使用 Material3 组件，主题颜色通过 `?attr/colorXxx` 动态引用以支持多主题切换。

---

## 目录

1. [文件清单总览](#1-文件清单总览)
2. [Activity 布局](#2-activity-布局)
3. [Fragment 布局](#3-fragment-布局)
4. [RecyclerView Item 布局](#4-recyclerview-item-布局)
5. [抽屉头部布局](#5-抽屉头部布局)
6. [视图 ID 速查表](#6-视图-id-速查表)
7. [布局设计规范](#7-布局设计规范)

---

## 1. 文件清单总览

| 文件名 | 类型 | 对应 Java 类 | 说明 |
|--------|------|------------|------|
| `activity_main.xml` | Activity | `MainActivity` | 主壳：DrawerLayout + Toolbar + FragmentContainer + BottomNav |
| `activity_secondary.xml` | Activity | `SecondaryActivity` | 二级壳：单 FragmentContainer，用于嵌入扩展 Fragment |
| `fragment_dashboard.xml` | Fragment | `DashboardFragment` | 仪表盘：RecyclerView（多设备模式）+ 单设备详情 ScrollView（覆盖层） |
| `fragment_devices.xml` | Fragment | `DevicesFragment` | 设备列表：RecyclerView + 空状态文本 |
| `fragment_alerts.xml` | Fragment | `AlertsFragment` | 告警列表：RecyclerView + 未读计数 + 全部确认按钮 |
| `fragment_send.xml` | Fragment | `SendFragment` | 发包工具：协议字段表单 + 发送按钮 |
| `fragment_settings.xml` | Fragment | `SettingsFragment` | 设置面板：主题/语言/网络配置等 |
| `fragment_map_mirror.xml` | Fragment | `MapMirrorFragment` | 地图视图：WebView 嵌入 Google Maps JS |
| `fragment_secondary_panel.xml` | Fragment | `SecondaryPanelFragment` | 二级面板通用布局 |
| `item_card_row.xml` | RecyclerView item | `DashboardAdapter` | 仪表盘卡片行：设备名 + 传感器值摘要 |
| `item_dashboard_activity.xml` | RecyclerView item | `ActivityAdapter` | 活动/操作日志行 |
| `item_dashboard_charts.xml` | RecyclerView item | `ChartsAdapter` | 图表卡片 |
| `item_dashboard_custom_sensor.xml` | RecyclerView item | 自定义传感器卡片 | 自定义传感器展示卡片 |
| `item_dashboard_gauges.xml` | RecyclerView item | `GaugesAdapter` | 仪表/进度条卡片 |
| `item_dashboard_header.xml` | RecyclerView item | `DashboardHeaderAdapter` | 仪表盘顶部 KPI 汇总头部 |
| `item_dashboard_kpi_row.xml` | RecyclerView item | `KpiRowAdapter` | KPI 数字行（设备数、在线数、告警数） |
| `item_dashboard_readings.xml` | RecyclerView item | `ReadingsAdapter` | 传感器读数卡片 |
| `nav_drawer_header.xml` | 导航抽屉头部 | `MainActivity` | 抽屉顶部 Header（应用名 + 版本） |

---

## 2. Activity 布局

### 2.1 `activity_main.xml` — 主 Activity 布局

**根容器**：`DrawerLayout`（支持左滑打开导航抽屉）

**结构树**：

```
DrawerLayout (id: drawer_layout)
│
├── LinearLayout (主内容区, orientation=vertical, padding=12dp)
│   ├── FrameLayout (background=@drawable/bg_shell_panel)
│   │   └── MaterialToolbar (id: toolbar)
│   │           title="Dashboard"  subtitle="Gsyn-Java"
│   │           paddingStart/End=8dp
│   │
│   ├── FrameLayout (id: fragment_container)
│   │       layout_weight=1  clipToPadding=false
│   │       paddingTop/Bottom=8dp
│   │       ← Fragment 在此区域替换 →
│   │
│   └── FrameLayout (background=@drawable/bg_shell_panel)
│           └── BottomNavigationView (id: bottom_nav)
│                   menu=@menu/bottom_nav
│                   labelVisibilityMode=labeled
│
└── NavigationView (id: nav_view)
        layout_gravity=start  width=280dp
        background=?colorBackground
        header=@layout/nav_drawer_header
        menu=@menu/drawer_nav
```

**关键设计**：
- Toolbar 和 BottomNav 均用 `bg_shell_panel` 包裹，与 Fragment 区域在视觉上分层
- `fragment_container` 使用 `layout_weight=1` 占满剩余高度
- `android:padding="12dp"` 给整个主内容区增加边距，使内容避开屏幕边缘

### 2.2 `activity_secondary.xml` — 二级 Activity 布局

**根容器**：单一 `FrameLayout`，id `fragment_container`，用于承载 `SecondaryActivity` 中动态加载的 Fragment。

---

## 3. Fragment 布局

### 3.1 `fragment_dashboard.xml` — 仪表盘

**根容器**：`CoordinatorLayout`

**两种显示模式**（通过 `visibility` 切换 Java 代码控制）：

#### 多设备模式（默认）

```
RecyclerView (id: rvDashboard)
    paddingBottom=80dp (避免被 BottomNav 遮挡)
    ← DashboardAdapter 填充仪表盘卡片 →

FloatingActionButton (id: fabAddCard)
    gravity=bottom|end, margin=16dp
    icon=ic_input_add  → 添加自定义传感器卡片
```

#### 单设备详情模式（覆盖层，visibility=gone 初始）

```
ScrollView (id: singleDeviceScroll)
└── LinearLayout
    ├── LinearLayout (id: sdHeroBanner) [colorPrimaryContainer 背景]
    │   ├── TextView "SINGLE DEVICE MODE"
    │   ├── TextView (id: sdDeviceName)   设备名
    │   ├── LinearLayout [状态行]
    │   │   ├── TextView (id: sdStatusBadge)  @drawable/bg_status_pill
    │   │   ├── TextView (id: sdAid)      AID 显示
    │   │   ├── Spacer
    │   │   └── TextView (id: sdTransport) UDP/MQTT
    │   └── TextView (id: sdLastSeen)     最后在线时间
    │
    ├── Section Header "LATEST READINGS"
    ├── LinearLayout (id: sdSensorGrid)  ← 动态注入传感器行 →
    ├── TextView (id: sdNoSensors)        空状态提示
    │
    ├── MaterialCardView [进度条区]
    │   ├── Water Level: sdLevelValue + sdProgressWater (LinearProgressIndicator)
    │   └── Humidity:    sdHumValue  + sdProgressHumidity
    │
    ├── MaterialCardView [温度折线图]
    │   └── MiniTrendChartView (id: sdChartTemp, height=120dp)
    │
    ├── LinearLayout [Quick Stats 3列]
    │   ├── Card: sdAlerts     活跃告警数
    │   ├── Card: sdRules      规则数
    │   └── Card: sdThroughput 吞吐量
    │
    └── MaterialButton (id: btnExitSingleMode) "退出单设备模式"
```

### 3.2 `fragment_devices.xml` — 设备列表

```
LinearLayout (vertical)
├── TextView  页面标题 "Devices"
├── LinearLayout [在线统计行]
│   ├── TextView (id: tvOnlineCount)   "在线: x"
│   └── TextView (id: tvTotalCount)    "总计: x"
├── RecyclerView (id: rvDevices)       设备列表
└── TextView (id: tvNoDevices)         空状态文本（visibility=gone）
```

### 3.3 `fragment_alerts.xml` — 告警列表

```
LinearLayout (vertical)
├── LinearLayout [操作栏]
│   ├── TextView (id: tvAlertBadge)     未读告警计数徽章
│   └── MaterialButton (id: btnAckAll)  "全部确认" 按钮
├── RecyclerView (id: rvAlerts)         告警列表
└── TextView (id: tvNoAlerts)           空状态文本
```

### 3.4 `fragment_send.xml` — 发包工具

```
ScrollView
└── LinearLayout (vertical, padding=16dp)
    ├── 区域1：连接控制
    │   ├── TextInputLayout + TextInputEditText (id: etUdpHost)   UDP 目标 IP
    │   ├── TextInputLayout + TextInputEditText (id: etUdpPort)   UDP 端口
    │   ├── MaterialButton (id: btnStartUdp)   "启动 UDP 监听"
    │   ├── MaterialButton (id: btnStopUdp)    "停止 UDP"
    │   ├── MaterialButton (id: btnConnMqtt)   "连接 MQTT"
    │   └── MaterialButton (id: btnDiscMqtt)   "断开 MQTT"
    │
    ├── 区域2：单传感器发包
    │   ├── TextInputEditText (id: etAid)       AID
    │   ├── TextInputEditText (id: etSensorId)  传感器 ID
    │   ├── TextInputEditText (id: etUnit)      单位
    │   ├── TextInputEditText (id: etValue)     数值
    │   └── MaterialButton (id: btnSend)        "发送"
    │
    ├── 区域3：多传感器发包行（动态添加）
    │   └── LinearLayout (id: llMultiRows)      ← 动态注入 MultiSensorRow →
    │
    └── 区域4：原始 HEX 发包
        ├── TextInputEditText (id: etRawHex)    原始帧 HEX 字符串
        └── MaterialButton (id: btnSendRaw)     "发送 Raw"
```

### 3.5 `fragment_settings.xml` — 设置页

```
ScrollView
└── LinearLayout (vertical)
    ├── 分组：连接设置
    │   ├── udp_host / udp_port 输入框
    │   ├── mqtt_broker / mqtt_port / mqtt_topic 输入框
    │   └── 保存按钮
    │
    ├── 分组：外观设置
    │   ├── 强调色预设选择器（ThemePreset 8 个）
    │   └── 背景预设选择器（BgPreset 12 个）
    │
    ├── 分组：语言设置
    │   └── 语言选择按钮组（系统/中文/英文）
    │
    ├── 分组：数据维护
    │   ├── 数据库大小显示
    │   ├── 清理旧数据按钮
    │   └── 导出 CSV 按钮
    │
    └── 版本信息
```

### 3.6 `fragment_map_mirror.xml` — 地图

```
FrameLayout
└── WebView (id: wvMap, match_parent)
        ← 加载 Google Maps JavaScript API
          设备坐标由 GeohashDecoder 解码后注入 →
```

### 3.7 `fragment_secondary_panel.xml` — 二级面板

通用二级面板布局，包含：
- Toolbar（含返回按钮）
- Fragment 容器，用于在 `SecondaryActivity` 中承载各扩展 Fragment

---

## 4. RecyclerView Item 布局

### 4.1 `item_card_row.xml` — 仪表盘卡片行

```
MaterialCardView (background=@drawable/bg_shell_panel_compact)
└── LinearLayout (horizontal)
    ├── LinearLayout (vertical, weight=1)
    │   ├── TextView (id: tvDeviceName)    设备名称
    │   └── TextView (id: tvSensorSummary) 最新传感器值摘要
    ├── TextView (id: tvStatusBadge)       在线状态（@drawable/bg_status_pill）
    └── ImageView (id: ivDragHandle)       拖拽排序手柄图标
```

### 4.2 `item_dashboard_header.xml` — 仪表盘头部卡片

KPI 汇总区，展示：
- 总设备数 / 在线设备数 / 总告警数 / 吞吐量（msgs/s）
- UDP/MQTT 连接状态指示点

### 4.3 `item_dashboard_kpi_row.xml` — KPI 行

3 等分横排 KPI：设备总数、在线量、未读告警数。

### 4.4 `item_dashboard_readings.xml` — 传感器读数卡片

```
MaterialCardView
└── LinearLayout (vertical)
    ├── TextView  传感器 ID
    ├── TextView (id: tvValue)   数值 + 单位（大字）
    ├── ProgressBar (可选，仅 Gauge 模式)
    └── TextView  最后更新时间
```

### 4.5 `item_dashboard_charts.xml` — 图表卡片

```
MaterialCardView
└── LinearLayout
    ├── TextView 图表标题
    └── MiniTrendChartView (height=100dp)
```

### 4.6 `item_dashboard_gauges.xml` — 仪表卡片

```
MaterialCardView
└── LinearLayout
    ├── TextView 传感器标签
    ├── LinearProgressIndicator (id: progressGauge)
    └── LinearLayout [最小值 + 当前值 + 最大值]
```

### 4.7 `item_dashboard_custom_sensor.xml` — 自定义传感器卡片

```
MaterialCardView (可长按删除)
└── LinearLayout
    ├── TextView (id: tvCustomSensorId)  传感器 ID
    ├── TextView (id: tvCustomValue)     当前值
    └── ImageButton (id: ibDelete)       删除按钮
```

### 4.8 `item_dashboard_activity.xml` — 活动日志行

```
LinearLayout (horizontal, paddingVertical=8dp)
├── TextView (id: tvLogTime)    时间戳
├── TextView (id: tvLogAction)  动作类型（如 rule_triggered）
└── TextView (id: tvLogDetail)  详细描述（ellipsize=end）
```

---

## 5. 抽屉头部布局

### `nav_drawer_header.xml`

```
LinearLayout (vertical, padding=16dp, background=?colorSurfaceVariant)
├── ImageView                  应用图标（圆形）
├── TextView                   应用名称 "Gsyn-Java"
└── TextView                   版本号 + 协议版本
```

---

## 6. 视图 ID 速查表

| ID | 所在布局 | 类型 | 说明 |
|----|---------|------|------|
| `drawer_layout` | `activity_main` | `DrawerLayout` | 主抽屉容器 |
| `toolbar` | `activity_main` | `MaterialToolbar` | 顶部工具栏 |
| `fragment_container` | `activity_main` | `FrameLayout` | Fragment 替换容器 |
| `bottom_nav` | `activity_main` | `BottomNavigationView` | 底部导航栏 |
| `nav_view` | `activity_main` | `NavigationView` | 侧滑导航抽屉 |
| `rvDashboard` | `fragment_dashboard` | `RecyclerView` | 多设备仪表盘列表 |
| `fabAddCard` | `fragment_dashboard` | `FloatingActionButton` | 添加自定义卡片 |
| `singleDeviceScroll` | `fragment_dashboard` | `ScrollView` | 单设备模式覆盖层 |
| `sdDeviceName` | `fragment_dashboard` | `TextView` | 单设备模式设备名 |
| `sdStatusBadge` | `fragment_dashboard` | `TextView` | 状态胶囊 |
| `sdSensorGrid` | `fragment_dashboard` | `LinearLayout` | 动态传感器行容器 |
| `sdProgressWater` | `fragment_dashboard` | `LinearProgressIndicator` | 水位进度条 |
| `sdProgressHumidity` | `fragment_dashboard` | `LinearProgressIndicator` | 湿度进度条 |
| `sdChartTemp` | `fragment_dashboard` | `MiniTrendChartView` | 温度趋势图 |
| `btnExitSingleMode` | `fragment_dashboard` | `MaterialButton` | 退出单设备模式 |

---

## 7. 布局设计规范

### 主题颜色引用

所有颜色均使用 `?attr/colorXxx` 动态属性，不硬编码颜色值，确保多主题兼容：

| 用途 | 属性 |
|------|------|
| 页面背景 | `?android:colorBackground` |
| 卡片/面板背景 | `?attr/colorSurface` |
| 主要文本 | `?attr/colorOnSurface` |
| 次要文本 | `?attr/colorOnSurfaceVariant` |
| 强调色区域 | `?attr/colorPrimaryContainer` |
| 强调色区域文本 | `?attr/colorOnPrimaryContainer` |
| 轮廓线 | `?attr/colorOutline` |

### 间距规范

| 层级 | 间距 |
|------|------|
| 页面外边距 | 12 dp（`activity_main` padding） |
| 卡片内边距 | 12–16 dp |
| 卡片间距 | 8 dp（marginTop） |
| 按钮区间距 | 8–12 dp |

### Fragment 替换规则

- 一级页面（底部导航）：替换 `activity_main` 的 `fragment_container`
- 扩展页面（侧滑抽屉）：同样替换 `fragment_container`，底部导航取消选中
- 二级页面（独立打开）：替换 `activity_secondary` 的 `fragment_container`

