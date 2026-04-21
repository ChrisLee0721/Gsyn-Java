# Gsyn Binary Protocol Reference

This document describes the Gsyn wire protocol as implemented in `core/protocol/`.  
It mirrors the Flutter source at `lib/protocol/codec/`.

> 中文版請見 [PROTOCOL_zh.md](PROTOCOL_zh.md)

---

## Packet Frame Layout

```
┌────────┬────────┬────────┬────────┬────────┬─────────────────┬────────┐
│ CMD    │ AID    │ TID    │ SEQ    │ LEN    │ BODY            │ CRC8   │
│ 1 byte │ 1 byte │ 1 byte │ 1 byte │ 1 byte │ LEN bytes       │ 1 byte │
└────────┴────────┴────────┴────────┴────────┴─────────────────┴────────┘
```

| Field | Type | Description |
|-------|------|-------------|
| CMD | `uint8` | Command byte — determines body format |
| AID | `uint8` | Source Application ID (node address, 1–254) |
| TID | `uint8` | Target Application ID (0 = broadcast) |
| SEQ | `uint8` | Sequence counter — wraps at 255 |
| LEN | `uint8` | Body byte count (0–255) |
| BODY | `bytes[LEN]` | Command-specific payload |
| CRC8 | `uint8` | CRC-8/SMBUS over bytes 0..(4+LEN) |

---

## Command Table

### Control Commands (0x01–0x0F)

| CMD | Hex | Body | Response | Description |
|-----|-----|------|----------|-------------|
| PING | `0x01` | empty | PONG | Heartbeat probe |
| PONG | `0x02` | empty | — | Heartbeat reply |
| ID_REQUEST | `0x03` | empty | ID_ASSIGN | Node with no AID requests one |
| ID_ASSIGN | `0x05` | `[assigned_aid: 1B]` | — | Master assigns AID |
| TIME_REQUEST | `0x07` | empty | TIME_RESPONSE | Request current UNIX time |
| TIME_RESPONSE | `0x08` | `[unix_sec: 4B big-endian]` | — | 32-bit UNIX timestamp |
| HANDSHAKE_ACK | `0x09` | empty | — | Accept handshake |
| HANDSHAKE_NACK | `0x0A` | empty | — | Reject handshake |

### Secure Commands (0x10–0x1F)

| CMD | Hex | Body | Description |
|-----|-----|------|-------------|
| SECURE_DICT_READY | `0x10` | `[dict_id: 1B]` | Announce encryption dictionary |

### Data Commands (0x20–0x2F)

| CMD | Hex | Body Format | Description |
|-----|-----|-------------|-------------|
| DATA_FULL | `0x20` | Multi-sensor body (see below) | Full snapshot of all sensors |
| DATA_DIFF | `0x21` | Diff body (see below) | Changed sensors only |
| DATA_HEART | `0x22` | empty | Re-use last FULL template |
| DATA_FULL_SENSOR | `0x23` | Single-sensor body | One sensor reading |

---

## Body Formats

### DATA_FULL / DATA_FULL_SENSOR Body

Each sensor occupies one newline-terminated record:

```
[sensor_id: 2 ASCII chars][unit_code: 1 byte][state_code: 1 byte][value: Base62 string]\n
```

Example (TEMP sensor, 25.6 °C, OK):
```
TE\x01\x0015A\n
```

Multiple sensors are concatenated without separator (each terminated by `\n`).

### DATA_DIFF Body

Only fields that changed relative to the stored FULL template:

```
[sensor_id: 2 bytes][changed_fields_bitmask: 1 byte][...changed fields...]
```

Bitmask bits: `bit0=value` `bit1=state` `bit2=unit`

The `DiffEngine` reconstructs the full reading list by applying the diff to the template.

### DATA_HEART Body

Empty body. The `DiffEngine` re-emits the last FULL template for the given AID.

---

## Sensor ID Codes (2 ASCII bytes)

| Sensor | Code | Default Unit |
|--------|------|-------------|
| Temperature | `TE` | °C |
| Humidity | `HU` | % |
| Pressure | `PR` | hPa |
| Water Level | `LV` | % |
| CO₂ | `CO` | ppm |
| Light | `LI` | lux |
| Motion | `MO` | bool |
| Generic | `G0`–`G9` | — |

---

## Unit Codes (1 byte)

| Code | Symbol | Meaning |
|------|--------|---------|
| `0x00` | — | No unit / boolean |
| `0x01` | °C | Celsius |
| `0x02` | % | Percentage |
| `0x03` | hPa | Hectopascal |
| `0x04` | ppm | Parts per million |
| `0x05` | lux | Illuminance |
| `0x06` | m/s | Wind speed |
| `0x07` | mm | Millimetres (rainfall) |

---

## State Codes (1 byte)

| Code | Name | Meaning |
|------|------|---------|
| `0x00` | OK | Value within normal range |
| `0x01` | WARN | Value approaching threshold |
| `0x02` | ERROR | Value exceeded threshold / sensor fault |
| `0x03` | OFFLINE | Sensor not responding |

---

## CRC Algorithms

### CRC-8/SMBUS

```
Polynomial : 0x07
Initial    : 0x00
Input/Output reflected : No
XOR out    : 0x00
```

Computed over all bytes from `CMD` through end of `BODY` (excludes the CRC byte itself).

### CRC-16/CCITT-FALSE

```
Polynomial : 0x1021
Initial    : 0xFFFF
Input/Output reflected : No
XOR out    : 0x0000
```

Used for secure payload integrity only.

---

## Base62 Encoding

Values are encoded as Base62 (alphabet `0-9A-Za-z`) for compact ASCII representation in packet bodies.

### Sensor Value Encoding

Floating-point sensor values are scaled by a unit-specific factor before encoding:

| Unit | Scale factor |
|------|-------------|
| °C | × 100 |
| % | × 100 |
| hPa | × 10 |
| ppm | × 1 |

```
encodeSensorValue(25.6, "°C") → encode(2560) → "aN"   (2 chars)
decodeSensorValue("aN", "°C") → 25.6
```

### Timestamp Encoding

Millisecond timestamps are encoded as 8-character Base64url strings (standard Base64 with `+`→`-` `/`→`_`):

```
encodeTimestamp(1713600000000) → "AYy2MQAA"
```

---

## Example: PING packet

```
CMD=0x01  AID=0x01  TID=0x01  SEQ=0x00  LEN=0x00  CRC8=?

Bytes: 01 01 01 00 00
CRC8 of [01 01 01 00 00] = 0x04

Full packet: 01 01 01 00 00 04
```

## Example: DATA_FULL_SENSOR (TEMP = 25.6 °C, OK)

```
Body: "TE" + 0x01 + 0x00 + "aN" + 0x0A
     = 54 45 01 00 61 4E 0A   (7 bytes)

CMD=0x23  AID=0x01  TID=0x00  SEQ=0x01  LEN=0x07
Header: 23 01 00 01 07
Body  : 54 45 01 00 61 4E 0A
CRC8 over [23 01 00 01 07 54 45 01 00 61 4E 0A]

Full packet: 23 01 00 01 07 54 45 01 00 61 4E 0A <crc>
```

---

## Geohash

Device GPS positions may be encoded as [Geohash](https://en.wikipedia.org/wiki/Geohash) strings in device records. The `GeohashDecoder` class decodes these to `(lat, lng)` pairs for map display.

Precision table:

| Length | Lat error | Lng error |
|--------|-----------|-----------|
| 4 | ±20 km | ±20 km |
| 5 | ±2.4 km | ±2.4 km |
| 6 | ±0.61 km | ±0.61 km |
| 7 | ±76 m | ±76 m |
| 8 | ±19 m | ±19 m |

---

*For the Java implementation see `core/protocol/`. For the Flutter reference see `lib/protocol/codec/`.*