# 故障排查指南

> English version: [TROUBLESHOOTING.md](TROUBLESHOOTING.md)

本文档涵盖开发者和用户在使用 Gsyn Java 时遇到的最常见问题及其解决方法。

---

## 目录

1. [Google Maps 显示空白/白色页面](#1-google-maps-显示空白白色页面)
2. [Android Studio 运行按钮（▶）无法点击](#2-android-studio-运行按钮-无法点击)
3. [UDP 传输接收不到数据](#3-udp-传输接收不到数据)
4. [MQTT 传输无法连接](#4-mqtt-传输无法连接)
5. [CI/CD Keystore 错误](#5-cicd-keystore-错误)
6. [语言切换无效](#6-语言切换无效)
7. [切换主题后颜色没有更新](#7-切换主题后颜色没有更新)
8. [仪表盘卡片为空/全零](#8-仪表盘卡片为空全零)
9. [应用启动时崩溃](#9-应用启动时崩溃)
10. [Gradle 同步失败](#10-gradle-同步失败)

---

## 1. Google Maps 显示空白/白色页面

**症状：** 地图页面加载后只显示白色或灰色瓦片，没有任何地图内容。

**原因与解决方案：**

| 原因 | 解决方案 |
|------|---------|
| 未设置 `MAPS_API_KEY` | 在 `local.properties` 中添加 `MAPS_API_KEY=AIzaSy...` |
| API Key 的 SHA-1 指纹不匹配 | 运行 `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`，将输出的 SHA-1 添加到 Google Cloud Console 凭据中 |
| 未启用所需 API | 在 Google Cloud Console → API 和服务中启用 **Maps SDK for Android** |
| GCP 项目未开启结算 | Google Maps 需要绑定结算账号（有免费额度） |
| MapFragment 未使用 `commitNow()` | 参见 `ARCHITECTURE.md §Google Maps 集成` — 必须使用 `commitNow()` |
| Application ID 不匹配 | Debug 构建使用 `com.opensynaptic.gsynjava`，确保 API Key 限制与此完全匹配 |

**快速诊断：** 在 Logcat 中搜索 `MAPS_API_KEY` 或 `AuthFailure` 标签。

---

## 2. Android Studio 运行按钮（▶）无法点击

**症状：** 绿色三角形播放按钮不可点击。

**原因与解决方案：**

| 原因 | 解决方案 |
|------|---------|
| Gradle 同步未完成/失败 | 等待同步完成，或点击 **File → Sync Project with Gradle Files** |
| 未选择运行配置 | 点击 ▶ 旁边的下拉菜单，选择 `app` |
| 没有连接设备/模拟器 | 通过 **Device Manager** 启动 AVD，或连接开启 USB 调试的实体设备 |
| SDK 未安装 | 打开 **SDK Manager** 安装 Android SDK Platform 34 |
| 项目未被识别为 Android 项目 | 确保打开的是根目录（包含 `settings.gradle` 的目录），而非子目录 |

---

## 3. UDP 传输接收不到数据

**症状：** 设备正在发送数据包，但仪表盘没有任何内容显示。

**排查步骤：**

1. 检查设置 → UDP 已**启用**，且端口与发送方一致（默认 `9876`）
2. 在实体设备上，确认设备与发送方在同一网络
3. 在模拟器上，需要端口转发：
   ```bash
   adb forward udp:9876 udp:9876
   ```
4. 检查防火墙是否拦截了 9876 端口（Windows Defender、iptables 等）
5. 在 Logcat 中过滤 `TransportManager` 查看原始接收事件
6. 在应用的 **发送** 标签页发送测试数据包，观察回环是否正常

**Android 模拟器说明：** 模拟器的虚拟网络与主机隔离。从主机发送到模拟器时，目标地址使用 `10.0.2.2`。

---

## 4. MQTT 传输无法连接

**症状：** MQTT 开关已开启，但状态显示"已断开"。

**原因与解决方案：**

| 原因 | 解决方案 |
|------|---------|
| Broker 主机名不正确 | 若 DNS 无法解析，改用 IP 地址 |
| 端口被阻止 | 默认 MQTT 端口为 `1883`（非加密），检查 Broker 防火墙规则 |
| Broker 需要身份验证 | Gsyn Java 目前只支持匿名连接 |
| Topic 格式错误 | 多级通配符使用 `#`，例如 `opensynaptic/#` |
| Broker 仅支持 TLS/SSL | Paho 客户端未配置 TLS，请连接非 TLS 端点 |

---

## 5. CI/CD Keystore 错误

**症状：** GitHub Actions 失败，出现以下错误：

```
Keystore type: （空）
❌ 错误: Keystore 必须是 JKS 格式
```

或

```
error: alias not found
```

**根本原因：** 存储在 GitHub Secrets 中的 Keystore 必须是 **JKS 格式**，而非 PKCS12。

**生成正确的 JKS Keystore：**

```bash
keytool -genkeypair \
  -storetype JKS \
  -alias gsyn-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -keystore release.jks \
  -storepass 你的Store密码 \
  -keypass 你的Key密码 \
  -dname "CN=Gsyn Release, OU=Dev, O=OpenSynaptic, L=Unknown, ST=Unknown, C=CN"
```

**将现有 PKCS12 转换为 JKS：**

```bash
keytool -importkeystore \
  -srckeystore release.p12 \
  -srcstoretype PKCS12 \
  -destkeystore release.jks \
  -deststoretype JKS
```

**编码并上传到 GitHub Secrets：**

```bash
# macOS/Linux
base64 -i release.jks | pbcopy

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
```

所需 GitHub Secrets：

| Secret 名称 | 值 |
|------------|---|
| `KEYSTORE_BASE64` | Base64 编码的 `release.jks` |
| `KEY_ALIAS` | `gsyn-release`（或你的别名） |
| `KEY_PASSWORD` | Key 密码 |
| `STORE_PASSWORD` | Store 密码 |

---

## 6. 语言切换无效

**症状：** 在设置中更改语言后，界面仍显示原来的语言。

**原因与解决方案：**

| 原因 | 解决方案 |
|------|---------|
| Java/XML 中有硬编码字符串 | 所有用户可见文本必须使用 `getString(R.string.xxx)` 或 `@string/xxx`，不能硬编码 |
| `values-zh/strings.xml` 缺少对应 key | 在中文字符串文件中补充缺失的 key |
| Activity 没有重新创建 | `LocaleHelper.applyAndSave()` 会自动触发 Activity 重建，若未触发请检查 `LocaleHelper` 实现 |
| Android 13+ 缺少 `locale_config.xml` | 需要 `res/xml/locale_config.xml` 和 Manifest 中的 `android:localeConfig` |

---

## 7. 切换主题后颜色没有更新

**症状：** 在设置中选择新的主题预设后，界面颜色没有变化。

**解决方案：** 主题更改需要 **Activity 重新创建**。设置页的"保存"按钮会调用 `requireActivity().recreate()`。如果你正在修改主题相关代码，请确保 `AppThemeConfig.applyTheme(activity)` 在 `super.onCreate()` 之前调用——它必须是最先执行的操作。

---

## 8. 仪表盘卡片为空/全零

**症状：** 卡片可见，但所有数值显示 `0`、`--` 或 `N/A`。

**原因与解决方案：**

| 原因 | 解决方案 |
|------|---------|
| 未启用任何传输 | 进入设置 → 启用 UDP 或 MQTT，然后保存 |
| 还没有设备发送数据 | 在**发送**标签页发送测试数据包，或使用 OpenSynaptic 固件发送 |
| 数据库为空 | 这是首次启动时的正常状态——卡片会显示 `0`，收到第一个数据包后才会更新 |
| 卡片类型被隐藏 | 进入设置 → 打开对应卡片的开关 |

---

## 9. 应用启动时崩溃

**首先查看 Logcat。** 常见原因：

| 异常 | 原因 | 解决方案 |
|------|------|---------|
| `AppController` 中的 `NullPointerException` | 在 `Application.onCreate()` 之前调用了 `AppController.get()` | 确保只在 `Activity`/`Fragment`/`Service` 上下文中访问 `AppController` |
| `SQLiteException: no such table` | 数据库 Schema 变更但没有迁移脚本 | 开发阶段卸载重装应用；生产环境需在 `AppDatabaseHelper` 中添加迁移 |
| `Resources$NotFoundException` | 某语言缺少字符串资源 | 在 `values/strings.xml` 和 `values-zh/strings.xml` 中同时添加该 key |
| `IllegalStateException: commitNow()` | Fragment 事务在 `onSaveInstanceState` 后执行 | 确保地图 Fragment 在 `onViewCreated` 中添加，而不是更晚的时机 |

---

## 10. Gradle 同步失败

**常见错误：**

| 错误 | 解决方案 |
|------|---------|
| `Could not resolve com.google.android.gms:play-services-maps` | 检查网络连接；如果在代理环境下，在 `gradle.properties` 中配置代理 |
| `SDK location not found` | 创建 `local.properties`，添加 `sdk.dir=C:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk` |
| `Duplicate class kotlin.collections` | 更新 AGP 版本或添加 `resolutionStrategy { force ... }` |
| `minSdk(24) > device API` | 使用运行 Android 7.0 及以上版本的模拟器或实体设备 |
| 构建缓存损坏 | 运行 `./gradlew clean` 后重试 |

---

*如果你的问题未在此列出，请在 GitHub 提交 Issue，并附上完整的 Logcat 输出和 Gradle 错误信息。*

