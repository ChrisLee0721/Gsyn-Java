# Gsyn-Java `transport` 包开发者文档

> 包路径：`com.opensynaptic.gsynjava.transport`  
> 本包是应用的**通信传输层**，负责 UDP 监听、MQTT 接入、原始字节帧的完整解码流水线，以及将解码后的 `DeviceMessage` 分发给所有注册监听者。  
> 同时对外提供双向通信能力（发送命令）和实时流量统计。

---

## 目录

1. [包结构总览](#1-包结构总览)
2. [TransportManager — 核心类](#2-transportmanager--核心类)
3. [接口定义](#3-接口定义)
4. [单例生命周期](#4-单例生命周期)
5. [UDP 传输](#5-udp-传输)
6. [MQTT 传输](#6-mqtt-传输)
7. [命令发送](#7-命令发送)
8. [入站解码流水线](#8-入站解码流水线)
9. [流量统计](#9-流量统计)
10. [与其他层的依赖关系](#10-与其他层的依赖关系)
11. [扩展指引](#11-扩展指引)

---

## 1. 包结构总览

```
transport/
└── TransportManager.java   // 单例传输管理器（UDP + MQTT + 解码流水线）
```

---

## 2. TransportManager — 核心类

**文件**：`transport/TransportManager.java`  
**模式**：懒加载单例（`synchronized get(context)`）  
**线程模型**：

| 线程 | 职责 |
|------|------|
| `gsyn-udp-listener`（后台线程） | 阻塞接收 UDP 数据报 |
| Paho MQTT 内部线程 | MQTT 消息回调 |
| `ScheduledExecutorService`（单线程） | 每秒触发 `emitStats()` |
| 调用方线程 | `startUdp`、`connectMqtt`、`sendCommand` 等均可从任意线程调用（synchronized） |

---

## 3. 接口定义

### `MessageListener`

```java
public interface MessageListener {
    void onMessage(Models.DeviceMessage message);
}
```

每当成功解码一条完整的 `DeviceMessage` 时，所有注册的 `MessageListener` 会在**接收线程**（UDP 线程或 MQTT 回调线程）上同步回调。

**注意**：`AppController` 实现此接口并在首次创建时自动注册。

### `StatsListener`

```java
public interface StatsListener {
    void onStats(Models.TransportStats stats);
}
```

每秒由 `ScheduledExecutorService` 触发，携带最新的 `TransportStats` 快照。  
UI 层（如 `DashboardFragment`）注册此接口以实时更新连接状态指示器。

---

## 4. 单例生命周期

```java
// 获取单例（幂等）
TransportManager tm = TransportManager.get(context);

// 启动 UDP 监听（绑定全 0.0.0.0：port）
tm.startUdp("127.0.0.1", 9876);

// 或连接 MQTT
tm.connectMqtt("192.168.1.100", 1883, "opensynaptic/#");

// 注册监听器
tm.addMessageListener(myListener);
tm.addStatsListener(myStatsListener);

// 停止
tm.stopUdp();
tm.disconnectMqtt();
```

**注意**：单例在应用进程生命周期内不会被销毁；`stopUdp()` / `disconnectMqtt()` 仅停止传输，不销毁单例。

---

## 5. UDP 传输

### `startUdp(host, port)`

```java
public synchronized void startUdp(String host, int port) throws Exception
```

1. 先调用 `stopUdp()` 关闭已有 Socket
2. 绑定 `DatagramSocket(port)`（监听全局所有接口的入站数据报）
3. `host` 参数仅用于出站命令的默认目标，不影响接收行为
4. 启动 `gsyn-udp-listener` 后台线程
5. 每次接收到数据报，切片出实际数据字节后调用 `decodeIncoming(data, "udp")`
6. 调用 `emitStats()` 通知监听器

**接收缓冲区**：每次分配 1024 字节的临时缓冲区；实际数据通过 `System.arraycopy` 精确切片。

### `stopUdp()`

```java
public synchronized void stopUdp()
```

1. 设置 `udpRunning = false`
2. 关闭 `DatagramSocket`（会中断阻塞的 `receive()`，线程自然退出）
3. 清空 `DiffEngine` 模板缓存（设备重连后需要重新学习 DATA_FULL 模板）
4. 调用 `emitStats()`

### 状态查询

```java
boolean running = tm.isUdpRunning(); // 是否正在监听
```

---

## 6. MQTT 传输

### `connectMqtt(broker, port, topic)`

```java
public synchronized void connectMqtt(String broker, int port, String topic) throws MqttException
```

1. 先调用 `disconnectMqtt()` 断开已有连接
2. 使用 Paho `MqttClient`，客户端 ID = `"gsyn-java-" + UUID.randomUUID()`（每次新建，保证唯一）
3. 持久化策略：`MemoryPersistence`（内存存储，应用重启后不保留未确认消息）
4. 连接选项：`setAutomaticReconnect(true)`，`setCleanSession(true)`
5. 订阅主题：若 `topic` 为 null 或空，默认订阅 `"opensynaptic/#"`
6. 成功后设置 `mqttConnected = true`，调用 `emitStats()`

**回调集成**：

| 回调 | 行为 |
|------|------|
| `connectionLost` | 设置 `mqttConnected = false`，`emitStats()` |
| `messageArrived` | 调用 `decodeIncoming(payload, "mqtt")` |
| `deliveryComplete` | 空（不处理出站确认） |

### `disconnectMqtt()`

```java
public synchronized void disconnectMqtt()
```

1. 若 `mqttClient` 已连接，调用 `disconnect()`
2. 置空 `mqttClient`，`mqttConnected = false`
3. 清空 `DiffEngine` 模板缓存
4. 调用 `emitStats()`

### 状态查询

```java
boolean connected = tm.isMqttConnected();
```

---

## 7. 命令发送

```java
public synchronized boolean sendCommand(byte[] frame, int deviceAid,
                                         String udpHost, int udpPort)
```

**发送策略**（优先级顺序）：

1. **MQTT**：若 `mqttClient != null && mqttClient.isConnected()`，发布到 `"opensynaptic/cmd/{deviceAid}"`，QoS 默认 0
2. **UDP**：创建临时 `DatagramSocket`（不绑定端口），发送到 `{udpHost}:{udpPort}`，发送完成后立即关闭

**返回值**：成功 `true`，任何异常 `false`（异常记录到 logcat `"TransportManager"`）。

**调用方**：`RulesEngine.execute()` 在 `send_command` 动作中调用；`SendFragment` 用户手动发包时也调用。

---

## 8. 入站解码流水线

```java
private void decodeIncoming(byte[] data, String transportType)
```

所有入站字节（UDP 和 MQTT）均经过同一条流水线：

```
原始字节数组 (data)
    │
    ▼
PacketDecoder.decode(data)
    │ 返回 PacketMeta（cmd, aid, tid, tsSec, bodyOffset, bodyLen, crc8Ok, crc16Ok）
    │ CRC 检查失败 → return（丢弃）
    │ 非数据命令（OsCmd.isDataCmd 为 false）→ return（丢弃）
    │
    ▼
从 data 中切出 body 字节
    │
    ▼
DiffEngine.processPacket(cmd, aid, tid, body)
    │ DATA_FULL  → 学习模板，返回 body 文本
    │ DATA_HEART → 用缓存模板重建文本（含当前时间戳）
    │ DATA_DIFF  → 按位掩码更新缓存槽位，重建文本
    │ 缓存缺失 / 解析失败 → return null → decodeIncoming 返回（丢弃）
    │
    ▼
BodyParser.parseText(bodyText)
    │ 返回 BodyParseResult（headerAid, headerState, readings）
    │ null 或 readings 为空 → return（丢弃）
    │
    ▼
构建 DeviceMessage
    │  cmd, deviceAid, tid, timestampSec, nodeId, nodeState,
    │  transportType, readings, rawFrame
    │
    ▼
totalMessages++; messagesThisSecond++;
    │
    ▼
for (MessageListener l : messageListeners) {
    l.onMessage(message);    // 同步，在接收线程执行
}
```

### 丢弃条件总结

| 条件 | 描述 |
|------|------|
| `PacketMeta == null` | 包太短（< 16 字节） |
| `!crc16Ok \|\| !crc8Ok` | CRC 校验失败 |
| `!OsCmd.isDataCmd(cmd)` | 管理命令（握手/Ping 等），不处理数据 |
| `bodyText == null` | DiffEngine 缓存缺失或格式错误 |
| `parsed == null \|\| readings.isEmpty()` | 包体无法解析或无传感器数据 |

---

## 9. 流量统计

### `emitStats()`（私有，每秒自动触发 + 状态变更时手动触发）

```java
private synchronized void emitStats()
```

构建 `TransportStats` 快照：

| 字段 | 来源 |
|------|------|
| `udpConnected` | `udpRunning` |
| `mqttConnected` | `mqttConnected` |
| `messagesPerSecond` | `messagesThisSecond`（每秒归零） |
| `totalMessages` | `totalMessages`（累计） |

快照存入 `lastStats` 供 `getLastStats()` 获取，并同步通知所有 `StatsListener`。

### 获取最新统计

```java
Models.TransportStats stats = tm.getLastStats();
```

---

## 10. 与其他层的依赖关系

```
TransportManager
    │
    ├── 使用：core.protocol.PacketDecoder
    ├── 使用：core.protocol.OsCmd
    ├── 使用：core.protocol.DiffEngine
    ├── 使用：core.protocol.BodyParser
    ├── 使用：data.Models（DeviceMessage, PacketMeta, BodyParseResult, TransportStats）
    │
    ├── 通知：MessageListener（AppController 实现）
    └── 通知：StatsListener（DashboardFragment 等 UI 实现）

AppController
    └── 持有 TransportManager 引用，注册自身为 MessageListener，
        并在 sendCommand 触发场景下传递 udpHost/udpPort
```

---

## 11. 扩展指引

### 新增传输协议（如 TCP/WebSocket）

1. 在 `TransportManager` 中添加新的连接状态字段、连接/断开方法
2. 在新的接收线程/回调中调用 `decodeIncoming(data, "tcp")`（或对应类型字符串）
3. 在 `sendCommand()` 的优先级链中添加新协议的发送分支
4. 在 `TransportStats` 模型中添加新的连接状态字段

### 修改 MQTT 主题结构

修改 `connectMqtt()` 中的 `mqttClient.subscribe(topic)`，以及 `sendCommand()` 中的 `"opensynaptic/cmd/" + deviceAid`。

### 增加 QoS / 持久化

将 `MemoryPersistence` 改为 `MqttDefaultFilePersistence`（持久化未确认消息到文件），并在 `MqttConnectOptions` 中设置适当 QoS。

### 并发流量压力优化

如同时处理大量设备，可将 `decodeIncoming` 改为提交到 `ExecutorService` 线程池（注意 `DiffEngine` 非线程安全，需为每个 aid 分配独立实例或加锁）。

