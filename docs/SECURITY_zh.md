# 安全指南

> English version: [SECURITY.md](SECURITY.md)

本文档描述了 Gsyn Java 在开发、构建和部署过程中的安全注意事项。

---

## API Key 管理

### Google Maps API Key

Maps API Key 在**构建时**通过 `local.properties` 和 `app/build.gradle` 中的 `manifestPlaceholders` 注入，**永远不**硬编码在源代码中。

```properties
# local.properties（不提交到版本控制）
MAPS_API_KEY=AIzaSy...yourkey...
```

`app/build.gradle` 读取方式：

```groovy
android {
    defaultConfig {
        manifestPlaceholders = [mapsApiKey: project.findProperty("MAPS_API_KEY") ?: ""]
    }
}
```

**规则：**
- `local.properties` 已加入 `.gitignore`——绝对不要提交它
- CI/CD 中，将 Key 存储在 GitHub Secrets 的 `MAPS_API_KEY` 中，通过 `-P` Gradle 属性传入
- 在 Google Cloud Console 中限制你的 API Key，仅允许 `com.opensynaptic.gsynjava` 包名及你的发布 SHA-1 指纹使用

### 在 Google Cloud Console 限制 API Key

1. 打开 [Google Cloud Console → 凭据](https://console.cloud.google.com/apis/credentials)
2. 点击你的 Maps API Key → **编辑**
3. 在**应用程序限制**下选择 **Android 应用**
4. 添加条目：
   - 软件包名称：`com.opensynaptic.gsynjava`
   - SHA-1 证书指纹：*（你的 Debug 或 Release 指纹）*
5. 在 **API 限制**下，仅限制为此应用实际使用的 API

**获取 Debug SHA-1：**
```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

**获取 Release SHA-1：**
```bash
keytool -list -v \
  -keystore release.jks \
  -alias gsyn-release \
  -storepass 你的Store密码
```

---

## Keystore 管理

### 生成发布用 Keystore

```bash
keytool -genkeypair \
  -storetype JKS \
  -alias gsyn-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -keystore release.jks
```

> ⚠️ **Keystore 必须是 JKS 格式。** PKCS12 格式的 Keystore 会导致 CI 验证失败。

### 需要保密的内容

| 文件 / 值 | 存储位置 | 绝对不要 |
|----------|---------|---------|
| `release.jks` | 加密存储；CI 使用 Base64 编码 | 提交到 Git |
| Store 密码 | GitHub Secret `STORE_PASSWORD` | 硬编码在 `build.gradle` |
| Key 密码 | GitHub Secret `KEY_PASSWORD` | 输出到 CI 日志 |
| `local.properties` | 仅本地磁盘 | 提交到 Git |
| `MAPS_API_KEY` | `local.properties` 或 CI Secret | 硬编码在源代码中 |

### Keystore 备份

请在**至少两个不同位置**保存 `release.jks` 的加密备份。一旦丢失 Keystore，你将永远无法为同一个 Play Store 应用发布更新。

---

## local.properties——黄金法则

```
local.properties 已加入 .gitignore——绝对不能提交。
```

此文件可能包含：
- `sdk.dir`——Android SDK 路径（机器相关，不敏感）
- `MAPS_API_KEY`——敏感
- 本地 Release 构建的签名凭据——敏感

如果你不小心提交了 `local.properties`，请立即更换 API Key 和密码。

---

## 网络安全

### UDP

Gsyn 协议使用**未加密的 UDP**。这适用于：
- 局域网部署
- 实验室/开发环境
- 隔离的物联网网络

**不适用于：**
- 通过公网传输敏感传感器数据
- 没有 VPN 隧道保护的生产环境

### MQTT

本项目的 Paho 客户端连接时不启用 TLS。如需生产部署，请为你的 MQTT Broker 配置 TLS，并修改 `TransportManager`，在 `MqttConnectOptions` 中使用 SSL socket factory。

---

## 设备上存储的数据

Gsyn Java 在设备上的 SQLite 数据库中存储以下数据：

| 表名 | 内容 | 保留时间 |
|------|------|---------|
| `devices` | 设备 ID、标签、最后在线时间、在线状态 | 永久保留 |
| `sensor_data` | 带时间戳的传感器读数 | 7 天（自动清除） |
| `alerts` | 告警记录 | 永久保留（手动清除） |
| `rules` | 规则定义 | 永久保留 |
| `operations_log` | 规则动作日志 | 永久保留 |

所有数据存储在应用的私有 SQLite 数据库中：
```
/data/data/com.opensynaptic.gsynjava/databases/gsyn.db
```

这对其他应用不可访问（标准 Android 沙箱机制）。

---

## 上报安全问题

如果你发现了安全漏洞，请**不要公开提交 GitHub Issue**。请直接联系维护者，或使用 GitHub 的私有安全公告功能：

**GitHub → Security → Advisories → Report a vulnerability**

---

*CI/CD 安全配置，请参见 [CI_CD_zh.md](CI_CD_zh.md)。*

