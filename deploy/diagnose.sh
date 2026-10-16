#!/bin/bash
# 记账助手云端服务诊断脚本 - 在服务器上执行
echo "═══════════════════════════════════════════"
echo "  记账助手云端服务诊断"
echo "═══════════════════════════════════════════"

echo ""
echo "── 1. 服务状态 ──"
systemctl status jizhang-cloud --no-pager 2>&1 | head -12

echo ""
echo "── 2. 最近日志（找报错）──"
journalctl -u jizhang-cloud -n 25 --no-pager 2>&1 | tail -25

echo ""
echo "── 3. 端口监听 ──"
ss -tlnp | grep 8900 || echo "❌ 8900 端口无监听！"

echo ""
echo "── 4. 本机自测 ──"
curl -s --max-time 5 http://127.0.0.1:8900/api/health && echo " ← 本机OK" || echo "❌ 本机也不通"

echo ""
echo "── 5. SMTP 配置 ──"
if [ -f /opt/jizhang-cloud/smtp.env ]; then
  echo "smtp.env 内容（密码已隐藏）："
  sed 's/SMTP_PASS=.*/SMTP_PASS=***已配置***/' /opt/jizhang-cloud/smtp.env
else
  echo "❌ smtp.env 不存在"
fi

echo ""
echo "── 6. Node 进程 ──"
ps aux | grep "server.mjs" | grep -v grep || echo "❌ 无 Node 进程在跑"

echo ""
echo "── 7. 常见问题自动修复 ──"
# 服务没跑 → 重启
if ! systemctl is-active --quiet jizhang-cloud 2>/dev/null; then
  echo "▸ 服务未运行，尝试重启..."
  systemctl restart jizhang-cloud
  sleep 3
  curl -s --max-time 5 http://127.0.0.1:8900/api/health && echo " ← 重启后OK" || echo "❌ 重启后仍失败，请把上面的日志发给我"
fi

echo ""
echo "═══════════════════════════════════════════"
echo "  诊断完成。如果本机OK但外网不通，"
echo "  检查阿里云安全组是否放行 8900/TCP"
echo "═══════════════════════════════════════════"
