# Gsyn 二進位協議參考

本文件描述在 `core/protocol/` 中實現的 Gsyn 有線協議。  
對應 Flutter 源碼 `lib/protocol/codec/`。

> English version: [PROTOCOL.md](PROTOCOL.md)

---

## 封包幀結構

```
┌────────┬────────┬────────┬────────┬────────┬─────────────────┬────────┐
│ CMD    │ AID    │ TID    │ SEQ    │ LEN    │ BODY            │ CRC8   │
│ 1 位元組│ 1 位元組│ 1 位元組│ 1 位元組│ 1 位元組│ LEN 位元組      │ 1 位元組│
└────────┴────────┴────────┴────────┴────────┴─────────────────┴────────┘
```

| 欄位 | 類型 | 描述 |
|------|------|------|
| CMD | `uint8` | 命令位元組 — 決定 Body 格式 |
| AID | `uint8` | 來源應用程式 ID（節點地址，1–254） |
| TID | `uint8` | 目標應用程式 ID（0 = 廣播） |
| SEQ | `uint8` | 序列計數器 — 255 後回繞 |
| LEN | `uint8` | Body 位元組數（0–255） |
| BODY | `bytes[LEN]` | 命令特定的載荷 |
| CRC8 | `uint8` | 對 bytes 0..(4+LEN) 計算的 CRC-8/SMBUS |

---

## 命令表

### 控制命令（0x01–0x0F）

| CMD | 十六進位 | Body | 回應 | 描述 |
|-----|---------|------|------|------|
| PING | `0x01` | 空 | PONG | 心跳探測 |
| PONG | `0x02` | 空 | — | 心跳回覆 |
| ID_REQUEST | `0x03` | 空 | ID_ASSIGN | 無 AID 的節點請求分配 |
| ID_ASSIGN | `0x05` | `[assigned_aid: 1B]` | — | 主機分配 AID |
| TIME_REQUEST | `0x07` | 空 | TIME_RESPONSE | 請求當前 UNIX 時間 |
| TIME_RESPONSE | `0x08` | `[unix_sec: 4B 大端序]` | — | 32 位元 UNIX 時間戳 |
| HANDSHAKE_ACK | `0x09` | 空 | — | 接受握手 |
| HANDSHAKE_NACK | `0x0A` | 空 | — | 拒絕握手 |

### 安全命令（0x10–0x1F）

| CMD | 十六進位 | Body | 描述 |
|-----|---------|------|------|
| SECURE_DICT_READY | `0x10` | `[dict_id: 1B]` | 宣告加密字典就緒 |

### 資料命令（0x20–0x2F）

| CMD | 十六進位 | Body 格式 | 描述 |
|-----|---------|----------|------|
| DATA_FULL | `0x20` | 多感測器 Body（見下文） | 所有感測器的完整快照 |
| DATA_DIFF | `0x21` | Diff Body（見下文） | 僅變更的感測器 |
| DATA_HEART | `0x22` | 空 | 重用最後的 FULL 模板 |
| DATA_FULL_SENSOR | `0x23` | 單感測器 Body | 單個感測器讀數 |

---

## Body 格式

### DATA_FULL / DATA_FULL_SENSOR Body

每個感測器佔用一條以換行符結尾的記錄：

```
[sensor_id: 2 ASCII 字元][unit_code: 1 位元組][state_code: 1 位元組][value: Base62 字串]\n
```

範例（TEMP 感測器，25.6 °C，狀態正常）：
```
TE\x01\x0015A\n
```

多個感測器直接串接（每條以 `\n` 結尾，無分隔符）。

### DATA_DIFF Body

相對於儲存的 FULL 模板，僅包含已變更的欄位：

```
[sensor_id: 2 位元組][changed_fields_bitmask: 1 位元組][...已變更欄位...]
```

位元遮罩位元：`bit0=值` `bit1=狀態` `bit2=單位`

`DiffEngine` 透過將差異應用於模板來重建完整讀數清單。

### DATA_HEART Body

空 Body。`DiffEngine` 重新發出指定 AID 的最後一個 FULL 模板。

---

## 感測器 ID 代碼（2 ASCII 位元組）

| 感測器 | 代碼 | 預設單位 |
|--------|------|---------|
| 溫度 | `TE` | °C |
| 濕度 | `HU` | % |
| 氣壓 | `PR` | hPa |
| 水位 | `LV` | % |
| CO₂ | `CO` | ppm |
| 光照 | `LI` | lux |
| 移動 | `MO` | 布爾值 |
| 通用 | `G0`–`G9` | — |

---

## 單位代碼（1 位元組）

| 代碼 | 符號 | 含義 |
|------|------|------|
| `0x00` | — | 無單位 / 布爾值 |
| `0x01` | °C | 攝氏度 |
| `0x02` | % | 百分比 |
| `0x03` | hPa | 百帕 |
| `0x04` | ppm | 百萬分之一 |
| `0x05` | lux | 照度 |
| `0x06` | m/s | 風速 |
| `0x07` | mm | 毫米（降雨量） |

---

## 狀態代碼（1 位元組）

| 代碼 | 名稱 | 含義 |
|------|------|------|
| `0x00` | OK | 數值在正常範圍內 |
| `0x01` | WARN | 數值接近閾值 |
| `0x02` | ERROR | 數值超出閾值 / 感測器故障 |
| `0x03` | OFFLINE | 感測器無回應 |

---

## CRC 演算法

### CRC-8/SMBUS

```
多項式  : 0x07
初始值  : 0x00
輸入/輸出反射 : 否
XOR 輸出 : 0x00
```

對從 `CMD` 到 `BODY` 末尾的所有位元組計算（不包括 CRC 位元組本身）。

### CRC-16/CCITT-FALSE

```
多項式  : 0x1021
初始值  : 0xFFFF
輸入/輸出反射 : 否
XOR 輸出 : 0x0000
```

僅用於安全載荷的完整性驗證。

---

## Base62 編碼

數值以 Base62（字母表 `0-9A-Za-z`）編碼，在封包 Body 中提供緊湊的 ASCII 表示。

### 感測器值編碼

浮點感測器值在編碼前根據單位特定因子縮放：

| 單位 | 縮放因子 |
|------|---------|
| °C | × 100 |
| % | × 100 |
| hPa | × 10 |
| ppm | × 1 |

```
encodeSensorValue(25.6, "°C") → encode(2560) → "aN"   (2 字元)
decodeSensorValue("aN", "°C") → 25.6
```

### 時間戳編碼

毫秒時間戳被編碼為 8 字元的 Base64url 字串（標準 Base64，`+`→`-`，`/`→`_`）：

```
encodeTimestamp(1713600000000) → "AYy2MQAA"
```

---

## 範例：PING 封包

```
CMD=0x01  AID=0x01  TID=0x01  SEQ=0x00  LEN=0x00  CRC8=?

位元組：01 01 01 00 00
[01 01 01 00 00] 的 CRC8 = 0x04

完整封包：01 01 01 00 00 04
```

## 範例：DATA_FULL_SENSOR（TEMP = 25.6 °C，狀態正常）

```
Body："TE" + 0x01 + 0x00 + "aN" + 0x0A
     = 54 45 01 00 61 4E 0A   (7 位元組)

CMD=0x23  AID=0x01  TID=0x00  SEQ=0x01  LEN=0x07
標頭：23 01 00 01 07
Body：54 45 01 00 61 4E 0A
對 [23 01 00 01 07 54 45 01 00 61 4E 0A] 計算 CRC8

完整封包：23 01 00 01 07 54 45 01 00 61 4E 0A <crc>
```

---

## Geohash

裝置 GPS 位置可在裝置記錄中編碼為 [Geohash](https://en.wikipedia.org/wiki/Geohash) 字串。`GeohashDecoder` 類將其解碼為 `(lat, lng)` 座標對以供地圖顯示。

精度表：

| 長度 | 緯度誤差 | 經度誤差 |
|------|---------|---------|
| 4 | ±20 km | ±20 km |
| 5 | ±2.4 km | ±2.4 km |
| 6 | ±0.61 km | ±0.61 km |
| 7 | ±76 m | ±76 m |
| 8 | ±19 m | ±19 m |

---

*Java 實現詳見 `core/protocol/`。Flutter 參考實現詳見 `lib/protocol/codec/`。*

