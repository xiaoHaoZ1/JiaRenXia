import { expect, test } from "@playwright/test";
import { shot } from "./helpers";

// 中英双语：语言切换器、英文页渲染、英文下载链接、英文互动演示。

// 导航 aria-label 与切换链接的显示文案都随语言变化：统一用 .nav-lang 类定位切换器
const NAV = /主导航|Main navigation/;

test("语言切换器在两种语言间往返", async ({ page, isMobile }) => {
  await page.goto("/");
  const nav = page.getByRole("navigation", { name: NAV });
  if (isMobile) await page.getByRole("button", { name: "展开菜单" }).click();
  await nav.locator("a.nav-lang").click();
  await expect(page).toHaveURL(/\/en\/?$/);
  await expect(
    page.getByRole("heading", { name: /remember what matters/i, level: 1 })
  ).toBeVisible();
  // 客户端切页后 <html lang> 与标题应跟着换
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page).toHaveTitle(/JiaRenXia/);

  const navEn = page.getByRole("navigation", { name: NAV });
  if (isMobile) await page.getByRole("button", { name: "Open menu" }).click();
  await navEn.locator("a.nav-lang").click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("heading", { name: /记住该惦记的事/, level: 1 })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("lang", "zh-CN");
});

test("切换器指向同页的另一语言版本", async ({ page, isMobile }) => {
  await page.goto("/download");
  const nav = page.getByRole("navigation", { name: NAV });
  if (isMobile) await page.getByRole("button", { name: "展开菜单" }).click();
  await nav.locator("a.nav-lang").click();
  await expect(page).toHaveURL(/\/en\/download$/);
});

test("英文各页直接打开且显示英文标题", async ({ page }, testInfo) => {
  await page.goto("/en");
  await expect(page.getByRole("heading", { name: /Know everyone/, level: 1 })).toBeVisible();
  await shot(page, testInfo, "en-home");

  for (const [path, heading] of [
    ["/en/download", "Download"],
    ["/en/extensions", "More apps"],
    ["/en/help", "Help"],
    ["/en/privacy", "Privacy policy"],
    ["/en/terms", "Terms of use"],
  ] as const) {
    await page.goto(path);
    await expect(
      page.getByRole("heading", { name: new RegExp(heading, "i") }).first()
    ).toBeVisible();
    await shot(page, testInfo, `en${path.replace(/\//g, "-")}`);
  }
});

test("英文下载页的安装包链接带 src=web", async ({ page }) => {
  await page.goto("/en/download");
  const link = page.locator("a[href*='dl.jiarenxia.com/jiarenxia-']").first();
  await expect(link).toHaveAttribute(
    "href",
    /^https:\/\/dl\.jiarenxia\.com\/jiarenxia-\d+\.\d+\.\d+\.apk\?src=web$/
  );
});

test("英文下载页把已知匿名来源带到安装包链接", async ({ page }) => {
  await page.goto("/en/download?src=xhs-kinship");
  const link = page.locator("a[href*='dl.jiarenxia.com/jiarenxia-']").first();
  await expect(link).toHaveAttribute("href", /\?src=xhs-kinship$/);
});

test("英文首页关系图可切换分支并查看人物称谓", async ({ page }, testInfo) => {
  await page.goto("/en");
  const demo = page.locator(".family-demo");

  const maternal = demo.getByRole("tab", { name: "Maternal side", exact: true });
  await maternal.click();
  await expect(maternal).toHaveAttribute("aria-selected", "true");
  await expect(demo.getByText("Now viewing · Maternal side")).toBeVisible();

  // 称谓保留中文：英文页 aria-label 是「拼音名，中文称谓」
  await demo.getByRole("button", { name: "Guo Zixing，姥爷" }).click();
  await expect(demo.getByText("Me → mother's father")).toBeVisible();
  await shot(page, testInfo, "en-family-graph");
});

test("英文帮助页称谓演示可用", async ({ page }) => {
  await page.goto("/en/help");
  const demo = page.locator(".kin-demo");
  await expect(demo).toBeVisible();
  // 称谓保留中文原词，附拼音
  await demo.getByRole("button", { name: "wife's father" }).click();
  await expect(demo.getByText("岳父 (yuèfù)")).toBeVisible();
});

test("英文页 hydrate 后控制台没有报错", async ({ page }) => {
  const errors: string[] = [];
  page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
  page.on("pageerror", (e) => errors.push(String(e)));
  await page.goto("/en");
  await expect(page.getByRole("heading", { name: /Know everyone/, level: 1 })).toBeVisible();
  await page.goto("/en/download");
  await expect(page.getByRole("heading", { name: /Download/ }).first()).toBeVisible();
  expect(errors).toEqual([]);
});
