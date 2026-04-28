# Gsyn-Java `res/xml` 目录开发者文档

> 路径：`app/src/main/res/xml/`  
> 本目录存放 Android 框架级别的 XML 配置文件，不属于界面布局、菜单或值资源，而是直接被 `AndroidManifest.xml` 或 Android 系统服务引用。  
> 当前包含 2 个配置文件：语言列表声明和网络安全配置。

---

## 目录

1. [文件清单](#1-文件清单)
2. [locale_config.xml — 支持语言声明](#2-locale_configxml--支持语言声明)
3. [network_security_config.xml — 网络安全配置](#3-network_security_configxml--网络安全配置)
4. [与 AndroidManifest.xml 的关联](#4-与-androidmanifestxml-的关联)
5. [扩展指引](#5-扩展指引)

---

## 1. 文件清单

| 文件名 | Android 框架属性 | 说明 |
|--------|----------------|------|
| `locale_config.xml` | `android:localeConfig` (API 33+) | 声明应用支持的完整语言列表 |
| `network_security_config.xml` | `android:networkSecurityConfig` | 网络明文传输及证书信任配置 |

---

## 2. locale_config.xml — 支持语言声明

**文件内容**：

```xml
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="zh" />
</locale-config>
```

### 用途

从 Android 13（API 33）起，系统语言选择器可以读取此文件，向用户展示应用支持的语言列表，让用户直接在**系统设置 → 语言 → 应用语言**中为本应用单独设置语言，而无需进入应用内部设置。

### 声明的语言

| 语言标签 | 语言 | 对应资源目录 |
|---------|------|------------|
| `en` | 英语 | `res/values/strings.xml`（默认） |
| `zh` | 中文（简体） | `res/values-zh/strings.xml` |

### 与 LocaleHelper 的关系

| 层面 | 机制 |
|------|------|
| **系统 UI**（Android 13+） | 读取 `locale_config.xml`，在设置 → 语言中展示可选语言 |
| **应用内部** | `LocaleHelper.applyAndSave(lang)` 调用 `AppCompatDelegate.setApplicationLocales()` |

两种途径最终都通过 AppCompat языка API 生效，互相兼容（系统选择器更改语言后，应用内 `LocaleHelper.current()` 也能正确反映）。

### AndroidManifest.xml 引用方式

```xml
<application
    android:localeConfig="@xml/locale_config"
    ... />
```

---

## 3. network_security_config.xml — 网络安全配置

**文件内容**：

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

### 用途

覆盖 Android 9（API 28）起的默认安全策略。默认策略会**阻止所有明文 HTTP/UDP 传输**，而本应用需要通过明文 UDP 或未加密的 MQTT（tcp://）与 OpenSynaptic 节点通信，因此必须显式允许。

### `cleartextTrafficPermitted="true"` 的影响范围

| 协议 | 是否受此配置影响 | 说明 |
|------|----------------|------|
| HTTP | 是 | 允许 `http://` WebView / HttpURLConnection |
| MQTT（tcp://） | 是 | 允许未加密的 MQTT 连接到任意 broker |
| UDP | 是（DatagramSocket） | 允许明文 UDP 数据报收发 |
| HTTPS | 否 | TLS 连接不受明文配置影响 |
| MQTT（ssl://） | 否 | TLS MQTT 连接不受影响 |

### 安全注意事项

> **⚠️ 生产环境风险提示**  
> `cleartextTrafficPermitted="true"` 允许所有明文流量，适用于**局域网 / 可信网络**中的 IoT 传感器通信场景。  
> 若应用需要对接公网或处理敏感数据，应：
> 1. 将 MQTT broker 配置为 SSL（`ssl://`），或
> 2. 限制允许明文通信的域名/IP 范围：
>
> ```xml
> <network-security-config>
>     <base-config cleartextTrafficPermitted="false" />
>     <domain-config cleartextTrafficPermitted="true">
>         <domain includeSubdomains="true">192.168.1.0</domain>
>     </domain-config>
> </network-security-config>
> ```

### AndroidManifest.xml 引用方式

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ... />
```

---

## 4. 与 AndroidManifest.xml 的关联

```xml
<application
    android:name=".GsynApp"
    android:label="@string/app_name"
    android:localeConfig="@xml/locale_config"
    android:networkSecurityConfig="@xml/network_security_config"
    android:theme="@style/Theme.GsynJava"
    ... >
```

两个 XML 配置均通过 `android:localeConfig` 和 `android:networkSecurityConfig` 属性在 `<application>` 节点声明，系统在应用启动时自动加载。

---

## 5. 扩展指引

### 新增支持语言

1. 在 `locale_config.xml` 中添加 `<locale android:name="{语言代码}" />`
2. 创建对应的 `res/values-{语言代码}/strings.xml` 翻译文件
3. 在 `LocaleHelper` 中添加语言常量
4. 在 `SettingsFragment` 中添加语言切换 UI

### 收紧网络安全策略（推荐）

将全局明文允许改为仅针对本地网络：

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <!-- 允许本地局域网通信 -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">localhost</domain>
        <domain includeSubdomains="true">192.168</domain>
        <domain includeSubdomains="true">10.0</domain>
        <domain includeSubdomains="true">172.16</domain>
    </domain-config>
</network-security-config>
```

### 添加自定义 CA 证书（用于私有 MQTT TLS）

如使用私有 CA 签发的 SSL 证书：

```xml
<network-security-config>
    <base-config>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="@raw/my_ca_cert" />  <!-- 将 .crt 放入 res/raw/ -->
        </trust-anchors>
    </base-config>
</network-security-config>
```

