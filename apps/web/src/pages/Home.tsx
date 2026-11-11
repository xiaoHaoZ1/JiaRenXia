// 首页：首屏（主宣传语 + App 截图）、日常场景、一家人、守护家人侠预告、隐私承诺、结尾引导。
// 称谓演示已移到帮助页（/help#kinship）。截图取自 docs/images（合成数据生成），图中人名均为虚构。

import { Link } from "react-router";
import { Icon } from "../icons";
import type { IconName } from "../icons";
import type { ReactNode } from "react";
import { SITE } from "../site";
import { useLang, useTo } from "../i18n";
import homeShot from "../assets/shots/home.png";
import heroFam1200 from "../assets/photos/hero-family-1200.webp";
import heroFam760 from "../assets/photos/hero-family-760.webp";
import dinner1536 from "../assets/photos/dinner-1536.webp";
import dinner900 from "../assets/photos/dinner-900.webp";
import shouhuxiaIcon from "../assets/shouhuxia-icon.png";
import FamilyGraphDemo from "./FamilyGraphDemo";
import "./home.css";

/** 按标点（、，；。：？）拆成短语，只在短语之间换行，避免句中断开或末尾单独剩一个字。
 *  英文文案不适用：整句按普通文本流换行。 */
function Phrases({ text }: { text: string }) {
  const lang = useLang();
  if (lang === "en") return <>{text}</>;
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
function CalChip({ month, day }: { month: string; day: number }) {
  return (
    <span className="cal">
      <span className="cal-m">{month}</span>
      <span className="cal-d">{day}</span>
    </span>
  );
}

const T = {
  zh: {
    kicker: "家人侠 · 献给有孝心的普通人",
    h1a: "认全家族所有人，",
    h1b: "记住该惦记的事。",
    lead: "家里人是谁、该怎么叫，一张图里看明白；群里说好的事、该给谁打个电话、谁的生日快到了，家人侠也都替你记着。资料只存在你自己的手机里。",
    downloadApp: "下载 App",
    helpLink: "使用帮助",
    releasedMain: `Android 正式版 ${SITE.apkVersion} 已发布`,
    releasedSub: "免费下载",
    unreleasedMain: "Android 正式版尚未发布",
    unreleasedSub: "需要 Android 8.0 及以上",
    heroPhotoAlt: "一家三代人在客厅沙发上的合影",
    homeShotAlt: "家人侠首页截图：今天是母亲的生日，下方是核心家人和他们的称谓",
    floatTaskName: "给姥姥过八十大寿",
    floatTaskSub: "从微信转来 · 到日子提醒你",
    floatCallName: "该给爷爷打电话了",
    floatCallSub: "每周一次 · 上次是 7 天前",
    scenesKicker: "日常里的小事",
    scenesH: "这些事，家人侠帮你记着",
    scene1Title: "群里说的事，转给家人侠",
    scene1Text: "家里群里说好的事，长按转发到家人侠就记下了。说到的日子会自动填上，记错了也能改。",
    scene1Tags: ["家事提醒", "微信转发"],
    scene1Avatar: "妈",
    scene1Msg: "下周六中午给姥姥过八十大寿，都早点到哈",
    scene1CardT: "给姥姥过八十大寿",
    scene1CardS: "周六 · 到日子提醒你",
    scene2Title: "每周给爷爷打个电话",
    scene2Text: "想常联系的家人，定个周期；到点提醒你打个电话，打完点一下，下次重新算。",
    scene2Tags: ["联系提醒"],
    scene2CardT: "该给爷爷打电话了",
    scene2CardS: "每周一次 · 上次是 7 天前",
    scene2Done: "打完了",
    scene3Title: "爸妈的生日，农历的也不忘",
    scene3Text: "生日、纪念日记一次，每年到点提醒，农历生日自动换成当年的日子。放个桌面插件，未来 30 天的事一眼看全。",
    scene3Tags: ["生日纪念日", "桌面插件"],
    month9: "9月",
    month10: "10月",
    scene3MomT: "妈妈 · 生日",
    scene3MomS: "农历八月十六 · 还有 3 天",
    scene3WedT: "爸妈结婚纪念日",
    scene3WedS: "还有 16 天",
    familyPoints: [
      ["tree", "真实的一家人", "不是临时输入关系链；录入一次，父母两边和配偶两边都留在图里。"],
      ["title", "称谓", "信息不够不乱猜；家里叫法不同，可以自己改。"],
      ["share", "分享给家人", "把家人档案发过去，对方选“我是谁”，称谓换成他的叫法。"],
    ] as [IconName, string, string][],
    familyKicker: "一家人",
    familyH: "家里谁是谁，一张图认全",
    familyLead: "父母两边、配偶两边、兄弟姐妹和孩子，按辈分排在一起。点到谁，就能看见名字、称谓和他在家里的位置。",
    familyMore: "看看称谓是怎么算的 →",
    gxTitle: "守护家人侠",
    gxTextPre: "守护功能由独立应用「守护家人侠」提供：装在长辈手机上，手机太久没人动就替你喊家里人。正式版已发布，可以在",
    gxTextLink: "「守护家人侠」",
    gxTextPost: "页了解并下载。",
    privacyH: "家里的事，留在家里",
    privacyLead: "家人的名字、关系、生日和电话，是一个家最私密的东西。所以从一开始，家人侠就只把它们留在你的手机里。",
    privacyLink: "阅读隐私政策",
    privacyPoints: [
      ["cloudOff", "不上传", "家人档案、关系和提醒只存在你的手机里，家人侠不会把它们上传到任何服务器。"],
      ["userCheck", "不用注册", "不用注册、不用登录，默认也不需要联网。"],
      ["inboxOff", "没有后台", "家人侠没有账号和服务器，你的资料没有任何地方可以上传。"],
    ] as [IconName, string, string][],
    ctaH: "从记下第一位家人开始",
    ctaText: "家人侠完全免费，家人档案、提醒、导出表格和高清关系图等功能均可使用。",
    ctaPhotoAlt: "一家人围坐在圆桌旁吃饭",
  },
  en: {
    kicker: "JiaRenXia · for people who care about family",
    h1a: "Know everyone in your family,",
    h1b: "remember what matters.",
    lead: "See who everyone is and what to call them in one family graph. Plans from the family chat, who to call this week, whose birthday is coming — JiaRenXia remembers it all. Your data stays on your own phone.",
    downloadApp: "Get the app",
    helpLink: "User guide",
    releasedMain: `Android release ${SITE.apkVersion} is out`,
    releasedSub: "Free download",
    unreleasedMain: "The Android release is not out yet",
    unreleasedSub: "Requires Android 8.0+",
    heroPhotoAlt: "A family of three generations together on a living-room sofa",
    homeShotAlt: "JiaRenXia home screen: today is mother's birthday, with close family and their kinship titles below",
    floatTaskName: "Grandma's 80th birthday",
    floatTaskSub: "Shared from WeChat · reminds you on the day",
    floatCallName: "Time to call Grandpa",
    floatCallSub: "Weekly · last call 7 days ago",
    scenesKicker: "Everyday things",
    scenesH: "JiaRenXia remembers these for you",
    scene1Title: "Forward family chat plans to JiaRenXia",
    scene1Text: "Plans agreed in the family group chat: forward the message to JiaRenXia and it's recorded. The mentioned date is filled in automatically, and you can fix it if it was read wrong.",
    scene1Tags: ["Family matters", "WeChat forward"],
    scene1Avatar: "Mom",
    scene1Msg: "Grandma's 80th birthday lunch next Saturday — everyone come early!",
    scene1CardT: "Grandma's 80th birthday",
    scene1CardS: "Saturday · reminds you on the day",
    scene2Title: "Call Grandpa every week",
    scene2Text: "For the family you want to keep close, set a cycle; at the right time you're reminded to call, tap when done, and the next round starts over.",
    scene2Tags: ["Call reminders"],
    scene2CardT: "Time to call Grandpa",
    scene2CardS: "Weekly · last call 7 days ago",
    scene2Done: "Done",
    scene3Title: "Parents' birthdays — lunar ones too",
    scene3Text: "Record birthdays and anniversaries once and get reminded every year; lunar birthdays convert to the right day each year. Add the home-screen widget to see the next 30 days at a glance.",
    scene3Tags: ["Birthdays & anniversaries", "Home widget"],
    month9: "Sep",
    month10: "Oct",
    scene3MomT: "Mom · Birthday",
    scene3MomS: "Lunar Aug 16 · in 3 days",
    scene3WedT: "Parents' anniversary",
    scene3WedS: "In 16 days",
    familyPoints: [
      ["tree", "A real family", "Not a one-off relation chain; record once and both your side and your spouse's side stay in the graph."],
      ["title", "Kinship titles", "No guessing when information is missing; if your family uses different terms, customize them."],
      ["share", "Share with family", "Send the family archive over; they pick \"who I am\" and titles switch to their point of view."],
    ] as [IconName, string, string][],
    familyKicker: "One family",
    familyH: "Who's who — the whole family in one graph",
    familyLead: "Both parents' sides, your spouse's side, siblings and children, laid out by generation. Tap anyone to see their name, kinship title, and place in the family.",
    familyMore: "See how kinship titles work →",
    gxTitle: "FamilyGuardian",
    gxTextPre: "The guardian feature lives in a separate app, FamilyGuardian: installed on an elder's phone, it calls you when the phone sits still too long. The stable release is out — learn more and get it on the ",
    gxTextLink: "FamilyGuardian",
    gxTextPost: " page.",
    privacyH: "Family matters stay in the family",
    privacyLead: "Names, relationships, birthdays and phone numbers are among the most private things a family has. That's why JiaRenXia keeps them only on your phone, from day one.",
    privacyLink: "Read the privacy policy",
    privacyPoints: [
      ["cloudOff", "No uploads", "Family records, relationships and reminders live only on your phone — JiaRenXia never uploads them anywhere."],
      ["userCheck", "No sign-up", "No account, no login, and no internet needed by default."],
      ["inboxOff", "No backend", "JiaRenXia has no accounts and no servers — there's simply nowhere your data could go."],
    ] as [IconName, string, string][],
    ctaH: "Start by recording your first family member",
    ctaText: "JiaRenXia is completely free — family records, reminders, spreadsheet export and HD family graph images are all included.",
    ctaPhotoAlt: "A family sharing dinner around a round table",
  },
} as const;

/** 正式版发布状态（site.ts 中维护版本号）。 */
function ReleaseStatus() {
  const t = T[useLang()];
  const [main, sub] = SITE.apkVersion
    ? [t.releasedMain, t.releasedSub]
    : [t.unreleasedMain, t.unreleasedSub];
  return (
    <p className={SITE.apkVersion ? "hero-status is-ok" : "hero-status"}>
      <span className="dot" />
      <span><span className="phrase">{main}</span> · <span className="phrase">{sub}</span></span>
    </p>
  );
}

function Hero() {
  const t = T[useLang()];
  const to = useTo();
  return (
    <section className="home-hero">
      <div className="hero-copy">
        <span className="kicker"><Icon name="heart" size={16} />{t.kicker}</span>
        <h1>
          {t.h1a}
          <span className="accent">{t.h1b}</span>
        </h1>
        <p className="hero-lead"><Phrases text={t.lead} /></p>
        <div className="hero-actions">
          <Link className="btn btn-primary btn-lg" to={to("/download")}>
            {t.downloadApp}<Icon name="arrowRight" size={20} />
          </Link>
          <Link className="btn btn-soft btn-lg" to={to("/help")}>{t.helpLink}</Link>
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
            alt={t.heroPhotoAlt}
            loading="eager" fetchPriority="high" decoding="async" />
        </div>
        <Phone className="phone-hero" src={homeShot} alt={t.homeShotAlt} />
        {/* 浮动小卡片只是装饰，内容取自真实使用场景 */}
        <div className="float-card fc-title" aria-hidden="true">
          <span className="remind-dot fc-ico-tasks"><Icon name="tasks" size={22} /></span>
          <div>
            <div className="fc-name">{t.floatTaskName}</div>
            <div className="fc-sub">{t.floatTaskSub}</div>
          </div>
        </div>
        <div className="float-card fc-remind" aria-hidden="true">
          <span className="remind-dot"><Icon name="bell" size={22} /></span>
          <div>
            <div className="fc-name">{t.floatCallName}</div>
            <div className="fc-sub">{t.floatCallSub}</div>
          </div>
        </div>
      </div>
    </section>
  );
}

/** 场景卡：上半部分用 HTML/CSS 画的小场景（纯装饰），下半部分文案。 */
function SceneCard({ visual, title, text, tags }: {
  visual: ReactNode; title: string; text: string; tags: readonly string[];
}) {
  return (
    <article className="scene-card">
      <div className="scene-visual" aria-hidden="true">{visual}</div>
      <div className="scene-copy">
        <h3>{title}</h3>
        <p><Phrases text={text} /></p>
        <div className="scene-tags">
          {tags.map((s) => <span key={s} className="tag">{s}</span>)}
        </div>
      </div>
    </article>
  );
}

function Scenes() {
  const t = T[useLang()];
  return (
    <section className="home-scenes" aria-labelledby="scenes-h">
      <div className="section-head">
        <span className="kicker">{t.scenesKicker}</span>
        <h2 id="scenes-h"><Phrases text={t.scenesH} /></h2>
      </div>
      <div className="scene-grid">
        <SceneCard
          title={t.scene1Title}
          text={t.scene1Text}
          tags={t.scene1Tags}
          visual={
            <div className="sc-chat">
              <div className="sc-msg">
                <span className="sc-msg-av">{t.scene1Avatar}</span>
                <span className="sc-msg-bub">{t.scene1Msg}</span>
              </div>
              <span className="sc-arrow"><Icon name="arrowRight" size={18} /></span>
              <div className="sc-card">
                <span className="remind-dot fc-ico-tasks"><Icon name="tasks" size={18} /></span>
                <div>
                  <div className="sc-card-t">{t.scene1CardT}</div>
                  <div className="sc-card-s">{t.scene1CardS}</div>
                </div>
              </div>
            </div>
          }
        />
        <SceneCard
          title={t.scene2Title}
          text={t.scene2Text}
          tags={t.scene2Tags}
          visual={
            <div className="sc-call">
              <div className="sc-card">
                <span className="remind-dot"><Icon name="bell" size={18} /></span>
                <div>
                  <div className="sc-card-t">{t.scene2CardT}</div>
                  <div className="sc-card-s">{t.scene2CardS}</div>
                </div>
              </div>
              <span className="sc-done"><Icon name="check" size={14} />{t.scene2Done}</span>
            </div>
          }
        />
        <SceneCard
          title={t.scene3Title}
          text={t.scene3Text}
          tags={t.scene3Tags}
          visual={
            <div className="sc-days">
              <div className="sc-day">
                <CalChip month={t.month9} day={18} />
                <div>
                  <div className="sc-card-t">{t.scene3MomT}</div>
                  <div className="sc-card-s">{t.scene3MomS}</div>
                </div>
              </div>
              <div className="sc-day">
                <CalChip month={t.month10} day={1} />
                <div>
                  <div className="sc-card-t">{t.scene3WedT}</div>
                  <div className="sc-card-s">{t.scene3WedS}</div>
                </div>
              </div>
            </div>
          }
        />
      </div>
    </section>
  );
}

function Family() {
  const t = T[useLang()];
  const to = useTo();
  return (
    <section className="home-family" aria-labelledby="family-h">
      <div className="family-copy">
        <div className="family-intro">
          <span className="kicker"><Icon name="tree" size={16} />{t.familyKicker}</span>
          <h2 id="family-h"><Phrases text={t.familyH} /></h2>
          <p className="family-lead"><Phrases text={t.familyLead} /></p>
          <Link className="family-more" to={to("/help#kinship")}>{t.familyMore}</Link>
        </div>
        <ul className="feature-list">
          {t.familyPoints.map(([icon, ti, d]) => (
            <li key={ti}>
              <span className="feature-ico"><Icon name={icon} /></span>
              <div>
                <h4>{ti}</h4>
                <p><Phrases text={d} /></p>
              </div>
            </li>
          ))}
        </ul>
      </div>
      <FamilyGraphDemo />
    </section>
  );
}

/** 守护家人侠：说明守护由独立应用承担，指向「守护家人侠」页下载。 */
function GuardianXia() {
  const t = T[useLang()];
  const to = useTo();
  return (
    <section className="home-gxia" aria-labelledby="gxia-h">
      <div className="gxia-card">
        <img className="gxia-appicon" src={shouhuxiaIcon} alt="" />
        <div>
          <h3 id="gxia-h">{t.gxTitle}</h3>
          <p>
            {t.gxTextPre}
            <Link to={to("/shouhuxia")}>{t.gxTextLink}</Link>
            {t.gxTextPost}
          </p>
        </div>
      </div>
    </section>
  );
}

function PrivacyBand() {
  const t = T[useLang()];
  const to = useTo();
  return (
    <section className="home-privacy" aria-labelledby="privacy-h">
      <div>
        <span className="privacy-mark"><Icon name="lock" size={36} /></span>
        <h2 id="privacy-h"><Phrases text={t.privacyH} /></h2>
        <p className="lead"><Phrases text={t.privacyLead} /></p>
        <Link to={to("/privacy")}>{t.privacyLink}</Link>
      </div>
      <ul className="privacy-points">
        {t.privacyPoints.map(([icon, ti, d]) => (
          <li key={ti}>
            <Icon name={icon} size={28} className="ico" />
            <div>
              <h3>{ti}</h3>
              <p><Phrases text={d} /></p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

function ClosingCta() {
  const t = T[useLang()];
  const to = useTo();
  return (
    <section className="home-cta">
      <img className="cta-photo"
        src={dinner1536}
        srcSet={`${dinner900} 900w, ${dinner1536} 1536w`}
        sizes="(max-width: 960px) 92vw, 960px"
        width={1536} height={1024}
        alt={t.ctaPhotoAlt}
        loading="lazy" decoding="async" />
      <h2>{t.ctaH}</h2>
      <p><Phrases text={t.ctaText} /></p>
      <div className="hero-actions">
        <Link className="btn btn-primary btn-lg" to={to("/download")}>
          {t.downloadApp}<Icon name="arrowRight" size={20} />
        </Link>
        <Link className="btn btn-soft btn-lg" to={to("/help")}>{t.helpLink}</Link>
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
