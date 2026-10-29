import { useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { Icon } from "../icons";

type Tone = "self" | "paternal" | "maternal" | "spouse" | "descendant";
type EdgeKind = "parent" | "spouse" | "relative";
type ViewId = "all" | "paternal" | "maternal" | "spouse" | "spousePaternal" | "spouseMaternal";

type DemoPerson = { name: string; title: string; path: string; note: string; tone: Tone };
type LayoutNode = { id: string; x: number; y: number };
type LayoutEdge = { from: string; to: string; kind: EdgeKind };
type DemoView = { label: string; description: string; focus: string; nodes: LayoutNode[]; edges: LayoutEdge[] };

const STAGE_WIDTH = 1120;
const STAGE_HEIGHT = 590;
const NODE_WIDTH = 96;
const NODE_HEIGHT = 76;

const PEOPLE: Record<string, DemoPerson> = {
  zhudi: { name: "朱棣", title: "我", path: "我", note: "明成祖，年号永乐。官网演示家庭以他为观察视角。", tone: "self" },
  zhuyuanzhang: { name: "朱元璋", title: "父亲", path: "父亲", note: "明太祖，年号洪武。", tone: "paternal" },
  ma: { name: "马皇后", title: "母亲", path: "母亲", note: "孝慈高皇后，幼年由郭子兴夫妇抚养。", tone: "maternal" },
  guo: { name: "郭子兴", title: "姥爷", path: "母亲的父亲", note: "濠州红巾军元帅，马皇后养父。", tone: "maternal" },
  zhangfu: { name: "张夫人", title: "姥姥", path: "母亲的母亲", note: "郭子兴妻，马皇后养母。", tone: "maternal" },
  guotianxu: { name: "郭天叙", title: "舅舅", path: "母亲的兄弟", note: "郭子兴之子，马皇后养兄弟。", tone: "maternal" },
  zhuxinglong: { name: "朱兴隆", title: "伯父", path: "父亲的哥哥", note: "朱元璋长兄，追封南昌王。", tone: "paternal" },
  wang: { name: "王氏", title: "伯母", path: "父亲的哥哥的妻子", note: "南昌王妃。", tone: "paternal" },
  zhuwenzheng: { name: "朱文正", title: "堂哥", path: "伯父的儿子", note: "洪都保卫战主将。", tone: "paternal" },
  zhushouqian: { name: "朱守谦", title: "堂侄", path: "堂哥的儿子", note: "靖江王，藩于桂林。", tone: "paternal" },
  caoguo: { name: "曹国长公主", title: "姑姑", path: "父亲的姐姐", note: "朱元璋之姐，李文忠之母。", tone: "paternal" },
  lizhen: { name: "李贞", title: "姑父", path: "姑姑的丈夫", note: "曹国公、驸马都尉。", tone: "paternal" },
  liwenzhong: { name: "李文忠", title: "表哥", path: "姑姑的儿子", note: "曹国公，明初名将。", tone: "paternal" },
  zhubiao: { name: "朱标", title: "哥哥", path: "哥哥", note: "懿文太子。", tone: "paternal" },
  chang: { name: "常氏", title: "嫂子", path: "哥哥的妻子", note: "懿文太子妃，常遇春之女。", tone: "paternal" },
  zhuyunwen: { name: "朱允炆", title: "侄子", path: "哥哥的儿子", note: "建文帝。", tone: "paternal" },
  xuhou: { name: "徐皇后", title: "妻子", path: "妻子", note: "仁孝文皇后，徐达长女。", tone: "spouse" },
  xuda: { name: "徐达", title: "岳父", path: "妻子的父亲", note: "中山王，明初开国名将。", tone: "spouse" },
  xieshi: { name: "谢氏", title: "岳母", path: "妻子的母亲", note: "徐达继室。", tone: "spouse" },
  xuhuizu: { name: "徐辉祖", title: "小舅子", path: "妻子的弟弟", note: "袭魏国公，靖难时率兵抗燕。", tone: "spouse" },
  xuzengshou: { name: "徐增寿", title: "小舅子", path: "妻子的弟弟", note: "追封定国公。", tone: "spouse" },
  xushi: { name: "徐氏", title: "妻子的妹妹", path: "妻子的妹妹，也是弟弟朱桂的妻子", note: "代王妃；同一个人在家庭中可能有多条关系路径。", tone: "spouse" },
  zhugaochi: { name: "朱高炽", title: "儿子", path: "儿子", note: "明仁宗，年号洪熙。", tone: "descendant" },
  zhangshi: { name: "张氏", title: "儿媳", path: "儿子的妻子", note: "诚孝昭皇后。", tone: "descendant" },
  zhuzhanji: { name: "朱瞻基", title: "孙子", path: "儿子的儿子", note: "明宣宗，年号宣德。", tone: "descendant" },
  sunshi: { name: "孙氏", title: "孙媳", path: "孙子的妻子", note: "孝恭章皇后。", tone: "descendant" },
  zhuqizhen: { name: "朱祁镇", title: "曾孙", path: "儿子的儿子的儿子", note: "明英宗，年号正统、天顺。", tone: "descendant" },
  zhugaochu: { name: "朱高煦", title: "儿子", path: "儿子", note: "汉王。", tone: "descendant" },
  weishi: { name: "韦氏", title: "儿媳", path: "儿子的妻子", note: "汉王妃。", tone: "descendant" },
  zhuzhanhe: { name: "朱瞻壑", title: "孙子", path: "儿子的儿子", note: "汉王世子。", tone: "descendant" },
  yongan: { name: "永安公主", title: "女儿", path: "女儿", note: "朱棣长女。", tone: "descendant" },
  yuanrong: { name: "袁容", title: "女婿", path: "女儿的丈夫", note: "广平侯、驸马都尉。", tone: "descendant" },
};

const VIEWS: Record<ViewId, DemoView> = {
  all: {
    label: "我的家庭", description: "以朱棣为“我”，先看父母、配偶、兄嫂和四代子孙。", focus: "zhudi",
    nodes: [
      { id: "guo", x: 650, y: 54 }, { id: "zhangfu", x: 770, y: 54 },
      { id: "zhuyuanzhang", x: 470, y: 170 }, { id: "ma", x: 620, y: 170 }, { id: "guotianxu", x: 850, y: 170 },
      { id: "chang", x: 160, y: 300 }, { id: "zhubiao", x: 280, y: 300 },
      { id: "zhudi", x: 500, y: 300 }, { id: "xuhou", x: 620, y: 300 },
      { id: "zhuyunwen", x: 220, y: 430 },
      { id: "zhugaochi", x: 410, y: 430 }, { id: "zhangshi", x: 530, y: 430 },
      { id: "zhugaochu", x: 665, y: 430 }, { id: "weishi", x: 785, y: 430 },
      { id: "yongan", x: 925, y: 430 }, { id: "yuanrong", x: 1045, y: 430 },
      { id: "zhuzhanji", x: 470, y: 548 }, { id: "zhuzhanhe", x: 725, y: 548 },
    ],
    edges: [
      { from: "guo", to: "zhangfu", kind: "spouse" }, { from: "guo", to: "ma", kind: "parent" },
      { from: "guo", to: "guotianxu", kind: "parent" }, { from: "zhuyuanzhang", to: "ma", kind: "spouse" },
      { from: "zhuyuanzhang", to: "zhubiao", kind: "parent" }, { from: "zhuyuanzhang", to: "zhudi", kind: "parent" },
      { from: "chang", to: "zhubiao", kind: "spouse" }, { from: "zhubiao", to: "zhuyunwen", kind: "parent" },
      { from: "zhudi", to: "xuhou", kind: "spouse" }, { from: "zhudi", to: "zhugaochi", kind: "parent" },
      { from: "zhudi", to: "zhugaochu", kind: "parent" }, { from: "zhudi", to: "yongan", kind: "parent" },
      { from: "zhugaochi", to: "zhangshi", kind: "spouse" }, { from: "zhugaochi", to: "zhuzhanji", kind: "parent" },
      { from: "zhugaochu", to: "weishi", kind: "spouse" }, { from: "zhugaochu", to: "zhuzhanhe", kind: "parent" },
      { from: "yongan", to: "yuanrong", kind: "spouse" },
    ],
  },
  paternal: {
    label: "父系", description: "父亲朱元璋一侧：伯父、姑姑、堂表亲和后人。", focus: "zhuyuanzhang",
    nodes: [
      { id: "zhuyuanzhang", x: 540, y: 70 }, { id: "zhudi", x: 540, y: 205 },
      { id: "zhuxinglong", x: 160, y: 205 }, { id: "wang", x: 280, y: 205 },
      { id: "caoguo", x: 780, y: 205 }, { id: "lizhen", x: 900, y: 205 },
      { id: "zhuwenzheng", x: 220, y: 350 }, { id: "liwenzhong", x: 840, y: 350 },
      { id: "zhushouqian", x: 220, y: 505 },
    ],
    edges: [
      { from: "zhuyuanzhang", to: "zhudi", kind: "parent" },
      { from: "zhuxinglong", to: "wang", kind: "spouse" }, { from: "zhuxinglong", to: "zhuwenzheng", kind: "parent" },
      { from: "zhuwenzheng", to: "zhushouqian", kind: "parent" },
      { from: "caoguo", to: "lizhen", kind: "spouse" }, { from: "caoguo", to: "liwenzhong", kind: "parent" },
      { from: "zhuyuanzhang", to: "zhuxinglong", kind: "relative" }, { from: "zhuyuanzhang", to: "caoguo", kind: "relative" },
    ],
  },
  maternal: {
    label: "母系", description: "母亲马皇后一侧：养父母郭子兴夫妇和养兄弟。", focus: "ma",
    nodes: [
      { id: "guo", x: 430, y: 90 }, { id: "zhangfu", x: 550, y: 90 },
      { id: "ma", x: 480, y: 260 }, { id: "guotianxu", x: 710, y: 260 }, { id: "zhudi", x: 480, y: 455 },
    ],
    edges: [
      { from: "guo", to: "zhangfu", kind: "spouse" }, { from: "guo", to: "ma", kind: "parent" },
      { from: "guo", to: "guotianxu", kind: "parent" }, { from: "ma", to: "zhudi", kind: "parent" },
    ],
  },
  spouse: {
    label: "配偶系", description: "妻子徐皇后，以及两人的儿女、儿媳女婿和孙辈。", focus: "xuhou",
    nodes: [
      { id: "zhudi", x: 480, y: 75 }, { id: "xuhou", x: 600, y: 75 },
      { id: "zhugaochi", x: 270, y: 235 }, { id: "zhangshi", x: 390, y: 235 },
      { id: "zhugaochu", x: 520, y: 235 }, { id: "weishi", x: 640, y: 235 },
      { id: "yongan", x: 780, y: 235 }, { id: "yuanrong", x: 900, y: 235 },
      { id: "zhuzhanji", x: 330, y: 390 }, { id: "zhuzhanhe", x: 580, y: 390 },
      { id: "zhuqizhen", x: 270, y: 505 }, { id: "sunshi", x: 390, y: 505 },
    ],
    edges: [
      { from: "zhudi", to: "xuhou", kind: "spouse" },
      { from: "zhudi", to: "zhugaochi", kind: "parent" }, { from: "zhudi", to: "zhugaochu", kind: "parent" },
      { from: "zhudi", to: "yongan", kind: "parent" },
      { from: "zhugaochi", to: "zhangshi", kind: "spouse" }, { from: "zhugaochi", to: "zhuzhanji", kind: "parent" },
      { from: "zhugaochu", to: "weishi", kind: "spouse" }, { from: "zhugaochu", to: "zhuzhanhe", kind: "parent" },
      { from: "yongan", to: "yuanrong", kind: "spouse" },
      { from: "zhuzhanji", to: "sunshi", kind: "spouse" }, { from: "zhuzhanji", to: "zhuqizhen", kind: "parent" },
    ],
  },
  spousePaternal: {
    label: "配偶父系", description: "岳父徐达一侧：徐皇后和她的弟弟、妹妹。", focus: "xuda",
    nodes: [
      { id: "xuda", x: 440, y: 85 }, { id: "xieshi", x: 560, y: 85 },
      { id: "xuhou", x: 260, y: 260 }, { id: "xuhuizu", x: 455, y: 260 },
      { id: "xuzengshou", x: 650, y: 260 }, { id: "xushi", x: 845, y: 260 }, { id: "zhudi", x: 260, y: 440 },
    ],
    edges: [
      { from: "xuda", to: "xieshi", kind: "spouse" },
      { from: "xuda", to: "xuhou", kind: "parent" }, { from: "xuda", to: "xuhuizu", kind: "parent" },
      { from: "xuda", to: "xuzengshou", kind: "parent" }, { from: "xuda", to: "xushi", kind: "parent" },
      { from: "xuhou", to: "zhudi", kind: "spouse" },
    ],
  },
  spouseMaternal: {
    label: "配偶母系", description: "岳母谢氏一侧：同一组儿女，换到配偶母系视角查看。", focus: "xieshi",
    nodes: [
      { id: "xuda", x: 440, y: 85 }, { id: "xieshi", x: 560, y: 85 },
      { id: "xuhou", x: 260, y: 260 }, { id: "xuhuizu", x: 455, y: 260 },
      { id: "xuzengshou", x: 650, y: 260 }, { id: "xushi", x: 845, y: 260 }, { id: "zhudi", x: 260, y: 440 },
    ],
    edges: [
      { from: "xuda", to: "xieshi", kind: "spouse" },
      { from: "xieshi", to: "xuhou", kind: "parent" }, { from: "xieshi", to: "xuhuizu", kind: "parent" },
      { from: "xieshi", to: "xuzengshou", kind: "parent" }, { from: "xieshi", to: "xushi", kind: "parent" },
      { from: "xuhou", to: "zhudi", kind: "spouse" },
    ],
  },
};

function edgePath(edge: LayoutEdge, nodes: Map<string, LayoutNode>) {
  const from = nodes.get(edge.from);
  const to = nodes.get(edge.to);
  if (!from || !to) return "";
  if (edge.kind === "spouse") {
    const direction = from.x < to.x ? 1 : -1;
    return `M ${from.x + direction * NODE_WIDTH / 2} ${from.y} H ${to.x - direction * NODE_WIDTH / 2}`;
  }
  if (edge.kind === "relative") {
    return `M ${from.x} ${from.y} C ${from.x} ${from.y + 80}, ${to.x} ${to.y - 80}, ${to.x} ${to.y}`;
  }
  const startY = from.y + NODE_HEIGHT / 2;
  const endY = to.y - NODE_HEIGHT / 2;
  const midY = startY + (endY - startY) / 2;
  return `M ${from.x} ${startY} V ${midY} H ${to.x} V ${endY}`;
}

/** 官网内的固定演示关系图：只使用合成历史人物数据，不读取访问者资料。 */
export default function FamilyGraphDemo() {
  const [activeView, setActiveView] = useState<ViewId>("all");
  const [selectedId, setSelectedId] = useState("zhudi");
  const viewportRef = useRef<HTMLDivElement>(null);
  const view = VIEWS[activeView];
  const selected = PEOPLE[selectedId] ?? PEOPLE[view.focus];
  const nodeMap = new Map(view.nodes.map((node) => [node.id, node]));

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      const viewport = viewportRef.current;
      const focus = nodeMap.get(view.focus);
      if (!viewport || !focus) return;
      const left = Math.max(0, Math.min(STAGE_WIDTH - viewport.clientWidth, focus.x - viewport.clientWidth / 2));
      viewport.scrollTo({ left, behavior: "auto" });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [activeView]);

  const selectView = (id: ViewId) => {
    setActiveView(id);
    setSelectedId(VIEWS[id].focus);
  };

  return (
    <div className="family-demo" aria-label="可交互家庭关系图演示">
      <div className="family-demo-tabs" role="tablist" aria-label="选择家庭分支">
        {(Object.entries(VIEWS) as [ViewId, DemoView][]).map(([id, item]) => (
          <button key={id} type="button" role="tab" aria-selected={activeView === id}
            aria-controls="family-demo-canvas" className={activeView === id ? "is-active" : ""}
            onClick={() => selectView(id)}>{item.label}</button>
        ))}
      </div>
      <div className="family-demo-heading">
        <div><span className="family-demo-eyebrow">当前查看 · {view.label}</span><p>{view.description}</p></div>
        <span className="family-demo-hint">点一位家人，看看怎么称呼</span>
      </div>
      <div id="family-demo-canvas" ref={viewportRef} className="family-demo-viewport" role="tabpanel"
        tabIndex={0} aria-label={`${view.label}关系图，窄屏设备可以左右滑动`}>
        <div className="family-demo-stage" style={{ width: STAGE_WIDTH, height: STAGE_HEIGHT }}>
          <svg className="family-demo-lines" viewBox={`0 0 ${STAGE_WIDTH} ${STAGE_HEIGHT}`} aria-hidden="true">
            {view.edges.map((edge, index) => (
              <path key={`${edge.from}-${edge.to}-${index}`} d={edgePath(edge, nodeMap)} className={`edge-${edge.kind}`} />
            ))}
          </svg>
          {view.nodes.map((node) => {
            const person = PEOPLE[node.id];
            const isSelected = node.id === selectedId;
            return (
              <button key={node.id} type="button"
                className={`family-demo-node tone-${person.tone}${isSelected ? " is-selected" : ""}`}
                style={{ left: node.x, top: node.y }} aria-label={`${person.name}，${person.title}`}
                aria-pressed={isSelected} onClick={() => setSelectedId(node.id)}>
                <span className="family-demo-surname">{person.name.slice(0, 1)}</span>
                <strong>{person.name}</strong><small>{person.title}</small>
              </button>
            );
          })}
        </div>
      </div>
      <div className="family-demo-detail" aria-live="polite">
        <span className={`family-demo-detail-avatar tone-${selected.tone}`}>{selected.name.slice(0, 1)}</span>
        <div className="family-demo-detail-copy">
          <p className="family-demo-detail-name"><strong>{selected.name}</strong><span>{selected.title}</span></p>
          <p className="family-demo-path">{selected.path === "我" ? "当前视角 · 我" : `我 → ${selected.path}`}</p>
          <p className="family-demo-note">{selected.note}</p>
        </div>
        <Link className="family-demo-cta" to="/download">建立我的家庭图<Icon name="arrowRight" size={18} /></Link>
      </div>
      <p className="family-demo-caption">应用内置的明朝演示家庭，不对应真实用户资料。人物关系用于展示家人侠的记录、分支与称谓能力。</p>
    </div>
  );
}
