import { StrictMode } from "react";
import { createRoot, hydrateRoot } from "react-dom/client";
import { BrowserRouter } from "react-router";
import App from "./App";
import "./styles.css";

const root = document.getElementById("root")!;
const app = (
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>
);

// 构建产物里 #root 已经是预渲染好的首屏（scripts/prerender.mjs），直接接管现有 DOM，
// 避免白屏闪烁；开发模式下 #root 里只有占位注释（没有元素），走普通渲染。
if (root.firstElementChild) hydrateRoot(root, app);
else createRoot(root).render(app);
