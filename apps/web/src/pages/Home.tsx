// 首页：首屏（主宣传语 + App 截图）、日常场景、一家人、守护侠预告、隐私承诺、结尾引导。
// 称谓演示已移到帮助页（/help#kinship）。截图取自 docs/images（合成数据生成），图中人名均为虚构。

import { Link } from "react-router";
import { Icon } from "../icons";
import type { IconName } from "../icons";
import type { ReactNode } from "react";
import { SITE } from "../site";
import homeShot from "../assets/shots/home.png";
import graphShot from "../assets/shots/graph.png";
import heroFam1200 from "../assets/photos/hero-family-1200.webp";
import heroFam760 from "../assets/photos/hero-family-760.webp";
import dinner1536 from "../assets/photos/dinner-1536.webp";
import dinner900 from "../assets/photos/dinner-900.webp";
import shouhuxiaIcon from "../assets/shouhuxia-icon.png";
import "./home.css";

/** 按标点（、，；。：？）拆成短语，只在短语之间换行，避免句中断开或末尾单独剩一个字。 */
function Phrases({ text }: { text: string }) {
  return <>{text.split(/(?<=[、，；。：？])/).map((s, i) => <span key={i} className="phrase">{s}</span>)}</>;
}

/** 手机外框里的 App 截图。 */
function Phone({ src, alt, className = "", eager = false }: {
  src: string; alt: string; className?: string; eager?: boolean;
}) {
  return (
    <div className={`phone ${className}`}>
      <img src={src} alt={alt} width={540} height={1200}
           loading={eager ? "eager" : "lazy"} decoding="async" />
    </div>
  );
}

/** App 的日历日期块：上为月份色条，下为日期。 */
function CalChip({ month, day }: { month: number; day: number }) {
  return (
    <span className="cal">
      <span className="cal-m">{month}月</span>
      <span className="cal-d">{day}</span>
    </span>
  );
}

/** 正式版发布状态（site.ts 中维护版本号）。 */
function ReleaseStatus() {
  const [main, sub] = SITE.apkVersion
    ? [`Android 正式版 ${SITE.apkVersion} 已发布`, "免费下载"]
    : ["Android 正式版尚未发布", "需要 Android 8.0 及以上"];
  return (
    <p className={SITE.apkVersion ? "hero-status is-ok" : "hero-status"}>
      <span className="dot" />
      <span><span className="phrase">{main}</span> · <span className="phrase">{sub}</span></span>
    </p>
  );
}

function Hero() {
  return (
    <section className="home-hero">
      <div className="hero-copy">
        <span className="kicker"><Icon name="heart" size={16} />家人侠 · 献给有孝心的普通人</span>
        <h1>
          认全家族所有人，
          <span className="accent">记住该惦记的事。</span>
        </h1>
        <p className="hero-lead">
          <Phrases text="家里人是谁、该怎么叫，一张图里看明白；群里说好的事、该给谁打个电话、谁的生日快到了，家人侠也都替你记着。资料只存在你自己的手机里。" />
        </p>
        <div className="hero-actions">
          <Link className="btn btn-primary btn-lg" to="/download">
            下载 App<Icon name="arrowRight" size={20} />
          </Link>
          <Link className="btn btn-soft btn-lg" to="/help">使用帮助</Link>
        </div>
        <ReleaseStatus />
      </div>

      <div className="hero-visual">
        {/* 两层暖色光晕（纯装饰，宽不过视口） */}
        <span className="hero-glow g1" aria-hidden="true" />
        <span className="hero-glow g2" aria-hidden="true" />
        <div className="hero-frame">
          <img
            src={heroFam1200}
            srcSet={`${heroFam760} 760w, ${heroFam1200} 1200w`}
            sizes="(max-width: 860px) 92vw, 520px"
            width={1200} height={800}
            alt="一家三代人在客厅沙发上的合影"
            loading="eager" fetchPriority="high" decoding="async" />
        </div>
        <Phone className="phone-hero" src={homeShot}
               alt="家人侠首页截图：今天是母亲的生日，下方是核心家人和他们的称谓" />
        {/* 浮动小卡片只是装饰，内容取自真实使用场景 */}
        <div className="float-card fc-title" aria-hidden="true">
          <span className="remind-dot fc-ico-tasks"><Icon name="tasks" size={22} /></span>
          <div>
            <div className="fc-name">给姥姥过八十大寿</div>
            <div className="fc-sub">从微信转来 · 到日子提醒你</div>
          </div>
        </div>
        <div className="float-card fc-remind" aria-hidden="true">
          <span className="remind-dot"><Icon name="bell" size={22} /></span>
          <div>
            <div className="fc-name">该给爷爷打电话了</div>
            <div className="fc-sub">每周一次 · 上次是 7 天前</div>
          </div>
        </div>
      </div>
    </section>
  );
}

/** 场景卡：上半部分用 HTML/CSS 画的小场景（纯装饰），下半部分文案。 */
function SceneCard({ visual, title, text, tags }: {
  visual: ReactNode; title: string; text: string; tags: string[];
}) {
  return (
    <article className="scene-card">
      <div className="scene-visual" aria-hidden="true">{visual}</div>
      <div className="scene-copy">
        <h3>{title}</h3>
        <p><Phrases text={text} /></p>
        <div className="scene-tags">
          {tags.map((t) => <span key={t} className="tag">{t}</span>)}
        </div>
      </div>
    </article>
  );
}

function Scenes() {
  return (
    <section className="home-scenes" aria-labelledby="scenes-h">
      <div className="section-head">
        <span className="kicker">日常里的小事</span>
        <h2 id="scenes-h"><Phrases text="这些事，家人侠帮你记着" /></h2>
      </div>
      <div className="scene-grid">
        <SceneCard
          title="群里说的事，转给家人侠"
          text="家里群里说好的事，长按转发到家人侠就记下了。说到的日子会自动填上，记错了也能改。"
          tags={["家事提醒", "微信转发"]}
          visual={
            <div className="sc-chat">
              <div className="sc-msg">
                <span className="sc-msg-av">妈</span>
                <span className="sc-msg-bub">下周六中午给姥姥过八十大寿，都早点到哈</span>
              </div>
              <span className="sc-arrow"><Icon name="arrowRight" size={18} /></span>
              <div className="sc-card">
                <span className="remind-dot fc-ico-tasks"><Icon name="tasks" size={18} /></span>
                <div>
                  <div className="sc-card-t">给姥姥过八十大寿</div>
                  <div className="sc-card-s">周六 · 到日子提醒你</div>
                </div>
              </div>
            </div>
          }
        />
        <SceneCard
          title="每周给爷爷打个电话"
          text="想常联系的家人，定个周期；到点提醒你打个电话，打完点一下，下次重新算。"
          tags={["联系提醒"]}
          visual={
            <div className="sc-call">
              <div className="sc-card">
                <span className="remind-dot"><Icon name="bell" size={18} /></span>
                <div>
                  <div className="sc-card-t">该给爷爷打电话了</div>
                  <div className="sc-card-s">每周一次 · 上次是 7 天前</div>
                </div>
              </div>
              <span className="sc-done"><Icon name="check" size={14} />打完了</span>
            </div>
          }
        />
        <SceneCard
          title="爸妈的生日，农历的也不忘"
          text="生日、纪念日记一次，每年到点提醒，农历生日自动换成当年的日子。放个桌面插件，未来 30 天的事一眼看全。"
          tags={["生日纪念日", "桌面插件"]}
          visual={
            <div className="sc-days">
              <div className="sc-day">
                <CalChip month={9} day={18} />
                <div>
                  <div className="sc-card-t">妈妈 · 生日</div>
                  <div className="sc-card-s">农历八月十六 · 还有 3 天</div>
                </div>
              </div>
              <div className="sc-day">
                <CalChip month={10} day={1} />
                <div>
                  <div className="sc-card-t">爸妈结婚纪念日</div>
                  <div className="sc-card-s">还有 16 天</div>
                </div>
              </div>
            </div>
          }
        />
      </div>
    </section>
  );
}

const FAMILY_POINTS: [IconName, string, string][] = [
  ["tree", "关系图", "可以只看父系、母系或配偶一支，也能导出图片发到家里群。"],
  ["title", "称谓", "信息不够不乱猜；家里叫法不同，可以自己改。"],
  ["share", "分享给家人", "把家人档案发过去，对方选“我是谁”，称谓换成他的叫法。"],
];

function Family() {
  return (
    <section className="home-family" aria-labelledby="family-h">
      <div className="family-copy">
        <span className="kicker"><Icon name="tree" size={16} />一家人</span>
        <h2 id="family-h"><Phrases text="一家人，都在这张图里" /></h2>
        <p className="family-lead">
          <Phrases text="谁是谁、该怎么叫，打开就知道。全家按辈分排开，称谓自动算好；分享给家里人，换成他自己的叫法。" />
        </p>
        <ul className="feature-list">
          {FAMILY_POINTS.map(([icon, t, d]) => (
            <li key={t}>
              <span className="feature-ico"><Icon name={icon} /></span>
              <div>
                <h4>{t}</h4>
                <p><Phrases text={d} /></p>
              </div>
            </li>
          ))}
        </ul>
        <Link className="family-more" to="/help#kinship">看看称谓是怎么算的 →</Link>
      </div>
      <div className="family-visual">
        <Phone className="phone-sm" src={graphShot}
               alt="家庭关系图截图：按辈分排开的家人，含父母、舅舅、岳父岳母、兄弟姐妹、配偶和子女" />
      </div>
    </section>
  );
}

/** 守护侠测试版：说明守护由独立应用承担，指向「小侠家族」页下载。 */
function GuardianXia() {
  return (
    <section className="home-gxia" aria-labelledby="gxia-h">
      <div className="gxia-card">
        <img className="gxia-appicon" src={shouhuxiaIcon} alt="" />
        <div>
          <h3 id="gxia-h">守护侠（测试版）</h3>
          <p>
            守护功能由独立应用「守护侠」提供，给需要守护的家人使用。目前是测试版，可以在
            <Link to="/extensions">「小侠家族」</Link>页下载。
          </p>
        </div>
      </div>
    </section>
  );
}

const PRIVACY_POINTS: [IconName, string, string][] = [
  ["cloudOff", "不上传", "家人档案、关系和提醒只存在你的手机里，家人侠不会把它们上传到任何服务器。"],
  ["userCheck", "不用注册", "不用注册、不用登录，默认也不需要联网。"],
  ["inboxOff", "没有后台", "家人侠没有账号和服务器，你的资料没有任何地方可以上传。"],
];

function PrivacyBand() {
  return (
    <section className="home-privacy" aria-labelledby="privacy-h">
      <div>
        <span className="privacy-mark"><Icon name="lock" size={36} /></span>
        <h2 id="privacy-h"><Phrases text="家里的事，留在家里" /></h2>
        <p className="lead">
          <Phrases text="家人的名字、关系、生日和电话，是一个家最私密的东西。所以从一开始，家人侠就只把它们留在你的手机里。" />
        </p>
        <Link to="/privacy">阅读隐私政策</Link>
      </div>
      <ul className="privacy-points">
        {PRIVACY_POINTS.map(([icon, t, d]) => (
          <li key={t}>
            <Icon name={icon} size={28} className="ico" />
            <div>
              <h3>{t}</h3>
              <p><Phrases text={d} /></p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

function ClosingCta() {
  return (
    <section className="home-cta">
      <img className="cta-photo"
        src={dinner1536}
        srcSet={`${dinner900} 900w, ${dinner1536} 1536w`}
        sizes="(max-width: 960px) 92vw, 960px"
        width={1536} height={1024}
        alt="一家人围坐在圆桌旁吃饭"
        loading="lazy" decoding="async" />
      <h2>从记下第一位家人开始</h2>
      <p>
        <Phrases text="家人侠完全免费，家人档案、提醒、导出表格和高清关系图等功能均可使用。" />
      </p>
      <div className="hero-actions">
        <Link className="btn btn-primary btn-lg" to="/download">
          下载 App<Icon name="arrowRight" size={20} />
        </Link>
        <Link className="btn btn-soft btn-lg" to="/help">使用帮助</Link>
      </div>
    </section>
  );
}

export default function Home() {
  return (
    <div className="home">
      <Hero />
      <Scenes />
      <Family />
      <GuardianXia />
      <PrivacyBand />
      <ClosingCta />
    </div>
  );
}
