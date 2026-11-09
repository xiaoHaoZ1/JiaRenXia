// 站点 SEO 元数据：预渲染脚本（scripts/prerender.mjs）、客户端路由切换（head.ts）
// 与 sitemap.xml 共用这一张表。改页面标题或搜索摘要只改这里。
//
// 标题中文 30 字以内、摘要 70~150 字，超出会被搜索引擎截断。
// 英文页与中文页一一同路径（/en 前缀），各自有独立的标题与摘要。

import { SITE } from "./site";
import { localizedPath, splitLangPath, type Lang } from "./i18n";

/** 线上站点地址，用于 canonical、og:url 与 sitemap。结尾不带斜杠。 */
export const SITE_URL = "https://jiarenxia.com";

export const SITE_NAME = "家人侠";
export const SITE_NAME_EN = "JiaRenXia";

/** 分享卡片封面（知乎、微信、QQ、Twitter 等抓 og:image）。
 *  由 scripts/brand-images.mjs 从 scripts/og-cover.html 渲染，产物已入库。 */
export const OG_IMAGE = {
  path: "/og-cover.png",
  width: 1200,
  height: 630,
  alt: "家人侠 — 认全家族所有人，记住该惦记的事。",
};

export type PageSeo = {
  /** 标签页与搜索结果标题 */
  title: string;
  /** 搜索结果摘要 */
  description: string;
  /** sitemap 中的权重与更新频率 */
  priority: string;
  changefreq: string;
};

/** 可被搜索引擎收录的中文页面，顺序即 sitemap 顺序。 */
export const PAGES: Record<string, PageSeo> = {
  "/": {
    title: "家人侠 — 认全家族所有人，记住该惦记的事",
    description:
      "家人侠是一款完全免费的安卓应用：录入家人关系生成关系图，称谓一看就懂、分享给家人换成他的叫法；" +
      "生日、纪念日、家事、联系提醒按时到点。" +
      "所有资料只保存在自己手机里，默认不联网、不上传、不用注册。",
    priority: "1.0",
    changefreq: "weekly",
  },
  "/download": {
    title: "免费下载 Android 安装包 - 家人侠",
    description:
      "家人侠安卓版安装包免费下载，页面附文件大小与 SHA-256 校验值，可自行核对。" +
      "支持 Android 8.0 及以上，完全免费、无广告、无内购、不用注册。",
    priority: "0.9",
    changefreq: "weekly",
  },
  "/shouhuxia": {
    title: "守护侠 — 手机太久没人动，它会替你喊家里人",
    description:
      "守护侠是给独居长辈手机用的免费安卓应用：锁屏太久没人用，先响铃请对方确认，" +
      "没人应答就群发短信、按顺序拨打紧急联系人，还能每天发一条平安短信。" +
      "不联网、不注册，设置只存在这台手机里。",
    priority: "0.7",
    changefreq: "monthly",
  },
  "/help": {
    title: "使用帮助 - 家人侠",
    description:
      "家人侠图文使用教程：每屏都配了 App 实拍截图，一步步教老人和家用——加家人、看资料打电话、" +
      "生日与家事提醒、亲戚关系图、备份换机，附常见问题与作者联系方式。",
    priority: "0.8",
    changefreq: "monthly",
  },
  "/privacy": {
    title: "隐私政策 - 家人侠",
    description:
      "家人侠隐私政策：家庭资料只保存在手机本地，App 默认不联网、不上传、不用注册；" +
      "本官网是纯静态页面，不收集数据、不使用 Cookie、不加载第三方统计。",
    priority: "0.3",
    changefreq: "yearly",
  },
  "/terms": {
    title: "用户协议 - 家人侠",
    description:
      "家人侠用户协议：全部功能完全免费，没有广告也没有内购；打赏完全自愿，不影响任何功能的使用。",
    priority: "0.3",
    changefreq: "yearly",
  },
};

/** 英文页 SEO：与 PAGES 同路径一一对应。 */
export const PAGES_EN: Record<string, PageSeo> = {
  "/": {
    title: "JiaRenXia — Know everyone in your family, remember what matters",
    description:
      "JiaRenXia is a completely free Android app: record family relationships to build a family graph " +
      "with proper Chinese kinship titles, share the archive so relatives see their own view, and get " +
      "timely reminders for birthdays, anniversaries and family matters. Everything stays on your phone " +
      "— offline by default, no uploads, no sign-up.",
    priority: "1.0",
    changefreq: "weekly",
  },
  "/download": {
    title: "Free Android APK download - JiaRenXia",
    description:
      "Download the free JiaRenXia Android package; file size and SHA-256 checksum are listed for " +
      "verification. Requires Android 8.0+. Completely free, no ads, no in-app purchases, no sign-up.",
    priority: "0.9",
    changefreq: "weekly",
  },
  "/shouhuxia": {
    title: "ShouHuXia — calls you when an elder's phone sits still too long",
    description:
      "ShouHuXia is a free Android app for an elder who lives alone: if the phone stays untouched " +
      "too long after lock, it rings to ask for confirmation; with no answer it texts all emergency " +
      "contacts and dials them in order, plus an optional daily check-in text. Offline, no sign-up.",
    priority: "0.7",
    changefreq: "monthly",
  },
  "/help": {
    title: "User guide - JiaRenXia",
    description:
      "Illustrated JiaRenXia tutorial with real app screenshots: add family members, view profiles and " +
      "call them, birthday and family-matter reminders, the relationship graph, backup and restore, " +
      "plus FAQ and contact.",
    priority: "0.8",
    changefreq: "monthly",
  },
  "/privacy": {
    title: "Privacy policy - JiaRenXia",
    description:
      "JiaRenXia privacy policy: family data is stored only on your phone; the app is offline by " +
      "default with no uploads and no sign-up. This site is purely static — no data collection, " +
      "no cookies, no third-party analytics.",
    priority: "0.3",
    changefreq: "yearly",
  },
  "/terms": {
    title: "Terms of use - JiaRenXia",
    description:
      "JiaRenXia terms of use: every feature is completely free with no ads and no in-app purchases; " +
      "tipping the author is entirely optional and unlocks nothing.",
    priority: "0.3",
    changefreq: "yearly",
  },
};

const NOT_FOUND_ZH: PageSeo = {
  title: "找不到页面 - 家人侠",
  description: "这个地址下没有内容，回家人侠首页看看吧。",
  priority: "0",
  changefreq: "yearly",
};

const NOT_FOUND_EN: PageSeo = {
  title: "Page not found - JiaRenXia",
  description: "There is nothing at this address. Head back to the JiaRenXia home page.",
  priority: "0",
  changefreq: "yearly",
};

/** 不在 PAGES 里的路径（找不到页面）：给搜索引擎 noindex，避免产生软 404。 */
export const NOT_FOUND_SEO: PageSeo = NOT_FOUND_ZH;

/** 去掉结尾斜杠（"/" 除外），让 "/help" 与 "/help/" 得到同一份元数据。 */
export function normalizePath(path: string): string {
  const p = path.replace(/\/+$/, "");
  return p === "" ? "/" : p;
}

function tableFor(lang: Lang): Record<string, PageSeo> {
  return lang === "en" ? PAGES_EN : PAGES;
}

export function seoFor(path: string): PageSeo {
  const { lang, rest } = splitLangPath(normalizePath(path));
  return tableFor(lang)[rest] ?? (lang === "en" ? NOT_FOUND_EN : NOT_FOUND_ZH);
}

/** 该路径是否允许被收录。 */
export function isIndexable(path: string): boolean {
  const { lang, rest } = splitLangPath(normalizePath(path));
  return rest in tableFor(lang);
}

export function canonicalFor(path: string): string {
  const p = normalizePath(path);
  return p === "/" ? `${SITE_URL}/` : SITE_URL + p;
}

/** 中英文互指 + x-default（x-default 指向中文版）。仅可收录页面调用。 */
export function hreflangFor(path: string): { hreflang: string; href: string }[] {
  const { lang, rest } = splitLangPath(normalizePath(path));
  const zh = localizedPath("zh", rest);
  const en = localizedPath("en", rest);
  const zhHref = zh === "/" ? `${SITE_URL}/` : SITE_URL + zh;
  const enHref = SITE_URL + en;
  return lang === "en"
    ? [
        { hreflang: "en", href: enHref },
        { hreflang: "zh-CN", href: zhHref },
        { hreflang: "x-default", href: zhHref },
      ]
    : [
        { hreflang: "zh-CN", href: zhHref },
        { hreflang: "en", href: enHref },
        { hreflang: "x-default", href: zhHref },
      ];
}

/** 页面语言（预渲染与 head.ts 写 <html lang> 用）。 */
export function langOf(path: string): Lang {
  return splitLangPath(normalizePath(path)).lang;
}

/** 全部可收录地址（zh + en），预渲染与 sitemap 遍历用。 */
export function allIndexablePaths(): string[] {
  return [
    ...Object.keys(PAGES),
    ...Object.keys(PAGES_EN).map((p) => localizedPath("en", p)),
  ];
}

/** 结构化数据（JSON-LD）：帮助搜索引擎把"家人侠"识别成一个 App 而不是影视梗。 */
export function jsonLdFor(path: string): unknown[] {
  const { lang, rest } = splitLangPath(normalizePath(path));
  if (!(rest in tableFor(lang))) return [];
  const website = {
    "@context": "https://schema.org",
    "@type": "WebSite",
    name: SITE_NAME,
    alternateName: ["jiarenxia", "家人侠App", "家人侠 App", "JiaRenXia"],
    url: `${SITE_URL}/`,
    inLanguage: lang === "en" ? "en" : "zh-CN",
  };
  if (rest !== "/" && rest !== "/download") return [website];
  const desc = tableFor(lang)["/"].description;
  return [
    website,
    {
      "@context": "https://schema.org",
      "@type": "SoftwareApplication",
      name: SITE_NAME,
      alternateName: ["jiarenxia", "JiaRenXia"],
      applicationCategory: "LifestyleApplication",
      operatingSystem: "Android 8.0+",
      url: `${SITE_URL}/`,
      downloadUrl: lang === "en" ? `${SITE_URL}/en/download` : `${SITE_URL}/download`,
      installUrl: SITE.apkDownloadUrl || `${SITE_URL}/jiarenxia.apk`,
      inLanguage: lang === "en" ? "en" : "zh-CN",
      description: desc,
      offers: { "@type": "Offer", price: "0", priceCurrency: "CNY" },
    },
  ];
}
