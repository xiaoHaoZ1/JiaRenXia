// 帮助页：给家里老人看的图文教程。每一节配一张 App 截图 + 「跟着点」的步骤。
// 截图与首页同源——真机实拍（英文名合成数据）存在 docs/images/，复制到 assets/shots/；
// App 界面有明显变化时同步更新。

import { SITE } from "../site";
import { useLang } from "../i18n";
import KinshipDemo from "../KinshipDemo";
import homeShot from "../assets/shots/home.png";
import detailShot from "../assets/shots/detail.png";
import affairsShot from "../assets/shots/affairs.png";
import graphShot from "../assets/shots/graph.png";
import "./help.css";

const T = {
  zh: {
    h1: "帮助",
    intro: "给家里老人看的图文教程：每一屏都配了 App 里的样子，跟着点就行。还有搞不懂的地方，写信给作者。",
    shot1Alt: "家庭页截图：顶部是“家庭”标题和“添加家人”按钮，下面是写着姓氏的圆形家人头像与称谓",
    sec1H: "一打开：家人都在这里",
    sec1P: "App 底下三个标签：家庭、家事、我的。打开就是「家庭」，家里人都在这一屏，头像旁的小字写着 TA 和你的关系。",
    sec1Steps: [
      "右上角「添加家人」，填上姓名就把人加进来",
      "点圆圈头像或名字，看 TA 的资料",
      "旁边的放大镜是找人，人多时直接搜",
    ],
    shot2Alt: "家人资料页截图：大头像和名字，下面是称谓、打电话按钮、重要日期与生日提醒开关",
    sec2H: "看一个人的资料",
    sec2P: "点开任何一位家人，就是 TA 的资料页：大头像、名字、该叫什么，一目了然。",
    sec2Steps: [
      "「打电话」直接拨号",
      "「编辑」改姓名、生日这些信息",
      "「添加关系」把 TA 和其他家人连起来（比如：是某某的儿子）",
      "生日提醒打开，还能选当天或提前 1、3、7 天提醒",
    ],
    shot3Alt: "家事页截图：顶部是标题和小铃铛，下面列着今天要办的事和未来 30 天",
    sec3H: "今天要办的事、快到的生日",
    sec3P: "底下点「家事」：今天是几号、谁过生日、什么事到期，下面列出未来 30 天。",
    sec3Steps: [
      "事办完了，点右边的 ✓",
      "顶栏小铃铛设置每天几点提醒、哪类要不要提醒",
      "右下角「新建事项」记缴费、医保等到点要办的事",
    ],
    shot4Alt: "家庭关系图截图：一家人画在一张图上，红框标出“我”，连线上写着称谓，顶部胶囊可选只看父系或母系",
    sec4H: "谁该叫什么，看图就明白",
    sec4P: "家庭页里的「家庭关系图」入口，把一家人自动画在一张图上。",
    sec4Steps: [
      "红框是「我」，连线上写着称谓",
      "顶部胶囊可以只看父系、母系或配偶方",
      "两个手指放大缩小，按住拖动",
    ],
    faq1H: "字太小怎么办",
    faq1P: "「我的 → 外观与字号」里把字调大，整个 App 的字都跟着变大。",
    faq2H: "换手机怎么办",
    faq2Steps: [
      "旧手机「我的 → 备份全部数据」，设个密码记好",
      "把备份文件发到新手机（微信、数据线都行）",
      "新手机装好家人侠，「从备份恢复」选这个文件、输入密码",
    ],
    faq2Note: "注意：卸载会清空资料，换机、卸载之前都先备份。",
    faq3H: "家人侠收费吗？",
    faq3P: "完全免费。家人档案、提醒、导出表格、高清关系图等所有功能都开放使用，没有任何付费门槛。如果它帮到了你，可以点顶部的「支持作者」扫码请作者喝杯咖啡。",
    faq4H: "卸载重装数据还在吗？",
    faq4P: "不在。家庭资料只保存在手机本地，卸载或清除数据前请先导出备份。",
    contactH: "联系",
    contactP: "有任何问题请写信到 ",
  },
  en: {
    h1: "Help",
    intro: "An illustrated tutorial for family elders: every screen shows the real app — just follow along. Still stuck? Write to the author.",
    shot1Alt: "Family tab screenshot: the \"Family\" title and \"Add family\" button on top, then round avatars with kinship titles",
    sec1H: "On opening: the whole family is here",
    sec1P: "Three tabs at the bottom: Family, Matters, Me. It opens on \"Family\" — everyone lives on this screen, with a small title next to each avatar showing how they're related to you.",
    sec1Steps: [
      "\"Add family\" at the top right — fill in a name to add someone",
      "Tap an avatar or name to see their profile",
      "The magnifier next to it is search, handy when there are many people",
    ],
    shot2Alt: "Profile screenshot: large avatar and name, with kinship title, call button, important dates and the birthday-reminder switch",
    sec2H: "View someone's profile",
    sec2P: "Tap any family member to open their profile: big avatar, name and what to call them at a glance.",
    sec2Steps: [
      "\"Call\" dials them directly",
      "\"Edit\" changes name, birthday and other info",
      "\"Add relation\" links them to other family members (e.g. someone's son)",
      "Turn on birthday reminders — same day or 1, 3, 7 days ahead",
    ],
    shot3Alt: "Matters tab screenshot: title and bell icon on top, today's items and the next 30 days below",
    sec3H: "Today's to-dos and upcoming birthdays",
    sec3P: "Tap \"Matters\" at the bottom: today's date, whose birthday it is, what's due, plus the next 30 days below.",
    sec3Steps: [
      "Done with an item? Tap the ✓ on the right",
      "The bell icon in the top bar sets reminder times and which kinds to notify",
      "\"New matter\" at the bottom right records things like bills and insurance renewals",
    ],
    shot4Alt: "Family graph screenshot: the whole family in one graph with \"me\" outlined in red, kinship titles on the edges, and pills on top to show only paternal or maternal side",
    sec4H: "What to call everyone — the graph tells you",
    sec4P: "The \"Family graph\" entry on the Family tab draws everyone automatically.",
    sec4Steps: [
      "The red box is \"me\"; edges carry the kinship titles",
      "The pills on top can show only the paternal side, maternal side, or spouse's side",
      "Pinch with two fingers to zoom, hold and drag to pan",
    ],
    faq1H: "Text too small?",
    faq1P: "In \"Me → Appearance & font size\", drag the slider up — every screen in the app grows together.",
    faq2H: "Switching phones?",
    faq2Steps: [
      "On the old phone: \"Me → Back up all data\", set a password and remember it",
      "Send the backup file to the new phone (WeChat or a cable both work)",
      "Install JiaRenXia on the new phone, choose \"Restore from backup\", pick the file and enter the password",
    ],
    faq2Note: "Note: uninstalling erases the data — always back up before switching phones or uninstalling.",
    faq3H: "Does JiaRenXia cost anything?",
    faq3P: "It's completely free. Family records, reminders, spreadsheet export and HD family graph images are all open — nothing is paywalled. If it helped you, tap \"Support the author\" at the top to buy the author a coffee.",
    faq4H: "Does data survive an uninstall?",
    faq4P: "No. Family data lives only on the phone itself — export a backup before uninstalling or clearing data.",
    contactH: "Contact",
    contactP: "For any questions, write to ",
  },
} as const;

/** 教程配图：带手机外框的截图（外框样式在 styles.css，与首页共用）。 */
function Shot({ src, alt }: { src: string; alt: string }) {
  return (
    <div className="phone" aria-hidden="false">
      <img src={src} alt={alt} width={540} height={1199} loading="lazy" decoding="async" />
    </div>
  );
}

export default function Help() {
  const lang = useLang();
  const t = T[lang];
  return (
    <>
      <h1>{t.h1}</h1>
      <p className="muted">{t.intro}</p>

      <div className="card help-sec">
        <Shot src={homeShot} alt={t.shot1Alt} />
        <div>
          <h3>{t.sec1H}</h3>
          <p>{t.sec1P}</p>
          <ol className="help-steps">
            {t.sec1Steps.map((s) => <li key={s}>{s}</li>)}
          </ol>
        </div>
      </div>

      <div className="card help-sec">
        <Shot src={detailShot} alt={t.shot2Alt} />
        <div>
          <h3>{t.sec2H}</h3>
          <p>{t.sec2P}</p>
          <ol className="help-steps">
            {t.sec2Steps.map((s) => <li key={s}>{s}</li>)}
          </ol>
        </div>
      </div>

      <div className="card help-sec">
        <Shot src={affairsShot} alt={t.shot3Alt} />
        <div>
          <h3>{t.sec3H}</h3>
          <p>{t.sec3P}</p>
          <ol className="help-steps">
            {t.sec3Steps.map((s) => <li key={s}>{s}</li>)}
          </ol>
        </div>
      </div>

      <div className="card help-sec">
        <Shot src={graphShot} alt={t.shot4Alt} />
        <div>
          <h3>{t.sec4H}</h3>
          <p>{t.sec4P}</p>
          <ol className="help-steps">
            {t.sec4Steps.map((s) => <li key={s}>{s}</li>)}
          </ol>
        </div>
      </div>

      <div className="card" id="kinship">
        <KinshipDemo />
      </div>

      <div className="card">
        <h3>{t.faq1H}</h3>
        <p>{t.faq1P}</p>
        <h3>{t.faq2H}</h3>
        <ol className="help-steps">
          {t.faq2Steps.map((s) => <li key={s}>{s}</li>)}
        </ol>
        <p>{t.faq2Note}</p>
      </div>

      <div className="card">
        <h3>{t.faq3H}</h3>
        <p>{t.faq3P}</p>
        <h3>{t.faq4H}</h3>
        <p>{t.faq4P}</p>
        <h3>{t.contactH}</h3>
        <p>
          {t.contactP}
          <a href={`mailto:${SITE.contactEmail}`}>{SITE.contactEmail}</a>{lang === "en" ? "." : "。"}
        </p>
      </div>
    </>
  );
}
