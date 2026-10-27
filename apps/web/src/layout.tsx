// 页头（标志 + 导航 + 手机折叠菜单）与页脚。

import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router";
import type { ReactNode } from "react";
import { applyHead } from "./head";
import { SITE } from "./site";
import { Modal } from "./ui";

const NAV = [
  ["/", "首页"],
  ["/download", "下载"],
  ["/extensions", "小侠家族"],
  ["/help", "帮助"],
] as const;

export function PublicLayout() {
  const [open, setOpen] = useState(false);
  const [donateOpen, setDonateOpen] = useState(false);
  const loc = useLocation();
  const key = loc.pathname;
  // 站内跳转时同步标题与搜索摘要（首屏的 head 由预渲染写好，这里只管后续切换），
  // 并收起手机菜单：open 是本组件 state，key 只重建子树 DOM 不会重置它
  useEffect(() => { applyHead(key); setOpen(false); }, [key]);
  return (
    <div key={key} className="site">
      <header className="site-header">
        <div className="container header-inner">
          <Link to="/" className="brand">
            <img src="/favicon.svg" alt="" />
            家人侠
          </Link>
          <button
            className="menu-btn"
            aria-label={open ? "收起菜单" : "展开菜单"}
            aria-expanded={open}
            onClick={() => setOpen(!open)}
          >
            ☰
          </button>
          <nav className={open ? "nav open" : "nav"} aria-label="主导航">
            {NAV.map(([to, label]) => (
              <NavLink key={to} to={to} end={to === "/"}>
                {label}
              </NavLink>
            ))}
            {/* 支持作者不是页面：平级按钮，点开打赏弹窗 */}
            <button
              type="button"
              className="nav-btn"
              onClick={() => { setOpen(false); setDonateOpen(true); }}
            >
              支持作者
            </button>
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
                <strong>家人侠</strong>
                <span className="footer-motto">献给每一位有孝心的人。</span>
              </div>
            </div>
            <nav className="footer-links" aria-label="页脚">
              <a href={SITE.sourceUrl} target="_blank" rel="noopener">源代码</a>
              <Link to="/privacy">隐私政策</Link>
              <Link to="/terms">用户协议</Link>
              <a href={`mailto:${SITE.contactEmail}`}>联系邮箱：{SITE.contactEmail}</a>
            </nav>
          </div>
          <p className="footer-note">
            家人侠官方网站。家人侠的家庭资料只存在你自己的手机里。首页家庭照片为 AI 生成的示意图。
          </p>
        </div>
      </footer>
      {donateOpen ? (
        <Modal title="支持作者" onClose={() => setDonateOpen(false)}>
          <p className="donate-lead">
            家人侠完全免费。如果它帮你记住了家人的生日、或是陪长辈度过了一天，欢迎用微信扫码请作者喝杯咖啡。
          </p>
          <img
            className="donate-qr"
            src="/wechat-donate.png"
            alt="微信收款码：扫码打赏作者"
          />
          <div className="row">
            <button type="button" className="btn" onClick={() => setDonateOpen(false)}>
              关闭
            </button>
          </div>
        </Modal>
      ) : null}
    </div>
  );
}

export function Page({ children }: { children: ReactNode }) {
  return <div className="page">{children}</div>;
}
