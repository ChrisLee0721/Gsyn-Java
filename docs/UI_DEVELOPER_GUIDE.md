# Gsyn-Java UI Layer — Developer Guide

> **Package root:** `com.opensynaptic.gsynjava.ui`  
> **Language:** Java  
> **Min SDK / Target SDK:** See `build.gradle`  
> **UI toolkit:** Material Design 3 (Material Components for Android) + AndroidX AppCompat  
> **Architecture pattern:** Single-Activity + multiple Fragments, manual navigation (no Jetpack Navigation component)

---

## Table of Contents

1. [Package Structure Overview](#1-package-structure-overview)
2. [Activity Layer](#2-activity-layer)
   - 2.1 [MainActivity](#21-mainactivity)
   - 2.2 [SecondaryActivity](#22-secondaryactivity)
3. [Fragment Layer](#3-fragment-layer)
   - 3.1 [alerts — AlertsFragment](#31-alerts--alertsfragment)
   - 3.2 [common — CardRowAdapter & UiFormatters](#32-common--cardrowadapter--uiformatters)
   - 3.3 [dashboard — DashboardFragment](#33-dashboard--dashboardfragment)
   - 3.4 [dashboard — DashboardCardAdapter](#34-dashboard--dashboardcardadapter)
   - 3.5 [dashboard — DashboardCardConfig](#35-dashboard--dashboardcardconfig)
   - 3.6 [dashboard — DashboardCardItem](#36-dashboard--dashboardcarditem)
   - 3.7 [devices — DevicesFragment](#37-devices--devicesfragment)
   - 3.8 [mirror — HealthMirrorFragment](#38-mirror--healthmirrorfragment)
   - 3.9 [mirror — HistoryMirrorFragment](#39-mirror--historymirrorfragment)
   - 3.10 [mirror — MapMirrorFragment](#310-mirror--mapmirrorfragment)
   - 3.11 [mirror — RulesMirrorFragment](#311-mirror--rulesmirrorfragment)
   - 3.12 [send — SendFragment](#312-send--sendfragment)
   - 3.13 [settings — SettingsFragment](#313-settings--settingsfragment)
4. [Custom View Layer](#4-custom-view-layer)
   - 4.1 [widget — MiniTrendChartView](#41-widget--minitrendchartview)
5. [Navigation Architecture](#5-navigation-architecture)
6. [Theme & Styling System](#6-theme--styling-system)
7. [Data Flow Through the UI](#7-data-flow-through-the-ui)
8. [Naming Conventions & Coding Style](#8-naming-conventions--coding-style)
9. [Extending the Dashboard](#9-extending-the-dashboard)
10. [Common Pitfalls & FAQ](#10-common-pitfalls--faq)

---

## 1. Package Structure Overview

```
ui/
├── MainActivity.java               ← Single primary host activity
├── SecondaryActivity.java          ← Lightweight detail host activity
├── alerts/
│   └── AlertsFragment.java         ← Alert list, filtering, acknowledgement
├── common/
│   ├── CardRowAdapter.java         ← Reusable ListView adapter (title/subtitle/meta/badge)
│   └── UiFormatters.java           ← Pure-static formatting helpers
├── dashboard/
│   ├── DashboardFragment.java      ← Main dashboard host + live data orchestration
│   ├── DashboardCardAdapter.java   ← RecyclerView adapter for configurable card list
│   ├── DashboardCardConfig.java    ← Persistence layer for card order/visibility
│   └── DashboardCardItem.java      ← Data model for one card slot
├── devices/
│   └── DevicesFragment.java        ← Device list with live search & detail dialog
├── mirror/
│   ├── HealthMirrorFragment.java   ← System health / transport stats + DB prune
│   ├── HistoryMirrorFragment.java  ← Sensor data history (last 24 h) + CSV export
│   ├── MapMirrorFragment.java      ← Google Maps device location map with auto-refresh
│   └── RulesMirrorFragment.java    ← Automation rules CRUD + operation log
├── send/
│   └── SendFragment.java           ← Packet builder / manual protocol test bench
├── settings/
│   └── SettingsFragment.java       ← App settings: transport, theme, locale, dashboard
└── widget/
    └── MiniTrendChartView.java     ← Lightweight canvas-drawn sparkline/trend chart
```

**Design philosophy:**  
All fragments are self-contained: they obtain their dependencies directly from `AppController.get(context)` (a singleton application controller exposing `repository()` and `transport()`). No dependency injection framework is used — by design, this keeps the build simple and the startup fast for IoT edge use.

---

## 2. Activity Layer

### 2.1 `MainActivity`

**File:** `ui/MainActivity.java`  
**Superclass:** `AppCompatActivity`  
**Implements:** `NavigationView.OnNavigationItemSelectedListener`

#### Purpose
The single primary screen of the application. It hosts:
- A **Toolbar** at the top (title + subtitle), wired to a **DrawerLayout**.
- A **NavigationView** (side drawer) for secondary/advanced navigation.
- A **BottomNavigationView** for primary tab navigation.
- A **FrameLayout** (`R.id.fragment_container`) into which all Fragment transactions are committed.

#### Lifecycle & Theme Injection
```java
// CRITICAL: Theme overlays MUST be applied before super.onCreate()
getTheme().applyStyle(AppThemeConfig.getAccentOverlayRes(...), true);
getTheme().applyStyle(AppThemeConfig.getBgOverlayRes(...), true);
super.onCreate(savedInstanceState);
```
`AppThemeConfig.applyBgToWindow(getWindow(), this)` is called **after** `setContentView()` to synchronise status-bar and navigation-bar colours with the selected background preset.

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `binding` | `ActivityMainBinding` | View-binding object generated from `activity_main.xml` |
| `drawerToggle` | `ActionBarDrawerToggle` | Manages the hamburger ↔ back-arrow animation for the drawer |

#### Navigation Mapping

| Bottom Nav item ID | Drawer item ID | Fragment loaded |
|--------------------|----------------|-----------------|
| `R.id.nav_dashboard` | `R.id.nav_main_dashboard` | `DashboardFragment` |
| `R.id.nav_devices` | `R.id.nav_main_devices` | `DevicesFragment` |
| `R.id.nav_alerts` | `R.id.nav_main_alerts` | `AlertsFragment` |
| `R.id.nav_send` | `R.id.nav_main_send` | `SendFragment` |
| `R.id.nav_settings` | `R.id.nav_main_settings` | `SettingsFragment` |
| *(drawer only)* | `R.id.nav_drawer_map` | `MapMirrorFragment` |
| *(drawer only)* | `R.id.nav_drawer_history` | `HistoryMirrorFragment` |
| *(drawer only)* | `R.id.nav_drawer_rules` | `RulesMirrorFragment` |
| *(drawer only)* | `R.id.nav_drawer_health` | `HealthMirrorFragment` |

#### Key Methods

| Method | Visibility | Notes |
|--------|-----------|-------|
| `onCreate(Bundle)` | `protected` | Full activity setup; theme, binding, drawer, bottom nav |
| `onBottomNavSelected(MenuItem)` | `private` | Returns `boolean`; dispatches to `showFragment()` |
| `onNavigationItemSelected(MenuItem)` | `public` | Side drawer handler; mirrors tabs or loads drawer-only fragments |
| `showFragment(Fragment, String, String)` | `private` | Sets toolbar title/subtitle and commits `replace()` transaction |

#### Synchronisation between Drawer and Bottom Nav
When the user taps a bottom nav item, the corresponding drawer item is checked via `binding.navView.setCheckedItem()`. When the user taps a **drawer-exclusive** item (Map, History, Rules, Health), all bottom nav items are temporarily unchecked by toggling group-checkable state:
```java
binding.bottomNav.getMenu().setGroupCheckable(0, true, false); // allow manual deselect
for (int i = 0; i < binding.bottomNav.getMenu().size(); i++) {
    binding.bottomNav.getMenu().getItem(i).setChecked(false);
}
binding.bottomNav.getMenu().setGroupCheckable(0, true, true);  // restore
```

---

### 2.2 `SecondaryActivity`

**File:** `ui/SecondaryActivity.java`  
**Superclass:** `AppCompatActivity`

#### Purpose
A lightweight "detail" container for the four mirror fragments when they need to run independently (e.g. launched from a notification deep-link or an external intent). Unlike `MainActivity` it has **no bottom navigation bar and no drawer** — only a toolbar with a back arrow.

#### Constants (Intent Extras)

| Constant | Value | Meaning |
|----------|-------|---------|
| `EXTRA_MODE` | `"mode"` | Selects which fragment to host |
| `MODE_HISTORY` | `"history"` | Host `HistoryMirrorFragment` |
| `MODE_MAP` | `"map"` | Host `MapMirrorFragment` |
| `MODE_RULES` | `"rules"` | Host `RulesMirrorFragment` |
| `MODE_HEALTH` | `"health"` | Host `HealthMirrorFragment` |

#### Factory Method
```java
Intent intent = SecondaryActivity.intent(context, SecondaryActivity.MODE_MAP, R.string.title_map);
startActivity(intent);
```
The static `intent(Context, String, int)` factory builds a properly-formed intent with both mode and the localised title string, avoiding duplication of extra keys at call sites.

#### Fragment Resolution
`fragmentForMode(String mode)` and `subtitleFor(String mode)` both follow the same switch-like logic. The default case falls through to `HistoryMirrorFragment`.

---

## 3. Fragment Layer

### 3.1 `alerts` — `AlertsFragment`

**File:** `ui/alerts/AlertsFragment.java`  
**Layout:** `fragment_alerts.xml`  
**Dependencies:** `AppRepository`, `CardRowAdapter`, `UiFormatters`

#### Purpose
Displays all alerts stored in the local database. Provides:
- Live **summary row** (Critical / Warning / Info counts + unacknowledged total).
- **Spinner filter** to show all / Critical / Warning / Info.
- **SwipeRefreshLayout** for manual refresh.
- **ListView** with `CardRowAdapter` rows; tapping an unacknowledged alert calls `repository.acknowledgeAlert(id)`.

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `binding` | `FragmentAlertsBinding` | View-binding; nullified in `onDestroyView()` |
| `repository` | `AppRepository` | Data access; obtained from `AppController` |
| `currentAlerts` | `List<Models.AlertItem>` | Currently displayed snapshot (for click mapping) |
| `adapter` | `CardRowAdapter` | ListView adapter |

#### Severity Level Mapping

| Spinner position | Level int | Label |
|-----------------|-----------|-------|
| 0 | `null` (all) | All |
| 1 | `2` | Critical |
| 2 | `1` | Warning |
| 3 | `0` | Info |

The repository method `getAlerts(Integer level, int limit)` accepts `null` to fetch all levels.

#### Badge Color Mapping
```java
int color = ctx.getColor(
    a.level == 2 ? R.color.gsyn_danger :
    a.level == 1 ? R.color.gsyn_warning :
                   R.color.gsyn_info);
```

#### Lifecycle Hooks
- `onCreateView` — inflates layout, wires adapter, spinner, swipe refresh.
- `onStart` — triggers first `load()`.
- `onDestroyView` — nullifies `binding` to prevent memory leaks.

---

### 3.2 `common` — `CardRowAdapter` & `UiFormatters`

#### `CardRowAdapter`

**File:** `ui/common/CardRowAdapter.java`  
**Superclass:** `BaseAdapter` (classic ListView adapter, **not** RecyclerView)

##### Purpose
A generic, reusable ListView adapter that renders `item_card_row.xml` items. Every displayed list in the app (Alerts, Devices, History, Health, Rules) reuses this same adapter. The only interface is the `Row` data class.

##### `Row` Data Class

| Field | Type | Notes |
|-------|------|-------|
| `title` | `String` | Primary text, always visible |
| `subtitle` | `String` | Secondary text; hidden if null/empty |
| `meta` | `String` | Tertiary text (bottom); hidden if null/empty |
| `badge` | `String` | Tag pill text (top-right); hidden if null/empty |
| `badgeColor` | `@ColorInt int` | Background tint of the badge pill |
| `badgeTextColor` | `@ColorInt int` | Text colour inside the pill |

##### ViewHolder Pattern
```java
private static class ViewHolder {
    final MaterialCardView card;
    final TextView title, subtitle, meta, badge;
}
```
Follows the classic ViewHolder pattern (pre-RecyclerView). `convertView.setTag(holder)` caches the holder to avoid `findViewById` on every scroll.

##### Badge Rendering
`bindBadge()` builds a `GradientDrawable` with:
- Fill: `ColorUtils.setAlphaComponent(bgColor, 50)` — semi-transparent fill.
- Stroke: `ColorUtils.setAlphaComponent(bgColor, 130)` — slightly more opaque border, width = max(1, 1dp).
- Corner radius: `14dp * density`.

Card border is also tinted: `holder.card.setStrokeColor(ColorUtils.setAlphaComponent(row.badgeColor, 110))`.

---

#### `UiFormatters`

**File:** `ui/common/UiFormatters.java`  
**Type:** `final` utility class with private constructor (no instances)

All methods are `public static`. The class is **thread-safe** except where noted.

| Method | Signature | Behaviour |
|--------|-----------|-----------|
| `formatDateTime` | `(long ms) → String` | Returns locale-aware date/time string or `"N/A"` for `ms ≤ 0`. Synchronised on a static `DateFormat` instance. |
| `formatRelativeTime` | `(long ms) → String` | Delegates to `DateUtils.getRelativeTimeSpanString` with minute granularity and abbreviated relative format. Returns `"—"` for `ms ≤ 0`. |
| `formatSensorSummary` | `(List<SensorData>) → String` | Formats up to 3 sensor readings as `"sensorId value unit  ·  …"`. Appends `"+N more"` if list has more than 3. |
| `trimNumber` | `(double) → String` | Returns integer string if fractional part `< 0.0001`, otherwise 2 decimal places. |
| `safe` | `(String) → String` | Null-safe trim; never returns `null`. |
| `upperOrFallback` | `(String, String) → String` | Upper-cases the string if non-empty, else returns `fallback`. |

> **Important:** `formatDateTime` synchronises on a static `DateFormat` because `DateFormat` is not thread-safe. All callers from the UI thread satisfy this, but the `synchronized` block is there as a safety net.

---

### 3.3 `dashboard` — `DashboardFragment`

**File:** `ui/dashboard/DashboardFragment.java`  
**Layout:** `fragment_dashboard.xml`  
**Implements:** `TransportManager.MessageListener`, `TransportManager.StatsListener`

#### Purpose
The application's primary screen. Hosts a **configurable, drag-to-reorder RecyclerView** of dashboard cards plus an optional **single-device full-screen view**. Receives real-time updates from the transport layer.

#### Fields

| Field | Type | Purpose |
|-------|------|---------|
| `binding` | `FragmentDashboardBinding` | View-binding |
| `repository` | `AppRepository` | Data reads |
| `transportManager` | `TransportManager` | Live message & stats subscription |
| `lastMsgPerSecond` | `int` | Cached throughput from last stats callback |
| `cardAdapter` | `DashboardCardAdapter` | RecyclerView adapter |
| `cardConfig` | `DashboardCardConfig` | Persisted card order/visibility |
| `touchHelper` | `ItemTouchHelper` | Drag-and-drop support |

#### Lifecycle Flow

```
onCreateView()
  ├─ setupRecyclerView()   → DashboardCardConfig.load(), attach ItemTouchHelper
  ├─ setupFab()            → wire FAB to showAddSensorDialog()
  └─ return binding.root

onStart()
  ├─ transportManager.addMessageListener(this)
  ├─ transportManager.addStatsListener(this)
  └─ refresh()

onStop()
  ├─ transportManager.removeMessageListener(this)
  └─ transportManager.removeStatsListener(this)

onDestroyView()
  └─ binding = null
```

#### Drag-to-Reorder (`ItemTouchHelper.Callback`)
- Only vertical drag (`UP | DOWN`) is allowed — no swipe-to-dismiss.
- Position 0 (the `HEADER` card) is **protected**: `getMovementFlags()` returns `0` for it.
- During drag: item alpha = 0.85, elevation = 16 dp (visual lift).
- On `clearView` (drop): `persistOrderFromAdapter()` writes the new order to `DashboardCardConfig`.

#### FAB — Adding Custom Sensor Card
`showAddSensorDialog()` presents a `MaterialAlertDialogBuilder` with two `EditText` fields:
1. **Sensor ID** (auto-uppercased): maps to a protocol sensor identifier.
2. **Label** (optional): display name; falls back to sensor ID if empty.

On confirm: `cardConfig.addCustomSensor(sid, label)` → `cardConfig.save()` → `cardAdapter.setItems(cardConfig.visibleCards())` → `refresh()`.

#### Single-Device Mode
Toggled via a preference key `"single_device_mode"` (boolean). When active:
- `rvDashboard` and `fabAddCard` are `GONE`.
- `singleDeviceScroll` is `VISIBLE`.
- `refreshSingleDeviceView(device, snapshot)` fills a hero section, status badge, live gauges, temperature trend chart, and a dynamically-generated sensor grid.

The sensor grid pairs readings 2-per-row using `LinearLayout` rows with equal-weight children. Each cell is a `MaterialCardView` containing four `TextView`s: sensor ID (label small), value (display small, bold), unit (body medium), and relative time (label small).

#### `refresh()` — Data Snapshot Construction

```
1. Query last 24h sensor data (limit 50)
2. Iterate rows:
   a. latestBySensorId.putIfAbsent(sensorId, item)   [most-recent-first, DESC order]
   b. trendBySensorId: accumulate up to 12 float values
   c. Identify TEMP/HUM/PRES/LEVEL by sensorId pattern
3. Compute snap.subtitle (format string), snap.syncStatus, snap.transportStatus
4. Build latestReadingsText (bullet list), recentAlertsSummary (last 3), opsSummary (last 3)
5. cardAdapter.setSnapshot(snap)  → triggers full rebind
6. If singleModeEnabled: refreshSingleDeviceView()
```

SensorId pattern matching (case-insensitive after `toUpperCase(ROOT)`):
| SensorId pattern | Assigned to |
|------------------|-------------|
| contains `"TEMP"` or `"TMP"` or equals `"T1"` | `latestTemp`, `tempTrend` |
| contains `"HUM"` or equals `"H1"` | `latestHum`, `humTrend` |
| contains `"PRES"` or `"BAR"` or equals `"P1"` | `latestPressure` |
| contains `"LEVEL"` or `"LVL"` or equals `"L1"` | `latestLevel` |

#### TransportManager Listener Callbacks
Both callbacks run on whatever thread the transport uses, so they route to `getActivity().runOnUiThread(this::refresh)`.

---

### 3.4 `dashboard` — `DashboardCardAdapter`

**File:** `ui/dashboard/DashboardCardAdapter.java`  
**Superclass:** `RecyclerView.Adapter<RecyclerView.ViewHolder>`

#### Purpose
A multi-view-type RecyclerView adapter that renders the configurable dashboard card list. Supports **8 built-in card types** plus **unlimited CUSTOM_SENSOR** cards.

#### View Type Constants

| Constant | Int | Card | Layout resource |
|----------|-----|------|-----------------|
| `TYPE_HEADER` | 0 | Status header | `item_dashboard_header` |
| `TYPE_KPI_ROW1` | 1 | Total devices / Online rate | `item_dashboard_kpi_row` |
| `TYPE_KPI_ROW2` | 2 | Active alerts / Throughput | `item_dashboard_kpi_row` |
| `TYPE_KPI_ROW3` | 3 | Active rules / Cumulative | `item_dashboard_kpi_row` |
| `TYPE_GAUGES` | 4 | Live metrics + progress bars | `item_dashboard_gauges` |
| `TYPE_CHARTS` | 5 | Temp + humidity trend charts | `item_dashboard_charts` |
| `TYPE_ACTIVITY` | 6 | Recent alerts + ops log | `item_dashboard_activity` |
| `TYPE_LATEST_READINGS` | 7 | Raw latest readings text | `item_dashboard_readings` |
| `TYPE_CUSTOM_SENSOR` | 8 | User-defined sensor card | `item_dashboard_custom_sensor` |

#### `Snapshot` — Live Data Container
```java
public static class Snapshot {
    int totalDevices, online, alerts, rules;
    long totalMessages;
    int throughput;
    double latestTemp, latestHum, latestPressure, latestLevel;
    int readingCount;
    long latestSampleMs;
    String subtitle, syncStatus, transportStatus;
    String recentAlertsSummary, opsSummary, latestReadingsText;
    boolean singleModeEnabled;
    List<Float> tempTrend, humTrend;
    Map<String, Models.SensorData> latestBySensorId;
    Map<String, List<Float>> trendBySensorId;
}
```
The `Snapshot` is fully rebuilt on each `refresh()` call in `DashboardFragment` and passed to the adapter via `setSnapshot(Snapshot)`, which calls `notifyDataSetChanged()`.

#### `Listener` Interface
```java
public interface Listener {
    void onToggleSingleMode();               // HEADER card toggle button clicked
    void onItemMoved(int fromPos, int toPos); // drag-to-reorder notification
    void onRemoveCustomCard(int adapterPosition, DashboardCardItem item);
}
```

#### ViewHolder Classes (inner/static)

| VH class | Bound fields |
|----------|-------------|
| `HeaderVH` | `tvSubtitle`, `tvSyncStatus`, `tvTransportStatus`, `btnToggle` |
| `KpiRowVH` | `tvLabelLeft`, `tvValueLeft`, `tvLabelRight`, `tvValueRight` |
| `GaugesVH` | `tvLiveMetrics`, `progressWater`, `progressHumidity` |
| `ChartsVH` | `chartTemp`, `chartHumidity` (both `MiniTrendChartView`) |
| `ActivityVH` | `tvRecentAlerts`, `tvOpsSummary` |
| `ReadingsVH` | `tvLatestReadings` |
| `CustomSensorVH` | `tvSensorLabel`, `tvSensorId`, `tvSensorValue`, `tvSensorMeta`, `chartSensor` |

`CustomSensorVH` is an **inner** class (not static) because it references the outer adapter's `items` list in its click listener. All other VHs are `static`.

#### KPI Rows — Bind Logic
Three KPI rows share one layout but are distinguished by view type. The adapter's `onBindViewHolder` hard-wires the label/value strings:
- **Row1:** `totalDevices` + online-rate percentage `"%.1f%%"`
- **Row2:** `alerts` + throughput `"%d msg/s"`
- **Row3:** `rules` + `totalMessages` cumulative count

#### Custom Sensor Card — Data Lookup
Sensor IDs are case-insensitive. `CustomSensorVH.bind()` does:
```java
data = s.latestBySensorId.get(item.sensorId.toUpperCase()); // try upper-case first
if (data == null) data = s.latestBySensorId.get(item.sensorId); // fall back to original case
```

---

### 3.5 `dashboard` — `DashboardCardConfig`

**File:** `ui/dashboard/DashboardCardConfig.java`  
**Type:** `final` class, private constructor

#### Purpose
Persistence layer for the dashboard card list. Serialises/deserialises the list of `DashboardCardItem` objects to `SharedPreferences` as a JSON array under the key `"cards_json_v2"`.

#### SharedPreferences Details
- File name: `"dashboard_card_prefs"` (`Context.MODE_PRIVATE`)
- Key: `"cards_json_v2"`

#### JSON Schema (per card object)
```json
{
  "type":     "KPI_ROW1",
  "visible":  true,
  "order":    1,
  "sensorId": "",
  "label":    ""
}
```
- `type` is the enum name (e.g., `"CUSTOM_SENSOR"`).
- Unknown `type` values are **silently skipped** (avoids crashes after downgrade).
- Missing fields use safe defaults (`visible=true`, `order=arrayIndex`, `sensorId=""`, `label=sensorId`).

#### `defaultCards()` — Factory
Returns 8 pre-ordered built-in cards (HEADER through LATEST_READINGS) with sequential order indices 0–7.

#### Public API

| Method | Description |
|--------|-------------|
| `load(Context)` | Static factory; returns config from prefs or default if absent/corrupt |
| `save(Context)` | Saves current `cards` list; also re-indexes `order` fields before writing |
| `visibleCards()` | Returns sorted (by `order`) list of visible cards only |
| `isVisible(Type)` | Queries visibility of a specific card type |
| `setVisible(Type, boolean)` | Mutates visibility without saving (caller must call `save`) |
| `moveCard(int, int)` | Moves card within the list; use after drag-to-reorder |
| `addCustomSensor(String, String)` | Appends a new `CUSTOM_SENSOR` card at end |
| `removeCard(DashboardCardItem)` | Removes by reference |

#### `ensureHeader()` — Invariant Maintenance
`load()` always calls `ensureHeader(list)` after parsing JSON. This guarantees that a `HEADER` card exists and is at index 0, even if the user somehow produces malformed saved data (e.g. via manual SharedPreferences edit).

---

### 3.6 `dashboard` — `DashboardCardItem`

**File:** `ui/dashboard/DashboardCardItem.java`

#### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `type` | `Type` (enum) | — | Card category |
| `visible` | `boolean` | `true` | Whether to include in `visibleCards()` |
| `order` | `int` | — | Sort priority |
| `sensorId` | `String` | `""` | Sensor identifier; only meaningful for `CUSTOM_SENSOR` |
| `label` | `String` | `""` | Display name; only meaningful for `CUSTOM_SENSOR` |

#### `Type` Enum

```
HEADER          Fixed header row — always position 0, not draggable
KPI_ROW1        Total Devices + Online Rate
KPI_ROW2        Active Alerts + Throughput
KPI_ROW3        Active Rules + Cumulative Traffic
GAUGES          Live sensor metrics + progress bars
CHARTS          Temperature + Humidity trend charts
ACTIVITY        Recent alerts + operations log feed
LATEST_READINGS Latest raw sensor readings text block
CUSTOM_SENSOR   User-defined sensor (specific sensorId)
```

#### `isDraggable()`
Returns `false` only for `HEADER`; all other types (including `CUSTOM_SENSOR`) may be reordered.

#### `customSensor()` Factory
```java
DashboardCardItem.customSensor("CO2", "Carbon Dioxide", nextOrder);
```
Sets `visible = true`; falls back to `sensorId` for `label` if label is empty.

---

### 3.7 `devices` — `DevicesFragment`

**File:** `ui/devices/DevicesFragment.java`  
**Layout:** `fragment_devices.xml`

#### Purpose
Displays all known IoT devices with a persistent **live search/filter** bar, online/offline counts, and a tap-to-open **detail dialog**.

#### Fields

| Field | Type | Notes |
|-------|------|-------|
| `binding` | `FragmentDevicesBinding` | View-binding |
| `repository` | `AppRepository` | Data source |
| `allDevices` | `List<Models.Device>` | Full list loaded from DB |
| `visibleDevices` | `List<Models.Device>` | Filtered subset (drives tap index) |
| `adapter` | `CardRowAdapter` | ListView adapter |

#### Search Logic
`filter()` performs case-insensitive substring match on both `device.aid` (as string) and `device.name`. An empty query shows all devices. The filter is triggered on every text change via `TextWatcher.onTextChanged`.

#### Online Heuristic
```java
boolean online = "online".equalsIgnoreCase(d.status) ||
                 System.currentTimeMillis() - d.lastSeenMs < 5 * 60_000L;
```
A device is considered online if its status string is `"online"` OR it was seen within the last 5 minutes (300 000 ms). This dual-check tolerates stale status strings from devices that reconnect without sending an explicit status update.

#### Device Detail Dialog
`showDeviceDetails(Device)` fetches latest sensor readings for the device and shows a formatted `AlertDialog` with:
- AID, Name, Type, Status, Transport type
- GPS coordinates (lat/lng)
- Last seen (absolute + relative time)
- Sensor readings list (bullet format: `• sensorId = value unit · datetime`)

#### `toRows()` — Badge Colours
```java
online ? R.color.gsyn_online : R.color.gsyn_warning
```
Offline devices get warning-orange badges; online devices get green.

---

### 3.8 `mirror` — `HealthMirrorFragment`

**File:** `ui/mirror/HealthMirrorFragment.java`  
**Layout:** `fragment_secondary_panel.xml` (shared with all mirror fragments)

#### Purpose
System health dashboard for the transport layer and local database. Displays:
- **Summary:** device count, messages/second, total messages.
- **Detail:** UDP status, MQTT connection state, database size in KB.
- **List:** each device as a `CardRowAdapter.Row` with online/offline badge.
- **Action button:** "Prune Old Data" — calls `repository.pruneOldData(7)` (deletes records older than 7 days).

#### Shared Layout — `fragment_secondary_panel.xml`
All four Mirror fragments use this layout. View IDs:
- `R.id.tvSectionLabel` — section heading
- `R.id.tvSummary` — primary stats line
- `R.id.tvDetail` — secondary detail text
- `R.id.tvEmpty` — shown when list is empty
- `R.id.list` — `ListView`
- `R.id.btnAction` — `MaterialButton` (each fragment sets its own text and click listener)

---

### 3.9 `mirror` — `HistoryMirrorFragment`

**File:** `ui/mirror/HistoryMirrorFragment.java`  
**Layout:** `fragment_secondary_panel.xml`

#### Purpose
Displays the last 24 hours of sensor data readings (up to 500 rows). Provides a **CSV export** action.

#### Data Query
```java
long now = System.currentTimeMillis();
List<Models.SensorData> rows =
    repository.querySensorData(now - 24L * 3600L * 1000L, now, 500);
```

#### Card Row Mapping
| `CardRowAdapter.Row` field | Value |
|---------------------------|-------|
| `title` | `"sensorId · value unit"` |
| `subtitle` | Device AID line |
| `meta` | Formatted absolute datetime |
| `badge` | `sensorId.toUpperCase()` or `"DATA"` |
| `badgeColor` | `R.color.gsyn_info` |

#### CSV Export
`exportCsv()` delegates entirely to `repository.exportHistoryCsv()`, which returns a `File`. Success/failure is shown via a long `Toast`.

---

### 3.10 `mirror` — `MapMirrorFragment`

**File:** `ui/mirror/MapMirrorFragment.java`  
**Layout:** `fragment_map_mirror.xml`  
**Implements:** `OnMapReadyCallback`

#### Purpose
Interactive Google Maps view showing all devices with valid GPS coordinates as coloured markers. Supports **map type switching** (Normal / Satellite / Hybrid) and **10-second auto-refresh**.

#### Fields

| Field | Type | Notes |
|-------|------|-------|
| `repository` | `AppRepository` | Device data source |
| `googleMap` | `GoogleMap` | Null until `onMapReady()` |
| `tvMapSummary` | `TextView` | Summary line above the map |
| `currentMarkers` | `List<Marker>` | Currently displayed markers |
| `autoRefreshHandler` | `Handler` (main Looper) | Drives 10 s polling |
| `autoRefreshRunnable` | `Runnable` | Repost self after `loadMarkers()` |

#### Map Initialisation
The `SupportMapFragment` is managed as a **child fragment** with a fixed tag `"MAP_FRAG"`:
```java
SupportMapFragment mapFrag = (SupportMapFragment)
    getChildFragmentManager().findFragmentByTag("MAP_FRAG");
if (mapFrag == null) { /* create new */ }
mapFrag.getMapAsync(this);
```
Using `commitNow()` ensures the map fragment is attached synchronously before `getMapAsync()` is called.

#### Dark Map Style
When `AppThemeConfig.BgPreset.isLight == false`, the fragment loads `R.raw.map_style_dark` (a custom Google Maps JSON style) to match the app's dark background.

#### Default Camera Position
```java
googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(35.0, 105.0), 4f));
```
China-centred, zoom 4 — visible even with no device data.

#### Marker Logic
- Devices with `|lat| < 1e-7 AND |lng| < 1e-7` are treated as having **no GPS** and are not plotted.
- Online devices: `HUE_GREEN` marker; offline: `HUE_ORANGE`.
- If exactly 1 mapped device: zoom to 14 on its centre.
- If > 1: fit bounds with 120 dp padding.

#### Auto-Refresh
```
onStart()  → autoRefreshHandler.post(autoRefreshRunnable)
onStop()   → autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
onDestroyView() → removeCallbacks (defence against leaked runnables)
```
`autoRefreshRunnable` calls `loadMarkers()` then posts itself with a 10 000 ms delay.

---

### 3.11 `mirror` — `RulesMirrorFragment`

**File:** `ui/mirror/RulesMirrorFragment.java`  
**Layout:** `fragment_secondary_panel.xml`

#### Purpose
Full CRUD interface for automation rules, combined with an operations log view. Allows:
- **Viewing** all rules + last 30 operation logs.
- **Toggling** a rule enabled/disabled by tapping its list row.
- **Deleting** a rule by long-pressing its list row.
- **Creating** a new threshold rule via dialog.

#### Fields
- `cachedRules` (`List<Models.Rule>`) — snapshot used to map list positions to rule objects.

#### Rule → `CardRowAdapter.Row` Mapping
| Field | Value |
|-------|-------|
| `title` | `rule.name` |
| `subtitle` | `"sensorIdFilter operator threshold → actionType"` |
| `meta` | `"AID ALL/deviceAidFilter · cooldown Xs"` |
| `badge` | `"ENABLED"` or `"DISABLED"` |
| `badgeColor` | `gsyn_online` (green) or `gsyn_warning` (orange) |

Operation logs appear **after** rules in the combined list with badge `"LOG"` and `gsyn_info` colour.

#### Tap / Long-Press Handling
Only the first `cachedRules.size()` rows are rules; the rest are log rows. Both listeners guard against out-of-range positions.

#### Create Rule Dialog
Minimal dialog with two `EditText` fields:
- Sensor ID (default: `"TEMP"`)
- Threshold (default: `"50"`)

Created rule always uses operator `">"` and action `"create_alert"`. Operation log entry `"CREATE_RULE"` is written after every successful creation.

---

### 3.12 `send` — `SendFragment`

**File:** `ui/send/SendFragment.java`  
**Layout:** `fragment_send.xml`  
**Dependencies:** `PacketBuilder`, `ProtocolConstants`, `TransportManager`

#### Purpose
A **protocol test bench** and manual packet-sending tool. Enables the developer / QA engineer to build and inject arbitrary protocol frames without hardware. Structured as three tabs.

#### `MultiSensorRow` Inner Class
Holds references to dynamically created views for multi-sensor rows:
```java
final View root;
final Spinner spSid, spUnit, spState;
final TextInputEditText etVal;
```

#### Tab Structure

| Tab index | Label | Visibility ID | Content |
|-----------|-------|---------------|---------|
| 0 | Control | `binding.tabControl` | 7 control-packet buttons (PING, PONG, ID_REQUEST, ID_ASSIGN, TIME_REQUEST, HS_ACK, HS_NACK, SECURE_DICT_READY) |
| 1 | Data | `binding.tabData` | Single-sensor form + multi-sensor dynamic rows |
| 2 | Raw | `binding.tabRaw` | Free-form hex input |

#### Route Parameters
The route for every packet:
- **AID** (`binding.etAid`): Target device address ID
- **TID** (`binding.etTid`): Transmission session ID
- **SEQ** (`binding.etSeq`): Sequence number
- **IP** (`binding.etIp`): Target host
- **Port** (`binding.etPort`): UDP port (default 9876)

`updateRouteSummary()` builds a display string for the current route on any change.

#### Single-Sensor Data Tab
- Spinner for Sensor ID (`ProtocolConstants.OS_SENSOR_IDS`)
- Spinner for Unit (`ProtocolConstants.OS_UNITS`) — auto-selected based on sensor ID via `ProtocolConstants.defaultUnitFor(sid)`
- Spinner for State (`ProtocolConstants.OS_STATES`)
- `EditText` for value
- Preview label showing the encoded packet body structure

#### Multi-Sensor Tab
`addMultiSensorRow()` dynamically inflates a horizontal `LinearLayout` with:
- SID spinner (weight=1), Unit spinner (weight=1), State spinner (weight=0.6)
- Value `TextInputEditText` (weight=1)
- Remove button (`"✕"`)

Max rows: unlimited (hardware-constrained).

#### Raw Hex Tab
Accepts a hex string, builds bytes via `PacketBuilder.buildRawHex(hex)`, and sends.

#### `sendAndLog(String label, byte[] frame)`
Core send method:
1. Reads AID, host, port from form fields.
2. Calls `transport().sendCommand(frame, aid, host, port)`.
3. Logs the operation to `repository.logOperation("SEND_CMD", ...)`.
4. Appends to in-memory `logs` list (capped at 20 entries).
5. Updates `tvLastResult` and `tvLog`.
6. Shows success/failure `Toast`.

#### Command Reference Table
`setupCmdRef()` sets a static text block into `tvCmdRef` showing all protocol command codes (0x01–0x25) with their hex byte and description. This is hardcoded in Java rather than a string resource to keep it in one place alongside the code that sends them.

---

### 3.13 `settings` — `SettingsFragment`

**File:** `ui/settings/SettingsFragment.java`  
**Layout:** `fragment_settings.xml`

#### Purpose
Application configuration screen. Covers:
1. **Language** (English / Chinese / System default)
2. **Accent colour** theme selection
3. **Background** preset selection
4. **Dashboard card visibility** toggles
5. **Single-device mode** toggle
6. **Transport settings**: UDP host/port/enable, MQTT broker/port/topic/enable
7. **Tile URL** for map tiles
8. **Transport status display** (runtime read-only summary)

#### Language Picker
`loadLanguagePref()` reads the current locale from `LocaleHelper.current()` and checks the appropriate `RadioButton` in `binding.rgLanguage`. On change, `LocaleHelper.applyAndSave(lang)` is called — AppCompat handles the activity recreation automatically.

Language constants: `LocaleHelper.LANG_EN = "en"`, `LANG_ZH = "zh"`, `LANG_SYSTEM = ""`.

#### Accent Chip Group — `buildAccentChips()`
Dynamically generates one `Chip` per `AppThemeConfig.ThemePreset` enum value. Each chip:
- Gets a unique ID via `View.generateViewId()`.
- Shows a 40×40 oval `GradientDrawable` as its chip icon, coloured with `preset.color()`.
- Uses `setOnClickListener` (not `OnCheckedChangeListener`) to avoid duplicate events.
- The currently active preset chip is pre-checked.

#### Background Chip Group — `buildBgChips()`
Same pattern as accent chips but iterates `AppThemeConfig.BgPreset`. Adds a 2dp grey stroke to background dots for visibility on light surfaces.

#### Dashboard Card Visibility
`loadCardConfig()` reads `DashboardCardConfig` and sets the initial state of 7 toggle switches. Single-device mode switch has an **immediate** listener (changes apply without pressing Save). The other card visibility switches are saved only when "Save" is pressed.

#### Save Flow — `savePrefs()`
```
1. saveCardConfig()          → DashboardCardConfig.save()
2. SharedPreferences.edit()  → apply transport & other prefs
3. TransportManager:        start/stop UDP, connect/disconnect MQTT
4. refreshTransportStatus()  → update status TextView
5. requireActivity().recreate() → apply theme changes
```
`recreate()` is always called on save so that any theme change (accent or background) takes effect immediately without manual app restart.

#### `refreshTransportStatus()`
Populates `tvTransportStatus`, `tvDeviceCount`, `tvAlertCount`, `tvRuleCount`, `tvDbSize`, and `tvRuntimeHint` with live data from the repository and transport manager.

#### `formatBytes(long)`
Private helper: formats byte counts as `"X B"`, `"X.X KB"`, or `"X.XX MB"`.

---

## 4. Custom View Layer

### 4.1 `widget` — `MiniTrendChartView`

**File:** `ui/widget/MiniTrendChartView.java`  
**Superclass:** `android.view.View`

#### Purpose
A lightweight, self-contained canvas-drawn sparkline chart. Used in:
- `DashboardCardAdapter.ChartsVH` (temperature + humidity trends)
- `DashboardCardAdapter.CustomSensorVH` (per-sensor trend)
- `DashboardFragment.refreshSingleDeviceView()` (single-device temperature chart)

#### Paint Objects

| Paint | Style | Colour | Use |
|-------|-------|--------|-----|
| `linePaint` | STROKE, 3dp round cap/join | `#5AC8FA` (default) | Sparkline path |
| `fillPaint` | FILL | `rgba(90,200,250,0.16)` | Area fill below line |
| `gridPaint` | STROKE, 1dp | `rgba(255,255,255,0.2)` | 4 horizontal grid lines |
| `pointPaint` | FILL | Same as linePaint | Data point circles |
| `textPaint` | TEXT, 12sp | `rgba(255,255,255,0.86)` | Title text |

#### Layout Areas (inside `onDraw`)
```
left  = 16dp
right = width - 16dp
top   = 32dp   (below title)
bottom = height - 16dp
titleBaseline = 18dp
```

#### `onDraw` Algorithm
1. Draw title text at `(left, titleBaseline)`.
2. Draw 4 horizontal grid lines evenly spaced between `top` and `bottom`.
3. If `points` is empty, return early.
4. Compute `min` and `max` from the data; if `|max - min| < 0.0001`, expand by ±1 to avoid division by zero.
5. Iterate points, computing `x` and `y`:
   - `x = left + (right - left) * i / (n - 1)`
   - `y = bottom - normalized * (bottom - top)` where `normalized = (value - min) / (max - min)`
6. Build `Path line` and `Path fill` simultaneously.
7. Draw filled area, then line, then draw a 2.5dp point circle at each data point.
8. Draw a larger 4dp circle at the last point (latest value emphasis).

#### Public API

| Method | Description |
|--------|-------------|
| `setSeries(List<Float>)` | Replace data and call `invalidate()` |
| `setChartColor(int color)` | Set line, fill (alpha 40), and point colours; call `invalidate()` |
| `setTitle(String)` | Set title text and content description; call `invalidate()` |

#### XML Usage
```xml
<com.opensynaptic.gsynjava.ui.widget.MiniTrendChartView
    android:id="@+id/chartTemp"
    android:layout_width="match_parent"
    android:layout_height="80dp" />
```

---

## 5. Navigation Architecture

### Overview
```
MainActivity
├── BottomNavigationView (primary navigation)
│   ├── Dashboard     → DashboardFragment
│   ├── Devices       → DevicesFragment
│   ├── Alerts        → AlertsFragment
│   ├── Send          → SendFragment
│   └── Settings      → SettingsFragment
│
└── NavigationView / DrawerLayout (secondary navigation)
    ├── (mirrors bottom nav items above)
    ├── Map           → MapMirrorFragment
    ├── History       → HistoryMirrorFragment
    ├── Rules         → RulesMirrorFragment
    └── Health        → HealthMirrorFragment

SecondaryActivity (independent entry point)
├── MODE_MAP     → MapMirrorFragment
├── MODE_HISTORY → HistoryMirrorFragment
├── MODE_RULES   → RulesMirrorFragment
└── MODE_HEALTH  → HealthMirrorFragment (default)
```

### Fragment Transaction Policy
All transactions use `replace()` into a single container (`R.id.fragment_container`). There is **no back stack** — pressing back exits the activity. This is intentional for IoT dashboards where forward-only navigation is the norm.

### State Preservation
- `savedInstanceState == null` guard ensures fragments are only committed on fresh starts, not on rotation/restore.
- Fragment-level state (e.g., scroll position in `AlertsFragment`) is not explicitly saved; `onStart()`'s `load()` call always refreshes from the database.

---

## 6. Theme & Styling System

The app uses an **overlay-based dynamic theming** approach:

1. **Accent colour** — `AppThemeConfig.ThemePreset` enum (e.g., Teal, Purple, Orange…). Each preset maps to a style overlay resource. Selected at runtime via `getTheme().applyStyle(overlayRes, true)` **before** `super.onCreate()`.

2. **Background preset** — `AppThemeConfig.BgPreset` enum (e.g., Dark, Light, Amoled…). Also applied via overlay before `super.onCreate()`. Post-`setContentView`, `applyBgToWindow(window, context)` synchronises status/nav bar colours. `AppThemeConfig.applyBgToRoot(view, context)` sets the root view's background colour for fragments.

3. **Map dark style** — `MapMirrorFragment` checks `BgPreset.isLight` and conditionally loads `R.raw.map_style_dark`.

4. **Locale** — `LocaleHelper` persists the user's language preference and applies it via `AppCompatDelegate.setApplicationLocales()`.

---

## 7. Data Flow Through the UI

```
TransportManager (UDP/MQTT receive thread)
        │
        │ onMessage(DeviceMessage)     (via MessageListener)
        │ onStats(TransportStats)      (via StatsListener)
        ▼
DashboardFragment.refresh()           (bounced to UI thread via runOnUiThread)
        │
        ├─ repository.querySensorData(...)   ─── SQLite (in-memory/file)
        ├─ repository.getTotalDeviceCount()
        ├─ repository.getOnlineDeviceCount()
        ├─ repository.getUnacknowledgedAlertCount()
        ├─ repository.getAlerts(null, 3)
        ├─ repository.getOperationLogs(3)
        │
        └─ DashboardCardAdapter.setSnapshot(snap)
                │
                └─ notifyDataSetChanged() → onBindViewHolder() per card
                        │
                        └─ MiniTrendChartView.setSeries(List<Float>) → invalidate()
```

All other fragments (`AlertsFragment`, `DevicesFragment`, etc.) poll the repository only on `onStart()` (and on user-initiated swipe-refresh). They do **not** subscribe to transport callbacks — their data source is the database snapshot.

---

## 8. Naming Conventions & Coding Style

| Element | Convention | Example |
|---------|-----------|---------|
| Activity fields | camelCase, `binding` for view binding | `binding`, `drawerToggle` |
| Fragment fields | camelCase, `binding` always nullified in `onDestroyView` | `binding`, `repository`, `adapter` |
| View IDs in XML | `tvXxx` (TextView), `etXxx` (EditText), `btnXxx` (Button), `rvXxx` (RecyclerView), `ivXxx` (ImageView), `switchXxx` (Switch) | `tvSummary`, `etAid`, `btnSave` |
| Constants | `SCREAMING_SNAKE_CASE` | `EXTRA_MODE`, `MODE_MAP` |
| Colour constants | `gsyn_` prefix | `gsyn_danger`, `gsyn_online` |
| SharedPrefs keys | lowercase snake_case | `"udp_host"`, `"single_device_mode"` |
| Suppressed resource keys | `KEY_` prefix | `KEY_CARDS` |

---

## 9. Extending the Dashboard

### Adding a New Built-in Card Type
1. Add an enum value to `DashboardCardItem.Type`.
2. Add a `TYPE_XXX` int constant in `DashboardCardAdapter`.
3. Add a new `layout/item_dashboard_xxx.xml`.
4. Add a new `XxxVH` ViewHolder class in `DashboardCardAdapter`.
5. Wire it in `getItemViewType()`, `onCreateViewHolder()`, and `onBindViewHolder()`.
6. Add a `DashboardCardItem(Type.XXX, n)` entry in `DashboardCardConfig.defaultCards()`.
7. Expose any new data fields in `DashboardCardAdapter.Snapshot` and populate them in `DashboardFragment.refresh()`.

### Adding a New Custom Sensor
No code changes needed — users add custom sensor cards from the FAB dialog in `DashboardFragment`. To pre-populate a sensor programmatically:
```java
DashboardCardConfig cfg = DashboardCardConfig.load(context);
cfg.addCustomSensor("CO2", "Carbon Dioxide");
cfg.save(context);
```

### Adding a New Mirror Fragment
1. Create `XxxMirrorFragment extends Fragment`, inflating `fragment_secondary_panel.xml`.
2. Add a `MODE_XXX` constant and a `case` in `fragmentForMode()` / `subtitleFor()` in `SecondaryActivity`.
3. Add a drawer menu item in `drawer_nav.xml`.
4. Handle the item ID in `MainActivity.onNavigationItemSelected()`.

---

## 10. Common Pitfalls & FAQ

### Q: My fragment throws NPE after rotation.
**A:** Always null-check `binding` before any access (especially in async callbacks). Use the pattern:
```java
if (binding == null) return;
```
The view binding is nullified in `onDestroyView()`; any delayed callback that fires after that point will have a null binding.

### Q: The dashboard doesn't update when new data arrives.
**A:** `DashboardFragment` subscribes to `TransportManager` listeners only between `onStart()` and `onStop()`. Outside this window (e.g. when a mirror fragment is showing), no automatic refresh occurs. Other fragments require a swipe-refresh or navigation-away-and-back.

### Q: Theme changes don't take effect immediately.
**A:** The overlay system requires the activity to be re-created. `SettingsFragment.savePrefs()` calls `requireActivity().recreate()`. Do not apply overlays at any other point in the lifecycle — the theme must be fully set before `super.onCreate()`.

### Q: A device appears offline even though it's sending data.
**A:** The online heuristic in `DevicesFragment.toRows()` and `HealthMirrorFragment.load()` checks both `device.status` (string) and `lastSeenMs` recency (< 5 min). If the device status write is delayed, the 5-minute window acts as a live-ness fallback.

### Q: The map shows no markers even though devices have GPS.
**A:** `MapMirrorFragment.loadMarkers()` skips devices where both `|lat| < 1e-7` and `|lng| < 1e-7`. Devices with coordinates `(0, 0)` (default/uninitialised) are treated as having no GPS data.

### Q: How do I add a new language?
**A:** Add a new `res/values-xx/strings.xml` for the locale code, add a `RadioButton` in `fragment_settings.xml`, add a constant in `LocaleHelper`, and handle the new constant in `SettingsFragment.loadLanguagePref()`.

### Q: `MiniTrendChartView` flickers during scroll.
**A:** The view is moderately expensive to draw. Consider setting `setLayerType(LAYER_TYPE_HARDWARE, null)` on the view after data is stable, or reducing the number of visible chart views by showing them only when ≥ 2 data points exist (already done for temperature trend in single-device mode).

