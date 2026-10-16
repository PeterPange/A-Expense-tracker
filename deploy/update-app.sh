#!/bin/bash
# ═══════════════════════════════════════════════════════════════
#  记账助手 - 发新版脚本
#
#  用法（在云服务器上执行）:
#    sudo bash update-app.sh <apk文件> <版本名> <版本号> <更新说明>
#
#  示例:
#    sudo bash update-app.sh app-debug.apk "0.4.0" 4 "新增周视图/年度报表"
#
#  执行后 App 内「设置 → 检测更新」即可发现新版本并一键升级
# ═══════════════════════════════════════════════════════════════
set -e

APK_FILE="$1"
VERSION_NAME="$2"
VERSION_CODE="$3"
CHANGELOG="$4"
DATA_DIR="/opt/jizhang-cloud/data"
SERVICE_NAME="jizhang-cloud"

if [ "$(id -u)" != "0" ]; then
  echo "❌ 请用 root 执行: sudo bash $0 <apk> <版本名> <版本号> <更新说明>"
  exit 1
fi

if [ -z "$APK_FILE" ] || [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
  echo "用法: sudo bash $0 <apk文件> <版本名> <版本号> <更新说明>"
  echo "示例: sudo bash $0 app.apk \"0.4.0\" 4 \"新增周视图\""
  exit 1
fi

if [ ! -f "$APK_FILE" ]; then
  echo "❌ APK 文件不存在: $APK_FILE"
  exit 1
fi

CHANGELOG="${CHANGELOG:-功能优化与修复}"

echo "═══════════════════════════════════════════"
echo "  发布新版本"
echo "  版本: $VERSION_NAME (code: $VERSION_CODE)"
echo "  说明: $CHANGELOG"
echo "  APK:  $APK_FILE ($(du -h "$APK_FILE" | cut -f1))"
echo "═══════════════════════════════════════════"

# 1. 复制 APK
cp "$APK_FILE" "$DATA_DIR/latest.apk"
echo "✓ APK 已部署"

# 2. 写版本信息
cat > "$DATA_DIR/app-version.json" <<EOF
{
  "versionCode": $VERSION_CODE,
  "versionName": "$VERSION_NAME",
  "changelog": "$CHANGELOG",
  "apkUrl": "/app/latest.apk",
  "publishedAt": $(date +%s000)
}
EOF
echo "✓ 版本信息已写入"

# 3. 重启服务（加载新端点，如果还没部署过 v2 的话）
if ! curl -s --max-time 3 http://127.0.0.1:8900/api/app/version 2>/dev/null | grep -q "versionCode"; then
  echo "▸ 检测到服务未包含版本端点，重新部署..."
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  if [ -f "$SCRIPT_DIR/deploy-jizhang-cloud.sh" ]; then
    bash "$SCRIPT_DIR/deploy-jizhang-cloud.sh"
  else
    systemctl restart "$SERVICE_NAME"
    echo "⚠ 找不到 deploy-jizhang-cloud.sh，仅重启了服务"
    echo "  如版本端点仍不可用，请重新执行 deploy-jizhang-cloud.sh"
  fi
else
  echo "✓ 版本端点已就绪"
fi

# 4. 验证
sleep 2
V=$(curl -s --max-time 5 http://127.0.0.1:8900/api/app/version 2>/dev/null)
if echo "$V" | grep -q "$VERSION_CODE"; then
  echo "✓ 版本接口验证通过"
else
  echo "⚠ 版本接口异常: $V"
  echo "  请检查: journalctl -u $SERVICE_NAME -n 10"
fi

APK_SIZE=$(curl -s -o /dev/null -w "%{size_download}" --max-time 10 http://127.0.0.1:8900/app/latest.apk 2>/dev/null)
if [ "$APK_SIZE" -gt 1000000 ]; then
  echo "✓ APK 下载验证通过 ($(($APK_SIZE / 1024 / 1024))MB)"
else
  echo "⚠ APK 下载异常（$APK_SIZE 字节）"
fi

echo ""
echo "═══════════════════════════════════════════"
echo "  🎉 发布完成！"
echo "═══════════════════════════════════════════"
echo ""
echo "  App 内「我的 → 检测更新」即可发现新版本"
echo "  版本接口: http://$(curl -s --max-time 3 ifconfig.me 2>/dev/null || echo '服务器IP'):8900/api/app/version"
echo ""
