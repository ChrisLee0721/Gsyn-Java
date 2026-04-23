# Testing Guide

> 中文版請見 [TESTING_zh.md](TESTING_zh.md)

This guide covers unit testing, manual end-to-end validation, and how to verify the full UDP/MQTT
pipeline without physical hardware.

---

## Unit Tests

All unit tests live in:

```
app/src/test/java/com/opensynaptic/gsynjava/core/protocol/ProtocolCodecTest.java
```

The protocol layer is **pure Java** (no Android dependencies), so tests run on the JVM directly —
no emulator or device needed.

### Run via Gradle

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

### Run via Android Studio

Right-click `ProtocolCodecTest.java` → **Run 'ProtocolCodecTest'**

### Test Coverage

| Test | What it verifies |
|------|-----------------|
| `base62_roundtrip` | `Base62Codec.encode` / `decode` round-trip for edge values (0, 1, 61, 62, large, negative) |
| `timestamp_roundtrip` | `encodeTimestamp` / `decodeTimestamp` preserve a UNIX timestamp exactly |
| `packet_build_and_decode` | `PacketBuilder.buildSensorPacket` produces a frame that `PacketDecoder.decode` accepts with CRC-8 and CRC-16 both valid |
| `body_parse_sensor` | `BodyParser.parseText` correctly extracts `TEMP` and `HUM` sensor readings from a raw body string |
| `diff_engine_full_heart_roundtrip` | A `DATA_FULL` frame is stored as a template; a subsequent `DATA_HEART` with empty body reconstructs the original readings |
| `diff_engine_clear` | `DiffEngine.clear()` purges all templates; HEART after clear returns `null` |
| `protocol_constants_default_unit` | `ProtocolConstants.defaultUnitFor()` returns correct SI units for `TEMP`, `HUM`, `PRES`, and `""` for unknown IDs |
| `os_cmd_is_secure` | `OsCmd.isSecureCmd()` correctly identifies secure vs. non-secure command bytes |

---

## End-to-End Validation (Real Device — Recommended)

### Prerequisites

- Android phone on the **same Wi-Fi network** as your PC
- App installed with UDP enabled (`Settings` → UDP port `9876`)
- Python 3 on PC

### Step 1 — Find the phone's IP

On the phone: **Settings → Wi-Fi → (tap network) → IP address**

Or run from PC after `adb connect`:

```powershell
adb shell ip route
```

### Step 2 — Send a test packet from PC

Save as `send_test.py` and run:

```python
import socket

def crc8(data: bytes) -> int:
    crc = 0
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = ((crc << 1) ^ 0x07) & 0xFF if crc & 0x80 else (crc << 1) & 0xFF
    return crc

# DATA_FULL packet — AID=1, TID=0, SEQ=1
# Body: two sensors TEMP and HUM with Base62-encoded values
body = b"1.U.AABlqg4|TEMP>U.Cel:44D|HUM>U.%:52h|"
header = bytes([0x20, 0x01, 0x00, 0x01, len(body)])
frame = header + body
frame += bytes([crc8(frame)])

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.sendto(frame, ("192.168.x.x", 9876))  # ← replace with your phone's IP
print(f"Sent {len(frame)} bytes")
```

```powershell
python send_test.py
```

### Step 3 — Verify on device

1. **Dashboard** → Readings card should show `TEMP` and `HUM` values
2. **Devices** tab → device with AID=1 should appear as Online
3. **Health** tab → `totalMessages` counter should increment

---

## End-to-End Validation (Emulator)

The emulator requires port forwarding because it runs on a virtual network.

### Step 1 — Forward the UDP port

```powershell
adb forward udp:9876 udp:9876
```

> Run this after every emulator restart.

### Step 2 — Send to localhost

Change the send address in the Python script above:

```python
sock.sendto(frame, ("127.0.0.1", 9876))
```

### Step 3 — Verify identically to real device above

---

## MQTT End-to-End Validation

### Start a local broker (Mosquitto)

```powershell
# Install: https://mosquitto.org/download/
mosquitto -v
```

### Configure the App

`Settings` → MQTT Broker:
- **Real device**: `tcp://<PC-LAN-IP>:1883`
- **Emulator**: `tcp://10.0.2.2:1883`

### Publish a test packet

Use [MQTT Explorer](https://mqtt-explorer.com/) to publish the raw bytes to topic `gsyn/sensor/1`,
or use `mosquitto_pub` with the hex payload from the Python script above.

---

## Logcat Verification

In Android Studio **Logcat**, filter by tag to trace the full pipeline:

| Filter tag | What it shows |
|------------|--------------|
| `TransportManager` | Raw bytes received, send confirmations |
| `PacketDecoder` | CRC result, header fields |
| `DiffEngine` | Template store / reconstruct |
| `AppRepository` | SQL insert row counts |
| `RulesEngine` | Rule evaluation results |
| `DashboardFragment` | Snapshot field values on each refresh |

Example filter string in Logcat search bar:

```
tag:PacketDecoder | tag:DiffEngine | tag:AppRepository
```

