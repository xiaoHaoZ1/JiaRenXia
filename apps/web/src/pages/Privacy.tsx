import { useLang } from "../i18n";

const T = {
  zh: {
    h1: "隐私政策",
    draft: "草稿，尚未经过法律或合规审核。",
    phoneH: "手机里的家庭资料",
    phoneP:
      "家人侠的全部资料（家庭成员、称谓、关系、生日、家庭事项、备份文件、共享内容）只保存在你的手机本地。" +
      "App 默认不联网、不上传，本网站也收不到它们。「帮助与关于」里的「联网检查更新」需要你手动开启，" +
      "仅用于查询新版本和下载安装包，仍不上传任何资料，可随时关闭。下载安装包时，下载服务只按日期、" +
      "安装包和匿名来源（例如官网或某条宣传内容）汇总下载次数，不记录 IP 地址、设备信息或用户身份。",
    siteH: "本网站",
    siteP:
      "本站是纯静态的说明页面：没有账号、没有登录、没有后端接口，不使用 Cookie、统计、广告或第三方脚本，不收集任何信息。",
    contactH: "联系",
    contactP: "隐私相关问题请写信到 ",
  },
  en: {
    h1: "Privacy policy",
    draft: "Draft — not yet reviewed for legal or compliance purposes.",
    phoneH: "Family data on your phone",
    phoneP:
      "Everything JiaRenXia holds (family members, kinship titles, relationships, birthdays, family " +
      "matters, backup files and shared content) is stored only on your phone. The app is offline by " +
      "default and uploads nothing — this website can't receive any of it either. The \"check for " +
      "updates online\" option under \"Help & about\" is off unless you enable it; it only queries the " +
      "latest version and downloads the package — still no data is uploaded, and it can be turned off " +
      "anytime. When a package is downloaded, the download service only counts downloads by date, " +
      "package and anonymous source (e.g. the website or a promo link); it does not record IP " +
      "addresses, device info or user identity.",
    siteH: "This website",
    siteP:
      "This site is a purely static informational page: no accounts, no login, no backend APIs, and " +
      "no cookies, analytics, ads or third-party scripts. It collects nothing.",
    contactH: "Contact",
    contactP: "For privacy questions, write to ",
  },
} as const;

export default function Privacy() {
  const lang = useLang();
  const t = T[lang];
  return (
    <>
      <h1>{t.h1}</h1>
      <p className="note">{t.draft}</p>
      <div className="card">
        <h3>{t.phoneH}</h3>
        <p>{t.phoneP}</p>
        <h3>{t.siteH}</h3>
        <p>{t.siteP}</p>
        <h3>{t.contactH}</h3>
        <p>
          {t.contactP}
          <a href="mailto:2107290326@qq.com">2107290326@qq.com</a>{lang === "en" ? "." : "。"}
        </p>
      </div>
    </>
  );
}
