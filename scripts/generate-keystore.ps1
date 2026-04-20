# ============================================================================
# 生成 JKS 格式签名密钥库脚本 (Windows PowerShell)
# Script to generate JKS keystore for Android app signing
# ============================================================================

Write-Host "========================================"
Write-Host "  Gsyn Java - Keystore 生成工具"
Write-Host "========================================"
Write-Host ""

# 默认值
$defaultAlias = "gsyn-release"
$defaultKeystore = "release.jks"
$defaultKeysize = "2048"
$defaultValidity = "10000"

# 提示输入参数
$keystoreName = Read-Host "Keystore 文件名 (默认: $defaultKeystore)"
if ([string]::IsNullOrWhiteSpace($keystoreName)) { $keystoreName = $defaultKeystore }

$keyAlias = Read-Host "密钥别名 (默认: $defaultAlias)"
if ([string]::IsNullOrWhiteSpace($keyAlias)) { $keyAlias = $defaultAlias }

$keySize = Read-Host "密钥大小 (默认: $defaultKeysize)"
if ([string]::IsNullOrWhiteSpace($keySize)) { $keySize = $defaultKeysize }

$validity = Read-Host "有效期/天 (默认: $defaultValidity)"
if ([string]::IsNullOrWhiteSpace($validity)) { $validity = $defaultValidity }

Write-Host ""
Write-Host "将使用以下参数生成 keystore："
Write-Host "  文件名: $keystoreName"
Write-Host "  别名: $keyAlias"
Write-Host "  密钥大小: $keySize bits"
Write-Host "  有效期: $validity 天"
Write-Host ""

# 检查文件是否已存在
if (Test-Path $keystoreName) {
    $overwrite = Read-Host "⚠️  文件 '$keystoreName' 已存在，是否覆盖？(y/N)"
    if ($overwrite -notmatch '^[Yy]$') {
        Write-Host "已取消。"
        exit 0
    }
    Remove-Item $keystoreName -Force
}

Write-Host ""
Write-Host "开始生成 keystore..."
Write-Host "注意：请妥善保管即将输入的密码！"
Write-Host ""

# 生成 JKS keystore
& keytool -genkeypair `
    -storetype JKS `
    -alias $keyAlias `
    -keyalg RSA `
    -keysize $keySize `
    -validity $validity `
    -keystore $keystoreName

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ Keystore 生成失败" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✅ Keystore 生成成功: $keystoreName" -ForegroundColor Green
Write-Host ""

# 验证 keystore 格式
Write-Host "正在验证 keystore 格式..."
$keystoreInfo = & keytool -J-Duser.language=en -list -v -keystore $keystoreName 2>&1 | Out-String
$keystoreType = if ($keystoreInfo -match "Keystore type:\s*(\w+)") { $Matches[1] } else { "UNKNOWN" }

if ($keystoreType -ne "JKS") {
    Write-Host "❌ 错误: Keystore 类型不是 JKS (实际: $keystoreType)" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Keystore 格式验证通过: $keystoreType" -ForegroundColor Green
Write-Host ""

# 显示 keystore 信息
Write-Host "Keystore 信息："
& keytool -J-Duser.language=en -list -v -keystore $keystoreName | Select-Object -First 20

Write-Host ""
Write-Host "========================================"
Write-Host "  下一步：配置 GitHub Secrets"
Write-Host "========================================"
Write-Host ""
Write-Host "1. 将 keystore 编码为 Base64："
Write-Host ""
Write-Host "   PowerShell 命令（自动复制到剪贴板）："
Write-Host "   [Convert]::ToBase64String([IO.File]::ReadAllBytes('$keystoreName')) | Set-Clipboard"
Write-Host ""

$doEncode = Read-Host "   是否现在执行并复制到剪贴板？(y/N)"
if ($doEncode -match '^[Yy]$') {
    $base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystoreName))
    $base64 | Set-Clipboard
    Write-Host "   ✅ Base64 已复制到剪贴板 (长度: $($base64.Length) 字符)" -ForegroundColor Green
}

Write-Host ""
Write-Host "2. 在 GitHub 仓库中添加以下 4 个 Secrets："
Write-Host "   Settings → Secrets and variables → Actions → New repository secret"
Write-Host ""
Write-Host "   Secret 名称              | 值"
Write-Host "   -------------------------|----------------------------------"
Write-Host "   KEYSTORE_BASE64          | (步骤 1 中的 Base64 字符串)"
Write-Host "   KEYSTORE_PASSWORD        | (你输入的 keystore 密码)"
Write-Host "   KEY_ALIAS                | $keyAlias"
Write-Host "   KEY_PASSWORD             | (你输入的 key 密码)"
Write-Host ""
Write-Host "3. 完成后即可推送 tag 触发自动发布："
Write-Host "   git tag v1.0.0"
Write-Host "   git push origin v1.0.0"
Write-Host ""
Write-Host "⚠️  安全提示："
Write-Host "   - 请妥善保管 $keystoreName 文件和密码"
Write-Host "   - 建议将 keystore 文件加密备份到安全位置"
Write-Host "   - 不要将 keystore 文件提交到 Git 仓库"
Write-Host ""
Write-Host "完整文档: RELEASE.md"
Write-Host "========================================"
