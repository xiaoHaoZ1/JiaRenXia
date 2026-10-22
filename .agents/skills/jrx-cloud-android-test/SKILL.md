---
name: jrx-cloud-android-test
description: 云端 Linux 环境为家人侠跑 Android 构建、双模拟器与截图录屏验收的方法与排障（区别于 AGENTS.md 的本机 Windows/WSL 流程）
---

# 云端 Linux Android 验收环境

适用：Devin 云端 Ubuntu VM。与 AGENTS.md 不同：不用 cmd.exe/gradlew.bat，不照搬 E:\ 路径。
运行状态（HEAD、adb devices、模拟器是否在线）一律现场查，不假定。

## 环境检查

- 工具链应在 `~/Android/Sdk/{platform-tools,emulator,cmdline-tools/latest/bin}`；缺则按仓库 blueprint 重跑对应 initialize 步骤。
- `~/repos/JiaRenXia/apps/android/local.properties` 含 `sdk.dir=/home/ubuntu/Android/Sdk`（不入库，重建 worktree 要补）。
- `~/.gradle/init.d/maven-mirror.gradle`：Maven Central 直连全 429 → mavenCentral() 重定向华为云；同文件还设 Robolectric android-all 镜像与测试 JVM `TZ=Asia/Shanghai`（GuardEngineTest 依赖系统默认时区，UTC 会挂）。缺了单测会失败。
- 构建：`cd apps/android && bash gradlew :app:assembleDebug`（gradlew 入库 644，**不要 chmod +x**，避免 mode 变更；`bash gradlew` 即可）。APK 输出 `app/build/outputs/apk/debug/app-debug.apk`，applicationId `com.jiarenxia.app`。

## 双模拟器（云端验收只用模拟器，不操作物理真机）

- AVD：`jrx_test`（默认 5554，汇总者 A）、`jrx_peer`（**必须 -port 5556**，成员 B）。API 36 x86_64 google_apis。
- 这两台都是 x86_64 模拟器，行为结论不等同物理真机；AGENTS.md 里 `<serial>` 指代的开发机真机（真实家庭数据），云端会话绝不连接、不安装、不取数。
- 启动（KVM 可用、要有窗口——验收人看桌面）：
  `~/Android/Sdk/emulator/emulator -avd jrx_test -gpu swiftshader_indirect -no-snapshot-save &`
  `~/Android/Sdk/emulator/emulator -avd jrx_peer -port 5556 -gpu swiftshader_indirect -no-snapshot-save &`
- 等开机：`adb -s <serial> shell getprop sys.boot_completed` 回 1（冷启动约 40-90s）。
- 进程被杀（VM 休眠/重启）后重跑上面两条即可；AVD 磁盘数据本会话内验证可续用（快照行为未逐一验证，别当保证）。
- 所有 adb 命令**必须 `adb -s emulator-5554` / `-s emulator-5556` 指定 serial**，双机并存时裸 adb 会报 more than one device。

## 安装与版本核对

- `adb -s <serial> install -r apps/android/app/build/outputs/apk/debug/app-debug.apk`；两台都装同一 APK 再验收协作。
- 核对构建对应提交：`git rev-parse HEAD` + `git status --short`（有未提交改动要说明）；`adb -s <s> shell dumpsys package com.jiarenxia.app | grep version` 报 versionCode/versionName。
- 启动 App：`adb -s <s> shell monkey -p com.jiarenxia.app -c android.intent.category.LAUNCHER 1`。

## 截图 / 录屏 / 取证

- 屏幕：`adb -s <s> exec-out screencap -p > out.png`（比 `adb shell screencap` 可靠）。
- UI 节点：`adb -s <s> shell uiautomator dump /dev/tty`（注意弹软键盘后节点/坐标会漂移：先 `input keyevent 4` 收键盘再 dump 再点）。
- 应用私有目录取证：`adb -s <s> exec-out run-as com.jiarenxia.app cat <files/cache 路径> > out`；协作包在 `cache/collab/`，收发记录在 `files/collab_incoming/`。
- 连续录屏交 Devin 测试 agent（screen recording），不要自己开 recordmydesktop。

## 常见故障

- `input text` 不能输中文 → 测试数据用 ASCII 名。
- Gboard "Try out your stylus" 手写教程会吞掉 `input text`（密码只进 1-3 位），**每次重装 APK 后会重新武装**；`pm clear com.google.android.inputmethod.latin` 只能临时复位。彻底解决：把教程每步 Next 点完直到变 Close。点确定前先数 `•` 个数。
- 拉库核验：`exec-out run-as <pkg> cat db db-wal > out.db` 拼接是错的（WAL 字节被 sqlite 忽略，看不到未 checkpoint 的提交，会误报“应用没落库”）。正确：db、db-wal 分别拉成同名兄弟文件，sqlite 自动合并 WAL。
- 「共享与连接」页没有底部导航：底部 ~(x,2274) 是碰一碰行，点了会进 NFC 页；返回走页内左上箭头。
- SAF 文件选择器：网格按时间排不按文件名，点卡片中心；每次接收前先 `rm /sdcard/Download/家人侠-*.jrxb` 清旧包防误选。
- uiautomator dump 会漏节点/陈旧（快滚、刚开键盘）；先 screencap 核对像素，渲染出的控件可按像素位置点。
- `input text` 偶发重复尾字符；dump 核对字段值，`KEYCODE_DEL` 修。
- gradlew 被 chmod +x 会脏工作区（mode 变更），检出时 git status 留意。
