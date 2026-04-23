# 故障排查指南

> English version: [TROUBLESHOOTING.md](TROUBLESHOOTING.md)

本文档汇总 Gsyn Java 开发、运行和集成过程中最常见的问题及解决方案。

---

## 构建与配置

### Gradle 同步失败 — "SDK location not found"

`local.properties` 文件缺失或路径错误。

```properties
# local.properties（Windows）
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=YOUR_KEY_HERE
```

---

### 地图页面显示空白灰色

API Key 缺失，或 SHA-1 指纹限制不匹配。

1. 确认 `local.properties` 中已设置 `MAPS_API_KEY`
2. 在 [Google Cloud Console](https://console.cloud.google.com) → API 和服务 → 凭据中，检查 Android 应用限制：
   - 包名：`com.opensynaptic.gsynjava`
   - SHA-1：与**调试**密钥库匹配
3. 获取调试 SHA-1：
   ```powershell
   keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" `
           -alias androiddebugkey -storepass android -keypass android
   ```

---

### 应用启动即崩溃

查看 **Logcat** 中的实际异常。最常见原因是在 `Application.onCreate()` 完成之前调用了 `AppController.get()`。

确保 `AppController.get(context)` 只在 Activity 或 Fragment 中调用，**不要**在静态初始化块中调用。

---

## 传输层 — UDP

### 启用 UDP 后 Dashboard 无数据

按顺序排查：

1. **端口不匹配** — Settings 中的 UDP 端口必须与设备发送的目标端口一致
2. **目标 IP 错误** — IoT 设备必须向 **Android 设备的 IP** 发送，而非 `127.0.0.1`
3. **防火墙 / 模拟器** — 模拟器需先运行 `adb forward udp:9876 udp:9876`
4. **CRC 校验失败** — 在 `PacketDecoder.decode()` 中添加日志确认：
   ```java
   Log.d("PacketDecoder", "raw=" + raw.length + "B  crc8Ok=" + result.crc8Ok);
   ```
5. **CRC 算法不匹配** — Gsyn 使用 CRC-8/SMBUS（多项式 `0x07`，初始值 `0x00`），确认发送端使用相同算法

---

### sendUdp() 抛出"Network on main thread"异常

在 UI 线程调用了网络操作，改为在后台线程执行：

```java
new Thread(() -> tm.sendUdp(bytes, host, port)).start();
```

---

## 传输层 — MQTT

### MQTT 连接立即失败

1. 确认 Broker URL 格式：`tcp://host:1883` 或 `ssl://host:8883`
2. 模拟器连接宿主机 Broker 使用：`tcp://10.0.2.2:1883`
3. Eclipse Paho v3 **不支持** MQTT 5 协议的 Broker
4. 使用 TLS 时，Broker 证书必须受 Android 信任存储信任

---

### Broker 收到消息但 App 不显示

App 订阅的是 `gsyn/#`。确认发布端的 topic 以 `gsyn/` 开头（如 `gsyn/sensor/1`），且 Broker ACL 允许该订阅。

---

## 规则引擎

### 规则触发一次后不再触发

规则的 `cooldownMs`（默认 60 000 ms = 1 分钟）未到期。等待 60 秒后再次触发，或在规则编辑页面减小冷却时间。

---

### 规则已启用但从不触发

1. 规则中的 `sensorId` 必须与数据包中的传感器 ID **完全一致**（大小写不敏感，但不允许多余空格）
2. 在 `RulesEngine.evaluate()` 中添加日志排查：
   ```java
   Log.d("RulesEngine", "rule=" + rule.sensorId + " reading=" + reading.sensorId
         + " value=" + reading.value + " threshold=" + rule.threshold);
   ```

---

## Dashboard

### CUSTOM_SENSOR 卡片显示"—"

1. 卡片设置中的传感器 ID 必须与设备发送的完全一致
2. 在 `DashboardFragment.refresh()` 中添加日志检查快照键：
   ```java
   Log.d("SNAP", "keys=" + snap.latestBySensorId.keySet());
   ```
3. 如果键存在但值为 `0.0`，检查 `BodyParser` 是否正确解析了单位字段

---

### 收到数据包后 Dashboard 不刷新

`onMessage()` 在后台 UDP 线程调用，UI 更新必须切回主线程：

```java
@Override
public void onMessage(Models.DeviceMessage msg) {
    if (getActivity() != null) {
        getActivity().runOnUiThread(this::refresh);
    }
}
```

如果 `getActivity()` 为 null，说明 Fragment 已 detach，可以安全忽略。

---

## 地图

### 设备标记不显示

1. 设备必须发送 `LAT` 和 `LNG` 传感器 ID，**或** `GEO`（Geohash）传感器 ID
2. 确认 `AppRepository.updateDeviceGeo()` 在处理数据包时被调用
3. 通过 **Database Inspector** 确认 `devices` 表中 `lat`/`lng` 字段有值：
   Android Studio → View → Tool Windows → App Inspection → Database Inspector

---

## 数据库

### 如何实时查看 SQLite 数据

**Android Studio → View → Tool Windows → App Inspection → Database Inspector**

选择 `gsyn_db` 即可浏览并实时查询所有表，无需任何 adb 命令。

---

### sensor_data 表意外增长

`insertSensorDataBatch()` 会自动删除 7 天前的数据，正常情况下不会无限增长。
如果仍在增长，确认没有绕过 `AppRepository` 直接调用插入操作。

---

## Release / CI

### GitHub Actions："Tag number over 30 is not supported"

Keystore 使用了 PKCS12 格式，Android Gradle Plugin 需要 **JKS** 格式。
使用脚本重新生成：

```powershell
.\scripts\generate-keystore.ps1
```

详细步骤参见 [RELEASE.md](../RELEASE.md)。

---

### Release APK 安装成功但启动崩溃

Release 构建启用了 ProGuard，可能裁剪了必要类。在 `app/proguard-rules.pro` 中添加：

```proguard
-keep class com.opensynaptic.gsynjava.core.protocol.** { *; }
-keep class com.opensynaptic.gsynjava.data.Models { *; }
```

