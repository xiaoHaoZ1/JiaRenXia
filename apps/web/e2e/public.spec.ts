import { expect, test } from "@playwright/test";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { ROOT, shot } from "./helpers";

// 公共页面、导航、支持作者弹窗、直接访问、未知页面。

test("公共页面与导航", async ({ page }, testInfo) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: /记住该惦记的事/, level: 1 })).toBeVisible();
  await shot(page, testInfo, "home");

  for (const [path, heading] of [
    ["/download", "下载"],
    ["/extensions", "小侠家族"],
    ["/help", "帮助"],
    ["/privacy", "隐私政策"],
    ["/terms", "用户协议"],
  ] as const) {
    await page.goto(path);
    await expect(page.getByRole("heading", { name: heading }).first()).toBeVisible();
    await shot(page, testInfo, `public-${path.slice(1)}`);
  }

  // 点击导航回家
  await page.getByRole("link", { name: "家人侠", exact: true }).first().click();
  await expect(page).toHaveURL(/\/$/);
});

test("导航「支持作者」点开是收款码", async ({ page, isMobile }, testInfo) => {
  await page.goto("/");
  const nav = page.getByRole("navigation", { name: "主导航" });
  if (isMobile) {
    // 手机端折叠菜单里直接列出
    await page.getByRole("button", { name: "展开菜单" }).click();
  }
  const support = nav.getByRole("button", { name: "支持作者" });
  await expect(support).toBeVisible();
  await support.click();
  const dialog = page.getByRole("dialog", { name: "支持作者" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("img", { name: /收款码/ })).toBeVisible();
  await shot(page, testInfo, "donate-modal");
  await dialog.getByRole("button", { name: "关闭" }).click();
  await expect(dialog).toBeHidden();
});

test("手机菜单折叠/展开", async ({ page, isMobile }, testInfo) => {
  test.skip(!isMobile, "仅手机项目");
  await page.goto("/");
  const btn = page.getByRole("button", { name: "展开菜单" });
  await expect(btn).toBeVisible();
  await btn.click();
  const nav = page.getByRole("navigation", { name: "主导航" });
  await expect(nav.getByRole("button", { name: "支持作者" })).toBeVisible();
  await shot(page, testInfo, "mobile-menu-open");
  await nav.getByRole("link", { name: "帮助", exact: true }).click();
  await expect(page.getByRole("heading", { name: "帮助", exact: true })).toBeVisible();
});

test("桌面导航不显示折叠按钮", async ({ page, isMobile }) => {
  test.skip(Boolean(isMobile), "仅桌面项目");
  await page.goto("/");
  await expect(page.getByRole("button", { name: "展开菜单" })).toBeHidden();
});

test("直接访问与刷新", async ({ page }) => {
  for (const path of ["/", "/download", "/help"] as const) {
    const resp = await page.goto(path);
    expect(resp?.status()).toBe(200);
    await page.reload();
    expect(resp?.status()).toBe(200);
  }
});

test("未知页面显示找不到页面", async ({ page }, testInfo) => {
  // 未知地址由 serve.mjs 以 404 状态返回预渲染的 404.html，前端路由照常接管
  // （状态码本身在 e2e/seo.spec.ts 里断言）
  await page.goto("/no-such-page");
  await expect(page.getByRole("heading", { name: "找不到这个页面" })).toBeVisible();
  await shot(page, testInfo, "not-found");
});

test("下载地址与版本清单走 dl.jiarenxia.com", async ({ page, request }) => {
  // 下载页按钮 → 版本化地址 + src=web
  await page.goto("/download");
  const href = await page
    .getByRole("link", { name: /下载/ })
    .filter({ hasText: /APK|安装包|下载安装/ })
    .first()
    .getAttribute("href");
  expect(href ?? (await page.locator("a[href*='dl.jiarenxia.com']").first().getAttribute("href")))
    .toMatch(/^https:\/\/dl\.jiarenxia\.com\/jiarenxia-\d+\.\d+\.\d+\.apk\?src=web$/);

  // App 端更新清单 → dl 域名 + src=update
  const resp = await request.get("/jiarenxia-version.json");
  expect(resp.ok()).toBeTruthy();
  const manifest = await resp.json();
  expect(manifest.url).toMatch(/^https:\/\/dl\.jiarenxia\.com\/jiarenxia-/);
  expect(manifest.url).toContain("src=update");

  // Pages 附件随构建生成
  expect(existsSync(join(ROOT, "apps", "web", "dist", "_redirects"))).toBeTruthy();
  expect(existsSync(join(ROOT, "apps", "web", "dist", "_headers"))).toBeTruthy();
});

test("旧安装包路径 302 到下载服务", async ({ request }) => {
  const resp = await request.get("/jiarenxia.apk", { maxRedirects: 0 });
  expect(resp.status()).toBe(302);
  expect(resp.headers()["location"]).toMatch(
    /^https:\/\/dl\.jiarenxia\.com\/jiarenxia-\d+\.\d+\.\d+\.apk\?src=legacy$/
  );
});

test("帮助页有称谓演示", async ({ page }) => {
  await page.goto("/help");
  await expect(page.getByRole("heading", { name: /称谓怎么算/ })).toBeVisible();
  await page.getByRole("button", { name: "妻子的父亲" }).click();
  await expect(page.getByText("岳父")).toBeVisible();
});

test("系统深色模式下官网仍为浅色", async ({ page }) => {
  await page.emulateMedia({ colorScheme: "dark" });
  await page.goto("/");
  const bg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  expect(bg).toBe("rgb(253, 248, 242)");
});

test("页脚有源代码链接", async ({ page }) => {
  await page.goto("/");
  const link = page.getByRole("link", { name: "源代码" });
  await expect(link).toHaveAttribute("href", "https://github.com/xiaoHaoZ1/JiaRenXia");
});
