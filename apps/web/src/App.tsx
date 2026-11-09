// 路由总表：纯静态站点，只保留公共页面。中文在根路径，英文整树挂在 /en 下。

import { Navigate, Route, Routes } from "react-router";
import { PublicLayout } from "./layout";

import Home from "./pages/Home";
import Download from "./pages/Download";
import ShouHuXia from "./pages/ShouHuXia";
import Help from "./pages/Help";
import Privacy from "./pages/Privacy";
import Terms from "./pages/Terms";
import NotFound from "./pages/NotFound";

export default function App() {
  return (
    <Routes>
      <Route element={<PublicLayout lang="zh" />}>
        <Route path="/" element={<Home />} />
        <Route path="/download" element={<Download />} />
        <Route path="/shouhuxia" element={<ShouHuXia />} />
        {/* 旧「小侠家族」页地址（线上另由 _redirects 301 兜底） */}
        <Route path="/extensions" element={<Navigate to="/shouhuxia" replace />} />
        <Route path="/help" element={<Help />} />
        <Route path="/privacy" element={<Privacy />} />
        <Route path="/terms" element={<Terms />} />
        <Route path="*" element={<NotFound />} />
      </Route>
      <Route path="/en" element={<PublicLayout lang="en" />}>
        <Route index element={<Home />} />
        <Route path="download" element={<Download />} />
        <Route path="shouhuxia" element={<ShouHuXia />} />
        <Route path="extensions" element={<Navigate to="/en/shouhuxia" replace />} />
        <Route path="help" element={<Help />} />
        <Route path="privacy" element={<Privacy />} />
        <Route path="terms" element={<Terms />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
