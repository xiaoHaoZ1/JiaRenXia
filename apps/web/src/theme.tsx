// 站点主题切换：「家书」（默认，与 App 一致）与「经典」。
// 选择写入 localStorage("jrx-theme") 并体现在 <html data-theme> 上；
// index.html 里的内联脚本在首帧前读回，避免经典用户看到家书闪屏。

import { useEffect, useState } from "react";
import type { Lang } from "./i18n";

export type SiteTheme = "jiashu" | "classic";
const THEME_KEY = "jrx-theme";

/** 当前生效的主题：<html data-theme="classic"> 即经典，其余（含未设置）为家书。 */
function currentTheme(): SiteTheme {
  if (typeof document === "undefined") return "jiashu";
  return document.documentElement.dataset.theme === "classic" ? "classic" : "jiashu";
}

function applyTheme(t: SiteTheme) {
  document.documentElement.dataset.theme = t;
  try {
    localStorage.setItem(THEME_KEY, t);
  } catch {
    // 隐私模式等写不进去时只换当前页外观
  }
}

const T = {
  zh: { group: "主题", classic: "经典", jiashu: "家书" },
  en: { group: "Theme", classic: "Classic", jiashu: "Letter" },
} as const;

/** 页头主题切换：选中项的样式本身就是当前主题的活示例（胶囊底 / 朱砂下划线）。 */
export function ThemeSwitch({ lang }: { lang: Lang }) {
  const t = T[lang];
  // 预渲染与 hydration 首帧都按默认「家书」渲染，挂载后再校正，避免属性不一致
  const [theme, setTheme] = useState<SiteTheme>("jiashu");
  useEffect(() => setTheme(currentTheme()), []);
  const pick = (v: SiteTheme) => () => {
    setTheme(v);
    applyTheme(v);
  };
  return (
    <span className="theme-switch" role="group" aria-label={t.group}>
      <button
        type="button"
        className={theme === "classic" ? "on" : ""}
        aria-pressed={theme === "classic"}
        onClick={pick("classic")}
      >
        {t.classic}
      </button>
      <button
        type="button"
        className={theme === "jiashu" ? "on" : ""}
        aria-pressed={theme === "jiashu"}
        onClick={pick("jiashu")}
      >
        {t.jiashu}
      </button>
    </span>
  );
}
