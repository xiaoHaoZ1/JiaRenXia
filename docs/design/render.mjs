// 把设计稿 HTML 渲染成 PNG（本机 Edge，与官网自检同一个浏览器）。
//
// 用法（产品根目录下）：node docs\design\render.mjs
// 产物写到不入库的 runtime\design\（仓库里只留 HTML/CSS 源文件，图随时可重出）。
// Playwright 装在 apps/web，这里用 createRequire 指过去，本脚本不需要自己的依赖。
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const HERE = resolve(fileURLToPath(import.meta.url), "..");
const ROOT = resolve(HERE, "..", "..");
const OUT = join(ROOT, "runtime", "design");

// Playwright 只在主工作区 apps/web 装过（worktree 里没有 node_modules）。
// 在 worktree（<仓库根>/worktrees/<名>）里运行时，向上两级回退到主工作区。
function loadChromium() {
  const mainRoot = resolve(ROOT, "..", "..");
  for (const base of [ROOT, mainRoot]) {
    try {
      return createRequire(join(base, "apps", "web", "package.json"))(
        "@playwright/test"
      ).chromium;
    } catch (e) {
      if (e.code !== "MODULE_NOT_FOUND") throw e;
    }
  }
  throw new Error("找不到 @playwright/test：先在 apps/web 跑一次 npm ci")
}

const chromium = loadChromium();

mkdirSync(OUT, { recursive: true });
const icons = readFileSync(join(HERE, "icons.html"), "utf8");
const css = readFileSync(join(HERE, "base.css"), "utf8");

const browser = await chromium.launch({ channel: "msedge" });
const page = await browser.newPage({ deviceScaleFactor: 2 });

for (const [name, width] of [["main", 1700], ["mine", 1320]]) {
  const html = readFileSync(join(HERE, `${name}.html`), "utf8")
    .replace("<!--ICONS-->", icons)
    .replace('<link rel="stylesheet" href="base.css">', `<style>${css}</style>`);
  await page.setViewportSize({ width, height: 1000 });
  await page.setContent(html, { waitUntil: "load" });
  const el = await page.$(".sheet");
  writeFileSync(join(OUT, `${name}.png`), await el.screenshot({ type: "png" }));
  const box = await el.boundingBox();
  console.log(`写入 ${join(OUT, name)}.png  ${Math.round(box.width)}×${Math.round(box.height)} CSS px`);
}

await browser.close();
