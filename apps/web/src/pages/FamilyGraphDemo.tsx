import { useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { Icon } from "../icons";
import { useLang, useTo } from "../i18n";

type Tone = "self" | "paternal" | "maternal" | "spouse" | "descendant";
type EdgeKind = "parent" | "spouse" | "relative";
type ViewId = "all" | "paternal" | "maternal" | "spouse" | "spousePaternal" | "spouseMaternal";

type L10n = { zh: string; en: string };
type DemoPerson = { name: L10n; title: L10n; path: L10n; note: L10n; tone: Tone };
type LayoutNode = { id: string; x: number; y: number };
type LayoutEdge = { from: string; to: string; kind: EdgeKind };
type DemoView = { label: L10n; description: L10n; focus: string; nodes: LayoutNode[]; edges: LayoutEdge[] };

const STAGE_WIDTH = 1120;
const STAGE_HEIGHT = 590;
const NODE_WIDTH = 96;
const NODE_HEIGHT = 76;

// 英文版称谓展示中文原词（家人侠的核心能力）+ 英文路径说明
const PEOPLE: Record<string, DemoPerson> = {
  zhudi: { name: { zh: "朱棣", en: "Zhu Di" }, title: { zh: "我", en: "Me" }, path: { zh: "我", en: "me" }, note: { zh: "明成祖，年号永乐。官网演示家庭以他为观察视角。", en: "The Yongle Emperor. The demo family is viewed from his perspective." }, tone: "self" },
  zhuyuanzhang: { name: { zh: "朱元璋", en: "Zhu Yuanzhang" }, title: { zh: "父亲", en: "父亲" }, path: { zh: "父亲", en: "father" }, note: { zh: "明太祖，年号洪武。", en: "The Hongwu Emperor, founder of Ming." }, tone: "paternal" },
  ma: { name: { zh: "马皇后", en: "Empress Ma" }, title: { zh: "母亲", en: "母亲" }, path: { zh: "母亲", en: "mother" }, note: { zh: "孝慈高皇后，幼年由郭子兴夫妇抚养。", en: "Empress Xiaocigao, raised by the Guo family." }, tone: "maternal" },
  guo: { name: { zh: "郭子兴", en: "Guo Zixing" }, title: { zh: "姥爷", en: "姥爷" }, path: { zh: "母亲的父亲", en: "mother's father" }, note: { zh: "濠州红巾军元帅，马皇后养父。", en: "Red Turban leader; Empress Ma's adoptive father." }, tone: "maternal" },
  zhangfu: { name: { zh: "张夫人", en: "Lady Zhang" }, title: { zh: "姥姥", en: "姥姥" }, path: { zh: "母亲的母亲", en: "mother's mother" }, note: { zh: "郭子兴妻，马皇后养母。", en: "Guo Zixing's wife; Empress Ma's adoptive mother." }, tone: "maternal" },
  guotianxu: { name: { zh: "郭天叙", en: "Guo Tianxu" }, title: { zh: "舅舅", en: "舅舅" }, path: { zh: "母亲的兄弟", en: "mother's brother" }, note: { zh: "郭子兴之子，马皇后养兄弟。", en: "Guo Zixing's son; Empress Ma's adoptive brother." }, tone: "maternal" },
  zhuxinglong: { name: { zh: "朱兴隆", en: "Zhu Xinglong" }, title: { zh: "伯父", en: "伯父" }, path: { zh: "父亲的哥哥", en: "father's elder brother" }, note: { zh: "朱元璋长兄，追封南昌王。", en: "Zhu Yuanzhang's eldest brother, posthumously Prince of Nanchang." }, tone: "paternal" },
  wang: { name: { zh: "王氏", en: "Lady Wang" }, title: { zh: "伯母", en: "伯母" }, path: { zh: "父亲的哥哥的妻子", en: "father's elder brother's wife" }, note: { zh: "南昌王妃。", en: "Princess consort of Nanchang." }, tone: "paternal" },
  zhuwenzheng: { name: { zh: "朱文正", en: "Zhu Wenzheng" }, title: { zh: "堂哥", en: "堂哥" }, path: { zh: "伯父的儿子", en: "paternal uncle's son" }, note: { zh: "洪都保卫战主将。", en: "Commander in the defense of Hongdu." }, tone: "paternal" },
  zhushouqian: { name: { zh: "朱守谦", en: "Zhu Shouqian" }, title: { zh: "堂侄", en: "堂侄" }, path: { zh: "堂哥的儿子", en: "cousin's son" }, note: { zh: "靖江王，藩于桂林。", en: "Prince of Jingjiang, based in Guilin." }, tone: "paternal" },
  caoguo: { name: { zh: "曹国长公主", en: "Princess Caoguo" }, title: { zh: "姑姑", en: "姑姑" }, path: { zh: "父亲的姐姐", en: "father's elder sister" }, note: { zh: "朱元璋之姐，李文忠之母。", en: "Zhu Yuanzhang's sister; Li Wenzhong's mother." }, tone: "paternal" },
  lizhen: { name: { zh: "李贞", en: "Li Zhen" }, title: { zh: "姑父", en: "姑父" }, path: { zh: "姑姑的丈夫", en: "paternal aunt's husband" }, note: { zh: "曹国公、驸马都尉。", en: "Duke of Cao and imperial son-in-law." }, tone: "paternal" },
  liwenzhong: { name: { zh: "李文忠", en: "Li Wenzhong" }, title: { zh: "表哥", en: "表哥" }, path: { zh: "姑姑的儿子", en: "paternal aunt's son" }, note: { zh: "曹国公，明初名将。", en: "Duke of Cao, famed early-Ming general." }, tone: "paternal" },
  zhubiao: { name: { zh: "朱标", en: "Zhu Biao" }, title: { zh: "哥哥", en: "哥哥" }, path: { zh: "哥哥", en: "elder brother" }, note: { zh: "懿文太子。", en: "Crown Prince Yiwen." }, tone: "paternal" },
  chang: { name: { zh: "常氏", en: "Lady Chang" }, title: { zh: "嫂子", en: "嫂子" }, path: { zh: "哥哥的妻子", en: "elder brother's wife" }, note: { zh: "懿文太子妃，常遇春之女。", en: "Crown Princess; daughter of Chang Yuchun." }, tone: "paternal" },
  zhuyunwen: { name: { zh: "朱允炆", en: "Zhu Yunwen" }, title: { zh: "侄子", en: "侄子" }, path: { zh: "哥哥的儿子", en: "elder brother's son" }, note: { zh: "建文帝。", en: "The Jianwen Emperor." }, tone: "paternal" },
  xuhou: { name: { zh: "徐皇后", en: "Empress Xu" }, title: { zh: "妻子", en: "妻子" }, path: { zh: "妻子", en: "wife" }, note: { zh: "仁孝文皇后，徐达长女。", en: "Empress Renxiaowen; Xu Da's eldest daughter." }, tone: "spouse" },
  xuda: { name: { zh: "徐达", en: "Xu Da" }, title: { zh: "岳父", en: "岳父" }, path: { zh: "妻子的父亲", en: "wife's father" }, note: { zh: "中山王，明初开国名将。", en: "Prince of Zhongshan, founding general of Ming." }, tone: "spouse" },
  xieshi: { name: { zh: "谢氏", en: "Lady Xie" }, title: { zh: "岳母", en: "岳母" }, path: { zh: "妻子的母亲", en: "wife's mother" }, note: { zh: "徐达继室。", en: "Xu Da's second wife." }, tone: "spouse" },
  xuhuizu: { name: { zh: "徐辉祖", en: "Xu Huizu" }, title: { zh: "小舅子", en: "小舅子" }, path: { zh: "妻子的弟弟", en: "wife's younger brother" }, note: { zh: "袭魏国公，靖难时率兵抗燕。", en: "Inherited Duke of Wei; fought against Zhu Di in the civil war." }, tone: "spouse" },
  xuzengshou: { name: { zh: "徐增寿", en: "Xu Zengshou" }, title: { zh: "小舅子", en: "小舅子" }, path: { zh: "妻子的弟弟", en: "wife's younger brother" }, note: { zh: "追封定国公。", en: "Posthumously Duke of Ding." }, tone: "spouse" },
  xushi: { name: { zh: "徐氏", en: "Lady Xu" }, title: { zh: "妻子的妹妹", en: "妻子的妹妹" }, path: { zh: "妻子的妹妹，也是弟弟朱桂的妻子", en: "wife's younger sister — also brother Zhu Gui's wife" }, note: { zh: "代王妃；同一个人在家庭中可能有多条关系路径。", en: "Princess consort of Dai; one person can sit on multiple relation paths." }, tone: "spouse" },
  zhugaochi: { name: { zh: "朱高炽", en: "Zhu Gaochi" }, title: { zh: "儿子", en: "儿子" }, path: { zh: "儿子", en: "son" }, note: { zh: "明仁宗，年号洪熙。", en: "The Hongxi Emperor." }, tone: "descendant" },
  zhangshi: { name: { zh: "张氏", en: "Lady Zhang" }, title: { zh: "儿媳", en: "儿媳" }, path: { zh: "儿子的妻子", en: "son's wife" }, note: { zh: "诚孝昭皇后。", en: "Empress Chengxiaozhao." }, tone: "descendant" },
  zhuzhanji: { name: { zh: "朱瞻基", en: "Zhu Zhanji" }, title: { zh: "孙子", en: "孙子" }, path: { zh: "儿子的儿子", en: "son's son" }, note: { zh: "明宣宗，年号宣德。", en: "The Xuande Emperor." }, tone: "descendant" },
  sunshi: { name: { zh: "孙氏", en: "Lady Sun" }, title: { zh: "孙媳", en: "孙媳" }, path: { zh: "孙子的妻子", en: "grandson's wife" }, note: { zh: "孝恭章皇后。", en: "Empress Xiaogongzhang." }, tone: "descendant" },
  zhuqizhen: { name: { zh: "朱祁镇", en: "Zhu Qizhen" }, title: { zh: "曾孙", en: "曾孙" }, path: { zh: "儿子的儿子的儿子", en: "son's son's son" }, note: { zh: "明英宗，年号正统、天顺。", en: "Emperor Yingzong (Zhengtong/Tianshun eras)." }, tone: "descendant" },
  zhugaochu: { name: { zh: "朱高煦", en: "Zhu Gaoxu" }, title: { zh: "儿子", en: "儿子" }, path: { zh: "儿子", en: "son" }, note: { zh: "汉王。", en: "Prince of Han." }, tone: "descendant" },
  weishi: { name: { zh: "韦氏", en: "Lady Wei" }, title: { zh: "儿媳", en: "儿媳" }, path: { zh: "儿子的妻子", en: "son's wife" }, note: { zh: "汉王妃。", en: "Princess consort of Han." }, tone: "descendant" },
  zhuzhanhe: { name: { zh: "朱瞻壑", en: "Zhu Zhanhe" }, title: { zh: "孙子", en: "孙子" }, path: { zh: "儿子的儿子", en: "son's son" }, note: { zh: "汉王世子。", en: "Heir of the Prince of Han." }, tone: "descendant" },
  yongan: { name: { zh: "永安公主", en: "Princess Yong'an" }, title: { zh: "女儿", en: "女儿" }, path: { zh: "女儿", en: "daughter" }, note: { zh: "朱棣长女。", en: "Zhu Di's eldest daughter." }, tone: "descendant" },
  yuanrong: { name: { zh: "袁容", en: "Yuan Rong" }, title: { zh: "女婿", en: "女婿" }, path: { zh: "女儿的丈夫", en: "daughter's husband" }, note: { zh: "广平侯、驸马都尉。", en: "Marquis of Guangping, imperial son-in-law." }, tone: "descendant" },
};

const VIEWS: Record<ViewId, DemoView> = {
  all: {
    label: { zh: "我的家庭", en: "My family" }, description: { zh: "以朱棣为“我”，先看父母、配偶、兄嫂和四代子孙。", en: "With Zhu Di as \"me\": parents, wife, elder brother and four generations of descendants." }, focus: "zhudi",
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
    label: { zh: "父系", en: "Paternal side" }, description: { zh: "父亲朱元璋一侧：伯父、姑姑、堂表亲和后人。", en: "Father Zhu Yuanzhang's side: uncles, aunts, cousins and their descendants." }, focus: "zhuyuanzhang",
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
    label: { zh: "母系", en: "Maternal side" }, description: { zh: "母亲马皇后一侧：养父母郭子兴夫妇和养兄弟。", en: "Mother Empress Ma's side: adoptive parents Guo Zixing and Lady Zhang, and her adoptive brother." }, focus: "ma",
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
    label: { zh: "配偶系", en: "Spouse's side" }, description: { zh: "妻子徐皇后，以及两人的儿女、儿媳女婿和孙辈。", en: "Wife Empress Xu, the couple's children and in-laws, and the grandchildren." }, focus: "xuhou",
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
    label: { zh: "配偶父系", en: "Spouse's paternal" }, description: { zh: "岳父徐达一侧：徐皇后和她的弟弟、妹妹。", en: "Father-in-law Xu Da's side: Empress Xu and her brothers and sister." }, focus: "xuda",
    nodes: [
      { id: "xuda", x: 440, y: 85 }, { id: "xieshi", x: 560, y: 85 },
      { id: "zhudi", x: 120, y: 260 }, { id: "xuhou", x: 260, y: 260 },
      { id: "xuhuizu", x: 455, y: 260 }, { id: "xuzengshou", x: 650, y: 260 }, { id: "xushi", x: 845, y: 260 },
    ],
    edges: [
      { from: "xuda", to: "xieshi", kind: "spouse" },
      { from: "xuda", to: "xuhou", kind: "parent" }, { from: "xuda", to: "xuhuizu", kind: "parent" },
      { from: "xuda", to: "xuzengshou", kind: "parent" }, { from: "xuda", to: "xushi", kind: "parent" },
      { from: "xuhou", to: "zhudi", kind: "spouse" },
    ],
  },
  spouseMaternal: {
    label: { zh: "配偶母系", en: "Spouse's maternal" }, description: { zh: "岳母谢氏一侧：同一组儿女，换到配偶母系视角查看。", en: "Mother-in-law Lady Xie's side: the same children, seen from the spouse's maternal view." }, focus: "xieshi",
    nodes: [
      { id: "xuda", x: 440, y: 85 }, { id: "xieshi", x: 560, y: 85 },
      { id: "zhudi", x: 120, y: 260 }, { id: "xuhou", x: 260, y: 260 },
      { id: "xuhuizu", x: 455, y: 260 }, { id: "xuzengshou", x: 650, y: 260 }, { id: "xushi", x: 845, y: 260 },
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

const T = {
  zh: {
    aria: "可交互家庭关系图演示",
    tablistLabel: "选择家庭分支",
    eyebrow: "当前查看 · ",
    hint: "点一位家人，看看怎么称呼",
    viewportLabelSuffix: "关系图，窄屏设备可以左右滑动",
    detailMe: "当前视角 · 我",
    detailPathPre: "我 → ",
    cta: "建立我的家庭图",
    caption: "应用内置的明朝演示家庭，不对应真实用户资料。人物关系用于展示家人侠的记录、分支与称谓能力。",
  },
  en: {
    aria: "Interactive family graph demo",
    tablistLabel: "Choose a family branch",
    eyebrow: "Now viewing · ",
    hint: "Tap a family member to see what you'd call them",
    viewportLabelSuffix: " family graph — swipe sideways on narrow screens",
    detailMe: "Current view · me",
    detailPathPre: "Me → ",
    cta: "Build my family graph",
    caption: "A demo Ming-dynasty family built into the app — not real user data. It shows how JiaRenXia records relationships, branches and kinship titles.",
  },
} as const;

/** 官网内的固定演示关系图：只使用合成历史人物数据，不读取访问者资料。 */
export default function FamilyGraphDemo() {
  const lang = useLang();
  const t = T[lang];
  const to = useTo();
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
    <div className="family-demo" aria-label={t.aria}>
      <div className="family-demo-tabs" role="tablist" aria-label={t.tablistLabel}>
        {(Object.entries(VIEWS) as [ViewId, DemoView][]).map(([id, item]) => (
          <button key={id} type="button" role="tab" aria-selected={activeView === id}
            aria-controls="family-demo-canvas" className={activeView === id ? "is-active" : ""}
            onClick={() => selectView(id)}>{item.label[lang]}</button>
        ))}
      </div>
      <div className="family-demo-heading">
        <div><span className="family-demo-eyebrow">{t.eyebrow}{view.label[lang]}</span><p>{view.description[lang]}</p></div>
        <span className="family-demo-hint">{t.hint}</span>
      </div>
      <div id="family-demo-canvas" ref={viewportRef} className="family-demo-viewport" role="tabpanel"
        tabIndex={0} aria-label={`${view.label[lang]}${t.viewportLabelSuffix}`}>
        <div className="family-demo-stage" style={{ width: STAGE_WIDTH, height: STAGE_HEIGHT }}>
          <svg className="family-demo-lines" viewBox={`0 0 ${STAGE_WIDTH} ${STAGE_HEIGHT}`} aria-hidden="true">
            {view.edges.map((edge, index) => (
              <path key={`${edge.from}-${edge.to}-${index}`} d={edgePath(edge, nodeMap)} className={`edge-${edge.kind}`} />
            ))}
          </svg>
          {view.nodes.map((node) => {
            const person = PEOPLE[node.id];
            const isSelected = node.id === selectedId;
            const name = person.name[lang];
            return (
              <button key={node.id} type="button"
                className={`family-demo-node tone-${person.tone}${isSelected ? " is-selected" : ""}`}
                style={{ left: node.x, top: node.y }} aria-label={`${name}，${person.title[lang]}`}
                aria-pressed={isSelected} onClick={() => setSelectedId(node.id)}>
                <span className="family-demo-surname">{name.slice(0, 1)}</span>
                <strong>{name}</strong><small>{person.title[lang]}</small>
              </button>
            );
          })}
        </div>
      </div>
      <div className="family-demo-detail" aria-live="polite">
        <span className={`family-demo-detail-avatar tone-${selected.tone}`}>{selected.name[lang].slice(0, 1)}</span>
        <div className="family-demo-detail-copy">
          <p className="family-demo-detail-name"><strong>{selected.name[lang]}</strong><span>{selected.title[lang]}</span></p>
          <p className="family-demo-path">{selected.path.zh === "我" ? t.detailMe : `${t.detailPathPre}${selected.path[lang]}`}</p>
          <p className="family-demo-note">{selected.note[lang]}</p>
        </div>
        <Link className="family-demo-cta" to={to("/download")}>{t.cta}<Icon name="arrowRight" size={18} /></Link>
      </div>
      <p className="family-demo-caption">{t.caption}</p>
    </div>
  );
}
