# 家人侠网站：信息结构、界面与使用流程

版本：V1.2
日期：2026-10-18
状态：纯静态站点（`apps/web`），无后端、无账号体系。会员功能已整体下线（见 FAMILY_AFFAIRS 第 8 节留档）：买家中心、管理界面、`/membership` 页面与全部 `/api` 接口一并删除。

---

## 1. 原则

- 延续 App 的温暖、克制、可信赖（BRAND.md），面向普通家庭成员：中文、字大、按钮清楚、一屏只讲一件事。
- 手机与电脑都好用：手机单列，电脑最多两列；可点区域不小于 44×44 像素。
- **产品页面不出现技术术语**（不提 API、数据库、端口等）。
- **如实显示状态**：`src/site.ts` 里 `apkVersion` 留空时下载页写“尚未发布”、不放下载按钮。
- 不加载任何第三方脚本、字体、统计或广告；不收数据、不用 Cookie。

## 2. 视觉

- 设计方向“同一个家”：网站是 App 的延伸，视觉语言直接沿用 App，特色来自只有家人侠才有的元素（称谓、姓氏头像、生日提醒），而不是额外装饰。
- 颜色与 App 浅色方案对齐（`core/designsystem/.../ui/theme/Color.kt`、`Theme.kt`，2026-10-10 同步）：主色砖红 `#B4473C`，主色浅底 `#FBEAE7`，辅色暖棕 `#7D5A44`（浅底 `#F6EDE4`），第三色绿 `#57755B`（浅底 `#F1F6F2`），背景 `#FBF8F5`，卡片统一 `#FFFFFF`，描边 `#EFE7E1`，正文 `#1F1A17`，次要文字 `#7A6E68`，错误 `#BA1A1A`。系统深色模式时使用 App 深色方案（背景 `#17120F`，卡片 `#221A16`，描边 `#342A25`，次要文字 `#BCAAA3`，主色 `#FFB4A7`，主色浅底 `#3A2420`）。变量统一定义在 `apps/web/src/styles.css` 的 `:root`。
- 页头为与背景同色的平底（取消渐变），页头半透明磨砂效果保留。
- 沿用 App 造型：写着姓氏的圆形头像 + 侧别色环（父系砖红、母系绿、配偶侧棕）、灰色称谓小字、日历日期块、白底描边卡片（无阴影，圆角 14px）；选中态用淡主色底（与 App 筛选胶囊一致）。
- 图标为站内自绘的线性 SVG（`apps/web/src/icons.tsx`），不引第三方图标库。
- 字体使用系统中文字体（不下载网络字体），正文 17px 起，行高 1.7。页脚落款“献给每一位有孝心的人。”用系统楷体（没有时回落），只用在这一句。
- 首页标题和说明按标点拆成短语换行，避免句中断开或末尾单独剩一个字。
- 首页与帮助页的截图取自 `docs/images/`（`ReadmeShotsTest` 用合成数据生成），复制在 `apps/web/src/assets/shots/`；App 界面有明显变化时同步更新。不得使用真机截图或真实家庭数据。
- 标志取自 App 图标：砖红心形内嵌小屋（`assets/brand/logo.svg`）。
- 品牌位图由 `apps/web/scripts/brand-images.mjs` 用 Playwright + 本机 Edge 渲染，产物入库在 `public/`：分享封面 `og-cover.png`（1200×630，设计稿是 `scripts/og-cover.html`）、`apple-touch-icon.png`（180×180，不透明，iOS 自己裁圆角）、`favicon-32.png` 与 `favicon.ico`（透明底）。**不参与 `npm run build`**，改了设计稿或标志才手动跑 `npm run brand-images`。
- 尊重“减少动态效果”系统设置（浮动卡片、碰一碰插画等动画全部停止）；有清晰的键盘焦点样式。

## 3. 站点结构

全部为公共页面，无需登录。

| 路径 | 页面 | 内容 |
|---|---|---|
| `/` | 首页 | ① 首屏：kicker「家人侠 · 献给有孝心的普通人」、h1「认全家族所有人，记住该惦记的事。」、支撑文案、下载与"使用帮助"按钮、版本信息（取自 `src/site.ts`）、全家福相框（AI 示意图）加右下叠放的手机截图与两张浮动卡片，手机宽度下只显示相框；② 日常场景三卡（群里说的事转给家人侠 / 每周给爷爷打个电话 / 爸妈的生日农历的也不忘，均为 HTML/CSS 画的场景，不用插画）；③ 一家人（关系图截图 + 三要点 + 链到 `/help#kinship`）；④ 守护侠（测试版）卡片，链到小侠家族页；⑤ 浅色隐私区「家里的事，留在家里」；⑥ 结尾团圆饭照片（AI 示意图）+ 引导。官网固定浅色主题，页脚注明首页家庭照片为 AI 生成的示意图。不宣传碰一碰 |
| `/download` | 下载 | 正式版卡：版本、大小、SHA-256 与下载按钮（链接到下载服务 `dl.jiarenxia.com/jiarenxia-<版本>.apk`）；宣传内容可用白名单查询参数（如 `?src=xhs-call`）把匿名来源带到下载服务，按天汇总次数，不使用 Cookie 或用户级埋点；`apkVersion` 为空时只显示“尚未发布”与系统要求（Android 8.0 及以上）。下方另有「Android 测试版（Beta）」卡：debug 构建、应用名「家人侠-测试版」、与正式版共存，配置在 `site.ts` 的 `APK_BETA`，`version` 为空时显示“暂无测试版可下载” |
| `/extensions` | 小侠家族 | 同作者的其他小应用（当前为「朋友侠」「吃什么」）：每个应用一张卡片，图标 + 名称 + 阶段胶囊（如“测试版”）+ 简介 + 版本/大小/系统要求/SHA-256 + 下载按钮（链接到站点根目录对应安装包）；应用清单在 `src/site.ts` 的 `EXTENSIONS`，version 为空时显示“安装包还在准备中” |
| `/help` | 帮助 | 称谓推导演示卡（`#kinship` 锚点，例选"妻子的父亲"得"岳父"）；给家里老人看的图文教程：每节一张 App 截图（与首页同源的合成数据截图，配手机外框）+「跟着点」步骤——认识家庭页、看资料打电话、家事提醒、关系图；附字太小/换手机说明、常见问题、联系邮箱 |
| `/privacy` | 隐私政策（草稿） | 家庭资料只存手机本地；本站为纯静态页面、不收数据不用 Cookie；页首标明“草稿，尚未经过法律或合规审核” |
| `/terms` | 用户协议（草稿） | 全部功能免费、打赏自愿不影响功能；同样标明草稿 |
| 其他 | 找不到页面 | 返回首页链接；未知路径由 serve.mjs 以 404 状态返回预渲染的 404.html |

页头：标志与“家人侠”、首页 / 下载 / 小侠家族 / 帮助 / 支持作者（页链接当前页为粉色胶囊；“支持作者”是按钮不是页面，点开打赏弹窗）。手机上折叠为菜单按钮，各项直接排成一列。
页脚：标志与落款、隐私政策、用户协议、联系邮箱；内容不足一屏时页脚贴底。

## 4. 打赏

- 入口只有一处：导航「支持作者」按钮，打开弹窗（通用 `Modal`）。
- 文案口径：家人侠完全免费、所有功能开放；打赏是对作者的自愿支持，不影响任何功能。
- 收款码是公共静态资源，与仓库外源文件 （本机离线备份目录，公开快照不含） 同步。

## 5. 技术说明（开发者）

- React + TypeScript + Vite（纯前端构建，无服务端）；SPA 路由：`/ /download /extensions /help /privacy /terms` 与 `*` 兜底。
- 构建分三步（`npm run build`）：① `vite build` 出浏览器产物；② `vite build --ssr src/entry-server.tsx` 出临时的 `dist-ssr/`；③ `node scripts/prerender.mjs` 把每个路由渲染成带正文的静态 HTML，并生成 `sitemap.xml`，最后删掉 `dist-ssr/`。详见第 6 节。
- 站点常量集中在 `src/site.ts`（联系邮箱、APK 版本/大小/SHA-256、小应用清单 `EXTENSIONS`）；APK 文件本身放 `public/`（`jiarenxia.apk`、`chishenme.apk`、`pengyouxia.apk`），不入库，发布前手动拷入并同步 site.ts。
- 部署：任意静态托管即可。产物里每个路由都有自己的 `index.html`（`/help` → `dist/help/index.html`），托管方只要支持目录默认页就行；未知路径应以 **404 状态**返回 `dist/404.html`（它同样带前端路由，页面上显示“找不到这个页面”）。
- 浏览器验证：Playwright（本机 Microsoft Edge，手机与电脑两种尺寸），`npm run e2e` 自动 `build` 后用**生产同款的 `serve.mjs`**（4173）起服务，这样预渲染页面与 404 状态码的行为和线上一致。这是响应式浏览器测试，**不是真机测试**。

## 6. 搜索引擎收录（SEO）

目标：百度、Google、Bing 搜“家人侠”“jiarenxia”能找到本站。

- **预渲染是前提**。站点是前端渲染的 SPA，直接抓取只能拿到空的 `<div id="root">`。Google 还能执行 JS，百度和 Bing 基本不行。因此构建时就把 HTML 渲染好（`scripts/prerender.mjs`），抓取方拿到的是完整正文；浏览器端 `main.tsx` 检测到 `#root` 已有内容就走 `hydrateRoot`，不会白屏闪烁。
- **元数据唯一事实来源是 `src/seo.ts`**：每页的 title、description、sitemap 权重、canonical 与 JSON-LD 都在这里。预渲染脚本、`sitemap.xml`、客户端路由切换（`src/head.ts`）、e2e 断言全都读它，改文案只改这一个文件。
- 客户端切换页面时由 `src/head.ts` **改写**已有的 head 标签而不是新增，避免出现两个 `<title>`。
- `public/robots.txt` 放行全站并声明 `Sitemap:`；`sitemap.xml` 由构建生成，`lastmod` 取构建日期。
- 分享卡片：每页都带 `og:image`（指向 `og-cover.png`）与 `twitter:card=summary_large_image`，往知乎、酷安、微信、QQ 发链接时有缩略图。站点图标同时提供 SVG 与位图（`favicon.ico`/`apple-touch-icon.png`）——百度、360、微信不认 SVG favicon，只给 SVG 会在搜索结果里空白。预渲染脚本会校验这几个文件确实存在，缺了直接让构建失败。
- **没有加 `FAQPage` 结构化数据**（评估后主动放弃）：Google 自 2023 年起把 FAQ 富媒体结果限定给政府、医疗类权威站点，普通站点标了也不展示；百度不读 schema.org 的 FAQPage，它走自己平台的结构化数据提交。加了只是增加维护负担。
- JSON-LD 声明 `SoftwareApplication`（免费、Android、下载地址）与 `WebSite`（alternateName 含 `jiarenxia`），帮助搜索引擎把“家人侠”认成一个 App——这个词同时是《速度与激情》的网络梗，竞争不小。
- **Cloudflare 的坑（最关键）**：Cloudflare 的「托管 robots.txt」/「Block AI bots」会把 Baiduspider 当成 AI 爬虫，既在 robots.txt 里加 `User-agent: Baiduspider / Disallow: /`，又在边缘直接 403 掉百度蜘蛛（实测：Baiduspider UA 返回 403，Googlebot UA 返回 200）。**必须在 Cloudflare 后台 Security → Configure AI bot policies 关掉（Training 设为 Allow、关掉 Bot Preference Sync）**，否则站内做什么都没用。2026-10-09 已按此处理，现在 Baiduspider UA 返回 200、robots.txt 里不再有 Cloudflare 托管内容；百度抓取诊断也已抓取成功（首次报「DNS 无法解析 IP」是百度自己的解析缓存没刷新，在该页点「报错」后恢复）。
- 收录提交：百度搜索资源平台（ziyuan.baidu.com，站点验证文件放 `public/`）、Google Search Console、Bing 网站管理员工具；三家都提交 `https://jiarenxia.com/sitemap.xml`。Google/Bing 建议用 DNS TXT 验证（域名 DNS 在 Cloudflare，最省事）。
- e2e 的 `e2e/seo.spec.ts` 守着这些结论：robots/sitemap 内容、每页静态 HTML 的 title/description/canonical/正文、未知地址的 404 与 noindex、hydrate 无控制台报错。

## 官网色板（2026-10-21 温馨化改版）

官网背景类 token 比 App 更暖：`--bg #fdf8f2`、`--surface #f8ece1`、`--surface-high #f3e3d5`、`--border #f0e2d5`、`--primary-bg #fcebe5`、`--secondary-bg #f8eee3`、`--radius 18px`；新增装饰用 `--warm #f6dcc8` / `--warm-bg #fdf0e6`（深色 `#4a3426` / `#2a1f19`）。品牌主色不变。

官网固定浅色主题（2026-10-22 用户决定）：`:root` 设 `color-scheme: light`，无 `@media (prefers-color-scheme: dark)` 规则；深色 token 已删。
