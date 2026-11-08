# 测试版/演示版在线更新（2026-11-08，feature/tap-payment → main `3f69b0d`）

## 做了什么

- `jiarenxia-version.json` 新增 `channels.beta` / `channels.demo`（顶层仍是正式版）。
- App 按 `applicationId` 后缀选渠道（`.debug`→beta、`.demo`→demo），渠道缺块报「该渠道暂无更新信息」，**不回落正式版**（签名不同装不上）。
- 演示版按 `BuildConfig.DEMO_ROLE` 选 groom/bride 子块——两包同 `com.jiarenxia.app.demo`，装错角色不会报错但会换掉「重置演示数据」的种子。
- **测试版/演示版默认开启「联网检查更新」**（`updateCheckEnabled` 默认值按渠道分），正式版仍默认关。
- 演示版 `apduservice` 的 aid-group 改 `category="payment"`：出现在系统「默认付款应用」列表，设为默认可顶替钱包（小米钱包交通卡抢前台问题）；**代价：必须设为默认付款应用 AID 才路由**，碰一碰前要先设。

## 本次发布物（R2 `jrx-download`，已上线）

| 文件 | vc / versionName | 大小 | SHA-256 |
|---|---|---|---|
| jiarenxia-beta-2.4.2.apk | 60 / 2.4.2-debug | 25,530,009 | `1dac9728…d932` |
| jiarenxia-demo-zhudi-2.4.2.apk | 60 / 2.4.2-demo | 25,616,191 | `1b45be43…6f96` |
| jiarenxia-demo-xu-2.4.2.apk | 60 / 2.4.2-demo | 25,719,841 | `e2725457…c1c6` |

下载页与版本清单均指向新文件；旧演示包（`jiarenxia-demo-zhudi.apk`、`-v2.apk`、`jiarenxia-demo-xu.apk`）仍在桶里作回退。

## 后续发布注意

- **发正式版时若要推渠道更新**：用新代码重打 `:app:assembleDebug`（beta）和 `:app:assembleDemo -PdemoRole=groom|bride`（demo），上传 R2，更新 `site.ts` 的 `APK_BETA`/`APK_DEMO`（version、versionCode、sizeBytes、sha256、file），`npm run build` 自动重生成清单渠道块。
- 渠道包 versionCode 必须大于手机里已装的，才会触发更新提示。
- 演示版 grooming/bride 更新只换 APK，已导入的家庭数据不受影响；「重置演示数据」仍回各自角色种子。
- `git commit -m` 在 cmd 下中文仍有静默失败风险，用 `git commit -F <文件>`。
