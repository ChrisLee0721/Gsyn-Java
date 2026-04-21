# 儀表板卡片系統

> English version: [DASHBOARD_CARDS.md](DASHBOARD_CARDS.md)

儀表板以完全可自訂的卡片清單構成。  
本文件說明各層如何運作，讓您能夠新增卡片類型或修改現有卡片。

---

## 概覽

```
DashboardFragment
    │
    ├── DashboardCardConfig      ← 將有序的 DashboardCardItem 清單持久化至 SharedPreferences
    │
    ├── DashboardCardAdapter     ← 含 9 種視圖類型的 RecyclerView 適配器
    │       └── Snapshot         ← 傳遞給每個 ViewHolder 的純資料快照
    │
    └── ItemTouchHelper          ← 拖曳排序回調，連接至適配器
```

---

## DashboardCardItem

卡片項目是一個包含三個重要欄位的簡單資料類：

```java
public class DashboardCardItem {
    public enum Type {
        HEADER,           // 始終置頂，不可拖曳
        KPI_ROW1,         // 總裝置數 + 在線率
        KPI_ROW2,         // 活躍告警 + 吞吐量
        KPI_ROW3,         // 規則數 + 累計流量
        GAUGES,           // 水位 + 濕度進度條
        CHARTS,           // 迷你趨勢折線圖
        ACTIVITY,         // 近期告警 + 操作日誌
        LATEST_READINGS,  // 原始感測器讀數清單
        CUSTOM_SENSOR     // 使用者自定義：顯示指定感測器 ID 的讀數
    }

    public Type    type;
    public boolean visible;
    public int     order;
    public String  sensorId;   // 僅用於 CUSTOM_SENSOR
    public String  label;      // CUSTOM_SENSOR 的顯示名稱
}
```

---

## DashboardCardConfig

將卡片清單以 JSON 格式序列化至 `SharedPreferences`（鍵名 `cards_json_v2`）。

```java
// 載入（首次執行時建立預設值）
DashboardCardConfig cfg = DashboardCardConfig.load(context);

// 取得按儲存順序排列的可見卡片
List<DashboardCardItem> visible = cfg.visibleCards();

// 切換可見性
cfg.setVisible(DashboardCardItem.Type.GAUGES, false);

// 新增自定義感測器卡片
cfg.addCustomSensor("TEMP1", "室內溫度");

// 移除任意卡片
cfg.removeCard(item);

// 持久化
cfg.save(context);
```

**儲存的 JSON 範例：**
```json
[
  {"type":"HEADER",        "visible":true,  "order":0, "sensorId":"", "label":""},
  {"type":"KPI_ROW1",      "visible":true,  "order":1, "sensorId":"", "label":""},
  {"type":"CUSTOM_SENSOR", "visible":true,  "order":8, "sensorId":"TEMP1", "label":"室內溫度"}
]
```

---

## DashboardCardAdapter

繼承 `RecyclerView.Adapter<RecyclerView.ViewHolder>`，支援多種視圖類型。

### 視圖類型分派

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

每種類型在 `onCreateViewHolder` 中載入其對應的版面文件（`item_dashboard_*.xml`）。

### Snapshot — 資料契約

每個 ViewHolder 接收單一 `Snapshot` 物件，而非自行查詢資料庫：

```java
// DashboardFragment.refresh() 建立快照：
DashboardCardAdapter.Snapshot snap = new DashboardCardAdapter.Snapshot();
snap.totalDevices = repository.getTotalDeviceCount();
snap.latestTemp   = ...;          // 從歷史查詢中提取
snap.latestBySensorId = ...;      // Map<String, SensorData>，用於 CUSTOM_SENSOR
snap.trendBySensorId  = ...;      // Map<String, List<Float>>，用於趨勢圖表
// ...
cardAdapter.setSnapshot(snap);    // 觸發 notifyDataSetChanged()
```

此模式使 ViewHolder 保持無狀態，易於測試。

---

## 拖曳排序

使用 `ItemTouchHelper.Callback` 附加到 `RecyclerView`：

```java
touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
    @Override
    public int getMovementFlags(RecyclerView rv, RecyclerView.ViewHolder vh) {
        // 位置 0（HEADER）被鎖定
        if (vh.getAdapterPosition() == 0) return 0;
        return makeMovementFlags(UP | DOWN, 0);  // 僅拖曳，不滑動
    }

    @Override
    public boolean onMove(..., ViewHolder from, ViewHolder to) {
        cardAdapter.moveItem(from.getAdapterPosition(), to.getAdapterPosition());
        return true;
    }

    @Override
    public void clearView(...) {
        persistOrderFromAdapter(); // 將新順序寫入 SharedPreferences
    }
});
touchHelper.attachToRecyclerView(binding.rvDashboard);
```

`persistOrderFromAdapter()` 根據適配器當前順序重建 `cardConfig.cards`（可見卡片在前，隱藏卡片追加在後）：

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

## 自定義感測器卡片

使用者點擊 FAB（+）時：

1. `MaterialAlertDialog` 詢問**感測器 ID**（如 `TEMP1`）和可選的顯示標籤
2. `DashboardCardConfig.addCustomSensor(sid, label)` 追加新的 `CUSTOM_SENSOR` 項目
3. `cardConfig.save()` + `cardAdapter.setItems(cfg.visibleCards())` 立即更新清單
4. 呼叫 `refresh()` 使卡片立即獲取資料

在 `CustomSensorVH.bind()` 中：
```java
// 以感測器 ID 查詢最新讀數（不區分大小寫）
Models.SensorData data = snap.latestBySensorId.get(item.sensorId.toUpperCase());
if (data != null) {
    tvSensorValue.setText(trimNumber(data.value));
    tvSensorMeta.setText("最後更新：" + formatRelativeTime(data.timestampMs));
    chartSensor.setSeries(snap.trendBySensorId.get(item.sensorId.toUpperCase()));
} else {
    tvSensorValue.setText("—");
    tvSensorMeta.setText("此感測器尚無資料");
}
```

---

## 單裝置專注模式

透過 `SharedPreferences` 中的布爾值（`single_device_mode`）控制。

```java
// DashboardFragment.refresh()
boolean singleModeEnabled = prefs.getBoolean("single_device_mode", false);
if (singleModeEnabled) {
    enterSingleDeviceMode(true);       // 隱藏 RecyclerView + FAB，顯示 ScrollView
    List<Models.Device> devices = repository.getAllDevices();
    if (!devices.isEmpty()) {
        refreshSingleDeviceView(devices.get(0), snap);
    } else {
        // 顯示佔位符「等待裝置連接…」
    }
    return;
}
enterSingleDeviceMode(false);          // 顯示 RecyclerView + FAB
```

切換開關在**兩個**位置提供以便發現：
1. **儀表板標頭卡片** — `btnToggleSingleMode` 按鈕，立即生效
2. **設定 → 卡片可見性區段** — `switchSingleDeviceMode`，立即生效（無需保存）

兩者均寫入相同的 `SharedPreferences` 鍵並呼叫 `refresh()`。

---

## 如何新增卡片類型

1. 在 `DashboardCardItem.Type` 中**新增枚舉值**
2. **建立版面文件** `item_dashboard_mycard.xml`
3. 在 `DashboardCardAdapter` 中**新增 ViewHolder 內部類**
4. **新增視圖類型常數**（`TYPE_MYCARD = 9`）
5. 在 `getItemViewType()`、`onCreateViewHolder()` 和 `onBindViewHolder()` 中**處理它**
6. 如果卡片需要新資料，在 `Snapshot` 中**新增欄位**
7. 在 `DashboardFragment.refresh()` 中**填充這些欄位**
8. 如果應預設顯示，在 `DashboardCardConfig.defaultCards()` 中**新增預設項目**
9. 在 `fragment_settings.xml` + `SettingsFragment.loadCardConfig()` / `saveCardConfig()` 中**新增切換開關**
