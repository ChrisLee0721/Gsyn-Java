# Gsyn Java Mirror

这是 `opensynaptic_dashboard` 的 Android Studio 原生 Java 镜像子项目，路径为：

- `native_android_java/`

## 当前已镜像的核心内容

- 原生 Android Studio 工程结构（Gradle / app / manifest / resources）
- OpenSynaptic 协议常量、CRC、Base62、时间戳编码、包构建与包解析
- SQLite 数据库 schema：`devices / sensor_data / alerts / rules / operation_logs / users / dashboard_layout / pending_commands`
- 统一仓储层：设备、历史、告警、规则、操作日志
- 传输层：UDP 监听、MQTT 订阅、发送命令
- 规则引擎：阈值触发 -> 创建告警 / 发送命令 / 日志记录
- 主导航：`Dashboard / Devices / Alerts / Send / Settings`
- 二级镜像页面：`Map / History / Rules / System Health`

## 打开方式

直接在 Android Studio 中打开：

- `opensynaptic_dashboard/native_android_java`

这是当前最稳的原生入口；如果你在仓库根目录里仍遇到 Android Studio 顶部运行按钮没有直接指向 `app`，可以优先改为打开这个子目录。

## 命令行构建

```powershell
Set-Location "C:\Users\MaoJu\AndroidStudioProjects\opensynaptic_dashboard\native_android_java"
.\gradlew.bat projects
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

## 说明

这个子项目的目标是把原版 dashboard 的核心协议、数据结构、页面分区和交互流转迁移到 Java 原生工程中，便于你继续在 Android Studio 里做完整原生化开发与扩展。

