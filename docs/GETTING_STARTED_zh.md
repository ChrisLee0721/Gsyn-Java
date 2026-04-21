# 開發者入門指南

> English version: [GETTING_STARTED.md](GETTING_STARTED.md)

歡迎使用 **Gsyn Java**，一款基於 OpenSynaptic 協議的 Android 遙測控制台。  
本指南幫助您在 30 分鐘內搭建工作開發環境並理解專案結構。

---

## 前置需求

| 工具 | 版本 | 說明 |
|------|------|------|
| Android Studio | Hedgehog 2023.1+ | 或更新版本 |
| JDK | 17 | 在 `compileOptions` 中設定 |
| Android SDK | API 34（編譯），API 24（最低） | |
| Git | 任意版本 | |
| Google Maps API 金鑰 | — | 可選，用於地圖頁面 |

---

## 複製與開啟

```bash
git clone https://github.com/ChrisLee0721/Gsyn-Java.git
cd Gsyn-Java
```

開啟 **Android Studio → File → Open** → 選擇 `Gsyn-Java` 資料夾。

等待 Gradle 同步完成（首次下載約 200 MB 依賴項）。

---

## 設定

### Maps API 金鑰（可選）

在專案根目錄建立或編輯 `local.properties`：

```properties
MAPS_API_KEY=AIzaSy...yourkey...
```

不設定此金鑰時，地圖頁面顯示空白灰色磚塊 — 其他功能均正常運作。

### 簽名（僅發布版本需要）

CI 透過 `-P` Gradle 屬性注入簽名。本地發布版本建置時，在 `local.properties` 中添加：

```properties
android.injected.signing.store.file=/path/to/release.jks
android.injected.signing.store.password=...
android.injected.signing.key.alias=...
android.injected.signing.key.password=...
```

Debug 版本會自動自簽名，無需任何設定。

---

## 建置與執行

```bash
./gradlew assembleDebug          # 建置 APK
./gradlew installDebug           # 建置並安裝到已連接的裝置/模擬器
./gradlew test                   # 單元測試
```

或在 Android Studio 中按綠色 ▶ 按鈕。

---

## 專案結構

```
app/src/main/
├── java/com/opensynaptic/gsynjava/
│   ├── AppController.java            ← 串聯所有元件的單例
│   ├── core/
│   │   ├── AppThemeConfig.java       ← 主題 + 背景預設
│   │   ├── LocaleHelper.java         ← 應用程式語言切換
│   │   └── protocol/                 ← 純 Java 編解碼器（無 Android 依賴）
│   │       ├── PacketDecoder.java
│   │       ├── PacketBuilder.java
│   │       ├── DiffEngine.java
│   │       └── CRC8.java
│   ├── data/
│   │   ├── AppRepository.java        ← 所有資料庫讀寫
│   │   ├── AppDatabaseHelper.java    ← 原始 SQLite 結構 + 遷移
│   │   └── Models.java               ← 純資料類（無 ORM）
│   ├── transport/
│   │   └── TransportManager.java     ← UDP + MQTT 監聽執行緒
│   ├── rules/
│   │   └── RulesEngine.java          ← 閾值評估
│   └── ui/
│       ├── MainActivity.java         ← DrawerLayout + BottomNav 宿主
│       ├── common/
│       │   ├── UiFormatters.java     ← 感知地區的格式化輔助工具
│       │   └── BaseSecondaryFragment.java
│       ├── dashboard/
│       │   ├── DashboardFragment.java
│       │   ├── DashboardCardAdapter.java
│       │   ├── DashboardCardConfig.java
│       │   └── DashboardCardItem.java
│       ├── devices/   alerts/   send/   settings/
│       ├── mirror/               ← 地圖、歷史、規則、健康
│       └── widget/
│           └── MiniTrendChartView.java  ← 自定義 Canvas 圖表
├── res/
│   ├── layout/                   ← XML 版面（已啟用 ViewBinding）
│   ├── values/strings.xml        ← 英文字串
│   ├── values-zh/strings.xml     ← 中文字串
│   ├── values/themes.xml         ← Material3 基礎主題
│   ├── values/theme_overlays.xml ← 強調色 + 背景疊加
│   └── xml/locale_config.xml     ← Android 13+ 地區設定聲明
└── AndroidManifest.xml
```

---

## 主要依賴項

```groovy
implementation 'androidx.appcompat:appcompat:1.7.0'         // AppCompatDelegate 語言 API
implementation 'com.google.android.material:material:1.12.0' // Material3 元件
implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
implementation 'com.google.android.gms:play-services-maps:18.2.0'
```

無 Retrofit、無 Room、無 Dagger。這是刻意的設計 — 詳見 [ARCHITECTURE.md](ARCHITECTURE.md)。

---

## 快速定向：啟動時發生了什麼

1. `MainActivity.onCreate()` 呼叫 `AppController.get(context)` 初始化單例圖
2. `DashboardFragment` 可見 → 呼叫 `transportManager.addMessageListener(this)`
3. 使用者在設定中啟用 UDP → `TransportManager.startUdp()` 開啟 `DatagramSocket`
4. 封包到達 → 解碼 → 存入 SQLite → 觸發 `onMessage()` → `refresh()` 更新 UI

這一切都在任何裝置連接之前發生。一旦傳輸啟用，應用程式始終處於*監聽*狀態。

---

## 下一步

- 閱讀 **[ARCHITECTURE.md](ARCHITECTURE.md)** 了解完整的分層設計
- 閱讀 **[PROTOCOL.md](PROTOCOL.md)** 了解二進位封包格式
- 閱讀 **[DASHBOARD_CARDS.md](DASHBOARD_CARDS.md)** 了解卡片系統
- 閱讀 **[UI_PATTERNS.md](UI_PATTERNS.md)** 了解 ViewBinding、主題和國際化模式
