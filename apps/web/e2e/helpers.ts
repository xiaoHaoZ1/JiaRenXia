// E2E 公共工具：截图路径。

import { mkdirSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import type { Page, TestInfo } from "@playwright/test";

export const ROOT = join(fileURLToPath(import.meta.url), "..", "..", "..", "..");

export function shotDir(testInfo: TestInfo): string {
  const dir = join(ROOT, "runtime", "e2e", "screenshots", testInfo.project.name);
  mkdirSync(dir, { recursive: true });
  return dir;
}

export async function shot(page: Page, testInfo: TestInfo, name: string) {
  await page.screenshot({
    path: join(shotDir(testInfo), `${name}.png`),
    fullPage: false,
  });
}
