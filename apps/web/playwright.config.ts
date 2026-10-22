import { defineConfig } from "@playwright/test";

// 两个 Edge 项目：桌面 1366×900、手机 390×844；打本机 serve.mjs（和线上同一个静态
// 服务器，这样预渲染页面、404 状态码等行为与生产一致）。
export default defineConfig({
  testDir: "./e2e",
  timeout: 120_000,
  retries: 0,
  workers: 1,
  reporter: [["list"]],
  use: {
    baseURL: "http://127.0.0.1:4173",
    channel: "msedge",
    screenshot: "off",
    trace: "off",
  },
  webServer: {
    command: "npm run build && npm run preview",
    url: "http://127.0.0.1:4173",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  projects: [
    {
      name: "desktop-edge",
      use: { channel: "msedge", viewport: { width: 1366, height: 900 } },
    },
    {
      name: "mobile-edge",
      use: {
        channel: "msedge",
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
        deviceScaleFactor: 3,
      },
    },
  ],
});
