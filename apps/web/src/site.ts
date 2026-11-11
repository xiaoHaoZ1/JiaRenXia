// 站点常量：本站为纯静态页面，部署在 Cloudflare Pages。发新版 APK 时更新
// 这里的版本，并把安装包按「名字-版本.apk」上传到 R2 桶 jrx-download
//（wrangler r2 object put，见 docs/deployment/LOCAL_WINDOWS.md）；
// 正式包还要用 gh release create v<版本> 传到 GitHub Release 并更新 apkGithubUrl。

export const DOWNLOAD_BASE = "https://dl.jiarenxia.com";

export const SITE = {
  contactEmail: "2107290326@qq.com",
  /** 公开源码仓库（GPL-3.0）。 */
  sourceUrl: "https://github.com/xiaoHaoZ1/JiaRenXia",
  /** 守护侠公开源码仓库（GPL-3.0，2026-10-27 开源）。 */
  guardianSourceUrl: "https://github.com/xiaoHaoZ1/ShouHuXia",
  /** 当前可下载的 Android 版本；留空表示尚未发布。 */
  apkVersion: "2.4.3",
  /** 与安装包一致的 versionCode；App 端「联网检查更新」按它判断新旧。 */
  apkVersionCode: 61,
  /** 本版更新说明（App 更新弹窗与下载页都可能展示；发布时与安装包同步更新）。 */
  apkNotes: "「上坟提醒」节日名单默认只含直系亲属（到祖父母一辈），叔姑舅姨、堂表亲仍可手动打开；家事页铃铛改为「提醒」文字按钮并新增引导。包含 2.4 全部新功能：「上坟提醒」（清明、中元、寒衣、除夕、冬至和每位家人的忌日都能提醒，新近去世的家人还会提醒头七到七七和百日）、「回家看看」（选好家里有谁、多久回去一次，按见面记录计时）、「长辈模式」（装在长辈手机上，只看家人、打电话和最近的日子）、农历按香港天文台历表换算、生日当天一键发祝福短信。",
  /** APK 文件大小与 SHA-256（供下载页核对；发布时与安装包同步更新）。 */
  apkSizeBytes: 3_797_503,
  apkSha256: "eda1c3846d0d9930d950dea81868ba30095b095e48831f14cfdedc8797d714e3",
  /** APK 下载地址：下载服务 dl.jiarenxia.com（Worker jrx-dl + R2 jrx-download），
   * 按版本命名；按 ?src=（web/update/legacy/direct）分日期计数到 D1。 */
  apkDownloadUrl: `${DOWNLOAD_BASE}/jiarenxia-2.4.3.apk`,
  /** 备用下载：同一个正式包上传到公开仓库 GitHub Release（tag v<版本>，文件名同 R2），
   * 国内部分线路比 dl 快。留空则下载页不显示备用链接；须与 apkVersion 一致，
   * 不一致时 prerender 直接让构建失败。 */
  apkGithubUrl: "https://github.com/xiaoHaoZ1/JiaRenXia/releases/download/v2.4.3/jiarenxia-2.4.3.apk",
};

/** 家人侠测试版：debug 构建（applicationId com.jiarenxia.app.debug、debug 签名，
 * 应用名「家人侠-测试版」），可与正式版同时安装、数据互不相通。
 * 发布测试版时：构建 :app:assembleDebug，把 app-debug.apk 按
 * 「jiarenxia-beta-<versionName 去掉 -debug>.apk」上传 R2 桶 jrx-download，
 * 并同步这里的版本/大小/校验值。version 留空表示暂无测试版。 */
export const APK_BETA = {
  /** 安装包的 versionName，含 -debug 后缀 */
  version: "2.4.3-debug",
  /** 与安装包一致的 versionCode；更新清单 channels.beta 用 */
  versionCode: 61,
  sizeBytes: 25_546_393,
  sha256: "dc301c5ef0a913427d213b512083be79304ce0d6237811745c5907ca1d2d51f4",
  file: `${DOWNLOAD_BASE}/jiarenxia-beta-2.4.3.apk`,
};

/** 家人侠演示版：demo 构建（applicationId com.jiarenxia.app.demo、debug 签名，
 * 应用名「家人侠-演示版」），预装明朝示例家谱，与正式版、测试版可同时安装。
 * 碰一碰使用独立 AID，只能与演示版互碰；「我的 → 重置演示数据」恢复初始数据。
 * 两个视角：朱棣（groom，朱家 35 位家人）与徐皇后（bride，徐家 8 位家人），
 * 两台手机各装一个即可演示「碰一碰结为夫妻」。version 留空表示暂无演示版。 */
export const APK_DEMO = {
  /** 安装包的 versionName，含 -demo 后缀 */
  version: "2.4.3-demo",
  /** 与安装包一致的 versionCode；更新清单 channels.demo 用 */
  versionCode: 61,
  groom: {
    label: "朱棣",
    sizeBytes: 25_345_582,
    sha256: "3f8c08e7de1e7fbb725ddf8611e69c8437b6a598e6274fbbe64bb514545b090b",
    file: `${DOWNLOAD_BASE}/jiarenxia-demo-zhudi-2.4.3.apk`,
  },
  bride: {
    label: "徐皇后",
    sizeBytes: 25_720_039,
    sha256: "2e44d53dc10446febbd7eb840de960077fc4111e6010a6de373986dc1347a017",
    file: `${DOWNLOAD_BASE}/jiarenxia-demo-xu-2.4.3.apk`,
  },
};

/** 守护侠单品页展示的应用信息。安装包放站点根目录、不入库；
 * 发新版时更新这里的版本/大小/校验值并替换安装包。version 留空表示尚未发布。 */
export const SHOUHUXIA = {
  id: "shouhuxia",
  name: "守护侠",
  icon: "shield",
  stage: "正式版",
  description:
    "装在长辈手机上：锁屏后太久没人用手机，先响铃请 TA 确认；没人应答就自动打给紧急联系人，" +
    "还能每天发一条平安短信。不联网、不注册，设置只存在这台手机里。",
  version: "1.1.0",
  /** 发布日期与本版亮点（展示在下载卡上） */
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
} as const;
