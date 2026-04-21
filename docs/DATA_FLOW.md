# Data Flow — Sensor to Screen

This document traces the complete path data takes from the moment a UDP packet arrives on the network interface to the moment a value appears on the Dashboard.

Understanding this flow is the fastest way to debug any data-related issue, or to add support for a new packet type.

---

## The Full Journey

```
[Physical Sensor Device]
        │
        │  UDP datagram (binary, OpenSynaptic protocol)
        ▼
[Android Network Stack]
        │
        ▼
TransportManager.UdpThread.run()
        │  DatagramSocket.receive(packet)
        │  byte[] raw = packet.getData()
        ▼
PacketDecoder.decode(raw)
        │  1. Check minimum length (9 bytes header)
        │  2. Validate CRC-8 over bytes[0..len-2]
        │  3. Parse header fields: CMD, AID, TID, SEQ, LEN
        │  4. Extract body = raw[header_size .. header_size+LEN]
        │  Returns: DecodeResult { valid, cmd, aid, tid, seq, rawBody }
        ▼
DiffEngine.process(cmd, aid, rawBody)
        │  CMD = DATA_FULL  → parse all (sensorId, value, unit) triplets
        │                     store as template[aid] for future DIFF
        │  CMD = DATA_DIFF  → apply delta bytes to template[aid]
        │                     return complete reconstructed readings
        │  CMD = HEARTBEAT  → return last known readings (template[aid])
        │  Returns: List<SensorReading> { sensorId, value, unit }
        ▼
AppRepository
        │  upsertDevice(aid, transport="udp", status="online", lastSeenMs=now())
        │  insertSensorDataBatch(aid, readings)
        │    └── INSERT INTO sensor_data (device_aid, sensor_id, value, unit, timestamp_ms)
        │    └── DELETE FROM sensor_data WHERE timestamp_ms < now() - 7days  (auto-prune)
        ▼
RulesEngine.evaluate(aid, readings)
        │  For each enabled Rule where rule.sensorIdFilter matches sensorId:
        │    Evaluate: reading.value OP rule.threshold
        │      (OP = >, <, >=, <=, ==)
        │    If true AND (now - rule.lastFiredMs) > rule.cooldownSeconds * 1000:
        │      rule.lastFiredMs = now
        │      switch (rule.actionType):
        │        "create_alert" → repository.insertAlert(aid, level, message)
        │        "send_command" → transportManager.sendUdp(cmd bytes, target)
        │        "log_only"     → repository.logOperation(action, details)
        ▼
TransportManager.notifyListeners(DeviceMessage)
        │  Calls onMessage(msg) on every registered MessageListener
        │  (runs on the udpThread — background!)
        ▼
DashboardFragment.onMessage(DeviceMessage)           [called on background thread]
        │  getActivity().runOnUiThread(this::refresh)
        ▼
DashboardFragment.refresh()                          [now on UI thread]
        │  repository.getTotalDeviceCount()           → snap.totalDevices
        │  repository.getOnlineDeviceCount()          → snap.online
        │  repository.getUnacknowledgedAlertCount()   → snap.alerts
        │  repository.querySensorData(last24h, 50)    → snap.latestBySensorId
        │                                               snap.latestTemp, snap.latestHum ...
        │                                               snap.tempTrend, snap.humTrend
        │  cardAdapter.setSnapshot(snap)              → RecyclerView rebinds visible cards
        ▼
[User sees updated values on screen]
```

---

## Code Locations

| Step | File | Key Method |
|------|------|-----------|
| Receive UDP | `TransportManager.java` | `UdpThread.run()` |
| Decode packet | `core/protocol/PacketDecoder.java` | `decode(byte[])` |
| Reconstruct diff | `core/protocol/DiffEngine.java` | `process(cmd, aid, body)` |
| Persist data | `data/AppRepository.java` | `insertSensorDataBatch()` |
| Evaluate rules | `rules/RulesEngine.java` | `evaluate(aid, readings)` |
| Notify UI | `transport/TransportManager.java` | `notifyMessageListeners()` |
| Refresh screen | `ui/dashboard/DashboardFragment.java` | `refresh()` |
| Bind to view | `ui/dashboard/DashboardCardAdapter.java` | `onBindViewHolder()` |

---

## MQTT Path (slightly different)

MQTT uses the Eclipse Paho library and arrives via `MqttCallback.messageArrived()`:

```
MqttClient callback thread
    │
    ▼
TransportManager.messageArrived(topic, mqttMsg)
    │  payload = mqttMsg.getPayload()  // byte[]
    │  (same decode → store → notify pipeline as UDP above)
    ▼
PacketDecoder.decode(payload)
    │  ... (identical from here)
```

The topic string is currently not used for routing — all MQTT messages are processed with the same decoder regardless of topic.

---

## Outgoing Data Flow (Send page)

```
User fills in SendFragment UI
        │
        ▼
PacketBuilder.buildDataFull(aid, tid, seq, readings)
        │  Constructs: [CMD][AID][TID][SEQ][LEN][...body...][CRC8]
        │  Returns: byte[]
        ▼
TransportManager.sendUdp(bytes, host, port)
        │  new DatagramSocket().send(new DatagramPacket(bytes, host, port))
        │  (runs on calling thread — UI thread is OK for a single short send)
        ▼
TransportManager.notifyListeners(DeviceMessage)
        │  The sent packet is decoded and stored locally
        │  so the Dashboard immediately reflects the outgoing data
```

---

## Database Schema

```sql
-- Devices
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

-- Sensor readings (time-series, 7-day rolling window)
CREATE TABLE sensor_data (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    device_aid    INTEGER,
    sensor_id     TEXT,
    value         REAL,
    unit          TEXT,
    timestamp_ms  INTEGER
);

-- Alert log
CREATE TABLE alerts (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    device_aid    INTEGER,
    level         INTEGER,      -- 0=info, 1=warning, 2=critical
    message       TEXT,
    acknowledged  INTEGER,      -- 0 | 1
    created_ms    INTEGER
);

-- Automation rules
CREATE TABLE rules (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT,
    sensor_id_filter TEXT,
    operator        TEXT,       -- ">", "<", ">=", "<=", "=="
    threshold       REAL,
    action_type     TEXT,       -- "create_alert" | "send_command" | "log_only"
    target_aid      INTEGER,
    cooldown_seconds INTEGER,
    enabled         INTEGER     -- 0 | 1
);

-- Operation log
CREATE TABLE operation_logs (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    action       TEXT,
    details      TEXT,
    timestamp_ms INTEGER
);
```

---

## Common Debugging Questions

### "I'm not seeing any data on the Dashboard"

1. Check Settings → is UDP enabled? Is the port correct?
2. Check that your device sends to the Android device's IP (not `127.0.0.1`)
3. Add a log in `PacketDecoder.decode()` to confirm packets are arriving:
   ```java
   Log.d("PACKET", "received " + raw.length + " bytes, CRC valid: " + result.valid);
   ```
4. If CRC fails, the packet is silently dropped — verify CRC-8 polynomial match

### "Rule fires but no alert appears"

1. Check `rule.cooldownSeconds` — if a rule fired recently it won't fire again until the cooldown expires
2. Check `rule.enabled` — rules can be toggled off
3. Add a log in `RulesEngine.evaluate()` to trace the comparison

### "CUSTOM_SENSOR card shows no data"

1. Verify the Sensor ID you entered matches exactly what the device sends (case-insensitive comparison is applied, but trailing spaces will break it)
2. Check `snap.latestBySensorId` — add a log in `DashboardFragment.refresh()`:
   ```java
   Log.d("SNAP", "latestBySensorId keys: " + snap.latestBySensorId.keySet());
   ```

### "Location not showing on Map"

1. Ensure the device sends `GEO` or `LAT`/`LNG` sensor IDs
2. `MapMirrorFragment` looks for `geohash` column in the `devices` table
3. `AppRepository.updateDeviceGeo()` must be called during packet processing — check that the sensor ID name matches what's expected in `TransportManager`

