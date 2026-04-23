# 技术参考手册 — Gsyn Java

> English version: [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md)

本文档是基于源码精确整理的技术参考，适用于技术汇报、代码审查和硬件集成对接工作。

---

## 目录

1. [Wire 协议 — 精确帧结构](#1-wire-协议--精确帧结构)
2. [命令字节表 (OsCmd)](#2-命令字节表-oscmd)
3. [CRC 算法 (OsCrc)](#3-crc-算法-oscrc)
4. [Base62 / Base64url 编码 (Base62Codec)](#4-base62--base64url-编码-base62codec)
5. [Body 文本格式与解析 (BodyParser)](#5-body-文本格式与解析-bodyparser)
6. [差分 / 心跳引擎 (DiffEngine)](#6-差分--心跳引擎-diffengine)
7. [协议常量 — 传感器 ID 与单位](#7-协议常量--传感器-id-与单位)
8. [包解码器 (PacketDecoder)](#8-包解码器-packetdecoder)
9. [包构建器 (PacketBuilder)](#9-包构建器-packetbuilder)
10. [传输层 (TransportManager)](#10-传输层-transportmanager)
11. [应用控制器 (AppController)](#11-应用控制器-appcontroller)
12. [数据模型 (Models.java)](#12-数据模型-modelsjava)
13. [线程模型](#13-线程模型)
14. [与 README 的已知差异](#14-与-readme-的已知差异)

---

## 1. Wire 协议 — 精确帧结构

每个 Gsyn 数据包遵循以下**固定 13 字节包头**布局，来源：`PacketDecoder.decode()` 与 `PacketBuilder.buildPacket()`：

```
偏移  大小  字段          说明
────  ────  ──────────    ────────────────────────────────────────────
 0     1    CMD           命令字节（见第2节）
 1     1    ROUTE_COUNT   恒为 1（标准包）
 2     4    AID           源设备 ID — 大端序 uint32
 6     1    TID           事务 / 模板 ID — uint8
 7     2    RESERVED      两个保留字节（全零，填充用）
 9     4    TS_SEC        UNIX 时间戳（秒）— 大端序 uint32
13     N    BODY          可变长载荷（N = 总长 - 16）
13+N   1    CRC8          仅对 BODY 字节计算的 CRC-8/SMBUS
14+N   2    CRC16         对字节 [0 .. 14+N-1] 计算的 CRC-16/CCITT-FALSE
```

**最小有效包：16 字节**（13 字节头 + 0 字节 body + 3 字节 CRC）。  
**最大帧：512 字节**（在 `PacketBuilder.buildPacket()` 中强制限制）。

> ⚠️ AID 是 **4 字节**（uint32），不是 README 顶部描述的 1 字节。

### 帧长公式

```
frameLen = 13 + bodyLen + 3
         = bodyLen + 16
```

---

## 2. 命令字节表 (OsCmd)

以下为 `OsCmd.java` 中的实际十进制与十六进制值：

| 常量名 | 十进制 | 十六进制 | 类别 | 描述 |
|--------|--------|----------|------|------|
| `ID_REQUEST` | 1 | `0x01` | 会话 | 节点请求 AID 分配 |
| `ID_ASSIGN` | 2 | `0x02` | 会话 | 主节点分配 AID |
| `ID_POOL_REQ` | 3 | `0x03` | 会话 | 池式 ID 请求 |
| `ID_POOL_RES` | 4 | `0x04` | 会话 | 池式 ID 响应 |
| `HANDSHAKE_ACK` | 5 | `0x05` | 会话 | 握手接受 |
| `HANDSHAKE_NACK` | 6 | `0x06` | 会话 | 握手拒绝 |
| `PING` | 9 | `0x09` | 控制 | 心跳请求 |
| `PONG` | 10 | `0x0A` | 控制 | 心跳响应 |
| `TIME_REQUEST` | 11 | `0x0B` | 控制 | 节点请求 UNIX 时间戳 |
| `TIME_RESPONSE` | 12 | `0x0C` | 控制 | 主节点发送时间戳 |
| `SECURE_DICT_READY` | 13 | `0x0D` | 安全 | 加密字典就绪 |
| `SECURE_CHANNEL_ACK` | 14 | `0x0E` | 安全 | 安全通道确认 |
| `DATA_FULL` | 63 | `0x3F` | 数据 | 完整传感器帧 |
| `DATA_FULL_SEC` | 64 | `0x40` | 数据 | 完整帧（加密） |
| `DATA_HEART` | 127 | `0x7F` | 数据 | 心跳帧（复用模板） |
| `DATA_HEART_SEC` | 128 | `0x80` | 数据 | 心跳帧（加密） |
| `DATA_DIFF` | 170 | `0xAA` | 数据 | 差分更新帧 |
| `DATA_DIFF_SEC` | 171 | `0xAB` | 数据 | 差分更新帧（加密） |

### 辅助方法

```java
OsCmd.isDataCmd(cmd)        // DATA_FULL/SEC/DIFF/SEC/HEART/SEC 均返回 true
OsCmd.isSecureCmd(cmd)      // 仅 FULL_SEC / DIFF_SEC / HEART_SEC 返回 true
OsCmd.normalizeDataCmd(cmd) // 去除 _SEC 后缀：FULL_SEC→FULL，DIFF_SEC→DIFF，HEART_SEC→HEART
OsCmd.nameOf(cmd)           // 返回可读字符串，如 "DATA_FULL"
```

---

## 3. CRC 算法 (OsCrc)

### CRC-8/SMBUS

- **多项式**：`0x07`（x⁸ + x² + x + 1）
- **初始值**：`0x00`
- **计算范围**：仅 BODY 字节
- **位置**：帧末第 3 字节（`packet[len-3]`）

```java
// OsCrc.crc8 实现
int crc = 0x00;
for (byte b : data) {
    crc = (crc ^ (b & 0xFF)) & 0xFF;
    for (int bit = 0; bit < 8; bit++) {
        crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
    }
}
```

### CRC-16/CCITT-FALSE

- **多项式**：`0x1021`（x¹⁶ + x¹² + x⁵ + 1）
- **初始值**：`0xFFFF`
- **计算范围**：从偏移 0 到倒数第 3 字节（不含最后 2 字节）
- **位置**：帧末最后 2 字节，大端序

```java
// OsCrc.crc16 实现
int crc = 0xFFFF;
for (byte b : data) {
    crc = (crc ^ ((b & 0xFF) << 8)) & 0xFFFF;
    for (int bit = 0; bit < 8; bit++) {
        crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
    }
}
```

### 验证逻辑

仅当 **`crc8Ok && crc16Ok` 同时为 true** 时，数据包才被接受处理。

---

## 4. Base62 / Base64url 编码 (Base62Codec)

### 字母表

```
"0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
 ^──数字──^  ^────────────小写字母────────────^  ^──大写字母──^
```

索引 0 = `'0'`，索引 61 = `'Z'`。支持负数（前缀 `-`）。

### 传感器值编码

```java
VALUE_SCALE = 10000

encodeValue(double v) → encode(Math.round(v * 10000))
decodeValue(String b) → decode(b) / 10000.0
```

示例：
- `25.6 °C` → `encode(256000)` → `"nRY"`
- `-12.34` → `encode(-123400)` → `"-VWw"`
- `0.0` → `"0"`

### 时间戳编码

时间戳使用 **Base64url**（非 Base62）：

```java
encodeTimestamp(long tsSec):
    bytes = [0x00, 0x00, (tsSec>>24)&0xFF, (tsSec>>16)&0xFF, (tsSec>>8)&0xFF, tsSec&0xFF]
    → Base64url（无填充），固定 8 字符

decodeTimestamp(String token):
    → 补齐到 4 的倍数，Base64url 解码
    → 从 bytes[2..5] 重建 uint32
```

示例：UNIX 时间戳 `1712345678` → `"AABlqg4o"`（8 字符，URL 安全，无 `=` 填充）。

---

## 5. Body 文本格式与解析 (BodyParser)

### Body 文本语法

```
body       = header "|" sensor_list
header     = aid "." node_state "." ts_token
sensor_list= (segment "|")*
segment    = sensor_id ">" state "." unit ":" b62_value
```

Body 字符串示例：
```
1.U.AABlqg4o|TEMP>U.Cel:nRY|HUM>U.%RH:52h|PRES>U.hPa:3eVQ|
```

解析结果：
- 头部：AID=1，节点状态=`U`（正常），时间戳=Base64url token
- TEMP：state=U，unit=`Cel`，value=`nRY` → `25.6`
- HUM：state=U，unit=`%RH`，value=`52h` → `48.2`
- PRES：state=U，unit=`hPa`，value=`3eVQ` → `1013.25`

### BodyParser.parseText() 解析步骤

```
1. 找第一个 '|' → 分割 header / payload
2. Header：最后一个 '.' 之后为 ts_token；之前：aid '.' node_state
3. Payload：按 '|' 分割，每个 segment 解析为：
      sid   = '>' 之前的部分
      state = '>' 之后，第一个 '.' 之前
      unit  = 第一个 '.' 到 ':' 之间
      b62   = ':' 之后（去除 '#' '!' '@' 等后缀标记）
4. value = Base62Codec.decodeValue(b62)
```

---

## 6. 差分 / 心跳引擎 (DiffEngine)

### 设计目标

通过只传输变化的传感器值来减少带宽消耗。引擎维护按 `(AID, TID)` 索引的模板缓存。

### 缓存键

```java
cache[aidStr][tidStr]
// aidStr = String.valueOf(aid)        e.g. "1"
// tidStr = String.format("%02d", tid) e.g. "01"
```

### 模板状态结构

```java
class TemplateState {
    String     signature;  // 含 {TS} 和 '\u0001' 占位符的 body 骨架
    List<byte[]> valsBin;  // 每个值槽的 UTF-8 字节缓存
}
```

### DATA_FULL 处理流程

```
1. 解析 body 文本 → DecompResult
2. 对每个传感器 segment：
     - 提取 tag（传感器ID）、meta（state.unit）、value（b62）
     - 向 signature 追加 "tag>\u0001:\u0001"
     - 向 valsBin 追加 meta 字节和 value 字节（每个传感器 2 个槽位）
3. 构建 signature = "{hBase}.{TS}|{segments...}"
4. 存入 cache[aid][tid]
5. 返回原始 body 文本
```

### DATA_HEART 处理流程

```
1. 查找 cache[aid][tid]
2. 缓存不存在 → 返回 null
3. 重建：将 {TS} 替换为当前时间戳，将每个 '\u0001' 替换为对应的 valsBin 内容
4. 返回重建后的 body 文本
```

### DATA_DIFF 处理流程

```
1. 查找 cache[aid][tid]
2. 读取大端序位掩码：maskLen = ceil(槽位数 / 8) 字节
3. 对掩码中每个置 1 的位 i：
     - 读取 1 字节长度前缀 → 读取 vLen 字节 → 更新 valsBin[i]
4. 使用更新后的模板重建 body
5. 返回重建后的 body 文本
```

### 状态管理

```java
diffEngine.clear()          // 在 stopUdp() 和 disconnectMqtt() 时调用
diffEngine.templateCount()  // 返回当前缓存的模板总数（用于 Health 页面）
```

---

## 7. 协议常量 — 传感器 ID 与单位

### 节点状态码

| 码 | 含义 |
|----|------|
| `U` | Unknown / 未分类（默认） |
| `A` | Active / 告警 |
| `W` | Warning / 警告 |
| `D` | Danger / 危险 |
| `O` | Offline / 离线 |
| `E` | Error / 错误 |
| `S` | Sleep / 休眠 |
| `I` | Idle / 空闲 |

### 传感器读数状态码（每条读数独立）

`U` · `A` · `W` · `D` · `O` · `E`

### 标准传感器 ID（部分）

| 类别 | ID 列表 |
|------|---------|
| 温度 | `TEMP` `T1` `T2` `T3` `TMP` |
| 湿度 | `HUM` `H1` `H2` `RH` |
| 气压 | `PRES` `P1` `BAR` |
| 液位 / 距离 | `LVL` `L1` `LEVEL` `DIST` `D1` |
| 电压 | `VOLT` `V1` `VBAT` `VCC` |
| 电流 | `CURR` `I1` `IBAT` |
| 功率 | `POWER` `PW1` |
| 光照 | `LUX` `LIGHT` |
| 气体 / 空气质量 | `CO2` `GAS` `PPM` `VOC` |
| 定位 | `GEO` `GEOHASH` `GEO1` `LOCATION` |

### 默认单位查询

```java
ProtocolConstants.defaultUnitFor("TEMP")  // → "°C"
ProtocolConstants.defaultUnitFor("HUM")   // → "%RH"
ProtocolConstants.defaultUnitFor("PRES")  // → "hPa"
ProtocolConstants.defaultUnitFor("TEMP2") // → "°C"  （前缀匹配）
ProtocolConstants.defaultUnitFor("XYZ")   // → ""    （未知）
```

匹配策略：**大小写不敏感 + 前缀匹配**，`"TEMP2"` 会匹配 `"TEMP"` 的映射规则。

---

## 8. 包解码器 (PacketDecoder)

### 方法签名

```java
public static Models.PacketMeta decode(byte[] packet)
// 返回 null 的情形：packet == null、长度 < 16、bodyLen < 0
```

### PacketMeta 字段来源

| 字段 | 来源字节 | 说明 |
|------|---------|------|
| `cmd` | `packet[0]` | 命令字节（uint8） |
| `routeCount` | `packet[1]` | 路由计数（uint8） |
| `aid` | `packet[2..5]` | 4 字节大端序 AID |
| `tid` | `packet[6]` | 事务 ID（uint8） |
| `tsSec` | `packet[9..12]` | 4 字节大端序 UNIX 时间戳 |
| `bodyOffset` | 13 | 固定值 |
| `bodyLen` | `packet.length - 16` | 计算得出 |
| `crc8Ok` | `packet[len-3]` | 与 `OsCrc.crc8(body)` 比对 |
| `crc16Ok` | `packet[len-2..len-1]` | 与 `OsCrc.crc16(bytes[0..len-3])` 比对 |

---

## 9. 包构建器 (PacketBuilder)

### 核心构建器

```java
PacketBuilder.buildPacket(int cmd, int aid, int tid, long tsSec, byte[] body)
// 构建：[13字节头][body][CRC8 1B][CRC16 2B]
// frameLen > 512 时返回 null
```

### 高层封装

```java
// 单传感器包
buildSensorPacket(int aid, int tid, long tsSec, String sensorId, String unit, double value)
// Body："{aid}.U.{ts}|{sensorId}>U.{unit}:{b62}|"

// 多传感器包（SensorEntry 列表）
buildMultiSensorPacket(int aid, int tid, long tsSec, List<SensorEntry> entries)
// Body："{aid}.U.{ts}|{sid1}>{state}.{unit}:{b62}|{sid2}...|"
```

### 控制帧构建（短帧，无完整包头）

```java
buildPing(int seq)            // [0x09][seq>>8][seq&0xFF]  3 字节
buildPong(int seq)            // [0x0A][seq>>8][seq&0xFF]  3 字节
buildIdRequest(int seq)       // [0x01][seq>>8][seq&0xFF]  3 字节
buildTimeRequest(int seq)     // [0x0B][seq>>8][seq&0xFF]  3 字节
buildIdAssign(int aid)        // [0x02][aid 4字节大端序]    5 字节
buildHandshakeAck(int seq)    // [0x05][seq>>8][seq&0xFF]  3 字节
buildHandshakeNack(int seq)   // [0x06][seq>>8][seq&0xFF]  3 字节
buildSecureDictReady(int seq) // [0x0D][seq>>8][seq&0xFF]  3 字节
buildRawHex(String hex)       // 解析十六进制字符串 → 原始字节数组
```

---

## 10. 传输层 (TransportManager)

### 单例访问

```java
TransportManager tm = TransportManager.get(context);
```

### UDP

```java
tm.startUdp(String host, int port)  // 在 "gsyn-udp-listener" 线程绑定 0.0.0.0:port
tm.stopUdp()                         // 关闭 socket，清空 DiffEngine 缓存
tm.isUdpRunning()                    // volatile boolean
tm.sendCommand(byte[] frame, int deviceAid, String udpHost, int udpPort)
```

UDP 接收循环：缓冲区大小 = 1024 字节。运行在守护线程 `"gsyn-udp-listener"` 上。

### MQTT

```java
tm.connectMqtt(String broker, int port, String topic)
// topic 为 null/空时默认订阅："opensynaptic/#"
// 客户端 ID："gsyn-java-{UUID}"
// 选项：AutoReconnect=true，CleanSession=true

tm.disconnectMqtt()   // 清空 DiffEngine 缓存
tm.isMqttConnected()  // volatile boolean
```

发送命令时，MQTT 发布到：`"opensynaptic/cmd/{deviceAid}"`。

### 入站消息处理管线

```
decodeIncoming(byte[] data, String transportType):
  1. PacketDecoder.decode(data)        → 长度 < 16 时返回 null
  2. 丢弃 !crc8Ok || !crc16Ok 的包
  3. 丢弃非数据命令字节的包（!OsCmd.isDataCmd）
  4. diffEngine.processPacket(cmd, aid, tid, body) → bodyText（缓存未命中时返回 null）
  5. BodyParser.parseText(bodyText)    → BodyParseResult
  6. 构建 DeviceMessage（cmd、aid、tid、tsSec、nodeId、nodeState、transportType、readings、rawFrame）
  7. totalMessages++; messagesThisSecond++
  8. 在调用线程（UDP线程 或 MQTT回调线程）上通知所有 MessageListener
```

### 统计定时器

`ScheduledExecutorService` 每隔 **1 秒** 触发 `emitStats()`：
- 快照 `udpRunning`、`mqttConnected`、`messagesThisSecond`、`totalMessages`
- 重置 `messagesThisSecond` 为 0
- 通知所有 `StatsListener`

---

## 11. 应用控制器 (AppController)

### 单例与组件装配

```java
AppController ctrl = AppController.get(context);
// 首次调用时构建：
//   SharedPreferences "gsyn_java_prefs"
//   AppRepository.get(context)
//   TransportManager.get(context)
//   RulesEngine(repository, transportManager)
//   transportManager.addMessageListener(this)
//   repository.seedDefaultRuleIfEmpty()  // 如无规则则插入默认规则
```

### onMessage() — 核心数据管线

在每个经过验证的入站数据包后调用（运行在传输层后台线程上）：

```
1. 根据 message.deviceAid / nodeId / transportType / lastSeenMs=now 构建 Device 对象
   status 固定为 "online"
2. 扫描 readings 中的 GEO 类传感器 ID
   → GeohashDecoder.decode(rawB62 或 unit) → 设置 device.lat / device.lng
3. repository.upsertDevice(device)
4. 对每条 SensorReading → 构建 SensorData → repository.insertSensorData(data)
5. rulesEngine.evaluate(message, udpHost, udpPort)
   （udpHost/udpPort 来自 SharedPreferences 键 "udp_host" / "udp_port"）
```

---

## 12. 数据模型 (Models.java)

所有模型均为纯 Java 对象，无 ORM 注解。

### PacketMeta（协议解码输出）

```java
int cmd, routeCount, aid, tid, bodyOffset, bodyLen
long tsSec
boolean crc8Ok, crc16Ok
```

### SensorReading（协议层，持久化前）

```java
String sensorId, unit, state, rawB62
double value
```

### DeviceMessage（完整解码包）

```java
int cmd, deviceAid, tid
long timestampSec
String nodeId, nodeState, transportType
List<SensorReading> readings
byte[] rawFrame
```

### Device（持久化）

```java
long id; int aid
String name, type, status, transportType
double lat, lng
long lastSeenMs
```

### SensorData（持久化时序数据）

```java
long id, timestampMs; int deviceAid
String sensorId, unit, rawB62
double value
```

### Rule（持久化自动化规则）

```java
long id, cooldownMs; boolean enabled
String name, operator, actionType, actionPayload
Integer deviceAidFilter; String sensorIdFilter
double threshold

// 内置求值方法（支持 >, <, >=, <=, ==, !=）：
boolean evaluate(double sensorValue)
```

### AppUser（用户管理）

```java
long id, createdMs
String username, passwordHash
String role  // "admin" | "viewer"
boolean isAdmin()
```

---

## 13. 线程模型

| 线程名称 | 创建者 | 阻塞在 | 职责 |
|---------|--------|--------|------|
| `main` | Android | — | Fragment 生命周期、视图更新、`runOnUiThread` 调度 |
| `gsyn-udp-listener` | `TransportManager.startUdp()` | `DatagramSocket.receive()` | 接收并解码 UDP 数据包 |
| `mqttCallbackThread` | Eclipse Paho 库 | MQTT Broker | MQTT 消息回调 |
| `pool-1-thread-1`（调度器） | `Executors.newSingleThreadScheduledExecutor()` | — | 每秒统计数据定时触发 |

**关键规则**：所有 `MessageListener.onMessage()` 和 `StatsListener.onStats()` 回调均在**后台线程**执行。所有 UI 更新必须通过 `getActivity().runOnUiThread(...)` 切回主线程。

---

## 14. 与 README 的已知差异

顶层 README 中存在若干与实际源码不符的描述。对接外部工具时请以本表为准：

| 内容 | README 描述 | 实际源码 |
|------|------------|---------|
| AID 字段大小 | 1 字节 | **4 字节**（uint32，大端序，偏移 2–5） |
| 包头大小 | 5 字节 | **13 字节** |
| `DATA_FULL` 十六进制 | `0x20` | **`0x3F`**（十进制 63） |
| `DATA_DIFF` 十六进制 | `0x21` | **`0xAA`**（十进制 170） |
| `DATA_HEART` 十六进制 | `0x22` | **`0x7F`**（十进制 127） |
| `PING` 十六进制 | `0x01` | **`0x09`**（十进制 9） |
| MQTT 订阅 topic | `gsyn/#` | **`opensynaptic/#`** |
| MQTT 命令 topic | `gsyn/out/<aid>` | **`opensynaptic/cmd/<aid>`** |
| 时间戳编码方式 | "8字符 Base64url" | 正确——但实现上使用 `Base64.getUrlEncoder`，非 Base62 |

