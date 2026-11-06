// 称谓推导演示：原首页互动演示，移到帮助页（见 Home.tsx 注释）。
// 例子与结果与 core/kinship 引擎测试一致；title 为 null 表示信息不足、不下结论。

import { Fragment, useState } from "react";
import { useLang } from "./i18n";
import "./kinship.css";

/** 按标点（、，；。：？）拆成短语，只在短语之间换行，避免句中断开或末尾单独剩一个字。
 *  英文文案不适用：整句按普通文本流换行。 */
function Phrases({ text }: { text: string }) {
  const lang = useLang();
  if (lang === "en") return <>{text}</>;
  return <>{text.split(/(?<=[、，；。：？])/).map((s, i) => <span key={i} className="phrase">{s}</span>)}</>;
}

interface KinExample {
  path: string[];
  title: string | null;
  she?: boolean;
  note?: string;
}

const KIN_EXAMPLES_ZH: KinExample[] = [
  { path: ["父亲", "哥哥"], title: "伯父", note: "按生日或长幼知道他比父亲大，所以是伯父；比父亲小就是叔叔。" },
  { path: ["母亲", "母亲"], title: "姥姥", she: true, note: "家里叫法不同？给她设置自己家的称呼（比如“外婆”）就行。" },
  { path: ["妻子", "父亲"], title: "岳父" },
  { path: ["妻子", "弟弟"], title: "小舅子" },
  { path: ["姐姐", "儿子"], title: "外甥" },
  { path: ["父亲", "父亲"], title: "爷爷" },
  { path: ["父亲", "兄弟"], title: null, note: "不知道他比父亲大还是小，就分不清伯父和叔叔。家人侠不会乱猜，而是请你补充一下。" },
];

// 英文版称谓保留中文原词（这是家人侠的核心能力），用英文解释关系链与含义
const KIN_EXAMPLES_EN: KinExample[] = [
  { path: ["father", "elder brother"], title: "伯父 (bófù)", note: "Known to be older than your father, so he's 伯父; a younger brother of your father would be 叔叔." },
  { path: ["mother", "mother"], title: "姥姥 (lǎolao)", she: true, note: "Does your family say it differently? Set her custom term (e.g. 外婆) once." },
  { path: ["wife", "father"], title: "岳父 (yuèfù)" },
  { path: ["wife", "younger brother"], title: "小舅子 (xiǎo jiùzi)" },
  { path: ["elder sister", "son"], title: "外甥 (wàisheng)" },
  { path: ["father", "father"], title: "爷爷 (yéye)" },
  { path: ["father", "brother"], title: null, note: "Without knowing whether he's older or younger than your father, 伯父 vs 叔叔 can't be decided — JiaRenXia won't guess; it asks you to fill that in." },
];

const T = {
  zh: {
    h: "称谓怎么算：点个例子试试",
    lead: "录好家人之间的关系，家人侠站在你的角度算出称谓；家里叫法不同，可以自定义。",
    groupLabel: "称谓例子",
    unknownSuffix: "（长幼未知）",
    me: "我",
    linkWord: "的",
    askPre: "",
    askMid: "，你该叫",
    askPost: "",
    he: "他",
    she: "她",
    unknownTitle: "伯父还是叔叔？",
    unknownHint: "补充长幼信息后，可以确定具体称谓",
    foot: "以上为演示。在 App 里，称谓按你录入的家人关系自动推导，堂表亲、姻亲、祖辈的兄弟姐妹也能算。",
  },
  en: {
    h: "How kinship titles are computed — try an example",
    lead: "Once you've recorded how family members relate, JiaRenXia works out the proper Chinese kinship title from your point of view; if your family uses different terms, you can customize them.",
    groupLabel: "Kinship examples",
    unknownSuffix: " (seniority unknown)",
    me: "Me",
    linkWord: "'s ",
    askPre: "Your ",
    askMid: " — you call ",
    askPost: ":",
    he: "him",
    she: "her",
    unknownTitle: "伯父 or 叔叔?",
    unknownHint: "Once seniority is filled in, the exact title can be decided",
    foot: "The above is a demo. In the app, titles are derived automatically from the relationships you record — cousins, in-laws and grandparents' siblings included.",
  },
} as const;

export default function KinshipDemo() {
  const lang = useLang();
  const t = T[lang];
  const examples = lang === "en" ? KIN_EXAMPLES_EN : KIN_EXAMPLES_ZH;
  const [idx, setIdx] = useState(0);
  const ex = examples[idx];
  const chain = ex.path.join(t.linkWord);
  return (
    <div className="kin-demo">
      {/* DOM 顺序为 标题 → 例子按钮 → 演示 → 说明：手机单列时按钮下面紧跟结果；电脑上用网格区域把演示放到右栏 */}
      <div className="kin-head">
        <h2 id="kin-h"><Phrases text={t.h} /></h2>
        <p className="lead"><Phrases text={t.lead} /></p>
      </div>
      <div className="kin-chips" role="group" aria-label={t.groupLabel}>
        {examples.map((e, i) => (
          <button key={e.path.join("") + i} type="button" className="kin-chip"
                  aria-pressed={i === idx} onClick={() => setIdx(i)}>
            {e.path.join(t.linkWord)}{e.title === null ? t.unknownSuffix : ""}
          </button>
        ))}
      </div>

      <div className="kin-stage">
        <div className="kin-chain" aria-hidden="true">
          <span className="kin-node is-me">{t.me}</span>
          {ex.path.map((p, i) => (
            <Fragment key={i}>
              <span className="kin-link">{t.linkWord}</span>
              <span className={i === ex.path.length - 1 ? "kin-node is-target" : "kin-node"}>{p}</span>
            </Fragment>
          ))}
        </div>
        {/* 朗读区域保持不变，只替换里面的内容（换 key 触发淡入动画） */}
        <div aria-live="polite">
          <div className="kin-result" key={idx}>
            <p className="kin-ask">{t.askPre}{chain}{t.askMid}{ex.she ? t.she : t.he}{t.askPost}</p>
            {ex.title ? (
              <p className="kin-title">{ex.title}</p>
            ) : (
              <>
                <p className="kin-title is-unknown">{t.unknownTitle}</p>
                <span className="kin-hint">{t.unknownHint}</span>
              </>
            )}
            {ex.note ? <p className="kin-note"><Phrases text={ex.note} /></p> : null}
          </div>
        </div>
      </div>

      <p className="kin-foot"><Phrases text={t.foot} /></p>
    </div>
  );
}
