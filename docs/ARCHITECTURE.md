# 家人侠 技术架构设计

版本：V0.1  
日期：2026-10-01  
项目名称：家人侠  
文档类型：Architecture

> V0.1 实现阶段的补充决策见 DEVELOPMENT_PLAN.md 第 2 节；与本文冲突时，以该节为准。
>
> **历史文档**：V0.1（2026-10-01）早期设计，与当前实现有出入（例如人情/保单表已于 DB v13 删除、守护已拆为独立应用「守护侠」）。当前数据库结构以 apps/android/app/schemas/ 的 Room 导出为准，产品计划以 ROADMAP.md 为准。

---

## 1. 文档目的

本文档用于定义“家人侠”V0.1 的整体技术架构、技术栈、模块边界、数据存储方式和关键实现原则。

本文档建立在以下基线文档之上：

- PRD.md
- DOMAIN_MODEL.md
- KINSHIP_ENGINE.md

技术实现必须服务于已经确定的产品需求和领域模型。

不得为了技术方便，擅自改变产品规则。

---

## 2. V0.1 技术目标

家人侠 V0.1 的技术目标是：

1. 完成 Android 本地应用
2. 不依赖服务器
3. 不强制用户注册
4. 所有核心家庭数据保存在本机
5. 支持人物和家庭关系管理
6. 支持多个手机号
7. 支持家庭关系图展示
8. 支持基础亲属关系推导
9. 支持公历生日和本地生日提醒
10. 支持通过手机号发起系统拨号
11. 为后续守护功能迁移预留独立模块
12. 为后续云同步、农历生日等能力保留扩展空间

---

## 3. 平台范围

V0.1 首个平台：

**Android**

V0.1 暂不开发：

- iOS
- Web
- Windows
- macOS
- 微信小程序

后续是否扩展其他平台，根据产品发展情况决定。

---

## 4. Android 技术栈

V0.1 建议采用现代 Android 原生技术栈。

主要技术方向：

- Kotlin
- Jetpack Compose
- Material 3
- Android Architecture Components
- Room
- DataStore
- Hilt
- Kotlin Coroutines
- Kotlin Flow

可替换的实现细节：

- 本地提醒调度（可由 WorkManager / AlarmManager 等根据提醒精度要求实现）
- 图片加载库
- 关系图绘图方式

具体依赖版本在项目初始化时根据当时稳定版本确定。

本文档不锁死具体版本号。

---

## 5. 为什么使用 Kotlin

Kotlin 作为 Android 主开发语言。

原因：

- Android 官方主推
- 与 Jetpack Compose 配合成熟
- 空安全
- 协程支持良好
- 适合现代 Android 架构
- 社区和生态成熟

V0.1 不考虑 Java 作为主要开发语言。

---

## 6. UI 技术

V0.1 使用：

**Jetpack Compose**

而不是传统 XML View 作为主要 UI 方案。

原因：

- 更适合新项目
- 状态驱动 UI
- 与 ViewModel / Flow 配合自然
- 便于快速迭代
- 更适合后续关系图、卡片等动态界面

---

## 7. UI 设计原则

家人侠 UI 应遵循：

- 简洁
- 温暖
- 清晰
- 低学习成本
- 核心家人优先
- 不追求复杂炫技动画

第一版重点是：

**能用、清楚、稳定。**

而不是：

**视觉效果极度复杂。**

---

## 8. 整体架构

V0.1 推荐采用：

**单 App + 多模块架构**

整体分层：

```text
UI Layer
    ↓
Domain Layer
    ↓
Data Layer
```

其中：

UI

负责页面、用户交互、状态展示

Domain

负责业务规则、亲属关系推导、UseCase

Data

负责 Room、本地文件、Repository

---

## 9. 推荐模块结构

项目初期推荐模块如下：

```text
JiaRenXia
│
├── app
│
├── core
│   ├── model
│   ├── database
│   ├── datastore
│   ├── common
│   ├── ui
│   └── kinship
│
├── feature
│   ├── family
│   ├── person
│   ├── relationship
│   ├── reminder
│   ├── guardian
│   └── settings
│
└── docs
```

注意：

以上首先描述的是逻辑边界。

具体 Gradle Module 是否从第一天全部物理拆分，可以根据项目复杂度决定。

V0.1 可以先保持工程可控，不为了“架构漂亮”制造过度模块化。

---

## 10. app 模块

app 是 Android 应用入口。

主要负责：

- Application
- Activity
- App Navigation
- Hilt 初始化
- 全局主题
- 启动流程
- 各 feature 模块组合

app 模块不应承载大量业务逻辑。

---

## 11. core:model

用于放置核心领域模型。

例如：

- Person
- Relationship
- PhoneNumber
- ImportantDate
- ReminderPreference
- RelationshipType
- Gender
- LivingStatus
- PhoneStatus
- RelativeAgeOrder

这些模型应与 DOMAIN_MODEL.md 保持一致。

---

## 12. core:kinship

这是家人侠最重要的核心模块之一。

负责：

- 关系路径计算
- 亲属称谓推导
- 结构关系生成
- EXACT / PARTIAL 判断
- missingInformation
- 长幼顺序处理

该模块必须：

- 尽量不依赖 Android UI
- 不依赖 Compose
- 不直接访问 Activity
- 不直接访问 Room DAO
- 尽量保持纯 Kotlin

这样未来：

- 单元测试更容易
- 可以独立开源
- 可以被其他模块复用

---

## 13. core:database

负责本地结构化数据存储。

V0.1 使用：

Room

主要保存：

- Person
- Relationship
- PhoneNumber
- ImportantDate
- ReminderPreference

Room 只负责持久化。

数据库 Entity 和领域模型可以分离。

不要强制让数据库 Entity 直接等于 Domain Model。

---

## 14. core:datastore

用于保存轻量配置。

例如：

- 是否完成首次初始化
- 用户设置
- 提醒总开关
- UI 偏好
- App 配置

建议使用：

DataStore

不使用 SharedPreferences 作为新项目核心配置方案。

---

## 15. core:common

用于保存通用能力。

例如：

- 日期工具
- Result 包装
- 通用扩展函数
- 日志接口
- 基础错误类型

不要把业务逻辑大量塞入 common。

---

## 16. core:ui

用于保存跨页面复用的 UI 组件。

例如：

- PersonAvatar
- FamilyMemberCard
- EmptyState
- Loading
- ConfirmDialog
- AppTopBar

避免每个 feature 重复写相同组件。

---

## 17. feature:family

负责：

- 家庭首页
- 核心家人展示
- 家庭关系入口
- 核心关系概览

首页优先展示：

用户已经建立的核心家人。

不存在的家庭角色：

不显示空占位。

---

## 18. feature:person

负责：

- 创建人物
- 查看人物
- 编辑人物
- 删除人物
- 头像管理
- 多手机号管理

不负责复杂亲属关系计算。

关系计算由 core:kinship 负责。

---

## 19. feature:relationship

负责：

- 家庭关系图
- 关系节点展示
- 点击人物
- 关系路径展示
- 查看推导后的称谓

底层关系数据来源：

Relationship

推导能力来源：

core:kinship

---

## 20. feature:reminder

负责 V0.1 的：

- 今天生日
- 即将生日
- 生日提醒设置
- 本地通知

V0.1 暂不负责：

- 联系提醒
- AI 祝福
- 复杂纪念日系统
- 农历生日换算

---

## 21. feature:guardian

用于隔离现有“守护”相关能力。

当前状态：

已迁移（V0.2），实现与约定见 GUARDIAN_MODULE.md。

设计原则：

- 不与家庭关系核心逻辑耦合
- 不影响核心 App 启动
- 不把监控逻辑写入 family / person 模块
- 单独评估权限和 Android 后台限制

V0.1 是否正式启用，由迁移成本决定。

---

## 22. feature:settings

负责：

- 数据管理
- 隐私设置
- 关于家人侠
- 开源信息
- 后续备份与恢复入口

V0.1 不要求正式实现完整备份恢复系统。

---

## 23. Repository 层

UI 不应直接访问 Room DAO。

推荐调用链：

```text
UI
↓
ViewModel
↓
UseCase / Domain Service
↓
Repository
↓
DAO
↓
Room
```

Repository 负责屏蔽具体数据来源。

例如：

- PersonRepository
- RelationshipRepository
- PhoneNumberRepository
- ImportantDateRepository
- ReminderRepository

---

## 24. UseCase

复杂业务建议通过 UseCase 表达。

例如：

- CreatePerson
- UpdatePerson
- DeletePerson
- AddRelationship
- RemoveRelationship
- GetFamilyGraph
- ResolveKinship
- SetBirthday
- UpdatePhoneNumber
- SetPrimaryPhoneNumber
- DisablePhoneNumber

简单查询不必强行创建大量 UseCase。

避免为了 Clean Architecture 而过度设计。

---

## 25. 数据库设计原则

Room 数据库需要遵守：

- 主键不能使用姓名
- Person 使用稳定 ID
- PhoneNumber.number 不能全局 UNIQUE
- 同人物最多一个主号码
- Relationship 两端必须引用存在的 Person
- ImportantDate(BIRTHDAY) 每个人最多一条
- 删除 Person 时清理关联数据
- 不因手机号停用而删除手机号记录

具体 Entity 和表结构将在后续 DATABASE_SCHEMA.md 中定义。

---

## 26. ID 策略

V0.1 建议核心实体使用：

UUID

例如：

- personId
- relationshipId
- phoneNumberId
- importantDateId

不要使用：

- 姓名
- 手机号

作为主键。

数据库内部是否额外使用自增数字 ID，可在 DATABASE_SCHEMA.md 中再决定。

---

## 27. 头像存储

V0.1 头像采用：

本地文件存储

数据库只保存：

- 头像文件在 App 私有目录中的路径（或指向该私有文件的 URI）

不保存相册或图片选择器返回的外部临时 URI（原因见第 28 节）。

不建议把大图片二进制直接保存进 Room。

---

## 28. 头像处理原则

用户从相册选择头像后，应用应考虑：

- 获取图片
- 裁剪为适合头像的比例
- 压缩
- 复制并保存到 App 私有目录
- 数据库记录引用

目标：

避免长期依赖外部相册临时权限或临时 URI 导致头像失效。

V0.1 只支持：

一人一个头像。

不做人物相册。

---

## 29. Local First

家人侠 V0.1 坚持：

Local First

默认：

- 不注册
- 不登录
- 不上传家庭数据
- 不依赖云服务器
- 不进行网络同步

核心数据保存在本地。

---

## 30. 数据安全

家人侠保存的信息具有较高隐私属性。

V0.1 至少遵循：

- 数据保存在 App 私有空间
- 头像保存在私有目录
- 不主动上传数据
- 不申请与功能无关的权限
- 不记录无必要的敏感日志

后续可以考虑：

- 数据库加密
- 本地备份加密
- 生物识别解锁
- 应用密码

这些不作为 V0.1 强制要求。

---

## 31. 权限最小化

V0.1 仅在真正需要时申请权限。

例如：

头像

只申请选择图片所需要的能力。

通知

生日提醒需要通知权限时，再向用户解释用途并申请。

电话

发起拨号优先使用系统拨号页面。

尽量避免申请直接拨号高权限。

---

## 32. 快捷拨号

人物存在 ACTIVE 手机号时：

可以从详情页进入拨号。

拨号号码选择遵循 PRD 规则：

- 默认优先使用主号码
- 没有主号码但存在 ACTIVE 号码时，使用第一个 ACTIVE 号码作为默认联系号码，但该号码不因此自动变成主号码
- 存在多个有效手机号时，允许用户选择号码
- INACTIVE 号码不进入拨号入口
- 人物没有任何 ACTIVE 号码时，不显示拨号快捷入口

推荐：

调用系统拨号界面并预填号码。

即：

用户仍需要自行确认拨出电话。

优点：

- 权限更少
- 风险更低
- 用户控制更明确

---

## 33. 生日提醒架构

V0.1 生日提醒为：

本地提醒

不依赖服务端。

主要流程：

```text
ImportantDate(BIRTHDAY)
        ↓
ReminderPreference
        ↓
Reminder Scheduler
        ↓
Android 本地通知
```

---

## 34. 提醒调度

生日提醒需要一个提醒调度抽象层。

具体实现可由 WorkManager / AlarmManager 等根据提醒精度要求评估后决定。

调度层负责：

- 检查近期生日
- 重新安排必要提醒
- 设备重启或系统调度后的恢复

ARCHITECTURE.md 不锁死具体提醒实现细节。

---

## 35. 生日提醒时间

产品规则来自 PRD：

默认：

- 提前 1 天
- 生日当天

规则：

- 生日提醒按年重复
- 用户可以选择是否开启生日提醒
- 单个人物可以关闭生日提醒
- 没有生日信息的人物不创建提醒

技术实现必须支持按年重复。

不要把“每年生日”硬编码成一次性通知。

---

## 36. Kinship Engine 架构

core:kinship 应设计成尽量纯粹的领域逻辑。

输入：

- observer
- target
- Person 数据
- Relationship 数据
- ImportantDate 中的生日
- SIBLING 关系上的 relativeAgeOrder

输出：

- relationshipPath
- structuralDescription
- standardTitle
- confidence
- missingInformation
- customTitle（未来扩展，V0.1 允许为空）

具体规则以：

KINSHIP_ENGINE.md

为准。

---

## 37. 关系图数据来源

关系图 UI 不应该自行计算关系。

推荐：

```text
Room
↓
Repository
↓
Family Graph Domain Model
↓
ViewModel
↓
Compose
```

UI 负责：

- 展示节点
- 展示连接
- 交互

领域层负责：

- 哪些人物有关联
- 关系是什么
- 当前观察者是谁

---

## 38. 家庭关系图 V0.1

第一版重点：

- 核心家庭关系
- 人物节点
- 基本连接线
- 点击节点
- 拖动
- 缩放

不要求：

- 上百人的大型家谱性能优化
- 自动复杂树布局
- 三维图谱
- 华丽动画
- 完整旁系族谱展示

---

## 39. 状态管理

Compose 页面状态推荐：

```text
Repository Flow
↓
ViewModel
↓
StateFlow
↓
Compose UI
```

UI 不直接持有数据库对象作为全局状态。

---

## 40. ViewModel

每个主要 Feature 可以拥有对应 ViewModel。

例如：

- FamilyViewModel
- PersonDetailViewModel
- PersonEditViewModel
- RelationshipViewModel
- ReminderViewModel
- SettingsViewModel

避免创建一个控制整个 App 的巨大 ViewModel。

---

## 41. Navigation

V0.1 使用 Compose Navigation。

主要路线可以包括：

- Splash
- Onboarding
- CreateSelf
- AddCoreFamily
- Home
- PersonDetail
- PersonEdit
- RelationshipGraph
- Reminder
- Guardian
- Settings

具体 route 命名在代码阶段确定。

---

## 42. 错误处理

业务错误与 UI 提示应分离。

例如：

- 人物不存在
- 关系冲突
- 主号码状态非法
- 生日数据非法
- 图片保存失败

领域层返回明确错误。

UI 决定如何展示。

---

## 43. 日志

日志中不得随意输出：

- 完整手机号
- 家庭成员姓名
- 生日
- 家庭关系
- 私人备注

开发阶段如需调试，应尽量脱敏。

Release 版本应减少不必要日志。

---

## 44. 测试策略

V0.1 至少需要：

单元测试

重点测试：

- Kinship Engine
- Relationship 一致性规则
- PhoneNumber 主号码规则
- relativeAgeOrder
- 生日提醒计算

Repository 测试

验证：

- Person CRUD
- Relationship CRUD
- 手机号状态修改
- ImportantDate 保存

UI 测试

第一阶段只覆盖关键流程即可：

- 创建“我”
- 添加家人
- 编辑人物
- 查看关系
- 查看生日提醒

---

## 45. Kinship Engine 测试优先级

core:kinship 属于重点测试模块。

至少覆盖：

- 父亲
- 母亲
- 儿子
- 女儿
- 配偶
- 哥哥
- 弟弟
- 姐姐
- 妹妹
- 爷爷
- 奶奶
- 姥爷
- 姥姥
- 伯父
- 叔叔
- 姑姑
- 舅舅
- 姨妈
- 岳父
- 岳母
- 公公
- 婆婆
- 信息不足后的 PARTIAL 结果

亲属称谓推导不允许只依靠人工点页面测试。

---

## 46. 守护模块隔离原则

守护模块和家庭关系模块必须低耦合。

例如：

feature:guardian

不应直接操作：

PersonDao

RelationshipDao

如果未来需要关联人物，应通过明确的领域接口进行。

这样即使守护模块暂时移除，也不会影响：

- 家庭关系
- 人物管理
- 生日提醒
- 关系推导

---

## 47. 后续云同步

V0.1 不做云同步。

但是架构应避免：

UI 直接绑定 Room。

通过 Repository 抽象后，未来理论上可以扩展：

LocalDataSource

RemoteDataSource

但 V0.1 不需要提前实现 RemoteDataSource。

---

## 48. 不做过度架构

家人侠虽然希望长期发展，但 V0.1 仍然是个人开源项目初期。

因此禁止为了“未来可能用到”提前实现大量复杂架构。

例如暂时不要：

- 微服务
- GraphQL Server
- 消息队列
- 多端同步协议
- CRDT
- 插件系统
- 复杂事件总线
- 远程配置平台

当前原则：

够用、清晰、可测试、可扩展。

---

## 49. 推荐开发顺序

建议开发顺序：

第一阶段

工程初始化：

- Kotlin
- Compose
- Hilt
- Room
- Navigation
- 基础主题

第二阶段

领域与数据库：

- Person
- PhoneNumber
- Relationship
- ImportantDate
- ReminderPreference

第三阶段

人物功能：

- 创建“我”
- 人物新增
- 人物详情
- 人物编辑
- 多手机号

第四阶段

家庭关系：

- 添加关系
- 家庭首页
- 家庭关系图

第五阶段

Kinship Engine：

- 基础路径
- 一级称谓
- 二级称谓
- relativeAgeOrder
- 降级策略

第六阶段

生日提醒：

- ImportantDate
- ReminderPreference
- 本地调度
- 通知页面

第七阶段

守护模块：

评估现有代码后决定是否迁移到 V0.1。

---

## 50. V0.1 架构完成标准

如果技术架构能够稳定支持以下能力，可以认为 V0.1 架构成立：

1. 完全本地运行
2. 创建和编辑人物
3. 多手机号管理
4. 家庭关系管理
5. 核心关系图展示
6. Kinship Engine 独立运行
7. 公历生日保存
8. 本地生日提醒
9. 系统拨号入口
10. App 重启后数据完整
11. 不依赖服务器
12. 不要求账号登录

---

## 51. 后续文档

ARCHITECTURE.md 确认后，继续拆分以下技术文档：

- DATABASE_SCHEMA.md（已建立）
- DEVELOPMENT_PLAN.md（已建立）
- TEST_PLAN.md

守护模块如果确定迁移，可单独建立：

- GUARDIAN_MODULE.md（已建立）

当前项目设计文档结构：

```text
<产品根目录>
└── docs
    ├── VISION.md
    ├── PRD.md
    ├── BRAND.md
    ├── ROADMAP.md
    ├── DOMAIN_MODEL.md
    ├── KINSHIP_ENGINE.md
    ├── ARCHITECTURE.md
    ├── DATABASE_SCHEMA.md
    └── DEVELOPMENT_PLAN.md
```
