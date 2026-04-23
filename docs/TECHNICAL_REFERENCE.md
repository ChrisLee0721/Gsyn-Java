# Technical Reference — Gsyn Java

> 中文版请见 [TECHNICAL_REFERENCE_zh.md](TECHNICAL_REFERENCE_zh.md)

This document is a precise, code-level reference derived directly from the source.  
It is intended for technical presentations, code reviews, and integration work.

---

## Table of Contents

1. [Wire Protocol — Exact Frame Layout](#1-wire-protocol--exact-frame-layout)
2. [Command Byte Table (OsCmd)](#2-command-byte-table-oscmd)
3. [CRC Algorithms (OsCrc)](#3-crc-algorithms-oscrc)
4. [Base62 / Base64url Encoding (Base62Codec)](#4-base62--base64url-encoding-base62codec)
5. [Body Text Format & Parser (BodyParser)](#5-body-text-format--bodyparser)
6. [Diff / Heart Engine (DiffEngine)](#6-diff--heart-engine-diffengine)
7. [Protocol Constants — Sensor IDs & Units](#7-protocol-constants--sensor-ids--units)
8. [Packet Decoder (PacketDecoder)](#8-packet-decoder-packetdecoder)
9. [Packet Builder (PacketBuilder)](#9-packet-builder-packetbuilder)
10. [Transport Layer (TransportManager)](#10-transport-layer-transportmanager)
11. [Application Controller (AppController)](#11-application-controller-appcontroller)
12. [Data Models (Models.java)](#12-data-models-modelsjava)
13. [Threading Model](#13-threading-model)
14. [Known Differences from README](#14-known-differences-from-readme)

---

## 1. Wire Protocol — Exact Frame Layout

Every Gsyn packet on the wire follows this **fixed 13-byte header** layout, verified from
`PacketDecoder.decode()` and `PacketBuilder.buildPacket()`:

```
Offset  Size  Field         Description
──────  ────  ──────────    ─────────────────────────────────────────────
 0       1    CMD           Command byte (see §2)
 1       1    ROUTE_COUNT   Always 1 for standard packets
 2       4    AID           Source Application ID — big-endian uint32
 6       1    TID           Transaction / Template ID — uint8
 7       2    RESERVED      Two zero bytes (padding)
 9       4    TS_SEC        UNIX timestamp in seconds — big-endian uint32
13       N    BODY          Variable-length payload (N = total - 16)
13+N     1    CRC8          CRC-8/SMBUS of BODY bytes only
14+N     2    CRC16         CRC-16/CCITT-FALSE of bytes[0 .. 14+N-1]
```

**Minimum valid packet: 16 bytes** (13-byte header + 0-byte body + 3 CRC bytes).  
**Maximum frame: 512 bytes** (enforced in `PacketBuilder.buildPacket()`).

> ⚠️ AID is **4 bytes** (uint32), not 1 byte as shown in the top-level README.

### Frame size formula

```
frameLen = 13 + bodyLen + 3
         = bodyLen + 16
```

---

## 2. Command Byte Table (OsCmd)

Actual decimal and hex values from `OsCmd.java`:

| Constant | Dec | Hex | Category | Description |
|----------|-----|-----|----------|-------------|
| `ID_REQUEST` | 1 | `0x01` | Session | Node requests an AID assignment |
| `ID_ASSIGN` | 2 | `0x02` | Session | Master assigns an AID |
| `ID_POOL_REQ` | 3 | `0x03` | Session | Pool-based ID request |
| `ID_POOL_RES` | 4 | `0x04` | Session | Pool-based ID response |
| `HANDSHAKE_ACK` | 5 | `0x05` | Session | Handshake accepted |
| `HANDSHAKE_NACK` | 6 | `0x06` | Session | Handshake rejected |
| `PING` | 9 | `0x09` | Control | Heartbeat request |
| `PONG` | 10 | `0x0A` | Control | Heartbeat reply |
| `TIME_REQUEST` | 11 | `0x0B` | Control | Node requests UNIX timestamp |
| `TIME_RESPONSE` | 12 | `0x0C` | Control | Master sends timestamp |
| `SECURE_DICT_READY` | 13 | `0x0D` | Secure | Encryption dictionary ready |
| `SECURE_CHANNEL_ACK` | 14 | `0x0E` | Secure | Secure channel acknowledged |
| `DATA_FULL` | 63 | `0x3F` | Data | Full sensor frame |
| `DATA_FULL_SEC` | 64 | `0x40` | Data | Full frame (encrypted) |
| `DATA_HEART` | 127 | `0x7F` | Data | Heartbeat (reuse template) |
| `DATA_HEART_SEC` | 128 | `0x80` | Data | Heartbeat (encrypted) |
| `DATA_DIFF` | 170 | `0xAA` | Data | Differential update frame |
| `DATA_DIFF_SEC` | 171 | `0xAB` | Data | Differential update (encrypted) |

### Helper Methods

```java
OsCmd.isDataCmd(cmd)     // true for FULL/FULL_SEC/DIFF/DIFF_SEC/HEART/HEART_SEC
OsCmd.isSecureCmd(cmd)   // true for FULL_SEC / DIFF_SEC / HEART_SEC
OsCmd.normalizeDataCmd(cmd) // strips _SEC suffix: FULL_SEC→FULL, DIFF_SEC→DIFF, HEART_SEC→HEART
OsCmd.nameOf(cmd)        // returns human-readable string e.g. "DATA_FULL"
```

---

## 3. CRC Algorithms (OsCrc)

### CRC-8/SMBUS

- **Polynomial**: `0x07` (x⁸ + x² + x + 1)
- **Init**: `0x00`
- **Scope**: Applied to BODY bytes only
- **Position**: byte at offset `13 + bodyLen`

```java
// Implementation (OsCrc.crc8)
int crc = 0x00;
for (byte b : data) {
    crc = (crc ^ (b & 0xFF)) & 0xFF;
    for (int bit = 0; bit < 8; bit++) {
        crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
    }
}
```

### CRC-16/CCITT-FALSE

- **Polynomial**: `0x1021` (x¹⁶ + x¹² + x⁵ + 1)
- **Init**: `0xFFFF`
- **Scope**: Applied to all bytes from offset 0 up to (but not including) the last 2 bytes
- **Position**: 2-byte big-endian at the last 2 bytes of the frame

```java
// Implementation (OsCrc.crc16)
int crc = 0xFFFF;
for (byte b : data) {
    crc = (crc ^ ((b & 0xFF) << 8)) & 0xFFFF;
    for (int bit = 0; bit < 8; bit++) {
        crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
    }
}
```

### Validation Logic in PacketDecoder

```java
// CRC-8: computed over body bytes only
int gotCrc8 = packet[packet.length - 3] & 0xFF;
int expCrc8 = meta.bodyLen > 0 ? OsCrc.crc8(body) : 0;
meta.crc8Ok = gotCrc8 == expCrc8;

// CRC-16: computed over all bytes except last 2
byte[] for16 = new byte[packet.length - 2];
System.arraycopy(packet, 0, for16, 0, packet.length - 2);
meta.crc16Ok = gotCrc16 == OsCrc.crc16(for16);
```

A packet is accepted **only if both** `crc8Ok && crc16Ok` are true.

---

## 4. Base62 / Base64url Encoding (Base62Codec)

### Alphabet

```
ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            ^─ digits ─^  ^──────── lowercase ─────────^  ^── uppercase ──^
```

Index 0 = `'0'`, index 61 = `'Z'`. Supports negative values (prefixed with `-`).

### Sensor Value Encoding

```java
VALUE_SCALE = 10000

encodeValue(double v) → encode(Math.round(v * 10000))
decodeValue(String b) → decode(b) / 10000.0
```

Examples:
- `25.6 °C` → `encode(256000)` → `"nRY"`
- `-12.34` → `encode(-123400)` → `"-VWw"`
- `0.0` → `"0"`

### Timestamp Encoding

Timestamps use **Base64url** (not Base62):

```java
encodeTimestamp(long tsSec):
    bytes = [0x00, 0x00, (tsSec>>24)&0xFF, (tsSec>>16)&0xFF, (tsSec>>8)&0xFF, tsSec&0xFF]
    → Base64url (no padding), always 8 characters

decodeTimestamp(String token):
    → pads to multiple of 4, Base64url decode
    → reconstructs uint32 from bytes[2..5]
```

Example: UNIX timestamp `1712345678` → `"AABlqg4o"` (8 chars, URL-safe, no `=` padding).

---

## 5. Body Text Format & BodyParser

### Full Body Text Grammar

```
body       = header "|" sensor_list
header     = aid "." node_state "." ts_token
sensor_list= (segment "|")*
segment    = sensor_id ">" state "." unit ":" b62_value
```

Example body string:
```
1.U.AABlqg4o|TEMP>U.Cel:nRY|HUM>U.%RH:52h|PRES>U.hPa:3eVQ|
```

Decoded:
- Header: AID=1, NodeState=`U` (Normal), Timestamp=Base64url token
- TEMP: state=U, unit=`Cel`, value=`nRY` → `25.6`
- HUM: state=U, unit=`%RH`, value=`52h` → `48.2`
- PRES: state=U, unit=`hPa`, value=`3eVQ` → `1013.25`

### BodyParser.parseText() Logic

```
1. Find first '|' → split header / payload
2. Header: last '.' separates ts_token; before that: aid '.' node_state
3. Payload: split by '|', each segment parsed as:
      sid = before '>'
      state = before first '.' after '>'
      unit = between first '.' and ':'
      b62 = after ':' (strip any '#', '!', '@' suffix markers)
4. value = Base62Codec.decodeValue(b62)
```

---

## 6. Diff / Heart Engine (DiffEngine)

### Purpose

Reduces bandwidth by only transmitting changed sensor values.  
The engine maintains a per-`(AID, TID)` template cache.

### Template Cache Key

```java
cache[aidStr][tidStr]
// aidStr = String.valueOf(aid)     e.g. "1"
// tidStr = String.format("%02d", tid)  e.g. "01"
```

### TemplateState Structure

```java
class TemplateState {
    String     signature;  // body with {TS} and '\u0001' placeholders
    List<byte[]> valsBin;  // cached UTF-8 bytes for each value slot
}
```

### DATA_FULL Processing

```
1. Parse body text → DecompResult
2. For each sensor segment:
     - Extract tag, meta (state.unit), value (b62)
     - Add "tag>\u0001:\u0001" to signature
     - Add meta bytes and value bytes to valsBin (2 slots per sensor)
3. Build signature = "{hBase}.{TS}|{segments...}"
4. Store TemplateState in cache[aid][tid]
5. Return original body text
```

### DATA_HEART Processing

```
1. Look up cache[aid][tid]
2. If miss → return null
3. Reconstruct: replace {TS} with current timestamp, replace each '\u0001' with cached valsBin
4. Return reconstructed body text
```

### DATA_DIFF Processing

```
1. Look up cache[aid][tid]
2. Read big-endian bitmask: maskLen = ceil(numSlots / 8) bytes
3. For each bit set in mask at position i:
     - Read 1-byte length prefix → read vLen bytes → update valsBin[i]
4. Reconstruct from updated template
5. Return reconstructed body text
```

### State Management

```java
diffEngine.clear()          // called on stopUdp() and disconnectMqtt()
diffEngine.templateCount()  // returns total cached template count (for Health page)
```

---

## 7. Protocol Constants — Sensor IDs & Units

### Node State Codes

| Code | Meaning |
|------|---------|
| `U` | Unknown / Unclassified (default) |
| `A` | Active / Alert |
| `W` | Warning |
| `D` | Danger |
| `O` | Offline |
| `E` | Error |
| `S` | Sleep |
| `I` | Idle |

### Sensor State Codes (per reading)

`U` · `A` · `W` · `D` · `O` · `E`

### Standard Sensor IDs (selected)

| Category | IDs |
|----------|-----|
| Temperature | `TEMP` `T1` `T2` `T3` `TMP` |
| Humidity | `HUM` `H1` `H2` `RH` |
| Pressure | `PRES` `P1` `BAR` |
| Level / Distance | `LVL` `L1` `LEVEL` `DIST` `D1` |
| Voltage | `VOLT` `V1` `VBAT` `VCC` |
| Current | `CURR` `I1` `IBAT` |
| Power | `POWER` `PW1` |
| Light | `LUX` `LIGHT` |
| Gas / Air | `CO2` `GAS` `PPM` `VOC` |
| Location | `GEO` `GEOHASH` `GEO1` `LOCATION` |

### Default Unit Lookup

```java
ProtocolConstants.defaultUnitFor("TEMP")  // → "°C"
ProtocolConstants.defaultUnitFor("HUM")   // → "%RH"
ProtocolConstants.defaultUnitFor("PRES")  // → "hPa"
ProtocolConstants.defaultUnitFor("TEMP2") // → "°C"  (prefix match)
ProtocolConstants.defaultUnitFor("XYZ")   // → ""    (unknown)
```

Matching is case-insensitive and supports **prefix matching** — `"TEMP2"` matches `"TEMP"`.

---

## 8. Packet Decoder (PacketDecoder)

### Signature

```java
public static Models.PacketMeta decode(byte[] packet)
// Returns null if: packet == null, length < 16, or bodyLen < 0
```

### Populated PacketMeta Fields

| Field | Source bytes | Description |
|-------|-------------|-------------|
| `cmd` | `packet[0]` | Command byte (uint8) |
| `routeCount` | `packet[1]` | Route count (uint8) |
| `aid` | `packet[2..5]` | 4-byte big-endian AID |
| `tid` | `packet[6]` | Transaction ID (uint8) |
| `tsSec` | `packet[9..12]` | 4-byte big-endian UNIX timestamp |
| `bodyOffset` | 13 | Fixed |
| `bodyLen` | `packet.length - 16` | Computed |
| `crc8Ok` | `packet[len-3]` | CRC-8 of body bytes |
| `crc16Ok` | `packet[len-2..len-1]` | CRC-16 of bytes[0..len-3] |

---

## 9. Packet Builder (PacketBuilder)

### Core Builder

```java
PacketBuilder.buildPacket(int cmd, int aid, int tid, long tsSec, byte[] body)
// Constructs: [header 13B][body][CRC8 1B][CRC16 2B]
// Returns null if frameLen > 512
```

### High-Level Builders

```java
// Single sensor packet
buildSensorPacket(int aid, int tid, long tsSec, String sensorId, String unit, double value)
// Body: "{aid}.U.{ts}|{sensorId}>U.{unit}:{b62}|"

// Multi-sensor (SensorEntry list)
buildMultiSensorPacket(int aid, int tid, long tsSec, List<SensorEntry> entries)
// Body: "{aid}.U.{ts}|{sid1}>{state}.{unit}:{b62}|{sid2}...|"
```

### Control Frame Builders (no full header, short frames)

```java
buildPing(int seq)           // [0x09][seq>>8][seq&0xFF]   3 bytes
buildPong(int seq)           // [0x0A][seq>>8][seq&0xFF]   3 bytes
buildIdRequest(int seq)      // [0x01][seq>>8][seq&0xFF]   3 bytes
buildTimeRequest(int seq)    // [0x0B][seq>>8][seq&0xFF]   3 bytes
buildIdAssign(int aid)       // [0x02][aid 4-byte BE]       5 bytes
buildHandshakeAck(int seq)   // [0x05][seq>>8][seq&0xFF]   3 bytes
buildHandshakeNack(int seq)  // [0x06][seq>>8][seq&0xFF]   3 bytes
buildSecureDictReady(int seq)// [0x0D][seq>>8][seq&0xFF]   3 bytes
buildRawHex(String hex)      // parses space-separated hex string → raw bytes
```

---

## 10. Transport Layer (TransportManager)

### Singleton Access

```java
TransportManager tm = TransportManager.get(context);
```

### UDP

```java
tm.startUdp(String host, int port)  // binds DatagramSocket to 0.0.0.0:port on "gsyn-udp-listener" thread
tm.stopUdp()                         // closes socket, clears DiffEngine cache
tm.isUdpRunning()                    // volatile boolean
tm.sendCommand(byte[] frame, int deviceAid, String udpHost, int udpPort)
```

UDP receive loop: buffer size = 1024 bytes. Runs on daemon thread `"gsyn-udp-listener"`.

### MQTT

```java
tm.connectMqtt(String broker, int port, String topic)
// Default topic if null/empty: "opensynaptic/#"
// Client ID: "gsyn-java-{UUID}"
// Options: AutoReconnect=true, CleanSession=true

tm.disconnectMqtt()  // clears DiffEngine cache
tm.isMqttConnected() // volatile boolean
```

Outgoing command via MQTT: publishes to `"opensynaptic/cmd/{deviceAid}"`.

### Incoming Message Pipeline

```java
decodeIncoming(byte[] data, String transportType):
  1. PacketDecoder.decode(data)       → null if length < 16
  2. Reject if !crc8Ok || !crc16Ok
  3. Reject if !OsCmd.isDataCmd(cmd)
  4. diffEngine.processPacket(cmd, aid, tid, body) → bodyText (null on cache miss)
  5. BodyParser.parseText(bodyText)   → BodyParseResult
  6. Build DeviceMessage (cmd, aid, tid, tsSec, nodeId, nodeState, transportType, readings, rawFrame)
  7. totalMessages++; messagesThisSecond++
  8. Notify all MessageListeners on the calling thread (UDP or MQTT callback thread)
```

### Stats Ticker

A `ScheduledExecutorService` fires every **1 second**, calling `emitStats()`:
- Snapshots `udpRunning`, `mqttConnected`, `messagesThisSecond`, `totalMessages`
- Resets `messagesThisSecond` to 0
- Notifies all `StatsListener` instances

### Listener Registration

```java
tm.addMessageListener(MessageListener)   // CopyOnWriteArrayList — thread-safe
tm.removeMessageListener(MessageListener)
tm.addStatsListener(StatsListener)
tm.removeStatsListener(StatsListener)
```

---

## 11. Application Controller (AppController)

### Singleton & Wiring

```java
AppController ctrl = AppController.get(context);
// On first call, constructs:
//   SharedPreferences "gsyn_java_prefs"
//   AppRepository.get(context)
//   TransportManager.get(context)
//   RulesEngine(repository, transportManager)
//   transportManager.addMessageListener(this)
//   repository.seedDefaultRuleIfEmpty()
```

### onMessage() — the central data pipeline

Called on every validated incoming packet (on the transport background thread):

```
1. Build Device from message.deviceAid / nodeId / status="online" / transportType / lastSeenMs=now
2. Scan readings for GEO sensor IDs → GeohashDecoder.decode(rawB62 or unit) → device.lat/lng
3. repository.upsertDevice(device)
4. For each SensorReading → build SensorData → repository.insertSensorData(data)
5. rulesEngine.evaluate(message, udpHost, udpPort)
   (udpHost/udpPort from SharedPreferences keys "udp_host" / "udp_port")
```

### Accessors

```java
ctrl.repository()    // AppRepository
ctrl.transport()     // TransportManager
ctrl.preferences()   // SharedPreferences
```

---

## 12. Data Models (Models.java)

All models are plain Java objects (no ORM annotations).

### PacketMeta

```java
int cmd, routeCount, aid, tid, bodyOffset, bodyLen
long tsSec
boolean crc8Ok, crc16Ok
```

### SensorReading (pre-DB, protocol-level)

```java
String sensorId, unit, state, rawB62
double value
```

### DeviceMessage (fully decoded packet)

```java
int cmd, deviceAid, tid
long timestampSec
String nodeId, nodeState, transportType
List<SensorReading> readings
byte[] rawFrame
```

### Device (persisted)

```java
long id; int aid
String name, type, status, transportType
double lat, lng
long lastSeenMs
```

### SensorData (persisted time-series)

```java
long id, timestampMs; int deviceAid
String sensorId, unit, rawB62
double value
```

### Rule (persisted automation)

```java
long id, cooldownMs; boolean enabled
String name, operator, actionType, actionPayload
Integer deviceAidFilter; String sensorIdFilter
double threshold

// Built-in evaluate:
boolean evaluate(double sensorValue)  // supports >, <, >=, <=, ==, !=
```

### AppUser

```java
long id, createdMs
String username, passwordHash
String role  // "admin" | "viewer"
boolean isAdmin()
```

---

## 13. Threading Model

| Thread name | Created by | Blocked on | Responsibility |
|-------------|-----------|------------|---------------|
| `main` | Android | — | Fragment lifecycle, UI binding, `runOnUiThread` dispatches |
| `gsyn-udp-listener` | `TransportManager.startUdp()` | `DatagramSocket.receive()` | Receive and decode UDP packets |
| `mqttCallbackThread` | Eclipse Paho library | MQTT broker | MQTT message delivery callback |
| `pool-1-thread-1` (scheduler) | `Executors.newSingleThreadScheduledExecutor()` | — | 1-second stats tick |

**Key rule**: All `MessageListener.onMessage()` and `StatsListener.onStats()` callbacks arrive on
background threads. Any UI update must be dispatched via `getActivity().runOnUiThread(...)`.

---

## 14. Known Differences from README

The top-level README contains several values that **do not match the actual source code**.
Use this table when integrating external tools:

| Topic | README says | Actual code |
|-------|-------------|-------------|
| AID field size | 1 byte | **4 bytes** (uint32, big-endian, bytes 2–5) |
| Header size | 5 bytes | **13 bytes** |
| `DATA_FULL` hex | `0x20` | **`0x3F`** (dec 63) |
| `DATA_DIFF` hex | `0x21` | **`0xAA`** (dec 170) |
| `DATA_HEART` hex | `0x22` | **`0x7F`** (dec 127) |
| `PING` hex | `0x01` | **`0x09`** (dec 9) |
| MQTT subscribe topic | `gsyn/#` | **`opensynaptic/#`** |
| MQTT command topic | `gsyn/out/<aid>` | **`opensynaptic/cmd/<aid>`** |
| Timestamp encoding | "8-char Base64url" | Correct — but implemented via `Base64.getUrlEncoder`, not Base62 |

