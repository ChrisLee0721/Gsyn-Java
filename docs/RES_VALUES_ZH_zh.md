# Gsyn-Java `res/values-zh` 目录开发者文档

> 路径：`app/src/main/res/values-zh/`  
> 本目录是中文（简体）语言的资源覆盖目录，与 `res/values/` 形成对应的多语言翻译层。  
> 当系统语言为中文或用户通过 `LocaleHelper` 切换到中文时，Android 资源框架自动优先使用本目录中的资源。

---

## 目录

1. [文件清单](#1-文件清单)
2. [strings.xml — 中文字符串翻译](#2-stringsxml--中文字符串翻译)
3. [翻译覆盖机制](#3-翻译覆盖机制)
4. [主要翻译条目分类](#4-主要翻译条目分类)
5. [扩展指引（维护翻译）](#5-扩展指引维护翻译)

---

## 1. 文件清单

| 文件名 | 说明 |
|--------|------|
| `strings.xml` | 全部 UI 文本的中文（简体）翻译，Key 与 `values/strings.xml` 完全一致 |

> 本目录**不包含** `colors.xml` 和 `themes.xml`，颜色和样式不参与本地化。

---

## 2. strings.xml — 中文字符串翻译

**对应英文文件**：`res/values/strings.xml`  
本文件覆盖英文版中的所有字符串 key，提供完整的中文（简体）翻译。

### 关键翻译对照（部分）

| Key | 英文 | 中文 |
|-----|------|------|
| `app_name` | Gsyn Java | Gsyn Java |
| `nav_dashboard` | Dashboard | 仪表盘 |
| `nav_devices` | Devices | 设备 |
| `nav_alerts` | Alerts | 告警 |
| `nav_send` | Send | 发包 |
| `nav_settings` | Settings | 设置 |
| `title_history` | History | 历史 |
| `title_map` | Map | 地图 |
| `title_rules` | Rules | 规则 |
| `title_health` | System Health | 系统健康 |
| `settings_section_transport` | Transport | 传输 |
| `settings_section_appearance` | Appearance | 外观 |
| `settings_section_language` | Language | 语言 |
| `settings_lang_system` | Follow System | 跟随系统 |
| `settings_lang_en` | English | 英文 |
| `settings_lang_zh` | 中文 | 中文 |
| `settings_save` | Save &amp; Apply | 保存并应用 |
| `transport_connected` | Connected | 已连接 |
| `transport_disconnected` | Disconnected | 未连接 |
| `alerts_filter_all` | All | 全部 |
| `dashboard_label_total_devices` | Total Devices | 设备总数 |
| `dashboard_label_online_rate` | Online Rate | 在线率 |
| `dashboard_label_active_alerts` | Active Alerts | 活跃告警 |
| `dashboard_label_throughput` | Throughput | 吞吐量 |
| `dashboard_single_mode_title` | Single Device Mode | 单设备模式 |
| `drawer_group_extensions` | Extensions | 扩展功能 |
| `drawer_item_map` | Map · Device Map | 地图 · 设备地图 |
| `drawer_item_history` | History · Data Log | 历史 · 数据日志 |
| `drawer_item_rules` | Rules · Rule Engine | 规则 · 规则引擎 |
| `drawer_item_health` | Health · System Health | 健康 · 系统健康 |
| `send_btn_single` | Send DATA_FULL | 发送 DATA_FULL |
| `mirror_rules_action` | New Rule | 新建规则 |
| `mirror_history_action` | Export CSV | 导出 CSV |
| `mirror_health_action` | Prune 7-day history | 清理 7 天历史 |
| `dialog_cancel` | Cancel | 取消 |
| `dialog_create` | Create | 创建 |

### 格式化字符串（中文版）

中文格式化字符串与英文版**占位符结构完全一致**，仅翻译文字部分：

| Key | 中文格式 |
|-----|---------|
| `dashboard_subtitle_format` | `在线 %1$d / %2$d · 未读告警 %3$d · 规则 %4$d` |
| `devices_summary_format` | `共 %1$d 台设备 · 在线 %2$d · 显示 %3$d` |
| `alerts_summary_format` | `严重 %1$d · 警告 %2$d · 信息 %3$d · 显示 %4$d` |
| `send_route_format` | `路由: AID %1$s · TID %2$s · SEQ %3$s · %4$s:%5$s` |
| `map_snippet_format` | `AID %1$d  ·  %2$s  ·  %3$s\n最后在线: %4$s` |
| `settings_transport_status_format` | `UDP: %1$s · MQTT: %2$s · 地图瓦片: %3$s` |
| `mirror_health_summary_format` | `%1$d 台设备 · %2$d msg/s · 累计 %3$d` |

---

## 3. 翻译覆盖机制

### Android 资源框架查找顺序

```
用户/系统语言 = 中文 (zh)
    │
    ▼ Android 按语言限定符从精确到宽泛查找
    
    1. res/values-zh-rCN/strings.xml   (简体中文+中国大陆，若存在)
    2. res/values-zh/strings.xml       ← 当前实现，匹配所有中文变体
    3. res/values/strings.xml          (英文默认，fallback)
```

### 与 LocaleHelper 的关系

```
用户在设置页点击"中文"按钮
    → LocaleHelper.applyAndSave("zh")
    → AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh"))
    → AppCompat 重建当前 Activity
    → Android 资源系统自动使用 values-zh/ 目录中的字符串
```

**无需手动**调用 `Resources.updateConfiguration()`，AppCompat 1.6+ 自动处理全部细节。

---

## 4. 主要翻译条目分类

### 导航与页面标题（`nav_*`、`title_*`、`shell_toolbar_subtitle_*`）

提供完整的中文页面名称和 Toolbar 副标题，确保用户在中文模式下看到的所有导航文字都是中文。

### 设置页（`settings_*`）

连接配置提示文字（如输入框 hint）、外观选项标签、语言选项标签、保存/应用按钮，以及各种 Toast 消息。

### 仪表盘（`dashboard_*`）

KPI 卡片标签、图表标题、单设备模式提示文字、自定义传感器卡片相关文字、空状态提示。

### 发包工具（`send_*`）

所有标签、Tab 标题（Control / Data / Raw）、按钮文字（"发送 DATA_FULL"、"发送多帧"等）、Toast 消息（"发送成功"/"发送失败"）。

### 扩展页镜像（`mirror_*`）

地图、历史、规则、健康四个扩展页的所有文本，包括摘要格式、空状态提示、操作按钮标签。

---

## 5. 扩展指引（维护翻译）

### 新增字符串的翻译流程

1. 在 `res/values/strings.xml` 添加英文条目
2. **同时**在 `res/values-zh/strings.xml` 添加对应中文翻译
3. 如暂无翻译，可临时保留英文（Android 会 fallback 到默认值文件）

### 检查遗漏翻译

Android Lint 规则 `MissingTranslation` 会在编译时警告未翻译的 key：

```
./gradlew lint
```

查看 `build/reports/lint-results-debug.html` 中的翻译缺失警告。

### 新增语言支持

1. 创建 `res/values-{语言代码}/strings.xml`（如 `values-ja/` 添加日文）
2. 在 `res/xml/locale_config.xml` 中添加 `<locale android:name="{语言代码}" />`
3. 在 `LocaleHelper` 中添加对应语言常量
4. 在 `SettingsFragment` UI 中添加语言切换按钮

### 翻译文件格式规范

- 保持 key 顺序与英文文件一致（便于 diff 对比）
- XML 特殊字符使用转义：`&amp;`（&）、`&lt;`（<）、`&gt;`（>）、`\'`（单引号，在非引号字符串中）
- 格式化参数（`%1$s`、`%2$d`）位置可根据中文语序调整，但**编号不可省略**

