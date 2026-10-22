// 路由总表：纯静态站点，只保留公共页面。

import { Route, Routes } from "react-router";
import { PublicLayout } from "./layout";

import Home from "./pages/Home";
import Download from "./pages/Download";
import Extensions from "./pages/Extensions";
import Help from "./pages/Help";
import Privacy from "./pages/Privacy";
import Terms from "./pages/Terms";
import NotFound from "./pages/NotFound";

export default function App() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route path="/" element={<Home />} />
        <Route path="/download" element={<Download />} />
        <Route path="/extensions" element={<Extensions />} />
        <Route path="/help" element={<Help />} />
        <Route path="/privacy" element={<Privacy />} />
        <Route path="/terms" element={<Terms />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
