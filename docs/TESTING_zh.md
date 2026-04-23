# 测试指南

> English version: [TESTING.md](TESTING.md)

本文档覆盖单元测试、手动端到端验证，以及在没有硬件设备的情况下验证完整 UDP/MQTT 数据链路的方法。

---

## 单元测试

所有单元测试位于：

```
app/src/test/java/com/opensynaptic/gsynjava/core/protocol/ProtocolCodecTest.java
```

协议层是**纯 Java**（无 Android 依赖），测试直接在 JVM 上运行，无需模拟器或真机。

### 通过 Gradle 运行

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

### 通过 Android Studio 运行

右键点击 `ProtocolCodecTest.java` → **Run 'ProtocolCodecTest'**

### 测试覆盖范围

| 测试名称 | 验证内容 |
|---------|---------|
| `base62_roundtrip` | `Base62Codec.encode` / `decode` 对边界值（0、1、61、62、大数、负数）的往返正确性 |
| `timestamp_roundtrip` | `encodeTimestamp` / `decodeTimestamp` 精确保留 UNIX 时间戳 |
| `packet_build_and_decode` | `PacketBuilder.buildSensorPacket` 构建的帧能被 `PacketDecoder.decode` 正确解析，CRC-8 和 CRC-16 均通过 |
| `body_parse_sensor` | `BodyParser.parseText` 从原始 body 字符串中正确提取 `TEMP` 和 `HUM` 传感器读数 |
| `diff_engine_full_heart_roundtrip` | `DATA_FULL` 帧存为模板后，空 body 的 `DATA_HEART` 能重建原始读数 |
| `diff_engine_clear` | `DiffEngine.clear()` 清除所有模板后，HEART 返回 `null` |
| `protocol_constants_default_unit` | `ProtocolConstants.defaultUnitFor()` 对 `TEMP`、`HUM`、`PRES` 返回正确单位，对未知 ID 返回 `""` |
| `os_cmd_is_secure` | `OsCmd.isSecureCmd()` 正确识别安全与非安全命令字节 |

---

## 端到端验证（真机 — 推荐）

### 前提条件

- Android 手机与 PC 在**同一 Wi-Fi 网络**
- App 已安装并在 `Settings` 中启用 UDP（端口 `9876`）
- PC 上已安装 Python 3

### 第一步 — 找到手机 IP

手机上：**设置 → Wi-Fi → 点击当前网络 → IP 地址**

### 第二步 — 从 PC 发送测试包

保存为 `send_test.py` 并运行：

```python
import socket

def crc8(data: bytes) -> int:
    crc = 0
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = ((crc << 1) ^ 0x07) & 0xFF if crc & 0x80 else (crc << 1) & 0xFF
    return crc

# DATA_FULL 包 — AID=1, TID=0, SEQ=1
body = b"1.U.AABlqg4|TEMP>U.Cel:44D|HUM>U.%:52h|"
header = bytes([0x20, 0x01, 0x00, 0x01, len(body)])
frame = header + body
frame += bytes([crc8(frame)])

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.sendto(frame, ("192.168.x.x", 9876))  # ← 替换为手机的 IP 地址
print(f"已发送 {len(frame)} 字节")
```

```powershell
python send_test.py
```

### 第三步 — 在设备上验证

1. **Dashboard** → Readings 卡片应显示 `TEMP` 和 `HUM` 值
2. **Devices** 标签页 → AID=1 的设备应显示为 Online
3. **Health** 标签页 → `totalMessages` 计数器应增加

---

## 端到端验证（模拟器）

模拟器运行在虚拟网络上，需要端口转发。

### 第一步 — 转发 UDP 端口

```powershell
adb forward udp:9876 udp:9876
```

> 每次重启模拟器后需重新运行此命令。

### 第二步 — 发送到 localhost

将 Python 脚本中的目标地址改为：

```python
sock.sendto(frame, ("127.0.0.1", 9876))
```

### 第三步 — 验证方式与真机相同

---

## MQTT 端到端验证

### 启动本地 Broker（Mosquitto）

```powershell
# 下载：https://mosquitto.org/download/
mosquitto -v
```

### 配置 App

`Settings` → MQTT Broker 地址：
- **真机**：`tcp://<PC局域网IP>:1883`
- **模拟器**：`tcp://10.0.2.2:1883`

### 发布测试消息

使用 [MQTT Explorer](https://mqtt-explorer.com/) 向 topic `gsyn/sensor/1` 发布 Python 脚本生成的原始字节。

---

## Logcat 验证

在 Android Studio **Logcat** 中按 tag 过滤，追踪数据链路各环节：

| 过滤 tag | 显示内容 |
|---------|---------|
| `TransportManager` | 收到的原始字节数、发送确认 |
| `PacketDecoder` | CRC 校验结果、包头字段 |
| `DiffEngine` | 模板存储 / 重建 |
| `AppRepository` | SQL 插入行数 |
| `RulesEngine` | 规则评估结果 |
| `DashboardFragment` | 每次 refresh 时的 Snapshot 字段值 |

Logcat 搜索栏过滤字符串示例：

```
tag:PacketDecoder | tag:DiffEngine | tag:AppRepository
```

