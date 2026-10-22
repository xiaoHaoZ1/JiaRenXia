import { Link } from "react-router";

export default function NotFound() {
  return (
    <div className="hero">
      <h1>找不到这个页面</h1>
      <p className="sub">你要找的页面不存在或已被移动。</p>
      <p>
        <Link className="btn btn-primary" to="/">回到首页</Link>
      </p>
    </div>
  );
}
