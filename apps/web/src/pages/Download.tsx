// 下载页：纯静态。安装包走下载服务 dl.jiarenxia.com（R2 + 版本命名）。
// 走外部地址（版本与校验值在 site.ts 同步维护）。

import { Link } from "react-router";
import { SITE } from "../site";

export default function Download() {
  return (
    <>
      <h1>下载</h1>
      <div className="card">
        {SITE.apkVersion ? (
          <>
            <h3>Android 正式版</h3>
            <ul className="plain">
              <li>版本：{SITE.apkVersion}</li>
              <li>大小：{(SITE.apkSizeBytes / 1024 / 1024).toFixed(1)} MB</li>
              <li>
                SHA-256：<code style={{ wordBreak: "break-all" }}>{SITE.apkSha256}</code>
              </li>
            </ul>
            <a
              className="btn btn-primary btn-big"
              href={SITE.apkDownloadUrl + "?src=web"}
              download
            >
              下载安装包
            </a>
          </>
        ) : (
          <>
            <h3>Android 正式版尚未发布</h3>
            <p className="muted">
              我们正在打磨正式版安装包。系统要求：Android 8.0 及以上。
              想第一时间知道发布消息，可以写信到{" "}
              <a href={`mailto:${SITE.contactEmail}`}>{SITE.contactEmail}</a>。
            </p>
          </>
        )}
      </div>
      <p className="muted">
        家人侠和守护侠的源代码以 GPL-3.0 协议公开，见 <a href={SITE.sourceUrl} target="_blank" rel="noopener">GitHub</a>。
      </p>
      <p className="muted">
        守护功能由独立应用「守护侠」提供（测试版，可在<Link to="/extensions">「小侠家族」</Link>页下载）。各家品牌手机对后台、自启动的限制不一样，装好守护侠后请先用「测试模式」走一遍流程，确认能响能拨，再放心交给它。
      </p>
      <p className="muted">家人侠完全免费：所有功能，包括导出表格和高清关系图，不收取任何费用。</p>
    </>
  );
}
