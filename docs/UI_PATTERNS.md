# UI Patterns

This document explains the recurring patterns used throughout the Gsyn Java UI layer.  
Understanding these patterns lets you read any Fragment in the codebase after reading this once.

---

## 1. ViewBinding

Every Fragment uses **ViewBinding** instead of `findViewById`.

```java
// Declare at class level
private FragmentDashboardBinding binding;

// Inflate in onCreateView
@Override
public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    binding = FragmentDashboardBinding.inflate(inflater, container, false);
    return binding.getRoot();
}

// Always null the binding in onDestroyView (prevents memory leaks)
@Override
public void onDestroyView() {
    binding = null;
    super.onDestroyView();
}
```

**Why:** `findViewById` is error-prone (wrong type, wrong ID, NPE at runtime). ViewBinding generates a typed class at compile time — if you rename a view ID in XML, the code won't compile.

**Rule:** Always guard view access with `if (binding == null) return;` in any method that may be called after `onDestroyView`.

---

## 2. Fragment Lifecycle vs Listener Registration

Fragments register as listeners in `onStart()` and unregister in `onStop()`:

```java
@Override
public void onStart() {
    super.onStart();
    transportManager.addMessageListener(this);
    transportManager.addStatsListener(this);
    refresh();  // initial data load
}

@Override
public void onStop() {
    transportManager.removeMessageListener(this);
    transportManager.removeStatsListener(this);
    super.onStop();
}
```

**Why not `onResume`/`onPause`?**  
`onStart`/`onStop` are called when the fragment becomes visible/invisible, including when the Activity goes to background. `onResume`/`onPause` are called for every focus change (e.g. dialogs), which would cause unnecessary redraws.

**Why not `onCreate`/`onDestroy`?**  
The binding doesn't exist yet in `onCreate`. Keeping listeners alive through `onDestroy` would cause callbacks to reach a null binding.

---

## 3. Background Thread → UI Thread

All `TransportManager` callbacks arrive on **background threads**.  
The canonical pattern for updating UI from a callback:

```java
@Override
public void onMessage(Models.DeviceMessage message) {
    // DON'T touch views here — this is a background thread!
    if (getActivity() != null) {
        getActivity().runOnUiThread(this::refresh);
    }
}
```

The `getActivity() != null` guard prevents a crash when the callback fires after the Fragment has been detached.

---

## 4. Theming — Material3 Attribute References

All colours in layouts use **theme attributes** rather than hardcoded hex values:

```xml
android:textColor="?attr/colorOnSurface"
android:background="?attr/colorSurface"
app:cardBackgroundColor="?attr/colorSecondaryContainer"
```

This is mandatory for the light/dark theme + accent colour system to work.  
The following attributes are available from Material3:

| Attribute | Role |
|-----------|------|
| `?attr/colorPrimary` | Brand accent (buttons, FAB) |
| `?attr/colorSurface` | Card background |
| `?attr/colorOnSurface` | Text on cards |
| `?attr/colorPrimaryContainer` | Hero banner background |
| `?attr/colorOnPrimaryContainer` | Text on hero banner |
| `?attr/colorSecondaryContainer` | Secondary card background |
| `?attr/colorOnSurfaceVariant` | Secondary / hint text |
| `?attr/colorOutline` | Dividers, borders |

**Never** write `android:textColor="#FFFFFF"` in a layout. Use `@android:color/white` only for text that must always be white (e.g. text on a coloured pill badge that you set programmatically).

---

## 5. Theme Overlay Architecture

Themes are built in three layers:

```
Theme.GsynJava                    (res/values/themes.xml)
    ↑ extends
Material3 / DayNight base theme

Theme.GsynJava + Accent Overlay   (applied in MainActivity.onCreate BEFORE super)
    ThemeOverlay.Accent.Teal
    ThemeOverlay.Accent.DeepBlue
    ...

Theme.GsynJava + Bg Overlay       (applied alongside Accent)
    ThemeOverlay.Bg.DarkNavy
    ThemeOverlay.Bg.SnowWhite
    ...
```

`AppThemeConfig.applyTheme(activity)` reads both presets from `SharedPreferences` and calls:

```java
activity.getTheme().applyStyle(accentOverlayResId, true);
activity.getTheme().applyStyle(bgOverlayResId, true);
```

The `true` parameter means the overlay **overrides** the base theme values.

`AppThemeConfig.applyBgToRoot(view, context)` paints the fragment's root view with the background colour so it matches the window background even before the layout is drawn.

---

## 6. Internationalisation (i18n)

All user-visible strings live in `res/values/strings.xml` (English, default) and `res/values-zh/strings.xml` (Chinese).

**Golden rule:** Never hardcode Chinese or English text in Java or XML. Use `@string/...` in XML and `getString(R.string....)` in Java.

### Language switching

```java
// LocaleHelper wraps AppCompatDelegate API (AppCompat 1.6+)
LocaleHelper.applyAndSave("en");  // switch to English, persisted automatically
LocaleHelper.applyAndSave("zh");  // switch to Chinese
LocaleHelper.applyAndSave("");    // follow system

// Read current selection
String lang = LocaleHelper.current(); // "en", "zh", or ""
```

`AppCompatDelegate.setApplicationLocales()` handles persistence across app restarts and triggers an Activity recreation automatically. **No manual `SharedPreferences` write is needed.**

### Locale-aware time formatting

```java
// Use DateUtils for relative time — auto-localises
String relTime = (String) DateUtils.getRelativeTimeSpanString(
    timestampMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
// → "5 minutes ago" (EN) or "5 分钟前" (ZH)
```

**Never** format time as `"X 分钟前"` in Java code. Use `DateUtils` instead.

---

## 7. RecyclerView with Multiple View Types

Pattern used in `DashboardCardAdapter`:

```java
// 1. Define int constants for each type
static final int TYPE_HEADER       = 0;
static final int TYPE_KPI_ROW1     = 1;
static final int TYPE_CUSTOM_SENSOR = 8;

// 2. Map item to type
@Override
public int getItemViewType(int position) {
    switch (items.get(position).type) {
        case HEADER:        return TYPE_HEADER;
        case CUSTOM_SENSOR: return TYPE_CUSTOM_SENSOR;
        // ...
    }
}

// 3. Inflate the correct layout per type
@Override
public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    LayoutInflater inf = LayoutInflater.from(parent.getContext());
    switch (viewType) {
        case TYPE_HEADER:
            return new HeaderVH(inf.inflate(R.layout.item_dashboard_header, parent, false));
        case TYPE_CUSTOM_SENSOR:
            return new CustomSensorVH(inf.inflate(R.layout.item_dashboard_custom_sensor, parent, false));
    }
}

// 4. Each ViewHolder is a static inner class with its own bind() method
static class HeaderVH extends RecyclerView.ViewHolder {
    final TextView tvSubtitle;
    HeaderVH(View v) {
        super(v);
        tvSubtitle = v.findViewById(R.id.tvSubtitle);
    }
    void bind(Snapshot s, Listener listener) {
        tvSubtitle.setText(s.subtitle);
    }
}
```

**Key insight:** `onCreateViewHolder` is called rarely (only when a new cell scrolls into view). `onBindViewHolder` is called every time the data changes. Keep `onBindViewHolder` fast — no database queries, no object creation.

---

## 8. MaterialAlertDialog Pattern

```java
new MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.my_dialog_title))
        .setView(customView)
        .setPositiveButton(android.R.string.ok, (dlg, w) -> {
            // handle confirm
        })
        .setNegativeButton(android.R.string.cancel, null) // null = just dismiss
        .show();
```

Use `android.R.string.ok` and `android.R.string.cancel` for standard system-localised button labels instead of defining your own.

---

## 9. Programmatic Views (when XML isn't enough)

When a layout needs to generate an unknown number of child views at runtime (e.g. the sensor reading grid in single-device mode):

```java
// Create card programmatically
MaterialCardView card = new MaterialCardView(requireContext());
LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);  // weight=1, fills half width
card.setLayoutParams(params);
card.setUseCompatPadding(true);

// Add children
LinearLayout inner = new LinearLayout(requireContext());
inner.setOrientation(LinearLayout.VERTICAL);
inner.setPadding(dp(12), dp(12), dp(12), dp(12));

TextView tvValue = new TextView(requireContext());
tvValue.setText("25.3");
tvValue.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_DisplaySmall);
inner.addView(tvValue);

card.addView(inner);
parentLayout.addView(card);
```

**When to use this vs XML:**
- XML: for fixed structure (always the same number of views)
- Programmatic: for dynamic structure (unknown number of items, especially non-list grids)

---

## 10. Custom View — MiniTrendChartView

`MiniTrendChartView` extends `View` and draws a sparkline using `Canvas`:

```java
// Usage
chartView.setSeries(listOfFloats);   // triggers invalidate() → onDraw()
chartView.setChartColor(0xFFFF7043); // orange-red
```

`onDraw()` normalises the data, maps float values to pixel coordinates, and draws a `Path` with `canvas.drawPath(path, paint)`.

This is the canonical pattern for lightweight custom charts in Android — avoids adding a charting library dependency (MPAndroidChart is 1.5 MB+).

---

## Summary: Decision Matrix

| Situation | Pattern |
|-----------|---------|
| Access views | ViewBinding |
| Update UI from background | `runOnUiThread(() -> refresh())` |
| Store user settings | `SharedPreferences` via `AppController.get(ctx).preferences()` |
| Format time | `DateUtils.getRelativeTimeSpanString()` |
| Show confirmation | `MaterialAlertDialogBuilder` |
| Dynamic list | `RecyclerView` + Adapter |
| Dynamic grid (non-list) | Programmatic `LinearLayout` rows |
| Theme colour | `?attr/colorXxx` in XML |
| String | `@string/name` in XML, `getString(R.string.name)` in Java |
| Language change | `LocaleHelper.applyAndSave(lang)` |

