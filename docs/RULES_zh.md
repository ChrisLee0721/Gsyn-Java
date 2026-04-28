# Gsyn-Java `rules` 包开发者文档

> 包路径：`com.opensynaptic.gsynjava.rules`  
> 本包是应用的**业务规则引擎层**，负责在每条设备消息到达时，依次评估数据库中所有启用的规则，并在条件满足时执行对应动作（创建告警或向设备发送命令）。

---

## 目录

1. [包结构总览](#1-包结构总览)
2. [RulesEngine — 核心引擎](#2-rulesengine--核心引擎)
3. [规则评估完整流程](#3-规则评估完整流程)
4. [动作执行详解](#4-动作执行详解)
5. [冷却机制](#5-冷却机制)
6. [与其他层的交互](#6-与其他层的交互)
7. [扩展指引](#7-扩展指引)

---

## 1. 包结构总览

```
rules/
└── RulesEngine.java    // 规则评估与动作执行引擎
```

---

## 2. RulesEngine — 核心引擎

**文件**：`rules/RulesEngine.java`  
**实例化**：由 `AppController` 在构造时创建（`new RulesEngine(repository, transportManager)`），无单例设计。  
**线程**：`onMessage` 回调在 `TransportManager` 的后台 UDP/MQTT 线程调用，`evaluate()` 直接在该线程同步执行。

### 成员变量

| 成员 | 类型 | 说明 |
|------|------|------|
| `repository` | `AppRepository` | 读取规则、写入告警、写入操作日志 |
| `transportManager` | `TransportManager` | 执行 `send_command` 动作时调用 `sendCommand()` |
| `lastTriggered` | `Map<Long, Long>` | 规则 ID → 上次触发时间戳（毫秒），用于冷却计算 |

### 主方法：`evaluate(message, udpHost, udpPort)`

```java
public void evaluate(Models.DeviceMessage message, String udpHost, int udpPort)
```

**参数**：

| 参数 | 说明 |
|------|------|
| `message` | 已解码的设备消息（含所有传感器读数） |
| `udpHost` | UDP 下行命令目标 IP（来自 SharedPreferences `udp_host`） |
| `udpPort` | UDP 下行命令目标端口（来自 SharedPreferences `udp_port`） |

---

## 3. 规则评估完整流程

```
evaluate(message) 被调用
    │
    ├── 从 repository 加载全部 enabled=1 的规则列表
    │   （若为空，立即返回）
    │
    └── 对每条 SensorReading in message.readings:
            │
            └── 对每条 Rule in enabledRules:
                    │
                    ├─[过滤1] deviceAidFilter ≠ null
                    │   且 deviceAidFilter ≠ message.deviceAid → skip
                    │
                    ├─[过滤2] sensorIdFilter 非空
                    │   且 sensorIdFilter.equalsIgnoreCase(reading.sensorId) == false → skip
                    │
                    ├─[评估] rule.evaluate(reading.value) == false → skip
                    │   评估逻辑：reading.value {operator} rule.threshold
                    │
                    ├─[冷却] lastTriggered[rule.id] 存在
                    │   且 now - last < rule.cooldownMs → skip
                    │
                    └── 满足全部条件 → execute(rule, message, reading, udpHost, udpPort)
                                        lastTriggered[rule.id] = now
```

### Filter 逻辑细节

| 过滤器字段 | 值为 null/空 时 | 值非空时 |
|------------|----------------|----------|
| `deviceAidFilter` | 匹配所有设备 | 精确匹配 `deviceAid` |
| `sensorIdFilter` | 匹配所有传感器 | 大小写不敏感精确匹配 `sensorId` |

### 支持的比较运算符

| operator | 含义 |
|----------|------|
| `>` | 大于 |
| `<` | 小于 |
| `>=` | 大于等于 |
| `<=` | 小于等于 |
| `==` | 等于 |
| `!=` | 不等于 |

评估逻辑在 `Models.Rule.evaluate(double)` 中实现，`RulesEngine` 直接调用。

---

## 4. 动作执行详解

### 4.1 `create_alert`（创建告警）

```java
Models.AlertItem alert = new Models.AlertItem();
alert.deviceAid = message.deviceAid;
alert.sensorId  = reading.sensorId;
alert.level     = reading.value > rule.threshold * 1.5 ? 2 : 1;
alert.message   = "Rule \"" + rule.name + "\": " + reading.sensorId
                  + "=" + reading.value + " " + reading.unit
                  + " " + rule.operator + " " + rule.threshold;
alert.createdMs = System.currentTimeMillis();
repository.insertAlert(alert);
```

**告警等级自动分级**：

| 条件 | level | 含义 |
|------|-------|------|
| `reading.value > rule.threshold * 1.5` | `2` | Danger（严重超阈值 50%） |
| 其他 | `1` | Warning（刚超阈值） |

**告警消息格式**：

```
Rule "TEMP > 50 create_alert": TEMP=65.34 °C > 50.0
```

### 4.2 `send_command`（发送命令）

```java
JSONObject json = new JSONObject(rule.actionPayload);
int targetAid    = json.optInt("target_aid", message.deviceAid);
String sensorId  = json.optString("sensor_id", "CMD");
String unit      = json.optString("unit", "");
double value     = json.optDouble("value", 0.0);
byte[] frame     = PacketBuilder.buildSensorPacket(targetAid, 1,
                       System.currentTimeMillis() / 1000L,
                       sensorId, unit, value);
if (frame != null) transportManager.sendCommand(frame, targetAid, udpHost, udpPort);
```

**`actionPayload` JSON 格式**：

```json
{
    "target_aid": 12345,
    "sensor_id": "CMD",
    "unit": "",
    "value": 1.0
}
```

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `target_aid` | 触发设备的 AID | 命令目标设备 AID |
| `sensor_id` | `"CMD"` | 命令传感器 ID |
| `unit` | `""` | 命令单位 |
| `value` | `0.0` | 命令值 |

**发送路径**：优先通过 MQTT（若已连接），否则通过 UDP 发送。

### 4.3 通用：操作日志

无论动作类型如何，每次触发都写入一条操作日志：

```
action:  "rule_triggered"
details: "Rule \"<name>\" triggered on device=<aid> sensor=<sensorId> action=<actionType>"
```

---

## 5. 冷却机制

- `lastTriggered` 是 `HashMap<Long, Long>`（规则 ID → 上次触发毫秒时间戳），存储在 `RulesEngine` 实例中（**内存级**，应用重启后重置）
- 冷却期间：若 `now - lastTriggered[rule.id] < rule.cooldownMs`，跳过
- 默认冷却：60 秒（`cooldownMs = 60000`），可在规则配置中修改
- **重启后**：冷却状态清空，第一条匹配的消息即可立即触发

---

## 6. 与其他层的交互

```
AppController
    │
    └── RulesEngine.evaluate(message, udpHost, udpPort)
            │
            ├── 读：AppRepository.getEnabledRules()
            ├── 写：AppRepository.insertAlert(alert)        [create_alert]
            ├── 写：AppRepository.logOperation(...)
            └── 发：TransportManager.sendCommand(frame, ...)  [send_command]
                        ├── MQTT: mqttClient.publish(...)
                        └── UDP:  DatagramSocket.send(...)
```

**注意**：`RulesEngine` 不持有对 `AppController` 的引用，不感知 UI 层。

---

## 7. 扩展指引

### 新增动作类型

在 `execute()` 方法的 if-else 链中添加新分支：

```java
} else if ("my_new_action".equals(rule.actionType)) {
    JSONObject json = new JSONObject(rule.actionPayload);
    // ... 自定义逻辑
}
```

### 新增过滤维度

当前仅支持按 `deviceAidFilter` 和 `sensorIdFilter` 过滤。如需按传感器值范围、节点状态等过滤，可在 `evaluate()` 的内层循环中添加额外条件：

```java
// 示例：仅在设备在线时触发
// if (!"online".equals(repository.getDeviceStatus(message.deviceAid))) continue;
```

### 动态规则重载

当前 `getEnabledRules()` 在每次 `evaluate()` 调用时均从数据库查询（保证实时性），如性能有要求可缓存规则列表并在规则变更时主动刷新。

### 持久化冷却状态

如需跨重启保持冷却状态，可将 `lastTriggered` 持久化到 SharedPreferences 或 `operation_logs` 表中，在 `RulesEngine` 构造时恢复。

