// 生成品牌位图：分享封面与站点图标。
//
// 为什么要位图：站点原本只有 favicon.svg，而百度、360、微信、QQ 这些平台不认 SVG
// 图标，搜索结果和分享卡片里会是空白；分享链接也没有缩略图。
//
// 这是一次性脚本，不参与 npm run build——产物已入库在 public/ 下。改了设计稿
// （scripts/og-cover.html）或品牌标志之后，手动重跑：
//     npm run brand-images
// 依赖本机 Edge（与 Playwright 自检同一个浏览器），不下载额外浏览器。

import { writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { chromium } from "@playwright/test";

const HERE = resolve(fileURLToPath(import.meta.url), "..");
const PUBLIC = join(resolve(HERE, ".."), "public");

/** 标志路径取自 public/favicon.svg（砖红心形内嵌小屋）。 */
const HEART = "M54,80 C36,63 24,51 24,40 C24,31 31,24 41,24 C47,24 51,26 54,31 C57,26 61,24 67,24 C77,24 84,31 84,40 C84,51 72,63 54,80 Z";
const HOUSE = "M43,54 L54,44 L65,54 L61,54 L61,68 L47,68 L47,54 Z";

/** 单色背景（或透明）上居中的标志，用于各尺寸图标。 */
function iconHtml(size, background) {
  const pad = Math.round(size * 0.14);
  return `<!doctype html><html><head><meta charset="utf-8"><style>
    *{margin:0;padding:0}
    html,body{width:${size}px;height:${size}px}
    body{display:flex;align-items:center;justify-content:center;
         background:${background};overflow:hidden}
    svg{width:${size - pad * 2}px;height:${size - pad * 2}px}
  </style></head><body>
    <svg viewBox="22 22 64 60" xmlns="http://www.w3.org/2000/svg">
      <path fill="#b4473c" d="${HEART}"/>
      <path fill="#f7ede4" d="${HOUSE}"/>
    </svg>
  </body></html>`;
}

/** 把 PNG 原样包进 ICO 容器（Vista 起支持 PNG 格式的图标条目）。 */
function pngToIco(png, size) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0); // 保留位
  header.writeUInt16LE(1, 2); // 类型：1 = 图标
  header.writeUInt16LE(1, 4); // 条目数
  const entry = Buffer.alloc(16);
  entry.writeUInt8(size >= 256 ? 0 : size, 0); // 宽（256 记作 0）
  entry.writeUInt8(size >= 256 ? 0 : size, 1); // 高
  entry.writeUInt8(0, 2);  // 调色板颜色数
  entry.writeUInt8(0, 3);  // 保留位
  entry.writeUInt16LE(1, 4);  // 颜色平面
  entry.writeUInt16LE(32, 6); // 位深
  entry.writeUInt32LE(png.length, 8);
  entry.writeUInt32LE(22, 12); // 图像数据偏移 = 6 + 16
  return Buffer.concat([header, entry, png]);
}

const browser = await chromium.launch({ channel: "msedge" });
const page = await browser.newPage({ deviceScaleFactor: 1 });
const written = [];

async function shootHtml(html, width, height, omitBackground) {
  await page.setViewportSize({ width, height });
  await page.setContent(html, { waitUntil: "load" });
  return page.screenshot({ type: "png", omitBackground });
}

// 1) 分享封面：用独立设计稿 scripts/og-cover.html
await page.setViewportSize({ width: 1200, height: 630 });
await page.goto(pathToFileURL(join(HERE, "og-cover.html")).href, { waitUntil: "load" });
writeFileSync(join(PUBLIC, "og-cover.png"), await page.screenshot({ type: "png" }));
written.push("og-cover.png (1200×630)");

// 2) iOS 添加到主屏幕用的图标：必须不透明，系统自己裁圆角
writeFileSync(
  join(PUBLIC, "apple-touch-icon.png"),
  await shootHtml(iconHtml(180, "#fff8f2"), 180, 180, false)
);
written.push("apple-touch-icon.png (180×180)");

// 3) 位图 favicon：透明底，与 favicon.svg 保持一致
const favicon32 = await shootHtml(iconHtml(32, "transparent"), 32, 32, true);
writeFileSync(join(PUBLIC, "favicon-32.png"), favicon32);
writeFileSync(join(PUBLIC, "favicon.ico"), pngToIco(favicon32, 32));
written.push("favicon-32.png (32×32)", "favicon.ico");

await browser.close();
console.log(`品牌图生成完成（写入 apps/web/public）：\n  ${written.join("\n  ")}`);
