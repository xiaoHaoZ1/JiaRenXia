// 站点常量：本站为纯静态页面，部署在 Cloudflare Pages。发新版 APK 时更新
// 这里的版本，并把安装包按「名字-版本.apk」上传到 R2 桶 jrx-download
//（wrangler r2 object put，见 docs/deployment/LOCAL_WINDOWS.md）。

export const DOWNLOAD_BASE = "https://dl.jiarenxia.com";

export const SITE = {
  contactEmail: "2107290326@qq.com",
  /** 公开源码仓库（GPL-3.0）。 */
  sourceUrl: "https://github.com/xiaoHaoZ1/JiaRenXia",
  /** 当前可下载的 Android 版本；留空表示尚未发布。 */
  apkVersion: "2.1.1",
  /** 与安装包一致的 versionCode；App 端「联网检查更新」按它判断新旧。 */
  apkVersionCode: 50,
  /** 本版更新说明（App 更新弹窗与下载页都可能展示；发布时与安装包同步更新）。 */
  apkNotes: "「家事时间线」桌面插件重设计：按日分组，今天要办的事一眼看到；你和另一半的相恋天数独立成卡。默认格子加大到 4×3。",
  /** APK 文件大小与 SHA-256（供下载页核对；发布时与安装包同步更新）。 */
  apkSizeBytes: 3_736_119,
  apkSha256: "b30fd7a97306655854a62c4db80043d9bc2c6dc2435bd4a92679a4ad6fec4086",
  /** APK 下载地址：下载服务 dl.jiarenxia.com（Worker jrx-dl + R2 jrx-download），
   * 按版本命名；按 ?src=（web/update/legacy/direct）分日期计数到 D1。 */
  apkDownloadUrl: `${DOWNLOAD_BASE}/jiarenxia-2.1.1.apk`,
};

/** 小侠家族页展示的同作者小应用。安装包同样放站点根目录、不入库；
 * 发新版时更新这里的版本/大小/校验值并替换安装包。version 留空表示尚未发布。 */
export const EXTENSIONS = [
  {
    id: "shouhuxia",
    name: "守护侠",
    icon: "shield",
    stage: "测试版",
    description:
      "装在长辈手机上：锁屏后太久没人用手机，先响铃请 TA 确认；没人应答就自动打给紧急联系人，" +
      "还能每天发一条平安短信。不联网、不注册，设置只存在这台手机里。",
    version: "1.0.2",
    released: "2026-10-23",
    whatsNew: "首页只保留一个设置入口",
    sizeBytes: 1_597_039,
    sha256: "5d9a56ecdb687008c81eb240b61ee3ff79328db368074b766b576d0daa25142b",
    /** 安装包地址：下载服务按版本命名 */
    file: `${DOWNLOAD_BASE}/shouhuxia-1.0.2.apk`,
    minAndroid: "8.0",
    note: "测试版：装好后请先打开「测试模式」走一遍，确认能响铃、能拨通，再开启守护。目前仍在真机测试，暂不建议作为唯一的安全保障。",
  },
  {
    id: "pengyouxia",
    name: "朋友侠",
    icon: "friends",
    stage: "测试版",
    description:
      "家人侠的兄弟应用，把家人换成朋友。记下朋友的生日、喜好和近况，农历公历生日都行，" +
      "到点提醒该联系了；通讯录导入、加密备份都有——资料同样只存在你自己的手机里。",
    version: "1.0.0",
    /** 发布日期与本版亮点（展示在卡片上） */
    released: "2026-10-17",
    whatsNew: "首个版本",
    sizeBytes: 2_230_507,
    sha256: "c99cd8399aab6f952b0c155533cde21bc7efb7a0de36bc7ea4dc966df10a4c21",
    /** 安装包地址：下载服务按版本命名 */
    file: `${DOWNLOAD_BASE}/pengyouxia-1.0.0.apk`,
    minAndroid: "8.0",
  },
  {
    id: "chishenme",
    name: "吃什么",
    icon: "wheel",
    stage: "测试版",
    description:
      "一日三餐不知道吃什么，就交给转盘。在家做、出去吃都能转——烤鱼自助烤肉店也行，" +
      "转盘可以自己加、自己改，午晚夜宵到点提醒，还能把结果一键发给微信好友。",
    version: "0.4.1",
    /** 发布日期与本版亮点（展示在卡片上） */
    released: "2026-10-11",
    whatsNew: "大转盘占满屏，整餐逐个转（像老虎机）；动画改成摩擦式减速更顺滑",
    sizeBytes: 1_750_921,
    sha256: "6c7614d01355e208488091f2a5b8928d40a2ef4c87318fe03d52ce720179cc35",
    /** 安装包地址：下载服务按版本命名 */
    file: `${DOWNLOAD_BASE}/chishenme-0.4.1.apk`,
    minAndroid: "8.0",
    /** 可选的安装提示（如实说明签名变更等需要用户知道的事） */
    note: "0.1.2 起换用正式签名：手机若已装更早的测试包，请先卸载再安装（卸载会清掉已改的菜）。",
  },
] as const;
