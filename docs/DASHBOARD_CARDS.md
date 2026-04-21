# Dashboard Card System

> 中文版請見 [DASHBOARD_CARDS_zh.md](DASHBOARD_CARDS_zh.md)


The Dashboard is built as a fully customisable card list.  
This document explains how each layer works so you can add new card types or modify existing ones.

---

## Overview

```
DashboardFragment
    │
    ├── DashboardCardConfig      ← persists ordered list of DashboardCardItem to SharedPreferences
    │
    ├── DashboardCardAdapter     ← RecyclerView adapter with 9 view types
    │       └── Snapshot         ← plain data snapshot passed to every ViewHolder
    │
    └── ItemTouchHelper          ← drag-to-reorder callback wired to the adapter
```

---

## DashboardCardItem

A card item is a simple data class with three important fields:

```java
public class DashboardCardItem {
    public enum Type {
        HEADER,           // always first, not draggable
        KPI_ROW1,         // total devices + online rate
        KPI_ROW2,         // active alerts + throughput
        KPI_ROW3,         // rules + cumulative traffic
        GAUGES,           // progress bars for level + humidity
        CHARTS,           // mini trend line charts
        ACTIVITY,         // recent alerts + ops log
        LATEST_READINGS,  // raw sensor readings dump
        CUSTOM_SENSOR     // user-defined: shows one specific sensorId
    }

    public Type    type;
    public boolean visible;
    public int     order;
    public String  sensorId;   // only for CUSTOM_SENSOR
    public String  label;      // display name for CUSTOM_SENSOR
}
```

---

## DashboardCardConfig

Serialises the card list as JSON in `SharedPreferences` (key `cards_json_v2`).

```java
// Load (creates defaults if first run)
DashboardCardConfig cfg = DashboardCardConfig.load(context);

// Get only visible cards in saved order
List<DashboardCardItem> visible = cfg.visibleCards();

// Toggle visibility
cfg.setVisible(DashboardCardItem.Type.GAUGES, false);

// Add a custom sensor card
cfg.addCustomSensor("TEMP1", "Room Temperature");

// Remove any card
cfg.removeCard(item);

// Persist
cfg.save(context);
```

**Stored JSON example:**
```json
[
  {"type":"HEADER",        "visible":true,  "order":0, "sensorId":"", "label":""},
  {"type":"KPI_ROW1",      "visible":true,  "order":1, "sensorId":"", "label":""},
  {"type":"CUSTOM_SENSOR", "visible":true,  "order":8, "sensorId":"TEMP1", "label":"Room Temp"}
]
```

---

## DashboardCardAdapter

Extends `RecyclerView.Adapter<RecyclerView.ViewHolder>` with multiple view types.

### View type dispatch

```java
@Override
public int getItemViewType(int position) {
    switch (items.get(position).type) {
        case HEADER:  return TYPE_HEADER;      // 0
        case KPI_ROW1: return TYPE_KPI_ROW1;   // 1
        // ...
        case CUSTOM_SENSOR: return TYPE_CUSTOM_SENSOR; // 8
    }
}
```

Each type inflates its own layout file (`item_dashboard_*.xml`) in `onCreateViewHolder`.

### Snapshot — the data contract

Every ViewHolder receives a single `Snapshot` object rather than querying the database themselves:

```java
// DashboardFragment.refresh() builds the snapshot:
DashboardCardAdapter.Snapshot snap = new DashboardCardAdapter.Snapshot();
snap.totalDevices = repository.getTotalDeviceCount();
snap.latestTemp   = ...;          // extracted from history query
snap.latestBySensorId = ...;      // Map<String, SensorData> for CUSTOM_SENSOR
snap.trendBySensorId  = ...;      // Map<String, List<Float>> for trend charts
// ...
cardAdapter.setSnapshot(snap);    // triggers notifyDataSetChanged()
```

This pattern keeps ViewHolders stateless and easy to test.

---

## Drag-to-Reorder

Implemented with `ItemTouchHelper.Callback` attached to the `RecyclerView`:

```java
touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
    @Override
    public int getMovementFlags(RecyclerView rv, RecyclerView.ViewHolder vh) {
        // position 0 (HEADER) is locked
        if (vh.getAdapterPosition() == 0) return 0;
        return makeMovementFlags(UP | DOWN, 0);  // drag only, no swipe
    }

    @Override
    public boolean onMove(..., ViewHolder from, ViewHolder to) {
        cardAdapter.moveItem(from.getAdapterPosition(), to.getAdapterPosition());
        return true;
    }

    @Override
    public void clearView(...) {
        persistOrderFromAdapter(); // write new order to SharedPreferences
    }
});
touchHelper.attachToRecyclerView(binding.rvDashboard);
```

`persistOrderFromAdapter()` rebuilds `cardConfig.cards` from the adapter's current order (visible cards first, then hidden cards appended at the end):

```java
private void persistOrderFromAdapter() {
    List<DashboardCardItem> visible = new ArrayList<>();
    for (int i = 0; i < cardAdapter.getItemCount(); i++)
        visible.add(cardAdapter.getItem(i));

    List<DashboardCardItem> hidden = new ArrayList<>();
    for (DashboardCardItem c : cardConfig.cards)
        if (!c.visible) hidden.add(c);

    cardConfig.cards.clear();
    cardConfig.cards.addAll(visible);
    cardConfig.cards.addAll(hidden);
    cardConfig.save(requireContext());
}
```

---

## Custom Sensor Cards

When the user taps the FAB (+):

1. A `MaterialAlertDialog` asks for **Sensor ID** (e.g. `TEMP1`) and an optional display label
2. `DashboardCardConfig.addCustomSensor(sid, label)` appends a new `CUSTOM_SENSOR` item
3. `cardConfig.save()` + `cardAdapter.setItems(cfg.visibleCards())` updates the list immediately
4. `refresh()` is called so the card gets data right away

In `CustomSensorVH.bind()`:
```java
// Look up latest reading by sensor ID (case-insensitive)
Models.SensorData data = snap.latestBySensorId.get(item.sensorId.toUpperCase());
if (data != null) {
    tvSensorValue.setText(trimNumber(data.value));
    tvSensorMeta.setText("Last seen: " + formatRelativeTime(data.timestampMs));
    chartSensor.setSeries(snap.trendBySensorId.get(item.sensorId.toUpperCase()));
} else {
    tvSensorValue.setText("—");
    tvSensorMeta.setText("No data yet for this sensor");
}
```

---

## Single-Device Focus Mode

Controlled by a boolean in `SharedPreferences` (`single_device_mode`).

```java
// DashboardFragment.refresh()
boolean singleModeEnabled = prefs.getBoolean("single_device_mode", false);
if (singleModeEnabled) {
    enterSingleDeviceMode(true);       // hide RecyclerView + FAB, show ScrollView
    List<Models.Device> devices = repository.getAllDevices();
    if (!devices.isEmpty()) {
        refreshSingleDeviceView(devices.get(0), snap);
    } else {
        // show placeholder "Waiting for device…"
    }
    return;
}
enterSingleDeviceMode(false);          // show RecyclerView + FAB
```

The toggle is exposed in **two** places for discoverability:
1. **Dashboard header card** — `btnToggleSingleMode` button, immediate effect
2. **Settings → card visibility section** — `switchSingleDeviceMode`, immediate effect (no Save needed)

Both write to the same `SharedPreferences` key and call `refresh()`.

---

## How to Add a New Card Type

1. **Add an enum value** to `DashboardCardItem.Type`
2. **Create a layout** `item_dashboard_mycard.xml`
3. **Add a ViewHolder** inner class in `DashboardCardAdapter`
4. **Add the view type constant** (`TYPE_MYCARD = 9`)
5. **Handle it** in `getItemViewType()`, `onCreateViewHolder()`, and `onBindViewHolder()`
6. **Add fields** to `Snapshot` if the card needs new data
7. **Populate those fields** in `DashboardFragment.refresh()`
8. **Add a default entry** in `DashboardCardConfig.defaultCards()` if it should appear by default
9. **Add a toggle** in `fragment_settings.xml` + `SettingsFragment.loadCardConfig()` / `saveCardConfig()`
