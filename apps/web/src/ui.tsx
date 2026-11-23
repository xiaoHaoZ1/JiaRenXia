// 通用界面小组件。

import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { SITE } from "./site";
import { useLang } from "./i18n";

export function Modal({
  title, children, onClose,
}: { title: string; children: ReactNode; onClose: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    ref.current?.querySelector<HTMLElement>("button, [href], input")?.focus();
  }, []);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div
      className="modal-mask"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal" ref={ref}>
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  );
}

/** 复制按钮：点一下复制文本，短暂显示“已复制”（原守护侠页内，公共化）。 */
export function CopyButton({ text, label, copiedLabel }: {
  text: string; label: string; copiedLabel: string;
}) {
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

/** 是否在微信内打开：挂载后才判定（预渲染/水合首帧恒 false，避免不一致）。 */
export function useInWeChat(): boolean {
  const [inWeChat, setInWeChat] = useState(false);
  useEffect(() => {
    setInWeChat(/MicroMessenger/i.test(navigator.userAgent));
  }, []);
  return inWeChat;
}

const WX_T = {
  zh: {
    banner:
      "微信里不能直接下载安装包。请点右上角「···」，选「在浏览器打开」，再点「下载安装包」。",
    title: "请用浏览器打开",
    ok: "知道了",
  },
  en: {
    banner:
      "WeChat can't download app files. Tap ··· at the top right, choose “Open in browser”, then tap “Download APK”.",
    title: "Open in your browser",
    ok: "Got it",
  },
} as const;

/** 微信内提示条：由调用方按 useInWeChat() 决定是否渲染。 */
export function WeChatBanner() {
  const t = WX_T[useLang()];
  return <p className="note wx-banner" role="status">{t.banner}</p>;
}

/** 微信内拦截下载后的说明弹窗（正文与横幅同文案）。 */
export function WeChatModal({ onClose }: { onClose: () => void }) {
  const t = WX_T[useLang()];
  return (
    <Modal title={t.title} onClose={onClose}>
      <p>{t.banner}</p>
      <div className="row">
        <button type="button" className="btn" onClick={onClose}>{t.ok}</button>
      </div>
    </Modal>
  );
}

const QQ_T = {
  zh: {
    num: "QQ 群：",
    copy: "复制群号",
    copied: "已复制",
    open: "打开 QQ 加群",
    qrAlt: "家人侠 QQ 群二维码",
    qrText: "用 QQ 扫码加群",
  },
  en: {
    num: "QQ group: ",
    copy: "Copy number",
    copied: "Copied",
    open: "Open QQ to join",
    qrAlt: "JiaRenXia QQ group QR code",
    qrText: "Scan with QQ to join",
  },
} as const;

/** QQ 群求助块：群号 + 复制 + 加群链接，右侧二维码（<720px 隐藏，手机扫不了自己屏幕）。 */
export function QqGroup() {
  const t = QQ_T[useLang()];
  return (
    <div className="qq-group">
      <div className="qq-group-main">
        <span className="qq-group-num">{t.num}{SITE.qqGroupNumber}</span>
        <CopyButton
          text={SITE.qqGroupNumber}
          label={t.copy}
          copiedLabel={t.copied}
        />
        <a
          className="btn btn-soft"
          href={SITE.qqGroupUrl}
          target="_blank"
          rel="noopener"
        >
          {t.open}
        </a>
      </div>
      <div className="qq-group-qr">
        <img src={SITE.qqGroupQr} alt={t.qrAlt} width={120} />
        <span className="muted">{t.qrText}</span>
      </div>
    </div>
  );
}
