// 小侠家族页：同作者的其他小应用，各自附下载。应用清单集中在 site.ts 的 EXTENSIONS，
// 安装包放站点根目录（不入库）；version 留空时如实显示“尚未发布”。

import { useState } from "react";
import { EXTENSIONS, SITE } from "../site";
import { SITE_URL } from "../seo";
import { Icon } from "../icons";
import { useLang } from "../i18n";
import shouhuxiaIcon from "../assets/shouhuxia-icon.png";
import fantongxiaIcon from "../assets/fantongxia-icon.png";

const T = {
  zh: {
    h1: "小侠家族",
    intro: "同一作者做的其他小应用。和家人侠一样：完全免费、不用注册、不联网，资料只存在你自己的手机里。",
    copied: "已复制",
    copyLink: "复制链接",
    version: "版本：",
    releasedSuffix: " 更新）",
    whatsNew: "本版更新：",
    size: "大小：",
    requirement: "系统要求：",
    requirementSuffix: " 及以上",
    downloadApk: "下载安装包",
    qrAlt: "手机扫码打开下载页",
    qrText: "手机扫码打开本页",
    step1: "点「下载安装包」，下完点开文件",
    step2: "系统提示「禁止安装未知来源应用」时，允许浏览器安装一次",
    step3: "回到下载页再点安装；以后新版本直接覆盖装，数据保留",
    shareNote: "要在微信里发给家人：复制本页链接丢群里；对方打不开下载时，点右上角 ··· 选择「在浏览器打开」。",
    missingPre: "安装包还在准备中。想第一时间知道发布消息，可以写信到 ",
  },
  en: {
    h1: "More apps",
    intro: "Other small apps by the same author. Just like JiaRenXia: completely free, no sign-up, offline — data stays on your own phone.",
    copied: "Copied",
    copyLink: "Copy link",
    version: "Version: ",
    releasedSuffix: " update)",
    whatsNew: "What's new: ",
    size: "Size: ",
    requirement: "Requirement: Android ",
    requirementSuffix: "+",
    downloadApk: "Download APK",
    qrAlt: "Scan the QR code on your phone to open the download page",
    qrText: "Scan with your phone to open this page",
    step1: "Tap \"Download APK\", then open the downloaded file",
    step2: "When the system blocks \"unknown sources\", allow the browser to install once",
    step3: "Go back to the download page and install; future versions install over it with data kept",
    shareNote: "To share with family in WeChat: copy this page's link into the group; if they can't open the download, tap ··· at the top right and choose \"Open in browser\".",
    missingPre: "The package is still being prepared. To hear about it first, write to ",
  },
} as const;

/** 有真实应用图标的条目按 id 放这里；其余回落到 site.ts 的线性图标名。 */
const APP_ICONS: Record<string, string> = {
  shouhuxia: shouhuxiaIcon,
  fantongxia: fantongxiaIcon,
};

/** 复制按钮：点一下复制文本，短暂显示“已复制” */
function CopyButton({ text, label, copiedLabel }: { text: string; label: string; copiedLabel: string }) {
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
      {copied ? copiedLabel : label}
    </button>
  );
}

export default function Extensions() {
  const lang = useLang();
  const t = T[lang];
  const en = lang === "en";
  return (
    <>
      <h1>{t.h1}</h1>
      <p className="muted">{t.intro}</p>
      {EXTENSIONS.map((app) => {
        const loc = "en" in app ? app.en : undefined;
        return (
        <div className="card" key={app.id}>
          <h3 className="ext-head">
            {APP_ICONS[app.id] ? (
              <img className="ext-appicon" src={APP_ICONS[app.id]} alt="" />
            ) : (
              <span className="ext-ico">
                <Icon name={app.icon} size={26} />
              </span>
            )}
            {en && loc ? loc.name : app.name}
            <span className={`tag ${app.stage === "正式版" ? "tag-ok" : "tag-test"}`}>{en && loc ? loc.stage : app.stage}</span>
          </h3>
          <p>{en && loc ? loc.description : app.description}</p>
          {"note" in app && app.note ? (
            <p className="note">{en && loc && "note" in loc && loc.note ? loc.note : app.note}</p>
          ) : null}
          {app.version ? (
            <>
              <ul className="plain">
                <li>
                  {t.version}{app.version}
                  {"released" in app && app.released ? (en ? ` (${app.released}${t.releasedSuffix}` : `（${app.released}${t.releasedSuffix}`) : null}
                </li>
                {"whatsNew" in app && app.whatsNew ? (
                  <li>{t.whatsNew}{en && loc && "whatsNew" in loc && loc.whatsNew ? loc.whatsNew : app.whatsNew}</li>
                ) : null}
                <li>{t.size}{(app.sizeBytes / 1024 / 1024).toFixed(1)} MB</li>
                <li>{t.requirement}{app.minAndroid}{t.requirementSuffix}</li>
                <li>
                  SHA-256：<code style={{ wordBreak: "break-all" }}>{app.sha256}</code>
                </li>
              </ul>
              <div className="ext-dl">
                <div className="row">
                  <a className="btn btn-primary btn-big" href={app.file + "?src=web"} download>
                    {t.downloadApk}
                  </a>
                  <CopyButton text={`${SITE_URL}${en ? "/en" : ""}/extensions`} label={t.copyLink} copiedLabel={t.copied} />
                </div>
                <div className="ext-qr">
                  <img src="/qr-extensions.svg" alt={t.qrAlt} />
                  <span className="muted">{t.qrText}</span>
                </div>
              </div>
              <ol className="plain muted ext-steps">
                <li>{t.step1}</li>
                <li>{t.step2}</li>
                <li>{t.step3}</li>
              </ol>
              <p className="muted">{t.shareNote}</p>
            </>
          ) : (
            <p className="muted">
              {t.missingPre}
              <a href={`mailto:${SITE.contactEmail}`}>{SITE.contactEmail}</a>。
            </p>
          )}
        </div>
        );
      })}
    </>
  );
}
