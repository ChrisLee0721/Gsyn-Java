# FAQ — Frequently Asked Questions

> 中文版請見 [FAQ_zh.md](FAQ_zh.md)

Answers to common questions about Gsyn Java's design decisions, capabilities, and limitations.

---

## General

### What is Gsyn Java?

Gsyn Java is an Android telemetry console for the **OpenSynaptic** IoT protocol. It receives sensor data from hardware nodes over UDP or MQTT, visualises the data on a configurable dashboard, and lets you define threshold-based alert rules.

It is a Java port of the original Flutter-based Gsyn app, designed to be more accessible for Android/Java learners and as an educational reference codebase.

---

### Is this production-ready?

It is functional and used for real deployments in lab and home-automation environments. However:
- UDP transport is **unencrypted** — not suitable for sensitive data over public networks
- No user authentication — whoever has the app can see all data
- Database has no automatic backup — data loss on uninstall

For production use, consider adding MQTT TLS and deploying behind a VPN.

---

### Does the app work without any OpenSynaptic hardware?

Yes. You can:
- Use the **Send** tab to send test packets to yourself (loopback `127.0.0.1:9876`)
- Use a Python/Node script to simulate a device over UDP
- Connect to any MQTT broker publishing OpenSynaptic-formatted payloads

See [PROTOCOL.md](PROTOCOL.md) for the packet format to build a simulator.

---

## Architecture Decisions

### Why no Room (ORM)?

Room adds compile-time annotation processing, which increases build time. The database schema for this project is simple (~5 tables, ~200-line helper), and raw SQLite is straightforward enough. Using Room would require adding Dagger/ViewModel/LiveData, which was intentionally avoided to keep the learning surface small.

If you extend this project significantly, Room + ViewModel is a reasonable upgrade path.

---

### Why no Retrofit or OkHttp?

The app communicates over **UDP and MQTT** — not HTTP. There are no REST API calls in the core flow.

The only HTTP-like request is Google Maps tile loading, which is handled entirely by the Maps SDK.

---

### Why no Dagger/Hilt for dependency injection?

The manual singleton pattern in `AppController` is explicit and easy to trace — you can follow the dependency graph by reading 50 lines of code. Hilt/Dagger adds significant complexity (annotation processing, component scopes, generated code) that is not justified at this scale.

For a larger project with many screens and async operations, Hilt would be appropriate.

---

### Why `commitNow()` instead of `commit()` for the map fragment?

`commit()` schedules the transaction to run asynchronously on the next frame. When `getMapAsync()` is called immediately after, the fragment may not yet be attached, causing the map to never initialise (blank screen).

`commitNow()` executes the transaction synchronously before returning, guaranteeing the fragment is attached when `getMapAsync()` is called.

---

### Why is the protocol layer in `core/protocol/` with no Android imports?

This is a deliberate design decision for **testability**. Pure Java classes can be unit-tested with standard JUnit without needing a device, emulator, or Robolectric. The `PacketDecoder`, `PacketBuilder`, `DiffEngine`, and `CRC8` classes have no Android dependencies.

---

### Why is sensor data kept for only 7 days?

To prevent unbounded database growth on long-running deployments. 7 days covers typical debugging and monitoring windows. The retention window is defined in one place (`AppRepository.insertSensorDataBatch`) and can be changed there.

---

## Features

### Can I add a new sensor type?

Yes. Sensor types are identified by a 2-byte ASCII code (e.g. `TE` for temperature). To add a new type:

1. Add the code to the sensor ID table in [PROTOCOL.md](PROTOCOL.md) (documentation)
2. Add a display name string in `values/strings.xml` and `values-zh/strings.xml`
3. Add a `case` in `UiFormatters.formatSensorLabel()` for the display label
4. The data will flow through automatically — no other changes needed

---

### Can I use a custom map tile server instead of Google Maps?

Yes — the **tile URL** field in Settings accepts any XYZ tile server URL with `{z}/{x}/{y}` placeholders. However, the map rendering engine is Google Maps SDK (required for the Geohash pin overlay). The tile URL field controls the base tile layer only if you implement a custom tile provider; by default, Google Maps uses its own tiles.

If you need full OpenStreetMap without Google Maps, replace `MapMirrorFragment` with an OSMDroid or Mapbox implementation.

---

### Can the app run as a background service?

Not currently. Transport (UDP/MQTT) runs on foreground threads tied to `TransportManager`. To run in the background, you would need to move transport into a `Service` (or `WorkManager` for periodic tasks) and request the `FOREGROUND_SERVICE` permission.

---

### What happens when the database is empty (no devices yet)?

The Dashboard displays all configured cards with zero/empty values. This is intentional — the UI always renders, regardless of data state. Values update automatically as soon as the first packet is received.

---

### Can I export data from the app?

There is no built-in export feature. The SQLite database file is at:
```
/data/data/com.opensynaptic.gsynjava/databases/gsyn.db
```

On a rooted device or via ADB backup, you can pull this file and open it with any SQLite browser.

---

## Building & CI

### Why does the CI require JKS format and not PKCS12?

The validation script uses `keytool -list -v` and parses the `Keystore type:` field. Android's `apksigner` and Gradle's signing block accept both formats, but the CI validation was written to enforce JKS for consistency with older toolchain expectations. See [CI_CD.md](CI_CD.md) for how to generate a correct JKS keystore.

---

### Can I build a release APK locally without CI?

Yes. Add to `local.properties`:

```properties
android.injected.signing.store.file=/absolute/path/to/release.jks
android.injected.signing.store.password=yourStorePassword
android.injected.signing.key.alias=gsyn-release
android.injected.signing.key.password=yourKeyPassword
```

Then run:
```bash
./gradlew assembleRelease
```

The signed APK will be at `app/build/outputs/apk/release/app-release.apk`.

---

*Still have a question? Open a GitHub Discussion or Issue.*

