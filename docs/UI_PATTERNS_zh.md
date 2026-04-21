# UI 模式

> English version: [UI_PATTERNS.md](UI_PATTERNS.md)

本文件說明 Gsyn Java UI 層中使用的共通模式。  
閱讀本文件後，您應能讀懂程式庫中任何 Fragment 的代碼。

---

## 1. ViewBinding

每個 Fragment 使用 **ViewBinding** 而非 `findViewById`。

```java
// 在類別層級聲明
private FragmentDashboardBinding binding;

// 在 onCreateView 中載入
@Override
public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    binding = FragmentDashboardBinding.inflate(inflater, container, false);
    return binding.getRoot();
}

// 在 onDestroyView 中始終將 binding 設為 null（防止記憶體洩漏）
@Override
public void onDestroyView() {
    binding = null;
    super.onDestroyView();
}
```

**原因：** `findViewById` 容易出錯（類型錯誤、ID 錯誤、執行時 NPE）。ViewBinding 在編譯時生成帶類型的類 — 若在 XML 中重命名視圖 ID，代碼將無法編譯。

**規則：** 在任何可能在 `onDestroyView` 後被呼叫的方法中，存取視圖前必須加 `if (binding == null) return;` 防護。

---

## 2. Fragment 生命週期與監聽器註冊

Fragment 在 `onStart()` 中註冊監聽器，在 `onStop()` 中登出：

```java
@Override
public void onStart() {
    super.onStart();
    transportManager.addMessageListener(this);
    transportManager.addStatsListener(this);
    refresh();  // 初始資料載入
}

@Override
public void onStop() {
    transportManager.removeMessageListener(this);
    transportManager.removeStatsListener(this);
    super.onStop();
}
```

**為何不用 `onResume`/`onPause`？**  
`onStart`/`onStop` 在 Fragment 可見/不可見時呼叫，包括 Activity 進入背景時。`onResume`/`onPause` 在每次焦點變更（如對話框）時呼叫，會導致不必要的重繪。

**為何不用 `onCreate`/`onDestroy`？**  
`onCreate` 時 binding 尚未存在。讓監聽器在 `onDestroy` 後仍存活，會導致回調觸及 null binding。

---

## 3. 背景執行緒 → UI 執行緒

所有 `TransportManager` 回調均在**背景執行緒**觸發。  
從回調更新 UI 的標準模式：

```java
@Override
public void onMessage(Models.DeviceMessage message) {
    // 不要在此觸碰視圖 — 這是背景執行緒！
    if (getActivity() != null) {
        getActivity().runOnUiThread(this::refresh);
    }
}
```

`getActivity() != null` 防護可防止在 Fragment 已分離後回調觸發時崩潰。

---

## 4. 主題設定 — Material3 屬性引用

版面中的所有顏色使用**主題屬性**而非硬編碼十六進位值：

```xml
android:textColor="?attr/colorOnSurface"
android:background="?attr/colorSurface"
app:cardBackgroundColor="?attr/colorSecondaryContainer"
```

這是亮/暗主題 + 強調色系統正常運作的必要條件。  
Material3 提供以下屬性：

| 屬性 | 用途 |
|------|------|
| `?attr/colorPrimary` | 品牌強調色（按鈕、FAB） |
| `?attr/colorSurface` | 卡片背景 |
| `?attr/colorOnSurface` | 卡片上的文字 |
| `?attr/colorPrimaryContainer` | 橫幅背景 |
| `?attr/colorOnPrimaryContainer` | 橫幅上的文字 |
| `?attr/colorSecondaryContainer` | 次要卡片背景 |
| `?attr/colorOnSurfaceVariant` | 次要 / 提示文字 |
| `?attr/colorOutline` | 分割線、邊框 |

**絕不**在版面中寫 `android:textColor="#FFFFFF"`。僅在文字必須始終為白色時使用 `@android:color/white`（例如以程式設定的彩色徽章上的文字）。

---

## 5. 主題疊加架構

主題以三層構建：

```
Theme.GsynJava                    (res/values/themes.xml)
    ↑ 繼承自
Material3 / DayNight 基礎主題

Theme.GsynJava + 強調色疊加   （在 MainActivity.onCreate 的 super 之前套用）
    ThemeOverlay.Accent.Teal
    ThemeOverlay.Accent.DeepBlue
    ...

Theme.GsynJava + 背景疊加     （與強調色同時套用）
    ThemeOverlay.Bg.DarkNavy
    ThemeOverlay.Bg.SnowWhite
    ...
```

`AppThemeConfig.applyTheme(activity)` 從 `SharedPreferences` 讀取兩個預設並呼叫：

```java
activity.getTheme().applyStyle(accentOverlayResId, true);
activity.getTheme().applyStyle(bgOverlayResId, true);
```

`true` 參數表示疊加會**覆蓋**基礎主題值。

`AppThemeConfig.applyBgToRoot(view, context)` 以背景顏色塗抹 Fragment 的根視圖，確保在版面繪製前也能與視窗背景匹配。

---

## 6. 國際化（i18n）

所有使用者可見字串放在 `res/values/strings.xml`（英文，預設）和 `res/values-zh/strings.xml`（中文）中。

**黃金法則：** 絕不在 Java 或 XML 中硬編碼中文或英文文字。XML 中使用 `@string/...`，Java 中使用 `getString(R.string....)`。

### 語言切換

```java
// LocaleHelper 封裝了 AppCompatDelegate API（AppCompat 1.6+）
LocaleHelper.applyAndSave("en");  // 切換到英文，自動持久化
LocaleHelper.applyAndSave("zh");  // 切換到中文
LocaleHelper.applyAndSave("");    // 跟隨系統設定

// 讀取當前選擇
String lang = LocaleHelper.current(); // "en"、"zh" 或 ""
```

`AppCompatDelegate.setApplicationLocales()` 負責跨重啟的持久化並自動觸發 Activity 重建。**無需手動寫入 `SharedPreferences`。**

### 感知地區的時間格式化

```java
// 使用 DateUtils 格式化相對時間 — 自動本地化
String relTime = (String) DateUtils.getRelativeTimeSpanString(
    timestampMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
// → "5 minutes ago"（英文）或 "5 分钟前"（中文）
```

**絕不**在 Java 代碼中將時間格式化為 `"X 分钟前"`。請使用 `DateUtils`。

---

## 7. 多視圖類型的 RecyclerView

`DashboardCardAdapter` 中使用的模式：

```java
// 1. 為每種類型定義 int 常數
static final int TYPE_HEADER        = 0;
static final int TYPE_KPI_ROW1      = 1;
static final int TYPE_CUSTOM_SENSOR = 8;

// 2. 將項目映射到類型
@Override
public int getItemViewType(int position) {
    switch (items.get(position).type) {
        case HEADER:        return TYPE_HEADER;
        case CUSTOM_SENSOR: return TYPE_CUSTOM_SENSOR;
        // ...
    }
}

// 3. 按類型載入正確的版面
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

// 4. 每個 ViewHolder 是帶有 bind() 方法的靜態內部類
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

**關鍵認識：** `onCreateViewHolder` 很少被呼叫（只有新格子滾入可見範圍時）。`onBindViewHolder` 在每次資料變更時被呼叫。保持 `onBindViewHolder` 快速 — 不查詢資料庫，不創建物件。

---

## 8. MaterialAlertDialog 模式

```java
new MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.my_dialog_title))
        .setView(customView)
        .setPositiveButton(android.R.string.ok, (dlg, w) -> {
            // 處理確認
        })
        .setNegativeButton(android.R.string.cancel, null) // null = 直接關閉
        .show();
```

使用 `android.R.string.ok` 和 `android.R.string.cancel` 作為標準系統本地化按鈕標籤，而非自行定義。

---

## 9. 程式化視圖（XML 不足時）

當版面需要在執行時生成未知數量的子視圖（例如單裝置模式中的感測器讀數網格）：

```java
// 以程式建立卡片
MaterialCardView card = new MaterialCardView(requireContext());
LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);  // 權重=1，填充半寬
card.setLayoutParams(params);
card.setUseCompatPadding(true);

// 新增子視圖
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

**何時用程式化 vs XML：**
- XML：固定結構（視圖數量始終相同）
- 程式化：動態結構（未知數量的項目，尤其是非清單網格）

---

## 10. 自定義視圖 — MiniTrendChartView

`MiniTrendChartView` 繼承 `View`，使用 `Canvas` 繪製迷你折線圖：

```java
// 使用方式
chartView.setSeries(listOfFloats);   // 觸發 invalidate() → onDraw()
chartView.setChartColor(0xFFFF7043); // 橙紅色
```

`onDraw()` 對資料進行標準化，將浮點值映射到像素座標，並使用 `canvas.drawPath(path, paint)` 繪製 `Path`。

這是 Android 中輕量自定義圖表的標準模式 — 避免添加圖表庫依賴（MPAndroidChart 超過 1.5 MB）。

---

## 摘要：決策矩陣

| 情況 | 模式 |
|------|------|
| 存取視圖 | ViewBinding |
| 從背景執行緒更新 UI | `runOnUiThread(() -> refresh())` |
| 儲存使用者設定 | 透過 `AppController.get(ctx).preferences()` 使用 `SharedPreferences` |
| 格式化時間 | `DateUtils.getRelativeTimeSpanString()` |
| 顯示確認對話框 | `MaterialAlertDialogBuilder` |
| 動態清單 | `RecyclerView` + Adapter |
| 動態網格（非清單） | 程式化 `LinearLayout` 列 |
| 主題顏色 | XML 中使用 `?attr/colorXxx` |
| 字串 | XML 中使用 `@string/name`，Java 中使用 `getString(R.string.name)` |
| 語言切換 | `LocaleHelper.applyAndSave(lang)` |
