# Getting Started — Developer Guide

Welcome to **Gsyn Java**, an Android telemetry console for the OpenSynaptic protocol.  
This guide helps you set up a working dev environment and understand the project in the first 30 minutes.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Android Studio | Hedgehog 2023.1+ | or newer |
| JDK | 17 | set in `compileOptions` |
| Android SDK | API 34 (compile), API 24 (min) | |
| Git | any | |
| Google Maps API key | — | optional, for Map page |

---

## Clone & Open

```bash
git clone https://github.com/ChrisLee0721/Gsyn-Java.git
cd Gsyn-Java
```

Open **Android Studio → File → Open** → select the `Gsyn-Java` folder.

Let Gradle sync complete (first time downloads ~200 MB of dependencies).

---

## Configuration

### Maps API Key (optional)

Create or edit `local.properties` in the project root:

```properties
MAPS_API_KEY=AIzaSy...yourkey...
```

Without this key the Map page shows a blank grey tile — everything else works normally.

### Signing (release builds only)

CI injects signing via `-P` Gradle properties. For local release builds, add to `local.properties`:

```properties
android.injected.signing.store.file=/path/to/release.jks
android.injected.signing.store.password=...
android.injected.signing.key.alias=...
android.injected.signing.key.password=...
```

Debug builds are self-signed automatically and need no configuration.

---

## Build & Run

```bash
./gradlew assembleDebug          # build APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew test                   # unit tests
```

Or press the green ▶ button in Android Studio.

---

## Project Structure

```
app/src/main/
├── java/com/opensynaptic/gsynjava/
│   ├── AppController.java            ← singleton wiring all components
│   ├── core/
│   │   ├── AppThemeConfig.java       ← theme + background presets
│   │   ├── LocaleHelper.java         ← per-app language switching
│   │   └── protocol/                 ← pure-Java codec (no Android deps)
│   │       ├── PacketDecoder.java
│   │       ├── PacketBuilder.java
│   │       ├── DiffEngine.java
│   │       └── CRC8.java
│   ├── data/
│   │   ├── AppRepository.java        ← all DB reads/writes
│   │   ├── AppDatabaseHelper.java    ← raw SQLite schema + migrations
│   │   └── Models.java               ← plain data classes (no ORM)
│   ├── transport/
│   │   └── TransportManager.java     ← UDP + MQTT listener threads
│   ├── rules/
│   │   └── RulesEngine.java          ← threshold evaluation
│   └── ui/
│       ├── MainActivity.java         ← DrawerLayout + BottomNav host
│       ├── common/
│       │   ├── UiFormatters.java     ← locale-aware formatting helpers
│       │   └── BaseSecondaryFragment.java
│       ├── dashboard/
│       │   ├── DashboardFragment.java
│       │   ├── DashboardCardAdapter.java
│       │   ├── DashboardCardConfig.java
│       │   └── DashboardCardItem.java
│       ├── devices/   alerts/   send/   settings/
│       ├── mirror/               ← Map, History, Rules, Health
│       └── widget/
│           └── MiniTrendChartView.java  ← custom canvas chart
├── res/
│   ├── layout/                   ← XML layouts (ViewBinding enabled)
│   ├── values/strings.xml        ← English strings
│   ├── values-zh/strings.xml     ← Chinese strings
│   ├── values/themes.xml         ← Material3 base theme
│   ├── values/theme_overlays.xml ← accent + background overlays
│   └── xml/locale_config.xml     ← Android 13+ locale declaration
└── AndroidManifest.xml
```

---

## Key Dependencies

```groovy
implementation 'androidx.appcompat:appcompat:1.7.0'         // AppCompatDelegate locale API
implementation 'com.google.android.material:material:1.12.0' // Material3 components
implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
implementation 'com.google.android.gms:play-services-maps:18.2.0'
```

No Retrofit, no Room, no Dagger. This is intentional — see [ARCHITECTURE.md](ARCHITECTURE.md).

---

## Quick Orientation: What Happens at Startup

1. `MainActivity.onCreate()` calls `AppController.get(context)` which wires the singleton graph
2. `DashboardFragment` becomes visible → calls `transportManager.addMessageListener(this)`
3. User enables UDP in Settings → `TransportManager.startUdp()` opens a `DatagramSocket`
4. A packet arrives → decoded → stored in SQLite → `onMessage()` fired → `refresh()` updates UI

All of this happens before any device connects. The app is always *listening* once transport is enabled.

---

## Next Steps

- Read **[ARCHITECTURE.md](ARCHITECTURE.md)** for the full layered design
- Read **[PROTOCOL.md](PROTOCOL.md)** for the binary packet format
- Read **[DASHBOARD_CARDS.md](DASHBOARD_CARDS.md)** for the card system
- Read **[UI_PATTERNS.md](UI_PATTERNS.md)** for ViewBinding, theming, and i18n patterns

