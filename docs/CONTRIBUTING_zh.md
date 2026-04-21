# 貢獻指南

> English version: [CONTRIBUTING.md](CONTRIBUTING.md)

本指南適用於希望新增功能、修復 Bug 或提交改進的開發者。

---

## 開發理念

> **可讀性優於技巧性。**

本專案同時也是教學參考。比起需要仔細思考的 2 行 lambda，更推薦明確的 10 行方法。初級開發者和學生應能在不使用偵錯器的情況下追蹤邏輯。

---

## 分支與提交規範

```
main            → 穩定的標記發布版本
feature/xxx     → 新功能分支
fix/xxx         → Bug 修復分支
```

**提交訊息格式：**

```
type: 簡短描述（72 字元以內）

- 要點說明
- 另一個要點
```

類型：`feat`、`fix`、`refactor`、`docs`、`test`、`chore`

---

## 編碼前檢查清單

- [ ] 閱讀 [ARCHITECTURE.md](ARCHITECTURE.md) 中相關章節
- [ ] 閱讀 [UI_PATTERNS.md](UI_PATTERNS.md)，確保代碼遵循既有模式
- [ ] 執行 `./gradlew assembleDebug` 確認專案能在本機編譯
- [ ] 在撰寫新方法前，先確認 `UiFormatters`、`AppRepository` 或 `AppThemeConfig` 中是否已有輔助方法

---

## 新增頁面 / Fragment

1. 建立繼承 `Fragment` 的 `MyNewFragment.java`
2. 建立 `fragment_my_new.xml` 版面
3. 所有顏色必須使用 `?attr/colorXxx` 主題屬性，不得硬編碼十六進位值
4. 所有字串必須放在 `res/values/strings.xml`（英文）與 `res/values-zh/strings.xml`（中文）
5. 在 `MainActivity` 中新增導航：
   - 底部導航分頁 → 在 `res/menu/bottom_nav.xml` 新增項目 + 在 `setOnItemSelectedListener` 中處理
   - 側邊欄擴展 → 在 `res/menu/drawer_nav.xml` 新增項目 + 在 `onNavigationItemSelected` 中處理

---

## 新增傳輸協議

1. 在 `transport/` 中建立 `MyTransport.java`
2. 實作 `start()`、`stop()`、`send(byte[])`
3. 每次收到封包時呼叫：
   ```java
   DecodeResult result = PacketDecoder.decode(rawBytes);
   if (result.valid) {
       List<SensorReading> readings = DiffEngine.process(result.cmd, result.aid, result.rawBody);
       repository.insertSensorDataBatch(result.aid, readings);
       rulesEngine.evaluate(result.aid, readings);
       notifyMessageListeners(new DeviceMessage(result.aid, readings));
   }
   ```
4. 在 `TransportManager` 中註冊啟動/停止，並在 `SettingsFragment` 中提供切換開關

---

## 新增規則動作類型

1. 在 `RulesEngine` 中新增字串常數（如 `"send_webhook"`）
2. 在 `RulesEngine.evaluate()` 中新增 `case` 分支實作動作邏輯
3. 在 `RulesMirrorFragment` 的規則建立對話框中新增選項
4. 在兩個語言文件中新增對應的字串資源

---

## 字串資源檢查清單

新增任何使用者可見文字時：

```
res/values/strings.xml      → 英文（必填）
res/values-zh/strings.xml   → 中文（必填）
```

命名格式：`section_description`，例如：
- `dashboard_label_total_devices`
- `settings_single_device_mode`
- `mirror_rules_dialog_title`

不要為不相關的概念使用相同的字串鍵。如果某個短語出現在兩個地方，可以使用兩個不同的鍵（翻譯可能有所不同）。

---

## 代碼風格

- **Java 8** — 可使用 Lambda 和 Stream；Record 和 `var` 不可用（minSdk 24 需要脫糖處理）
- **4 空格縮進**
- **禁止萬用字元匯入**（`import java.util.*` 不允許）
- **空值安全** — 在可能在 `onDestroyView` 後被非同步呼叫的 Fragment 方法中，存取視圖前必須空值檢查 `binding`
- **背景執行緒防護** — 所有來自 `TransportManager` 的回調都在背景執行緒；接觸視圖前必須呼叫 `runOnUiThread()`

---

## 測試

單元測試位於 `app/src/test/`。協議層完全可在無 Android 環境下測試：

```java
// 範例：測試封包解碼
@Test
public void testDecodeValidPacket() {
    byte[] packet = buildTestPacket(CMD_DATA_FULL, 42, ...);
    DecodeResult result = PacketDecoder.decode(packet);
    assertTrue(result.valid);
    assertEquals(42, result.aid);
}
```

需要裝置/模擬器的儀器測試放在 `app/src/androidTest/`。

執行所有單元測試：
```bash
./gradlew test
```

---

## 發布流程

1. 在 `app/build.gradle` 中更新 `versionCode` 和 `versionName`
2. `git add -A && git commit -m "chore: bump version to x.y.z"`
3. `git tag -a vx.y.z -m "vx.y.z: Release notes here"`
4. `git push origin main --tags`
5. GitHub Actions 自動建置簽名 APK（見 `.github/workflows/`）

---

## 常見陷阱

| 錯誤 | 正確做法 |
|------|---------|
| `binding.tvFoo.setText(R.string.bar)` | `binding.tvFoo.setText(getString(R.string.bar))` — R.string 是 int，不是 String |
| Java 中硬編碼 `"5 分钟前"` | 使用 `DateUtils.getRelativeTimeSpanString(...)` |
| XML 中 `android:textColor="#000000"` | `android:textColor="?attr/colorOnSurface"` |
| 在 `onBindViewHolder` 中查詢資料庫 | 在 `Fragment.refresh()` 中查詢，透過 `Snapshot` 傳遞資料 |
| MapFragment 使用 `commit()` | 必須使用 `commitNow()` — 見 ARCHITECTURE.md §Google Maps |
| 未做空值檢查直接呼叫 `getActivity().runOnUiThread(...)` | `if (getActivity() != null) getActivity().runOnUiThread(...)` |
