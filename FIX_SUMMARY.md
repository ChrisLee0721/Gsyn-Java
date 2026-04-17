# Release 问题修复总结

## 问题诊断

通过分析 GitHub Actions 工作流日志，发现 v1.0.0 tag 触发的 Release 构建失败，错误信息：

```
com.android.ide.common.signing.KeytoolException: Failed to read key from store 
"/home/runner/work/_temp/release.jks": Tag number over 30 is not supported
```

**根本原因**：
- Keystore 文件使用了 PKCS12 格式（Java 9+ 默认格式）
- Android Gradle Plugin 对 PKCS12 格式支持有限
- 需要使用传统的 JKS 格式

## 解决方案

### 1. 创建了完整的发布指南 (RELEASE.md)

详细文档包括：
- ✅ JKS keystore 生成步骤
- ✅ PKCS12 转 JKS 的转换方法
- ✅ GitHub Secrets 配置说明
- ✅ 版本发布完整流程
- ✅ 常见问题排查指南
- ✅ 安全最佳实践

### 2. 增强了 CI 工作流 (.github/workflows/android-ci.yml)

添加了 keystore 验证步骤：
- ✅ 自动检测 keystore 格式（必须是 JKS）
- ✅ 验证密钥别名是否存在
- ✅ 提供清晰的错误提示和解决方案
- ✅ 在工作流注释中添加常见错误处理说明

### 3. 提供了自动化工具

创建了两个 keystore 生成脚本：

**Linux/macOS**: `scripts/generate-keystore.sh`
```bash
./scripts/generate-keystore.sh
```

**Windows**: `scripts/generate-keystore.ps1`
```powershell
.\scripts\generate-keystore.ps1
```

脚本功能：
- ✅ 自动生成 JKS 格式 keystore
- ✅ 验证格式正确性
- ✅ 自动生成 Base64 编码
- ✅ 提供后续配置步骤指引

### 4. 更新了 README.md

添加了快速发布指南章节，包含：
- ✅ 发布流程概览
- ✅ GitHub Secrets 配置清单
- ✅ 指向 RELEASE.md 的详细文档链接

## 下一步操作

用户需要完成以下步骤才能成功发布：

### 方法一：使用自动化脚本（推荐）

1. 运行 keystore 生成脚本：
   ```bash
   # Linux/macOS
   ./scripts/generate-keystore.sh
   
   # Windows
   .\scripts\generate-keystore.ps1
   ```

2. 按提示生成 JKS keystore 并获取 Base64 编码

3. 在 GitHub 仓库配置 4 个 Secrets：
   - `KEYSTORE_BASE64` - Base64 编码的 keystore
   - `KEYSTORE_PASSWORD` - Keystore 密码
   - `KEY_ALIAS` - 密钥别名（如 gsyn-release）
   - `KEY_PASSWORD` - 密钥密码

4. 删除旧的 v1.0.0 tag 并重新创建：
   ```bash
   git tag -d v1.0.0
   git push origin :refs/tags/v1.0.0
   git tag v1.0.0
   git push origin v1.0.0
   ```

### 方法二：手动生成

参考 `RELEASE.md` 文档中的详细步骤。

## 验证

配置完成后，推送 tag 将触发 CI/CD 流程：
1. Workflow 会先验证 keystore 格式
2. 如果格式不正确，会提供清晰的错误信息
3. 格式正确则继续构建和签名 APK
4. 自动创建 GitHub Release 并上传 APK

## 文件变更清单

1. **新增文件**:
   - `RELEASE.md` - 完整发布指南
   - `scripts/generate-keystore.sh` - Linux/macOS 脚本
   - `scripts/generate-keystore.ps1` - Windows 脚本
   - `FIX_SUMMARY.md` - 本文档

2. **修改文件**:
   - `.github/workflows/android-ci.yml` - 添加 keystore 验证
   - `README.md` - 添加发布指南章节

3. **已忽略文件** (`.gitignore`):
   - `*.jks` - Keystore 文件
   - `*.keystore` - 其他格式 keystore
   - `keystore_base64.txt` - Base64 编码文件

## 安全提示

⚠️ 重要：
- Keystore 文件和密码已在 .gitignore 中配置，不会被提交
- 请妥善保管 keystore 文件，建议加密备份
- 一旦丢失无法恢复，需要重新生成（用户需重新安装应用）
- GitHub Secrets 是加密存储的，安全可靠

## 参考文档

- [RELEASE.md](./RELEASE.md) - 完整发布指南
- [Android 官方签名文档](https://developer.android.com/studio/publish/app-signing)
- [GitHub Actions Secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
