// 家人侠官网静态服务器：零依赖，仅供本机回环（127.0.0.1 + ::1，默认 8088）。
// 公网入口是 cloudflared 隧道回源到本端口。路由顺序：存在的文件直接返回 →
// 预渲染页面（/help → dist/help/index.html）→ 都没有则以 404 状态返回
// dist/404.html（同样带前端路由，页面上显示“找不到页面”）。
// 真 404 很重要：否则每个不存在的地址都返回 200，搜索引擎会判定成软 404。
// 日志写 runtime/logs/web.log（进程无控制台，不能依赖 stdout）。
// 用法：由 jrx.cmd web-start 经 wscript 静默拉起。

import { createServer } from "node:http";
import { appendFileSync, createReadStream, readFileSync, statSync } from "node:fs";
import { stat } from "node:fs/promises";
import { extname, join, normalize, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = resolve(fileURLToPath(import.meta.url), "..");
const DIST = join(HERE, "dist");
const LOG = resolve(HERE, "..", "..", "runtime", "logs", "web.log");
const PORT = Number(process.argv[2] || process.env.JRX_WEB_PORT || 8088);

function log(msg) {
  try { appendFileSync(LOG, `[${new Date().toISOString()}] ${msg}\n`); } catch {}
}
process.on("uncaughtException", (e) => { log(`uncaught: ${e.stack || e}`); process.exit(1); });
process.on("unhandledRejection", (e) => { log(`unhandledRejection: ${e && e.stack || e}`); });

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".mjs": "application/javascript; charset=utf-8",
  ".map": "application/json",
  ".json": "application/json; charset=utf-8",
  ".xml": "application/xml; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".ico": "image/x-icon",
  ".txt": "text/plain; charset=utf-8",
  ".apk": "application/vnd.android.package-archive",
  ".wasm": "application/wasm",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".webmanifest": "application/manifest+json",
};

function send(res, status, headers, body) {
  res.writeHead(status, headers);
  res.end(body === undefined || body === null ? undefined : body);
}

async function fileInfo(path) {
  try {
    const s = await stat(path);
    return s.isFile() ? s : null;  // 目录/其他一律返回 null
  } catch {
    return null;
  }
}

function stream(req, res, path, s, status = 200) {
  const ext = extname(path).toLowerCase();
  const headers = {
    "Content-Type": MIME[ext] || "application/octet-stream",
    "Content-Length": s.size,
    // 带哈希的 assets/ 可长缓存；其余（index.html、apk、收款码）不缓存
    "Cache-Control": path.includes(`${sep}assets${sep}`)
      ? "public, max-age=31536000, immutable"
      : "no-cache",
    "X-Content-Type-Options": "nosniff",
  };
  res.writeHead(status, headers);
  if (req.method === "HEAD") { res.end(); return; }
  createReadStream(path).on("error", () => res.destroy()).pipe(res);
}

// Pages _redirects：逐请求按 mtime 缓存读取，只精确匹配整路径（不支持通配）
let redirectsMtime = 0;
let redirectsMap = new Map();
function redirects() {
  const f = join(DIST, "_redirects");
  try {
    const m = statSync(f).mtimeMs;
    if (m !== redirectsMtime) {
      const map = new Map();
      for (const line of readFileSync(f, "utf8").split("\n")) {
        const m2 = /^(\S+)\s+(\S+)\s+(\d{3})/.exec(line.trim());
        if (m2) map.set(m2[1], { url: m2[2], status: Number(m2[3]) });
      }
      redirectsMtime = m;
      redirectsMap = map;
    }
  } catch { /* 无 _redirects */ }
  return redirectsMap;
}

async function handle(req, res) {
  try {
    if (req.method !== "GET" && req.method !== "HEAD") {
      return send(res, 405, { Allow: "GET, HEAD" }, "Method Not Allowed");
    }
    const url = new URL(req.url || "/", "http://localhost");
    const redir = redirects().get(url.pathname);
    if (redir) {
      return send(res, redir.status, { Location: redir.url }, "Redirect");
    }
    // 归一化后必须仍落在 dist 内（挡 .. 穿越）
    const path = normalize(decodeURIComponent(url.pathname)).replace(/^([/\\])+/, "");
    let full = resolve(join(DIST, path));
    if (full !== DIST && !full.startsWith(DIST + sep)) {
      return send(res, 403, {}, "Forbidden");
    }
    let s = await fileInfo(full);
    if (s === null) {
      // 无扩展名路径：先查 dist/<路径>.html（Pages 同款扁平页面），再查目录 index.html
      const flat = full + ".html";
      const sf = await fileInfo(flat);
      if (sf !== null) { full = flat; s = sf; }
    }
    if (s === null) {
      const indexed = join(full, "index.html");
      const si = await fileInfo(indexed);
      if (si !== null) { full = indexed; s = si; }
    }
    if (s === null) {
      // 未知地址：以 404 状态返回预渲染的“找不到页面”，前端路由照常接管
      full = join(DIST, "404.html");
      s = await fileInfo(full);
      if (s === null) {
        full = join(DIST, "index.html");
        s = await fileInfo(full);
      }
      if (s === null) return send(res, 503, {}, "dist 未构建：先运行 jrx.cmd web-build");
      return stream(req, res, full, s, 404);
    }
    stream(req, res, full, s);
  } catch (e) {
    log(`handle error: ${e && e.stack || e}`);
    send(res, 500, {}, "Internal Server Error");
  }
}

// IPv4 + IPv6 双回环：cloudflared 回源 localhost 时可能先解析 ::1
const v4 = createServer(handle).listen(PORT, "127.0.0.1", () =>
  log(`listening http://127.0.0.1:${PORT}（目录 ${DIST}）`));
v4.on("error", (e) => { log(`IPv4 listen failed: ${e.message}`); process.exit(1); });
try {
  const v6 = createServer(handle).listen(PORT, "::1", () => log(`listening http://[::1]:${PORT}`));
  v6.on("error", (e) => log(`IPv6 listen skipped: ${e.message}`));  // 无 IPv6 不致命
} catch (e) {
  log(`IPv6 listen skipped: ${e.message}`);
}
