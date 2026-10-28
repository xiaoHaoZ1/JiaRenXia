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
  apkVersion: "2.1.4",
  /** 与安装包一致的 versionCode；App 端「联网检查更新」按它判断新旧。 */
  apkVersionCode: 53,
  /** 本版更新说明（App 更新弹窗与下载页都可能展示；发布时与安装包同步更新）。 */
  apkNotes: "新增兄弟姐妹排行：编辑人物可填「在兄弟姐妹中排第几」，不知道生日时关系图与家庭页子女按老大、老二顺序排列；排行随备份、分享与家人协作合并一并保留。",
  /** APK 文件大小与 SHA-256（供下载页核对；发布时与安装包同步更新）。 */
  apkSizeBytes: 3_752_503,
  apkSha256: "56388e577f698b709a1c0ee54b6c01998ed772b52fb45c2c33287af24543985a",
  /** APK 下载地址：下载服务 dl.jiarenxia.com（Worker jrx-dl + R2 jrx-download），
   * 按版本命名；按 ?src=（web/update/legacy/direct）分日期计数到 D1。 */
  apkDownloadUrl: `${DOWNLOAD_BASE}/jiarenxia-2.1.4.apk`,
};

/** 家人侠测试版：debug 构建（applicationId com.jiarenxia.app.debug、debug 签名，
 * 应用名「家人侠-测试版」），可与正式版同时安装、数据互不相通。
 * 发布测试版时：构建 :app:assembleDebug，把 app-debug.apk 按
 * 「jiarenxia-beta-<versionName 去掉 -debug>.apk」上传 R2 桶 jrx-download，
 * 并同步这里的版本/大小/校验值。version 留空表示暂无测试版。 */
export const APK_BETA = {
  /** 安装包的 versionName，含 -debug 后缀 */
  version: "2.1.3-debug",
  sizeBytes: 26_191_429,
  sha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  file: `${DOWNLOAD_BASE}/jiarenxia-beta-2.1.3.apk`,
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
  },
] as const;
