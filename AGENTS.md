# 家人侠（JiaRenXia）— 开发约定

## 产品目录（2026-10-06 起统一）

产品根目录就是 Git 仓库的根目录（`.git` 在这里），两台开发机上各有一份克隆，经 GitHub 同步。
- `apps/android/` — 完整 Android Gradle 工程（Gradle 根目录、wrapper、`local.properties`、`keystore.properties` 都在这里）。
- `apps/web/` — 官网（纯静态站点，React + TypeScript + Vite）。
- `docs/` — 设计事实来源；`docs/web/`、`docs/deployment/`、`docs/design/`。
- `assets/`（品牌、截图）、`scripts/`（`jrx.cmd` 调用的入口）。
- `worktrees/`、`runtime/`、`private/` 不入库：worktree 一律建在 `worktrees/<名称>`（`git worktree add worktrees/<名称> -b <分支>`）；运行日志、截图产物在 `runtime/`；本机配置在 `private/`。
- 根目录统一入口 `jrx.cmd`：`jrx.cmd help` 查看全部命令（Android 构建、网站的构建/开发/端到端自检）。

## Android 模块结构（apps/android）

- `:app` — Android 应用模块（Compose + Hilt）。内部按包划分边界：`data`、`domain`、`reminder`、`ui`、`navigation`。
- `:core:model` — 纯 Kotlin/JVM 库，领域模型，不依赖 Android。
- `:core:kinship` — 纯 Kotlin/JVM 库，亲属关系推导引擎，依赖 `:core:model`，JUnit 单元测试。
- `:core:designsystem` — Android 库，主题与通用组件（JrxCard、PageHeader 等），不依赖数据层。
- 守护侠已于 2026-10-23 拆出为独立工程，含 `:guardianapp` 与 `:feature:guardian`；`:core:designsystem` 为拆分时的快照副本，两边各自演进。守护侠正式版与家人侠共用同一把签名密钥。
- `:baselineprofile` — Baseline Profile 生成模块。

## 构建 / 测试 / 安装（本机 Windows 环境）

下文 `<产品根目录>` 指仓库根目录。Devin 会话的命令在 WSL2 内执行（`uname` 可验证），构建、adb 等 Windows 工具链一律经 `cmd.exe /c` 调用。Gradle 根目录是 `apps/android`，以下 Gradle 命令与相对路径都以它为工作目录。完整验证命令：

```bash
cmd.exe /c "cd /d <产品根目录>\apps\android && gradlew.bat :app:testDebugUnitTest :core:kinship:test :core:model:test :app:lintDebug :app:assembleDebug :app:assembleRelease"
```

在产品根目录也可以运行 `jrx.cmd android-verify`（同一组任务，遇到下述 lint 内部崩溃自动整条重跑一次，日志在 `runtime\logs\`），或 `jrx.cmd android <任务…>`（参数原样转给 `apps\android\gradlew.bat`）。

已知问题：lint 偶尔会在 `lintAnalyzeDebugUnitTest` 阶段出现内部崩溃（`KotlinIllegalArgumentExceptionWithAttachments`，Kotlin 分析器的偶发问题，不是告警），单独重跑 `:app:lintDebug` 即可通过。

目录迁移后，旧 `build/` 里的增量状态带着旧绝对路径：首次构建若出现 “is located outside the root directory” 一类的 transform 错误，先运行 `gradlew.bat clean`（新建的 worktree 没有旧 build 目录，不受影响）。

- 单元测试：`:app` 用 Robolectric（`@Config(sdk = [34])`），`:core:*` 为纯 JUnit。
- 经 `cmd.exe` 运行时 `--tests` 模式不要加引号（如 `--tests *ScreenShotsTest*`），引号会导致匹配不到任何测试。
- Gradle wrapper 若因 services.gradle.org 超时（networkTimeout 10s）下载失败：从镜像下载 `gradle-8.13-bin.zip`，核对官方 SHA-256 后放入 Windows 侧 `<用户目录>\.gradle\wrapper\dists\gradle-8.13-bin\<hash>\`（gradlew.bat 跑在 Windows，不是 WSL 的 `~`）。
- `local.properties` 的 `sdk.dir` 必须指向本机 Android SDK。
- APK 输出（相对 `apps/android`）：`app/build/outputs/apk/debug/app-debug.apk`、`app/build/outputs/apk/release/app-release.apk`（有签名配置时；没有时为 `app-release-unsigned.apk`）。
- 性能变体（androidx.baselineprofile 插件，feature/app-perf 引入）：`nonMinifiedRelease` = release 语义不混淆 + profileable + debug 签名，供 profile 采集；`benchmark` = release 全量优化（R8）+ debug 签名，供真机实测。两者 applicationId 与 release 相同（`com.jiarenxia.app`）但用 debug 签名：既不能覆盖已装的正式版，也不能覆盖 `.debug` 包；装真实数据的手机上是正式版，**绝不在那台机器上装这些变体**，用模拟器或没装正式版的设备。实测参考（Redmi K70 Ultra / HyperOS，55 人测试家庭）：debug 冷启动 ~930ms / 切家庭 tab 掉帧 500ms+ → benchmark 冷启动 ~170ms / 切 tab 最差 ~50ms。
- Baseline Profile：`app/src/main/generated/baselineProfiles/baseline-prof.txt` 已入库，随全部变体打包（APK 内 `assets/dexopt/baseline.prof`）。标准生成 `gradlew.bat :app:generateReleaseBaselineProfile`（跑 `:baselineprofile` 模块的 `BaselineProfileGenerator`，需 USB 调试（安全设置））。**小米/HyperOS 会拦 SAVE_PROFILE 广播导致生成失败**，手动备选（等效）：装 nonMinifiedRelease → `adb` 走查（冷启动 + 三个 tab + 滑列表 + 详情页）→ `adb shell pm dump-profiles --dump-classes-and-methods com.jiarenxia.app` → 把 `/data/misc/profman/com.jiarenxia.app-primary.prof.txt` 内容复制进 baseline-prof.txt。重新生成前先恢复测试家庭数据覆盖更多热路径。
- Lint 报告：`apps/android/app/build/reports/lint-results-debug.html`，要求 0 error。
- README 截图（写入产品根目录的 `docs/images/`，测试中路径为相对模块目录的 `../../../docs/images/`）与大字号走查图（f13_/f15_ 写入临时目录）为 opt-in：
  `gradlew.bat :app:testDebugUnitTest --tests "*ReadmeShotsTest*" -PreadmeShots=true`；普通测试运行自动跳过。
  注意：`docs/images/` 的 README 截图自 2026-10-23 起为 vivo 真机实拍版（见下），跑该测试会用 Roborazzi 版覆盖同名文件。
- 测试家庭备份 `apps/android/testdata/jrx_test_family.jrxb`（合成数据，密码 `test1234`，55 人/54 关系）由
  `TestFamilyBackupTest` 生成：`gradlew.bat :app:testDebugUnitTest --tests *TestFamilyBackupTest* -PtestFamily=true`。
  同一命令同时生成英文名变体 `testdata/jrx_test_family_en.jrxb`（全员英文名、健在、无忌日，
  密码同 `test1234`），用于 README/官网等对外真机截图。
  真机使用：`adb.exe -s <serial> push testdata/jrx_test_family.jrxb /sdcard/Download/`，
  应用内 我的 → 从备份恢复 → 选择该文件 → 输入 `test1234`。内容改动需同步更新
  `app/src/test/.../data/backup/TestFamily.kt` 的期望表（`TestFamilyTitlesTest` 常驻校验）。
  新增人物时需同步 `TestFamilyBackupTest.ENGLISH_NAMES` 映射（缺名会直接报错）。
- 我的页“测试工具”区（debug 构建直接可见；正式版连点版本号开启 `testToolsEnabled`）：快速存档/读档走
  `data/backup/QuickSnapshotStore`（与备份同格式的加密 ZIP，密钥在 `filesDir/quick_snapshots/`，不进备份、不入云备份），
  “清空数据但保留存档”供走查时快速切换数据场景。

安装到已连接设备（Windows adb，在 `apps/android` 下执行）：`adb.exe` 在 PATH 上，或 SDK 自带的 `<SDK>\platform-tools\adb.exe`。

```bash
adb.exe -s <真机序列号> install -r app/build/outputs/apk/debug/app-debug.apk
```

- 小米/HyperOS 需在 开发者选项 → “USB 安装” 中允许安装，否则报 `INSTALL_FAILED_USER_RESTRICTED`。
- 设备走查：`adb shell uiautomator dump` 取元素坐标 + `adb shell input tap x y` 点击；`input text` 无法输入中文，测试数据用 ASCII 名字。
- 走查保活：手机闲置约 10 分钟会自动锁屏，走查期间每 ~4 分钟发一次 `adb shell input keyevent 59`（无害 SHIFT 键）保持唤醒；图案锁屏下 adb 无法解锁，因此**绝不**发送 keyevent 223/26（睡眠/锁屏）。

### 正式版签名

在 Android 工程根目录 `apps/android` 放 `keystore.properties`（与 `*.jks`/`*.keystore` 一样已被 .gitignore 忽略；`storeFile` 相对该目录解析）后，release 构建自动签名。以下命令在 `apps/android` 下执行：

```bash
# 1. 生成签名密钥（一次即可，妥善保管）
keytool -genkeypair -v -keystore jiarenxia-release.jks -keyalg RSA -keysize 4096 -validity 36500 -alias jiarenxia

# 2. keystore.properties（占位示例，勿提交真值）
# storeFile=jiarenxia-release.jks
# storePassword 填密码
# keyAlias=jiarenxia
# keyPassword 填密码

# 3. 构建（输出 app-release.apk；无 properties 时仍为 app-release-unsigned.apk）
gradlew.bat :app:assembleRelease
```

**警告**：密钥与口令丢了，后续更新将无法覆盖安装旧版本——务必备份。

- 新证书 DN `CN=JiaRenXia, O=xiaoHaoZ1, C=CN`，SHA-256 `00:15:22:AE:2C:13:CB:3B:BD:2F:38:CB:FA:49:FB:32:2D:5B:59:91:85:93:F5:67:A2:C0:65:E2:61:F8:75:D0`。核对密钥或安装包用 `keytool -list -v` / `<SDK>\build-tools\<版本>\apksigner.bat verify --print-certs`，指纹不一致就不是这把密钥。
- 真机已换用新密钥签名的正式版，新包可直接覆盖安装（`adb install -r`）。若个别设备仍装着旧密钥签名的 0.9.4，不能直接覆盖：App 内加密备份 → 用户自己把备份文件存好 → 卸载 → 安装新签名版本 → 从备份恢复。卸载前必须征得用户同意。
- debug 与 release 包签名不同，彼此不能覆盖安装（切换须先卸载）。
- debug 签名随机器而异，另一台电脑构建的 debug 包同样不能直接覆盖。

## 多会话并行开发（2026-10-06 起）

- 每个任务一个 worktree 和分支，从最新 main 建：`git worktree add worktrees/<名称> -b feature/<名称> main`。不要在主工作区改代码，主工作区留给负责审阅、合并的会话。
- `local.properties` 不入库：从主工作区 `apps/android/local.properties` 复制到新 worktree 的 `apps/android/`（内容为 `sdk.dir=<本机 Android SDK 路径>`，Windows 路径的冒号须转义，如 `C\:/...`）。
- 提交作者必须是用户本人，不能写 Devin 机器人。WSL 里的 git 没有配置提交人，**不要改 git config**，每次提交用：`git -c user.name=yangyonghao -c user.email=2107290326@qq.com commit ...`。
- 提交后随时推送分支。WSL 里的 git 没有 GitHub 凭据，用 Windows git 在主仓库目录推送（分支是共享的）：`cmd.exe /c "cd /d <产品根目录> && git push -u origin feature/<名称>"`。
- 各任务会话只推送自己的分支，不合并 main。审阅、合并 main、推送 main 由负责合并的会话完成；合并后删除已合并的 worktree 和分支。
- 命令执行工具会提前展开 `$变量`：一条命令里不要依赖 shell 变量、循环或 `export`，把值直接写进命令。
- 界面改动可以交给助手实现，但**设计由主代理定、改完由主代理逐页看渲染结果验收**（用户 2026-10-09 调整；此前 2026-10-06 的要求是主代理亲自写，现已放宽）。交付给助手的必须是已定稿的规格：每一行放什么、文案、层级、交互都写清楚，不要让助手自己拿主意；验收要看实际渲染图（`:app:testDebugUnitTest --tests *ScreenShotsTest*` 产出在 `app/build/outputs/roborazzi/`），不能只看它说"做完了"。构建、测试等验证照常委派。
- 真机 `<真机序列号>` 装的是新密钥签名的正式版，里面是用户的真实家庭数据，测试时直接用这些数据看效果。debug 包不能覆盖正式版；卸载、清除数据、从备份恢复等会覆盖数据的操作，必须先征得用户同意。可以截图、`uiautomator dump`，但不得把数据库或个人资料拷出手机、写进仓库或测试；测试用例一律用合成名字。
- `AGENTS.md` 中用「`private:start` / `private:end`」HTML 注释标记包住的段落是本机私密信息，不入公开快照：公开仓库的内容由维护者从开发仓库按版本导出，提交历史以版本为单位。`jrx.cmd public-snapshot <目标目录>` 导出 `git archive` 产物、剔除 `scripts/public-exclude.txt` 列出的文件并删掉这些段落，再做 `scripts/public-scan.txt` 泄漏扫描（有命中即失败）。

## 守护（已拆出）

守护模块随守护侠拆到独立工程（2026-10-23），开发约定见该仓库 AGENTS.md，
设计文档见该仓库 `docs/GUARDIAN_MODULE.md`。家人侠自 C3 起不含守护代码。

## 架构指引

- `data/` — `FamilyRepository` 是唯一写入路径；多表写入一律 `db.withTransaction`，校验失败用 `TxAbort` 回滚；头像经 `AvatarStorage`（`filesDir/avatars`）。
- `domain/` — `ObserveFamilyOverview`（resolveAll + 生日合成总览，`Dispatchers.Default`）、`FamilyOrdering`（核心家人分组排序，中国拼音 Collator）、`PersonSearch`。
- `reminder/` — 数据库是唯一事实来源：每天按各类提醒时刻做 WorkManager 检查（`ReminderTiming`，生日/事项/联系三时刻各自可配，默认 09:00），已过的时刻可当天补跑、按当日已通知键去重，跑完排最近的下一时刻；`ReminderSync` 在启动及生日/纪念日/事项/联系计划与记录/三个开关/三个时刻变化时 reconcile；`POST_NOTIFICATIONS` 只自动问一次。**`BirthdayReminderWorker` 类名不可改**——WorkManager 已持久化已排队任务名。
- `ui/` — Compose + Hilt ViewModel + StateFlow；业务规则不进 Composable；`ui/graph/GraphLayout` 为纯 JVM 布局算法。
- `navigation/AppNavHost` — 全部路由与通知深链（`personId` extra）。
- 界面改版（2026-10-09，见 `docs/DEVELOPMENT_PLAN.md` 2.16）：一级页只放入口，开关与详细设置在二级页，二级页共用 `ui/mine/SettingsRows.kt` 的 `SettingsScaffold`/`SettingsGroup`/`SettingsNavRow`/`SettingsSwitchRow`。「我的」= 个人资料 + 备份卡 + 数据管理/外观与字号/功能管理/帮助与关于四个入口；三类提醒的开关与时刻在**家事页顶栏铃铛**（`settings/reminders`）；分享、接收、碰一碰、婚礼祝词在**家庭页 → 共享与连接**（`share/connect`）。主题 token 在 `core/designsystem/ui/theme/`，改 token 即全 App 生效；页头组件是 `PageHeader`（平底，原名 `GradientHeader`，渐变页头已取消），背景色取 `JrxColors.pageHeaderBackground`。

## 约定

- 代码注释使用中文：类和公开函数写 KDoc，非显而易见的逻辑写注释；琐碎代码不注释。
- Git 提交信息使用中文。
- App 默认不联网：`INTERNET`/`REQUEST_INSTALL_PACKAGES` 仅为可选的「联网检查更新」声明（2026-10 起，`data/update/`）。联网代码只允许出现在 `data/update/`，且必须受 `updateCheckEnabled`（默认关）门控——未开启不得发起任何请求；请求只查官网版本清单与下载安装包。不引入统计、广告、崩溃上报等联网 SDK，家庭资料永不上传（会员功能 2026-10-08 下线后联网链路整体删除）。
- 应用锁已于 2026-10-09 下线：App 内不再有二次密码/指纹锁，`data/security/`、锁屏页、`androidx.biometric` 依赖全部删除，`MainActivity` 是 `ComponentActivity`。资料安全靠不联网 + 手机自带锁屏，不要再加 App 内锁。留档见 `docs/FAMILY_AFFAIRS.md` 第 4 节。
- `docs/` 为设计事实来源；实现阶段决策见 `docs/DEVELOPMENT_PLAN.md` 第 2 节。
- `apps/android/app/schemas/` 为 Room schema 导出目录，需纳入版本管理；`local.properties` 不入库。
- 源码一律用编辑工具写入，不要用 shell heredoc / echo 生成 Kotlin、SQL：反引号与 `$` 会被 shell 展开（0.9.0 前 MIGRATION_6_7 与 MigrationTest 曾因此损坏）。

## 家人共享

- 共享文件 = 备份格式（purpose="share" + sharedBy），导出走 BackupManager.exportShare、导入走 importShare（选“我是谁”→ is_self，设置保留接收方）。只含家人档案（人物/手机号/关系/生日/提醒偏好/头像）；事项/人情/保单与备注一律不写、不提供勾选。“分享给家人”与关系图共用同一个选人页（`ui/share/ShareScope*`，默认全选、可用 `personIds` 做部分分享）。
- 恢复流程（我的页“从备份恢复”与欢迎页“用已有资料开始”）共用 ui/restore/RestoreFlow，按 purpose 分流；BackupManager.restore 只收 purpose="backup" 的 plan。
- 已有数据默认合并（domain/merge/FamilyMerge，预览重算见 ui/share/MergePreview.kt）；对方→本机对应关系存 person_alias（DB v10，不进备份/共享）。
- 接收 intent-filter：ACTION_VIEW(content) / ACTION_SEND，mimeType 固定 application/octet-stream，不要注册通配 MIME。
- 微信接收已于 2026-10-28 在红米 K70 至尊版真机核对：可从微信正常转发 `.jrxb` 文件到家人侠并进入接收流程；现有 `application/octet-stream` intent-filter 可用，无需注册通配 MIME。
- 从分享面板选家人侠自己，可以在没有微信的情况下测接收流程。
- 碰一碰真机核对需要两部带 NFC 的手机；确认小米 HCE 在 category other 下能正常路由。单机可核对：无 NFC / NFC 关闭提示、男女等待页、读卡模式与 HCE 启停、半颗心动画（我的→调试→预览半颗心 左/右）、共享导入的“和我结为夫妻”。另外 `adb shell dumpsys nfc` 应能列出 `com.jiarenxia.app/io.github.xiaohaoz1.jiarenxia.nfc.TapHostService`（AID F04A52580001）；查不到说明 manifest 没注册该服务，碰一碰会完全无反应（0.8.1 的 bug）。小米手机绑了交通卡时，感应到读卡器会弹出小米钱包（logcat `NfcService: sendFieldOnEvent` → com.miui.tsmclient）；排查 HCE 断连看 `HostEmulationManager`，交易中出现 “Unbinding from service …TapHostService” 说明服务被解绑。协议为 HELLO v4（配对/时钟/多条目纪念日/祝词尾部 + 1 字节用途：0=结为夫妻、1=互加亲人），两部手机须同版本；真机反复测试用彩排模式（结为夫妻不写入数据）。OPPO（ColorOS 15）安全芯片为内置 eSE 且钱包有默认门禁卡时当不了卡片端（NCI 只有 RF_FIELD_INFO、没有 RF_INTF_ACTIVATED，当前 AID 为门禁卡 A0000003964D344D1061707056434D01），让它读卡、对方当卡片即可。
- FileProvider authority ${applicationId}.share-provider（安装后为 com.jiarenxia.app.share-provider），路径 res/xml/file_paths.xml（cacheDir/share）。

## 网站（纯静态站点）

- 设计事实来源：`docs/web/README.md`（网站规格）。
- 无后端：`apps/web` 构建产物 `dist/` 交给任意静态服务器/托管。**构建期预渲染**：每个路由都有自己的 `index.html`（`/help` → `dist/help/index.html`），未知路径以 **404 状态**返回 `dist/404.html`（不要再回退到 `/index.html`，那是软 404）。
- SEO（2026-10-09 起）：`npm run build` = `vite build` + `vite build --ssr src/entry-server.tsx` + `node scripts/prerender.mjs`（渲染静态 HTML、生成 `sitemap.xml`、删 `dist-ssr/`）。页面标题/摘要/canonical/JSON-LD 的唯一事实来源是 `apps/web/src/seo.ts`，**新增页面必须加进 `PAGES`** 才会被预渲染和收录；客户端切页由 `src/head.ts` 改写 head。原理与百度专项注意事项见 `docs/web/README.md` 第 6 节。
- **Cloudflare 会挡百度**：「托管 robots.txt」/「Block AI bots」把 Baiduspider 当 AI 爬虫，robots.txt 里加 `Disallow: /` 且边缘 403（Googlebot 不受影响）。**2026-10-09 已在 Cloudflare 后台关闭**（Security → Configure AI bot policies：Training 改 Allow、关 Bot Preference Sync），Baiduspider 现返回 200；百度站点已验证（验证文件 `apps/web/public/baidu_verify_codeva-*.html` 必须常驻，百度定期复查）、抓取诊断通过。这是站外配置，代码改不了，换域名或换账号要重做。
- 品牌位图（`public/og-cover.png` 分享封面、`apple-touch-icon.png`、`favicon-32.png`、`favicon.ico`）由 `apps/web/scripts/brand-images.mjs` 渲染，产物入库，**不参与 `npm run build`**；改设计稿 `scripts/og-cover.html` 或标志后手动跑 `npm run brand-images`（需本机 Edge）。
- 站点元数据集中在 `apps/web/src/site.ts`（联系邮箱、APK 版本/大小/SHA-256）。安装包不放 `apps/web/public/`：发布 = 传 `jiarenxia-<版本>.apk` 到 R2 + 同步 site.ts + `jrx.cmd web-deploy`（隧道为回退：DNS 已切 Pages，回退时把 DNS 指回机器 A 隧道再 `web-build`；serve.mjs 读 `dist/_redirects` 把旧 `/jiarenxia.apk` 等路径 302 到下载服务）。
- 测试：`apps/web` 下 `npm run typecheck`、`npm run build`、`npm run e2e`（Playwright + 本机 Edge；webServer 自动 `build` 后起生产同款的 `serve.mjs`，端口 4173）。`e2e/seo.spec.ts` 用 `request`（不执行 JS，模拟百度/Bing 抓取）断言预渲染产物。
- 在 worktree 里改前端：先 `npm ci`，再 `npx vite --host 127.0.0.1 --port 5180` 预览走查（可用 Playwright + Edge 截图桌面/手机/深色）。
- PowerShell 脚本必须同时兼容 Windows PowerShell 5.1 与 PowerShell 7，保存为 UTF-8 带 BOM；`.cmd` 文件只用 ASCII。
- 会员功能已整体下线（2026-10-08）：`services/membership`、买家中心、管理界面、设备凭证等随后端删除；需要恢复时从 Git 历史取回。
