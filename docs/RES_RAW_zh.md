# Gsyn-Java `res/raw` 目录开发者文档

> 路径：`app/src/main/res/raw/`  
> 本目录存放原始二进制或文本资源文件，不经过 `aapt` 处理，通过 `R.raw.xxx` 引用，可用 `context.getResources().openRawResource(R.raw.xxx)` 直接读取输入流。

---

## 目录

1. [文件清单](#1-文件清单)
2. [map_style_dark.json — 地图深色样式](#2-map_style_darkjson--地图深色样式)
3. [扩展指引](#3-扩展指引)

---

## 1. 文件清单

| 文件名 | 格式 | 引用方式 | 说明 |
|--------|------|---------|------|
| `map_style_dark.json` | JSON | `R.raw.map_style_dark` | Google Maps JavaScript API 深色样式配置 |

---

## 2. map_style_dark.json — 地图深色样式

**用途**：为 `MapMirrorFragment` 中嵌入的 Google Maps JavaScript API 提供统一的深色主题样式，使地图外观与应用的深色背景 (`BgPreset.DEEP_NAVY` 等) 协调统一。

### 加载方式

`MapMirrorFragment` 将此 JSON 文件内容注入到 WebView 的 JavaScript 中，通过 Maps API 的 `map.setOptions({styles: [...]})` 应用样式：

```java
// MapMirrorFragment 中
InputStream is = getResources().openRawResource(R.raw.map_style_dark);
String styleJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
webView.evaluateJavascript("setMapStyle(" + styleJson + ");", null);
```

### 样式内容（概要）

该 JSON 文件遵循 Google Maps Styling Wizard 格式，主要设置：

| 元素 | 样式规则 | 效果 |
|------|---------|------|
| 地图背景 | `geometry.fill color: #0F1923` | 与应用背景色 Deep Navy 匹配 |
| 道路 | `color: #1B2838`（表面色） | 低对比度深色道路 |
| 道路描边 | `color: #314050`（outline 色） | 细微边界 |
| 标注文字 | `color: #9AA0A6`（次要文本色） | 低亮度地名文字 |
| 文字描边 | `color: #0F1923` | 文字轮廓与背景融合 |
| 水域 | `color: #1B2838` | 深色水面 |
| 公园/绿地 | `color: #0D1A0D` | Forest Dark 绿 |
| 兴趣点 | `visibility: off` | 隐藏 POI 图标减少视觉干扰 |

### 与应用主题的对应关系

| 地图颜色 | 应用颜色常量 | 十六进制 |
|---------|------------|---------|
| 背景 | `AppColors.BACKGROUND` | `#0F1923` |
| 表面/道路 | `AppColors.SURFACE` | `#1B2838` |
| 轮廓 | `gsyn_outline` | `#314050` |
| 次要文本 | `AppColors.TEXT_SECONDARY` | `#9AA0A6` |

---

## 3. 扩展指引

### 新增地图样式（浅色主题）

当用户切换到浅色 `BgPreset`（如 Snow White）时，可提供对应的浅色地图样式：

1. 在 `res/raw/` 中新增 `map_style_light.json`（使用 Google Maps Styling Wizard 生成）
2. 在 `MapMirrorFragment` 中根据当前 `BgPreset.isLight` 选择加载哪个样式文件：

```java
boolean isLight = AppThemeConfig.loadBgPreset(requireContext()).isLight;
int styleRes = isLight ? R.raw.map_style_light : R.raw.map_style_dark;
InputStream is = getResources().openRawResource(styleRes);
```

### 新增其他原始资源

`res/raw/` 适合存放：
- JSON 配置文件（不需要 XML 解析）
- 二进制协议规范文件
- 静态 GeoJSON 数据
- 音频文件（告警提示音等）

**不适合**存放：
- 需要国际化的字符串（应使用 `res/values/strings.xml`）
- 图片资源（应使用 `res/drawable/` 或 `res/mipmap/`）
- XML 布局（应使用 `res/layout/`）

