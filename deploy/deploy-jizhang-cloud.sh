#!/bin/bash
# ═══════════════════════════════════════════════════════════════
#  记账助手 云端服务 - 阿里云一键部署脚本 v2
#
#  功能: 账号密码登录 + 邮箱验证码登录 + 记账数据云端同步
#  依赖: 仅 Node.js >= 16（零 npm 依赖，SMTP 用内置 tls 模块手写）
#
#  用法（root 执行）: bash deploy-jizhang-cloud.sh
#  可选: PORT=8900  自定义端口
#
#  邮箱验证码登录需要配置 SMTP（见下方 SMTP_ 变量说明）：
#    QQ邮箱:  SMTP_HOST=smtp.qq.com  SMTP_PORT=465  SMTP_USER=你的QQ号@qq.com  SMTP_PASS=授权码
#    163邮箱: SMTP_HOST=smtp.163.com SMTP_PORT=465  SMTP_USER=你的邮箱@163.com SMTP_PASS=授权码
#    （授权码在邮箱设置里开启 SMTP 服务后生成，不是登录密码）
# ═══════════════════════════════════════════════════════════════
set -e

CLOUD_DIR="/opt/jizhang-cloud"
PORT="${PORT:-8900}"
SERVICE_NAME="jizhang-cloud"

# ── SMTP 配置（邮箱验证码登录用，不配则该功能自动禁用）──
SMTP_HOST="${SMTP_HOST:-}"
SMTP_PORT="${SMTP_PORT_SMTP:-465}"
SMTP_USER="${SMTP_USER:-}"
SMTP_PASS="${SMTP_PASS:-}"

echo "═══════════════════════════════════════════"
echo "  记账助手云端服务部署 v2"
echo "  目录: $CLOUD_DIR   端口: $PORT"
if [ -n "$SMTP_HOST" ] && [ -n "$SMTP_USER" ] && [ -n "$SMTP_PASS" ]; then
  echo "  邮箱登录: 已配置 ($SMTP_USER)"
else
  echo "  邮箱登录: 未配置 SMTP（仅账号密码登录）"
fi
echo "═══════════════════════════════════════════"

if [ "$(id -u)" != "0" ]; then
  echo "❌ 请用 root 执行: sudo bash $0"
  exit 1
fi

# ── Node.js 检查 ──────────────────────────────────────────────
if command -v node >/dev/null 2>&1; then
  echo "✓ Node.js $(node -v)"
else
  echo "▸ 安装 Node.js 20 ..."
  if command -v apt-get >/dev/null 2>&1; then
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - >/dev/null 2>&1 \
      && apt-get install -y nodejs >/dev/null 2>&1
  elif command -v dnf >/dev/null 2>&1 || command -v yum >/dev/null 2>&1; then
    curl -fsSL https://rpm.nodesource.com/setup_20.x | bash - >/dev/null 2>&1 \
      && (dnf -y install nodejs >/dev/null 2>&1 || yum -y install nodejs >/dev/null 2>&1)
  fi
  command -v node >/dev/null 2>&1 && echo "✓ Node.js $(node -v)" || { echo "❌ Node.js 安装失败"; exit 1; }
fi

mkdir -p "$CLOUD_DIR/data"

# ── SMTP 配置文件（可后期修改后重启服务生效）───────────────────
cat > "$CLOUD_DIR/smtp.env" <<EOF
SMTP_HOST=$SMTP_HOST
SMTP_PORT=$SMTP_PORT
SMTP_USER=$SMTP_USER
SMTP_PASS=$SMTP_PASS
EOF
echo "✓ SMTP 配置写入 $CLOUD_DIR/smtp.env（可手动修改后 systemctl restart $SERVICE_NAME）"

# ── 服务代码 ──────────────────────────────────────────────────
cat > "$CLOUD_DIR/server.mjs" <<'SERVER_EOF'
/**
 * 记账助手云端服务 v2
 * - 账号密码登录（scrypt 哈希 + 令牌）
 * - 邮箱验证码登录（零依赖 SMTP，自动注册）
 * - 记账数据同步（全量快照 + 版本冲突检测）
 */
import http from "node:http";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import tls from "node:tls";
import readline from "node:readline";

const PORT = parseInt(process.env.PORT || "8900", 10);
const DATA_DIR = process.env.DATA_DIR || "/opt/jizhang-cloud/data";
const SMTP_ENV = path.join(path.dirname(DATA_DIR), "smtp.env");

// ── SMTP 配置 ──
let SMTP = { host: "", port: 465, user: "", pass: "" };
try {
  const env = fs.readFileSync(SMTP_ENV, "utf8");
  for (const line of env.split("\n")) {
    const m = line.match(/^(\w+)=(.*)$/);
    if (m) SMTP[m[1].toLowerCase()] = m[2];
  }
} catch {}
const smtpReady = () => Boolean(SMTP.host && SMTP.user && SMTP.pass);

// ── 存储 ──
fs.mkdirSync(DATA_DIR, { recursive: true });
const load = (f, def) => { try { return JSON.parse(fs.readFileSync(f, "utf8")); } catch { return def; } };
const save = (f, d) => fs.writeFileSync(f, JSON.stringify(d));
let users = load(path.join(DATA_DIR, "users.json"), {});
let tokens = load(path.join(DATA_DIR, "tokens.json"), {});
let emailCodes = {}; // { email: { code, expires, sentAt } }
const persist = () => { save(path.join(DATA_DIR, "users.json"), users); save(path.join(DATA_DIR, "tokens.json"), tokens); };

const TOKEN_TTL = 30 * 24 * 3600 * 1000;

function hashPwd(password, salt) {
  return crypto.scryptSync(password, salt, 32).toString("hex");
}
function newToken(username, userId) {
  const token = crypto.randomBytes(24).toString("hex");
  tokens[token] = { username, userId, expires: Date.now() + TOKEN_TTL };
  for (const [t, v] of Object.entries(tokens)) if (v.expires < Date.now()) delete tokens[t];
  persist();
  return token;
}
function auth(req) {
  const token = (req.headers["authorization"] || "").replace(/^Bearer\s+/i, "");
  const t = tokens[token];
  return t && t.expires > Date.now() ? t : null;
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    let b = "";
    req.on("data", c => { b += c; if (b.length > 20e6) { reject(new Error("too large")); req.destroy(); } });
    req.on("end", () => { try { resolve(b ? JSON.parse(b) : {}); } catch { reject(new Error("bad json")); } });
    req.on("error", reject);
  });
}
const userFile = (userId) => path.join(DATA_DIR, userId + ".json");

// ── 零依赖 SMTP 发送（TLS 直连，标准 SMTP 协议）──
async function sendMail(to, subject, body) {
  return new Promise((resolve, reject) => {
    const socket = tls.connect({
      host: SMTP.host, port: parseInt(SMTP.port) || 465,
      rejectUnauthorized: false,
    }, () => {});
    let stage = 0;
    const fail = (msg) => { try { socket.destroy(); } catch {} reject(new Error(msg)); };
    let buffer = "";

    const step = () => {
      const send = (cmd) => socket.write(cmd + "\r\n");
      switch (stage) {
        case 0: send("EHLO jizhang"); stage = 1; break;
        case 2: send("AUTH LOGIN"); stage = 3; break;
        case 4: send(Buffer.from(SMTP.user).toString("base64")); stage = 5; break;
        case 6: send(Buffer.from(SMTP.pass).toString("base64")); stage = 7; break;
        case 8: send(`MAIL FROM:<${SMTP.user}>`); stage = 9; break;
        case 10: send(`RCPT TO:<${to}>`); stage = 11; break;
        case 12:
          send("DATA");
          const mail = [
            `From: 记账助手 <${SMTP.user}>`,
            `To: <${to}>`,
            `Subject: =?UTF-8?B?${Buffer.from(subject).toString("base64")}?=`,
            "MIME-Version: 1.0",
            'Content-Type: text/plain; charset="UTF-8"',
            "",
            body,
            ".",
          ].join("\r\n");
          socket.write(mail + "\r\n");
          stage = 14;
          break;
        case 15: send("QUIT"); socket.end(); resolve(true); break;
      }
    };

    socket.on("data", (chunk) => {
      buffer += chunk.toString();
      if (!/\r?\n$/.test(buffer)) return;
      const lines = buffer.split(/\r?\n/).filter(l => l);
      buffer = "";
      const last = lines[lines.length - 1] || "";
      const code = parseInt(last.slice(0, 3));

      if (code >= 500) return fail("SMTP 拒绝: " + last);

      switch (stage) {
        case 1: // EHLO 响应
          if (code === 250) { step(); stage = 2; step(); }
          break;
        case 3: // AUTH LOGIN 响应
          if (code === 334) { step(); stage = 4; step(); }
          break;
        case 5: // 用户名响应
          if (code === 334) { step(); stage = 6; step(); }
          break;
        case 7: // 密码响应
          if (code === 235) { step(); stage = 8; step(); }
          else if (code >= 500) return fail("SMTP 认证失败（检查授权码）");
          break;
        case 9: if (code === 250) { step(); stage = 10; step(); } break;
        case 11: if (code === 250) { step(); stage = 12; step(); } break;
        case 13: if (code === 250) { step(); stage = 15; step(); } break;
        case 14: if (code === 250) { step(); stage = 15; step(); } else if (code === 354) { /* DATA 继续 */ } break;
      }
    });
    socket.on("error", (e) => fail("SMTP 连接失败: " + e.message));
    socket.on("secureConnect", () => {
      // 等 220 问候
      const wait220 = (chunk) => {
        buffer += chunk.toString();
        if (/\r?\n$/.test(buffer)) {
          socket.removeListener("data", wait220);
          if (buffer.startsWith("220")) { buffer = ""; step(); }
          else fail("SMTP 问候异常: " + buffer.slice(0, 50));
        }
      };
      socket.on("data", wait220);
    });
    setTimeout(() => fail("SMTP 超时"), 20000);
  });
}

// ── HTTP 服务 ──
const server = http.createServer(async (req, res) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Headers", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  if (req.method === "OPTIONS") { res.writeHead(204); return res.end(); }

  const json = (code, obj) => { res.writeHead(code, { "Content-Type": "application/json" }); res.end(JSON.stringify(obj)); };
  const url = new URL(req.url, "http://x");

  try {
    if (req.method === "GET" && url.pathname === "/api/health") {
      return json(200, { ok: true, service: "jizhang-cloud", time: Date.now(), emailLogin: smtpReady() });
    }

    // ── App 版本检测（无需登录）──
    if (req.method === "GET" && url.pathname === "/api/app/version") {
        const info = load(path.join(DATA_DIR, "app-version.json"), null);
        if (info) {
            return json(200, { ok: true, ...info });
        }
        return json(200, { ok: true, versionCode: 0, versionName: "0.0.0", changelog: "暂无版本信息", apkUrl: "" });
    }

    // ── APK 下载（无需登录）──
    if (req.method === "GET" && url.pathname === "/app/latest.apk") {
        const apkPath = path.join(DATA_DIR, "latest.apk");
        if (fs.existsSync(apkPath)) {
            const stat = fs.statSync(apkPath);
            res.writeHead(200, {
                "Content-Type": "application/vnd.android.package-archive",
                "Content-Length": stat.size,
                "Content-Disposition": "attachment; filename=app.apk",
            });
            fs.createReadStream(apkPath).pipe(res);
            return;
        }
        return json(404, { ok: false, error: "no apk" });
    }

    // ── 发送邮箱验证码 ──
    if (req.method === "POST" && url.pathname === "/api/email/code") {
      if (!smtpReady()) return json(400, { ok: false, error: "服务器未配置邮箱服务" });
      const { email } = await readBody(req);
      const to = String(email || "").trim().toLowerCase();
      if (!/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(to)) {
        return json(400, { ok: false, error: "邮箱格式不正确" });
      }
      // 60秒限频
      const prev = emailCodes[to];
      if (prev && Date.now() - prev.sentAt < 60000) {
        return json(429, { ok: false, error: "发送太频繁，请稍后再试" });
      }
      const code = String(crypto.randomInt(100000, 999999));
      emailCodes[to] = { code, expires: Date.now() + 5 * 60 * 1000, sentAt: Date.now() };
      await sendMail(to, "记账助手登录验证码", `您的验证码是：${code}\n\n5分钟内有效。如非本人操作请忽略本邮件。`);
      console.log(`[验证码] → ${to}`);
      return json(200, { ok: true });
    }

    // ── 邮箱验证码登录（自动注册）──
    if (req.method === "POST" && url.pathname === "/api/email/login") {
      const { email, code } = await readBody(req);
      const to = String(email || "").trim().toLowerCase();
      const rec = emailCodes[to];
      if (!rec || rec.expires < Date.now()) return json(401, { ok: false, error: "验证码已过期，请重新获取" });
      if (String(code || "").trim() !== rec.code) return json(401, { ok: false, error: "验证码错误" });
      delete emailCodes[to];

      // 按邮箱找/建用户
      let username = "邮箱:" + to;
      let u = users[username];
      if (!u) {
        const userId = crypto.randomBytes(8).toString("hex");
        u = { salt: "", hash: "", userId, createdAt: Date.now(), email: to };
        users[username] = u;
        persist();
        console.log(`[邮箱注册] ${to}`);
      }
      return json(200, { ok: true, token: newToken(username, u.userId), userId: u.userId, username: to });
    }

    // ── 注册（账号密码）──
    if (req.method === "POST" && url.pathname === "/api/register") {
      const { username, password } = await readBody(req);
      const name = String(username || "").trim();
      if (name.length < 2 || name.length > 30) return json(400, { ok: false, error: "用户名需2-30个字符" });
      if (!/^[a-zA-Z0-9_\u4e00-\u9fa5@.]+$/.test(name)) return json(400, { ok: false, error: "用户名仅限中英文数字下划线" });
      if (String(password || "").length < 6) return json(400, { ok: false, error: "密码至少6位" });
      if (users[name]) return json(400, { ok: false, error: "用户名已存在" });
      const salt = crypto.randomBytes(16).toString("hex");
      const userId = crypto.randomBytes(8).toString("hex");
      users[name] = { salt, hash: hashPwd(String(password), salt), userId, createdAt: Date.now() };
      persist();
      console.log(`[注册] ${name}`);
      return json(200, { ok: true, token: newToken(name, userId), userId, username: name });
    }

    // ── 登录（账号密码）──
    if (req.method === "POST" && url.pathname === "/api/login") {
      const { username, password } = await readBody(req);
      const u = users[String(username || "").trim()];
      if (!u || !u.hash || u.hash !== hashPwd(String(password || ""), u.salt)) {
        return json(401, { ok: false, error: "用户名或密码错误" });
      }
      console.log(`[登录] ${username}`);
      return json(200, { ok: true, token: newToken(u.username, u.userId), userId: u.userId, username: u.username });
    }

    // ── 以下需要登录 ──
    const t = auth(req);
    if (!t) return json(401, { ok: false, error: "未登录或登录已过期" });

    if (req.method === "POST" && url.pathname === "/api/sync") {
      const body = await readBody(req);
      const prev = load(userFile(t.userId), null);
      if (prev && prev.version && body.version && prev.version > body.version) {
        return json(409, { ok: false, error: "云端有更新的数据，请先恢复", serverVersion: prev.version });
      }
      body.version = body.version || Date.now();
      body.syncedAt = Date.now();
      save(userFile(t.userId), body);
      console.log(`[同步↑] ${t.username} ${(JSON.stringify(body).length / 1024).toFixed(1)}KB`);
      return json(200, { ok: true, version: body.version });
    }

    if (req.method === "GET" && url.pathname === "/api/sync") {
      const data = load(userFile(t.userId), null);
      return json(200, { ok: true, data, version: data?.version || 0 });
    }

    if (req.method === "GET" && url.pathname === "/api/profile") {
      return json(200, { ok: true, username: t.username, userId: t.userId, hasData: fs.existsSync(userFile(t.userId)) });
    }

    if (req.method === "POST" && url.pathname === "/api/logout") {
      const token = (req.headers["authorization"] || "").replace(/^Bearer\s+/i, "");
      delete tokens[token];
      persist();
      return json(200, { ok: true });
    }

    return json(404, { ok: false, error: "not found" });
  } catch (e) {
    console.error("错误:", e.message);
    return json(500, { ok: false, error: String(e.message).slice(0, 100) });
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`记账助手云端服务已启动: 0.0.0.0:${PORT}`);
  console.log(`邮箱登录: ${smtpReady() ? "已启用" : "未配置（仅账号密码）"}`);
  console.log(`数据目录: ${DATA_DIR}`);
});
SERVER_EOF
echo "✓ 服务代码写入"

# ── systemd ───────────────────────────────────────────────────
if command -v systemctl >/dev/null 2>&1 && [ -d /run/systemd/system ]; then
  systemctl stop "$SERVICE_NAME" >/dev/null 2>&1 || true
  cat > "/etc/systemd/system/${SERVICE_NAME}.service" <<EOF
[Unit]
Description=Jizhang Cloud Service
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
Environment=PORT=$PORT
Environment=DATA_DIR=$CLOUD_DIR/data
ExecStart=$(command -v node) $CLOUD_DIR/server.mjs
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
  systemctl daemon-reload
  systemctl enable --now "$SERVICE_NAME"
  echo "✓ systemd 服务已启动（开机自启）"
else
  pkill -f "jizhang-cloud/server.mjs" >/dev/null 2>&1 || true
  PORT=$PORT DATA_DIR=$CLOUD_DIR/data nohup node "$CLOUD_DIR/server.mjs" > "$CLOUD_DIR/cloud.log" 2>&1 &
  echo "✓ nohup 已启动"
fi

# ── 防火墙 ────────────────────────────────────────────────────
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q active; then
  ufw allow "$PORT/tcp" >/dev/null 2>&1 && echo "✓ ufw 放行 $PORT"
elif command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
  firewall-cmd --permanent --add-port="$PORT/tcp" >/dev/null 2>&1 && firewall-cmd --reload >/dev/null 2>&1 && echo "✓ firewalld 放行 $PORT"
else
  echo "▸ 未检测到防火墙（记得在阿里云控制台安全组放行 TCP $PORT！）"
fi

# ── 自测 ──────────────────────────────────────────────────────
sleep 3
H=$(curl -s --max-time 5 "http://127.0.0.1:$PORT/api/health" 2>/dev/null || echo "")
if echo "$H" | grep -q '"ok":true'; then
  TEST_U="test_$(date +%s)"
  R=$(curl -s -X POST "http://127.0.0.1:$PORT/api/register" -H "Content-Type: application/json" -d "{\"username\":\"$TEST_U\",\"password\":\"test123456\"}")
  TK=$(echo "$R" | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
  curl -s -X POST "http://127.0.0.1:$PORT/api/sync" -H "Authorization: Bearer $TK" -H "Content-Type: application/json" -d '{"records":[1,2,3],"version":1}' >/dev/null
  D=$(curl -s "http://127.0.0.1:$PORT/api/sync" -H "Authorization: Bearer $TK")
  if echo "$D" | grep -q '"records":\[1,2,3\]'; then
    echo "✓ 账号密码登录/同步 自测通过"
  else
    echo "⚠ 同步自测异常: $D"
  fi
  if echo "$H" | grep -q '"emailLogin":true'; then
    echo "✓ 邮箱验证码登录已启用"
  else
    echo "▸ 邮箱登录未启用（配置 SMTP 后重启服务即可）"
  fi
else
  echo "❌ 服务未就绪！查看: journalctl -u $SERVICE_NAME -n 20"
  exit 1
fi

echo ""
echo "═══════════════════════════════════════════"
echo "  🎉 部署完成"
echo "═══════════════════════════════════════════"
echo ""
echo "  App 服务器地址: http://YOUR_SERVER_IP:$PORT"
echo ""
if [ -z "$SMTP_HOST" ]; then
  echo "  📧 启用邮箱验证码登录（可选）："
  echo "  ┌────────────────────────────────────────────┐"
  echo "  │ 1. QQ邮箱设置 → 账户 → 开启SMTP → 获取授权码  │"
  echo "  │ 2. 编辑 $CLOUD_DIR/smtp.env 填入:      │"
  echo "  │    SMTP_HOST=smtp.qq.com                   │"
  echo "  │    SMTP_PORT=465                            │"
  echo "  │    SMTP_USER=你的QQ号@qq.com                 │"
  echo "  │    SMTP_PASS=授权码                          │"
  echo "  │ 3. systemctl restart $SERVICE_NAME        │"
  echo "  └────────────────────────────────────────────┘"
fi
echo "  ⚠ 阿里云控制台 → 安全组 → 放行 TCP $PORT"
echo "  服务管理: systemctl restart|stop|status $SERVICE_NAME"
echo "  查看日志: journalctl -u $SERVICE_NAME -f"
echo ""
