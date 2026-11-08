// 站点常量：本站为纯静态页面，部署在 Cloudflare Pages。发新版 APK 时更新
// 这里的版本，并把安装包按「名字-版本.apk」上传到 R2 桶 jrx-download
//（wrangler r2 object put，见 docs/deployment/LOCAL_WINDOWS.md）。

export const DOWNLOAD_BASE = "https://dl.jiarenxia.com";

export const SITE = {
  contactEmail: "2107290326@qq.com",
  /** 公开源码仓库（GPL-3.0）。 */
  sourceUrl: "https://github.com/xiaoHaoZ1/JiaRenXia",
  /** 守护侠公开源码仓库（GPL-3.0，2026-10-27 开源）。 */
  guardianSourceUrl: "https://github.com/xiaoHaoZ1/ShouHuXia",
  /** 当前可下载的 Android 版本；留空表示尚未发布。 */
  apkVersion: "2.4.2",
  /** 与安装包一致的 versionCode；App 端「联网检查更新」按它判断新旧。 */
  apkVersionCode: 60,
  /** 本版更新说明（App 更新弹窗与下载页都可能展示；发布时与安装包同步更新）。 */
  apkNotes: "修好选生日年份时「换个年代」透出残影的问题，首页「核心家人」两列改为左对齐。包含 2.4.1 全部新功能：「上坟提醒」（清明、中元、寒衣、除夕、冬至和每位家人的忌日都能提醒，新近去世的家人还会提醒头七到七七和百日）、「回家看看」（选好家里有谁、多久回去一次，按见面记录计时，节前与家人生日前也会提醒）、「长辈模式」（装在长辈手机上，只看家人、打电话和最近的日子）、农历按香港天文台历表换算、生日当天一键发祝福短信。",
  /** APK 文件大小与 SHA-256（供下载页核对；发布时与安装包同步更新）。 */
  apkSizeBytes: 3_797_503,
  apkSha256: "2cb8c0c81f428a8bfdb6017c054caa7b0b1c06a6445fa28be5738209a72cf571",
  /** APK 下载地址：下载服务 dl.jiarenxia.com（Worker jrx-dl + R2 jrx-download），
   * 按版本命名；按 ?src=（web/update/legacy/direct）分日期计数到 D1。 */
  apkDownloadUrl: `${DOWNLOAD_BASE}/jiarenxia-2.4.2.apk`,
};

/** 家人侠测试版：debug 构建（applicationId com.jiarenxia.app.debug、debug 签名，
 * 应用名「家人侠-测试版」），可与正式版同时安装、数据互不相通。
 * 发布测试版时：构建 :app:assembleDebug，把 app-debug.apk 按
 * 「jiarenxia-beta-<versionName 去掉 -debug>.apk」上传 R2 桶 jrx-download，
 * 并同步这里的版本/大小/校验值。version 留空表示暂无测试版。 */
export const APK_BETA = {
  /** 安装包的 versionName，含 -debug 后缀 */
  version: "2.4.1-debug",
  sizeBytes: 25_508_728,
  sha256: "454bc2481451dc879e1f9c46c606a493976be7184e5353816ca104565a6735c7",
  file: `${DOWNLOAD_BASE}/jiarenxia-beta-2.4.1.apk`,
};

/** 家人侠演示版：demo 构建（applicationId com.jiarenxia.app.demo、debug 签名，
 * 应用名「家人侠-演示版」），预装明朝示例家谱，与正式版、测试版可同时安装。
 * 碰一碰使用独立 AID，只能与演示版互碰；「我的 → 重置演示数据」恢复初始数据。
 * 两个视角：朱棣（groom，朱家 35 位家人）与徐皇后（bride，徐家 8 位家人），
 * 两台手机各装一个即可演示「碰一碰结为夫妻」。version 留空表示暂无演示版。 */
export const APK_DEMO = {
  /** 安装包的 versionName，含 -demo 后缀 */
  version: "2.4.1-demo",
  groom: {
    label: "朱棣",
    sizeBytes: 25_717_945,
    sha256: "12aea7a0c686e483818738145df9c2902c80938537c8edd6985332cb3acdfb0f",
    file: `${DOWNLOAD_BASE}/jiarenxia-demo-zhudi.apk`,
  },
  bride: {
    label: "徐皇后",
    sizeBytes: 25_718_645,
    sha256: "b1addd191e91657aed1765d86c2c21f5961aa5c76a1842d10c7e1136f560ef4c",
    file: `${DOWNLOAD_BASE}/jiarenxia-demo-xu.apk`,
  },
};

/** 小侠家族页展示的同作者小应用。安装包同样放站点根目录、不入库；
 * 发新版时更新这里的版本/大小/校验值并替换安装包。version 留空表示尚未发布。 */
export const EXTENSIONS = [
  {
    id: "shouhuxia",
    name: "守护侠",
    icon: "shield",
    stage: "正式版",
    description:
      "装在长辈手机上：锁屏后太久没人用手机，先响铃请 TA 确认；没人应答就自动打给紧急联系人，" +
      "还能每天发一条平安短信。不联网、不注册，设置只存在这台手机里。",
    version: "1.1.0",
    released: "2026-11-08",
    whatsNew: "首个正式版：首次使用四步向导（权限 → 紧急联系人 → 后台保护 → 实机演练）；可设 1–3 位紧急联系人，报警先群发短信再依次拨号",
    sizeBytes: 1_686_715,
    sha256: "2b73f45f9ab8fbc3dd0078007a828eca2137fdbb2cfdbfc918a1d7f6a84969ef",
    /** 安装包地址：下载服务按版本命名 */
    file: `${DOWNLOAD_BASE}/shouhuxia-1.1.0.apk`,
    minAndroid: "8.0",
    note: "首次使用请按首页向导完成权限设置和一次「实机演练」，确认能响铃、能发短信、能拨通后，守护即可开启。",
    en: {
      name: "ShouHuXia",
      stage: "Stable",
      description:
        "Installed on an elder's phone: if the phone sits untouched too long after lock, it first " +
        "rings to ask them to confirm; with no answer it automatically calls emergency contacts — " +
        "and can send a daily check-in text. Offline, no sign-up, settings stay on the phone.",
      whatsNew: "First stable release: four-step first-run wizard (permissions → emergency contacts → background protection → live drill); 1–3 emergency contacts; alerts send group texts first, then dial in turn",
      note: "On first use, finish the home wizard — permissions plus one live drill — to confirm it can ring, text and dial; the guard can then be enabled.",
    },
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
    en: {
      name: "PengYouXia",
      stage: "Beta",
      description:
        "JiaRenXia's sibling app — for friends instead of family. Record friends' birthdays, " +
        "preferences and recent news; lunar and solar birthdays both work, with timely reminders " +
        "to get in touch. Contacts import and encrypted backup included — data stays on your phone.",
      whatsNew: "First release",
    },
  },
  {
    id: "fantongxia",
    name: "饭桶侠",
    icon: "wheel",
    stage: "测试版",
    description:
      "一日三餐不知道吃什么，就交给转盘。在家做、出去吃都能转——烤鱼自助烤肉店也行，" +
      "转盘可以自己加、自己改，午晚夜宵到点提醒，还能把结果一键发给微信好友。",
    version: "0.1.0",
    released: "2026-10-27",
    whatsNew: "首个版本（由「吃什么」改名独立而成）",
    sizeBytes: 2_073_845,
    sha256: "6bd661904b5418dfaae6d2b5990ca6f0663616f7bd84c838f21abe43da255f06",
    file: `${DOWNLOAD_BASE}/fantongxia-0.1.0.apk`,
    minAndroid: "8.0",
    en: {
      name: "FanTongXia",
      stage: "Beta",
      description:
        "Can't decide what to eat? Let the wheel pick — cooking at home or eating out, even a " +
        "specific buffet or grill place. The wheel is fully editable, meal-time reminders are " +
        "included, and the result can be sent to a WeChat friend in one tap.",
      whatsNew: "First release (spun off and renamed from \"ChiShenMe\")",
    },
  },
] as const;
