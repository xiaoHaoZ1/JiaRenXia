// 客户端路由切换时同步 <head>。
//
// 首屏的 head 由预渲染（scripts/prerender.mjs）写死在静态 HTML 里，这里只改写已有
// 标签、没有才新建，避免出现两个 <title> 或两条 description。开发模式下模板里没有
// 这些标签，首次调用会补齐。

import { canonicalFor, isIndexable, OG_IMAGE, seoFor, SITE_NAME, SITE_URL } from "./seo";

function meta(attr: "name" | "property", key: string, content: string) {
  const selector = `meta[${attr}="${key}"]`;
  let el = document.head.querySelector<HTMLMetaElement>(selector);
  if (!el) {
    el = document.createElement("meta");
    el.setAttribute(attr, key);
    document.head.appendChild(el);
  }
  el.setAttribute("content", content);
}

function canonical(href: string) {
  let el = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
  if (!el) {
    el = document.createElement("link");
    el.rel = "canonical";
    document.head.appendChild(el);
  }
  el.href = href;
}

/** 按当前路径更新标题、摘要、canonical 与分享卡片信息。 */
export function applyHead(path: string) {
  const seo = seoFor(path);
  const url = canonicalFor(path);
  document.title = seo.title;
  meta("name", "description", seo.description);
  meta("name", "robots", isIndexable(path) ? "index,follow" : "noindex,follow");
  canonical(url);
  meta("property", "og:type", "website");
  meta("property", "og:site_name", SITE_NAME);
  meta("property", "og:locale", "zh_CN");
  meta("property", "og:title", seo.title);
  meta("property", "og:description", seo.description);
  meta("property", "og:url", url);
  const cover = SITE_URL + OG_IMAGE.path;
  meta("property", "og:image", cover);
  meta("property", "og:image:width", String(OG_IMAGE.width));
  meta("property", "og:image:height", String(OG_IMAGE.height));
  meta("property", "og:image:alt", OG_IMAGE.alt);
  meta("name", "twitter:card", "summary_large_image");
  meta("name", "twitter:title", seo.title);
  meta("name", "twitter:description", seo.description);
  meta("name", "twitter:image", cover);
}
