# Gsyn-Java `res/values` 目录开发者文档

> 路径：`app/src/main/res/values/`  
> 本目录包含应用的所有值资源：字符串（英文默认）、颜色常量、主题/样式定义，共 3 个 XML 文件。

---

## 目录

1. [文件清单](#1-文件清单)
2. [strings.xml — 英文字符串资源](#2-stringsxml--英文字符串资源)
3. [colors.xml — 颜色资源](#3-colorsxml--颜色资源)
4. [themes.xml — 主题与样式](#4-themesxml--主题与样式)
5. [多语言机制](#5-多语言机制)
6. [扩展指引](#6-扩展指引)

---

## 1. 文件清单

| 文件名 | 说明 | 行数 |
|--------|------|------|
| `strings.xml` | 全部 UI 文本（英文，默认语言） | 299 行 |
| `colors.xml` | 颜色常量（核心色、状态色、主题预设色、背景预设色） | 182 行 |
| `themes.xml` | 基础主题 + 8 个强调色 ThemeOverlay + 12 个背景 ThemeOverlay | 249 行 |

---

## 2. strings.xml — 英文字符串资源

**对应翻译文件**：`res/values-zh/strings.xml`（中文）

### 字符串分组概览

| 分组 | Key 前缀 | 说明 |
|------|---------|------|
| 应用名 | `app_name` | 应用显示名 |
| 底部导航 | `nav_` | 页面标签 + 抽屉开关描述 |
| 页面标题 | `title_` | 扩展页标题（History / Map / Rules / Health） |
| Toolbar 副标题 | `shell_toolbar_subtitle_` | 每个页面 Toolbar 下方描述文字 |
| 设置页 | `settings_` | 各设置项标签、提示文字、Toast |
| 传输状态 | `transport_` | Enabled / Disabled / Connected / Disconnected |
| 格式化占位 | `fmt_` | 通用占位文本（N/A、Never、No data） |
| 告警页 | `alerts_` | 告警列表标签、过滤器、Toast |
| 仪表盘 | `dashboard_` | KPI 标签、图表标题、单设备模式文字 |
| 设备页 | `devices_` | 列表标签、搜索提示、设备详情字段 |
| 地图 | `map_` | 地图标题、图层选项、状态 |
| 规则 | `rules_` | 规则页标签 |
| 发包页 | `send_` | 发包工具所有标签、提示、Toast |
| 抽屉扩展 | `drawer_` | 扩展功能组标题和条目 |
| 地图镜像 | `mirror_map_` | MapMirrorFragment 文本 |
| 历史镜像 | `mirror_history_` | HistoryMirrorFragment 文本 |
| 规则镜像 | `mirror_rules_` | RulesMirrorFragment 文本 |
| 健康镜像 | `mirror_health_` | HealthMirrorFragment 文本 |
| 对话框 | `dialog_` | 通用 Cancel / Create 按钮 |

### 重要格式化字符串

| Key | 参数说明 |
|-----|---------|
| `dashboard_subtitle_format` | %1$d=在线数, %2$d=总数, %3$d=未读告警, %4$d=规则数 |
| `devices_summary_format` | %1$d=总设备, %2$d=在线, %3$d=显示中 |
| `alerts_summary_format` | %1$d=严重, %2$d=警告, %3$d=信息, %4$d=显示中 |
| `send_route_format` | %1$s=AID, %2$s=TID, %3$s=SEQ, %4$s=IP, %5$s=Port |
| `map_snippet_format` | %1$d=AID, %2$s=名称, %3$s=状态, %4$s=最后在线 |
| `settings_transport_status_format` | %1$s=UDP 状态, %2$s=MQTT 状态, %3$s=Tile 状态 |

### 用法示例

```java
// 简单字符串
String label = getString(R.string.nav_dashboard);

// 带参数的格式化字符串
String subtitle = getString(R.string.dashboard_subtitle_format,
        onlineCount, totalCount, unackedAlerts, ruleCount);
```

---

## 3. colors.xml — 颜色资源

**XML 引用**：`@color/gsyn_primary`  
**Java 引用**：`ContextCompat.getColor(ctx, R.color.gsyn_primary)`

### 3.1 核心应用颜色（`gsyn_*`）

| 名称 | 十六进制 | 说明 |
|------|---------|------|
| `gsyn_primary` | `#1A73E8` | 主品牌色（深蓝） |
| `gsyn_on_primary` | `#FFFFFF` | 主色上的文字 |
| `gsyn_secondary` | `#34A853` | 辅助色（绿） |
| `gsyn_background` | `#0F1923` | 页面背景（Deep Navy） |
| `gsyn_surface` | `#1B2838` | 面板/卡片表面 |
| `gsyn_card` | `#213040` | 卡片背景 |
| `gsyn_surface_variant` | `#1E2833` | 变体表面 |
| `gsyn_surface_elevated` | `#233140` | 较高层次表面 |
| `gsyn_on_surface` | `#E8EAED` | 主要文本（亮白） |
| `gsyn_on_surface_muted` | `#9AA0A6` | 次要文本（灰） |
| `gsyn_outline` | `#314050` | 边框/轮廓线 |

### 3.2 状态颜色

| 名称 | 十六进制 | 含义 |
|------|---------|------|
| `gsyn_online` | `#34A853` | 在线（绿） |
| `gsyn_offline` | `#5F6368` | 离线（灰） |
| `gsyn_warning` | `#FBBC04` | 警告（黄） |
| `gsyn_danger` | `#EA4335` | 危险（红） |
| `gsyn_info` | `#4285F4` | 信息（蓝） |

### 3.3 阈值区颜色

`gsyn_zone_normal`（绿）、`gsyn_zone_warning`（黄）、`gsyn_zone_danger`（红）

### 3.4 图表调色板

`gsyn_chart_1` ~ `gsyn_chart_8`（蓝/绿/黄/红/紫/青/橙/蓝灰）

### 3.5 强调色预设（`theme_*`，8 套）

每套 4 个颜色值（primary / on / container / on_container）：

| 预设 | primary 色 |
|------|-----------|
| `theme_deep_blue` | `#1A73E8` |
| `theme_teal` | `#00897B` |
| `theme_purple` | `#7B1FA2` |
| `theme_amber` | `#FF8F00` |
| `theme_red` | `#D32F2F` |
| `theme_cyan` | `#0097A7` |
| `theme_green` | `#2E7D32` |
| `theme_pink` | `#C2185B` |

### 3.6 背景预设（`bg_*`，12 套）

**深色 6 套**（每套 6 个颜色）：`bg_deep_navy`、`bg_dark_slate`、`bg_charcoal`、`bg_true_black`、`bg_forest_dark`、`bg_warm_dark`

**浅色 6 套**（每套 9 个颜色，额外含 text/text2/hint）：`bg_snow_white`、`bg_cloud_grey`、`bg_paper_cream`、`bg_mint_light`、`bg_lavender_light`、`bg_sky_blue`

---

## 4. themes.xml — 主题与样式

### 4.1 基础主题：`Theme.GsynJava`

父主题：`Theme.Material3.Dark.NoActionBar`

| 主题属性 | 映射颜色资源 |
|---------|------------|
| `colorPrimary` | `@color/gsyn_primary` |
| `colorSecondary` | `@color/gsyn_secondary` |
| `android:colorBackground` | `@color/gsyn_background` |
| `colorSurface` | `@color/gsyn_surface` |
| `colorOnSurface` | `@color/gsyn_on_surface` |
| `colorSurfaceVariant` | `@color/gsyn_surface_variant` |
| `colorOutline` | `@color/gsyn_outline` |

> `statusBarColor` / `navigationBarColor` 在运行时由 `AppThemeConfig.applyBgToWindow()` 动态覆盖。

### 4.2 强调色 ThemeOverlay（8 个）

命名：`ThemeOverlay.GsynJava.Accent.{名称}`  
每个仅覆盖 `colorPrimary`、`colorOnPrimary`、`colorPrimaryContainer`、`colorOnPrimaryContainer`。

**应用方式**（在 `super.onCreate()` 之前）：

```java
getTheme().applyStyle(R.style.ThemeOverlay_GsynJava_Accent_Teal, true);
```

### 4.3 背景 ThemeOverlay（12 个）

命名：`ThemeOverlay.GsynJava.Bg.{名称}`

**深色覆盖层**：覆盖背景/表面/文字/状态栏色 + `windowLightStatusBar=false`  
**浅色覆盖层**：额外覆盖文字色 + `windowLightStatusBar=true`（系统栏图标变深色）

> **所有颜色项必须使用 `@color/` 引用，不可内联十六进制**，原因：Material3 内部将颜色属性解析为资源 ID，内联值会导致 `Resources$NotFoundException`。

---

## 5. 多语言机制

| 目录 | 语言 | 备注 |
|------|------|------|
| `values/strings.xml` | 英文（默认） | 应包含全部 key |
| `values-zh/strings.xml` | 中文（简体） | 完整翻译覆盖 |

支持的语言由 `res/xml/locale_config.xml` 声明（`en`、`zh`）。  
切换语言通过 `LocaleHelper.applyAndSave(lang)` 调用，AppCompat 自动持久化并重建 Activity。

---

## 6. 扩展指引

### 新增字符串

1. `values/strings.xml` 添加英文
2. `values-zh/strings.xml` 添加中文翻译
3. 格式化参数使用 `%1$s`、`%2$d` 等索引化形式

### 新增强调色预设

1. `colors.xml` 添加 4 个颜色值（primary/on/container/on_container）
2. `themes.xml` 添加 `ThemeOverlay.GsynJava.Accent.NewName`
3. `AppThemeConfig.ThemePreset` 枚举添加成员
4. `AppThemeConfig.getAccentOverlayRes()` 添加 case

### 新增背景预设

1. `colors.xml` 按模板添加颜色值（深色 6 个，浅色 9 个）
2. `themes.xml` 添加对应 `ThemeOverlay.GsynJava.Bg.NewName`
3. `AppThemeConfig.BgPreset` 枚举添加成员
4. `AppThemeConfig.getBgOverlayRes()` 添加 case

