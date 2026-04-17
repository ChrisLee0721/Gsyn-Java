# 发布指南 (Release Guide)

本项目使用 GitHub Actions 自动构建和发布 Android APK。当推送 `v*` 格式的 tag 时（如 `v1.0.0`、`v1.2.3-beta` 等），将自动触发 Release 流程。

## 一、配置签名密钥库 (Keystore)

### 1.1 生成 Keystore 文件

**重要提示**：必须使用 JKS 格式，而不是 PKCS12 格式。Android Gradle Plugin 对 PKCS12 格式的支持有限，可能导致 "Tag number over 30 is not supported" 错误。

使用以下命令生成 JKS 格式的 keystore：

```bash
keytool -genkeypair \
  -storetype JKS \
  -alias gsyn-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -keystore release.jks
```

命令参数说明：
- `-storetype JKS`: 指定使用 JKS 格式（必须）
- `-alias gsyn-release`: 密钥别名（可自定义，记录下来供后续使用）
- `-keyalg RSA`: 使用 RSA 算法
- `-keysize 2048`: 密钥长度 2048 位
- `-validity 10000`: 有效期 10000 天（约 27 年）
- `-keystore release.jks`: 输出文件名

执行后会提示输入：
1. **Keystore 密码**：至少 6 位，记录下来
2. **Key 密码**：可与 Keystore 密码相同，记录下来
3. 姓名、组织、城市等信息（根据实际填写）

### 1.2 验证 Keystore 格式

生成后，使用以下命令验证 keystore 格式：

```bash
keytool -list -v -keystore release.jks
```

确认输出中显示 `Keystore type: JKS` 而非 `Keystore type: PKCS12`。

### 1.3 转换已有 PKCS12 Keystore（如果需要）

如果已有 PKCS12 格式的 keystore，需要转换为 JKS 格式：

```bash
# 1. 导出证书和私钥到 PKCS12 临时文件（如果还不是 PKCS12）
keytool -importkeystore \
  -srckeystore old-keystore.jks \
  -destkeystore temp.p12 \
  -deststoretype PKCS12

# 2. 从 PKCS12 导入到新的 JKS keystore
keytool -importkeystore \
  -srckeystore temp.p12 \
  -srcstoretype PKCS12 \
  -destkeystore release.jks \
  -deststoretype JKS

# 3. 删除临时文件
rm temp.p12
```

## 二、配置 GitHub Secrets

进入仓库的 **Settings → Secrets and variables → Actions → Repository secrets**，添加以下 4 个 Secret：

### 2.1 KEYSTORE_BASE64

将 keystore 文件编码为 Base64：

**Linux / macOS:**
```bash
base64 release.jks | tr -d '\n' | pbcopy  # macOS，自动复制到剪贴板
# 或
base64 release.jks | tr -d '\n'           # Linux，手动复制输出
```

**Windows PowerShell:**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
# 或手动复制输出
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))
```

将输出的 Base64 字符串（不包含换行符）粘贴到 GitHub Secret `KEYSTORE_BASE64` 中。

### 2.2 KEYSTORE_PASSWORD

Keystore 文件的密码（生成时输入的第一个密码）。

### 2.3 KEY_ALIAS

生成 keystore 时使用的别名，例如 `gsyn-release`。

### 2.4 KEY_PASSWORD

Key 的密码（生成时输入的第二个密码，通常与 KEYSTORE_PASSWORD 相同）。

## 三、创建 Release

### 3.1 更新版本号

编辑 `app/build.gradle`，更新版本信息：

```gradle
defaultConfig {
    applicationId 'com.opensynaptic.gsynjava'
    minSdk 24
    targetSdk 34
    versionCode 2           // 每次发布递增（整数）
    versionName '1.1.0'     // 语义化版本号
    ...
}
```

### 3.2 提交并推送代码

```bash
git add app/build.gradle
git commit -m "chore: bump version to 1.1.0"
git push origin main
```

### 3.3 创建并推送 Tag

```bash
# 创建 tag（格式必须以 v 开头）
git tag v1.1.0

# 推送 tag 到远程仓库
git push origin v1.1.0
```

### 3.4 自动构建流程

推送 tag 后，GitHub Actions 会自动：

1. ✅ 检出代码
2. ✅ 设置 JDK 17
3. ✅ 解码 Keystore 文件
4. ✅ 构建并签名 Release APK
5. ✅ 重命名 APK 为 `Gsyn-Java-v1.1.0.apk`
6. ✅ 创建 GitHub Release
7. ✅ 上传 APK 到 Release Assets

### 3.5 查看构建状态

- 进入仓库的 **Actions** 标签页
- 查看 "Android CI" workflow 的运行状态
- 如果失败，点击查看日志排查问题

### 3.6 发布完成

构建成功后，在仓库的 **Releases** 页面可以看到新创建的 Release，用户可以下载 APK 安装。

## 四、常见问题排查

### 4.1 "Failed to read key from store: Tag number over 30 is not supported"

**原因**：Keystore 使用了 PKCS12 格式，而非 JKS 格式。

**解决方案**：
1. 按照 "一、配置签名密钥库" 重新生成 JKS 格式的 keystore
2. 或使用 "1.3 转换已有 PKCS12 Keystore" 转换现有 keystore
3. 重新将 JKS keystore 编码为 Base64 并更新 `KEYSTORE_BASE64` Secret
4. 重新推送 tag 或手动触发 workflow

### 4.2 "Keystore was tampered with, or password was incorrect"

**原因**：`KEYSTORE_PASSWORD` 或 `KEY_PASSWORD` 不正确。

**解决方案**：
1. 检查 GitHub Secrets 中的密码是否正确
2. 使用 `keytool -list -keystore release.jks` 在本地验证密码
3. 更新 Secrets 后重新运行 workflow

### 4.3 "Could not find key with alias 'xxx'"

**原因**：`KEY_ALIAS` 与 keystore 中的实际别名不匹配。

**解决方案**：
1. 使用 `keytool -list -v -keystore release.jks` 查看实际别名
2. 更新 GitHub Secret `KEY_ALIAS` 为正确值
3. 重新运行 workflow

### 4.4 构建成功但没有创建 Release

**原因**：Tag 格式不正确，或没有以 `v` 开头。

**解决方案**：
1. 确保 tag 名称以 `v` 开头，例如 `v1.0.0`、`v2.1.3-beta`
2. 删除错误的 tag：`git tag -d wrong-tag && git push origin :refs/tags/wrong-tag`
3. 重新创建正确格式的 tag

## 五、本地签名构建（可选）

如果需要在本地构建签名的 Release APK（用于测试），可以创建 `local.properties` 文件（不提交到 Git）：

```properties
# local.properties（已在 .gitignore 中忽略）
android.injected.signing.store.file=/path/to/release.jks
android.injected.signing.store.password=your_keystore_password
android.injected.signing.key.alias=gsyn-release
android.injected.signing.key.password=your_key_password
```

然后执行：

```bash
./gradlew assembleRelease
```

生成的签名 APK 位于：
```
app/build/outputs/apk/release/app-release.apk
```

## 六、安全提示

- ⚠️ **绝不要**将 keystore 文件或密码提交到 Git 仓库
- ⚠️ `release.jks` 和 `local.properties` 已在 `.gitignore` 中配置忽略
- ⚠️ 妥善保管 keystore 文件和密码，一旦丢失无法恢复
- ⚠️ 建议将 keystore 文件加密备份到安全位置

## 七、预发布版本

创建预发布版本（如 `v1.0.0-beta`、`v2.0.0-rc1`）：

```bash
git tag v1.0.0-beta
git push origin v1.0.0-beta
```

Workflow 会自动检测 tag 名称中的 `-` 符号，将 Release 标记为 "Pre-release"。
