import { Link } from "react-router";
import { useLang, useTo } from "../i18n";

const T = {
  zh: { h1: "找不到这个页面", sub: "你要找的页面不存在或已被移动。", back: "回到首页" },
  en: { h1: "Page not found", sub: "The page you're looking for doesn't exist or has moved.", back: "Back to home" },
} as const;

export default function NotFound() {
  const t = T[useLang()];
  const to = useTo();
  return (
    <div className="hero">
      <h1>{t.h1}</h1>
      <p className="sub">{t.sub}</p>
      <p>
        <Link className="btn btn-primary" to={to("/")}>{t.back}</Link>
      </p>
    </div>
  );
}
