# Gsyn Java Mirror

这是 [OpenSynaptic/Gsyn](https://github.com/OpenSynaptic/Gsyn)（Flutter 多平台 dashboard）的 Android Studio 原生 Java 镜像项目。

## 已镜像的核心内容

### 协议层（`core/protocol/`）
| Java 文件 | 对应 Flutter 源文件 | 说明 |
|---|---|---|
| `OsCmd.java` | `lib/protocol/codec/commands.dart` | 命令字节常量 + isDataCmd / isSecureCmd / normalizeDataCmd |
| `OsCrc.java` | `lib/protocol/codec/crc.dart` | CRC-8/SMBUS 和 CRC-16/CCITT-FALSE |
| `Base62Codec.java` | `lib/protocol/codec/base62.dart` | Base62 编解码 + 时间戳 Base64url + 传感器值缩放 |
| `BodyParser.java` | `lib/protocol/codec/body_parser.dart` | FULL 包体解析 → SensorReading 列表 |
| `PacketDecoder.java` | `lib/protocol/codec/packet_decoder.dart` | 线路包头解析 + CRC 校验 |
| `PacketBuilder.java` | `lib/protocol/codec/packet_builder.dart` | FULL/PING/PONG/ID_REQUEST/TIME_REQUEST/RAW_HEX 帧构建 |
| `DiffEngine.java` | `lib/protocol/codec/diff_engine.dart` | DIFF/HEART 模板引擎（学习/重建/差分更新） |
| `ProtocolConstants.java` | `lib/core/protocol_constants.dart` | 传感器 ID、单位、状态码常量及 defaultUnitFor() |

### 数据层（`data/`）
| Java 文件 | 对应 Flutter 源文件 | 说明 |
|---|---|---|
| `Models.java` | `lib/data/models/models.dart` + `lib/protocol/models/` | Device / SensorData / AlertItem / Rule / OperationLog / AppUser / SensorReading / DeviceMessage / PacketMeta |
| `AppDatabaseHelper.java` | `lib/data/database/database_helper.dart` | SQLite schema v1：devices / sensor_data / alerts / rules / operation_logs / users / dashboard_layout / pending_commands |
| `AppRepository.java` | `lib/data/repositories/repositories.dart` | 全部 CRUD：设备 upsert、传感器批量写入、告警 ACK、规则 CRUD、操作日志、CSV 导出、数据裁剪 |

### 传输层（`transport/`）
| Java 文件 | 对应 Flutter 源文件 | 说明 |
|---|---|---|
| `TransportManager.java` | `lib/protocol/transport/transport_manager.dart` + udp + mqtt | UDP 监听/发送、MQTT 订阅/发布、DiffEngine 集成（FULL/DIFF/HEART 自动处理）、统计广播 |

### 规则引擎（`rules/`）
| Java 文件 | 对应 Flutter 源文件 | 说明 |
|---|---|---|
| `RulesEngine.java` | `lib/rules/rules_engine.dart` | 阈值评估 → create_alert / send_command / log_only；冷却期管理 |

### 应用控制器
| Java 文件 | 说明 |
|---|---|
| `AppController.java` | 单例协调器：Repository + TransportManager + RulesEngine + 消息流转 |

### UI 层
| 页面/组件 | 对应 Flutter 页面 | 说明 |
|---|---|---|
| `MainActivity.java` | `app.dart` AppShell | BottomNav 5 标签导航 |
| `DashboardFragment.java` | `features/dashboard/dashboard_page.dart` | KPI 卡片、Mini 折线图、水位/湿度进度、告警/操作摘要、传输状态 |
| `DevicesFragment.java` | `features/devices/devices_page.dart` | 设备列表、搜索过滤、详情弹窗 |
| `AlertsFragment.java` | `features/alerts/alerts_page.dart` | 告警列表、严重级过滤、一键确认 |
| `SendFragment.java` | `features/send/send_page.dart` | 命令构建器（PING/PONG/ID_REQUEST/TIME_REQUEST/ID_ASSIGN/DATA_FULL_SENSOR/RAW_HEX）、发送日志 |
| `SettingsFragment.java` | `features/settings/settings_page.dart` | UDP/MQTT 连接配置、瓦片 URL、运行时统计 |
| `MapMirrorFragment.java` | `features/map/map_page.dart` | 设备坐标列表镜像（占位，后续可接 OSM 地图） |
| `HistoryMirrorFragment.java` | `features/history/history_page.dart` | 24h 传感器历史、CSV 导出 |
| `RulesMirrorFragment.java` | `features/rules_config/rules_config_page.dart` | 规则 CRUD、启停切换、操作日志列表 |
| `HealthMirrorFragment.java` | `features/system_health/system_health_page.dart` | 传输状态、设备在线率、DB 大小、7天数据裁剪 |
| `MiniTrendChartView.java` | `widgets/realtime_line_chart.dart` | 原生 Canvas 折线图（带填充、网格、端点高亮） |
| `CardRowAdapter.java` | 通用列表行 | MaterialCardView + 角标徽章 |
| `UiFormatters.java` | 通用格式化 | 相对时间、日期、传感器摘要、数字裁剪 |

## 单元测试（8 项全部通过）
```
✅ base62_roundtrip
✅ timestamp_roundtrip
✅ packet_build_and_decode
✅ body_parse_sensor
✅ diff_engine_full_heart_roundtrip
✅ diff_engine_clear
✅ protocol_constants_default_unit
✅ os_cmd_is_secure
```

## 打开方式

直接在 Android Studio 中打开本目录：
```
C:\Users\26427\StudioProjects\Gsyn-Java
```

## 命令行构建

```powershell
Set-Location "C:\Users\26427\StudioProjects\Gsyn-Java"
$env:JAVA_HOME = "C:\Users\26427\.jdks\openjdk-25.0.2"
.\gradlew.bat :app:testDebugUnitTest   # 运行单元测试
.\gradlew.bat :app:assembleDebug       # 构建 Debug APK
```

## 发布版本

本项目配置了自动化 CI/CD 流程。推送 `v*` 格式的 tag（如 `v1.0.0`）即可自动构建签名 APK 并创建 GitHub Release。

**详细发布指南请参考：[RELEASE.md](./RELEASE.md)**

快速发布步骤：
```bash
# 1. 更新 app/build.gradle 中的 versionCode 和 versionName
# 2. 提交代码
git add app/build.gradle
git commit -m "chore: bump version to 1.1.0"
git push origin main

# 3. 创建并推送 tag
git tag v1.1.0
git push origin v1.1.0
```

首次发布前需配置 GitHub Secrets（参见 [RELEASE.md](./RELEASE.md)）：
- `KEYSTORE_BASE64` - JKS 格式的 keystore 文件（Base64 编码）
- `KEYSTORE_PASSWORD` - Keystore 密码
- `KEY_ALIAS` - 密钥别名
- `KEY_PASSWORD` - 密钥密码

## 与 Flutter 源的主要差异

| 方面 | Flutter 源 | Java 镜像 |
|---|---|---|
| DI / 状态管理 | Riverpod Provider | 单例（AppController / AppRepository / TransportManager） |
| 图表 | fl_chart（矢量 Widget） | 原生 Canvas `MiniTrendChartView` |
| 地图 | flutter_map + OSM | 坐标列表占位（可后续集成 osmdroid） |
| MQTT | mqtt5_client | Eclipse Paho MQTT v3 |
| 多语言 | AppStrings / LocaleProvider | 中文硬编码字符串（可扩展至 strings.xml） |
