#!/bin/bash

# ============================================================================
# 生成 JKS 格式签名密钥库脚本
# Script to generate JKS keystore for Android app signing
# ============================================================================

set -e

echo "========================================"
echo "  Gsyn Java - Keystore 生成工具"
echo "========================================"
echo ""

# 默认值
DEFAULT_ALIAS="gsyn-release"
DEFAULT_KEYSTORE="release.jks"
DEFAULT_KEYSIZE="2048"
DEFAULT_VALIDITY="10000"

# 提示输入参数
read -p "Keystore 文件名 (默认: $DEFAULT_KEYSTORE): " KEYSTORE_NAME
KEYSTORE_NAME=${KEYSTORE_NAME:-$DEFAULT_KEYSTORE}

read -p "密钥别名 (默认: $DEFAULT_ALIAS): " KEY_ALIAS
KEY_ALIAS=${KEY_ALIAS:-$DEFAULT_ALIAS}

read -p "密钥大小 (默认: $DEFAULT_KEYSIZE): " KEY_SIZE
KEY_SIZE=${KEY_SIZE:-$DEFAULT_KEYSIZE}

read -p "有效期/天 (默认: $DEFAULT_VALIDITY): " VALIDITY
VALIDITY=${VALIDITY:-$DEFAULT_VALIDITY}

echo ""
echo "将使用以下参数生成 keystore："
echo "  文件名: $KEYSTORE_NAME"
echo "  别名: $KEY_ALIAS"
echo "  密钥大小: $KEY_SIZE bits"
echo "  有效期: $VALIDITY 天"
echo ""

# 检查文件是否已存在
if [ -f "$KEYSTORE_NAME" ]; then
    read -p "⚠️  文件 '$KEYSTORE_NAME' 已存在，是否覆盖？(y/N): " OVERWRITE
    if [[ ! "$OVERWRITE" =~ ^[Yy]$ ]]; then
        echo "已取消。"
        exit 0
    fi
    rm -f "$KEYSTORE_NAME"
fi

echo ""
echo "开始生成 keystore..."
echo "注意：请妥善保管即将输入的密码！"
echo ""

# 生成 JKS keystore
keytool -genkeypair \
    -storetype JKS \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize "$KEY_SIZE" \
    -validity "$VALIDITY" \
    -keystore "$KEYSTORE_NAME"

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ Keystore 生成失败"
    exit 1
fi

echo ""
echo "✅ Keystore 生成成功: $KEYSTORE_NAME"
echo ""

# 验证 keystore 格式
echo "正在验证 keystore 格式..."
KEYSTORE_TYPE=$(keytool -list -v -keystore "$KEYSTORE_NAME" 2>&1 | grep "Keystore type:" | awk '{print $3}')

if [ "$KEYSTORE_TYPE" != "JKS" ]; then
    echo "❌ 错误: Keystore 类型不是 JKS (实际: $KEYSTORE_TYPE)"
    exit 1
fi

echo "✅ Keystore 格式验证通过: $KEYSTORE_TYPE"
echo ""

# 显示 keystore 信息
echo "Keystore 信息："
keytool -list -v -keystore "$KEYSTORE_NAME" | head -20

echo ""
echo "========================================"
echo "  下一步：配置 GitHub Secrets"
echo "========================================"
echo ""
echo "1. 将 keystore 编码为 Base64："
echo ""

# 根据操作系统提供不同的命令
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    echo "   macOS 命令（自动复制到剪贴板）："
    echo "   base64 $KEYSTORE_NAME | tr -d '\\n' | pbcopy"
    echo ""
    read -p "   是否现在执行并复制到剪贴板？(y/N): " DO_ENCODE
    if [[ "$DO_ENCODE" =~ ^[Yy]$ ]]; then
        base64 "$KEYSTORE_NAME" | tr -d '\n' | pbcopy
        echo "   ✅ Base64 已复制到剪贴板"
    fi
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Linux
    echo "   Linux 命令："
    echo "   base64 $KEYSTORE_NAME | tr -d '\\n'"
    echo ""
    read -p "   是否现在显示 Base64 编码？(y/N): " DO_ENCODE
    if [[ "$DO_ENCODE" =~ ^[Yy]$ ]]; then
        echo ""
        base64 "$KEYSTORE_NAME" | tr -d '\n'
        echo ""
    fi
else
    # Windows (Git Bash) or other
    echo "   命令："
    echo "   base64 $KEYSTORE_NAME | tr -d '\\n'"
fi

echo ""
echo "2. 在 GitHub 仓库中添加以下 4 个 Secrets："
echo "   Settings → Secrets and variables → Actions → New repository secret"
echo ""
echo "   Secret 名称              | 值"
echo "   -------------------------|----------------------------------"
echo "   KEYSTORE_BASE64          | (步骤 1 中的 Base64 字符串)"
echo "   KEYSTORE_PASSWORD        | (你输入的 keystore 密码)"
echo "   KEY_ALIAS                | $KEY_ALIAS"
echo "   KEY_PASSWORD             | (你输入的 key 密码)"
echo ""
echo "3. 完成后即可推送 tag 触发自动发布："
echo "   git tag v1.0.0"
echo "   git push origin v1.0.0"
echo ""
echo "⚠️  安全提示："
echo "   - 请妥善保管 $KEYSTORE_NAME 文件和密码"
echo "   - 建议将 keystore 文件加密备份到安全位置"
echo "   - 不要将 keystore 文件提交到 Git 仓库"
echo ""
echo "完整文档: RELEASE.md"
echo "========================================"
