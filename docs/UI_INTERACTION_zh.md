# Gsyn-Java UI 交互逻辑文档

> 本文档是《UI 层开发者文档》（`UI_DEVELOPER_GUIDE_zh.md`）的交互专项补充。  
> 覆盖每个页面的**完整控件列表**、**每个控件按下后发生了什么**、**页面之间的跳转路径**，精确到代码行为。

---

## 目录

1. [导航总纲：页面地图](#1-导航总纲页面地图)
2. [MainActivity — 主壳层交互](#2-mainactivity--主壳层交互)
3. [DashboardFragment — 看板页交互](#3-dashboardfragment--看板页交互)
4. [AlertsFragment — 告警页交互](#4-alertsfragment--告警页交互)
5. [DevicesFragment — 设备页交互](#5-devicesfragment--设备页交互)
6. [SendFragment — 发包页交互](#6-sendfragment--发包页交互)
7. [SettingsFragment — 设置页交互](#7-settingsfragment--设置页交互)
8. [MapMirrorFragment — 地图页交互](#8-mapmirrorfragment--地图页交互)
9. [HistoryMirrorFragment — 历史数据页交互](#9-historymirrorfragment--历史数据页交互)
10. [RulesMirrorFragment — 规则页交互](#10-rulesmirrorfragment--规则页交互)
11. [HealthMirrorFragment — 健康状态页交互](#11-healthmirrorfragment--健康状态页交互)
12. [SecondaryActivity — 二级容器交互](#12-secondaryactivity--二级容器交互)
13. [交互流程图：完整页面跳转拓扑](#13-交互流程图完整页面跳转拓扑)
14. [边界情况与防护逻辑汇总](#14-边界情况与防护逻辑汇总)

---

## 1. 导航总纲：页面地图

### 一级页面（底部导航 + 侧滑抽屉共同控制）

```
应用启动
    │
    └─► MainActivity（唯一主 Activity）
            │
            ├─[底部导航栏 · 始终可见]──────────────────────────────────────────
            │   ├── 🏠 Dashboard    → DashboardFragment（默认展示）
            │   ├── 📋 Devices      → DevicesFragment
            │   ├── 🔔 Alerts       → AlertsFragment
            │   ├── 📤 Send         → SendFragment
            │   └── ⚙️ Settings     → SettingsFragment
            │
            └─[侧滑抽屉 · 点击汉堡图标或左划打开]────────────────────────────
                ├── Dashboard / Devices / Alerts / Send / Settings
                │     └── 与底部导航完全镜像；点击任意项 → 更新底部导航选中状态
                │           → 底部导航回调触发 → 加载对应 Fragment
                └── [扩展功能组]
                    ├── 🗺 Map        → MapMirrorFragment（底部导航全部取消选中）
                    ├── 📅 History   → HistoryMirrorFragment
                    ├── 📏 Rules     → RulesMirrorFragment
                    └── 💊 Health    → HealthMirrorFragment
```

### 二级页面（独立 SecondaryActivity）

`SecondaryActivity` 是独立的 Activity，通过 `startActivity(SecondaryActivity.intent(...))` 启动，当前 UI 中没有任何地方直接调用它（为未来的通知深链/外部 Intent 预留）。开发者可通过：

```java
startActivity(SecondaryActivity.intent(context, SecondaryActivity.MODE_MAP, R.string.title_map));
```

打开任意 Mirror Fragment 的独立全屏版。

---

## 2. `MainActivity` — 主壳层交互

### 页面结构（从上至下）

```
┌────────────────────────────────────────────────────────────┐
│  [汉堡图标]  Toolbar（标题 + 副标题）                        │  ← bg_shell_panel 背景
├────────────────────────────────────────────────────────────┤
│                                                            │
│              fragment_container（全高，承载当前 Fragment）  │
│                                                            │
├────────────────────────────────────────────────────────────┤
│  底部导航栏：Dashboard | Devices | Alerts | Send | Settings │  ← bg_shell_panel 背景
└────────────────────────────────────────────────────────────┘

抽屉层（从屏幕左侧滑出，宽 280dp）：
┌──────────────────┐
│  [抽屉 Header]   │  ← layout/nav_drawer_header.xml
│  Dashboard       │
│  Devices         │
│  Alerts          │
│  Send            │
│  Settings        │
│ ─ 扩展功能 ───── │
│  Map             │
│  History         │
│  Rules           │
│  Health          │
└──────────────────┘
```

---

### 2.1 汉堡图标（`ActionBarDrawerToggle`）

**控件：** Toolbar 左侧的汉堡/箭头图标，由 `ActionBarDrawerToggle` 自动管理  
**触发：** 点击

| 当前状态 | 点击后的行为 |
|---------|------------|
| 抽屉关闭 | 调用 `drawerLayout.openDrawer(GravityCompat.START)`，抽屉从左侧滑入；图标动画从汉堡变为箭头 |
| 抽屉打开 | 调用 `drawerLayout.closeDrawer(GravityCompat.START)`，抽屉关闭；图标还原为汉堡 |

> **注意：** 物理返回键不会关闭抽屉（MainActivity 未重写 `onBackPressed`），只会退出 Activity。

---

### 2.2 底部导航栏（`BottomNavigationView`）

**控件 ID：** `R.id.bottom_nav`  
**菜单资源：** `menu/bottom_nav.xml`（5 个 item）  
**触发：** 点击任意导航项

完整流程：

```
用户点击底部导航项
      │
      ▼
onBottomNavSelected(MenuItem item) 被回调
      │
      ├─ 根据 item.getItemId() 选择 Fragment、title、subtitle
      │
      ├─ binding.navView.setCheckedItem(对应抽屉项 ID)
      │      ← 保持抽屉与底部导航的选中状态同步
      │
      └─ showFragment(fragment, title, subtitle)
             ├─ binding.toolbar.setTitle(title)
             ├─ binding.toolbar.setSubtitle(subtitle)
             └─ getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit()
```

**各项触发对应的 Fragment 和 Toolbar 文字：**

| 导航项 | Fragment | Toolbar 标题 key | Toolbar 副标题 key |
|-------|---------|-----------------|------------------|
| Dashboard（首页）| `DashboardFragment` | `nav_dashboard` | `shell_toolbar_subtitle_dashboard` |
| Devices（设备）| `DevicesFragment` | `nav_devices` | `shell_toolbar_subtitle_devices` |
| Alerts（告警）| `AlertsFragment` | `nav_alerts` | `shell_toolbar_subtitle_alerts` |
| Send（发送）| `SendFragment` | `nav_send` | `shell_toolbar_subtitle_send` |
| Settings（设置）| `SettingsFragment` | `nav_settings` | `shell_toolbar_subtitle_settings` |

> **Fragment 替换策略：** 每次切换都是全量 `replace()`，上一个 Fragment 完全销毁（`onDestroyView` → 视图绑定置空），新 Fragment 重新创建。没有返回栈，不可"退回"到上一个 Tab。

---

### 2.3 侧滑抽屉导航（`NavigationView`）

**控件 ID：** `R.id.nav_view`  
**菜单资源：** `menu/drawer_nav.xml`  
**触发：** 左划屏幕 / 点击汉堡图标后，点击抽屉中的某一项

```
用户点击抽屉菜单项
      │
      ▼
onNavigationItemSelected(MenuItem item) 被回调
      │
      ├─ 镜像底部导航项（Dashboard / Devices / Alerts / Send / Settings）？
      │     └─ 是 → binding.bottomNav.setSelectedItemId(对应底部导航 ID)
      │               → 自动触发 onBottomNavSelected 回调 → 加载对应 Fragment
      │
      ├─ 扩展功能项（Map / History / Rules / Health）？
      │     └─ 是 → 构建对应 Fragment 和标题
      │             → 将底部导航所有项全部取消选中（详见下方说明）
      │             → showFragment(fragment, title, subtitle)
      │
      └─ 必然执行：binding.drawerLayout.closeDrawers()  ← 无论哪种情况抽屉都关闭
```

**底部导航取消选中的精确步骤（仅扩展功能项触发时）：**
```java
// Step 1：关闭整组的强制单选约束
binding.bottomNav.getMenu().setGroupCheckable(0, true, false);

// Step 2：逐项取消勾选
for (int i = 0; i < binding.bottomNav.getMenu().size(); i++) {
    binding.bottomNav.getMenu().getItem(i).setChecked(false);
}

// Step 3：恢复单选约束（后续点击仍互斥）
binding.bottomNav.getMenu().setGroupCheckable(0, true, true);
```
> 这三步是 Android 底部导航组件的官方绕行方法，官方 API 不允许直接取消所有选中项。

**抽屉专属菜单项触发的 Fragment 和标题：**

| 抽屉项 ID | Fragment | 标题 key | 副标题 key |
|----------|---------|---------|----------|
| `nav_drawer_map` | `MapMirrorFragment` | `title_map` | `shell_toolbar_subtitle_map` |
| `nav_drawer_history` | `HistoryMirrorFragment` | `title_history` | `shell_toolbar_subtitle_history` |
| `nav_drawer_rules` | `RulesMirrorFragment` | `title_rules` | `shell_toolbar_subtitle_rules` |
| `nav_drawer_health` | `HealthMirrorFragment` | `title_health` | `shell_toolbar_subtitle_health` |

---

### 2.4 应用启动时的初始状态

```java
if (savedInstanceState == null) {
    binding.bottomNav.setSelectedItemId(R.id.nav_dashboard);  // 默认选中 Dashboard
    binding.navView.setCheckedItem(R.id.nav_main_dashboard);  // 抽屉同步勾选
}
```

- `setSelectedItemId` 会**自动触发** `onBottomNavSelected` 回调，等价于用户手动点击了 Dashboard 项。
- 转屏/重建时（`savedInstanceState != null`），跳过此步骤，Android 系统自动恢复 Fragment 状态。

---

## 3. `DashboardFragment` — 看板页交互

### 页面结构（多设备模式）

```
┌──────────────────────────────────────────────────────────┐
│  RecyclerView（rvDashboard）                              │
│  ┌─────────────────────────┐                             │
│  │ HEADER 卡片             │  ← 固定第一张，不可拖拽      │
│  │  副标题行                │                             │
│  │  同步状态行              │                             │
│  │  传输状态行              │                             │
│  │  [切换单设备模式 按钮]   │                             │
│  ├─────────────────────────┤                             │
│  │ KPI_ROW1 卡片           │                             │
│  │  总设备数 | 在线率       │  ← 可长按拖拽              │
│  ├─────────────────────────┤                             │
│  │ KPI_ROW2 / ROW3 / ...   │                             │
│  │ GAUGES / CHARTS / ...   │                             │
│  │ CUSTOM_SENSOR × N       │                             │
│  └─────────────────────────┘                             │
│                                                          │
│                           [+ FAB]  ← 右下角浮动按钮     │
└──────────────────────────────────────────────────────────┘
```

---

### 3.1 FAB 按钮（`fabAddCard`）

**控件 ID：** `R.id.fabAddCard`  
**图标：** `ic_input_add`（加号）  
**触发：** 点击

```
点击 FAB
    │
    ▼
showAddSensorDialog() 被调用
    │
    ▼
弹出 MaterialAlertDialog，包含：
    ├── EditText（SensorId 输入框）：提示"Sensor ID (e.g. TEMP)"，inputType = text|캡 CHARACTERS
    └── EditText（Label 输入框）：提示"Display label (optional)"，inputType = text

[用户操作弹窗]
    │
    ├─ 点击"取消（Cancel）"
    │     └─ 关闭弹窗，无任何变化
    │
    └─ 点击"确定（OK）"
          │
          ├─ 读取输入，sid = 大写处理后的 SensorId
          │
          ├─ 如果 sid 为空：
          │     └─ Toast："Sensor ID cannot be empty"
          │         return，弹窗不关闭（用户可重新输入）
          │
          └─ 如果 sid 非空：
                ├─ cardConfig.addCustomSensor(sid, label 非空则用 label 否则用 sid)
                ├─ cardConfig.save(context) → 写入 SharedPreferences
                ├─ cardAdapter.setItems(cardConfig.visibleCards())
                │       → notifyDataSetChanged() → RecyclerView 刷新
                └─ refresh() → 立即从数据库拉取该传感器的最新数据填入新卡片
```

---

### 3.2 卡片长按 → 拖拽排序

**触发：** 在任意非 HEADER 卡片上**长按**

```
长按非 HEADER 卡片
    │
    ▼
ItemTouchHelper 捕获，触发 ACTION_STATE_DRAG
    ├─ 被拖拽卡片：alpha → 0.85f，elevation → 16f（产生浮起视觉）
    │
    ▼
拖拽过程中手指移动
    ├─ onMove(rv, from, to) 被连续调用
    ├─ cardAdapter.moveItem(f, t) → items 数组内交换位置
    └─ notifyItemMoved(f, t) → RecyclerView 动画

松手（drop）
    │
    ▼
clearView() 被调用
    ├─ 恢复 alpha → 1f，elevation → 0f
    └─ persistOrderFromAdapter()
            ├─ 遍历 adapter 当前 items，提取可见卡片
            ├─ 将隐藏卡片追加到末尾（不破坏隐藏卡片的 order 信息）
            ├─ cardConfig.cards 更新为新顺序
            └─ cardConfig.save(context) → 持久化到 SharedPreferences

注意：HEADER 卡片（位置 0）的 getMovementFlags() 返回 0，
      ItemTouchHelper 不会允许它被拖拽，也不会把其他卡片拖到它上面（onMove 会提前返回 false）。
```

---

### 3.3 HEADER 卡片上的"切换单设备模式"按钮（`btnToggleSingleMode`）

**控件 ID（ViewHolder 内）：** `R.id.btnToggleSingleMode`  
**触发：** 点击

```
点击切换按钮
    │
    ▼
DashboardCardAdapter.Listener.onToggleSingleMode() 被调用
    │
    ▼
DashboardFragment 内的实现：
    ├─ 读取当前 SharedPreferences: single_device_mode（默认 false）
    └─ 写入取反后的值（putBoolean("single_device_mode", !current).apply()）
           │
           └─ refresh() 立即调用
                   │
                   ├─ 如果新值 = true（进入单设备模式）：
                   │     ├─ rvDashboard → GONE
                   │     ├─ fabAddCard → GONE
                   │     ├─ singleDeviceScroll → VISIBLE
                   │     └─ 取 repository.getAllDevices().get(0) 填充单设备视图
                   │
                   └─ 如果新值 = false（退出单设备模式）：
                         ├─ rvDashboard → VISIBLE
                         ├─ fabAddCard → VISIBLE
                         └─ singleDeviceScroll → GONE
```

按钮文字会随状态同步更新（`btnToggle.setText(...)` 在每次 `bind` 时调用）：
- 当前为多设备模式：文字显示"Single Device Mode"（切换进入单设备）
- 当前为单设备模式：文字显示"Exit Single Mode"（切换回多设备）

---

### 3.4 单设备模式下的"退出"按钮（`btnExitSingleMode`）

**控件 ID：** `R.id.btnExitSingleMode`  
**触发：** 点击（仅在单设备模式视图可见时有效）

```
点击"退出单设备模式"
    │
    └─ enterSingleDeviceMode(false)
           ├─ rvDashboard → VISIBLE
           ├─ fabAddCard → VISIBLE
           └─ singleDeviceScroll → GONE

注意：此按钮只做视图切换，不写 SharedPreferences。
      下次进入 Dashboard 时，若 single_device_mode 仍为 true，
      refresh() 会再次触发单设备视图。
      若要永久退出，需通过 HEADER 卡片的切换按钮修改偏好设置。
```

---

### 3.5 CUSTOM_SENSOR 卡片上的删除按钮（`btnRemoveSensor`）

**控件 ID（CustomSensorVH 内）：** `R.id.btnRemoveSensor`  
**触发：** 点击

```
点击自定义传感器卡片右上角的删除（× ）按钮
    │
    ▼
CustomSensorVH 点击监听器
    ├─ pos = getAdapterPosition()
    └─ listener.onRemoveCustomCard(pos, items.get(pos)) 被调用

DashboardFragment 内的实现：
    ├─ cardConfig.removeCard(item) → 从 cards 列表中移除
    ├─ cardConfig.save(context) → 持久化
    └─ cardAdapter.setItems(cardConfig.visibleCards())
            → notifyDataSetChanged() → RecyclerView 刷新，该卡片消失
```

---

### 3.6 实时数据自动刷新

Dashboard 不需要用户手动触发刷新，通过以下两条路径自动更新：

| 路径 | 触发时机 | 调用链 |
|------|---------|--------|
| 新消息到达 | `TransportManager` 收到设备消息 | `onMessage()` → `runOnUiThread(this::refresh)` |
| 统计更新 | `TransportManager` 发出统计心跳 | `onStats()` → `lastMsgPerSecond = stats.messagesPerSecond` → `runOnUiThread(this::refresh)` |
| 手动进入页面 | Fragment `onStart()` | `refresh()` |

---

## 4. `AlertsFragment` — 告警页交互

### 页面结构

```
┌────────────────────────────────────────────────┐
│ 摘要卡片                                        │
│   tvSummary（总数行）                            │
│   Critical: [tvCriticalCount]                   │
│   Warning:  [tvWarningCount]                    │
│   Info:     [tvInfoCount]                       │
│   未确认:   [tvUnackedCount]                    │
├────────────────────────────────────────────────┤
│ 筛选卡片                                        │
│   "按级别筛选" 标签                              │
│   [spinnerLevel]（下拉选择器）                   │
│   tvFilterState（当前筛选状态文字）              │
├────────────────────────────────────────────────┤
│ 告警列表卡片（填满剩余高度）                     │
│   ListView（id: list）                          │
│     每行：CardRowAdapter.Row                    │
│     空态：tvEmpty                               │
└────────────────────────────────────────────────┘
整页包裹在 SwipeRefreshLayout 中
```

---

### 4.1 下拉刷新（`SwipeRefreshLayout`）

**触发：** 用户在页面向下滑动至顶部后继续下拉

```
触发下拉刷新手势
    │
    └─ swipeRefresh.setOnRefreshListener() 回调 → load()
            ├─ 读取 spinnerLevel 当前选中位置，换算成 level Integer
            ├─ repository.getAlerts(level, 200) → 更新 currentAlerts
            ├─ 重新查询 critical(2) / warning(1) / info(0) / unacked 的各自计数
            ├─ 更新 tvSummary / tvCriticalCount / tvWarningCount / tvInfoCount / tvUnackedCount / tvFilterState
            ├─ 构建 CardRowAdapter.Row 列表 → adapter.setRows(rows)
            └─ swipeRefresh.setRefreshing(false) ← 停止加载动画
```

---

### 4.2 级别筛选下拉框（`spinnerLevel`）

**控件 ID：** `R.id.spinner_level`  
**选项：** 全部 / Critical / Warning / Info  
**触发：** 选择任一选项

```
用户切换下拉框选项
    │
    └─ OnItemSelectedListener.onItemSelected() 被回调
            └─ load()（与下拉刷新调用的是同一个方法）
```

选项到 level 整数的精确映射：
```
位置 0 → level = null（查询全部）
位置 1 → level = 2（Critical）
位置 2 → level = 1（Warning）
位置 3 → level = 0（Info）
```

---

### 4.3 告警列表行点击

**触发：** 点击列表中任一行

```
点击告警行（位置 position）
    │
    ├─ 读取 currentAlerts.get(position) → alert 对象
    │
    ├─ 如果 alert.acknowledged == true：
    │     └─ 无任何反应（已确认告警点击无效果）
    │
    └─ 如果 alert.acknowledged == false（未确认）：
            ├─ repository.acknowledgeAlert(alert.id)
            │       → 数据库写入：将该告警标记为已确认
            ├─ Toast.makeText(ctx, R.string.alerts_acked_toast, SHORT).show()
            │       → 显示"已确认"提示
            └─ load() → 刷新列表（告警行的徽章文字从"CRITICAL"变为"CRITICAL · ACK"）
```

> **视觉区别：** 已确认告警的徽章文字在代码中拼接为 `lv + " · ACK"`（如 `"WARNING · ACK"`），颜色不变，使开发者可以直观识别已处理状态。

---

## 5. `DevicesFragment` — 设备页交互

### 页面结构

```
┌────────────────────────────────────────────────────┐
│ 统计卡片                                            │
│   tvSummary（总数行）                               │
│   Total: [tvTotalDevices]  Online: [tvOnlineDevices]│
│   Offline:[tvOfflineDevices] Filtered:[tvFilteredDevices]│
├────────────────────────────────────────────────────┤
│ 搜索框（etSearch，带搜索图标）                       │
├────────────────────────────────────────────────────┤
│ 设备列表卡片（填满剩余高度）                         │
│   ListView（id: list）                              │
│     每行：CardRowAdapter.Row                        │
│     空态：tvEmpty                                   │
└────────────────────────────────────────────────────┘
整页包裹在 SwipeRefreshLayout 中
```

---

### 5.1 下拉刷新

**触发：** 下拉手势

```
触发下拉刷新
    │
    └─ load()
            ├─ allDevices.clear()
            ├─ allDevices.addAll(repository.getAllDevices()) ← 重新从数据库加载完整设备列表
            ├─ filter() ← 基于当前搜索框文字重新过滤
            └─ swipeRefresh.setRefreshing(false)
```

---

### 5.2 搜索框（`etSearch`）

**触发：** 每次文字改变（`TextWatcher.onTextChanged`）

```
用户在搜索框中输入/删除字符
    │
    └─ filter() 立即被调用（无防抖，实时过滤）
            │
            ├─ q = etSearch.getText().toString().trim().toLowerCase()
            │
            ├─ 遍历 allDevices，保留满足以下任一条件的设备：
            │     ① q 为空（显示全部）
            │     ② String.valueOf(d.aid).contains(q) （按 AID 匹配）
            │     ③ d.name != null && d.name.toLowerCase().contains(q) （按名称匹配）
            │
            ├─ visibleDevices.clear(); visibleDevices.addAll(filtered)
            │
            ├─ 更新统计数字：tvTotalDevices / tvOnlineDevices / tvOfflineDevices / tvFilteredDevices
            │
            ├─ tvEmpty 文字：
            │     q 为空 → "还没有注册设备"
            │     q 非空 → "未找到匹配的设备"
            │
            └─ adapter.setRows(toRows(filtered)) → ListView 刷新
```

---

### 5.3 设备列表行点击（详情弹窗）

**触发：** 点击任意设备行

```
点击设备行（位置 position）
    │
    └─ showDeviceDetails(visibleDevices.get(position))
            │
            ├─ repository.getLatestReadingsByDevice(device.aid) → 获取最新传感器读数
            │
            ├─ 构建详情字符串（StringBuilder）：
            │     AID: xxx
            │     名称: xxx
            │     类型: xxx（大写，无则显示"SENSOR"）
            │     状态: xxx
            │     传输: xxx（大写）
            │     位置: lat, lng
            │     最后活跃: 绝对时间（相对时间）
            │     读数:
            │       • sensorId = value unit · datetime
            │       • sensorId = value unit · datetime
            │       ……
            │
            └─ AlertDialog.Builder
                    .setTitle(设备名称，无则 "Device AID")
                    .setMessage(详情字符串)
                    .setPositiveButton("关闭 / Close", null)
                    .show()
                           └─ 点击"关闭"：AlertDialog 自动 dismiss，回到设备列表
```

---

## 6. `SendFragment` — 发包页交互

### 页面结构

```
┌────────────────────────────────────────────────────────┐
│ 目标设备卡片                                            │
│   [spinnerDevice]（设备选择器）                         │
│   [etAid] [etTid] [etSeq]（路由参数输入框）             │
│   [etIp] [etPort]（IP 和端口输入框）                    │
│   tvRouteSummary（路由摘要）                            │
├────────────────────────────────────────────────────────┤
│ TabLayout：[控制] [数据] [原始]                         │
├────────────────────────────────────────────────────────┤
│ tabControl（可见/不可见）：8个控制报文发送按钮           │
│ tabData（可见/不可见）：                                │
│   单传感器卡片：spinnerSensorId/Unit/State + etValue   │
│                [btnSendSingle]                         │
│   多传感器卡片：动态行 + [btnAddSensor] [btnSendMulti] │
│ tabRaw（可见/不可见）：etRaw + [btnSendRaw] + 命令参考表│
├────────────────────────────────────────────────────────┤
│ Dispatch Console（日志面板）                            │
│   tvLastResult（最近一次结果）                          │
│   tvLog（最多 20 条历史记录）                           │
└────────────────────────────────────────────────────────┘
ScrollView 包裹全页（可滚动）
```

---

### 6.1 设备选择器（`spinnerDevice`）

**触发：** 选择任一选项（"手动输入" 或 "AID x  设备名"）

```
选择设备
    │
    ├─ 选中位置 == 0（"手动输入"）
    │     └─ 不修改 etAid 的内容；updateRouteSummary() 刷新摘要行
    │
    └─ 选中位置 > 0（实际设备）
            ├─ d = devices.get(pos - 1)
            ├─ etAid.setText(String.valueOf(d.aid)) ← 自动填充 AID 字段
            └─ updateRouteSummary()
```

---

### 6.2 AID / TID / SEQ / IP / Port 输入框

这 5 个输入框**没有程序监听器**（无 TextWatcher）。它们的值在以下时机被读取：
- 任何发送按钮点击时（`sendAndLog` 调用时实时读取）
- `updateRouteSummary()` 调用时（在选择设备后触发，不实时监听）

---

### 6.3 Tab 切换（`TabLayout`）

**触发：** 点击三个 Tab 标签之一

```
点击 Tab
    │
    └─ switchTab(tab.getPosition())
            ├─ tabControl.setVisibility(pos == 0 ? VISIBLE : GONE)
            ├─ tabData.setVisibility(pos == 1 ? VISIBLE : GONE)
            └─ tabRaw.setVisibility(pos == 2 ? VISIBLE : GONE)
```

视图全量切换（VISIBLE/GONE），不是 ViewPager，无滑动动画。初始显示 Tab 0（控制）。

---

### 6.4 控制 Tab — 8 个发送按钮

所有按钮触发逻辑结构相同，区别只在于调用的 `PacketBuilder` 方法和日志标签：

```
点击任意控制报文按钮（以 btnSendPing 为例）
    │
    ├─ seq = parseInt(textOf(etSeq), 0)
    ├─ frame = PacketBuilder.buildPing(seq) ← 构建 PING 报文字节数组
    └─ sendAndLog("PING", frame)

sendAndLog(label, frame) 的完整逻辑：
    ├─ 如果 frame == null：
    │     └─ Toast "构建报文失败" → return
    │
    ├─ aid  = parseInt(textOf(etAid), 1)
    ├─ host = textOf(etIp).trim()
    ├─ port = parseInt(textOf(etPort), 9876)
    │
    ├─ ok = transport().sendCommand(frame, aid, host, port)
    │       ← 通过 TransportManager 向目标 host:port 发送 UDP 报文
    │
    ├─ repository.logOperation("SEND_CMD", label + " → AID:" + aid + " host:port ok=" + ok)
    │       ← 写入操作日志（在规则页面和健康页面可见）
    │
    ├─ entry = "时间  标签  ✓ OK / ✗ FAIL  len=N"
    ├─ logs.add(0, entry)（插到头部）
    ├─ 如果 logs.size() > 20 → logs.remove(最后一条)（维持最多 20 条）
    │
    ├─ tvLastResult.setText(最新一条 entry)
    ├─ tvLog.setText(所有 logs 用换行连接)
    ├─ Toast：ok → "发送成功" | !ok → "发送失败"
    └─ updateRouteSummary()
```

**各按钮和对应的 PacketBuilder 方法：**

| 按钮 ID | 协议命令 | HEX | PacketBuilder 方法 | 读取的路由参数 |
|--------|---------|-----|-------------------|--------------|
| `btnSendPing` | PING | 0x01 | `buildPing(seq)` | SEQ |
| `btnSendPong` | PONG | 0x02 | `buildPong(seq)` | SEQ |
| `btnSendIdRequest` | ID_REQUEST | 0x03 | `buildIdRequest(seq)` | SEQ |
| `btnSendIdAssign` | ID_ASSIGN | 0x05 | `buildIdAssign(aid)` | AID |
| `btnSendTimeRequest` | TIME_REQUEST | 0x07 | `buildTimeRequest(seq)` | SEQ |
| `btnSendHsAck` | HANDSHAKE_ACK | 0x09 | `buildHandshakeAck(seq)` | SEQ |
| `btnSendHsNack` | HANDSHAKE_NACK | 0x0A | `buildHandshakeNack(seq)` | SEQ |
| `btnSendSecureDict` | SECURE_DICT_READY | 0x10 | `buildSecureDictReady(seq)` | SEQ |

---

### 6.5 数据 Tab — 单传感器发送（`btnSendSingle`）

**触发：** 点击"发送单传感器"按钮

```
点击 btnSendSingle
    │
    ├─ aid   = parseInt(etAid, 1)
    ├─ tid   = parseInt(etTid, 1)
    ├─ sid   = spinnerSensorId.getSelectedItem()（字符串）
    ├─ unit  = spinnerUnit.getSelectedItem()
    ├─ state = spinnerState.getSelectedItem()
    ├─ val   = parseDouble(etValue, 0.0)
    │
    ├─ frame = PacketBuilder.buildSensorPacket(aid, tid, currentTime/1000L, sid, unit, val)
    │          ← 构建 DATA_FULL（0x20）帧
    │
    ├─ sendAndLog("DATA_FULL[" + sid + "]", frame)（同上面的完整逻辑）
    └─ updateSinglePreview() ← 刷新预览行，展示当前报文体格式
```

**传感器 ID Spinner 联动：** 当用户切换传感器 ID 时，单位 Spinner 会自动选中该传感器的默认单位：
```
spinnerSensorId.OnItemSelectedListener.onItemSelected()
    │
    ├─ sid = ProtocolConstants.OS_SENSOR_IDS.get(pos)
    ├─ defUnit = ProtocolConstants.defaultUnitFor(sid)
    ├─ idx = ProtocolConstants.OS_UNITS.indexOf(defUnit)
    ├─ 如果 idx >= 0 → spinnerUnit.setSelection(idx)
    └─ updateSinglePreview()
```

---

### 6.6 数据 Tab — 添加多传感器行（`btnAddSensor`）

**触发：** 点击"+ 添加传感器行"按钮

```
点击 btnAddSensor
    │
    └─ addMultiSensorRow()
            │
            ├─ 动态创建水平 LinearLayout（row）
            ├─ 创建三个 Spinner：spSid（sensor IDs）、spUnit（units）、spState（states）
            ├─ 创建一个 TextInputEditText（值输入框，默认 "0.0"）
            ├─ 创建一个 MaterialButton（"✕" 删除按钮）
            │
            ├─ spSid 联动：切换 sensor ID 时自动选中默认单位（同单传感器逻辑）
            │
            ├─ row 加入 containerMultiSensor（LinearLayout，垂直）
            ├─ MultiSensorRow(row, spSid, spUnit, spState, etVal) 存入 multiRows 列表
            │
            └─ 删除按钮点击监听：
                    └─ multiRows.remove(msr)
                       containerMultiSensor.removeView(row) ← 从 UI 上移除该行
```

---

### 6.7 数据 Tab — 发送多传感器（`btnSendMulti`）

**触发：** 点击"发送多传感器"按钮

```
点击 btnSendMulti
    │
    ├─ 如果 multiRows 为空：
    │     └─ Toast "请先添加传感器行" → return
    │
    ├─ aid = parseInt(etAid, 1)
    ├─ tid = parseInt(etTid, 1)
    ├─ ts  = currentTime / 1000L
    │
    ├─ 遍历 multiRows，构建 List<PacketBuilder.SensorEntry>：
    │     每条：sid / unit / state / 解析 val
    │
    ├─ frame = PacketBuilder.buildMultiSensorPacket(aid, tid, ts, entries)
    │          ← 构建包含多个传感器字段的 DATA_FULL 帧
    │
    └─ sendAndLog("DATA_FULL[multi×" + entries.size() + "]", frame)
```

---

### 6.8 原始 Tab — 发送 Raw HEX（`btnSendRaw`）

**触发：** 点击"发送原始报文"按钮

```
点击 btnSendRaw
    │
    ├─ hex = etRaw.getText().toString().trim()
    │
    ├─ 如果 hex 为空：
    │     └─ Toast "请输入十六进制报文" → return
    │
    ├─ frame = PacketBuilder.buildRawHex(hex)
    │          ← 将十六进制字符串（可含空格）解析为 byte[]
    └─ sendAndLog("RAW_HEX", frame)
```

---

## 7. `SettingsFragment` — 设置页交互

### 页面结构（ScrollView，从上到下）

```
┌──────────────────────────────────────────────────────────┐
│ 运行时状态卡片                                            │
│   tvTransportStatus / tvRuntimeHint                       │
│   设备数 | 告警数 | 规则数 | 存储大小（只读展示）         │
├──────────────────────────────────────────────────────────┤
│ [UDP 设置卡片]                                            │
│   switchUdp（UDP 启用开关）                               │
│   etUdpHost / etUdpPort                                   │
├──────────────────────────────────────────────────────────┤
│ [MQTT 设置卡片]                                           │
│   switchMqtt（MQTT 启用开关）                             │
│   etMqttBroker / etMqttPort / etMqttTopic                │
├──────────────────────────────────────────────────────────┤
│ [外观设置卡片]                                            │
│   强调色 ChipGroup（chipGroupAccent）                     │
│   背景预设 ChipGroup（chipGroupBg）                       │
│   etTileUrl（地图瓦片 URL）                               │
│   [btnSave]（保存按钮）                                   │
├──────────────────────────────────────────────────────────┤
│ [语言设置卡片]                                            │
│   rgLanguage（RadioGroup）                                │
│     rbLangSystem / rbLangEn / rbLangZh                   │
├──────────────────────────────────────────────────────────┤
│ [看板卡片可见性卡片]                                       │
│   switchSingleDeviceMode + 分隔线                        │
│   switchCardKpiRow1~3 / Gauges / Charts / Activity / Readings│
└──────────────────────────────────────────────────────────┘
```

---

### 7.1 语言选择（`rgLanguage` RadioGroup）

**触发：** 点击任意 RadioButton（立即生效，无需按保存）

```
点击 rbLangSystem / rbLangEn / rbLangZh
    │
    └─ setOnCheckedChangeListener 回调
            ├─ rbLangEn → selected = "en"
            ├─ rbLangZh → selected = "zh"
            └─ 其他（rbLangSystem）→ selected = ""（空字符串，跟随系统）
                   │
                   └─ LocaleHelper.applyAndSave(selected)
                           ├─ 将语言偏好写入 SharedPreferences
                           └─ AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                                   ← AppCompat 自动重建 Activity，完成语言切换
                                   ← 重建后页面上的所有 string 资源都更新为新语言
```

> **注意：** 语言切换会触发 `requireActivity().recreate()`，与按下保存按钮的重建**是独立的两次重建**。切换语言后，如果未按保存，主题/传输设置不会丢失（因为它们从 SharedPreferences 读取）。

---

### 7.2 强调色 Chip 选择（`chipGroupAccent`）

**触发：** 点击任意颜色 Chip（立即写入偏好，无需按保存）

```
点击颜色 Chip（如 Teal）
    │
    └─ chip.setOnClickListener(v -> AppThemeConfig.saveThemePreset(context, preset))
            └─ 将 preset 枚举名写入 SharedPreferences key: "theme_preset"
                （视觉效果需按保存后 recreate 才能应用）

注意：颜色 Chip 使用 setOnClickListener 而非 OnCheckedChangeListener，
      原因是 ChipGroup 的 OnCheckedChangeListener 在程序化调用 check() 时
      也会触发，导致初始化时写入偏好设置产生副作用。
```

---

### 7.3 背景预设 Chip 选择（`chipGroupBg`）

与强调色 Chip 完全相同的模式，写入 key: `"bg_preset"`。

---

### 7.4 单设备模式开关（`switchSingleDeviceMode`）

**触发：** 拨动开关（立即生效，无需按保存）

```
拨动 switchSingleDeviceMode
    │
    └─ setOnCheckedChangeListener 回调
            └─ SharedPreferences.edit()
                    .putBoolean("single_device_mode", checked)
                    .apply()
                ← 下次进入 Dashboard 时，refresh() 会读取此值决定显示模式
```

---

### 7.5 保存按钮（`btnSave`）

**触发：** 点击  
**这是设置页唯一让大多数设置生效的按钮**（语言和单设备模式除外）

```
点击 btnSave
    │
    ▼
savePrefs() 执行以下步骤（顺序执行）：

步骤 1：saveCardConfig()
    ├─ 读取当前 DashboardCardConfig
    ├─ 同步 7 个卡片可见性开关的状态到 cfg
    ├─ 同步 single_device_mode 开关状态
    └─ cfg.save(context) → 写入 SharedPreferences("dashboard_card_prefs")

步骤 2：SharedPreferences 写入
    prefs.edit()
        .putString("udp_host", etUdpHost 内容)
        .putInt("udp_port", etUdpPort 内容，解析失败则 9876)
        .putBoolean("udp_enabled", switchUdp 状态)
        .putString("mqtt_broker", etMqttBroker 内容)
        .putInt("mqtt_port", etMqttPort 内容，解析失败则 1883)
        .putString("mqtt_topic", etMqttTopic 内容)
        .putBoolean("mqtt_enabled", switchMqtt 状态)
        .putString("tile_url", etTileUrl 内容)
        .apply()

步骤 3：TransportManager 重新配置（try-catch 包裹）
    ├─ switchUdp.isChecked() == true
    │     → manager.startUdp(host, port) ← 启动/重启 UDP 监听
    └─ switchUdp.isChecked() == false
           → manager.stopUdp()
    ├─ switchMqtt.isChecked() == true
    │     → manager.connectMqtt(broker, port, topic)
    └─ switchMqtt.isChecked() == false
           → manager.disconnectMqtt()
    ├─ 如果上述过程抛出异常：
    │     Toast "应用传输设置时出错：xxx"（LONG 时长）
    └─ 无异常：
           └─ refreshTransportStatus() ← 更新顶部状态卡片展示

步骤 4：Toast "设置已保存"（SHORT 时长）

步骤 5：requireActivity().recreate()
    ← 强制重建 MainActivity，使主题覆盖层（强调色/背景）生效
    ← 重建后，会重新 setSelectedItemId(nav_dashboard)，回到看板页
```

---

## 8. `MapMirrorFragment` — 地图页交互

### 页面结构

```
┌──────────────────────────────────────────────────────────┐
│ 控制栏（bg_shell_panel_compact 背景）                      │
│   tvMapSummary（设备数/在线数/已绘制数）                   │
│   [chipNormal] [chipSatellite] [chipHybrid]   [刷新]     │
├──────────────────────────────────────────────────────────┤
│ mapContainer（FrameLayout）                               │
│   SupportMapFragment（填满，由代码动态嵌入）               │
│     ├─ 缩放控件（右下角）                                  │
│     └─ 指南针                                             │
└──────────────────────────────────────────────────────────┘
```

---

### 8.1 手动刷新按钮（`btnRefresh`）

**触发：** 点击"刷新"按钮（Tonal 风格）

```
点击刷新
    │
    └─ loadMarkers()
            ├─ 如果 googleMap == null → return（地图未就绪）
            ├─ googleMap.clear() + currentMarkers.clear()
            │
            ├─ 遍历 repository.getAllDevices()：
            │     ├─ 统计在线数（status == "online"）
            │     ├─ 过滤无 GPS 设备（|lat| < 1e-7 且 |lng| < 1e-7 的跳过）
            │     ├─ 为每个有 GPS 的设备：
            │     │     ├─ 构建 MarkerOptions：位置 + 标题（设备名/AID）+ snippet（AID·状态·传输·相对时间）
            │     │     ├─ 在线 → HUE_GREEN 标记；离线 → HUE_ORANGE 标记
            │     │     ├─ googleMap.addMarker(options)
            │     │     └─ boundsBuilder.include(pos) 扩展视野边界
            │     └─ （无 GPS 设备跳过，不绘制）
            │
            ├─ 更新 tvMapSummary
            │
            └─ 如果有设备被绘制：
                    ├─ mappedCount == 1：
                    │     googleMap.animateCamera(newLatLngZoom(bounds.getCenter(), 14f))
                    └─ mappedCount > 1：
                          googleMap.animateCamera(newLatLngBounds(bounds, 120))
```

---

### 8.2 地图类型 Chip（`chipGroupMapType`）

**触发：** 点击 Normal / Satellite / Hybrid Chip（单选，`singleSelection = true`）

```
点击地图类型 Chip
    │
    └─ setOnCheckedStateChangeListener 回调
            ├─ 如果 googleMap == null 或 ids 为空 → return
            ├─ chipSatellite → googleMap.setMapType(MAP_TYPE_SATELLITE)
            ├─ chipHybrid   → googleMap.setMapType(MAP_TYPE_HYBRID)
            └─ 其他（chipNormal）→ googleMap.setMapType(MAP_TYPE_NORMAL)
```

---

### 8.3 地图标记点击

**触发：** 点击地图上的绿色/橙色标记

```
点击标记
    │
    └─ googleMap.setOnMarkerClickListener 回调
            ├─ marker.showInfoWindow() ← 弹出信息窗口
            │     内容：title = 设备名/AID
            │           snippet = "AID x · ONLINE/OFFLINE · UDP · 3分钟前"
            └─ return true（表示事件已处理，阻止默认的相机移动）
```

---

### 8.4 自动刷新（10 秒循环）

```
onStart() → autoRefreshHandler.post(autoRefreshRunnable)
    autoRefreshRunnable：
        ├─ loadMarkers()（同手动刷新逻辑）
        └─ autoRefreshHandler.postDelayed(this, 10_000)（10 秒后再次执行）

onStop() → autoRefreshHandler.removeCallbacks(autoRefreshRunnable)（停止轮询）
```

---

## 9. `HistoryMirrorFragment` — 历史数据页交互

### 页面结构（fragment_secondary_panel.xml）

```
┌──────────────────────────────────────────────┐
│ 摘要卡片                                      │
│   tvSectionLabel（"历史数据"）                 │
│   tvSummary（"共 N 条记录"）                   │
├──────────────────────────────────────────────┤
│ 详情卡片（colorSurfaceVariant 背景）            │
│   tvDetail（"最新记录时间：xxx"）               │
├──────────────────────────────────────────────┤
│ [btnAction]（"导出 CSV"）                      │
├──────────────────────────────────────────────┤
│ 历史数据列表（填满剩余高度）                    │
│   ListView                                    │
│     每行：sensorId · value unit | 设备 | 时间  │
│     空态：tvEmpty                              │
└──────────────────────────────────────────────┘
```

---

### 9.1 导出 CSV 按钮（`btnAction`，文字 = "导出 CSV"）

**触发：** 点击

```
点击"导出 CSV"
    │
    └─ exportCsv()
            ├─ try:
            │     file = repository.exportHistoryCsv()
            │     ← 数据库将近期传感器数据写入应用外部存储的 CSV 文件
            │
            │     Toast.makeText(ctx, "已导出到：" + file.getAbsolutePath(), LONG).show()
            │
            └─ catch(Exception ex):
                    Toast.makeText(ctx, "导出失败：" + ex.getMessage(), LONG).show()
```

> 导出成功后文件路径会在 Toast 中展示，用户可通过文件管理器访问；不会打开任何新的 Activity 或弹窗。

---

### 9.2 页面进入时自动加载

**触发：** `onStart()`

```
onStart() → load()
    ├─ 查询最近 24h 传感器数据，最多 500 条
    ├─ 更新 tvSectionLabel / tvSummary / tvDetail / tvEmpty
    └─ 构建卡片行列表 → adapter.setRows(cards) → ListView 刷新
```

历史数据列表**无下拉刷新**，离开并重新进入页面是唯一的刷新方式。

---

## 10. `RulesMirrorFragment` — 规则页交互

### 页面结构（fragment_secondary_panel.xml）

```
┌──────────────────────────────────────────────┐
│ 摘要卡片                                      │
│   tvSectionLabel（"自动化规则"）               │
│   tvSummary（"N 条规则，M 条操作日志"）         │
├──────────────────────────────────────────────┤
│ 详情卡片                                      │
│   tvDetail（规则说明文本）                     │
├──────────────────────────────────────────────┤
│ [btnAction]（"+ 新建规则"）                    │
├──────────────────────────────────────────────┤
│ 规则 + 日志混合列表（填满剩余高度）             │
│   ListView                                    │
│     前 N 行：规则（ENABLED/DISABLED 徽章）      │
│     后 M 行：操作日志（LOG 徽章）               │
│     空态：tvEmpty                              │
└──────────────────────────────────────────────┘
```

---

### 10.1 新建规则按钮（`btnAction`，文字 = "新建规则"）

**触发：** 点击

```
点击"新建规则"
    │
    └─ showCreateRuleDialog()
            │
            └─ AlertDialog：
                    包含两个 EditText：
                    ├─ sensorInput（传感器 ID，默认 "TEMP"）
                    └─ thresholdInput（阈值，默认 "50"）
                    │
                    ├─ 点击"取消"：关闭弹窗，无任何变化
                    │
                    └─ 点击"创建"：
                            ├─ rule.name = sensorId + " threshold"
                            ├─ rule.sensorIdFilter = sensorId（原样保存）
                            ├─ rule.operator = ">"（固定）
                            ├─ rule.threshold = parseDouble(thresholdInput, 50)
                            ├─ rule.actionType = "create_alert"（固定）
                            │
                            ├─ repository.saveRule(rule) ← 写入数据库
                            ├─ repository.logOperation("CREATE_RULE", "name=xxx threshold=xxx")
                            └─ load() ← 刷新列表，新规则出现在顶部
```

---

### 10.2 规则行 — 单击切换启用状态

**触发：** 单击列表中位置 < `cachedRules.size()` 的行

```
单击规则行（position）
    │
    └─ toggleRuleIfNeeded(position)
            ├─ 如果 position < 0 或 >= cachedRules.size() → return（忽略日志行）
            │
            ├─ rule = cachedRules.get(position)
            ├─ repository.toggleRule(rule.id, !rule.enabled)
            │       ← 数据库中反转该规则的 enabled 字段
            │
            ├─ repository.logOperation("TOGGLE_RULE", "ruleId=x enabled=true/false")
            │
            ├─ Toast："规则已启用" / "规则已禁用"（SHORT）
            │
            └─ load() ← 刷新列表（徽章颜色和文字随 enabled 状态更新）
```

---

### 10.3 规则行 — 长按删除规则

**触发：** 长按列表中位置 < `cachedRules.size()` 的行

```
长按规则行（position）
    │
    └─ deleteRuleIfNeeded(position) → 返回 boolean
            ├─ 如果 position < 0 或 >= cachedRules.size() → return false（事件未消费）
            │
            ├─ rule = cachedRules.get(position)
            ├─ repository.deleteRule(rule.id) ← 从数据库删除
            ├─ repository.logOperation("DELETE_RULE", "ruleId=x name=xxx")
            ├─ Toast："已删除规则 xxx"（SHORT）
            ├─ load() ← 立即刷新列表
            └─ return true（事件已消费）
```

> **无确认弹窗**：长按立即删除，没有二次确认 Dialog。开发者如需添加保护，可在 `deleteRuleIfNeeded` 中插入一个 AlertDialog。

---

### 10.4 操作日志行 — 点击与长按

- **单击**：`toggleRuleIfNeeded(position)` 被调用，但 `position >= cachedRules.size()`，方法直接 `return`，**无任何效果**。
- **长按**：`deleteRuleIfNeeded(position)` 被调用，同样因越界判断 `return false`，**无任何效果**。

---

## 11. `HealthMirrorFragment` — 健康状态页交互

### 页面结构（fragment_secondary_panel.xml）

```
┌──────────────────────────────────────────────┐
│ 摘要卡片                                      │
│   tvSectionLabel（"系统健康"）                 │
│   tvSummary（"N 台设备，X 条/秒，共 Y 条"）    │
├──────────────────────────────────────────────┤
│ 详情卡片                                      │
│   tvDetail（"UDP: 启用 | MQTT: 已连接 | DB: NKB"）│
├──────────────────────────────────────────────┤
│ [btnAction]（"清理旧数据"）                    │
├──────────────────────────────────────────────┤
│ 设备列表（填满剩余高度）                        │
│   ListView（每行：设备名 + AID + 传输类型 + 在线/离线）│
└──────────────────────────────────────────────┘
```

---

### 11.1 清理旧数据按钮（`btnAction`，文字 = "清理旧数据"）

**触发：** 点击

```
点击"清理旧数据"
    │
    └─ prune()
            ├─ deleted = repository.pruneOldData(7)
            │       ← 删除 7 天前的传感器数据、告警等旧记录
            │       ← 返回值为实际删除的记录数
            │
            ├─ Toast："已清理 N 条旧记录"（LONG）
            └─ load() ← 刷新页面（数据库大小等信息会更新）
```

---

### 11.2 页面进入时自动加载

**触发：** `onStart()`

```
onStart() → load()
    ├─ TransportManager.getLastStats() → 读取最新传输统计
    ├─ repository.getAllDevices() → 所有设备列表
    ├─ repository.getDatabaseSizeBytes() / 1024 → DB 大小（KB）
    ├─ 更新 tvSectionLabel / tvSummary / tvDetail / tvEmpty
    └─ 构建设备行列表 → adapter.setRows(rows) → ListView 刷新
```

设备列表**无下拉刷新**，也无 SwipeRefreshLayout。

---

## 12. `SecondaryActivity` — 二级容器交互

### 页面结构

```
┌──────────────────────────────────────────────┐
│ Toolbar                                       │
│   [← 返回箭头]  标题（来自 Intent）            │
│                副标题（来自 mode 推导）         │
├──────────────────────────────────────────────┤
│ secondary_fragment_container                  │
│   （填满，承载一个 Mirror Fragment）           │
└──────────────────────────────────────────────┘
```

---

### 12.1 返回箭头（Toolbar Navigation）

**触发：** 点击 Toolbar 左侧的返回图标

```
点击返回箭头
    │
    └─ binding.toolbar.setNavigationOnClickListener(v -> finish())
            └─ 调用 finish() → 销毁 SecondaryActivity，返回调用者
```

---

### 12.2 承载的 Fragment 内部交互

完全与在 `MainActivity` 内相同，参见各对应 Mirror Fragment 章节（第 8–11 章）。唯一区别是这里没有底部导航栏和抽屉，用户只能通过返回键/返回箭头退出。

---

## 13. 交互流程图：完整页面跳转拓扑

```
                    ┌───────────────────────────────────────────────┐
                    │               MainActivity                     │
                    │         (fragment_container)                  │
                    └──────┬──────────────────────────┬────────────┘
                           │                          │
              [底部导航栏]  │                [侧滑抽屉扩展功能]
                           │                          │
        ┌──────────────────┼───┐      ┌───────────────┼──────────────┐
        │                  │   │      │               │              │
        ▼          ▼       ▼   ▼      ▼               ▼              ▼
  Dashboard    Devices  Alerts  Send  Settings   Map Mirror   History Mirror
  Fragment    Fragment Fragment Frag  Fragment    Fragment      Fragment
                                                               │
        │                                       Rules Mirror  Health Mirror
        │                                        Fragment      Fragment
        │
        ▼（单设备模式时）
   singleDeviceScroll
   （同 Fragment 内的视图切换，非导航跳转）


  SecondaryActivity（独立 Activity，当前代码未直接调用，预留入口）
        ├── MODE_MAP     → MapMirrorFragment
        ├── MODE_HISTORY → HistoryMirrorFragment
        ├── MODE_RULES   → RulesMirrorFragment
        └── MODE_HEALTH  → HealthMirrorFragment
               ↑
    startActivity(SecondaryActivity.intent(...))
    （供外部 Intent / 通知深链接调用）
```

### 跳转行为总结

| 来源控件 | 目标 | 跳转类型 |
|---------|------|---------|
| 底部导航 Dashboard | DashboardFragment | Fragment replace（同 Activity） |
| 底部导航 Devices | DevicesFragment | Fragment replace |
| 底部导航 Alerts | AlertsFragment | Fragment replace |
| 底部导航 Send | SendFragment | Fragment replace |
| 底部导航 Settings | SettingsFragment | Fragment replace |
| 抽屉 Dashboard~Settings | 同底部导航 | Fragment replace（通过委托底部导航） |
| 抽屉 Map | MapMirrorFragment | Fragment replace（底部导航全清） |
| 抽屉 History | HistoryMirrorFragment | Fragment replace（底部导航全清） |
| 抽屉 Rules | RulesMirrorFragment | Fragment replace（底部导航全清） |
| 抽屉 Health | HealthMirrorFragment | Fragment replace（底部导航全清） |
| 保存设置（btnSave） | MainActivity 重建（重回 Dashboard）| `requireActivity().recreate()` |
| 切换语言（RadioButton）| Activity 重建（重回 Dashboard）| `LocaleHelper.applyAndSave()` 触发 |
| SecondaryActivity 返回箭头 | 调用方（通常是 MainActivity）| `finish()` |

> **没有任何一处** 使用 `startActivity(new Intent(ctx, MainActivity.class))`，所有页面跳转都在单 Activity 内通过 Fragment 事务完成，只有 `SecondaryActivity` 是真正的 Activity 跳转。

---

## 14. 边界情况与防护逻辑汇总

| 场景 | 防护代码位置 | 防护措施 |
|------|------------|---------|
| Fragment 被销毁后异步回调仍然触发 | 所有 Fragment 的回调入口 | `if (binding == null) return;` |
| 列表行点击时位置越界 | `RulesMirrorFragment.toggleRuleIfNeeded` / `deleteRuleIfNeeded` | `if (position < 0 \|\| position >= cachedRules.size()) return` |
| 地图未初始化时刷新 | `MapMirrorFragment.loadMarkers()` | `if (googleMap == null) return` |
| FAB 添加传感器时 ID 为空 | `DashboardFragment.showAddSensorDialog()` | `if (sid.isEmpty()) Toast...return` |
| 多传感器发送时无行 | `SendFragment` btnSendMulti | `if (multiRows.isEmpty()) Toast...return` |
| 原始 HEX 为空时发送 | `SendFragment` btnSendRaw | `if (hex.isEmpty()) Toast...return` |
| 设备 Spinner 选第 0 项（手动模式）时不覆盖 AID | `SendFragment.setupDeviceSpinner` | `if (pos > 0 && pos - 1 < devices.size())` 条件写入 |
| 自动刷新 Runnable 在 onDestroyView 后仍然执行 | `MapMirrorFragment.onDestroyView` | `autoRefreshHandler.removeCallbacks(autoRefreshRunnable)` |
| ItemTouchHelper 拖拽 HEADER 卡片 | `DashboardFragment` ItemTouchHelper.Callback | `getMovementFlags` 对 position==0 返回 0；`onMove` 对 f==0 或 t==0 返回 false |
| 设备状态字符串过期（未发送 "online" 状态更新）| `DevicesFragment.toRows()` / `HealthMirrorFragment.load()` | 双条件：`status == "online"` OR `lastSeenMs < 5min` |
| DashboardCardConfig 中 JSON 解析失败 | `DashboardCardConfig.load()` | `catch (JSONException)` → 返回 `defaultCards()` |
| DashboardCardConfig 中 HEADER 丢失 | `DashboardCardConfig.ensureHeader()` | 若无 HEADER 则强制插入到 index 0 |
| 保存设置时 TransportManager 抛异常 | `SettingsFragment.savePrefs()` | `try-catch` → 显示错误 Toast，不中止其余设置的保存 |
| 传感器值为整数时多余小数 | `UiFormatters.trimNumber()` | `Math.abs(value - Math.rint(value)) < 0.0001` 时输出整数字符串 |
| MiniTrendChartView 全相同值时 Y 轴压缩为 0 | `MiniTrendChartView.onDraw()` | `if (Math.abs(max - min) < 0.0001f) { max += 1f; min -= 1f; }` |

