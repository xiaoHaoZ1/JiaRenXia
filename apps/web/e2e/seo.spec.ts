import { expect, test } from "@playwright/test";
import { canonicalFor, PAGES, SITE_URL } from "../src/seo";

// 搜索引擎抓取视角的自检。
// 下面大部分用例走 request（纯 HTTP，不执行 JavaScript），正是百度、Bing 这类
// JS 渲染能力弱的抓取方看到的东西——页面必须在静态 HTML 里就带着标题、摘要和正文。

test("robots.txt 放行全站并指向 sitemap", async ({ request }) => {
  const res = await request.get("/robots.txt");
  expect(res.status()).toBe(200);
  expect(res.headers()["content-type"]).toContain("text/plain");
  const body = await res.text();
  expect(body).toContain(`Sitemap: ${SITE_URL}/sitemap.xml`);
  // 不能出现整站屏蔽
  expect(body).not.toMatch(/^\s*Disallow:\s*\/\s*$/m);
});

test("sitemap.xml 列出全部可收录页面", async ({ request }) => {
  const res = await request.get("/sitemap.xml");
  expect(res.status()).toBe(200);
  expect(res.headers()["content-type"]).toContain("xml");
  const xml = await res.text();
  for (const path of Object.keys(PAGES)) {
    expect(xml, path).toContain(`<loc>${canonicalFor(path)}</loc>`);
  }
});

test("每个页面的静态 HTML 都带标题、摘要、canonical 与正文", async ({ request }) => {
  for (const [path, seo] of Object.entries(PAGES)) {
    const res = await request.get(path);
    expect(res.status(), path).toBe(200);
    const html = await res.text();
    expect(html, path).toContain(`<title>${seo.title}</title>`);
    expect(html, path).toContain(`<meta name="description" content="${seo.description}" />`);
    expect(html, path).toContain(`<link rel="canonical" href="${canonicalFor(path)}" />`);
    expect(html, path).toContain('<meta name="robots" content="index,follow" />');
    // 空壳 SPA 的特征；出现即说明预渲染没生效
    expect(html, path).not.toContain('<div id="root"></div>');
    // 导航与页脚属于每页都有的正文
    expect(html, path).toContain("家人侠");
  }
});

test("首页静态 HTML 含结构化数据与首屏文案", async ({ request }) => {
  const html = await (await request.get("/")).text();
  expect(html).toContain('<script type="application/ld+json">');
  expect(html).toContain("SoftwareApplication");
  expect(html).toContain("认全家族所有人");
});

test("分享卡片与站点图标齐全", async ({ request }) => {
  const html = await (await request.get("/")).text();
  expect(html).toContain(`<meta property="og:image" content="${SITE_URL}/og-cover.png" />`);
  expect(html).toContain('<meta name="twitter:card" content="summary_large_image" />');
  for (const [path, type] of [
    ["/og-cover.png", "image/png"],
    ["/apple-touch-icon.png", "image/png"],
    ["/favicon.ico", "image/x-icon"],
    ["/favicon.svg", "image/svg+xml"],
  ] as const) {
    const res = await request.get(path);
    expect(res.status(), path).toBe(200);
    expect(res.headers()["content-type"], path).toContain(type);
  }
});

test("未知地址返回 404 且标记 noindex", async ({ request }) => {
  const res = await request.get("/this-page-does-not-exist");
  expect(res.status()).toBe(404);
  const html = await res.text();
  expect(html).toContain('<meta name="robots" content="noindex,follow" />');
  expect(html).toContain("找不到这个页面");
});

test("站内跳转会更新标题，且页面只有一个 title", async ({ page, isMobile }) => {
  await page.goto("/");
  await expect(page).toHaveTitle(PAGES["/"].title);
  // 手机上导航折叠在菜单里，要先展开
  if (isMobile) await page.getByRole("button", { name: "展开菜单" }).click();
  await page
    .getByRole("navigation", { name: "主导航" })
    .getByRole("link", { name: "下载", exact: true })
    .click();
  await expect(page).toHaveTitle(PAGES["/download"].title);
  expect(await page.locator("head > title").count()).toBe(1);
  expect(await page.locator('head > meta[name="description"]').count()).toBe(1);
});

test("预渲染页面能正常 hydrate，控制台没有报错", async ({ page }) => {
  const errors: string[] = [];
  page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
  page.on("pageerror", (e) => errors.push(String(e)));
  await page.goto("/");
  await expect(page.getByRole("heading", { name: /认全家族所有人/, level: 1 })).toBeVisible();
  await page.goto("/help");
  await expect(page.getByRole("heading", { name: "帮助", exact: true })).toBeVisible();
  expect(errors).toEqual([]);
});
