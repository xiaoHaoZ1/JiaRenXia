# 2026-11-01 换电脑交接：「家书」界面改版（第①批已完成）

## 接手先看

- 分支 `feature/jiashu`，已推送远端，最新提交 `8abf31b`。从 main `160ba5e` 开出；main 之后多了 `3e6bdb7`（AGENTS.md）和 `986f3b6`（测试版交接文档），都不碰本分支的文件，合并时不会冲突。
- 规格在本分支 `docs/DEVELOPMENT_PLAN.md` **2.22**（2.21 是「关于TA」）。设计稿源文件 `docs/design/jiashu-main.html`、`docs/design/jiashu-more.html`、`docs/design/jiashu.css`，`node docs/design/render.mjs` 重出 PNG 到 `runtime/design/`（需要 `apps/web` 装好 Playwright）。
- 本文**取代** main 上 `docs/handoffs/2026-11-01-beta-2.2.0-switch-computer.md`「其他待办」里关于「家书」的那段：那段写的“宋体保留一种字重”“列表行首加小方印”都已被用户否掉，规格已由本会话写定、方向经用户确认（见下）。
- **下一步：先把第①批的渲染图给用户看；用户认可后再做第②批家庭页。** 第①批是换电脑前刚做完的，用户还没看过效果。

## 用户已定（2026-11-01）

- 三个方向选「家书」：纸色底、墨色字、朱砂管喜事与印章、墨青管可点的操作。
- 不用宋体，全稿只用系统黑体，层级靠字号与字重。
- 家庭关系图版式、控件、交互不改；底色跟着换（用户原话“跟着也换底色”）。
- 家庭页名单不放头像（试过行首小方印，用户否掉）；只限家庭页，「我的」页个人资料的方形头像保留。
- 家庭页大标题按“我”的姓写「X家」（取名字首字，复姓查表取两字；名字为空或不是汉字时回退「家庭」）。
- 顶栏查找、添加（家事页的新建）用**图标加文字**，不单用图标。
- 人物详情等「关于TA」合并后再改——已合并进 main，本分支已包含，按顺序第⑤批做即可。
- 按计划分批：① 配色、字体与全局组件 → ② 家庭页 → ③ 家事页 → ④ 我的、二级页、弹框 → ⑤ 人物详情 → ⑥ 收尾。

由本会话拍板、用户未反对的细节（规格 2.22 已写明）：数字一律阿拉伯数字；开关保留 Material Switch，只有多选一改文字加朱砂下划线；深色模式文案仍叫「深色」；「N 代同堂」只数在世者、3 代以上才显示；今日惦记的取舍规则；「核心家人」改叫「至亲」。

## 已完成

| 内容 | 提交 |
|---|---|
| 设计稿源文件入库、render.mjs 支持重出；规格 2.22（token、字重、全局组件、各页规格、批次、不做清单） | `1666f0e` |
| 第①批：配色 token（浅/深）、三档字重、页标题 `PageTitle`（字号放大到 1.3 倍封顶）、圆角 4/6/8/12、主按钮全部 4dp、底栏只留文字加朱砂点、`JrxCard` 纸色细框、`TitleChip`/`CountdownBadge`/`DateTile` 改朱砂、关系图“我”改朱砂且普通卡片加发丝框（侧别色保持原色相）、桌面插件调色板、关系图导出图片、启动窗口底色同步换色 | `8abf31b` |

验证（机器 B，`8abf31b`）：:app 单元测试 937 个、0 失败（`GraphExportTest`、`JrxWidgetRenderTest` 里写死的旧底色已改成新底色）；lint 0 error、27 warning（改动文件里没有新增）；debug 与 release 构建成功。

新组件在 `core/designsystem/.../ui/designsystem/Paper.kt`：`PageTitle`（已用于家庭、家事、我的三页标题）；`DoubleRule`、`PageTitleBlock`、`SealMark`、`SealLabel`、`TopTextAction` 已写好，第②批起使用。新增扩展色 `JrxColors.seal` / `onSeal` / `sealLine` / `sealContainer`。

## 开工步骤（另一台电脑）

1. 先 `pwd` 确认机器，读 AGENTS.md 和本机的 `private/AGENTS.local.md`；`git status --short --branch` 看主工作区有没有未提交的改动（有就先问用户，不要 reset、clean 或覆盖）。
2. 按本机方式拉取远端，主工作区 `git merge --ff-only origin/main`。
3. `git branch feature/jiashu origin/feature/jiashu`，`git worktree add worktrees/jiashu feature/jiashu`，复制主工作区的 `apps/android/local.properties`。
4. 在 worktree 的 `apps/android` 跑 `gradlew.bat :app:testDebugUnitTest --tests *ScreenShotsTest*`，把这几张给用户看（`app/build/outputs/roborazzi/`）：`b_family_birthday_today`、`b_affairs_with_nav`、`b_mine_with_nav`、`b_graph`、`b_detail_mom`、`b_affairs_timeline_dark`，以及 `widget_ready_light`。想要改前对照，就在主工作区（main）跑同一条命令。

## 第②批：家庭页（规格 2.22 第四节「家庭页」1–10 条）

实现要点（设计已定，按规格逐条做，不要自己改文案和层级）：

1. **姓氏标题**：新增纯函数（建议 `domain/FamilyTitle.kt`）`familyTitle(selfName)`，返回「X家」或回退「家庭」；复姓表至少含欧阳、司马、上官、诸葛、东方、皇甫、尉迟、公孙、慕容、长孙、宇文、司徒、令狐、夏侯、端木、轩辕、独孤、南宫、西门、百里、呼延、钟离。JUnit 覆盖：单姓、复姓、单字名、空名、英文名。
2. **代数**：`FamilyUiState` 增加在世辈分数；`FamilyViewModel` 注入 `KinshipEngine`，用 `engine.generations(ov.graph, ov.self.person.id)`（同 `GraphViewModel` 第 219 行）只数在世者。摘要「N 位家人 · M 代同堂」，M ≥ 3 才加后半句；N 沿用现有口径（核心 + 其他，不含本人）。
3. **今日惦记**：新增纯函数，输入总览、今天、农历、`contactOverdue`、`defaultNumbers`，输出主句、次行、副句和拨号对象；替换 `TodayBirthdayBanner` 与「近期生日」卡（`BirthdayRow` 若再无调用方一并删）。单测覆盖：今天生日（本人、他人、多人）、7 天内生日、只有未联系、什么都没有（整块不显示）。主句用 `SealLabel("今日惦记")` + 700 大字，天数朱砂放大；「打个电话」用墨青文字按钮。
4. **页头**：`PageHeader` 内顶行右侧 `TopTextAction` 两个（查找 / 添加），下方 `PageTitleBlock(title, summary, titleTrailing = { SealMark("家") })`；删掉现在的两个 `OutlinedButton`。
5. **关系图入口**：一行（墨青线性图标 + 标题/说明 + 墨青箭头），上下发丝线；删掉 `JrxCard` 与 `GraphThumbnail`。
6. **至亲**：区块标题「至亲」+ 淡色「N 位」；单列行替换 `CoreMemberCell` 两列网格，状态优先级沿用 `CoreMemberCell` 里的 `status` 逻辑；称谓 `TitleChip(CORE)`；不放头像；行间发丝线。`FamilyOrdering.coreRows` 只有家庭页在用，改成单列后若无调用方，连同 `FamilyOrderingTest` 里两个 `核心家人分行_*` 用例一起删（排序本身保留）。
7. **其他家人分组**：行式同至亲，去头像。
8. **共享与连接**：行式入口，放在列表末尾。
9. **测试同步**：`ScreenShotsTest` 第 235 行、第 1723 行断言文字「核心家人」改成「至亲」；新增 `family_home_lg`（大字号，看标题封顶）与 `family_home_dark` 两张截图。
10. **验收**：主代理逐张看 `family_home`、`family_birthday_today`、`family_with_nav`、`family_home_lg`、`family_home_dark`，再跑全量验证（AGENTS.md「完整验证命令」或 `jrx.cmd android-verify`）。做完提交推送，给用户看图。

## 后续批次要记着的（第①批走查时看到的）

- 家事（③）：「已逾期 N 天」现在是 errorContainer 粉底，改朱砂实心印；铃铛与「更多」单独占一行在页头下方，挪进顶栏并加「新建」（去掉 `ExtendedFloatingActionButton`）。
- 我的与二级页（④）：外观页「深浅模式」「桌面插件配色」仍是分段按钮，改文字加朱砂下划线；共享与连接里「结为夫妻」的心形是墨青（`SettingsNavRow` 用 primary），考虑改朱砂；`SettingsScaffold` 标题还是行内 titleLarge。
- 人物详情（⑤）：区块标题 `SectionTitle` 是墨青；快捷操作是圆形 `QuickAction`；占位头像是饱和暖色渐变圆（`AvatarPalette`），稿子是纸色方框写姓，第⑤批一起定，关系图节点头像是否跟着改要先问用户（关系图版式不改）。
- 不在范围：官网 OG 封面（`OgCoverTest`）、守护侠（独立工程，有自己的 designsystem 副本）。

## 机器 B 上的残留（不入库，只在机器 B）

- `runtime/design/`：家书稿工作副本 `b-pages*.html/png`（已入库为 `docs/design/jiashu-*`）、宋体原稿备份 `_v1-serif/`、第①批改前改后对比图 `jiashu-b1/compare.png`。
- `runtime/jiashu-baseline/`：改版前（main `160ba5e`）的 135 张截图。
- `worktrees/jiashu`：干净，已推送；换回机器 B 时先 `git fetch` 再继续。
