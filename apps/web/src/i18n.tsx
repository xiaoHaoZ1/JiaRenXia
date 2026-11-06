// 站点双语（中文默认 / 英文 /en 前缀）：语言上下文与路径工具。
// 约定：中文路径保持不变；英文是同一棵路由树挂在 /en 下，英文页输出
// <html lang="en">、英文 SEO 与 canonical，双语页面互指 hreflang。

import { createContext, useContext } from "react";

export type Lang = "zh" | "en";

export const LangContext = createContext<Lang>("zh");

/** 当前语言（由 PublicLayout 按路由前缀提供）。 */
export function useLang(): Lang {
  return useContext(LangContext);
}

/** 生成当前语言下的站内路径：zh 原样；en 加 /en 前缀（"/" → "/en"）。 */
export function localizedPath(lang: Lang, to: string): string {
  if (lang === "zh") return to;
  return to === "/" ? "/en" : `/en${to}`;
}

/** 站内链接用：自动按当前语言补前缀，页面里写 to="/download" 即可。 */
export function useTo(): (to: string) => string {
  const lang = useLang();
  return (to: string) => localizedPath(lang, to);
}

/** 从 URL 还原语言与去掉语言前缀的站内路径（seo、语言切换用）。 */
export function splitLangPath(pathname: string): { lang: Lang; rest: string } {
  if (pathname === "/en" || pathname === "/en/") return { lang: "en", rest: "/" };
  if (pathname.startsWith("/en/")) {
    return { lang: "en", rest: pathname.slice(3) };
  }
  return { lang: "zh", rest: pathname };
}

/** 同一页面另一种语言的地址（语言切换器用）。 */
export function alternateLangPath(pathname: string): string {
  const { lang, rest } = splitLangPath(pathname);
  return localizedPath(lang === "zh" ? "en" : "zh", rest);
}
