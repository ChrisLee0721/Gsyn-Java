# Android Java Developer Guide — Gsyn Java

> 中文版请见 [ANDROID_DEV_GUIDE_zh.md](ANDROID_DEV_GUIDE_zh.md)

This guide covers the UI layer, data layer, theming, i18n, and key component implementations
for developers working on or extending Gsyn Java.

---

## Table of Contents

1. [Project Structure](#1-project-structure)
2. [Build Configuration](#2-build-configuration)
3. [App Entry & Singleton Wiring](#3-app-entry--singleton-wiring)
4. [Navigation Architecture (MainActivity)](#4-navigation-architecture-mainactivity)
5. [Database Design (AppDatabaseHelper)](#5-database-design-appdatabasehelper)
6. [Data Access Layer (AppRepository)](#6-data-access-layer-apprepository)
7. [Rules Engine (RulesEngine)](#7-rules-engine-rulesengine)
8. [Dashboard Card System](#8-dashboard-card-system)
9. [Theming System (AppThemeConfig)](#9-theming-system-appthemeconfig)
10. [Internationalisation (LocaleHelper)](#10-internationalisation-localehelper)
11. [ViewBinding Conventions](#11-viewbinding-conventions)
12. [Fragment Lifecycle & Listener Management](#12-fragment-lifecycle--listener-management)
13. [Custom View — MiniTrendChartView](#13-custom-view--minitrendchartview)
14. [Google Maps Integration](#14-google-maps-integration)
15. [SharedPreferences Key Reference](#15-sharedpreferences-key-reference)

---

## 1. Project Structure

```
app/src/main/java/com/opensynaptic/gsynjava/
├── AppController.java              ← Top-level singleton; wires all components
├── core/
│   ├── AppThemeConfig.java         ← Theme preset enums + SharedPreferences I/O
│   ├── AppThresholds.java          ← Sensor alert threshold constants
│   ├── LocaleHelper.java           ← Language switching (AppCompatDelegate API)
│   ├── AppColors.java              ← Runtime colour utilities
│   └── protocol/                   ← Pure-Java codec layer (zero Android deps)
│       ├── PacketDecoder.java
│       ├── PacketBuilder.java
│       ├── DiffEngine.java
│       ├── BodyParser.java
│       ├── Base62Codec.java
│       ├── OsCmd.java
│       ├── OsCrc.java
│       ├── ProtocolConstants.java
│       └── GeohashDecoder.java
├── data/
│   ├── AppDatabaseHelper.java      ← SQLiteOpenHelper — schema + migrations
│   ├── AppRepository.java          ← All database reads/writes (singleton)
│   └── Models.java                 ← Plain Java POJOs (no ORM annotations)
├── rules/
│   └── RulesEngine.java            ← Threshold automation evaluation & execution
├── transport/
│   └── TransportManager.java       ← UDP + MQTT send/receive (singleton)
└── ui/
    ├── MainActivity.java           ← DrawerLayout + BottomNav host Activity
    ├── SecondaryActivity.java      ← Deprecated, kept for compatibility
    ├── common/
    │   ├── BaseSecondaryFragment.java  ← Base class for drawer extension pages
    │   └── UiFormatters.java           ← Time, number, unit formatting helpers
    ├── dashboard/
    │   ├── DashboardFragment.java      ← Dashboard; implements two listener interfaces
    │   ├── DashboardCardAdapter.java   ← Multi-type RecyclerView adapter + Snapshot
    │   ├── DashboardCardConfig.java    ← Card config serialisation (JSON ↔ SharedPrefs)
    │   └── DashboardCardItem.java      ← Card data class; defines Type enum (9 types)
    ├── devices/                    ← Device list + detail bottom sheet
    ├── alerts/                     ← Alert list + acknowledgement
    ├── send/                       ← Command builder (3 tabs)
    ├── settings/                   ← UDP/MQTT config + theme/language settings
    ├── mirror/
    │   ├── MapMirrorFragment.java      ← Google Maps + device markers
    │   ├── HistoryMirrorFragment.java  ← Sensor history table + CSV export
    │   ├── RulesMirrorFragment.java    ← Rules CRUD + toggle
    │   └── HealthMirrorFragment.java   ← Transport status + DB stats
    └── widget/
        └── MiniTrendChartView.java     ← Pure Canvas trend line chart
```

---

## 2. Build Configuration

**Key settings in `app/build.gradle`:**

```groovy
android {
    compileSdk 34
    defaultConfig {
        minSdk 24          // Android 7.0+
        targetSdk 34
        // Inject Maps API key from local.properties at build time
        manifestPlaceholders = [mapsApiKey: MAPS_API_KEY]
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding true   // Generates binding classes for all layout XMLs
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
    implementation 'com.google.android.gms:play-services-maps:18.2.0'
}
```

**Maps API Key injection pipeline:**

```
local.properties
  MAPS_API_KEY=AIzaSy...
        ↓ (build.gradle reads)
manifestPlaceholders["mapsApiKey"]
        ↓ (AndroidManifest.xml references)
<meta-data android:name="com.google.android.geo.API_KEY"
           android:value="${mapsApiKey}" />
```

---

## 3. App Entry & Singleton Wiring

All singletons are initialised lazily on the first call to `AppController.get(context)`, in strict order:

```
Activity.onCreate()
  │
  └── AppController.get(this)
        │
        ├── 1. SharedPreferences "gsyn_java_prefs"
        ├── 2. AppRepository.get(context)
        │         └── AppDatabaseHelper(context)  // triggers SQLite onCreate()
        ├── 3. TransportManager.get(context)
        │         └── ScheduledExecutorService  (1-second stats ticker)
        ├── 4. RulesEngine(repository, transportManager)
        │         └── lastTriggered HashMap<Long, Long>
        ├── 5. transportManager.addMessageListener(appController)
        └── 6. repository.seedDefaultRuleIfEmpty()
                  └── inserts "TEMP > 50 create_alert" rule if rules table is empty
```

**Why no Application subclass?**
The project deliberately avoids early initialisation in `Application.onCreate()` to prevent
side-effects in testing. `AppController.get(context)` is a lazy singleton, constructed only
when first called from an Activity.

---

## 4. Navigation Architecture (MainActivity)

### Layout Structure

```xml
DrawerLayout (activity_main.xml)
├── CoordinatorLayout
│   ├── MaterialToolbar (binding.toolbar)
│   ├── FrameLayout (binding.fragmentContainer) ← all Fragments load here
│   └── BottomNavigationView (binding.bottomNav)
└── NavigationView (binding.navView)             ← side drawer
```

### Fragment Replacement

```java
// showFragment() — called by all navigation paths
getSupportFragmentManager().beginTransaction()
    .replace(R.id.fragment_container, fragment)
    .commit();
```

Every navigation is a `replace()` (not `add()`). The previous Fragment goes through
`onStop()` → `onDestroyView()` → `onDestroy()`. Listeners unregistered in `onStop()`
are cleaned up automatically — no leaks.

### Bottom Nav ↔ Drawer Sync

| User action | Handling |
|-------------|---------|
| Tap bottom nav tab | `onBottomNavSelected()` → `showFragment()` + sync drawer checked item |
| Tap drawer main menu | `onNavigationItemSelected()` → `bottomNav.setSelectedItemId()` → triggers bottom nav callback |
| Tap drawer extension page | Direct `showFragment()` + clear bottom nav selection (via `setGroupCheckable` trick) |

**Clearing bottom nav selection:**
```java
binding.bottomNav.getMenu().setGroupCheckable(0, true, false);  // disable mutual exclusion
for (int i = 0; i < binding.bottomNav.getMenu().size(); i++) {
    binding.bottomNav.getMenu().getItem(i).setChecked(false);
}
binding.bottomNav.getMenu().setGroupCheckable(0, true, true);   // restore mutual exclusion
```

### Theme Overlay Application Order

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    // ① BEFORE super.onCreate() — Material3 reads theme attrs during super
    getTheme().applyStyle(AppThemeConfig.getAccentOverlayRes(...), true);
    getTheme().applyStyle(AppThemeConfig.getBgOverlayRes(...), true);

    super.onCreate(savedInstanceState);    // ← super reads theme here

    // ② AFTER super.onCreate()
    AppController.get(this);
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    // ③ AFTER setContentView() — sync status/nav bar colours
    AppThemeConfig.applyBgToWindow(getWindow(), this);
}
```

---

## 5. Database Design (AppDatabaseHelper)

### Database File

- **Name**: `gsyn_java.db` (`AppDatabaseHelper.DB_NAME`)
- **Version**: `1` (`AppDatabaseHelper.DB_VERSION`)
- **Location**: `data/data/com.opensynaptic.gsynjava/databases/gsyn_java.db`

### Full Schema

```sql
CREATE TABLE devices (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    aid             INTEGER UNIQUE NOT NULL,      -- 4-byte device ID (uint32 in protocol)
    name            TEXT    NOT NULL DEFAULT '',
    type            TEXT    NOT NULL DEFAULT 'sensor',
    lat             REAL    DEFAULT 0.0,
    lng             REAL    DEFAULT 0.0,
    status          TEXT    NOT NULL DEFAULT 'offline',
    transport_type  TEXT    NOT NULL DEFAULT 'udp',
    last_seen_ms    INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE sensor_data (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    device_aid   INTEGER NOT NULL,
    sensor_id    TEXT    NOT NULL,
    unit         TEXT    NOT NULL DEFAULT '',
    value        REAL    NOT NULL,
    raw_b62      TEXT    DEFAULT '',
    timestamp_ms INTEGER NOT NULL
);
CREATE INDEX idx_sensor_data_aid_ts ON sensor_data(device_aid, timestamp_ms);

CREATE TABLE alerts (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    device_aid   INTEGER NOT NULL,
    sensor_id    TEXT    NOT NULL DEFAULT '',
    level        INTEGER NOT NULL DEFAULT 0,      -- 0=Info, 1=Warning, 2=Critical
    message      TEXT    NOT NULL DEFAULT '',
    acknowledged INTEGER NOT NULL DEFAULT 0,      -- 0=pending, 1=acknowledged
    created_ms   INTEGER NOT NULL
);
CREATE INDEX idx_alerts_aid_level ON alerts(device_aid, level);

CREATE TABLE rules (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    name              TEXT    NOT NULL DEFAULT '',
    device_aid_filter INTEGER DEFAULT NULL,       -- NULL = match all devices
    sensor_id_filter  TEXT    DEFAULT NULL,       -- NULL = match all sensors
    operator          TEXT    NOT NULL DEFAULT '>', -- >, <, >=, <=, ==, !=
    threshold         REAL    NOT NULL DEFAULT 0.0,
    action_type       TEXT    NOT NULL DEFAULT 'create_alert',
    action_payload    TEXT    NOT NULL DEFAULT '{}', -- JSON parameters
    enabled           INTEGER NOT NULL DEFAULT 1,
    cooldown_ms       INTEGER NOT NULL DEFAULT 60000
);

CREATE TABLE operation_logs (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user         TEXT    NOT NULL DEFAULT 'system',
    action       TEXT    NOT NULL DEFAULT '',
    details      TEXT    NOT NULL DEFAULT '',
    timestamp_ms INTEGER NOT NULL
);

CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT UNIQUE NOT NULL,
    password_hash TEXT    NOT NULL,    -- SHA-256 hex string
    role          TEXT    NOT NULL DEFAULT 'viewer',  -- 'admin' | 'viewer'
    created_ms    INTEGER NOT NULL
);

-- Reserved for future use
CREATE TABLE dashboard_layout (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER,
    layout_json TEXT    NOT NULL DEFAULT '{}'
);

CREATE TABLE pending_commands (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    device_aid  INTEGER NOT NULL,
    frame_hex   TEXT    NOT NULL,
    created_ms  INTEGER NOT NULL
);
```

### Seed Data

A default admin account is created when the database is first opened:
- **Username**: `admin`
- **Password hash**: `8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918` (SHA-256 of `"admin"`)
- **Role**: `admin`

---

## 6. Data Access Layer (AppRepository)

### Concurrency

All public methods are `synchronized` — safe for concurrent access from the UDP thread,
MQTT callback thread, and the UI thread.

### Key Method Details

#### upsertDevice()

```java
// Coordinate-preserving upsert:
// Only update lat/lng if the new message carries non-zero coordinates,
// preventing heartbeat packets from overwriting previously known location.
boolean hasNewCoords = Math.abs(device.lat) > 1e-7 || Math.abs(device.lng) > 1e-7;
values.put("lat", hasNewCoords ? device.lat : existingLat);
values.put("lng", hasNewCoords ? device.lng : existingLng);
```

#### getOnlineDeviceCount()

```java
// "Online" = last_seen_ms within the last 5 minutes
long cutoff = System.currentTimeMillis() - 5 * 60_000L;
SELECT COUNT(*) FROM devices WHERE last_seen_ms > ?
```

#### getLatestReadingsByDevice()

```sql
-- One row per sensor_id — the most recently inserted row (MAX(id))
SELECT * FROM sensor_data
WHERE id IN (
    SELECT MAX(id) FROM sensor_data
    WHERE device_aid = ?
    GROUP BY sensor_id
)
```

#### saveRule()

```java
// INSERT or UPDATE based on rule.id:
if (rule.id > 0) → db.update(...)   // edit existing rule
else              → db.insert(...)  // create new rule
```

#### exportHistoryCsv()

```java
// Exports last 24 hours, up to 500 rows
// File: getExternalFilesDir(null)/export_{timestamp}.csv
// Columns: timestamp, device_aid, sensor_id, value, unit
```

### Repository API Quick Reference

| Method | Key SQL | Notes |
|--------|---------|-------|
| `upsertDevice` | SELECT + UPDATE/INSERT | Preserves existing lat/lng |
| `getAllDevices` | ORDER BY `last_seen_ms DESC` | |
| `getOnlineDeviceCount` | WHERE `last_seen_ms > cutoff` | cutoff = now − 5 min |
| `getLatestReadingsByDevice` | `MAX(id) GROUP BY sensor_id` | One row per sensor |
| `querySensorData` | WHERE `timestamp_ms BETWEEN` LIMIT | |
| `insertAlert` | INSERT | Returns row id |
| `getAlerts(level, limit)` | WHERE `level=?` (nullable) ORDER BY `created_ms DESC` | |
| `saveRule` | INSERT or UPDATE by `rule.id` | |
| `toggleRule` | UPDATE SET `enabled` | |
| `pruneOldData` | DELETE WHERE `timestamp_ms < cutoff` | Returns deleted row count |
| `exportHistoryCsv` | SELECT 24h max 500 rows → File | External storage |

---

## 7. Rules Engine (RulesEngine)

### Cooldown State

`HashMap<Long, Long> lastTriggered` maps `ruleId → last trigger timestamp`.
This is **in-memory only** — cooldown state resets on app restart.

### Alert Level Calculation

```java
// When action_type = "create_alert":
alert.level = reading.value > rule.threshold * 1.5 ? 2 : 1;
//            > 150% of threshold → Critical (2)
//            ≤ 150% of threshold → Warning  (1)
```

### send_command — actionPayload JSON Format

```json
{
    "target_aid": 2,        // target device AID (defaults to triggering device)
    "sensor_id": "CMD",     // sensor ID in the outgoing packet
    "unit": "",             // unit string
    "value": 0.0            // command value
}
```

Builds a `DATA_FULL` frame via `PacketBuilder.buildSensorPacket()` and calls
`transportManager.sendCommand()` — which prefers MQTT if connected, otherwise UDP.

### Evaluation Flow

```
evaluate(message, udpHost, udpPort):
  1. repository.getEnabledRules()      ← reads from DB on every call
  2. for each reading in message.readings:
       for each rule in enabledRules:
         a. Device filter:  rule.deviceAidFilter != null && != message.deviceAid → skip
         b. Sensor filter:  rule.sensorIdFilter != null && !equalsIgnoreCase(reading.sensorId) → skip
         c. Threshold eval: rule.evaluate(reading.value) → false → skip
         d. Cooldown check: now - lastTriggered[rule.id] < rule.cooldownMs → skip
         e. Update lastTriggered[rule.id] = now
         f. execute(rule, message, reading, udpHost, udpPort)
         g. repository.logOperation("rule_triggered", ...)  ← always logged
```

---

## 8. Dashboard Card System

### Card Type Enum (DashboardCardItem.Type)

| Value | Description | Draggable |
|-------|-------------|-----------|
| `HEADER` | Fixed title card (always first) | ❌ |
| `KPI_ROW1` | Total devices + online rate | ✅ |
| `KPI_ROW2` | Active alerts + throughput | ✅ |
| `KPI_ROW3` | Active rules + cumulative messages | ✅ |
| `GAUGES` | Water level + humidity progress bars | ✅ |
| `CHARTS` | Temperature + humidity trend charts | ✅ |
| `ACTIVITY` | Recent alerts + operation log | ✅ |
| `LATEST_READINGS` | Latest raw sensor readings | ✅ |
| `CUSTOM_SENSOR` | User-defined sensor card | ✅ |

### Snapshot Data Contract

`DashboardCardAdapter.Snapshot` is built **once per `refresh()` call** and shared across
all ViewHolders. This guarantees a single database query per refresh cycle:

```java
class Snapshot {
    // KPI counters
    int totalDevices, online, alerts, rules, totalMessages, throughput;

    // Sensor maps (sensor ID upper-cased → SensorData)
    Map<String, SensorData> latestBySensorId;
    // Trend data (sensor ID → last 12 values, chronological order)
    Map<String, List<Float>> trendBySensorId;

    // Built-in sensor shortcut fields
    double latestTemp, latestHum, latestPressure, latestLevel;
    List<Float> tempTrend = new ArrayList<>();
    List<Float> humTrend  = new ArrayList<>();

    // Text summaries
    String subtitle, syncStatus, transportStatus;
    String latestReadingsText, recentAlertsSummary, opsSummary;

    long    latestSampleMs;
    int     readingCount;
    boolean singleModeEnabled;
}
```

### Sensor Matching Logic in refresh()

```java
// Uses contains() rather than equals() to handle variants like T1, TEMP2
if (sid.contains("TEMP") || sid.contains("TMP") || sid.equals("T1"))  → snap.latestTemp
if (sid.contains("HUM")  || sid.equals("H1"))                         → snap.latestHum
if (sid.contains("PRES") || sid.contains("BAR") || sid.equals("P1"))  → snap.latestPressure
if (sid.contains("LEVEL")|| sid.contains("LVL") || sid.equals("L1"))  → snap.latestLevel
```

Data window: **last 24 hours, maximum 50 rows** (ordered `timestamp_ms DESC`).

### Drag-to-Reorder Implementation

```java
ItemTouchHelper.Callback:
  getMovementFlags(): returns 0 for position 0 (HEADER) — not draggable
  onMove():           cardAdapter.moveItem(from, to)
                      → Collections.swap + notifyItemMoved
  clearView():        persistOrderFromAdapter()
                      → rebuilds cardConfig.cards (visible order + hidden cards appended)
                      → cardConfig.save(context) → SharedPreferences
```

### Card Config Serialisation (DashboardCardConfig)

Card order and visibility are serialised as a JSON array in SharedPreferences key `"dashboard_cards"`:

```json
[
  {"type":"HEADER","visible":true,"order":0,"sensorId":"","label":""},
  {"type":"KPI_ROW1","visible":true,"order":1,"sensorId":"","label":""},
  {"type":"CUSTOM_SENSOR","visible":true,"order":5,"sensorId":"CO2","label":"Carbon Dioxide"}
]
```

---

## 9. Theming System (AppThemeConfig)

### Two-Layer Overlay Architecture

```
Theme.GsynJava (themes.xml)              ← Material3 base theme
    +
ThemeOverlay_GsynJava_Accent_Xxx         ← Accent colour (colorPrimary / colorSecondary)
    +
ThemeOverlay_GsynJava_Bg_Xxx             ← Background colour (colorBackground / colorSurface)
```

Both overlays are applied with `getTheme().applyStyle(resId, true)` before `super.onCreate()`.
The second overlay wins any conflicting attribute.

### 8 Accent Colour Presets (ThemePreset)

| Enum | Hex | Label |
|------|-----|-------|
| `DEEP_BLUE` | `#1A73E8` | Deep Blue (default) |
| `TEAL` | `#00897B` | Teal |
| `PURPLE` | `#7B1FA2` | Purple |
| `AMBER` | `#FF8F00` | Amber |
| `RED` | `#D32F2F` | Red |
| `CYAN` | `#0097A7` | Cyan |
| `GREEN` | `#2E7D32` | Green |
| `PINK` | `#C2185B` | Pink |

### 12 Background Presets (BgPreset)

Each preset carries three colour slots (`bgHex` / `surfaceHex` / `cardHex`) and an `isLight` flag:

**Dark (6)**: `DEEP_NAVY` (default), `DARK_SLATE`, `CHARCOAL`, `TRUE_BLACK` (AMOLED), `FOREST_DARK`, `WARM_DARK`

**Light (6)**: `SNOW_WHITE`, `CLOUD_GREY`, `PAPER_CREAM`, `MINT_LIGHT`, `LAVENDER_LIGHT`, `SKY_BLUE`

### Status Bar / Navigation Bar Colour Sync

```java
applyBgToWindow(Window, Context):
    window.setStatusBarColor(bg.bgColor())
    window.setNavigationBarColor(bg.bgColor())
    // Toggle status-bar icon colour based on isLight:
    if (bg.isLight) flags |= SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    else            flags &= ~SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    // Android 8+ (API 26): sync navigation bar icon colour
    if (API >= O) flags |= / &= ~SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
```

---

## 10. Internationalisation (LocaleHelper)

### Implementation

Uses the **AppCompat 1.6+ per-app locale API** — no `attachBaseContext()` override needed:

```java
// Switch language (persisted + triggers Activity recreation automatically)
LocaleHelper.applyAndSave("zh");      // Chinese
LocaleHelper.applyAndSave("en");      // English
LocaleHelper.applyAndSave("system");  // Follow system locale
```

```java
// Under the hood:
AppCompatDelegate.setApplicationLocales(
    LocaleListCompat.forLanguageTags("zh")
);
// AppCompat persists the locale to the system and recreates the Activity
```

### Android 13+ Declaration

`AndroidManifest.xml`:
```xml
<application android:localeConfig="@xml/locale_config" ...>
```

`res/xml/locale_config.xml`:
```xml
<locale-config>
    <locale android:name="en"/>
    <locale android:name="zh"/>
</locale-config>
```

### String Resource Locations

| Path | Language |
|------|----------|
| `res/values/strings.xml` | English (base) |
| `res/values-zh/strings.xml` | Chinese (override) |

All user-visible text must use string resource references. Hardcoded strings in Java or XML
will not respond to language switching.

---

## 11. ViewBinding Conventions

### Enabling

`app/build.gradle`:
```groovy
buildFeatures { viewBinding true }
```

Every layout XML gets a generated binding class: `activity_main.xml` → `ActivityMainBinding`,
`fragment_dashboard.xml` → `FragmentDashboardBinding`, etc.

### In an Activity

```java
private ActivityMainBinding binding;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    // Access views: binding.toolbar, binding.bottomNav, etc.
}
```

### In a Fragment (with leak prevention)

```java
private FragmentDashboardBinding binding;

@Override
public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
    binding = FragmentDashboardBinding.inflate(inflater, container, false);
    return binding.getRoot();
}

@Override
public void onDestroyView() {
    binding = null;  // ← REQUIRED — prevents retaining a reference to a destroyed View
    super.onDestroyView();
}
```

A Fragment's View lifecycle is shorter than the Fragment itself (`onDestroyView` fires before
`onDestroy`). Failing to null the binding will leak the View hierarchy.

---

## 12. Fragment Lifecycle & Listener Management

### Canonical Pattern (from DashboardFragment)

```java
@Override
public void onStart() {
    super.onStart();
    // Register when the Fragment becomes visible
    transportManager.addMessageListener(this);
    transportManager.addStatsListener(this);
    refresh(); // immediate refresh to avoid stale data
}

@Override
public void onStop() {
    // Unregister when the Fragment is no longer visible
    transportManager.removeMessageListener(this);
    transportManager.removeStatsListener(this);
    super.onStop();
}
```

**Why `onStart`/`onStop` rather than `onResume`/`onPause`?**  
In multi-window mode, `onPause` fires when the window loses focus but the Fragment is still
visible. `onStop` reliably marks the Fragment as truly not visible.

### Threading for UI Updates

`TransportManager` callbacks arrive on background threads — all UI work must be dispatched:

```java
@Override
public void onMessage(Models.DeviceMessage message) {
    if (getActivity() != null) {     // ← guard: Fragment may have detached
        getActivity().runOnUiThread(this::refresh);
    }
}
```

---

## 13. Custom View — MiniTrendChartView

Extends `View`. Draws a trend line chart using pure Canvas API — no third-party chart library.

### Core API

```java
chart.setTitle("Temperature");
chart.setChartColor(0xFFFF7043);       // line + gradient fill colour (ARGB)
chart.setSeries(List<Float> values);   // set data, automatically calls invalidate()
```

### Drawing Features

- **Gradient fill**: vertical gradient below the line (chart colour → transparent) via `LinearGradient`
- **Grid lines**: horizontal dashed lines at 20% opacity
- **Extrema dots**: filled circles at the max and min data points (radius 4dp)
- **Auto Y-axis range**: derived from data min/max with 10% padding on each side

### Usage in Dashboard

```java
// Temperature trend (CHARTS card type)
binding.chartTemp.setTitle(getString(R.string.dashboard_temp_title));
binding.chartTemp.setChartColor(0xFFFF7043);
binding.chartTemp.setSeries(snapshot.tempTrend);  // List<Float>, up to 12 points

// Humidity trend
binding.chartHum.setTitle(getString(R.string.dashboard_hum_title));
binding.chartHum.setChartColor(0xFF42A5F5);
binding.chartHum.setSeries(snapshot.humTrend);
```

---

## 14. Google Maps Integration

### Nested SupportMapFragment

```java
// Must use getChildFragmentManager() (not getParentFragmentManager())
// Must use commitNow() (not commit()) to prevent the blank-map race condition
SupportMapFragment mapFrag = SupportMapFragment.newInstance();
getChildFragmentManager().beginTransaction()
    .add(R.id.mapContainer, mapFrag, "MAP_FRAG")
    .commitNow();    // ← synchronous — fragment is attached before getMapAsync is called
mapFrag.getMapAsync(this);  // this implements OnMapReadyCallback
```

### Device Marker Update

```java
private void refreshMarkers() {
    googleMap.clear();
    for (Models.Device device : repository.getAllDevices()) {
        if (Math.abs(device.lat) < 1e-7 && Math.abs(device.lng) < 1e-7) continue;
        boolean online = System.currentTimeMillis() - device.lastSeenMs < 5 * 60_000L;
        BitmapDescriptor icon = BitmapDescriptorFactory.defaultMarker(
            online ? BitmapDescriptorFactory.HUE_GREEN
                   : BitmapDescriptorFactory.HUE_RED);
        googleMap.addMarker(new MarkerOptions()
            .position(new LatLng(device.lat, device.lng))
            .title(device.name)
            .icon(icon));
    }
}
```

Dark map style is applied via `R.raw.map_style_dark` when `BgPreset.isLight == false`.

---

## 15. SharedPreferences Key Reference

### Main Config File (`gsyn_java_prefs`)

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `udp_host` | String | `"127.0.0.1"` | UDP target host for outgoing commands |
| `udp_port` | int | `9876` | UDP port (listen + send) |
| `udp_enabled` | boolean | `false` | Auto-start UDP on launch |
| `mqtt_broker` | String | `""` | MQTT broker hostname |
| `mqtt_port` | int | `1883` | MQTT port |
| `mqtt_topic` | String | `""` | Subscribe topic (empty = `opensynaptic/#`) |
| `mqtt_user` | String | `""` | MQTT username |
| `mqtt_pass` | String | `""` | MQTT password |
| `mqtt_enabled` | boolean | `false` | Auto-connect MQTT on launch |
| `single_device_mode` | boolean | `false` | Dashboard single-device mode |
| `dashboard_cards` | String | (default JSON) | Card order and visibility JSON array |

### Theme Config File (`app_theme_prefs`)

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `app_theme_preset` | String | `"DEEP_BLUE"` | ThemePreset enum name |
| `app_bg_preset` | String | `"DEEP_NAVY"` | BgPreset enum name |

