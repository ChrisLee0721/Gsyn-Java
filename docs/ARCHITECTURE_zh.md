# 架構指南

> English version: [ARCHITECTURE.md](ARCHITECTURE.md)

> 本文件說明 Gsyn Java 的內部設計及關鍵決策的依據。

> **初次接觸本專案？** 請先閱讀 [GETTING_STARTED.md](GETTING_STARTED.md)，再回到此處。

---

## 分層架構

```
┌──────────────────────────────────────────────┐
│                  UI 層                        │  Fragment、View、Adapter
├──────────────────────────────────────────────┤
│              AppController                    │  單一協調中心
├────────────────┬─────────────┬───────────────┤
│  AppRepository │ TransportMgr│  RulesEngine  │  業務邏輯
├────────────────┴─────────────┴───────────────┤
│              協議層                            │  Codec、CRC、Base62、DiffEngine
├──────────────────────────────────────────────┤
│         SQLite (AppDatabaseHelper)            │  資料持久化
└──────────────────────────────────────────────┘
```

### 設計原則

1. **不使用框架級依賴注入** — `AppController` 是單例，負責串聯所有元件。  
   Flutter 版本使用 Riverpod，Java 版使用更簡單的單例模式，避免引入 Dagger/Hilt 的額外開銷。

2. **協議層為純 Java** — `core/protocol/` 中不依賴任何 Android API，使單元測試無需 Robolectric 或裝置模擬器。

3. **Repository 是唯一的資料庫寫入者** — 所有 SQLite 寫入均透過 `AppRepository`。`TransportManager` 解碼後呼叫 Repository 方法，絕不直接存取資料庫。

4. **監聽器優於 LiveData** — `TransportManager` 與 `RulesEngine` 透過簡單的監聽器介面通知。Fragment 在 `onStart()` 註冊、`onStop()` 登出，避免記憶體洩漏。

---

## AppController 生命週期

```
Application.onCreate()
  └── AppController.get(context)          ← 延遲初始化單例
        ├── AppDatabaseHelper.getInstance()
        ├── AppRepository(dbHelper)
        ├── TransportManager.get(context)
        │     └── 將 AppRepository 註冊為儲存目標
        └── RulesEngine(repository)
              └── 每次訊息批次處理後由 TransportManager 呼叫
```

---

## 封包接收流程

```
NetworkThread (UDP) / MqttCallback (MQTT)
  │
  │  原始 byte[]
  ▼
PacketDecoder.decode(bytes)
  │  驗證 CRC-8
  │  解析標頭 (CMD/AID/TID/SEQ/LEN)
  │  回傳 Result { valid, meta, rawBody }
  ▼
DiffEngine.process(cmd, aid, rawBody)
  │  FULL  → 儲存 template[aid]，回傳解析後的讀數
  │  DIFF  → 對 template[aid] 套用差異，回傳完整讀數
  │  HEART → 原樣回傳 template[aid]
  ▼
AppRepository
  │  upsertDevice(aid, status="online", lastSeenMs=now)
  │  insertSensorDataBatch(aid, readings)
  ▼
RulesEngine.evaluate(aid, readings)
  │  對每條啟用規則中符合 sensorId 的讀數：
  │    若條件成立且不在冷卻期：
  │      action = "create_alert" → repository.insertAlert()
  │      action = "send_command" → transportManager.sendUdp(...)
  │      action = "log_only"     → repository.logOperation(...)
  ▼
MessageListeners.onMessage(DeviceMessage)
  │  在背景執行緒呼叫
  │  UI Fragment 呼叫 getActivity().runOnUiThread(this::refresh)
```

---

## 封包發送流程

```
UI（SendFragment 按鈕點擊）
  │
  ▼
PacketBuilder.buildXxx(aid, tid, seq, ...)
  │  構建 byte[]：標頭 + 載體 + CRC8
  ▼
TransportManager.sendUdp(bytes, host, port)
  │  在呼叫執行緒執行 DatagramSocket.send()（UI 執行緒發送短封包沒問題）
  │
  └── 另外：TransportManager.publishMqtt(bytes, topic)
              在呼叫執行緒執行 MqttClient.publish()
```

---

## 資料庫設計決策

### 為何使用 SQLite 而非 Room？

- 保持依賴最少
- `AppDatabaseHelper` 約 200 行，涵蓋所有查詢需求
- Room 的注解處理器會增加建置時間，在此規模下無顯著收益

### 資料保留

`sensor_data` 在每次 `insertSensorDataBatch()` 時自動清除 **7 天**前的資料：

```java
db.execSQL("DELETE FROM sensor_data WHERE timestamp_ms < ?",
           new Object[]{System.currentTimeMillis() - 7 * 24 * 3600 * 1000L});
```

此設計可防止資料無限增長，無需另行排程背景任務。

---

## 執行緒模型

| 執行緒 | 職責 |
|--------|------|
| Main (UI) | Fragment 生命週期、視圖更新 |
| `udpThread` | 阻塞式 `DatagramSocket.receive()` 迴圈 |
| `mqttCallbackThread` | Eclipse Paho 回調執行緒 |
| `scheduler`（單執行緒） | 每秒統計 tick，透過 `ScheduledExecutorService` |

所有監聽器回調均在背景執行緒觸發，UI 更新必須透過 `runOnUiThread()` 分派。

---

## 主題系統

主題以 **Material3 樣式疊加**方式實現，在 `super.onCreate()` 之前套用：

```
res/values/themes.xml
  Theme.GsynJava                 ← Material3 基礎主題

res/values/theme_overlays.xml
  ThemeOverlay.Accent.Teal       ← colorPrimary = 藍綠色
  ThemeOverlay.Accent.Indigo     ← colorPrimary = 靛藍色
  ...
  ThemeOverlay.Bg.Dark           ← colorBackground = #1A1A2E，isLight=false
  ThemeOverlay.Bg.Warm           ← colorBackground = #FFF8F0
  ...
```

`AppThemeConfig` 從 `SharedPreferences` 讀取使用者的預設，套用對應的疊加組合。背景疊加也會驅動 Google Maps 深色風格及 NavigationView 背景色。

---

## Fragment 導航模型

```
MainActivity
  ├── fragment_container (FrameLayout)  ← 所有 Fragment 的宿主
  ├── BottomNavigationView              ← 分頁：儀表板/裝置/告警/發送/設定
  └── NavigationView (DrawerLayout)     ← 擴展：地圖/歷史/規則/健康

分頁選擇：
  bottomNav.setOnItemSelectedListener → showFragment(new XxxFragment(), title, subtitle)
  同時同步側邊欄選中項目

側邊欄選擇：
  onNavigationItemSelected:
    - 主分頁  → bottomNav.setSelectedItemId(id)（觸發上述流程）
    - 擴展頁  → showFragment(new XxxMirrorFragment(), ...)
                 取消所有底部導航欄選中狀態
```

擴展頁（地圖、歷史、規則、健康）直接載入至 `MainActivity` 的 `fragment_container`，讓側邊欄與底部導航欄始終可見。

---

## Google Maps 整合

`MapMirrorFragment` 透過 `getChildFragmentManager()` 以程式方式加入 `SupportMapFragment`：

```java
SupportMapFragment mapFrag = SupportMapFragment.newInstance();
getChildFragmentManager().beginTransaction()
    .add(R.id.mapContainer, mapFrag, "MAP_FRAG")
    .commitNow();          // ← commitNow() 至關重要
mapFrag.getMapAsync(this);
```

`commitNow()`（而非 `commit()`）確保 Fragment 在 `getMapAsync` 被呼叫前已同步掛載，避免地圖空白的競態條件。

API 金鑰在建置時透過 `local.properties` 的 `manifestPlaceholders` 注入。Debug 版本的 applicationId **不**附加 `.debug`，確保 API 金鑰的 Android 應用程式限制同時適用於 Debug 與 Release 版本。

---

## 儀表板卡片系統（v1.2.0+）

儀表板為可設定、可拖曳排序的卡片清單，詳見 [DASHBOARD_CARDS.md](DASHBOARD_CARDS.md)。

```
DashboardFragment
    ├── DashboardCardConfig   ← 以 JSON 序列化卡片順序至 SharedPreferences
    ├── DashboardCardAdapter  ← 含 9 種視圖類型的 RecyclerView 適配器
    │     └── Snapshot        ← 每次 refresh() 建立一次的不可變資料快照
    └── ItemTouchHelper       ← 拖曳回調：moveItem() → persistOrderFromAdapter()
```

核心設計決策：**以 Snapshot 作為資料契約。** `DashboardFragment.refresh()` 只建立一個 `Snapshot` 物件並傳遞給每張卡片，而非讓每個 ViewHolder 自行查詢資料庫。優點：
- 每次刷新週期只查詢一次資料庫
- ViewHolder 無狀態，可安全回收
- 新增卡片類型只需在 `Snapshot` 加欄位並在 `refresh()` 讀取

---

## 多語言系統（v1.2.0+）

應用程式語言切換使用 `AppCompatDelegate.setApplicationLocales()`（AppCompat 1.6+）：

```java
// 切換語言 — 跨重啟持久化，並自動觸發 Activity 重建
LocaleListCompat locales = LocaleListCompat.forLanguageTags("zh");
AppCompatDelegate.setApplicationLocales(locales);
```

Android 13+ 還需在 `res/xml/locale_config.xml` 及 Manifest 中聲明 `android:localeConfig`。

**所有使用者可見文字**必須放在 `res/values/strings.xml`（英文）與 `res/values-zh/strings.xml`（中文）中。Java 或 XML 中的硬編碼字串不會響應語言切換。
