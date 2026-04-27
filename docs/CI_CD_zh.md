# CI/CD 指南

> English version: [CI_CD.md](CI_CD.md)

本文档介绍 Gsyn Java 的 GitHub Actions CI/CD 流水线——它的工作原理、配置方法以及如何排查失败。

---

## 概述

流水线在每次向 `main` 推送和每个 Pull Request 时运行。当推送版本标签（如 `v1.3.0`）时，还会额外构建**签名的 Release APK** 并创建 GitHub Release。

```
推送到 main / PR
    └── build-and-test 任务
          ├── 检出代码
          ├── 设置 JDK 17
          ├── 恢复 Gradle 缓存
          ├── ./gradlew assembleDebug
          └── ./gradlew test

推送标签 vX.Y.Z
    └── build-and-test 任务（同上）
    └── release 任务（依赖 build-and-test）
          ├── 从 KEYSTORE_BASE64 Secret 解码 Keystore
          ├── 验证 Keystore 格式（JKS）和别名
          ├── ./gradlew assembleRelease -Pandroid.injected.signing.*
          └── 将 APK 上传到 GitHub Release
```

---

## 所需 GitHub Secrets

进入 **仓库 → Settings → Secrets and variables → Actions → New repository secret**，添加以下 Secret：

| Secret 名称 | 描述 |
|------------|------|
| `KEYSTORE_BASE64` | Base64 编码的 `release.jks`（JKS 格式） |
| `KEY_ALIAS` | Keystore 中的 Key 别名（如 `gsyn-release`） |
| `KEY_PASSWORD` | 私钥密码 |
| `STORE_PASSWORD` | Keystore Store 密码 |
| `MAPS_API_KEY` | Google Maps API Key（构建时注入） |

### 将 Keystore 编码为 Secret

```bash
# macOS/Linux
base64 -i release.jks | tr -d '\n' | pbcopy

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
```

将剪贴板内容粘贴为 `KEYSTORE_BASE64` 的值。

---

## Workflow 文件位置

```
.github/workflows/android.yml
```

关键步骤：

```yaml
- name: Decode keystore
  run: |
    echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 --decode > /home/runner/work/_temp/release.jks

- name: Validate keystore
  run: |
    KEYSTORE_TYPE=$(keytool -J-Duser.language=en -list -v \
      -keystore /home/runner/work/_temp/release.jks \
      -storepass "${{ secrets.STORE_PASSWORD }}" 2>&1 \
      | grep "Keystore type:" | awk '{print $3}')
    if [ "$KEYSTORE_TYPE" != "JKS" ]; then
      echo "❌ 错误: Keystore 必须是 JKS 格式，当前是: $KEYSTORE_TYPE"
      exit 1
    fi

- name: Build release APK
  run: |
    ./gradlew assembleRelease \
      -Pandroid.injected.signing.store.file=/home/runner/work/_temp/release.jks \
      -Pandroid.injected.signing.store.password=${{ secrets.STORE_PASSWORD }} \
      -Pandroid.injected.signing.key.alias=${{ secrets.KEY_ALIAS }} \
      -Pandroid.injected.signing.key.password=${{ secrets.KEY_PASSWORD }}
```

---

## 触发 Release

1. 更新 `app/build.gradle` 中的 `versionCode` 和 `versionName`
2. 提交并推送：
   ```bash
   git add app/build.gradle
   git commit -m "chore: bump version to 1.3.0"
   git push origin main
   ```
3. 打标签并推送：
   ```bash
   git tag -a v1.3.0 -m "v1.3.0: 描述更新内容"
   git push origin v1.3.0
   ```
4. GitHub Actions 检测到标签，运行完整流水线，并将 APK 附加到 GitHub Release。

---

## 排查 CI 失败

### Keystore 相关错误

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| `Keystore type: (空)` | Store 密码错误 | 确认 `STORE_PASSWORD` Secret 与 Keystore 匹配 |
| `Keystore must be JKS format` | Keystore 是 PKCS12 格式 | 使用 `-storetype JKS` 重新生成 |
| `alias not found` | `KEY_ALIAS` Secret 错误 | 运行 `keytool -list -keystore release.jks` 查看可用别名 |
| `base64: invalid input` | `KEYSTORE_BASE64` 值损坏 | 重新编码 Keystore 文件（Base64 字符串中不能有换行符） |

### 构建失败

- **`SDK not found`** — CI Runner 会自动使用 `ANDROID_HOME`。如果失败，检查 `actions/setup-java` 和 `actions/setup-android` 的版本。
- **`MAPS_API_KEY not set`** — 添加 `MAPS_API_KEY` GitHub Secret。
- **`Gradle daemon failed to start`** — 在 CI 的 Gradle 命令中添加 `-Dorg.gradle.daemon=false`。

### 查看日志

每个任务步骤都会写入 GitHub Actions 日志。点击失败的步骤展开完整输出。对于 Gradle 失败，查找 `BUILD FAILED` 和其前面的 `> Task :app:xxx FAILED` 行。

---

## Gradle 缓存

Workflow 使用 `actions/cache` 缓存 Gradle Wrapper 和依赖项，缓存键基于 `gradle/wrapper/gradle-wrapper.properties` 和 `app/build.gradle` 的哈希值。首次运行后，通常可将 CI 时间从约 5 分钟缩短到约 2 分钟。

如果缓存导致问题（例如在重大依赖升级后），手动使其失效：

**Actions → Caches → 删除对应的缓存条目**

---

## 环境变量汇总

| 变量 | 设置方 | 用途 |
|------|-------|------|
| `JAVA_HOME` | `actions/setup-java` | Gradle |
| `ANDROID_HOME` | Runner 镜像 | Gradle |
| `MAPS_API_KEY` | GitHub Secret | `manifestPlaceholders` |
| 签名相关参数 | GitHub Secrets | `-P` Gradle 属性 |

---

*Keystore 安全详情，请参见 [SECURITY_zh.md](SECURITY_zh.md)。  
发布流程清单，请参见 [CONTRIBUTING_zh.md § 发布流程](CONTRIBUTING_zh.md#发布流程)。*

