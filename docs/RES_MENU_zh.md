# Gsyn-Java `res/menu` 目录开发者文档

> 路径：`app/src/main/res/menu/`  
> 本目录包含应用所有菜单定义文件，共 2 个 XML，分别对应底部导航栏和侧滑导航抽屉。

---

## 目录

1. [文件清单](#1-文件清单)
2. [bottom_nav.xml — 底部导航菜单](#2-bottom_navxml--底部导航菜单)
3. [drawer_nav.xml — 侧滑抽屉菜单](#3-drawer_navxml--侧滑抽屉菜单)
4. [菜单 ID 与 Fragment 映射表](#4-菜单-id-与-fragment-映射表)
5. [导航逻辑说明](#5-导航逻辑说明)
6. [扩展指引](#6-扩展指引)

---

## 1. 文件清单

| 文件名 | 使用控件 | 说明 |
|--------|---------|------|
| `bottom_nav.xml` | `BottomNavigationView`（`activity_main.xml`） | 底部 5 tab 一级导航 |
| `drawer_nav.xml` | `NavigationView`（`activity_main.xml`） | 侧滑抽屉，含主页面组 + 扩展功能组 |

---

## 2. bottom_nav.xml — 底部导航菜单

**引用位置**：`activity_main.xml` → `BottomNavigationView app:menu="@menu/bottom_nav"`

### 菜单项定义

| ID | 图标 | 标题字符串 | 对应 Fragment |
|----|------|-----------|--------------|
| `@+id/nav_dashboard` | `ic_menu_view` | `@string/nav_dashboard` | `DashboardFragment` |
| `@+id/nav_devices` | `ic_menu_agenda` | `@string/nav_devices` | `DevicesFragment` |
| `@+id/nav_alerts` | `ic_dialog_alert` | `@string/nav_alerts` | `AlertsFragment` |
| `@+id/nav_send` | `ic_menu_send` | `@string/nav_send` | `SendFragment` |
| `@+id/nav_settings` | `ic_menu_preferences` | `@string/nav_settings` | `SettingsFragment` |

### 显示特性

- `labelVisibilityMode="labeled"`：始终显示文字标签（不隐藏）
- 当前选中项由 `MainActivity` 维护，默认选中 `nav_dashboard`
- 使用 Material3 `BottomNavigationView`，选中时以 `colorPrimary` 着色

---

## 3. drawer_nav.xml — 侧滑抽屉菜单

**引用位置**：`activity_main.xml` → `NavigationView app:menu="@menu/drawer_nav"`

### 结构

```
drawer_nav.xml
│
├── <group checkableBehavior="single">      ← 主页面组（与底部导航镜像）
│   ├── nav_main_dashboard  "Dashboard"
│   ├── nav_main_devices    "Devices"
│   ├── nav_main_alerts     "Alerts"
│   ├── nav_main_send       "Send"
│   └── nav_main_settings   "Settings"
│
└── <item title="@string/drawer_group_extensions">  ← 扩展功能子菜单
    └── <menu>
        ├── nav_drawer_map      "@string/drawer_item_map"
        ├── nav_drawer_history  "@string/drawer_item_history"
        ├── nav_drawer_rules    "@string/drawer_item_rules"
        └── nav_drawer_health   "@string/drawer_item_health"
```

### 主页面组 ID

| ID | 标题 | 对应 Fragment | 镜像底部导航 ID |
|----|------|--------------|---------------|
| `@+id/nav_main_dashboard` | Dashboard | `DashboardFragment` | `nav_dashboard` |
| `@+id/nav_main_devices` | Devices | `DevicesFragment` | `nav_devices` |
| `@+id/nav_main_alerts` | Alerts | `AlertsFragment` | `nav_alerts` |
| `@+id/nav_main_send` | Send | `SendFragment` | `nav_send` |
| `@+id/nav_main_settings` | Settings | `SettingsFragment` | `nav_settings` |

**与底部导航的同步逻辑**（`MainActivity` 中实现）：
- 抽屉菜单中点击主页面项 → 更新 `bottom_nav.selectedItemId` → 触发 `BottomNavigationView.OnItemSelectedListener` → 加载对应 Fragment → 关闭抽屉

### 扩展功能组 ID

| ID | 标题字符串 Key | 对应 Fragment | 说明 |
|----|--------------|--------------|------|
| `@+id/nav_drawer_map` | `drawer_item_map` | `MapMirrorFragment` | 设备地图视图 |
| `@+id/nav_drawer_history` | `drawer_item_history` | `HistoryMirrorFragment` | 传感器历史数据图表 |
| `@+id/nav_drawer_rules` | `drawer_item_rules` | `RulesMirrorFragment` | 规则管理 |
| `@+id/nav_drawer_health` | `drawer_item_health` | `HealthMirrorFragment` | 系统健康状态 |

**扩展功能的导航行为**：
- 点击扩展项 → 加载对应 Fragment 到 `fragment_container`
- 底部导航栏**全部取消选中**（无对应 tab）
- 关闭抽屉

---

## 4. 菜单 ID 与 Fragment 映射表

| 菜单 ID | 来源菜单 | 目标 Fragment | 类型 |
|---------|---------|--------------|------|
| `nav_dashboard` | bottom_nav | `DashboardFragment` | 一级 |
| `nav_devices` | bottom_nav | `DevicesFragment` | 一级 |
| `nav_alerts` | bottom_nav | `AlertsFragment` | 一级 |
| `nav_send` | bottom_nav | `SendFragment` | 一级 |
| `nav_settings` | bottom_nav | `SettingsFragment` | 一级 |
| `nav_main_dashboard` | drawer_nav | `DashboardFragment` | 一级（抽屉镜像） |
| `nav_main_devices` | drawer_nav | `DevicesFragment` | 一级（抽屉镜像） |
| `nav_main_alerts` | drawer_nav | `AlertsFragment` | 一级（抽屉镜像） |
| `nav_main_send` | drawer_nav | `SendFragment` | 一级（抽屉镜像） |
| `nav_main_settings` | drawer_nav | `SettingsFragment` | 一级（抽屉镜像） |
| `nav_drawer_map` | drawer_nav | `MapMirrorFragment` | 扩展页 |
| `nav_drawer_history` | drawer_nav | `HistoryMirrorFragment` | 扩展页 |
| `nav_drawer_rules` | drawer_nav | `RulesMirrorFragment` | 扩展页 |
| `nav_drawer_health` | drawer_nav | `HealthMirrorFragment` | 扩展页 |

---

## 5. 导航逻辑说明

### 双导航同步

```
用户点击抽屉中的主页面项
    → 抽屉 NavigationView.setCheckedItem(主页面 ID)
    → bottom_nav.setSelectedItemId(对应底部 tab ID)
    → OnBottomNavItemSelected 回调触发
    → 替换 fragment_container 中的 Fragment
    → 关闭抽屉 (drawer_layout.closeDrawer(GravityCompat.START))
```

### 扩展页面导航

```
用户点击抽屉中的扩展项（如 Map）
    → bottom_nav.menu 所有 item setChecked(false)（全部取消选中）
    → 替换 fragment_container 为对应 Fragment
    → 关闭抽屉
```

### Toolbar 标题联动

```
每次 Fragment 替换时，MainActivity 更新 toolbar.title
    → 对应字符串来自 @string/nav_dashboard / nav_devices 等
```

---

## 6. 扩展指引

### 新增底部导航 Tab

1. 在 `bottom_nav.xml` 中添加 `<item>` 节点（BottomNavigationView 最多支持 5 个 item）
2. 在 `drawer_nav.xml` 主页面组中添加对应镜像项
3. 在 `MainActivity` 的 `OnItemSelectedListener` 中添加对应 `case`
4. 在 `strings.xml` 中添加标题字符串

### 新增侧滑抽屉扩展项

1. 在 `drawer_nav.xml` 的扩展子菜单中添加 `<item>`
2. 在 `strings.xml` 中添加 `drawer_item_xxx` 字符串
3. 在 `MainActivity` 的抽屉菜单监听中添加对应 `case`，加载新 Fragment

### 图标替换

当前使用 `@android:drawable/ic_*` 系统图标；建议替换为 Material Icons Vector XML（添加到 `res/drawable/`），效果更一致。

