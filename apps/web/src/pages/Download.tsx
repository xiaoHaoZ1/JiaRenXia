// 下载页：纯静态。安装包走下载服务 dl.jiarenxia.com（R2 + 版本命名）。
// 走外部地址（版本与校验值在 site.ts 同步维护）。

import { useEffect, useState } from "react";
import { Link } from "react-router";
import { downloadSourceFromSearch, type WebDownloadSource } from "../downloadSource";
import { useLang, useTo } from "../i18n";
import { APK_BETA, APK_DEMO, SITE } from "../site";

const T = {
  zh: {
    h1: "下载",
    stableTitle: "Android 正式版",
    stableMissingTitle: "Android 正式版尚未发布",
    stableMissing:
      "我们正在打磨正式版安装包。系统要求：Android 8.0 及以上。想第一时间知道发布消息，可以写信到 ",
    version: "版本：",
    size: "大小：",
    downloadStable: "下载安装包",
    betaTitle: "Android 测试版（Beta）",
    betaIntro:
      "开发中的功能抢先体验。它与正式版是两个独立应用（桌面图标名「家人侠-测试版」），" +
      "可以同时安装、数据互不相通；签名与正式版不同，两者之间不能直接覆盖升级；" +
      "未做压缩优化，体积比正式版大。",
    requirement: "系统要求：Android 8.0 及以上",
    downloadBeta: "下载测试版",
    betaMissing: "暂无测试版可下载。",
    demoTitle: "Android 演示版（预装示例家谱）",
    demoIntro:
      "打开就能看到一份完整的明朝示例家谱，不用先录数据就能试用全部功能。" +
      "它是独立应用（图标名「家人侠-演示版」），与正式版、测试版可同时安装；" +
      "碰一碰只能和演示版配对；「我的 → 重置演示数据」随时恢复初始数据。",
    demoPick:
      "一台手机试用装「朱棣」（家人更多）；拍「碰一碰结为夫妻」时：" +
      "一部手机装朱棣，另一部装徐皇后。",
    downloadDemoGroom: "下载演示版 · 朱棣",
    downloadDemoBride: "下载演示版 · 徐皇后",
    sourcePre: "家人侠和守护家人侠的源代码以 GPL-3.0 协议公开：",
    sourceJrx: "家人侠",
    sourceShx: "守护家人侠",
    sourcePost: "。",
    gxPre: "守护功能由独立应用「守护家人侠」提供（正式版，可在",
    gxLink: "「守护家人侠」",
    gxPost:
      "页下载）。各家品牌手机对后台、自启动的限制不一样，装好守护家人侠后请先做一次「实机演练」走一遍流程，确认能响能拨，再放心交给它。",
    free: "家人侠完全免费：所有功能，包括导出表格和高清关系图，不收取任何费用。",
  },
  en: {
    h1: "Download",
    stableTitle: "Android release",
    stableMissingTitle: "The Android release is not out yet",
    stableMissing:
      "We're still polishing the release package. Requirement: Android 8.0+. " +
      "To hear about it first, write to ",
    version: "Version: ",
    size: "Size: ",
    downloadStable: "Download APK",
    betaTitle: "Android beta",
    betaIntro:
      "Try upcoming features early. It's a separate app from the release (icon name " +
      "\"JiaRenXia Beta\") — both can be installed side by side with independent data; " +
      "the signature differs from the release so they can't upgrade over each other; " +
      "no size optimization, so the file is larger.",
    requirement: "Requirement: Android 8.0+",
    downloadBeta: "Download beta",
    betaMissing: "No beta build available right now.",
    demoTitle: "Android demo build (sample family included)",
    demoIntro:
      "Opens to a complete Ming-dynasty sample family — try every feature " +
      "without entering data first. It's a separate app (\"JiaRenXia Demo\") " +
      "that installs alongside the release and beta; tap-to-pair only works " +
      "between demo builds; \"Me → Reset demo data\" restores the initial data anytime.",
    demoPick:
      "For a single phone, pick \"Zhu Di\" (more relatives). To film " +
      "\"tap to become spouses\": install Zhu Di on one phone and Empress Xu on the other.",
    downloadDemoGroom: "Download demo · Zhu Di",
    downloadDemoBride: "Download demo · Empress Xu",
    sourcePre: "The source code of JiaRenXia and FamilyGuardian is published under GPL-3.0: ",
    sourceJrx: "JiaRenXia",
    sourceShx: "FamilyGuardian",
    sourcePost: ".",
    gxPre: "The guardian feature lives in a separate app, FamilyGuardian (stable release — get it on the ",
    gxLink: "FamilyGuardian",
    gxPost:
      " page). Phone brands restrict background work and auto-start differently, so after " +
      "installing please run the built-in live drill once to confirm it can ring and dial " +
      "before relying on it.",
    free: "JiaRenXia is completely free: every feature, including spreadsheet export and HD family graph images, costs nothing.",
  },
} as const;

export default function Download() {
  // 预渲染与首次 hydration 都保持 web，挂载后再读取查询参数，避免服务端与浏览器
  // 首屏属性不一致。来源码不写 Cookie，也不在浏览器持久化。
  const [downloadSource, setDownloadSource] = useState<WebDownloadSource>("web");
  const lang = useLang();
  const t = T[lang];
  const to = useTo();
  useEffect(() => {
    setDownloadSource(downloadSourceFromSearch(window.location.search));
  }, []);

  return (
    <>
      <h1>{t.h1}</h1>
      <div className="card">
        {SITE.apkVersion ? (
          <>
            <h3>{t.stableTitle}</h3>
            <ul className="plain">
              <li>{t.version}{SITE.apkVersion}</li>
              <li>{t.size}{(SITE.apkSizeBytes / 1024 / 1024).toFixed(1)} MB</li>
              <li>
                SHA-256：<code style={{ wordBreak: "break-all" }}>{SITE.apkSha256}</code>
              </li>
            </ul>
            <a
              className="btn btn-primary btn-big"
              href={SITE.apkDownloadUrl + `?src=${downloadSource}`}
              download
            >
              {t.downloadStable}
            </a>
          </>
        ) : (
          <>
            <h3>{t.stableMissingTitle}</h3>
            <p className="muted">
              {t.stableMissing}
              <a href={`mailto:${SITE.contactEmail}`}>{SITE.contactEmail}</a>。
            </p>
          </>
        )}
      </div>
      <div className="card">
        <h3>{t.betaTitle}</h3>
        <p className="muted">{t.betaIntro}</p>
        {APK_BETA.version ? (
          <>
            <ul className="plain">
              <li>{t.version}{APK_BETA.version}</li>
              <li>{t.size}{(APK_BETA.sizeBytes / 1024 / 1024).toFixed(1)} MB</li>
              <li>{t.requirement}</li>
              <li>
                SHA-256：<code style={{ wordBreak: "break-all" }}>{APK_BETA.sha256}</code>
              </li>
            </ul>
            <a
              className="btn btn-soft btn-big"
              href={APK_BETA.file + `?src=${downloadSource}`}
              download
            >
              {t.downloadBeta}
            </a>
          </>
        ) : (
          <p className="muted">{t.betaMissing}</p>
        )}
      </div>
      {APK_DEMO.version && (
        <div className="card">
          <h3>{t.demoTitle}</h3>
          <p className="muted">{t.demoIntro}</p>
          <ul className="plain">
            <li>{t.version}{APK_DEMO.version}</li>
            <li>{t.size}{(APK_DEMO.groom.sizeBytes / 1024 / 1024).toFixed(1)} MB</li>
            <li>{t.requirement}</li>
            <li>
              SHA-256（{APK_DEMO.groom.label}）：
              <code style={{ wordBreak: "break-all" }}>{APK_DEMO.groom.sha256}</code>
            </li>
            <li>
              SHA-256（{APK_DEMO.bride.label}）：
              <code style={{ wordBreak: "break-all" }}>{APK_DEMO.bride.sha256}</code>
            </li>
          </ul>
          <p className="muted">{t.demoPick}</p>
          <p>
            <a
              className="btn btn-soft btn-big"
              href={APK_DEMO.groom.file + `?src=${downloadSource}`}
              download
            >
              {t.downloadDemoGroom}
            </a>{" "}
            <a
              className="btn btn-soft btn-big"
              href={APK_DEMO.bride.file + `?src=${downloadSource}`}
              download
            >
              {t.downloadDemoBride}
            </a>
          </p>
        </div>
      )}
      <p className="muted">
        {t.sourcePre}
        <a href={SITE.sourceUrl} target="_blank" rel="noopener">{t.sourceJrx}</a>
        {lang === "en" ? ", " : "、"}
        <a href={SITE.guardianSourceUrl} target="_blank" rel="noopener">{t.sourceShx}</a>
        {t.sourcePost}
      </p>
      <p className="muted">
        {t.gxPre}<Link to={to("/shouhuxia")}>{t.gxLink}</Link>{t.gxPost}
      </p>
      <p className="muted">{t.free}</p>
    </>
  );
}
