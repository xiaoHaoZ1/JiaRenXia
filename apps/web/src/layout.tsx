// 页头（标志 + 导航 + 语言切换 + 手机折叠菜单）与页脚。

import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router";
import type { ReactNode } from "react";
import { applyHead } from "./head";
import { SITE } from "./site";
import { Modal } from "./ui";
import { alternateLangPath, LangContext, localizedPath, type Lang } from "./i18n";

const NAV = [
  ["/", { zh: "首页", en: "Home" }],
  ["/download", { zh: "下载", en: "Download" }],
  ["/extensions", { zh: "小侠家族", en: "More apps" }],
  ["/help", { zh: "帮助", en: "Help" }],
] as const;

const T = {
  zh: {
    brand: "家人侠",
    menuOpen: "展开菜单",
    menuClose: "收起菜单",
    navMain: "主导航",
    donate: "支持作者",
    motto: "献给每一位有孝心的人。",
    footerNav: "页脚",
    source: "源代码",
    privacy: "隐私政策",
    terms: "用户协议",
    contact: "联系邮箱：",
    footerNote:
      "家人侠官方网站。家人侠的家庭资料只存在你自己的手机里。首页家庭照片为 AI 生成的示意图。",
    donateLead:
      "家人侠完全免费。如果它帮你记住了家人的生日、或是陪长辈度过了一天，欢迎用微信扫码请作者喝杯咖啡。",
    donateAlt: "微信收款码：扫码打赏作者",
    close: "关闭",
    langSwitch: "EN",
    langSwitchLabel: "Switch to English",
  },
  en: {
    brand: "JiaRenXia",
    menuOpen: "Open menu",
    menuClose: "Close menu",
    navMain: "Main navigation",
    donate: "Support the author",
    motto: "For everyone who cares about family.",
    footerNav: "Footer",
    source: "Source code",
    privacy: "Privacy policy",
    terms: "Terms of use",
    contact: "Contact: ",
    footerNote:
      "Official JiaRenXia website. Your family data lives only on your own phone. " +
      "The family photo on the home page is AI-generated.",
    donateLead:
      "JiaRenXia is completely free. If it helped you remember a birthday or kept an " +
      "elder company for a day, you can scan the WeChat QR code to buy the author a coffee.",
    donateAlt: "WeChat QR code: scan to tip the author",
    close: "Close",
    langSwitch: "中文",
    langSwitchLabel: "切换到中文",
  },
} as const;

export function PublicLayout({ lang }: { lang: Lang }) {
  const [open, setOpen] = useState(false);
  const [donateOpen, setDonateOpen] = useState(false);
  const loc = useLocation();
  const key = loc.pathname;
  const t = T[lang];
  const to = (p: string) => localizedPath(lang, p);
  // 站内跳转时同步标题与搜索摘要（首屏的 head 由预渲染写好，这里只管后续切换），
  // 并收起手机菜单：open 是本组件 state，key 只重建子树 DOM 不会重置它
  useEffect(() => { applyHead(key); setOpen(false); }, [key]);
  return (
    <LangContext.Provider value={lang}>
      <div key={key} className="site">
        <header className="site-header">
          <div className="container header-inner">
            <Link to={to("/")} className="brand">
              <img src="/favicon.svg" alt="" />
              {t.brand}
            </Link>
            <button
              className="menu-btn"
              aria-label={open ? t.menuClose : t.menuOpen}
              aria-expanded={open}
              onClick={() => setOpen(!open)}
            >
              ☰
            </button>
            <nav className={open ? "nav open" : "nav"} aria-label={t.navMain}>
              {NAV.map(([to2, label]) => (
                <NavLink key={to2} to={to(to2)} end={to2 === "/"}>
                  {label[lang]}
                </NavLink>
              ))}
              {/* 支持作者不是页面：平级按钮，点开打赏弹窗 */}
              <button
                type="button"
                className="nav-btn"
                onClick={() => { setOpen(false); setDonateOpen(true); }}
              >
                {t.donate}
              </button>
              {/* 语言切换：同一页面的另一种语言地址，整页跳转走预渲染静态页 */}
              <a className="nav-lang" href={alternateLangPath(key)} hrefLang={lang === "zh" ? "en" : "zh-CN"}
                 aria-label={t.langSwitchLabel}>
                {t.langSwitch}
              </a>
            </nav>
          </div>
        </header>
        <main className="container site-main">
          <Outlet />
        </main>
        <footer className="site-footer">
          <div className="container">
            <div className="footer-top">
              <div className="footer-brand">
                <img src="/favicon.svg" alt="" />
                <div>
                  <strong>{t.brand}</strong>
                  <span className="footer-motto">{t.motto}</span>
                </div>
              </div>
              <nav className="footer-links" aria-label={t.footerNav}>
                <a href={SITE.sourceUrl} target="_blank" rel="noopener">{t.source}</a>
                <Link to={to("/privacy")}>{t.privacy}</Link>
                <Link to={to("/terms")}>{t.terms}</Link>
                <a href={`mailto:${SITE.contactEmail}`}>{t.contact}{SITE.contactEmail}</a>
              </nav>
            </div>
            <p className="footer-note">{t.footerNote}</p>
          </div>
        </footer>
        {donateOpen ? (
          <Modal title={t.donate} onClose={() => setDonateOpen(false)}>
            <p className="donate-lead">{t.donateLead}</p>
            <img
              className="donate-qr"
              src="/wechat-donate.png"
              alt={t.donateAlt}
            />
            <div className="row">
              <button type="button" className="btn" onClick={() => setDonateOpen(false)}>
                {t.close}
              </button>
            </div>
          </Modal>
        ) : null}
      </div>
    </LangContext.Provider>
  );
}

export function Page({ children }: { children: ReactNode }) {
  return <div className="page">{children}</div>;
}
