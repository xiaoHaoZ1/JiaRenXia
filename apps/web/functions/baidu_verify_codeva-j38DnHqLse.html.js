// 百度站点验证文件：Pages 会把 /x.html 308 到 /x，验证请求必须按原路径 200 返回。
// 内容与 public/baidu_verify_codeva-j38DnHqLse.html 保持一致（两者同步）。
export function onRequestGet() {
  return new Response("5c339d34c50ebffb59e5f9a5d93a3d4f", {
    status: 200,
    headers: { "Content-Type": "text/html; charset=utf-8" },
  });
}
