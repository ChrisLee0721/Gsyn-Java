# Gsyn-Java `core` 包开发者文档

> 包路径：`com.opensynaptic.gsynjava.core`  
> 子包：`com.opensynaptic.gsynjava.core.protocol`  
> 本包是整个应用的**基础能力层**，不依赖 Android UI 框架（protocol 子包纯 Java），可与单元测试直接使用。

---

## 目录

1. [包结构总览](#1-包结构总览)
2. [AppColors — 颜色常量](#2-appcolors--颜色常量)
3. [AppThresholds — 传感器告警阈值常量](#3-appthresholds--传感器告警阈值常量)
4. [AppThemeConfig — 主题与背景配置管理](#4-appthemeconfig--主题与背景配置管理)
5. [LocaleHelper — 语言切换工具](#5-localehelper--语言切换工具)
6. [protocol 子包总览](#6-protocol-子包总览)
7. [ProtocolConstants — 协议常量](#7-protocolconstants--协议常量)
8. [OsCmd — 命令字节码](#8-oscmd--命令字节码)
9. [OsCrc — CRC 校验计算](#9-oscrc--crc-校验计算)
10. [Base62Codec — Base62 编解码器](#10-base62codec--base62-编解码器)
11. [PacketBuilder — 数据包构建器](#11-packetbuilder--数据包构建器)
12. [PacketDecoder — 数据包头解析器](#12-packetdecoder--数据包头解析器)
13. [BodyParser — 包体文本解析器](#13-bodyparser--包体文本解析器)
14. [DiffEngine — DIFF/HEART 模板引擎](#14-diffengine--diffheart-模板引擎)
15. [GeohashDecoder — 地理坐标解码器](#15-geohash decoder--地理坐标解码器)
16. [依赖关系图](#16-依赖关系图)
17. [扩展指引](#17-扩展指引)

---

## 1. 包结构总览

```
core/
├── AppColors.java          // ARGB 颜色常量（Flutter AppColors 镜像）
├── AppThresholds.java      // 传感器告警阈值常量
├── AppThemeConfig.java     // 主题/背景预设枚举 + SharedPreferences 持久化
├── LocaleHelper.java       // AppCompat 语言切换工具
└── protocol/
    ├── ProtocolConstants.java  // OpenSynaptic 协议常量（单位、传感器 ID、状态码）
    ├── OsCmd.java              // 命令字节码常量与辅助方法
    ├── OsCrc.java              // CRC-8 / CRC-16 校验实现
    ├── Base62Codec.java        // Base62 + Base64URL 混合编解码
    ├── PacketBuilder.java      // 二进制数据包构建工具
    ├── PacketDecoder.java      // 数据包头/CRC 解析
    ├── BodyParser.java         // 包体文本段解析（header|sensor segments）
    ├── DiffEngine.java         // DATA_FULL/DIFF/HEART 差分模板引擎
    └── GeohashDecoder.java     // Geohash → WGS-84 经纬度解码
```

---

## 2. AppColors — 颜色常量

**文件**：`core/AppColors.java`  
**性质**：纯常量类，`final` + 私有构造，不可实例化。  
**用途**：在 Java 代码中以编程方式使用颜色（如给 View 动态着色时），避免每次都调用 `ContextCompat.getColor()`。XML 中仍应使用 `@color/gsyn_*` 资源引用。

### 常量分组

| 分组 | 常量名 | 颜色 (ARGB) | 说明 |
|------|--------|------------|------|
| **核心色板** | `PRIMARY` | `#1A73E8` | 主品牌蓝 |
| | `SECONDARY` | `#34A853` | 辅助绿 |
| | `BACKGROUND` | `#0F1923` | 应用背景（深海军蓝） |
| | `SURFACE` | `#1B2838` | 表面层 |
| | `CARD` | `#213040` | 卡片背景 |
| | `TEXT_PRIMARY` | `#E8EAED` | 主要文本 |
| | `TEXT_SECONDARY` | `#9AA0A6` | 次要文本 |
| **状态色** | `ONLINE` | `#34A853` | 在线（绿） |
| | `OFFLINE` | `#5F6368` | 离线（灰） |
| | `WARNING` | `#FBBC04` | 警告（黄） |
| | `DANGER` | `#EA4335` | 危险（红） |
| | `INFO` | `#4285F4` | 信息（蓝） |
| **阈值区颜色** | `ZONE_NORMAL` | `#34A853` | 正常区（绿） |
| | `ZONE_WARNING` | `#FBBC04` | 警告区（黄） |
| | `ZONE_DANGER` | `#EA4335` | 危险区（红） |
| **图表调色板** | `CHART_PALETTE[8]` | — | 8 种用于多系列图表的颜色，顺序：蓝、绿、黄、红、紫、青、橙、蓝灰 |

### 典型用法

```java
// 根据设备状态动态设置状态指示圆点颜色
int color = "online".equals(device.status) ? AppColors.ONLINE : AppColors.OFFLINE;
statusDot.setBackgroundTintList(ColorStateList.valueOf(color));

// 图表中为系列 i 选色
int seriesColor = AppColors.CHART_PALETTE[i % AppColors.CHART_PALETTE.length];
```

---

## 3. AppThresholds — 传感器告警阈值常量

**文件**：`core/AppThresholds.java`  
**性质**：纯常量类，`final` + 私有构造。  
**用途**：集中管理"何时触发 WARNING / DANGER"的数值边界，供 UI 颜色映射和告警逻辑引用。

### 常量列表

| 常量 | 值 | 含义 |
|------|----|------|
| `TEMP_WARNING` | `40.0` | 温度警告阈值（℃） |
| `TEMP_DANGER` | `60.0` | 温度危险阈值（℃） |
| `HUMIDITY_WARNING` | `80.0` | 湿度警告阈值（%RH） |
| `HUMIDITY_DANGER` | `95.0` | 湿度危险阈值（%RH） |
| `PRESSURE_WARNING` | `1050.0` | 气压警告阈值（hPa） |
| `PRESSURE_DANGER` | `1100.0` | 气压危险阈值（hPa） |
| `ONLINE_RATE_WARNING` | `0.9` | 在线率警告阈值（90%） |
| `ONLINE_RATE_DANGER` | `0.7` | 在线率危险阈值（70%） |

### 典型用法

```java
// UI 层根据阈值选择颜色
int color;
if (value >= AppThresholds.TEMP_DANGER)        color = AppColors.DANGER;
else if (value >= AppThresholds.TEMP_WARNING)  color = AppColors.WARNING;
else                                           color = AppColors.ZONE_NORMAL;
```

---

## 4. AppThemeConfig — 主题与背景配置管理

**文件**：`core/AppThemeConfig.java`  
**性质**：工具类，`final` + 私有构造，全部为静态方法 + 枚举。  
**依赖**：`android.content.SharedPreferences`，`android.view.Window`

### 4.1 枚举：`ThemePreset`（强调色预设）

8 种强调色预设，镜像 Flutter `AppThemePreset`：

| 枚举值 | hex | 标签 |
|--------|-----|------|
| `DEEP_BLUE` | `#1A73E8` | Deep Blue（默认） |
| `TEAL` | `#00897B` | Teal |
| `PURPLE` | `#7B1FA2` | Purple |
| `AMBER` | `#FF8F00` | Amber |
| `RED` | `#D32F2F` | Red |
| `CYAN` | `#0097A7` | Cyan |
| `GREEN` | `#2E7D32` | Green |
| `PINK` | `#C2185B` | Pink |

每个枚举有：
- `hex` — HTML 十六进制色值字符串
- `label` — 显示名称
- `color()` — 返回 ARGB int（供程序化使用）

### 4.2 枚举：`BgPreset`（背景色预设）

12 种背景预设（6 深色 + 6 浅色），镜像 Flutter `AppBgPreset`：

| 枚举值 | isLight | 标签 |
|--------|---------|------|
| `DEEP_NAVY` | false | Deep Navy（默认） |
| `DARK_SLATE` | false | Dark Slate |
| `CHARCOAL` | false | Charcoal |
| `TRUE_BLACK` | false | True Black (AMOLED) |
| `FOREST_DARK` | false | Forest Dark |
| `WARM_DARK` | false | Warm Dark |
| `SNOW_WHITE` | true | Snow White |
| `CLOUD_GREY` | true | Cloud Grey |
| `PAPER_CREAM` | true | Paper Cream |
| `MINT_LIGHT` | true | Mint Light |
| `LAVENDER_LIGHT` | true | Lavender |
| `SKY_BLUE` | true | Sky Blue |

每个枚举有：
- `bgHex` / `surfaceHex` / `cardHex` — 三层颜色的 hex 字符串
- `label` — 显示名称
- `isLight` — 是否为浅色主题（用于系统栏图标颜色判断）
- `bgColor()` / `surfaceColor()` / `cardColor()` — 解析为 ARGB int

### 4.3 静态方法

| 方法 | 说明 |
|------|------|
| `loadThemePreset(ctx)` | 从 SharedPreferences 读取当前强调色预设（默认 DEEP_BLUE） |
| `saveThemePreset(ctx, preset)` | 持久化强调色预设 |
| `loadBgPreset(ctx)` | 从 SharedPreferences 读取背景预设（默认 DEEP_NAVY） |
| `saveBgPreset(ctx, preset)` | 持久化背景预设 |
| `getAccentOverlayRes(preset)` | 返回对应强调色的 `ThemeOverlay` style 资源 ID |
| `getBgOverlayRes(preset)` | 返回对应背景的 `ThemeOverlay` style 资源 ID |
| `applyBgToWindow(window, ctx)` | 将背景色应用到状态栏/导航栏，更新图标颜色模式 |
| `applyBgToRoot(root, ctx)` | 将背景色直接设置给 Fragment 根 View 的背景色 |

### 4.4 Activity 中使用步骤

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    // 1. 先于 super.onCreate() 应用主题 overlay（顺序不可颠倒）
    getTheme().applyStyle(AppThemeConfig.getAccentOverlayRes(
            AppThemeConfig.loadThemePreset(this)), true);
    getTheme().applyStyle(AppThemeConfig.getBgOverlayRes(
            AppThemeConfig.loadBgPreset(this)), true);

    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // 2. 同步系统栏颜色和图标模式
    AppThemeConfig.applyBgToWindow(getWindow(), this);
}
```

### 4.5 SharedPreferences 存储详情

- **文件名**：`app_theme_prefs`（`MODE_PRIVATE`）
- **Key**：`app_theme_preset`（枚举名字符串）/ `app_bg_preset`（枚举名字符串）

---

## 5. LocaleHelper — 语言切换工具

**文件**：`core/LocaleHelper.java`  
**性质**：工具类，无需实例化。  
**依赖**：`androidx.appcompat.app.AppCompatDelegate`，`androidx.core.os.LocaleListCompat`（AppCompat 1.6+）

### 常量

| 常量 | 值 | 含义 |
|------|----|------|
| `LANG_SYSTEM` | `"system"` | 跟随系统语言 |
| `LANG_EN` | `"en"` | 英语 |
| `LANG_ZH` | `"zh"` | 中文 |

### 方法

| 方法 | 说明 |
|------|------|
| `applyAndSave(lang)` | 应用并持久化语言设置；AppCompat 自动重建当前 Activity，无需手动调 `recreate()` |
| `current()` | 返回当前活跃语言标签（返回值为 `LANG_*` 常量之一） |

### 实现原理

- `LANG_SYSTEM` → 传空 `LocaleListCompat`，AppCompat 恢复跟随系统
- 其他语言 → `LocaleListCompat.forLanguageTags(lang)` 覆盖应用级语言
- 语言信息由 **AppCompat** 持久化到其内部存储，无需开发者维护额外 SharedPreferences

### 使用示例

```java
// 设置页中切换语言
LocaleHelper.applyAndSave(LocaleHelper.LANG_ZH);

// 读取当前语言（用于选中状态高亮）
String lang = LocaleHelper.current(); // "zh" / "en" / "system"
```

---

## 6. protocol 子包总览

`protocol` 子包是 OpenSynaptic 协议的完整 Java 实现，**与 Android 框架解耦**（除 `GeohashDecoder` 外无任何 Android 导入），可直接在 JVM 单元测试中使用。

```
数据入站流程：
网络字节流
  → PacketDecoder.decode()         // 解析包头、验证 CRC-8 / CRC-16
  → DiffEngine.processPacket()     // FULL/DIFF/HEART 差分处理，还原完整 body 文本
  → BodyParser.parseText()         // 解析 header|sensor|sensor... 段结构
  → Models.DeviceMessage           // 交给 AppController

数据出站流程：
PacketBuilder.buildSensorPacket() / buildMultiSensorPacket()
  → Base62Codec.encodeValue()       // 传感器值编码
  → OsCrc.crc8() + OsCrc.crc16()   // 附加校验和
  → byte[]                          // 通过 TransportManager 发送
```

---

## 7. ProtocolConstants — 协议常量

**文件**：`core/protocol/ProtocolConstants.java`  
**性质**：常量类，镜像 Flutter `lib/core/protocol_constants.dart`。

### 7.1 `OS_STATES`（传感器状态码）

用于包体 sensor segment 中的状态字段：

| 代码 | 含义 |
|------|------|
| `U` | Normal（正常） |
| `A` | Alert（告警） |
| `W` | Warning（警告） |
| `D` | Danger（危险） |
| `O` | Offline（离线） |
| `E` | Error（错误） |

### 7.2 `OS_NODE_STATES`（节点状态码）

用于包体 header 段中的节点状态字段，额外包含：

| 代码 | 含义 |
|------|------|
| `S` | Sleep（休眠） |
| `I` | Idle（空闲） |

（其余与传感器状态码相同）

### 7.3 `OS_UNITS`（标准单位字符串）

约 40 个，按类别分组：温度（℃、℉、K）、湿度（%、%RH）、气压（hPa、kPa 等）、电压、电流、功率/能量、距离/液位、体积/流量、光照、气体/空气质量、速度/转速、质量、声音、频率、数字/逻辑（bool、cnt、raw、unit）。

### 7.4 `OS_SENSOR_IDS`（标准传感器 ID 前缀）

约 50 个，按类别分组：TEMP、HUM、PRES、LVL、VOLT、CURR、POWER、LUX、CO2/GAS、RPM/SPEED、WEIGHT、NOISE、COUNT/STATUS/BOOL、GEO/GEOHASH（定位）。

### 7.5 `OS_SENSOR_DEFAULT_UNIT`（传感器 ID → 默认单位映射）

```java
"TEMP" → "°C"，"HUM" → "%RH"，"PRES" → "hPa"，...
```

### 7.6 `defaultUnitFor(sensorId)`

根据传感器 ID（大小写不敏感）查找默认单位，支持精确匹配和前缀匹配。

```java
String unit = ProtocolConstants.defaultUnitFor("temp"); // → "°C"
String unit2 = ProtocolConstants.defaultUnitFor("TEMP_EXT"); // → "°C"（前缀匹配）
```

---

## 8. OsCmd — 命令字节码

**文件**：`core/protocol/OsCmd.java`

### 数据命令

| 常量 | 值 | 说明 |
|------|----|------|
| `DATA_FULL` | 63 | 完整帧（含全部 sensor 值） |
| `DATA_FULL_SEC` | 64 | 完整帧（加密变体） |
| `DATA_DIFF` | 170 | 差分帧（仅含变化的 sensor 值） |
| `DATA_DIFF_SEC` | 171 | 差分帧（加密变体） |
| `DATA_HEART` | 127 | 心跳帧（无 body，用缓存模板还原） |
| `DATA_HEART_SEC` | 128 | 心跳帧（加密变体） |

### 管理命令

| 常量 | 值 | 说明 |
|------|----|------|
| `ID_REQUEST` | 1 | 设备请求分配 AID |
| `ID_ASSIGN` | 2 | 服务器下发 AID |
| `ID_POOL_REQ` | 3 | AID 池请求 |
| `ID_POOL_RES` | 4 | AID 池响应 |
| `HANDSHAKE_ACK` | 5 | 握手确认 |
| `HANDSHAKE_NACK` | 6 | 握手拒绝 |
| `PING` | 9 | Ping |
| `PONG` | 10 | Pong |
| `TIME_REQUEST` | 11 | 时间同步请求 |
| `TIME_RESPONSE` | 12 | 时间同步响应 |
| `SECURE_DICT_READY` | 13 | 安全字典就绪 |
| `SECURE_CHANNEL_ACK` | 14 | 安全通道确认 |

### 方法

| 方法 | 说明 |
|------|------|
| `normalizeDataCmd(cmd)` | 将加密变体（SEC 后缀）映射到其明文等价值 |
| `isDataCmd(cmd)` | 判断是否为任意数据命令（含加密变体） |
| `isSecureCmd(cmd)` | 判断是否为加密变体命令 |
| `nameOf(cmd)` | 返回命令字节码的可读名称（调试用） |

---

## 9. OsCrc — CRC 校验计算

**文件**：`core/protocol/OsCrc.java`

OpenSynaptic 协议双重 CRC 校验的 Java 实现：

| 方法 | 多项式 | 用途 |
|------|--------|------|
| `crc8(byte[] data)` | `0x07`（CRC-8/DVB-S2 变体） | 覆盖包体（body），结果置于 `packet[len-3]` |
| `crc16(byte[] data)` | `0x1021`（CRC-16/CCITT-FALSE） | 覆盖包头+包体（除最后 2 字节外），结果置于 `packet[len-2..len-1]`（大端） |

> **注意**：CRC-8 输入为包体字节，CRC-16 输入为从包头起到 CRC-8 字节止的全部字节。

```java
// 验证收到的数据包
byte[] body = ...; // 已从 packet 中切出的 body 字节
int expectedCrc8 = OsCrc.crc8(body);
int gotCrc8 = packet[packet.length - 3] & 0xFF;
boolean ok = (expectedCrc8 == gotCrc8);
```

---

## 10. Base62Codec — Base62 编解码器

**文件**：`core/protocol/Base62Codec.java`

### 编码表

`"0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"`（共 62 字符，大小写均有）

### 常量

| 常量 | 值 | 说明 |
|------|----|------|
| `VALUE_SCALE` | `10000` | 传感器值精度：存储时乘以 10000，解码时除以 10000 → 支持 4 位小数 |

### 方法

| 方法 | 说明 |
|------|------|
| `encode(long value)` | long → Base62 字符串（支持负数，前缀 `-`） |
| `decode(String value)` | Base62 字符串 → long |
| `encodeValue(double sensorValue)` | 传感器 double 值 → Base62（乘以 10000 后编码） |
| `decodeValue(String b62)` | Base62 → 传感器 double 值（除以 10000） |
| `encodeTimestamp(long tsSec)` | Unix 秒时间戳 → Base64URL（6 字节大端，前 2 字节保留为 0x00） |
| `decodeTimestamp(String token)` | Base64URL 时间戳 token → Unix 秒 |

> `encodeTimestamp` 使用 **Base64URL（无填充）**，而非 Base62，这是协议规范要求。

---

## 11. PacketBuilder — 数据包构建器

**文件**：`core/protocol/PacketBuilder.java`

### 包帧格式

```
[0]    cmd          (1 byte)
[1]    routeCount   (1 byte, 固定=1)
[2-5]  aid          (4 bytes, 大端)
[6]    tid          (1 byte)
[7-8]  reserved     (2 bytes, 0x00)
[9-12] tsSec        (4 bytes, 大端)
[13..] body         (0-499 bytes, UTF-8)
[-3]   CRC-8        (body 的 CRC)
[-2..] CRC-16       (包头+body+CRC-8 的 CRC, 大端)
```

最大帧长 512 字节；超出则 `buildPacket` 返回 `null`。

### 内部类：`SensorEntry`

```java
public final class SensorEntry {
    public final String sensorId;
    public final String unit;
    public final String state;   // 默认 "U"
    public final double value;
}
```

### 方法

| 方法 | 说明 |
|------|------|
| `buildPacket(cmd, aid, tid, tsSec, body)` | 底层建帧，附 CRC-8/CRC-16 |
| `buildSensorPacket(aid, tid, tsSec, sensorId, unit, value)` | 构建单传感器 DATA_FULL 帧 |
| `buildMultiSensorPacket(aid, tid, tsSec, nodeId, nodeState, sensors)` | 兼容性重载（Map 格式传感器列表） |
| `buildMultiSensorPacket(aid, tid, tsSec, List<SensorEntry>)` | 新重载（SensorEntry 格式，供 SendFragment 使用） |
| `buildPing(seq)` | 构建 3 字节 PING 帧 |
| `buildPong(seq)` | 构建 3 字节 PONG 帧 |
| `buildIdRequest(seq)` | 构建 ID_REQUEST 帧 |
| `buildTimeRequest(seq)` | 构建 TIME_REQUEST 帧 |
| `buildIdAssign(aid)` | 构建 ID_ASSIGN 帧（5 字节） |
| `buildHandshakeAck(seq)` / `buildHandshakeNack(seq)` | 握手应答帧 |
| `buildSecureDictReady(seq)` | 安全字典就绪帧 |
| `buildRawHex(hex)` | 将十六进制字符串（含空格）解析为原始字节数组 |

### Body 文本格式

```
{nodeId}.{nodeState}.{tsToken}|{sensorId}>{state}.{unit}:{b62}|{sensorId2}>...
```

示例：`12345.U.AAAAAA|TEMP>U.°C:3E8|HUM>W.%RH:1Fc|`

---

## 12. PacketDecoder — 数据包头解析器

**文件**：`core/protocol/PacketDecoder.java`

### 方法

```java
public static Models.PacketMeta decode(byte[] packet)
```

- 最小包长 16 字节（13 字节头 + 0 字节 body + 3 字节 CRC），否则返回 `null`
- 解析结果填入 `Models.PacketMeta`：`cmd`、`routeCount`、`aid`（32-bit 大端）、`tid`、`tsSec`（32-bit 大端）、`bodyOffset=13`、`bodyLen`
- 同时验证 CRC-8（覆盖 body）和 CRC-16（覆盖前 N-2 字节），结果写入 `meta.crc8Ok` / `meta.crc16Ok`

---

## 13. BodyParser — 包体文本解析器

**文件**：`core/protocol/BodyParser.java`

### 方法

| 方法 | 说明 |
|------|------|
| `parse(byte[] bodyBytes)` | UTF-8 字节 → `BodyParseResult`（可 null） |
| `parseText(String text)` | 直接解析文本变体（`DiffEngine` 还原后的文本直接调此方法） |

### 解析逻辑

1. 以第一个 `|` 分割 header 与 payload
2. header 格式：`{nodeId}.{nodeState}.{tsToken}` — 用 `lastIndexOf('.')` 提取 tsToken，其余部分用 `indexOf('.')` 提取 nodeId 和 nodeState
3. payload 按 `|` 分隔为多个 sensor segment，每段格式：`{sensorId}>{state}.{unit}:{b62}`
4. b62 字段遇到 `#`、`!`、`@` 截断（附加元数据标记）
5. 返回 `Models.BodyParseResult`（含 `headerAid`、`headerState`、`tsToken`、`List<SensorReading>`）

---

## 14. DiffEngine — DIFF/HEART 模板引擎

**文件**：`core/protocol/DiffEngine.java`  
**性质**：**有状态**类，每个 `TransportManager` 实例持有一个 `DiffEngine` 实例。

### 核心设计

OpenSynaptic 协议支持三种数据帧类型，共享同一个按 (aid, tid) 索引的模板缓存：

| 帧类型 | 行为 |
|--------|------|
| `DATA_FULL` | 解析 body，提取模板（签名 + 值槽位），存入缓存，原样返回 body 文本 |
| `DATA_HEART` | 无 body，用缓存模板重建带当前时间戳的文本并返回 |
| `DATA_DIFF` | 读取位掩码，更新有变化的值槽位，用更新后模板重建文本并返回 |

### 模板格式

```
签名（signature）：
"{nodeId}.{nodeState}.{TS}|{sensorId}>\x01:\x01|..."
                                          ↑  ↑
                                     值槽位（\u0001 字符作为占位符）
                          每个 sensor 有 2 个槽位：state.unit 与 b62 值
```

### 主要方法

| 方法 | 说明 |
|------|------|
| `processPacket(cmd, aid, tid, body)` | 统一入口；根据 cmd 分发到 handleFull/handleHeart/handleDiff |
| `clear()` | 清空所有缓存（UDP 断开/MQTT 断开时调用） |
| `templateCount()` | 返回当前缓存模板总数（诊断用） |

### 状态管理注意事项

- `DiffEngine` 是 `TransportManager` 的成员变量（非静态单例）
- 每次 `stopUdp()` 和 `disconnectMqtt()` 都会调用 `diffEngine.clear()`
- 如果先收到 `DATA_DIFF` 而没有对应的 `DATA_FULL` 缓存，返回 `null`（数据丢弃，等待下一次完整帧）

---

## 15. GeohashDecoder — 地理坐标解码器

**文件**：`core/protocol/GeohashDecoder.java`

### 概述

将 **标准 Geohash**（base32 编码，32 字符）字符串解码为 WGS-84 纬度/经度。配合 `SensorReading.rawB62` 字段（携带 geohash 字符串）使用。

### 传感器 ID 约定

凡 `sensorId` 满足以下条件之一，即视为地理位置传感器：
- 完全等于 `"GEO"`
- 以 `"GEO"` 开头（如 `GEO1`、`GEOHASH`）

### 方法

| 方法 | 说明 |
|------|------|
| `decode(String geohash)` | 解码 geohash → `double[]{lat, lng}`，无效输入返回 `null` |
| `isGeoSensor(String sensorId)` | 判断是否为地理传感器（大小写不敏感） |

### 解码算法

标准 Geohash base32（字母表 `0123456789bcdefghjkmnpqrstuvwxyz`），交替缩小经度/纬度范围（首位缩小经度），最终取中值。

---

## 16.依赖关系图

```
AppController
    │
    ├── AppRepository (data)
    ├── TransportManager (transport)
    │       ├── DiffEngine ──► OsCmd
    │       ├── PacketDecoder ──► OsCrc
    │       └── BodyParser ──► Base62Codec
    └── RulesEngine (rules)
            └── PacketBuilder ──► Base62Codec, OsCmd, OsCrc

AppThemeConfig ──► AppColors（颜色 int 解析）
AppThresholds ──（被 UI 层及 RulesEngine 读取）
LocaleHelper ──（被 SettingsFragment 调用）
ProtocolConstants ──（被 SendFragment / BodyParser 读取）
GeohashDecoder ──（被 AppController 在 onMessage 中调用）
```

---

## 17.扩展指引

### 新增主题预设

1. 在 `AppThemeConfig.ThemePreset` 或 `BgPreset` 中添加枚举值
2. 在 `res/values/colors.xml` 中添加对应颜色资源
3. 在 `res/values/themes.xml` 中添加对应 `ThemeOverlay` style
4. 在 `AppThemeConfig.getAccentOverlayRes()` / `getBgOverlayRes()` switch 中添加 case

### 新增传感器类型

1. 在 `ProtocolConstants.OS_SENSOR_IDS` 中添加新 ID 前缀
2. 在 `ProtocolConstants.OS_UNITS` 中添加新单位（如未覆盖）
3. 在 `OS_SENSOR_DEFAULT_UNIT` 中添加 ID → 单位映射
4. 如为地理类型，更新 `GeohashDecoder.isGeoSensor()` 条件

### 新增协议命令

1. 在 `OsCmd` 中添加命令常量
2. 在 `OsCmd.nameOf()` 和相关 `isXxxCmd()` 方法中添加判断
3. 在 `PacketBuilder` 中添加对应 `buildXxx()` 方法
4. 在 `TransportManager.decodeIncoming()` 中按需处理新命令

