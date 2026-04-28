# Gsyn-Java `data` 包开发者文档

> 包路径：`com.opensynaptic.gsynjava.data`  
> 本包是整个应用的**持久化层**，包含数据模型定义、SQLite 数据库创建/升级以及所有 Repository CRUD 操作。  
> 外部代码通过 `AppRepository` 单例访问所有数据；直接操作 `AppDatabaseHelper` 是内部实现细节。

---

## 目录

1. [包结构总览](#1-包结构总览)
2. [Models — 所有数据模型](#2-models--所有数据模型)
3. [AppDatabaseHelper — SQLite DDL 与版本管理](#3-appdatabasehelper--sqlite-ddl-与版本管理)
4. [AppRepository — 数据访问层](#4-apprepository--数据访问层)
5. [数据库表结构参考](#5-数据库表结构参考)
6. [数据流与线程安全](#6-数据流与线程安全)
7. [扩展指引](#7-扩展指引)

---

## 1. 包结构总览

```
data/
├── Models.java            // 所有数据模型（POJO 类）
├── AppDatabaseHelper.java // SQLiteOpenHelper：DDL 建表、版本升级
└── AppRepository.java     // 单例 Repository：CRUD + CSV 导出 + 统计查询
```

---

## 2. Models — 所有数据模型

**文件**：`data/Models.java`  
**性质**：纯数据容器，`final` 外壳类 + 公共静态内部类，字段均为 `public`（无 getter/setter）。

### 2.1 `Device`（设备）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `long` | 数据库主键（`AUTOINCREMENT`） |
| `aid` | `int` | 设备地址 ID（AID），协议唯一标识 |
| `name` | `String` | 设备显示名称（默认 `""`，由 `nodeId` 推导填充） |
| `type` | `String` | 设备类型（默认 `"sensor"`） |
| `lat` | `double` | 纬度（WGS-84，由 GEO 传感器解码填充） |
| `lng` | `double` | 经度（WGS-84） |
| `status` | `String` | 在线状态：`"online"` / `"offline"` |
| `transportType` | `String` | 最后一次接收的传输协议：`"udp"` / `"mqtt"` |
| `lastSeenMs` | `long` | 最后收到消息的 Unix 毫秒时间戳 |

### 2.2 `SensorData`（传感器数据点）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `long` | 数据库主键 |
| `deviceAid` | `int` | 归属设备 AID |
| `sensorId` | `String` | 传感器 ID（如 `"TEMP"`、`"HUM"`） |
| `unit` | `String` | 单位字符串（如 `"°C"`、`"%RH"`） |
| `value` | `double` | 已解码数值（Base62 解码 ÷ 10000） |
| `rawB62` | `String` | 原始 Base62 编码字符串 |
| `timestampMs` | `long` | 数据时间戳（Unix 毫秒，由 `tsSec * 1000` 得到） |

### 2.3 `AlertItem`（告警条目）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `long` | 数据库主键 |
| `deviceAid` | `int` | 触发告警的设备 AID |
| `sensorId` | `String` | 触发告警的传感器 ID |
| `level` | `int` | 告警等级（`1`=Warning，`2`=Danger） |
| `message` | `String` | 人类可读告警描述 |
| `acknowledged` | `boolean` | 是否已确认 |
| `createdMs` | `long` | 告警创建时间戳（Unix 毫秒） |

### 2.4 `Rule`（触发规则）

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `id` | `long` | — | 数据库主键 |
| `name` | `String` | `""` | 规则显示名称 |
| `deviceAidFilter` | `Integer` | `null` | 设备过滤器（null = 匹配所有设备） |
| `sensorIdFilter` | `String` | `null` | 传感器 ID 过滤器（null/空 = 匹配所有） |
| `operator` | `String` | `">"` | 比较运算符（`>`、`<`、`>=`、`<=`、`==`、`!=`） |
| `threshold` | `double` | `0.0` | 阈值 |
| `actionType` | `String` | `"create_alert"` | 动作类型（`"create_alert"` / `"send_command"`） |
| `actionPayload` | `String` | `"{}"` | 动作参数 JSON 字符串 |
| `enabled` | `boolean` | `true` | 是否启用 |
| `cooldownMs` | `long` | `60000` | 触发冷却时间（毫秒，防止重复触发） |

**方法** `evaluate(double sensorValue)`：根据 `operator` 和 `threshold` 对传入值求值，返回 `boolean`。

### 2.5 `OperationLog`（操作日志）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `long` | 数据库主键 |
| `user` | `String` | 操作用户（默认 `"system"`） |
| `action` | `String` | 操作动作字符串（如 `"rule_triggered"`, `"SEED_RULE"`） |
| `details` | `String` | 详细描述文本 |
| `timestampMs` | `long` | 操作时间戳（Unix 毫秒） |

### 2.6 `AppUser`（应用用户）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `long` | 数据库主键 |
| `username` | `String` | 用户名（唯一） |
| `passwordHash` | `String` | SHA-256 密码哈希（十六进制） |
| `role` | `String` | 角色：`"admin"` 或 `"viewer"` |
| `createdMs` | `long` | 创建时间戳（Unix 毫秒） |

**方法** `isAdmin()`：返回 `role.equals("admin")`。

> **注意**：数据库初始化时自动插入 `admin` 用户，密码哈希为 `"admin"` 的 SHA-256（`8c6976e5...`）。生产环境应在首次启动后强制修改密码。

### 2.7 `SensorReading`（传感器读数，临时对象）

由 `BodyParser` 解析后创建，存在于内存中，不直接对应数据库表：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sensorId` | `String` | 传感器 ID |
| `unit` | `String` | 单位 |
| `value` | `double` | 解码值 |
| `state` | `String` | 传感器状态码（`"U"/"A"/"W"/"D"/"O"/"E"`） |
| `rawB62` | `String` | 原始 Base62 字符串（用于 Geohash 等特殊解码） |

### 2.8 `DeviceMessage`（已解码的完整设备消息，临时对象）

由 `TransportManager` 构建后通过 `MessageListener` 分发：

| 字段 | 类型 | 说明 |
|------|------|------|
| `cmd` | `int` | 命令字节码（`OsCmd.*`） |
| `deviceAid` | `int` | 设备 AID |
| `tid` | `int` | 模板/事务 ID |
| `timestampSec` | `long` | 包内时间戳（Unix 秒） |
| `nodeId` | `String` | 节点标识符（header 第一段） |
| `nodeState` | `String` | 节点状态码 |
| `transportType` | `String` | `"udp"` 或 `"mqtt"` |
| `readings` | `List<SensorReading>` | 所有传感器读数列表 |
| `rawFrame` | `byte[]` | 原始二进制帧数据（调试/转发用） |

### 2.9 `PacketMeta`（数据包元数据，临时对象）

由 `PacketDecoder.decode()` 返回：

| 字段 | 说明 |
|------|------|
| `cmd` | 命令字节码 |
| `routeCount` | 路由跳数（通常 1） |
| `aid` | 设备 AID |
| `tid` | 模板 ID |
| `tsSec` | Unix 秒时间戳 |
| `bodyOffset` | body 在原始数组中的起始偏移量（固定 13） |
| `bodyLen` | body 字节长度 |
| `crc8Ok` | body CRC-8 校验是否通过 |
| `crc16Ok` | 全帧 CRC-16 校验是否通过 |

### 2.10 `BodyParseResult`（包体解析结果，临时对象）

| 字段 | 说明 |
|------|------|
| `headerAid` | header 中解析出的 nodeId 字符串 |
| `headerState` | 节点状态码（默认 `"U"`） |
| `tsToken` | 时间戳 Base64URL token |
| `readings` | 传感器读数列表 |

### 2.11 `TransportStats`（传输统计，临时对象）

| 字段 | 说明 |
|------|------|
| `udpConnected` | UDP 监听是否活跃 |
| `mqttConnected` | MQTT 是否已连接 |
| `messagesPerSecond` | 最近 1 秒收到的消息数 |
| `totalMessages` | 运行以来收到的总消息数 |

---

## 3. AppDatabaseHelper — SQLite DDL 与版本管理

**文件**：`data/AppDatabaseHelper.java`  
**父类**：`android.database.sqlite.SQLiteOpenHelper`

| 属性 | 值 |
|------|----|
| 数据库文件名 | `gsyn_java.db` |
| 当前版本 | `1` |

### `onCreate(db)` 创建的表

| 表名 | 说明 |
|------|------|
| `devices` | 设备信息，`aid` 唯一索引 |
| `sensor_data` | 传感器历史数据，`(device_aid, timestamp_ms)` 复合索引 |
| `alerts` | 告警记录，`(device_aid, level)` 复合索引 |
| `rules` | 触发规则 |
| `operation_logs` | 操作审计日志 |
| `users` | 用户账号（含初始 admin 用户） |
| `dashboard_layout` | 仪表盘布局 JSON（用户个性化） |
| `pending_commands` | 待发送命令队列 |

### `onUpgrade(db, oldVersion, newVersion)`

v1 为初始版本，`onUpgrade` 为 no-op。  
**扩展时**请在此方法中添加 `ALTER TABLE` 语句，并递增 `DB_VERSION`。

### 初始数据

`users` 表预置一条管理员记录：
- `username`：`admin`
- `password_hash`：`8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918`（`admin` 的 SHA-256）
- `role`：`admin`

---

## 4. AppRepository — 数据访问层

**文件**：`data/AppRepository.java`  
**模式**：懒加载单例（双重检查锁），所有公共方法 `synchronized`（线程安全）。

```java
AppRepository repo = AppRepository.get(context);
```

### 4.1 设备操作

| 方法 | 说明 |
|------|------|
| `upsertDevice(device)` | 插入或更新设备。若已存在（按 `aid`），保留旧坐标（当新消息不含位置时） |
| `getAllDevices()` | 返回全部设备列表，按 `last_seen_ms` 降序 |
| `getTotalDeviceCount()` | 返回 `devices` 表总行数 |
| `getOnlineDeviceCount()` | 返回最近 5 分钟内有消息的设备数（在线判定阈值） |

**在线判定逻辑**：`last_seen_ms > System.currentTimeMillis() - 5 * 60_000`（5 分钟窗口）

### 4.2 传感器数据操作

| 方法 | 说明 |
|------|------|
| `insertSensorData(data)` | 插入一条传感器数据 |
| `getLatestReadingsByDevice(aid)` | 返回指定设备每个 sensorId 的最新一条读数（子查询 MAX(id) GROUP BY sensor_id） |
| `querySensorData(fromMs, toMs, limit)` | 时间范围内查询，按 `timestamp_ms` 降序，限制行数 |

### 4.3 告警操作

| 方法 | 说明 |
|------|------|
| `insertAlert(alert)` | 插入告警，返回新行 ID |
| `getAlerts(level, limit)` | 按级别（null=全部）查询告警，按 `created_ms` 降序 |
| `getUnacknowledgedAlertCount()` | 返回未确认告警数 |
| `acknowledgeAlert(id)` | 将指定 ID 告警的 `acknowledged` 设为 1 |

### 4.4 规则操作

| 方法 | 说明 |
|------|------|
| `getAllRules()` | 返回全部规则，按 `id` 升序 |
| `getEnabledRules()` | 返回 `enabled=1` 的规则 |
| `saveRule(rule)` | `rule.id > 0` → UPDATE，否则 INSERT；返回规则 ID |
| `toggleRule(ruleId, enabled)` | 切换规则启用状态 |
| `deleteRule(ruleId)` | 删除指定规则 |
| `seedDefaultRuleIfEmpty()` | 若 `rules` 表为空，自动插入默认规则（`TEMP > 50 create_alert`）并写操作日志 |

### 4.5 操作日志

| 方法 | 说明 |
|------|------|
| `logOperation(action, details)` | 插入一条操作日志（`user="system"`，当前时间戳） |
| `getOperationLogs(limit)` | 按 `timestamp_ms` 降序返回最近 N 条操作日志 |

### 4.6 维护工具

| 方法 | 说明 |
|------|------|
| `getDatabaseSizeBytes()` | 返回数据库文件大小（字节） |
| `pruneOldData(retentionDays)` | 删除 `retention_days` 天前的 `sensor_data` 记录，返回删除行数 |
| `exportHistoryCsv()` | 导出最近 24 小时最多 500 条传感器数据为 CSV 文件，保存至 `getExternalFilesDir(null)` |

**CSV 列格式**：`timestamp,device_aid,sensor_id,value,unit`

---

## 5. 数据库表结构参考

### `devices`

```sql
CREATE TABLE devices (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    aid            INTEGER UNIQUE NOT NULL,
    name           TEXT    NOT NULL DEFAULT '',
    type           TEXT    NOT NULL DEFAULT 'sensor',
    lat            REAL    DEFAULT 0.0,
    lng            REAL    DEFAULT 0.0,
    status         TEXT    NOT NULL DEFAULT 'offline',
    transport_type TEXT    NOT NULL DEFAULT 'udp',
    last_seen_ms   INTEGER NOT NULL DEFAULT 0
);
```

### `sensor_data`

```sql
CREATE TABLE sensor_data (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    device_aid   INTEGER NOT NULL,
    sensor_id    TEXT    NOT NULL,
    unit         TEXT    NOT NULL DEFAULT '',
    value        REAL    NOT NULL,
    raw_b62      TEXT    DEFAULT '',
    timestamp_ms INTEGER NOT NULL
);
CREATE INDEX idx_sensor_data_aid_ts ON sensor_data(device_aid, timestamp_ms);
```

### `alerts`

```sql
CREATE TABLE alerts (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    device_aid   INTEGER NOT NULL,
    sensor_id    TEXT    NOT NULL DEFAULT '',
    level        INTEGER NOT NULL DEFAULT 0,
    message      TEXT    NOT NULL DEFAULT '',
    acknowledged INTEGER NOT NULL DEFAULT 0,
    created_ms   INTEGER NOT NULL
);
CREATE INDEX idx_alerts_aid_level ON alerts(device_aid, level);
```

### `rules`

```sql
CREATE TABLE rules (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    name              TEXT    NOT NULL DEFAULT '',
    device_aid_filter INTEGER DEFAULT NULL,
    sensor_id_filter  TEXT    DEFAULT NULL,
    operator          TEXT    NOT NULL DEFAULT '>',
    threshold         REAL    NOT NULL DEFAULT 0.0,
    action_type       TEXT    NOT NULL DEFAULT 'create_alert',
    action_payload    TEXT    NOT NULL DEFAULT '{}',
    enabled           INTEGER NOT NULL DEFAULT 1,
    cooldown_ms       INTEGER NOT NULL DEFAULT 60000
);
```

### `operation_logs`

```sql
CREATE TABLE operation_logs (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user         TEXT    NOT NULL DEFAULT 'system',
    action       TEXT    NOT NULL DEFAULT '',
    details      TEXT    NOT NULL DEFAULT '',
    timestamp_ms INTEGER NOT NULL
);
```

### `users`

```sql
CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    UNIQUE NOT NULL,
    password_hash TEXT    NOT NULL,
    role          TEXT    NOT NULL DEFAULT 'viewer',
    created_ms    INTEGER NOT NULL
);
```

### `dashboard_layout`

```sql
CREATE TABLE dashboard_layout (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER,
    layout_json TEXT NOT NULL DEFAULT '{}'
);
```

### `pending_commands`

```sql
CREATE TABLE pending_commands (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    device_aid INTEGER NOT NULL,
    frame_hex  TEXT    NOT NULL,
    created_ms INTEGER NOT NULL
);
```

---

## 6. 数据流与线程安全

### 写入路径（消息到达 → 持久化）

```
TransportManager 后台线程
  → AppController.onMessage(message)
      ├── repository.upsertDevice(device)      // UPDATE devices SET ...
      └── repository.insertSensorData(data)    // INSERT INTO sensor_data ...
          // 随后
          └── rulesEngine.evaluate(...)
              └── repository.insertAlert(...)  // 可能触发
```

### 读取路径（UI 层定时查询）

```
主线程（或 Fragment 的 ScheduledExecutorService）
  → repository.getAllDevices()          // UI 列表刷新
  → repository.getLatestReadingsByDevice(aid)  // 传感器卡片
  → repository.getAlerts(null, 50)      // 告警列表
  → repository.getOperationLogs(100)   // 日志页面
```

### 线程安全保证

所有 `AppRepository` 公共方法均标注 `synchronized`，使用同一个 `SQLiteOpenHelper` 实例，SQLite WAL 模式默认未开启（v1 保持兼容性）。

> **最佳实践**：在 Fragment 中应通过 `ExecutorService` 或 ViewModel 在后台线程调用 Repository 方法，避免在主线程执行数据库操作。

---

## 7. 扩展指引

### 新增数据表

1. 在 `AppDatabaseHelper.onCreate()` 添加 `CREATE TABLE` 语句
2. 递增 `DB_VERSION`，在 `onUpgrade()` 中添加迁移 SQL
3. 在 `Models.java` 中添加对应的 POJO 类
4. 在 `AppRepository` 中添加对应的 map*(Cursor)、insert/query 方法

### 新增 Rule 动作类型

1. 在 `RulesEngine.execute()` 中添加 `else if ("new_action_type".equals(...))` 分支
2. 在 `RulesMirrorFragment` UI 中添加对应的动作类型选项
3. `actionPayload` 为 JSON 字符串，可在新分支中用 `JSONObject` 解析任意参数

### 修改在线判定时间窗口

`getOnlineDeviceCount()` 中硬编码了 `5 * 60_000`（5 分钟），如需配置化可改为从 `SharedPreferences` 读取。

### 数据导出扩展

`exportHistoryCsv()` 当前仅导出最近 24 小时 / 500 条数据。可添加参数化版本以支持自定义时间范围和更多字段。

