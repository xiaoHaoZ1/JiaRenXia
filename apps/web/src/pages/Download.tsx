// 下载页：纯静态。安装包走下载服务 dl.jiarenxia.com（R2 + 版本命名）；
// 正式版另有 GitHub Release 备用链接。走外部地址（版本与校验值在 site.ts 同步维护）。
// 微信内不能直接下载安装包：显示提示条，所有安装包链接都拦成「请用浏览器打开」弹窗。

import { useEffect, useState, type MouseEventHandler } from "react";
import { Link } from "react-router";
import { downloadSourceFromSearch, type WebDownloadSource } from "../downloadSource";
import { useLang, useTo } from "../i18n";
import { APK_BETA, APK_DEMO, SITE } from "../site";
import { SITE_URL } from "../seo";
import {
  CopyButton, QqGroup, useInWeChat, WeChatBanner, WeChatModal,
} from "../ui";

const T = {
  zh: {
    h1: "下载家人侠",
    sub: "免费、无广告、不用注册。安卓手机，系统 Android 8.0 及以上。",
    stableTitle: "Android 正式版",
    stableMissingTitle: "Android 正式版尚未发布",
    stableMissing:
      "我们正在打磨正式版安装包。系统要求：Android 8.0 及以上。想第一时间知道发布消息，可以写信到 ",
    version: "版本：",
    size: "大小：",
    sizeSuffix: " MB · 完全免费",
    downloadStable: "下载安装包",
    copyPage: "复制本页链接",
    copiedPage: "已复制",
    mirrorPre: "下载慢或打不开？试试",
    mirrorLink: "备用下载（GitHub）",
    mirrorPost: "。",
    qrAlt: "家人侠下载页二维码",
    qrText: "手机扫码下载",
    notesSummary: "这一版更新了什么",
    stepsH: "第一次安装，照着做",
    steps: [
      "点「下载安装包」，下载完成后，在通知栏或手机自带的「文件管理 → 下载」里点开它。",
      "手机提示「禁止安装未知来源的应用」时，点「设置」，打开「允许来自此来源的应用」，返回后再点安装。",
      "小米、华为、OPPO、vivo 等手机安装应用商店以外的应用时，会提示「风险」或「未经检测」，有的要等几秒或登录账号。看清名字是「家人侠」后，选「继续安装」。",
      "华为手机如果开着「纯净模式」，先在「设置 → 系统和更新 → 纯净模式」里暂时关掉。已经升级到 HarmonyOS NEXT 的华为手机不能安装安卓应用，暂时用不了家人侠。",
      "以后有新版本，下载后直接覆盖安装，家里的资料都会留着。",
    ],
    stepsNote:
      "发给家里人：点「复制本页链接」，粘贴到微信。对方在微信里打开后，点右上角「···」选「在浏览器打开」再下载。",
    helpH: "装不上、有问题？",
    helpP: "加入家人侠 QQ 群，直接问作者；新版本也会在群里说。",
    helpMailPre: "也可以写信到 ",
    moreSummary: "其他版本与校验值",
    shaReleasePre: "正式版 SHA-256（核对安装包是否完整）：",
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
    sourcePre: "家人侠和守护侠的源代码以 GPL-3.0 协议公开：",
    sourceJrx: "家人侠",
    sourceShx: "守护侠",
    sourcePost: "。",
    gxPre: "守护功能由独立应用「守护侠」提供（正式版，可在",
    gxLink: "「守护侠」",
    gxPost:
      "页下载）。各家品牌手机对后台、自启动的限制不一样，装好守护侠后请先做一次「实机演练」走一遍流程，确认能响能拨，再放心交给它。",
    free: "家人侠完全免费：所有功能，包括导出表格和高清关系图，不收取任何费用。",
  },
  en: {
    h1: "Download JiaRenXia",
    sub: "Free, no ads, no sign-up. For Android phones running Android 8.0 or later.",
    stableTitle: "Android release",
    stableMissingTitle: "The Android release is not out yet",
    stableMissing:
      "We're still polishing the release package. Requirement: Android 8.0+. " +
      "To hear about it first, write to ",
    version: "Version: ",
    size: "Size: ",
    sizeSuffix: " MB · Completely free",
    downloadStable: "Download APK",
    copyPage: "Copy page link",
    copiedPage: "Copied",
    mirrorPre: "Slow or failing? Try the ",
    mirrorLink: "backup download (GitHub)",
    mirrorPost: ".",
    qrAlt: "JiaRenXia download page QR code",
    qrText: "Scan to download on your phone",
    notesSummary: "What's new in this release",
    stepsH: "First install: follow these steps",
    steps: [
      "Tap “Download APK”. When it finishes, open the file from the notification or the Downloads folder.",
      "If the phone blocks “unknown sources”, tap Settings, allow installs from this source, then go back and tap Install.",
      "Xiaomi, Huawei, OPPO, vivo and other phones warn about apps installed outside their app stores (“risky” or “not scanned”); some make you wait a few seconds or sign in. Check that the name is JiaRenXia, then choose to continue.",
      "On Huawei phones with Pure Mode on, turn it off temporarily first. Huawei phones already on HarmonyOS NEXT can't install Android apps, so JiaRenXia isn't available there yet.",
      "For future versions, just download and install over the old one — your family data stays.",
    ],
    stepsNote:
      "To send it to family: copy the page link and paste it into WeChat. If they open it inside WeChat, they should tap ··· at the top right and choose “Open in browser” before downloading.",
    helpH: "Trouble installing? Questions?",
    helpP: "Join the JiaRenXia QQ group (in Chinese) to ask the author directly; new versions are announced there too.",
    helpMailPre: "Or email ",
    moreSummary: "Other builds and checksums",
    shaReleasePre: "Release SHA-256 (to verify the file): ",
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
    sourcePre: "The source code of JiaRenXia and ShouHuXia is published under GPL-3.0: ",
    sourceJrx: "JiaRenXia",
    sourceShx: "ShouHuXia",
    sourcePost: ".",
    gxPre: "The guardian feature lives in a separate app, ShouHuXia (stable release — get it on the ",
    gxLink: "ShouHuXia",
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
  const [wxOpen, setWxOpen] = useState(false);
  const lang = useLang();
  const t = T[lang];
  const to = useTo();
  const inWeChat = useInWeChat();
  useEffect(() => {
    setDownloadSource(downloadSourceFromSearch(window.location.search));
  }, []);
  const sizeMb = (SITE.apkSizeBytes / 1024 / 1024).toFixed(1);
  // 微信内统一拦截安装包下载：阻止跳转并弹「请用浏览器打开」
  const wxGuard: MouseEventHandler<HTMLAnchorElement> | undefined = inWeChat
    ? (e) => { e.preventDefault(); setWxOpen(true); }
    : undefined;

  return (
    <>
      {inWeChat && <WeChatBanner />}
      <h1>{t.h1}</h1>
      <p className="muted">{t.sub}</p>

      <div className="card dl-main">
        {SITE.apkVersion ? (
          <>
            <div className="dl-main-grid">
              <div>
                <h3>{t.stableTitle} {SITE.apkVersion}</h3>
                <p className="muted">{sizeMb}{t.sizeSuffix}</p>
                <div className="dl-actions">
                  <a
                    className="btn btn-primary btn-big"
                    href={SITE.apkDownloadUrl + `?src=${downloadSource}`}
                    download
                    rel="nofollow noopener"
                    onClick={wxGuard}
                  >
                    {t.downloadStable}
                  </a>
                  <CopyButton
                    text={`${SITE_URL}${lang === "en" ? "/en" : ""}/download`}
                    label={t.copyPage}
                    copiedLabel={t.copiedPage}
                  />
                </div>
                {SITE.apkGithubUrl && (
                  <p className="muted">
                    {t.mirrorPre}
                    <a href={SITE.apkGithubUrl} rel="nofollow noopener" onClick={wxGuard}>{t.mirrorLink}</a>
                    {t.mirrorPost}
                  </p>
                )}
              </div>
              <div className="dl-main-qr">
                <img src="/qr-download.svg" alt={t.qrAlt} width={132} height={132} />
                <span className="muted">{t.qrText}</span>
              </div>
            </div>
            {lang === "zh" && (
              <details className="dl-notes">
                <summary>{t.notesSummary}</summary>
                <p>{SITE.apkNotes}</p>
              </details>
            )}
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

      <div className="card dl-steps">
        <h3>{t.stepsH}</h3>
        <ol>
          {t.steps.map((s) => <li key={s}>{s}</li>)}
        </ol>
        <p className="muted">{t.stepsNote}</p>
      </div>

      <div className="card dl-help">
        <h3>{t.helpH}</h3>
        <p>{t.helpP}</p>
        <QqGroup />
        <p className="muted">
          {t.helpMailPre}
          <a href={`mailto:${SITE.contactEmail}`}>{SITE.contactEmail}</a>
          {lang === "en" ? "." : "。"}
        </p>
      </div>

      <details className="dl-more">
        <summary>{t.moreSummary}</summary>
        {SITE.apkVersion && (
          <p className="muted">
            {t.shaReleasePre}
            <code style={{ wordBreak: "break-all" }}>{SITE.apkSha256}</code>
          </p>
        )}
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
              rel="nofollow noopener"
              onClick={wxGuard}
            >
              {t.downloadBeta}
            </a>
          </>
        ) : (
          <p className="muted">{t.betaMissing}</p>
        )}
        {APK_DEMO.version && (
          <>
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
                rel="nofollow noopener"
                onClick={wxGuard}
              >
                {t.downloadDemoGroom}
              </a>{" "}
              <a
                className="btn btn-soft btn-big"
                href={APK_DEMO.bride.file + `?src=${downloadSource}`}
                download
                rel="nofollow noopener"
                onClick={wxGuard}
              >
                {t.downloadDemoBride}
              </a>
            </p>
          </>
        )}
      </details>

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

      {wxOpen && <WeChatModal onClose={() => setWxOpen(false)} />}
    </>
  );
}
