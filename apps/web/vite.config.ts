import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// 纯静态站点：无后端代理，构建产物 dist 直接交给任意静态服务器。
export default defineConfig({
  plugins: [react()],
  build: { outDir: "dist", sourcemap: false },
});
