// 预渲染入口：只在构建时由 Node 执行（vite build --ssr），浏览器不会加载它。
// scripts/prerender.mjs 调用 render() 拿到各路由的首屏 HTML，再写成静态页面。
//
// 顺带把 src/seo.ts 的元数据转出一次，让预渲染脚本和浏览器端共用同一份定义。

import { StrictMode } from "react";
import { renderToString } from "react-dom/server";
import { StaticRouter } from "react-router";
import App from "./App";
import "./styles.css";

/** 渲染指定路由的首屏 HTML（不含 <html>/<head>，只是 #root 的内容）。 */
export function render(path: string): string {
  return renderToString(
    <StrictMode>
      <StaticRouter location={path}>
        <App />
      </StaticRouter>
    </StrictMode>
  );
}

export {
  canonicalFor,
  isIndexable,
  jsonLdFor,
  OG_IMAGE,
  PAGES,
  seoFor,
  SITE_NAME,
  SITE_URL,
} from "./seo";
// 转出站点常量：prerender 据此生成 jiarenxia-version.json（App 端更新检查读它）
export { SITE } from "./site";
export { EXTENSIONS } from "./site";
