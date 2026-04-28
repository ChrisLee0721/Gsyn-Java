# Gsyn-Java `res/drawable` 目录开发者文档

> 路径：`app/src/main/res/drawable/`  
> 本目录存放所有矢量/XML 可绘制资源。当前共 3 个 XML Shape Drawable，均为 UI 面板/状态指示的通用背景。

---

## 目录

1. [资源清单](#1-资源清单)
2. [bg_shell_panel.xml — 主面板背景](#2-bg_shell_panelxml--主面板背景)
3. [bg_shell_panel_compact.xml — 紧凑面板背景](#3-bg_shell_panel_compactxml--紧凑面板背景)
4. [bg_status_pill.xml — 状态胶囊背景](#4-bg_status_pillxml--状态胶囊背景)
5. [设计规范与主题兼容性](#5-设计规范与主题兼容性)
6. [扩展指引](#6-扩展指引)

---

## 1. 资源清单

| 文件名 | 用途 | 圆角半径 | 边框 |
|--------|------|---------|------|
| `bg_shell_panel.xml` | 各 Fragment 内卡片/面板通用背景 | 24 dp | 1 dp `colorOutline` |
| `bg_shell_panel_compact.xml` | 紧凑布局（如列表项）面板背景 | 12 dp | 1 dp `colorOutline` |
| `bg_status_pill.xml` | 设备状态/传感器状态胶囊标签背景 | 全圆（50%） | 无 |

---

## 2. bg_shell_panel.xml — 主面板背景

**引用方式**：`@drawable/bg_shell_panel`

```xml
<shape android:shape="rectangle">
    <solid android:color="?attr/colorSurface" />
    <corners android:radius="24dp" />
    <stroke
        android:width="1dp"
        android:color="?attr/colorOutline" />
</shape>
```

### 特性说明

| 属性 | 值 | 说明 |
|------|----|------|
| 填充色 | `?attr/colorSurface` | 动态引用主题 surface 色 → 随 `BgPreset` ThemeOverlay 自动变化 |
| 圆角 | 24 dp | Material3 大卡片标准圆角 |
| 边框宽度 | 1 dp | 微妙轮廓线 |
| 边框色 | `?attr/colorOutline` | 动态引用主题 outline 色 |

### 使用场景

- `DashboardFragment` 各仪表盘卡片容器
- `SendFragment` 发包区域面板
- `SettingsFragment` 设置项分组面板

---

## 3. bg_shell_panel_compact.xml — 紧凑面板背景

**引用方式**：`@drawable/bg_shell_panel_compact`

与 `bg_shell_panel.xml` 结构相同，差异仅在圆角半径：

| 属性 | bg_shell_panel | bg_shell_panel_compact |
|------|----|------|
| 圆角 | 24 dp | 12 dp |
| 填充色 | `?attr/colorSurface` | `?attr/colorSurface` |
| 边框 | 1 dp `colorOutline` | 1 dp `colorOutline` |

### 使用场景

- `item_card_row.xml` 等列表 item 容器
- 嵌套在主面板内的次级区块
- 空间较紧凑的对话框/底部面板

---

## 4. bg_status_pill.xml — 状态胶囊背景

**引用方式**：`@drawable/bg_status_pill`

```xml
<shape android:shape="rectangle">
    <solid android:color="?attr/colorSurface" />   <!-- 或在代码中动态设置 tint -->
    <corners android:radius="999dp" />              <!-- 全圆效果 -->
</shape>
```

（实际文件以 999 dp 或 50% 实现全圆，无边框）

### 使用场景

- `DevicesFragment` 中 `status_badge`（在线/离线状态标签）
- `DashboardFragment` KPI 行中状态指示胶囊
- `AlertsFragment` 告警等级标签徽章

### 颜色设置方式

状态胶囊的背景色由代码动态设置（而非 XML），以适应不同状态：

```java
// 在 Adapter 中动态着色
statusBadge.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_status_pill));
statusBadge.getBackground().setTint(
    "online".equals(device.status) ? AppColors.ONLINE : AppColors.OFFLINE
);
```

---

## 5. 设计规范与主题兼容性

### 主题颜色属性（?attr）的必要性

所有 Drawable 均使用 `?attr/colorSurface` 和 `?attr/colorOutline`，而**不硬编码颜色值**。这是为了确保：

- 切换 `BgPreset`（如从 Deep Navy → Snow White）时，面板背景自动适配
- 深/浅色模式下轮廓线颜色合理
- 无需为每个主题变体维护重复的 Drawable 文件

> **在代码中手动设置背景色时也应遵循此原则**，使用 `MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface, fallback)` 而非硬编码。

### 圆角设计规范

| 场景 | 圆角值 | Drawable |
|------|--------|----------|
| 主卡片 / 全页面面板 | 24 dp | `bg_shell_panel` |
| 列表 item / 次级面板 | 12 dp | `bg_shell_panel_compact` |
| 状态徽章 / 胶囊标签 | 全圆 | `bg_status_pill` |

---

## 6. 扩展指引

### 新增 Drawable

1. 在 `res/drawable/` 中创建新的 XML Shape / Vector XML
2. 优先使用 `?attr/colorXxx` 引用主题颜色，避免硬编码
3. 如为状态相关背景（有多种颜色），使用 `<selector>` 或在代码中设置 `setTint()`

### 添加带阴影的面板

Material3 推荐使用 `MaterialCardView` 的 `cardElevation` 属性实现阴影，而非 XML Drawable 的 `layer-list` 模拟。

### 与 Vector Asset 配合

如需添加图标，推荐通过 **Vector Asset Studio**（Android Studio）导入 Material Icons，生成为 `ic_xxx.xml` 矢量文件，统一存放于本目录。

