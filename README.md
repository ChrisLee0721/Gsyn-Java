# Gsyn Java — OpenSynaptic Telemetry Console (Android)

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%2024%2B-brightgreen?logo=android" />
  <img src="https://img.shields.io/badge/Language-Java%2017-orange?logo=java" />
  <img src="https://img.shields.io/badge/Maps-Google%20Maps%20SDK-blue?logo=googlemaps" />
  <img src="https://img.shields.io/badge/Transport-UDP%20%7C%20MQTT-blueviolet" />
  <img src="https://img.shields.io/badge/License-MIT-lightgrey" />
</p>

> A full-featured **Android native Java** mirror of the [OpenSynaptic/Gsyn](https://github.com/OpenSynaptic/Gsyn) Flutter dashboard.  
> Receives, decodes, stores, visualizes, and sends Gsyn binary protocol packets — all on-device.

---

## Table of Contents

- [Features](#features)
- [Architecture Overview](#architecture-overview)
- [Protocol Layer](#protocol-layer)
- [Data Layer](#data-layer)
- [Transport Layer](#transport-layer)
- [Rules Engine](#rules-engine)
- [UI Layer](#ui-layer)
- [Google Maps Setup](#google-maps-setup)
- [Getting Started](#getting-started)
- [Build & Test](#build--test)
- [Release & CI/CD](#release--cicd)
- [Configuration Reference](#configuration-reference)
- [Differences from Flutter Source](#differences-from-flutter-source)
- [Unit Tests](#unit-tests)
- [Contributing](#contributing)

---

## Documentation Index

| Document | Audience | Contents |
|----------|----------|----------|
| [docs/GETTING_STARTED.md](docs/GETTING_STARTED.md) | All developers | Setup, project structure, first run |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Mid–Senior | Layered design, threading, theming |
| [docs/DATA_FLOW.md](docs/DATA_FLOW.md) | All | UDP → decode → DB → UI complete trace |
| [docs/PROTOCOL.md](docs/PROTOCOL.md) | Protocol devs | Binary packet format, CRC, Base62 |
| [docs/DASHBOARD_CARDS.md](docs/DASHBOARD_CARDS.md) | UI contributors | Card system, drag reorder, custom sensors |
| [docs/UI_PATTERNS.md](docs/UI_PATTERNS.md) | Junior devs / students | ViewBinding, theming, i18n, RecyclerView |
| [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) | Contributors | Branch convention, checklist, pitfalls |

---

## Features

| Feature | Detail |
|---------|--------|
| 📡 **UDP Transport** | Bidirectional UDP socket — listen on configurable port, send packets to any IP:port |
| 🔗 **MQTT Transport** | Eclipse Paho v3 client — subscribe/publish with TLS support |
| 🔒 **Binary Protocol** | Full Gsyn packet codec: CRC-8/CRC-16 validation, Base62 encoding, FULL/DIFF/HEART frames |
| 🗺️ **Google Maps** | Live device markers with online/offline colour coding, satellite/hybrid/normal layers |
| 📊 **Real-time Charts** | Native Canvas `MiniTrendChartView` — temperature & humidity trends |
| 🚨 **Alerts** | Three-level alert system (Info / Warning / Critical) with acknowledgement |
| ⚙️ **Rules Engine** | Threshold-based automation: create alerts, send commands, or log events |
| 🎨 **Theming** | Multiple accent colours + background presets, dark/light mode support |
| 🗄️ **SQLite Persistence** | Full local database: devices, sensor data, alerts, rules, operation logs |
| 📤 **CSV Export** | One-tap sensor history export |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  MainActivity · DashboardFragment · DevicesFragment          │
│  AlertsFragment · SendFragment · SettingsFragment            │
│  MapMirrorFragment · HistoryMirrorFragment                   │
│  RulesMirrorFragment · HealthMirrorFragment                  │
└────────────────────────┬────────────────────────────────────┘
                         │ observes / calls
┌────────────────────────▼────────────────────────────────────┐
│                    AppController (Singleton)                  │
│  Coordinates: Repository ↔ TransportManager ↔ RulesEngine   │
└───────┬─────────────────┬──────────────────┬────────────────┘
        │                 │                  │
┌───────▼──────┐  ┌───────▼──────┐  ┌───────▼──────┐
│ AppRepository│  │TransportMgr  │  │ RulesEngine  │
│ (SQLite CRUD)│  │(UDP + MQTT)  │  │ (Thresholds) │
└───────┬──────┘  └───────┬──────┘  └──────────────┘
        │                 │
┌───────▼──────────────────▼──────┐
│          Protocol Layer          │
│  PacketDecoder · PacketBuilder   │
│  BodyParser · DiffEngine         │
│  OsCmd · OsCrc · Base62Codec     │
│  ProtocolConstants · Geohash     │
└──────────────────────────────────┘
```

---

## Protocol Layer

> `app/src/main/java/com/opensynaptic/gsynjava/core/protocol/`

### Packet Structure

Every Gsyn wire packet follows this fixed header layout:

```
Byte  0      : CMD   (command byte, see OsCmd)
Byte  1      : AID   (source Application ID, uint8)
Byte  2      : TID   (target Application ID, uint8)
Byte  3      : SEQ   (sequence number, uint8)
Byte  4      : LEN   (body length, uint8)
Bytes 5..N   : BODY  (variable, command-dependent)
Byte  N+1    : CRC8  (CRC-8/SMBUS of bytes 0..N)
```

### Command Reference — `OsCmd.java`

| Constant | Hex | Category | Description |
|----------|-----|----------|-------------|
| `PING` | `0x01` | Control | Heartbeat request |
| `PONG` | `0x02` | Control | Heartbeat reply |
| `ID_REQUEST` | `0x03` | Control | Node requests an AID assignment |
| `ID_ASSIGN` | `0x05` | Control | Master assigns an AID to a node |
| `TIME_REQUEST` | `0x07` | Control | Node requests UNIX timestamp |
| `TIME_RESPONSE` | `0x08` | Control | Master sends 4-byte UNIX time |
| `HANDSHAKE_ACK` | `0x09` | Control | Handshake accepted |
| `HANDSHAKE_NACK` | `0x0A` | Control | Handshake rejected |
| `SECURE_DICT_READY` | `0x10` | Secure | Encryption dictionary is ready |
| `DATA_FULL` | `0x20` | Data | Full sensor frame (all sensors) |
| `DATA_DIFF` | `0x21` | Data | Differential update frame |
| `DATA_HEART` | `0x22` | Data | Heartbeat data (reuses template) |
| `DATA_FULL_SENSOR` | `0x23` | Data | Single-sensor FULL frame |

Helper predicates: `OsCmd.isDataCmd(cmd)` · `OsCmd.isSecureCmd(cmd)` · `OsCmd.normalizeDataCmd(cmd)`

### CRC — `OsCrc.java`

| Algorithm | Poly | Init | Use |
|-----------|------|------|-----|
| CRC-8/SMBUS | `0x07` | `0x00` | Packet integrity (every packet) |
| CRC-16/CCITT-FALSE | `0x1021` | `0xFFFF` | Secure payload validation |

### Base62 Codec — `Base62Codec.java`

Compact encoding for sensor values and timestamps embedded in packet bodies.

```java
String encoded = Base62Codec.encode(value);          // uint32 → ≤6 chars
long   decoded = Base62Codec.decode(encoded);

String sv = Base62Codec.encodeSensorValue(25.6f, "°C"); // float → scaled Base62
String ts = Base62Codec.encodeTimestamp(System.currentTimeMillis()); // 8-char Base64url
```

### Body Parser — `BodyParser.java`

Parses `DATA_FULL` / `DATA_FULL_SENSOR` body bytes into `List<SensorReading>`.

```
Body format (DATA_FULL, one line per sensor):
  [sensorId:2B][unit:1B][state:1B][value:Base62]\n
```

### Packet Decoder — `PacketDecoder.java`

```java
PacketDecoder.Result r = PacketDecoder.decode(rawBytes);
if (r.valid) {
    byte cmd = r.meta.cmd;
    List<SensorReading> readings = r.sensorReadings;
}
```

### Packet Builder — `PacketBuilder.java`

```java
PacketBuilder.buildPing(aid, tid, seq);
PacketBuilder.buildPong(aid, tid, seq);
PacketBuilder.buildIdRequest(aid, tid, seq);
PacketBuilder.buildIdAssign(aid, tid, seq, assignedId);
PacketBuilder.buildTimeRequest(aid, tid, seq);
PacketBuilder.buildDataFullSensor(aid, tid, seq, sensorId, unit, state, value);
PacketBuilder.buildRawHex("01 00 00 00 01 01 00");
```

### Diff Engine — `DiffEngine.java`

Template-based compression for DIFF and HEART frames.

```
FULL  → stored as per-AID template
DIFF  → only changed fields; engine reconstructs full reading list
HEART → no delta; engine re-emits the last FULL template
```

### Protocol Constants — `ProtocolConstants.java`

Canonical sensor IDs, unit codes, state codes, and `defaultUnitFor(sensorId)` lookup.

### Geohash Decoder — `GeohashDecoder.java`

```java
double[] latLng = GeohashDecoder.decode("wtw3ew5"); // → [31.23, 121.47]
```

---

## Data Layer

> `app/src/main/java/com/opensynaptic/gsynjava/data/`

### Models — `Models.java`

| Model | Key Fields |
|-------|-----------|
| `Device` | `aid, name, status, lat, lng, lastSeenMs, transportType` |
| `SensorData` | `deviceAid, sensorId, value, unit, state, timestampMs` |
| `AlertItem` | `id, deviceAid, level (0/1/2), message, createdMs, acknowledged` |
| `Rule` | `id, sensorId, condition, threshold, action, enabled, cooldownMs` |
| `OperationLog` | `id, action, details, timestampMs` |
| `SensorReading` | `sensorId, unit, state, value` — protocol-level (pre-DB) |
| `DeviceMessage` | `meta + readings` — fully decoded packet |
| `TransportStats` | `udpConnected, mqttConnected, messagesPerSecond, totalMessages` |

### Database Schema — `AppDatabaseHelper.java` (SQLite v1)

```sql
CREATE TABLE devices      (aid INTEGER PRIMARY KEY, name TEXT, status TEXT,
                           lat REAL, lng REAL, last_seen_ms INTEGER, transport_type TEXT);
CREATE TABLE sensor_data  (id INTEGER PRIMARY KEY AUTOINCREMENT,
                           device_aid INTEGER, sensor_id TEXT, value REAL,
                           unit TEXT, state INTEGER, timestamp_ms INTEGER);
CREATE TABLE alerts       (id INTEGER PRIMARY KEY AUTOINCREMENT,
                           device_aid INTEGER, sensor_id TEXT, level INTEGER,
                           message TEXT, created_ms INTEGER, acknowledged INTEGER DEFAULT 0);
CREATE TABLE rules        (id INTEGER PRIMARY KEY AUTOINCREMENT,
                           sensor_id TEXT, condition TEXT, threshold REAL,
                           action TEXT, enabled INTEGER DEFAULT 1, cooldown_ms INTEGER DEFAULT 60000);
CREATE TABLE operation_logs (id INTEGER PRIMARY KEY AUTOINCREMENT,
                             action TEXT, details TEXT, timestamp_ms INTEGER);
```

### Repository API — `AppRepository.java`

```java
// Devices
repo.upsertDevice(device);
repo.getAllDevices();
repo.getTotalDeviceCount();
repo.getOnlineDeviceCount();

// Sensor data (auto-trims data older than 7 days)
repo.insertSensorDataBatch(aid, readings);
repo.querySensorData(fromMs, toMs, limit);
repo.exportSensorDataCsv(fromMs, toMs);

// Alerts
repo.insertAlert(alert);
repo.acknowledgeAlert(alertId);
repo.getUnacknowledgedAlertCount();
repo.getAlerts(sensorId /*null=all*/, limit);

// Rules
repo.insertRule(rule);  repo.updateRule(rule);  repo.deleteRule(id);
repo.getEnabledRules();

// Audit log
repo.logOperation(action, details);
repo.getOperationLogs(limit);
```

---

## Transport Layer

> `app/src/main/java/com/opensynaptic/gsynjava/transport/TransportManager.java`

### UDP

Binds to `0.0.0.0:<port>` on a dedicated background thread. Sends via `DatagramSocket`.

```java
tm.startUdp(9876);
tm.sendUdp(bytes, "192.168.1.100", 9876);
tm.stopUdp();
```

### MQTT (Eclipse Paho v3)

```java
tm.startMqtt("tcp://broker:1883", "user", "pass");
tm.stopMqtt();
```

Subscribes to `gsyn/#`. Publishes outgoing to `gsyn/out/<aid>`.

### Message Pipeline

```
Raw bytes (UDP / MQTT)
  → PacketDecoder.decode()        CRC validation
  → DiffEngine.process()          DIFF/HEART reconstruction
  → AppRepository.upsertDevice()
  → AppRepository.insertSensorDataBatch()
  → RulesEngine.evaluate()        threshold automation
  → MessageListener.onMessage()   UI refresh callbacks
```

### Registering Listeners

```java
tm.addMessageListener(msg -> { /* DeviceMessage */ });
tm.addStatsListener(stats -> { /* TransportStats every 1 s */ });
tm.removeMessageListener(listener);
tm.removeStatsListener(listener);
```

---

## Rules Engine

> `app/src/main/java/com/opensynaptic/gsynjava/rules/RulesEngine.java`

Evaluated automatically after every incoming data batch. Rule fields:

| Field | Values | Meaning |
|-------|--------|---------|
| `sensorId` | `"TEMP"`, `"HUM"`, … | Sensor to watch |
| `condition` | `">"` `">="` `"<"` `"<="` `"=="` | Comparison operator |
| `threshold` | `double` | Trigger value |
| `action` | `"create_alert"` / `"send_command"` / `"log_only"` | Effect |
| `cooldownMs` | `long` (default 60 000) | Min ms between repeated triggers |

---

## UI Layer

### Navigation Structure

```
MainActivity  (DrawerLayout)
├── BottomNavigationView
│   ├── Dashboard      — KPI + trends + gauges
│   ├── Devices        — list + search + detail sheet
│   ├── Alerts         — filter by severity, ACK
│   ├── Send           — command builder (3 tabs)
│   └── Settings       — UDP / MQTT config
└── Side Drawer (in-app fragments)
    ├── Map            — Google Maps device markers
    ├── History        — 24h sensor table + CSV export
    ├── Rules          — rule CRUD + toggle
    └── Health         — transport status + DB stats
```

### MiniTrendChartView

```java
chart.setTitle("Temperature");
chart.setChartColor(0xFFFF7043);
chart.setSeries(List.of(22f, 23.5f, 25f, 24f));
```

Gradient fill, grid lines, peak/min highlight dots — all pure Canvas, no 3rd-party chart lib.

### Theming

```java
// In Activity.onCreate() BEFORE super.onCreate()
getTheme().applyStyle(AppThemeConfig.getAccentOverlayRes(accentPreset), true);
getTheme().applyStyle(AppThemeConfig.getBgOverlayRes(bgPreset), true);

// After setContentView
AppThemeConfig.applyBgToWindow(getWindow(), this);
```

---

## Google Maps Setup

### 1 — Enable the API

In [Google Cloud Console](https://console.cloud.google.com):
- Enable **Maps SDK for Android**
- Create or select an API Key

### 2 — Restrict the Key (recommended)

Add an **Android app** restriction with:
- **Package name**: `com.opensynaptic.gsynjava`
- **SHA-1** (get your debug key):
  ```bash
  keytool -list -v -keystore ~/.android/debug.keystore \
          -alias androiddebugkey -storepass android -keypass android
  ```

### 3 — Set the key

`local.properties` (never commit):
```properties
MAPS_API_KEY=YOUR_KEY_HERE
```

> The debug build intentionally omits the `.debug` package suffix so the same key works for both debug and release builds.

---

## Getting Started

### Prerequisites

| Tool | Minimum |
|------|---------|
| Android Studio | Hedgehog 2023.1+ |
| JDK | 17 |
| Android SDK | API 24 |
| Target SDK | API 34 |
| Google Play Services | Required on device/emulator |

### Clone

```bash
git clone https://github.com/ChrisLee0721/Gsyn-Java.git
cd Gsyn-Java
```

Open in **Android Studio** → File → Open → select this folder.

Create `local.properties`:
```properties
sdk.dir=/path/to/Android/Sdk
MAPS_API_KEY=YOUR_KEY
```

---

## Build & Test

```powershell
# Unit tests
.\gradlew.bat :app:testDebugUnitTest

# Debug APK
.\gradlew.bat :app:assembleDebug

# Install to device
.\gradlew.bat :app:installDebug
```

---

## Unit Tests

```
✅ base62_roundtrip
✅ timestamp_roundtrip
✅ packet_build_and_decode
✅ body_parse_sensor
✅ diff_engine_full_heart_roundtrip
✅ diff_engine_clear
✅ protocol_constants_default_unit
✅ os_cmd_is_secure
```

---

## Release & CI/CD

```bash
git tag v1.2.0
git push origin v1.2.0   # triggers GitHub Actions → signed APK → GitHub Release
```

Required **GitHub Secrets**:

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Release keystore (Base64) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |
| `MAPS_API_KEY` | Google Maps API key |

See [RELEASE.md](./RELEASE.md) for full instructions.

---

## Configuration Reference

### SharedPreferences Keys

| Key | Default | Description |
|-----|---------|-------------|
| `udp_port` | `9876` | UDP listen port |
| `udp_enabled` | `false` | Auto-start UDP on launch |
| `mqtt_broker` | `""` | Broker URL (`tcp://host:1883`) |
| `mqtt_user` | `""` | MQTT username |
| `mqtt_pass` | `""` | MQTT password |
| `mqtt_enabled` | `false` | Auto-connect MQTT on launch |
| `theme_preset` | `DEFAULT` | Accent colour |
| `bg_preset` | `DEFAULT` | Background preset |

### Dashboard Card Visibility

| Key | Default | Card |
|-----|---------|------|
| `dash_kpi_row1` | `true` | Total devices / Online rate |
| `dash_kpi_row2` | `true` | Active alerts / Throughput |
| `dash_kpi_row3` | `true` | Rules / Total messages |
| `dash_gauges` | `true` | Water level / Humidity gauges |
| `dash_charts` | `true` | Temperature / Humidity trends |
| `dash_activity` | `true` | Recent alerts & operations |
| `dash_readings` | `true` | Latest raw sensor readings |

---

## Differences from Flutter Source

| Aspect | Flutter Source | Java Mirror |
|--------|---------------|-------------|
| DI / State | Riverpod Provider | Singleton (`AppController`) |
| Charts | `fl_chart` | Native Canvas `MiniTrendChartView` |
| Maps | `flutter_map` + OSM | Google Maps SDK for Android |
| MQTT | `mqtt5_client` | Eclipse Paho MQTT v3 |
| Navigation | GoRouter | BottomNav + DrawerLayout |
| Multi-language | `LocaleProvider` | `strings.xml` (Chinese default) |

---

## Contributing

1. Fork → feature branch: `git checkout -b feat/your-feature`
2. Follow [Conventional Commits](https://www.conventionalcommits.org/)
3. Open a Pull Request against `main`

---

*© OpenSynaptic — MIT License*
