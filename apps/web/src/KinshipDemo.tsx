// 称谓推导演示：原首页互动演示，移到帮助页（见 Home.tsx 注释）。
// 例子与结果与 core/kinship 引擎测试一致；title 为 null 表示信息不足、不下结论。

import { Fragment, useState } from "react";
import "./kinship.css";

/** 按标点（、，；。：？）拆成短语，只在短语之间换行，避免句中断开或末尾单独剩一个字。 */
function Phrases({ text }: { text: string }) {
  return <>{text.split(/(?<=[、，；。：？])/).map((s, i) => <span key={i} className="phrase">{s}</span>)}</>;
}

interface KinExample {
  path: string[];
  title: string | null;
  she?: boolean;
  note?: string;
}

const KIN_EXAMPLES: KinExample[] = [
  { path: ["父亲", "哥哥"], title: "伯父", note: "按生日或长幼知道他比父亲大，所以是伯父；比父亲小就是叔叔。" },
  { path: ["母亲", "母亲"], title: "姥姥", she: true, note: "家里叫法不同？给她设置自己家的称呼（比如“外婆”）就行。" },
  { path: ["妻子", "父亲"], title: "岳父" },
  { path: ["妻子", "弟弟"], title: "小舅子" },
  { path: ["姐姐", "儿子"], title: "外甥" },
  { path: ["父亲", "父亲"], title: "爷爷" },
  { path: ["父亲", "兄弟"], title: null, note: "不知道他比父亲大还是小，就分不清伯父和叔叔。家人侠不会乱猜，而是请你补充一下。" },
];

export default function KinshipDemo() {
  const [idx, setIdx] = useState(0);
  const ex = KIN_EXAMPLES[idx];
  const chain = ex.path.join("的");
  return (
    <div className="kin-demo">
      {/* DOM 顺序为 标题 → 例子按钮 → 演示 → 说明：手机单列时按钮下面紧跟结果；电脑上用网格区域把演示放到右栏 */}
      <div className="kin-head">
        <h2 id="kin-h"><Phrases text="称谓怎么算：点个例子试试" /></h2>
        <p className="lead">
          <Phrases text="录好家人之间的关系，家人侠站在你的角度算出称谓；家里叫法不同，可以自定义。" />
        </p>
      </div>
      <div className="kin-chips" role="group" aria-label="称谓例子">
        {KIN_EXAMPLES.map((e, i) => (
          <button key={e.path.join("") + i} type="button" className="kin-chip"
                  aria-pressed={i === idx} onClick={() => setIdx(i)}>
            {e.path.join("的")}{e.title === null ? "（长幼未知）" : ""}
          </button>
        ))}
      </div>

      <div className="kin-stage">
        <div className="kin-chain" aria-hidden="true">
          <span className="kin-node is-me">我</span>
          {ex.path.map((p, i) => (
            <Fragment key={i}>
              <span className="kin-link">的</span>
              <span className={i === ex.path.length - 1 ? "kin-node is-target" : "kin-node"}>{p}</span>
            </Fragment>
          ))}
        </div>
        {/* 朗读区域保持不变，只替换里面的内容（换 key 触发淡入动画） */}
        <div aria-live="polite">
          <div className="kin-result" key={idx}>
            <p className="kin-ask">{chain}，你该叫{ex.she ? "她" : "他"}</p>
            {ex.title ? (
              <p className="kin-title">{ex.title}</p>
            ) : (
              <>
                <p className="kin-title is-unknown">伯父还是叔叔？</p>
                <span className="kin-hint">补充长幼信息后，可以确定具体称谓</span>
              </>
            )}
            {ex.note ? <p className="kin-note"><Phrases text={ex.note} /></p> : null}
          </div>
        </div>
      </div>

      <p className="kin-foot">
        <Phrases text="以上为演示。在 App 里，称谓按你录入的家人关系自动推导，堂表亲、姻亲、祖辈的兄弟姐妹也能算。" />
      </p>
    </div>
  );
}
