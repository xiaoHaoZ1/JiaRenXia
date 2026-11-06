import { SITE } from "../site";
import { useLang } from "../i18n";

const T = {
  zh: {
    h1: "用户协议",
    draft: "草稿，尚未经过法律或合规审核。",
    natureH: "软件性质",
    nature1: "家人侠是个人作品，免费提供给个人使用，全部功能不收取任何费用。",
    nature2: "通过「帮助 → 支持作者」的打赏是自愿的，不影响任何功能。",
    nature3Pre: "家人侠",
    nature3Link: "源代码",
    nature3Post:
      "以 GPL-3.0 开源；你可以按该许可使用、修改和分发，但不得冒用「家人侠」名称和图标发布改版。",
    dataH: "数据责任",
    dataP: "家庭资料只保存在你自己的手机里，请自行做好备份；卸载或清除数据后无法找回。",
    otherH: "其他",
    otherP: "本软件按现状提供，作者会持续改进但不作任何担保。如有问题请写信到 ",
  },
  en: {
    h1: "Terms of use",
    draft: "Draft — not yet reviewed for legal or compliance purposes.",
    natureH: "Nature of the software",
    nature1: "JiaRenXia is a personal work, offered free for personal use; every feature costs nothing.",
    nature2: "Tipping the author via \"Help → Support the author\" is voluntary and unlocks nothing.",
    nature3Pre: "JiaRenXia's ",
    nature3Link: "source code",
    nature3Post:
      " is open under GPL-3.0: you may use, modify and distribute it under that license, but you may " +
      "not publish derivative versions under the \"JiaRenXia\" name or icon.",
    dataH: "Your data",
    dataP:
      "Family data is stored only on your own phone — please keep your own backups. " +
      "Uninstalling or clearing data cannot be undone.",
    otherH: "Miscellaneous",
    otherP:
      "The software is provided as is; the author keeps improving it but makes no warranty. " +
      "Questions? Write to ",
  },
} as const;

export default function Terms() {
  const lang = useLang();
  const t = T[lang];
  return (
    <>
      <h1>{t.h1}</h1>
      <p className="note">{t.draft}</p>
      <div className="card">
        <h3>{t.natureH}</h3>
        <ul>
          <li>{t.nature1}</li>
          <li>{t.nature2}</li>
          <li>
            {t.nature3Pre}
            <a href={SITE.sourceUrl} target="_blank" rel="noopener">{t.nature3Link}</a>
            {t.nature3Post}
          </li>
        </ul>
        <h3>{t.dataH}</h3>
        <p>{t.dataP}</p>
        <h3>{t.otherH}</h3>
        <p>
          {t.otherP}
          <a href="mailto:2107290326@qq.com">2107290326@qq.com</a>{lang === "en" ? "." : "。"}
        </p>
      </div>
    </>
  );
}
