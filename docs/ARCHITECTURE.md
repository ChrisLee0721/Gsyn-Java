# Architecture Guide

> 中文版請見 [ARCHITECTURE_zh.md](ARCHITECTURE_zh.md)


This document explains the internal design of Gsyn Java and the rationale behind key decisions.

> **New to the project?** Start with [GETTING_STARTED.md](GETTING_STARTED.md), then come back here.

---

## Layered Architecture

```
┌──────────────────────────────────────────────┐
│                  UI Layer                     │  Fragments, Views, Adapters
├──────────────────────────────────────────────┤
│              AppController                    │  Single coordination point
├────────────────┬─────────────┬───────────────┤
│  AppRepository │ TransportMgr│  RulesEngine  │  Business logic
├────────────────┴─────────────┴───────────────┤
│              Protocol Layer                   │  Codec, CRC, Base62, DiffEngine
├──────────────────────────────────────────────┤
│         SQLite (AppDatabaseHelper)            │  Persistence
└──────────────────────────────────────────────┘
```

### Design Principles

1. **No framework DI** — `AppController` is a singleton that wires all components together.  
   The Flutter source uses Riverpod; this Java port uses the simpler singleton pattern to avoid adding Dagger/Hilt overhead.

2. **Protocol layer is pure Java** — no Android dependencies in `core/protocol/`. This makes unit testing straightforward without robolectric or device emulation.

3. **Repository is the only DB writer** — all SQLite writes go through `AppRepository`. The `TransportManager` calls repository methods after decoding; it never accesses the database directly.

4. **Listeners over LiveData** — `TransportManager` and `RulesEngine` notify via simple listener interfaces. Fragments register on `onStart()` and unregister on `onStop()` to avoid leaks.

---

## AppController Lifecycle

```
Application.onCreate()
  └── AppController.get(context)          ← lazy singleton init
        ├── AppDatabaseHelper.getInstance()
        ├── AppRepository(dbHelper)
        ├── TransportManager.get(context)
        │     └── registers AppRepository as its storage target
        └── RulesEngine(repository)
              └── called by TransportManager after each message batch
```

---

## Incoming Packet Flow

```
NetworkThread (UDP) / MqttCallback (MQTT)
  │
  │  raw byte[]
  ▼
PacketDecoder.decode(bytes)
  │  validates CRC-8
  │  parses header (CMD/AID/TID/SEQ/LEN)
  │  returns Result { valid, meta, rawBody }
  ▼
DiffEngine.process(cmd, aid, rawBody)
  │  FULL  → stores template[aid], returns parsed readings
  │  DIFF  → applies delta to template[aid], returns full readings
  │  HEART → returns template[aid] unchanged
  ▼
AppRepository
  │  upsertDevice(aid, status="online", lastSeenMs=now)
  │  insertSensorDataBatch(aid, readings)
  ▼
RulesEngine.evaluate(aid, readings)
  │  for each enabled rule matching a reading's sensorId:
  │    if condition(reading.value, rule.threshold) && not in cooldown:
  │      action = "create_alert" → repository.insertAlert()
  │      action = "send_command" → transportManager.sendUdp(...)
  │      action = "log_only"     → repository.logOperation(...)
  ▼
MessageListeners.onMessage(DeviceMessage)
  │  called on background thread
  │  UI fragments call getActivity().runOnUiThread(this::refresh)
```

---

## Outgoing Packet Flow

```
UI (SendFragment button click)
  │
  ▼
PacketBuilder.buildXxx(aid, tid, seq, ...)
  │  constructs byte[] with header + body + CRC8
  ▼
TransportManager.sendUdp(bytes, host, port)
  │  DatagramSocket.send() on caller thread (OK from UI — short op)
  │
  └── also: TransportManager.publishMqtt(bytes, topic)
              MqttClient.publish() on caller thread
```

---

## Database Schema Decisions

### Why SQLite and not Room?

- Keeps the dependency count minimal
- `AppDatabaseHelper` is ~200 lines and covers all needed queries
- Room's annotation processor would increase build time with no significant benefit at this scale

### Data Retention

`sensor_data` is automatically trimmed to **7 days** during each `insertSensorDataBatch()` call:

```java
db.execSQL("DELETE FROM sensor_data WHERE timestamp_ms < ?",
           new Object[]{System.currentTimeMillis() - 7 * 24 * 3600 * 1000L});
```

This prevents unbounded growth without requiring a separate background job.

---

## Threading Model

| Thread | Responsibility |
|--------|---------------|
| Main (UI) | Fragment lifecycle, view updates |
| `udpThread` | Blocking `DatagramSocket.receive()` loop |
| `mqttCallbackThread` | Eclipse Paho callback thread |
| `scheduler` (single-thread) | 1-second stats tick via `ScheduledExecutorService` |

All listener callbacks arrive on background threads. UI updates must be dispatched via `runOnUiThread()`.

---

## Theming System

Themes are implemented as **Material3 style overlays** applied before `super.onCreate()`:

```
res/values/themes.xml
  Theme.GsynJava                 ← base Material3 theme

res/values/theme_overlays.xml
  ThemeOverlay.Accent.Teal       ← colorPrimary = teal
  ThemeOverlay.Accent.Indigo     ← colorPrimary = indigo
  ...
  ThemeOverlay.Bg.Dark           ← colorBackground = #1A1A2E, isLight=false
  ThemeOverlay.Bg.Warm           ← colorBackground = #FFF8F0
  ...
```

`AppThemeConfig` reads the user's preset from `SharedPreferences` and applies the correct overlay pair. The background overlay also drives the Google Maps dark style and the NavigationView background colour.

---

## Fragment Navigation Model

```
MainActivity
  ├── fragment_container (FrameLayout)  ← host for all fragments
  ├── BottomNavigationView              ← tabs: Dashboard/Devices/Alerts/Send/Settings
  └── NavigationView (DrawerLayout)     ← extensions: Map/History/Rules/Health

Tab selection:
  bottomNav.setOnItemSelectedListener → showFragment(new XxxFragment(), title, subtitle)
  Also syncs drawer checked item

Drawer selection:
  onNavigationItemSelected:
    - Main tabs  → bottomNav.setSelectedItemId(id)  (triggers above)
    - Extensions → showFragment(new XxxMirrorFragment(), ...)
                   deselects all bottom nav items
```

Extension pages (Map, History, Rules, Health) are loaded **directly into `fragment_container`** within `MainActivity`. This keeps the drawer and bottom nav visible at all times — navigating via the secondary `SecondaryActivity` was removed because it hid the sidebar.

---

## Google Maps Integration

`MapMirrorFragment` uses `SupportMapFragment` added programmatically via `getChildFragmentManager()`:

```java
SupportMapFragment mapFrag = SupportMapFragment.newInstance();
getChildFragmentManager().beginTransaction()
    .add(R.id.mapContainer, mapFrag, "MAP_FRAG")
    .commitNow();          // ← commitNow() is critical
mapFrag.getMapAsync(this);
```

`commitNow()` (not `commit()`) ensures the fragment is synchronously attached before `getMapAsync` is called, preventing the blank-map race condition.

The API key is injected at build time via `manifestPlaceholders` from `local.properties`. The debug build does **not** append `.debug` to the application ID, ensuring the API key's Android app restriction matches both debug and release builds.

---

## Dashboard Card System (v1.2.0+)

The Dashboard is a configurable, drag-reorderable card list. Full details in [DASHBOARD_CARDS.md](DASHBOARD_CARDS.md).

```
DashboardFragment
    ├── DashboardCardConfig   ← JSON-serialised card order in SharedPreferences
    ├── DashboardCardAdapter  ← RecyclerView adapter with 9 view types
    │     └── Snapshot        ← immutable data snapshot, built once per refresh()
    └── ItemTouchHelper       ← drag callback: moveItem() → persistOrderFromAdapter()
```

Key design decision: **Snapshot as a data contract.** Rather than letting each ViewHolder query the database, `DashboardFragment.refresh()` builds a single `Snapshot` object once and passes it to every card. This means:
- Database is queried exactly once per refresh cycle, not N times
- ViewHolders are stateless — they can be recycled without side effects
- Adding a new card type only requires adding fields to `Snapshot` and a read in `refresh()`

---

## Localisation System (v1.2.0+)

Per-app language switching uses `AppCompatDelegate.setApplicationLocales()` (AppCompat 1.6+):

```java
// Switch language — persists across restarts, triggers Activity recreation automatically
LocaleListCompat locales = LocaleListCompat.forLanguageTags("zh");
AppCompatDelegate.setApplicationLocales(locales);
```

Android 13+ also requires `res/xml/locale_config.xml` and `android:localeConfig` in the manifest.

**All user-visible text** must be in `res/values/strings.xml` (EN) and `res/values-zh/strings.xml` (ZH). Hardcoded strings in Java or XML will not respond to language switching.
