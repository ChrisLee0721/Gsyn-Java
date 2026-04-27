# 常见问题解答 (FAQ)

> English version: [FAQ.md](FAQ.md)

关于 Gsyn Java 设计决策、功能特性和限制的常见问题解答。

---

## 通用问题

### Gsyn Java 是什么？

Gsyn Java 是一个用于 **OpenSynaptic** 物联网协议的 Android 遥测控制台。它通过 UDP 或 MQTT 接收来自硬件节点的传感器数据，在可配置的仪表盘上可视化数据，并允许你定义基于阈值的告警规则。

它是原始 Flutter 版 Gsyn 应用的 Java 移植版本，专为 Android/Java 学习者设计，也作为教学参考代码库使用。

---

### 这是生产就绪的产品吗？

功能上是可用的，已在实验室和家庭自动化环境中实际部署使用。但是：
- UDP 传输**未加密**——不适合通过公网传输敏感数据
- 无用户认证——任何持有该应用的人都可以查看所有数据
- 数据库无自动备份——卸载应用会导致数据丢失

如需生产部署，请考虑添加 MQTT TLS 并在 VPN 后面运行。

---

### 没有 OpenSynaptic 硬件也能使用该应用吗？

可以。你可以：
- 使用**发送**标签页向自己发送测试数据包（回环地址 `127.0.0.1:9876`）
- 使用 Python/Node 脚本通过 UDP 模拟设备
- 连接到任何发布 OpenSynaptic 格式数据的 MQTT Broker

数据包格式请参见 [PROTOCOL_zh.md](PROTOCOL_zh.md)，用于构建模拟器。

---

## 架构决策

### 为什么不使用 Room（ORM）？

Room 增加了编译时注解处理，延长了构建时间。本项目的数据库结构很简单（约 5 张表、约 200 行的 Helper 类），直接使用原始 SQLite 已经足够清晰。使用 Room 还需要引入 Dagger/ViewModel/LiveData，而这些被刻意避免，以减小学习负担。

如果你大幅扩展这个项目，Room + ViewModel 是合理的升级路径。

---

### 为什么不使用 Retrofit 或 OkHttp？

应用通过 **UDP 和 MQTT** 通信——而非 HTTP。核心流程中没有 REST API 调用。

唯一类似 HTTP 的请求是 Google Maps 瓦片加载，完全由 Maps SDK 处理。

---

### 为什么不用 Dagger/Hilt 做依赖注入？

`AppController` 中手动的单例模式是显式且易于追踪的——你可以通过阅读约 50 行代码来理解整个依赖关系图。Hilt/Dagger 引入了大量复杂性（注解处理、组件作用域、生成代码），在这个规模下不值得。

对于拥有更多页面和异步操作的大型项目，Hilt 会更合适。

---

### 为什么地图 Fragment 使用 `commitNow()` 而不是 `commit()`？

`commit()` 会将事务调度为在下一帧异步执行。如果紧接着调用 `getMapAsync()`，Fragment 可能尚未被附加，导致地图永远无法初始化（白屏）。

`commitNow()` 会在返回前同步执行事务，确保调用 `getMapAsync()` 时 Fragment 已经附加完毕。

---

### 为什么协议层在 `core/protocol/` 中且没有 Android 依赖？

这是出于**可测试性**的刻意设计。纯 Java 类无需设备、模拟器或 Robolectric 就可以用标准 JUnit 进行单元测试。`PacketDecoder`、`PacketBuilder`、`DiffEngine` 和 `CRC8` 类均没有 Android 依赖。

---

### 为什么传感器数据只保留 7 天？

为了防止长期运行的部署中数据库无限增长。7 天覆盖了典型的调试和监控时间窗口。保留周期在一处统一定义（`AppRepository.insertSensorDataBatch`），可以直接在那里修改。

---

## 功能特性

### 可以添加新的传感器类型吗？

可以。传感器类型通过 2 字节 ASCII 码标识（例如 `TE` 表示温度）。添加新类型步骤：

1. 在 [PROTOCOL_zh.md](PROTOCOL_zh.md) 的传感器 ID 表中添加新代码（文档）
2. 在 `values/strings.xml` 和 `values-zh/strings.xml` 中添加显示名称字符串
3. 在 `UiFormatters.formatSensorLabel()` 中添加对应的 `case` 显示标签
4. 数据会自动流通——无需其他改动

---

### 可以使用自定义地图瓦片服务器代替 Google Maps 吗？

可以——设置中的**瓦片 URL** 字段接受任何带有 `{z}/{x}/{y}` 占位符的 XYZ 瓦片服务器 URL。不过，地图渲染引擎是 Google Maps SDK（Geohash 图钉叠加层需要它）。瓦片 URL 字段仅在你实现自定义瓦片提供者时控制底图层；默认情况下 Google Maps 使用自己的瓦片。

如果需要完全脱离 Google Maps 使用 OpenStreetMap，请将 `MapMirrorFragment` 替换为 OSMDroid 或 Mapbox 实现。

---

### 应用能在后台运行吗？

目前不能。UDP/MQTT 传输运行在绑定到 `TransportManager` 的前台线程上。如需后台运行，需要将传输逻辑移入 `Service`（或使用 `WorkManager` 处理周期性任务），并申请 `FOREGROUND_SERVICE` 权限。

---

### 数据库为空时（还没有设备）会显示什么？

仪表盘会显示所有已配置的卡片，值为零或空。这是有意为之——无论数据状态如何，UI 始终渲染。收到第一个数据包后，值会自动更新。

---

### 可以导出应用中的数据吗？

目前没有内置的导出功能。SQLite 数据库文件位于：
```
/data/data/com.opensynaptic.gsynjava/databases/gsyn.db
```

在已 Root 的设备上或通过 ADB 备份，你可以提取此文件并用任何 SQLite 浏览器打开。

---

## 构建与 CI

### 为什么 CI 要求 JKS 格式而不是 PKCS12？

验证脚本使用 `keytool -list -v` 并解析 `Keystore type:` 字段。Android 的 `apksigner` 和 Gradle 签名块都支持两种格式，但 CI 验证脚本被编写为强制使用 JKS，以与旧版工具链期望保持一致。如何生成正确的 JKS Keystore，请参见 [CI_CD_zh.md](CI_CD_zh.md)。

---

### 可以不通过 CI 在本地构建 Release APK 吗？

可以。在 `local.properties` 中添加：

```properties
android.injected.signing.store.file=/绝对路径/release.jks
android.injected.signing.store.password=你的Store密码
android.injected.signing.key.alias=gsyn-release
android.injected.signing.key.password=你的Key密码
```

然后运行：
```bash
./gradlew assembleRelease
```

签名后的 APK 位于 `app/build/outputs/apk/release/app-release.apk`。

---

*还有其他问题？欢迎在 GitHub 提交 Discussion 或 Issue。*

