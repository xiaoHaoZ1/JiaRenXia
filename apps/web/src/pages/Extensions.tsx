// 小侠家族页：同作者的其他小应用，各自附下载。应用清单集中在 site.ts 的 EXTENSIONS，
// 安装包放站点根目录（不入库）；version 留空时如实显示“尚未发布”。

import { useState } from "react";
import { EXTENSIONS, SITE } from "../site";
import { SITE_URL } from "../seo";
import { Icon } from "../icons";

/** 复制按钮：点一下复制文本，短暂显示“已复制” */
function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      type="button"
      className="btn"
      onClick={() => {
        const done = () => {
          setCopied(true);
          setTimeout(() => setCopied(false), 2000);
        };
        const fallback = () => {
          // 剪贴板 API 不可用时退回选择复制
          const ta = document.createElement("textarea");
          ta.value = text;
          document.body.appendChild(ta);
          ta.select();
          document.execCommand("copy");
          ta.remove();
          done();
        };
        const clip = navigator.clipboard;
        if (clip) clip.writeText(text).then(done, fallback);
        else fallback();
      }}
    >
      {copied ? "已复制" : "复制链接"}
    </button>
  );
}

export default function Extensions() {
  return (
    <>
      <h1>小侠家族</h1>
      <p className="muted">
        同一作者做的其他小应用。和家人侠一样：完全免费、不用注册、不联网，资料只存在你自己的手机里。
      </p>
      {EXTENSIONS.map((app) => (
        <div className="card" key={app.id}>
          <h3 className="ext-head">
            <span className="ext-ico">
              <Icon name={app.icon} size={26} />
            </span>
            {app.name}
            <span className="tag tag-test">{app.stage}</span>
          </h3>
          <p>{app.description}</p>
          {"note" in app && app.note ? (
            <p className="note">{app.note}</p>
          ) : null}
          {app.version ? (
            <>
              <ul className="plain">
                <li>
                  版本：{app.version}
                  {"released" in app && app.released ? `（${app.released} 更新）` : null}
                </li>
                {"whatsNew" in app && app.whatsNew ? <li>本版更新：{app.whatsNew}</li> : null}
                <li>大小：{(app.sizeBytes / 1024 / 1024).toFixed(1)} MB</li>
                <li>系统要求：Android {app.minAndroid} 及以上</li>
                <li>
                  SHA-256：<code style={{ wordBreak: "break-all" }}>{app.sha256}</code>
                </li>
              </ul>
              <div className="ext-dl">
                <div className="row">
                  <a className="btn btn-primary btn-big" href={app.file + "?src=web"} download>
                    下载安装包
                  </a>
                  <CopyButton text={`${SITE_URL}/extensions`} />
                </div>
                <div className="ext-qr">
                  <img src="/qr-extensions.svg" alt="手机扫码打开下载页" />
                  <span className="muted">手机扫码打开本页</span>
                </div>
              </div>
              <ol className="plain muted ext-steps">
                <li>点「下载安装包」，下完点开文件</li>
                <li>系统提示「禁止安装未知来源应用」时，允许浏览器安装一次</li>
                <li>回到下载页再点安装；以后新版本直接覆盖装，数据保留</li>
              </ol>
              <p className="muted">
                要在微信里发给家人：复制本页链接丢群里；对方打不开下载时，点右上角 ··· 选择「在浏览器打开」。
              </p>
            </>
          ) : (
            <p className="muted">
              安装包还在准备中。想第一时间知道发布消息，可以写信到{" "}
              <a href={`mailto:${SITE.contactEmail}`}>{SITE.contactEmail}</a>。
            </p>
          )}
        </div>
      ))}
    </>
  );
}
