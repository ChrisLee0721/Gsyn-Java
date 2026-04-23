# Changelog

All notable changes to Gsyn Java are documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).  
Versions follow [Semantic Versioning](https://semver.org/).

---

## [1.3.0] — 2026-04-23

### Added
- **Local push notifications** — Warning/Critical alerts trigger Android notifications via `AlertNotificationHelper`; notification channels registered on startup; `POST_NOTIFICATIONS` permission declared
- **Batch alert operations** — "Ack All" and "Clear Acked" buttons in Alerts screen
- **Alert keyword search** — real-time `SearchView` filter across message / sensorId / deviceAid
- **Bottom-nav unread badge** — Alerts tab shows unacknowledged count via `BadgeDrawable`
- **Long-press to delete** alert items; hint shown in each row subtitle
- **Time-range picker** in History mirror — 1 h / 6 h / 24 h / 7 d Spinner injected above Export button
- **Enhanced rule creation dialog** — operator (`>/</>=/<==/!=/==`), alert level, and cooldown fields
- `AppRepository.getAllLatestReadings()` — single batched SQL replaces per-device N+1 sensor queries
- `AppRepository.acknowledgeAllAlerts()`, `deleteAlert()`, `deleteAcknowledgedAlerts()`, `getAlertCountsByLevel()`, `exportHistoryCsv(from, to, limit)`

### Changed
- **All fragments now load data off the main thread** (`ExecutorService`) — `AlertsFragment`, `DevicesFragment`, `HistoryMirrorFragment`, `HealthMirrorFragment`, `RulesMirrorFragment`, `MapMirrorFragment`
- `DevicesFragment` implements `TransportManager.MessageListener` — auto-refreshes on incoming packets
- `HealthMirrorFragment` implements `TransportManager.StatsListener` — transport counters update every second without polling
- `MapMirrorFragment.loadMarkers()` moved to background thread; map UI updated on main thread
- `AlertsFragment` separates `allLoaded` (full DB set) from `currentAlerts` (filtered for click mapping) — fixes search-clear regression
- `AppController.onMessage()` falls back to `System.currentTimeMillis()` when device timestamp is zero
- Device detail dialog is now scrollable (`ScrollingMovementMethod`) — handles devices with many sensors
- CSV export includes human-readable `datetime` column alongside raw `timestamp_ms`
- `versionCode` → 3, `versionName` → 1.3.0

### Fixed
- Duplicate `mirror_history_summary_format` resource key in both `values/strings.xml` and `values-zh/strings.xml` removed
- `AlertsFragment.applyFilterAndRender()` no longer overwrites `allLoaded` — clearing search now correctly restores the full list

---

## [1.2.0] — 2026-04-23

### Added
- Dashboard card system: 9 configurable card types with drag-reorder (`DashboardCardAdapter`, `DashboardCardConfig`, `DashboardCardItem`)
- Custom sensor card — display any arbitrary sensor ID on the Dashboard
- Gauge cards for water level and humidity (native Canvas)
- Per-app language switching — Chinese / English toggle in Settings (`LocaleHelper`, `AppCompatDelegate`)
- `res/xml/locale_config.xml` for Android 13+ per-app language support
- `MiniTrendChartView` — pure Canvas temperature and humidity trend charts (no third-party chart library)
- Rules screen (`RulesMirrorFragment`) — full CRUD + enable/disable toggle
- History screen (`HistoryMirrorFragment`) — 24-hour sensor table + one-tap CSV export
- Health screen (`HealthMirrorFragment`) — live transport stats and database row counts
- `DiffEngine` — template-based DIFF/HEART packet reconstruction
- `GeohashDecoder` — decode geohash strings to lat/lng for Map markers
- `BaseSecondaryFragment` and `UiFormatters` shared utilities
- CI/CD: GitHub Actions workflow with keystore format validation step
- `scripts/generate-keystore.sh` and `scripts/generate-keystore.ps1` automation scripts
- `RELEASE.md` — complete release and signing guide
- `docs/TESTING.md` + `docs/TESTING_zh.md` — unit test and end-to-end validation guide
- `docs/TROUBLESHOOTING.md` + `docs/TROUBLESHOOTING_zh.md` — common issues reference
- Full documentation set: `ARCHITECTURE`, `DATA_FLOW`, `PROTOCOL`, `DASHBOARD_CARDS`, `UI_PATTERNS`, `CONTRIBUTING`, `GETTING_STARTED` (EN + ZH)

### Fixed
- `SupportMapFragment` blank-map race condition — switched from `commit()` to `commitNow()`
- Extension pages (Map, History, Rules, Health) now load inside `MainActivity` fragment container, keeping the drawer and bottom nav visible at all times
- Debug build no longer appends `.debug` to application ID, allowing the same Maps API key for both debug and release builds

### Changed
- Navigation architecture: removed `SecondaryActivity`; all fragments hosted in `MainActivity`
- Bottom nav and drawer remain visible regardless of which page is active

---

## [1.0.0] — 2026-01-15

### Added
- Initial release — full Android Java port of [OpenSynaptic/Gsyn](https://github.com/OpenSynaptic/Gsyn) Flutter dashboard
- UDP transport: bidirectional `DatagramSocket`, configurable port
- MQTT transport: Eclipse Paho v3, subscribe `gsyn/#`, publish `gsyn/out/<aid>`
- Binary protocol codec: `PacketDecoder`, `PacketBuilder`, `BodyParser`, `OsCrc`, `Base62Codec`, `OsCmd`, `ProtocolConstants`
- SQLite persistence: devices, sensor_data, alerts, rules, operation_logs (7-day rolling window for sensor_data)
- `AppRepository` — single database access point, no direct DB access elsewhere
- `RulesEngine` — threshold-based automation (`create_alert` / `send_command` / `log_only`)
- `AppController` — singleton wiring all components
- Dashboard: KPI rows, trend charts, gauge cards, activity feed, raw readings
- Devices list with search and detail bottom sheet
- Alerts list with severity filter and acknowledgement
- Send page — command builder with 3 tabs (Ping / Data / Raw Hex)
- Settings page — UDP / MQTT configuration, theme presets
- Map page — Google Maps SDK, live device markers, satellite/hybrid/normal layers
- Theming: Material3 overlay system, multiple accent colours and background presets, dark/light mode
- 8 JVM unit tests covering the full protocol codec layer

