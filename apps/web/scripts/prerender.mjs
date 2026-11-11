// 预渲染：把每个路由渲染成带正文的静态 HTML，并生成 sitemap.xml。
//
// 为什么需要：站点是前端渲染的 SPA，直接抓取只能拿到一个空的 <div id="root">。
// Google 还能执行 JS，百度和 Bing 的 JS 渲染能力很弱，等于什么都没抓到。构建时
// 先把 HTML 渲染好，三家搜索引擎抓到的就是完整正文。
//
// 由 npm run build 在两次 vite build（浏览器产物 dist、预渲染产物 dist-ssr）之后调用。
// 页面标题与摘要来自 src/seo.ts，是唯一事实来源。

import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = resolve(fileURLToPath(import.meta.url), "..");
const WEB = resolve(HERE, "..");
const DIST = join(WEB, "dist");
const SSR = join(WEB, "dist-ssr");

const entry = join(SSR, "entry-server.js");
if (!existsSync(entry)) {
  console.error(`预渲染失败：缺少 ${entry}，请先运行 vite build --ssr src/entry-server.tsx`);
  process.exit(1);
}
const {
  render, OG_IMAGE, SITE_NAME, SITE_URL, SITE, SHOUHUXIA, APK_BETA, APK_DEMO,
  allIndexablePaths, canonicalFor, hreflangFor, isIndexable, jsonLdFor, langOf, seoFor,
} = await import(pathToFileURL(entry).href);

const template = readFileSync(join(DIST, "index.html"), "utf8");

/** 供写进 HTML 属性的转义。 */
function esc(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function headFor(path) {
  const seo = seoFor(path);
  const url = canonicalFor(path);
  const lang = langOf(path);
  const tags = [
    `<title>${esc(seo.title)}</title>`,
    `<meta name="description" content="${esc(seo.description)}" />`,
    `<meta name="robots" content="${isIndexable(path) ? "index,follow" : "noindex,follow"}" />`,
    `<link rel="canonical" href="${esc(url)}" />`,
    `<meta property="og:type" content="website" />`,
    `<meta property="og:site_name" content="${esc(SITE_NAME)}" />`,
    `<meta property="og:locale" content="${lang === "en" ? "en_US" : "zh_CN"}" />`,
    `<meta property="og:title" content="${esc(seo.title)}" />`,
    `<meta property="og:description" content="${esc(seo.description)}" />`,
    `<meta property="og:url" content="${esc(url)}" />`,
    `<meta property="og:image" content="${esc(SITE_URL + OG_IMAGE.path)}" />`,
    `<meta property="og:image:width" content="${OG_IMAGE.width}" />`,
    `<meta property="og:image:height" content="${OG_IMAGE.height}" />`,
    `<meta property="og:image:alt" content="${esc(OG_IMAGE.alt)}" />`,
    `<meta name="twitter:card" content="summary_large_image" />`,
    `<meta name="twitter:title" content="${esc(seo.title)}" />`,
    `<meta name="twitter:description" content="${esc(seo.description)}" />`,
    `<meta name="twitter:image" content="${esc(SITE_URL + OG_IMAGE.path)}" />`,
  ];
  // 中英文互指 + x-default：只给可收录页面发
  if (isIndexable(path)) {
    for (const { hreflang, href } of hreflangFor(path)) {
      tags.push(`<link rel="alternate" hreflang="${hreflang}" href="${esc(href)}" />`);
    }
  }
  for (const ld of jsonLdFor(path)) {
    // JSON 里的 < 一律转义，避免提前闭合 </script>
    const json = JSON.stringify(ld).replace(/</g, "\\u003c");
    tags.push(`<script type="application/ld+json">${json}</script>`);
  }
  return tags.join("\n    ");
}

/** 把 head 与首屏 HTML 填进模板；占位注释若被构建流程去掉则退化为锚点替换。 */
function page(path) {
  const head = headFor(path);
  const body = render(path);
  let html = template.includes("<!--app-head-->")
    ? template.replace("<!--app-head-->", head)
    : template.replace("</head>", `  ${head}\n  </head>`);
  html = html.includes("<!--app-html-->")
    ? html.replace("<!--app-html-->", body)
    : html.replace('<div id="root"></div>', `<div id="root">${body}</div>`);
  // 英文页把 <html lang> 从模板的 zh-CN 换成 en
  if (langOf(path) === "en") html = html.replace('lang="zh-CN"', 'lang="en"');
  if (!html.includes("<title>")) throw new Error(`预渲染失败：${path} 没能写入 <title>`);
  if (html.includes('<div id="root"></div>')) throw new Error(`预渲染失败：${path} 首屏仍是空壳`);
  return html;
}

function write(relPath, content) {
  const full = join(DIST, relPath);
  mkdirSync(dirname(full), { recursive: true });
  writeFileSync(full, content, "utf8");
  return relPath;
}

const written = [];
for (const path of allIndexablePaths()) {
  const rel = path === "/" ? "index.html"
    : path === "/en" ? "en/index.html"
    : `${path.slice(1)}.html`;
  written.push(write(rel, page(path)));
}
// 未知路径用它兜底（serve.mjs 以 404 状态返回），内容是"找不到页面"且 noindex
written.push(write("404.html", page("/404")));

// App 端「联网检查更新」读的版本清单：与 site.ts 同源，随构建重新生成。
// serve.mjs 对非 assets 一律 no-cache，无需额外处理缓存。
if (SITE.apkVersion) {
  // GitHub 备用链接与 apkVersion 必须一致：发版漏更新会让备用链接指向不存在的包
  if (
    SITE.apkGithubUrl &&
    !SITE.apkGithubUrl.endsWith(`/v${SITE.apkVersion}/jiarenxia-${SITE.apkVersion}.apk`)
  ) {
    throw new Error(
      "预渲染失败：GitHub 备用链接与 apkVersion 不一致，发版时更新或清空 apkGithubUrl"
    );
  }
  // 测试版/演示版渠道清单：App 按 applicationId 后缀选渠道块读；
  // 顶层字段始终是正式版（旧版客户端只认顶层，不受影响）。
  const channels = {};
  if (APK_BETA.version) {
    channels.beta = {
      versionCode: APK_BETA.versionCode,
      versionName: APK_BETA.version,
      sizeBytes: APK_BETA.sizeBytes,
      sha256: APK_BETA.sha256,
      url: APK_BETA.file + "?src=update",
      notes: "",
    };
  }
  if (APK_DEMO.version) {
    // 演示版两包同 applicationId，按 BuildConfig.DEMO_ROLE 选 groom/bride 子块
    channels.demo = {
      versionCode: APK_DEMO.versionCode,
      versionName: APK_DEMO.version,
      notes: "",
      groom: { url: APK_DEMO.groom.file + "?src=update", sizeBytes: APK_DEMO.groom.sizeBytes, sha256: APK_DEMO.groom.sha256 },
      bride: { url: APK_DEMO.bride.file + "?src=update", sizeBytes: APK_DEMO.bride.sizeBytes, sha256: APK_DEMO.bride.sha256 },
    };
  }
  written.push(
    write(
      "jiarenxia-version.json",
      JSON.stringify(
        {
          versionCode: SITE.apkVersionCode,
          versionName: SITE.apkVersion,
          sizeBytes: SITE.apkSizeBytes,
          sha256: SITE.apkSha256,
          url: SITE.apkDownloadUrl + "?src=update",
          notes: SITE.apkNotes || "",
          ...(Object.keys(channels).length ? { channels } : {}),
        },
        null,
        2
      ) + "\n"
    )
  );
}

// 旧站点根目录的安装包路径 → 下载服务版本化地址（Pages _redirects）
written.push(
  write(
    "_redirects",
    [
      "/jiarenxia.apk " + SITE.apkDownloadUrl + "?src=legacy 302",
      `/${SHOUHUXIA.id}.apk ${SHOUHUXIA.file}?src=legacy 302`,
      // 旧「小侠家族」页地址 → 守护侠单品页（旧链接与旧二维码仍可用）
      "/extensions /shouhuxia 301",
      "/en/extensions /en/shouhuxia 301",
      "",
    ].join("\n")
  )
);

// sitemap.xml：只列可收录页面（中英双语，每条 url 带 xhtml:link 语言互指）
const today = new Date().toISOString().slice(0, 10);
const urls = allIndexablePaths()
  .map((path) => {
    const seo = seoFor(path);
    const alternates = hreflangFor(path)
      .map((a) => `    <xhtml:link rel="alternate" hreflang="${a.hreflang}" href="${a.href}" />`)
      .join("\n");
    return [
      "  <url>",
      `    <loc>${canonicalFor(path)}</loc>`,
      alternates,
      `    <lastmod>${today}</lastmod>`,
      `    <changefreq>${seo.changefreq}</changefreq>`,
      `    <priority>${seo.priority}</priority>`,
      "  </url>",
    ].join("\n");
  })
  .join("\n");
written.push(
  write(
    "sitemap.xml",
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
      '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n' +
      `${urls}\n</urlset>\n`
  )
);

// 预渲染出来的图片路径必须真的存在于 dist（SSR 构建与浏览器构建的资源哈希若对不上，
// 页面会静默少图，这里直接让构建失败）
const missing = new Set();
for (const rel of written) {
  if (!rel.endsWith(".html")) continue;
  const html = readFileSync(join(DIST, rel), "utf8");
  for (const ref of html.match(/\/assets\/[\w.-]+/g) ?? []) {
    if (!existsSync(join(DIST, ref))) missing.add(`${rel} → ${ref}`);
  }
}
// head 里还引用了 public/ 下不带哈希的品牌位图，少了分享卡片和站点图标会空白
for (const brand of [OG_IMAGE.path, "/favicon.svg", "/favicon.ico", "/apple-touch-icon.png"]) {
  if (!existsSync(join(DIST, brand.slice(1)))) missing.add(`head → ${brand}（跑 npm run brand-images）`);
}
if (missing.size > 0) {
  console.error("预渲染失败：引用了 dist 里不存在的资源\n  " + [...missing].join("\n  "));
  process.exit(1);
}

rmSync(SSR, { recursive: true, force: true });
console.log(`预渲染完成：${written.join("、")}`);
