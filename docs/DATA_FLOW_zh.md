# 資料流程：感測器到畫面

> English version: [DATA_FLOW.md](DATA_FLOW.md)

本文件追蹤資料從 UDP 封包抵達網路介面的那一刻，到數值顯示在儀表板上的完整路徑。

理解此流程是除錯任何資料相關問題，或為新封包類型添加支援的最快方式。

---

## 完整路徑

```
[實體感測器裝置]
        │
        │  UDP 數據報（二進位，OpenSynaptic 協議）
        ▼
[Android 網路堆疊]
        │
        ▼
TransportManager.UdpThread.run()
        │  DatagramSocket.receive(packet)
        │  byte[] raw = packet.getData()
        ▼
PacketDecoder.decode(raw)
        │  1. 檢查最小長度（9 位元組標頭）
        │  2. 對 bytes[0..len-2] 驗證 CRC-8
        │  3. 解析標頭欄位：CMD、AID、TID、SEQ、LEN
        │  4. 提取 body = raw[header_size .. header_size+LEN]
        │  回傳：DecodeResult { valid, cmd, aid, tid, seq, rawBody }
        ▼
DiffEngine.process(cmd, aid, rawBody)
        │  CMD = DATA_FULL  → 解析所有 (sensorId, value, unit) 三元組
        │                     儲存為 template[aid] 供後續 DIFF 使用
        │  CMD = DATA_DIFF  → 對 template[aid] 套用差異位元組
        │                     回傳完整重建的讀數
        │  CMD = HEARTBEAT  → 回傳最後已知讀數（template[aid]）
        │  回傳：List<SensorReading> { sensorId, value, unit }
        ▼
AppRepository
        │  upsertDevice(aid, transport="udp", status="online", lastSeenMs=now())
        │  insertSensorDataBatch(aid, readings)
        │    └── INSERT INTO sensor_data (device_aid, sensor_id, value, unit, timestamp_ms)
        │    └── DELETE FROM sensor_data WHERE timestamp_ms < now() - 7天（自動清除）
        ▼
RulesEngine.evaluate(aid, readings)
        │  對每條啟用規則（rule.sensorIdFilter 匹配 sensorId）：
        │    評估：reading.value OP rule.threshold
        │      （OP = >、<、>=、<=","==")
        │    若為真且 (now - rule.lastFiredMs) > rule.cooldownSeconds * 1000：
        │      rule.lastFiredMs = now
        │      switch (rule.actionType):
        │        "create_alert" → repository.insertAlert(aid, level, message)
        │        "send_command" → transportManager.sendUdp(cmd bytes, target)
        │        "log_only"     → repository.logOperation(action, details)
        ▼
TransportManager.notifyListeners(DeviceMessage)
        │  對每個已註冊的 MessageListener 呼叫 onMessage(msg)
        │  （在 udpThread 上執行 — 背景執行緒！）
        ▼
DashboardFragment.onMessage(DeviceMessage)           [在背景執行緒呼叫]
        │  getActivity().runOnUiThread(this::refresh)
        ▼
DashboardFragment.refresh()                          [現在在 UI 執行緒]
        │  repository.getTotalDeviceCount()           → snap.totalDevices
        │  repository.getOnlineDeviceCount()          → snap.online
        │  repository.getUnacknowledgedAlertCount()   → snap.alerts
        │  repository.querySensorData(last24h, 50)    → snap.latestBySensorId
        │                                               snap.latestTemp、snap.latestHum ...
        │                                               snap.tempTrend、snap.humTrend
        │  cardAdapter.setSnapshot(snap)              → RecyclerView 重新綁定可見卡片
        ▼
[使用者在畫面上看到更新後的數值]
```

---

## 代碼位置

| 步驟 | 文件 | 關鍵方法 |
|------|------|---------|
| 接收 UDP | `TransportManager.java` | `UdpThread.run()` |
| 解碼封包 | `core/protocol/PacketDecoder.java` | `decode(byte[])` |
| 重建差異 | `core/protocol/DiffEngine.java` | `process(cmd, aid, body)` |
| 持久化資料 | `data/AppRepository.java` | `insertSensorDataBatch()` |
| 評估規則 | `rules/RulesEngine.java` | `evaluate(aid, readings)` |
| 通知 UI | `transport/TransportManager.java` | `notifyMessageListeners()` |
| 刷新畫面 | `ui/dashboard/DashboardFragment.java` | `refresh()` |
| 綁定視圖 | `ui/dashboard/DashboardCardAdapter.java` | `onBindViewHolder()` |

---

## MQTT 路徑（略有不同）

MQTT 使用 Eclipse Paho 函式庫，透過 `MqttCallback.messageArrived()` 接收：

```
MqttClient 回調執行緒
    │
    ▼
TransportManager.messageArrived(topic, mqttMsg)
    │  payload = mqttMsg.getPayload()  // byte[]
    │  （與 UDP 相同的解碼 → 儲存 → 通知管道）
    ▼
PacketDecoder.decode(payload)
    │  ...（從此處開始完全相同）
```

目前主題字串不用於路由 — 所有 MQTT 訊息均使用相同的解碼器處理，與主題無關。

---

## 資料發送流程（發送頁面）

```
使用者填寫 SendFragment UI
        │
        ▼
PacketBuilder.buildDataFull(aid, tid, seq, readings)
        │  構建：[CMD][AID][TID][SEQ][LEN][...body...][CRC8]
        │  回傳：byte[]
        ▼
TransportManager.sendUdp(bytes, host, port)
        │  new DatagramSocket().send(new DatagramPacket(bytes, host, port))
        │  （在呼叫執行緒執行 — 單次短發送在 UI 執行緒可行）
        ▼
TransportManager.notifyListeners(DeviceMessage)
        │  發送的封包被解碼並本地儲存
        │  因此儀表板立即反映發出的資料
```

---

## 資料庫結構

```sql
-- 裝置
CREATE TABLE devices (
    aid           INTEGER PRIMARY KEY,
    name          TEXT,
    device_type   TEXT,
    status        TEXT,         -- "online" | "offline"
    transport_type TEXT,        -- "udp" | "mqtt"
    geohash       TEXT,
    lat           REAL,
    lng           REAL,
    last_seen_ms  INTEGER
);

-- 感測器讀數（時間序列，7 天滾動視窗）
CREATE TABLE sensor_data (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    device_aid    INTEGER,
    sensor_id     TEXT,
    value         REAL,
    unit          TEXT,
    timestamp_ms  INTEGER
);

-- 告警日誌
CREATE TABLE alerts (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    device_aid    INTEGER,
    level         INTEGER,      -- 0=資訊, 1=警告, 2=嚴重
    message       TEXT,
    acknowledged  INTEGER,      -- 0 | 1
    created_ms    INTEGER
);

-- 自動化規則
CREATE TABLE rules (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT,
    sensor_id_filter TEXT,
    operator        TEXT,       -- ">"、"<"、">="、"<="、"=="
    threshold       REAL,
    action_type     TEXT,       -- "create_alert" | "send_command" | "log_only"
    target_aid      INTEGER,
    cooldown_seconds INTEGER,
    enabled         INTEGER     -- 0 | 1
);

-- 操作日誌
CREATE TABLE operation_logs (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    action       TEXT,
    details      TEXT,
    timestamp_ms INTEGER
);
```

---

## 常見除錯問題

### 「儀表板上看不到任何資料」

1. 檢查設定 → UDP 是否已啟用？端口是否正確？
2. 確認裝置發送至 Android 裝置的 IP（而非 `127.0.0.1`）
3. 在 `PacketDecoder.decode()` 中新增日誌確認封包是否到達：
   ```java
   Log.d("PACKET", "received " + raw.length + " bytes, CRC valid: " + result.valid);
   ```
4. 若 CRC 驗證失敗，封包會被靜默丟棄 — 確認 CRC-8 多項式一致

### 「規則觸發但沒有告警出現」

1. 檢查 `rule.cooldownSeconds` — 若規則最近已觸發，將等到冷卻期結束才再次觸發
2. 檢查 `rule.enabled` — 規則可被切換關閉
3. 在 `RulesEngine.evaluate()` 中新增日誌追蹤比較過程

### 「CUSTOM_SENSOR 卡片不顯示資料」

1. 確認輸入的感測器 ID 與裝置發送的完全一致（已套用不區分大小寫比較，但尾隨空格會導致匹配失敗）
2. 檢查 `snap.latestBySensorId` — 在 `DashboardFragment.refresh()` 中新增日誌：
   ```java
   Log.d("SNAP", "latestBySensorId keys: " + snap.latestBySensorId.keySet());
   ```

### 「地圖上不顯示位置」

1. 確認裝置發送 `GEO` 或 `LAT`/`LNG` 感測器 ID
2. `MapMirrorFragment` 在 `devices` 表中查找 `geohash` 欄位
3. 封包處理期間必須呼叫 `AppRepository.updateDeviceGeo()` — 確認感測器 ID 名稱與 `TransportManager` 中的預期值一致
