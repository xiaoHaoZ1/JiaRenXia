// 站点 SEO 元数据：预渲染脚本（scripts/prerender.mjs）、客户端路由切换（head.ts）
// 与 sitemap.xml 共用这一张表。改页面标题或搜索摘要只改这里。
//
// 标题中文 30 字以内、摘要 70~150 字，超出会被搜索引擎截断。

import { SITE } from "./site";

/** 线上站点地址，用于 canonical、og:url 与 sitemap。结尾不带斜杠。 */
export const SITE_URL = "https://jiarenxia.com";

export const SITE_NAME = "家人侠";

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

/** 可被搜索引擎收录的页面，顺序即 sitemap 顺序。 */
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
  "/extensions": {
    title: "小侠家族 - 家人侠",
    description:
      "家人侠作者的免费小应用集合。「守护侠」：长辈手机无人应答时自动联系紧急联系人；" +
      "「朋友侠」：记住朋友的生日与近况，农历公历都行、到点提醒；" +
      "「饭桶侠」：转盘帮你决定午晚夜宵吃什么，可自己加菜改菜。全都免费、不联网、不用注册。",
    priority: "0.6",
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

/** 不在 PAGES 里的路径（找不到页面）：给搜索引擎 noindex，避免产生软 404。 */
export const NOT_FOUND_SEO: PageSeo = {
  title: "找不到页面 - 家人侠",
  description: "这个地址下没有内容，回家人侠首页看看吧。",
  priority: "0",
  changefreq: "yearly",
};

/** 去掉结尾斜杠（"/" 除外），让 "/help" 与 "/help/" 得到同一份元数据。 */
export function normalizePath(path: string): string {
  const p = path.replace(/\/+$/, "");
  return p === "" ? "/" : p;
}

export function seoFor(path: string): PageSeo {
  return PAGES[normalizePath(path)] ?? NOT_FOUND_SEO;
}

/** 该路径是否允许被收录。 */
export function isIndexable(path: string): boolean {
  return normalizePath(path) in PAGES;
}

export function canonicalFor(path: string): string {
  const p = normalizePath(path);
  return p === "/" ? `${SITE_URL}/` : SITE_URL + p;
}

/** 结构化数据（JSON-LD）：帮助搜索引擎把"家人侠"识别成一个 App 而不是影视梗。 */
export function jsonLdFor(path: string): unknown[] {
  const p = normalizePath(path);
  if (!(p in PAGES)) return [];
  const website = {
    "@context": "https://schema.org",
    "@type": "WebSite",
    name: SITE_NAME,
    alternateName: ["jiarenxia", "家人侠App", "家人侠 App"],
    url: `${SITE_URL}/`,
    inLanguage: "zh-CN",
  };
  if (p !== "/" && p !== "/download") return [website];
  return [
    website,
    {
      "@context": "https://schema.org",
      "@type": "SoftwareApplication",
      name: SITE_NAME,
      alternateName: "jiarenxia",
      applicationCategory: "LifestyleApplication",
      operatingSystem: "Android 8.0+",
      url: `${SITE_URL}/`,
      downloadUrl: `${SITE_URL}/download`,
      installUrl: SITE.apkDownloadUrl || `${SITE_URL}/jiarenxia.apk`,
      inLanguage: "zh-CN",
      description: PAGES["/"].description,
      offers: { "@type": "Offer", price: "0", priceCurrency: "CNY" },
    },
  ];
}
