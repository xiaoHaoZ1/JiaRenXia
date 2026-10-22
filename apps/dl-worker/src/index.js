// jrx-dl：dl.jiarenxia.com 下载服务。
// 从 R2 桶 jrx-download 下发按版本命名的 APK；只按「日期 + 文件 + 来源」
// 计数（D1 jrx-stats），不记录 IP、UA 或任何请求头。

const HOMEPAGE = "https://jiarenxia.com/download";
const APK_CT = "application/vnd.android.package-archive";

/** 只允许形如 jiarenxia-1.2.1.apk 的扁平键名；拒绝对象名穿越与大写。 */
export function isValidKey(key) {
  return /^[a-z0-9][a-z0-9.-]*\.apk$/.test(key);
}

/** 版本命名（xxx-1.2.1.apk）可长缓存；其余不缓存。 */
function cacheControl(key) {
  return /-\d+\.\d+\.\d+\.apk$/.test(key)
    ? "public, max-age=31536000, immutable"
    : "no-cache";
}

/** src 参数白名单：web=官网按钮、update=应用内更新、legacy=旧站点根路径跳转。 */
export function parseSrc(v) {
  return v === "web" || v === "update" || v === "legacy" ? v : "direct";
}

/** 计数条件：完整 GET 200，或从字节 0 起步的 206 分段。 */
export function shouldCount(method, status, rangeOffset) {
  if (method !== "GET") return false;
  if (status === 200) return true;
  return status === 206 && rangeOffset === 0;
}

/** UTC+8 日期（YYYY-MM-DD），统计口径按中国时区切日。 */
export function cnDay(nowMs) {
  const d = new Date(nowMs + 8 * 3600 * 1000);
  return (
    d.getUTCFullYear() + "-" +
    String(d.getUTCMonth() + 1).padStart(2, "0") + "-" +
    String(d.getUTCDate()).padStart(2, "0")
  );
}

/** R2 range → { offset, end }：含 suffix 请求（Range: bytes=-n）也要归一。 */
export function resolveRange(range, size) {
  if (!range) return null;
  const offset = range.offset !== undefined
    ? range.offset
    : size - (range.suffix || 0);
  const length = range.length !== undefined
    ? range.length
    : size - offset;
  return { offset, end: offset + length - 1 };
}

/** 从 Range 头解析起始字节（bytes=a-b → a），无则 null。 */
function rangeStart(request) {
  const m = /bytes=(\d+)-/.exec(request.headers.get("range") || "");
  return m ? Number(m[1]) : null;
}

function text(body, status) {
  return new Response(body, {
    status,
    headers: { "Content-Type": "text/plain; charset=utf-8" },
  });
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method Not Allowed", {
        status: 405,
        headers: { Allow: "GET, HEAD" },
      });
    }
    if (url.pathname === "/") {
      return Response.redirect(HOMEPAGE, 302);
    }
    const key = url.pathname.slice(1);
    if (!isValidKey(key)) return text("Not Found", 404);

    const isHead = request.method === "HEAD";
    const obj = isHead
      ? await env.BUCKET.head(key)
      : await env.BUCKET.get(key, {
          range: request.headers,
          onlyIf: request.headers,
        });
    if (obj === null) return text("Not Found", 404);

    // 条件请求未满足（If-None-Match 等）：R2 返回无 body 的对象
    if (!isHead && !("body" in obj)) {
      return new Response(null, {
        status: 304,
        headers: { ETag: obj.httpEtag },
      });
    }

    const headers = new Headers();
    obj.writeHttpMetadata(headers);
    headers.set("ETag", obj.httpEtag);
    headers.set("Content-Type", APK_CT);
    headers.set("Accept-Ranges", "bytes");
    headers.set("Content-Disposition", `attachment; filename="${key}"`);
    headers.set("X-Content-Type-Options", "nosniff");
    headers.set("Cache-Control", cacheControl(key));

    // R2 在请求没带 Range 时也会返回覆盖全文件的 range；只有客户端真的
    // 请求了分段才回 206，否则一律 200（App 内更新只接受 200）
    const range = obj.range && request.headers.has("range")
      ? resolveRange(obj.range, obj.size)
      : null;
    let status = 200;
    if (!isHead && range) {
      status = 206;
      headers.set(
        "Content-Range",
        `bytes ${range.offset}-${range.end}/${obj.size}`,
      );
      headers.set("Content-Length", String(range.end - range.offset + 1));
    } else {
      headers.set("Content-Length", String(obj.size));
    }

    // B5：完整下载或从 0 起的分段计一次；HEAD、续传分段不计。
    const off = range ? range.offset : rangeStart(request);
    if (shouldCount(request.method, status, off === null ? 0 : off)) {
      const day = cnDay(Date.now());
      const src = parseSrc(url.searchParams.get("src"));
      ctx.waitUntil(
        env.DB.prepare(
          "INSERT INTO daily(day,file,src,n) VALUES(?1,?2,?3,1) " +
            "ON CONFLICT(day,file,src) DO UPDATE SET n = n + 1",
        )
          .bind(day, key, src)
          .run()
          .catch(() => {}),
      );
    }

    return new Response(isHead ? null : obj.body, { status, headers });
  },
};
