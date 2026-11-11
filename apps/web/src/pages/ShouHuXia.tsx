// 守护家人侠单品宣传页：首屏（图标 + 主宣传语 + CSS 画的响铃确认手机）、
// 工作原理四步、功能清单、丑话说在前面、下载卡（版本/校验/安装步骤/扫码）、
// 隐私三点。版本与校验值集中在 site.ts 的 SHOUHUXIA。

import { useState } from "react";
import { SHOUHUXIA } from "../site";
import { SITE_URL } from "../seo";
import { Icon } from "../icons";
import type { IconName } from "../icons";
import { useLang } from "../i18n";
import shouhuxiaIcon from "../assets/shouhuxia-icon.png";
import "./home.css";
import "./shouhuxia.css";

const T = {
  zh: {
    kicker: "守护家人侠 · 装在长辈手机上的守夜人",
    h1a: "手机太久",
    h1a2: "没人动，",
    h1b: "它会替 TA 喊你。",
    lead: "守护家人侠装在独居长辈的手机上：锁屏后太久没人用，先大声响铃请 TA 确认；还是没人应答，就先给紧急联系人群发短信，再按顺序拨过去。不联网、不注册，设置只存在这台手机里。",
    downloadBtn: "下载安装包",
    howBtn: "看看它怎么工作",
    releasedMain: `Android 正式版 ${SHOUHUXIA.version} 已发布`,
    releasedSub: "免费",
    mockTitle: "你还好吗？",
    mockOk: "我没事",
    mockSub: "没人确认就联系紧急联系人",
    howKicker: "工作原理",
    howH: "它是怎么守的",
    steps: [
      ["lock", "手机锁屏，开始计时", "锁屏那一刻起倒计时，默认 8 小时、可在 1–24 小时间调；解锁、接打电话都算「在用」，来消息亮屏不算。"],
      ["bell", "到点响铃请确认", "到点仍没人用过手机，大声响铃并弹出全屏确认页；点「我没事」就解除、重新计时。"],
      ["message", "没人应答，先发短信", "确认页等过也没人点，先给全部紧急联系人群发一条紧急短信。"],
      ["phone", "再按顺序拨过去", "顺序就是联系人顺序；有人接通即停，最多两轮，最后把「谁接了 / 无人接听」群发给联系人。"],
    ] as [IconName, string, string][],
    featKicker: "细节上",
    featH: "设好一次，之后不用管",
    feats: [
      ["friends", "紧急联系人 1–3 位", "从通讯录选或手动输入，顺序就是拨打顺序，随时可调整。"],
      ["moon", "夜间免打扰", "设个休息时段，时段内到点的报警顺延到时段结束，不半夜惊扰。"],
      ["message", "每日平安短信", "每天给紧急联系人发一条近况；家人天天收到，就说明守护在正常工作。"],
      ["shield", "四步向导 + 实机演练", "联系人 → 权限 → 实机演练 → 开启，首页主按钮始终指向下一步；演练走一遍真实流程，子女帮长辈设一次就行。"],
    ] as [IconName, string, string][],
    honestKicker: "丑话说在前面",
    honestH: "它有做不到的地方",
    honestPoints: [
      "手机没电、欠费、拔出 SIM 卡、开飞行模式时无法报警。",
      "被守护的手机必须设锁屏（上滑、图案、密码、指纹都行），不然「解锁」信号不可靠。",
      "报警最多拨两轮，情况说明靠短信；建议关掉 SIM 卡 PIN 码，免得重启后要先输 PIN 才能拨号。",
    ],
    dlKicker: "下载",
    dlH: "Android 安装包",
    version: "版本：",
    releasedSuffix: " 更新）",
    whatsNew: "本版更新：",
    size: "大小：",
    requirement: "系统要求：Android ",
    requirementSuffix: " 及以上",
    downloadApk: "下载安装包",
    copied: "已复制",
    copyLink: "复制链接",
    qrAlt: "手机扫码打开本页",
    qrText: "手机扫码打开本页",
    step1: "点「下载安装包」，下完点开文件",
    step2: "系统提示「禁止安装未知来源应用」时，允许浏览器安装一次",
    step3: "回到下载页再点安装；以后新版本直接覆盖装，设置保留",
    shareNote: "要在微信里发给家人：复制本页链接丢群里；对方打不开下载时，点右上角 ··· 选择「在浏览器打开」。",
    firstUse: "首次使用请按首页向导完成权限设置和一次「实机演练」，确认能响铃、能发短信、能拨通后，守护即可开启。",
    privacyH1: "和家人侠一样：",
    privacyH2: "不外传",
    privacyLead: "紧急联系人和设置只存在这台手机里；拨号和短信走运营商网络，只联系你设定的人。",
    privacyPoints: [
      ["cloudOff", "不联网", "守护家人侠本身不需要联网，没有任何账号和服务器。"],
      ["userCheck", "不注册", "装完按向导设好就用，没有登录这回事。"],
      ["lock", "只在本机", "配置存在设备加密存储区，不参与云备份；日志里的手机号一律打码。"],
    ] as [IconName, string, string][],
  },
  en: {
    kicker: "FamilyGuardian · a watchful companion on an elder's phone",
    h1a: "If the phone sits still",
    h1a2: "too long,",
    h1b: "it calls you for them.",
    lead: "FamilyGuardian lives on the phone of an elder who lives alone: if it stays untouched too long after lock, it first rings loudly to ask them to confirm; with no answer, it texts all emergency contacts and dials them in order. Offline, no sign-up — settings stay on the phone.",
    downloadBtn: "Download APK",
    howBtn: "See how it works",
    releasedMain: `Android release ${SHOUHUXIA.version} is out`,
    releasedSub: "Free",
    mockTitle: "Are you OK?",
    mockOk: "I'm fine",
    mockSub: "Emergency contacts will be called if unconfirmed",
    howKicker: "How it works",
    howH: "How it keeps watch",
    steps: [
      ["lock", "Screen locks, timer starts", "The countdown starts when the screen locks — 8 hours by default, adjustable from 1–24h. Unlocking and calls count as activity; a lit-up notification doesn't."],
      ["bell", "Rings to ask for a tap", "If nobody has used the phone by then, it rings loudly and shows a full-screen confirm page; one tap on \"I'm fine\" resets the timer."],
      ["message", "No answer? Texts first", "If the confirm page gets no response, it sends an emergency text to every contact."],
      ["phone", "Then dials in order", "Contacts are called in their listed order; it stops when someone picks up, tries at most two rounds, then texts everyone the outcome."],
    ] as [IconName, string, string][],
    featKicker: "Details",
    featH: "Set it once, then forget it",
    feats: [
      ["friends", "1–3 emergency contacts", "Pick from contacts or type numbers; their order is the call order, adjustable anytime."],
      ["moon", "Night quiet hours", "Set a rest period; alarms falling inside it are postponed until it ends."],
      ["message", "Daily check-in text", "A short status text reaches the contacts every day — receiving it daily means the guard is alive."],
      ["shield", "Four-step wizard + live drill", "Contacts → permissions → live drill → enable. The main button always points to the next step; set it up once for the elder."],
    ] as [IconName, string, string][],
    honestKicker: "Honest limits",
    honestH: "What it cannot do",
    honestPoints: [
      "It can't alert anyone when the phone is dead, out of credit, has no SIM, or is in airplane mode.",
      "The guarded phone must have a screen lock (swipe, pattern, PIN, fingerprint) — otherwise the unlock signal isn't reliable.",
      "At most two call rounds, and the situation is explained by text; removing the SIM PIN is recommended so dialing works after a reboot.",
    ],
    dlKicker: "Download",
    dlH: "Android APK",
    version: "Version: ",
    releasedSuffix: " update)",
    whatsNew: "What's new: ",
    size: "Size: ",
    requirement: "Requirement: Android ",
    requirementSuffix: "+",
    downloadApk: "Download APK",
    copied: "Copied",
    copyLink: "Copy link",
    qrAlt: "Scan the QR code on your phone to open this page",
    qrText: "Scan with your phone to open this page",
    step1: "Tap \"Download APK\", then open the downloaded file",
    step2: "When the system blocks \"unknown sources\", allow the browser to install once",
    step3: "Go back to the download page and install; future versions install over it with settings kept",
    shareNote: "To share with family in WeChat: copy this page's link into the group; if they can't open the download, tap ··· at the top right and choose \"Open in browser\".",
    firstUse: "On first use, finish the home wizard — permissions plus one live drill — to confirm it can ring, text and dial; the guard can then be enabled.",
    privacyH1: "Like JiaRenXia:",
    privacyH2: "nothing leaves the phone",
    privacyLead: "Contacts and settings live only on this phone; calls and texts go over the carrier network, only to the people you chose.",
    privacyPoints: [
      ["cloudOff", "Offline", "FamilyGuardian needs no internet — there are no accounts and no servers."],
      ["userCheck", "No sign-up", "Finish the wizard and it just works; there is no login."],
      ["lock", "On-device only", "Settings live in device-encrypted storage, excluded from cloud backup; phone numbers are masked in logs."],
    ] as [IconName, string, string][],
  },
} as const;

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

/** 首屏：左侧文案，右侧 CSS 画的手机——全屏「我没事」确认页。 */
function Hero() {
  const t = T[useLang()];
  const en = useLang() === "en";
  return (
    <section className="home-hero shx-hero">
      <div className="hero-copy">
        <span className="kicker"><Icon name="shield" size={16} />{t.kicker}</span>
        <h1>
          <span className="phrase">{t.h1a}</span>
          <span className="phrase">{t.h1a2}</span>
          <span className="accent">{t.h1b}</span>
        </h1>
        <p className="hero-lead">{t.lead}</p>
        <div className="hero-actions">
          <a className="btn btn-primary btn-lg" href="#dl">
            {t.downloadBtn}<Icon name="arrowRight" size={20} />
          </a>
          <a className="btn btn-soft btn-lg" href="#how">{t.howBtn}</a>
        </div>
        <p className="hero-status is-ok">
          <span className="dot" />
          <span><span className="phrase">{t.releasedMain}</span> · <span className="phrase">{t.releasedSub}</span></span>
        </p>
      </div>

      <div className="hero-visual" aria-hidden="true">
        <span className="hero-glow g1" />
        <span className="hero-glow g2" />
        <img className="shx-appicon" src={shouhuxiaIcon} alt="" />
        <div className="shx-phone">
          <div className="shx-screen">
            <span className="shx-ring"><Icon name="bell" size={30} /></span>
            <div className="shx-mock-title">{t.mockTitle}</div>
            <div className="shx-mock-ok">{t.mockOk}</div>
            <div className="shx-mock-sub">{t.mockSub}</div>
          </div>
        </div>
        <div className="float-card fc-title">
          <span className="remind-dot"><Icon name="bell" size={22} /></span>
          <div>
            <div className="fc-name">{en ? "08:00  Ringing…" : "08:00  响铃确认中"}</div>
            <div className="fc-sub">{en ? "untouched for 8h" : "已有 8 小时没人用手机"}</div>
          </div>
        </div>
        <div className="float-card fc-remind">
          <span className="remind-dot fc-ico-tasks"><Icon name="phone" size={22} /></span>
          <div>
            <div className="fc-name">{en ? "Calling: daughter" : "正在拨打：女儿"}</div>
            <div className="fc-sub">{en ? "emergency contact 1 of 2" : "紧急联系人 1/2"}</div>
          </div>
        </div>
      </div>
    </section>
  );
}

/** 工作原理：四张步骤卡，编号 + 图标 + 一句话。 */
function How() {
  const t = T[useLang()];
  return (
    <section className="shx-how" id="how" aria-labelledby="how-h">
      <div className="section-head">
        <span className="kicker">{t.howKicker}</span>
        <h2 id="how-h">{t.howH}</h2>
      </div>
      <ol className="shx-steps">
        {t.steps.map(([icon, ti, d], i) => (
          <li key={ti} className="shx-step">
            <span className="shx-step-n">{i + 1}</span>
            <span className="feature-ico"><Icon name={icon} /></span>
            <h3>{ti}</h3>
            <p>{d}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}

function Feats() {
  const t = T[useLang()];
  return (
    <section className="shx-feats" aria-labelledby="feats-h">
      <div className="section-head">
        <span className="kicker">{t.featKicker}</span>
        <h2 id="feats-h">{t.featH}</h2>
      </div>
      <ul className="feature-list shx-featlist">
        {t.feats.map(([icon, ti, d]) => (
          <li key={ti}>
            <span className="feature-ico"><Icon name={icon} /></span>
            <div>
              <h4>{ti}</h4>
              <p>{d}</p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** 诚实说明：能做到哪、做不到哪，提前讲清楚。 */
function Honest() {
  const t = T[useLang()];
  return (
    <section className="shx-honest card" aria-labelledby="honest-h">
      <span className="kicker">{t.honestKicker}</span>
      <h2 id="honest-h">{t.honestH}</h2>
      <ul className="plain">
        {t.honestPoints.map((p) => <li key={p}>{p}</li>)}
      </ul>
    </section>
  );
}

/** 下载卡：版本/大小/校验 + 按钮 + 二维码 + 安装步骤。 */
function DownloadCard() {
  const t = T[useLang()];
  const en = useLang() === "en";
  const app = SHOUHUXIA;
  const loc = "en" in app && en ? app.en : undefined;
  return (
    <section className="shx-dl card" id="dl" aria-labelledby="dl-h">
      <span className="kicker">{t.dlKicker}</span>
      <h2 id="dl-h">{t.dlH}</h2>
      <p>{loc?.description ?? app.description}</p>
      {app.note ? <p className="note">{loc?.note ?? app.note}</p> : null}
      <ul className="plain">
        <li>
          {t.version}{app.version}
          {app.released ? (en ? ` (${app.released}${t.releasedSuffix}` : `（${app.released}${t.releasedSuffix}`) : null}
        </li>
        {app.whatsNew ? <li>{t.whatsNew}{loc?.whatsNew ?? app.whatsNew}</li> : null}
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
          <CopyButton text={`${SITE_URL}${en ? "/en" : ""}/shouhuxia`} label={t.copyLink} copiedLabel={t.copied} />
        </div>
        <div className="ext-qr">
          <img src="/qr-shouhuxia.svg" alt={t.qrAlt} />
          <span className="muted">{t.qrText}</span>
        </div>
      </div>
      <ol className="plain muted ext-steps">
        <li>{t.step1}</li>
        <li>{t.step2}</li>
        <li>{t.step3}</li>
      </ol>
      <p className="muted">{t.shareNote}</p>
      <p className="note">{t.firstUse}</p>
    </section>
  );
}

function PrivacyBand() {
  const t = T[useLang()];
  return (
    <section className="home-privacy" aria-labelledby="shx-privacy-h">
      <div>
        <span className="privacy-mark"><Icon name="lock" size={36} /></span>
        <h2 id="shx-privacy-h">
          <span className="phrase">{t.privacyH1}</span>
          <span className="phrase">{t.privacyH2}</span>
        </h2>
        <p className="lead">{t.privacyLead}</p>
      </div>
      <ul className="privacy-points">
        {t.privacyPoints.map(([icon, ti, d]) => (
          <li key={ti}>
            <Icon name={icon} size={28} className="ico" />
            <div>
              <h3>{ti}</h3>
              <p>{d}</p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default function ShouHuXiaPage() {
  return (
    <div className="home shx">
      <Hero />
      <How />
      <Feats />
      <Honest />
      <DownloadCard />
      <PrivacyBand />
    </div>
  );
}
