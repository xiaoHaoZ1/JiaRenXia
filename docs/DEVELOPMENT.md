# 家人侠 — 开发指南

本仓库是家人侠的统一产品根目录：Android 应用、官网（纯静态站点）、设计文档、资料都在这里。

协作约定（提交规范、完整验证命令、正式版签名、发布流程、两台开发机差异）以仓库根目录的
[AGENTS.md](../AGENTS.md) 为准，本文不重复；这里是面向开发者的入口说明。

## 目录

```text
JiaRenXia/
├─ apps/
│  ├─ android/        Android 应用（完整 Gradle 工程）
│  └─ web/            官网（纯静态站点，React + TypeScript + Vite）
├─ docs/              设计文档（事实来源）；web/、deployment/、migration/、design/
├─ assets/            品牌资源（brand/）与产品体验截图（screenshots/）
├─ scripts/           根目录任务入口（jrx.cmd 调用）
├─ worktrees/         Git worktree（不入库）
├─ runtime/           本机日志、截图等运行产物（不入库）
└─ private/           本机配置（不入库）
```

## 快速开始

### 官网（纯静态站点）

需要 Node 20+。没有后端，构建产物 `apps/web/dist` 可交给任意静态服务器。

```bat
jrx.cmd web-build   :: npm ci + 构建 → apps\web\dist
jrx.cmd web-dev     :: 开发服务器（Vite，热更新）
jrx.cmd web-e2e     :: Playwright 端到端自检（自动构建 + vite preview）
jrx.cmd web-start   :: 本机生产上线（serve.mjs，回环 8088，经 cloudflared 对公网）
jrx.cmd web-stop    :: 官网下线；web-status 查状态
```

- 站点包含：首页、下载、小侠家族、帮助（含“支持作者”收款码）、隐私政策、用户协议。
- 下载页引用的 `public/jiarenxia.apk` 不入库，部署前把最新签名包拷进去，并同步 `src/site.ts` 里的版本、大小与 SHA-256。
- 生产部署与故障处理见 [deployment/LOCAL_WINDOWS.md](deployment/LOCAL_WINDOWS.md)。

### Android

```bat
jrx.cmd android :app:assembleDebug     :: 在产品根目录构建（转到 apps\android 执行 gradlew）
jrx.cmd android-verify                 :: AGENTS.md 规定的完整验证
```

也可以直接在 `apps\android` 下运行 `gradlew.bat`；用 Android Studio 时打开 `apps/android`。

## 从源码构建

### 环境

- JDK 17 及以上
- Android SDK（compileSdk 36）
- Android Studio：本项目使用 Android Gradle Plugin 8.13，在 Android Studio Otter 2 Feature Drop（2025.2.2）上验证
- 设备要求：Android 8.0（API 26）及以上

### 常用命令

Gradle 工程根目录是 `apps/android`，以下命令在该目录执行（在产品根目录可用 `jrx.cmd android <任务>` 代替 `./gradlew <任务>`）：

```bash
cd apps/android

# 构建调试包（Windows 使用 gradlew.bat）
./gradlew :app:assembleDebug

# 运行全部单元测试
./gradlew :core:model:test :core:kinship:test :app:testDebugUnitTest

# 代码检查
./gradlew :app:lintDebug

# 安装到已连接的手机
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

小米 / HyperOS 手机需要在“开发者选项”中打开“USB 安装”，安装时还要在手机上确认“继续安装”。

完整验证任务清单与已知问题见 [AGENTS.md](../AGENTS.md) 的「构建 / 测试 / 安装」；
发布正式版签名包的做法见其「正式版签名」小节。

## 技术栈

- Kotlin、Jetpack Compose（Material 3）
- Room（本地数据库）、DataStore（设置）
- Hilt（依赖注入）、Kotlin Coroutines / Flow
- WorkManager（提醒调度）、Navigation Compose
- 单元测试：JUnit、Robolectric
- 网站：React、TypeScript、Vite（纯静态，无后端）；浏览器测试 Playwright

## Android 工程结构

```text
apps/android
├── app            Android 应用
│   ├── data       Room 数据库、FamilyRepository、头像与设置存储
│   ├── domain     家庭总览、核心家人排序、搜索
│   ├── reminder   提醒计算与调度
│   └── ui         各页面（Compose）
├── core
│   ├── model      领域模型（纯 Kotlin）
│   ├── kinship    亲属称谓推导引擎（纯 Kotlin，可独立测试）
│   └── designsystem 设计系统（主题、通用组件、插画资源）
├── feature
│   └── guardian   守护模块（无活动检测 + 紧急联系；不依赖 Room/Hilt）
└── testdata       测试家庭备份（合成数据）
```

## 设计文档

| 文档 | 内容 |
|---|---|
| [VISION.md](VISION.md) | 产品理念 |
| [PRD.md](PRD.md) | V0.1 产品需求（历史文档） |
| [BRAND.md](BRAND.md) | 品牌设计说明 |
| [DOMAIN_MODEL.md](DOMAIN_MODEL.md) | 领域模型 |
| [KINSHIP_ENGINE.md](KINSHIP_ENGINE.md) | 亲属称谓推导规则 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 技术架构 |
| [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) | 数据库结构 |
| [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) | 各版本的实现决策与开发记录（编号偏旧，非仅 V0.1/V0.2） |
| [GUARDIAN_MODULE.md](GUARDIAN_MODULE.md) | 守护模块设计与约定 |
| [FAMILY_AFFAIRS.md](FAMILY_AFFAIRS.md) | 家事、家人共享、碰一碰、合并（会员设计已下线，第 8 节留档） |
| [web/README.md](web/README.md) | 网站信息结构、界面与使用流程 |
| [deployment/LOCAL_WINDOWS.md](deployment/LOCAL_WINDOWS.md) | 官网静态构建与部署 |
| migration/2026-10-06-unified-root.md | 统一目录迁移记录与回滚说明（维护者内部文档，未随源码公开） |
