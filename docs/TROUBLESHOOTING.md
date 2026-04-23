# Troubleshooting Guide

> 中文版請見 [TROUBLESHOOTING_zh.md](TROUBLESHOOTING_zh.md)

A quick-reference for the most common issues when running, developing, or integrating with Gsyn Java.

---

## Build & Setup

### Gradle sync fails — "SDK location not found"

`local.properties` is missing or has the wrong path.

```properties
# local.properties (Windows)
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=YOUR_KEY_HERE
```

---

### Map page shows a blank grey tile

Either the API key is missing or the SHA-1 fingerprint restriction doesn't match.

1. Confirm `MAPS_API_KEY` is set in `local.properties`
2. In [Google Cloud Console](https://console.cloud.google.com) → APIs & Services → Credentials, verify the Android app restriction includes:
   - Package: `com.opensynaptic.gsynjava`
   - SHA-1: matches your **debug** keystore
3. Get the debug SHA-1:
   ```powershell
   keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" `
           -alias androiddebugkey -storepass android -keypass android
   ```

---

### App crashes immediately on launch

Check **Logcat** for the actual exception. The most common cause is a `NullPointerException` in
`AppController.get()` when called before `Application.onCreate()` completes.

Ensure `AppController.get(context)` is only called from an Activity or Fragment, **not** from a
static initialiser.

---

## Transport — UDP

### Dashboard shows no data after enabling UDP

Go through this checklist in order:

1. **Port mismatch** — Settings UDP port must match the port your device sends to
2. **Wrong target IP** — your IoT device must send to the **Android device's IP**, not `127.0.0.1`
3. **Firewall / emulator** — on emulator, run `adb forward udp:9876 udp:9876` first
4. **CRC failure** — add a log to confirm packets arrive but CRC fails:
   ```java
   // In PacketDecoder.decode()
   Log.d("PacketDecoder", "raw=" + raw.length + "B  crc8Ok=" + result.crc8Ok);
   ```
5. **Wrong CRC polynomial** — Gsyn uses CRC-8/SMBUS (poly `0x07`, init `0x00`). Verify your sender uses the same algorithm.

---

### sendUdp() throws "Network on main thread"

`TransportManager.sendUdp()` is being called on the UI thread with strict mode enabled.
Move it to a background thread:

```java
new Thread(() -> tm.sendUdp(bytes, host, port)).start();
```

---

## Transport — MQTT

### MQTT connection fails immediately

1. Verify the broker URL format: `tcp://host:1883` or `ssl://host:8883`
2. On emulator, use `tcp://10.0.2.2:1883` to reach a broker on the host PC
3. Check broker logs — Eclipse Paho v3 does **not** support MQTT 5 brokers
4. If using TLS, the broker certificate must be trusted by the Android trust store

---

### Messages arrive on broker but App doesn't show them

The app subscribes to `gsyn/#`. Confirm your broker's ACL allows this subscription and that the
publisher is sending to a topic that matches `gsyn/…` (e.g. `gsyn/sensor/1`).

---

## Rules Engine

### Rule fires once, then never again

The rule's `cooldownMs` (default 60 000 ms = 1 minute) has not elapsed. Either:
- Wait 60 seconds and trigger again, or
- Reduce `cooldownMs` in the Rules screen

---

### Rule is enabled but never fires

1. Check that `sensorId` in the rule **exactly** matches the sensor ID in the packet (comparison is case-insensitive, but trailing spaces will break it)
2. Add a log in `RulesEngine.evaluate()` to trace the comparison:
   ```java
   Log.d("RulesEngine", "rule=" + rule.sensorId + " reading=" + reading.sensorId
         + " value=" + reading.value + " threshold=" + rule.threshold);
   ```

---

## Dashboard

### CUSTOM_SENSOR card shows "—" (no data)

1. The sensor ID in the card settings must **exactly** match what the device sends
2. Add a log in `DashboardFragment.refresh()` to inspect the snapshot keys:
   ```java
   Log.d("SNAP", "keys=" + snap.latestBySensorId.keySet());
   ```
3. If the key is present but value is `0.0`, check that `BodyParser` is parsing the unit field correctly

---

### Dashboard doesn't refresh after a packet arrives

`onMessage()` is called on the **background UDP thread**. UI updates must be dispatched to the
main thread:

```java
@Override
public void onMessage(Models.DeviceMessage msg) {
    if (getActivity() != null) {
        getActivity().runOnUiThread(this::refresh);
    }
}
```

If `getActivity()` is null, the fragment has detached — this is safe to ignore.

---

## Location / Map

### Device marker doesn't appear on Map

1. The device must send `LAT` and `LNG` sensor IDs **or** a `GEO` (geohash) sensor ID
2. Confirm `AppRepository.updateDeviceGeo()` is called during packet processing —
   check `TransportManager` for where geo fields are extracted from readings
3. Verify the `devices` table has non-zero `lat`/`lng` via **Database Inspector**:
   Android Studio → View → Tool Windows → App Inspection → Database Inspector

---

## Database

### How to inspect the SQLite database live

**Android Studio → View → Tool Windows → App Inspection → Database Inspector**

Select `gsyn_db` to browse and query all tables in real time without any adb commands.

---

### sensor_data table grows unexpectedly

This should not happen — `insertSensorDataBatch()` automatically deletes rows older than 7 days.
If the table is still growing, confirm you are not calling any insert method that bypasses
`AppRepository`.

---

## Release / CI

### GitHub Actions: "Tag number over 30 is not supported"

The keystore is in PKCS12 format. Android Gradle Plugin requires **JKS** format.
Regenerate using the provided script:

```powershell
.\scripts\generate-keystore.ps1
```

See [RELEASE.md](../RELEASE.md) for full instructions.

---

### Signed APK installs but crashes on first open

The release build runs ProGuard. If a required class is stripped, add a keep rule to
`app/proguard-rules.pro`:

```proguard
-keep class com.opensynaptic.gsynjava.core.protocol.** { *; }
-keep class com.opensynaptic.gsynjava.data.Models { *; }
```

