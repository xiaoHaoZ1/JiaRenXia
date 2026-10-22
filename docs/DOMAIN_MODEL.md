# 家人侠 领域模型设计

版本：V0.1  
日期：2026-10-01  
项目名称：家人侠  
文档类型：Domain Model

> V0.1 实现阶段的补充决策见 DEVELOPMENT_PLAN.md 第 2 节；与本文冲突时，以该节为准。
>
> **历史文档**：V0.1（2026-10-01）早期设计，与当前实现有出入（例如人情/保单表已于 DB v13 删除、守护已拆为独立应用「守护侠」）。当前数据库结构以 apps/android/app/schemas/ 的 Room 导出为准，产品计划以 ROADMAP.md 为准。

---

## 1. 文档目的

本文档用于定义“家人侠”中的核心领域对象、对象之间的关系、关键数据约束以及未来亲属关系推导的基础。

本文档主要回答：

- 什么是“人物”
- 什么是“关系”
- 手机号应该如何建模
- 生日和提醒应该属于什么对象
- 哪些信息应该直接保存
- 哪些信息应该通过关系推导得到
- 哪些数据规则必须在领域层保持一致

本文档暂时不规定：

- Room 数据库具体表结构
- Kotlin 类具体实现
- Android API 使用方式
- UI 具体布局

---

## 2. 核心领域对象

家人侠 V0.1 的核心领域对象包括：

1. Person
2. Relationship
3. PhoneNumber
4. ImportantDate
5. ReminderPreference

其中最核心的是：

**Person + Relationship**

整个家庭关系网络都建立在这两个对象之上。

---

## 3. Person

Person 表示一个真实人物。

例如：

- 我
- 父亲
- 母亲
- 配偶
- 子女
- 祖辈
- 配偶父母
- 其他家人

未来也可以扩展到：

- 朋友
- 恩师
- 重要同事
- 其他重要的人

每一个人物都必须拥有独立唯一标识。

即使两个家庭成员姓名完全相同，也必须是两个不同的 Person。

---

## 4. Person 基础字段

Person 至少包含以下概念字段：

- id
- name
- gender
- avatar
- note
- livingStatus
- isSelf
- createdAt
- updatedAt

---

### 4.1 id

人物唯一标识。

要求：

- 全局唯一
- 不使用姓名作为唯一标识
- 不因为人物改名而变化

---

### 4.2 name

人物主姓名。

V0.1：

- 必填
- 只保留一个主姓名

后续可以扩展：

- 小名
- 乳名
- 曾用名
- 家庭内部称呼

---

### 4.3 gender

人物性别。

性别为 V0.1 必填字段：

- 创建人物时必须填写
- 未填写性别时，不允许完成该人物创建
- 不允许系统自动猜测人物性别

V0.1 主要用于：

- 关系展示
- 亲属称谓推导
- 人物信息展示

需要注意：

底层关系类型不能完全依赖性别。

例如：

`PARENT`

表示父母关系。

结合人物性别后，才可以进一步展示为：

- 父亲
- 母亲

---

### 4.4 avatar

人物头像。

V0.1：

- 可为空
- 用户可以从本地相册选择
- 不做人脸识别
- 不做人脸匹配

---

### 4.5 note

人物备注。

用于保存不适合结构化建模的信息。

例如：

- 喜欢喝什么茶
- 春节通常什么时候回家
- 目前住在哪里
- 家庭内部的特殊称呼习惯

---

### 4.6 livingStatus

人物状态。

V0.1 至少支持：

- ALIVE：在世
- DECEASED：已故

人物被标记为已故后：

- 不自动从关系图删除
- 不删除关系
- 不删除头像
- 不删除历史号码
- 不删除已有重要日期

---

### 4.7 isSelf

表示当前人物是否为“我”。

规则：

- 整个本地数据中最多只有一个 `isSelf = true`
- “我”是当前关系网络的默认观察中心
- 普通人物编辑页面不允许直接删除“我”

---

## 5. PhoneNumber

PhoneNumber 表示某个人物拥有的一个电话号码记录。

手机号不直接作为 Person 的单一字段保存。

原因：

一个人物可能存在：

- 多个当前有效手机号
- 工作手机号
- 备用手机号
- 已停用手机号
- 历史手机号

因此手机号必须作为独立领域对象存在。

---

## 6. PhoneNumber 字段

PhoneNumber 至少包含：

- id
- personId
- number
- label
- status
- isPrimary
- note
- createdAt
- updatedAt

---

### 6.1 personId

表示该手机号属于哪个人物。

一个 Person 可以拥有：

**0 到多个 PhoneNumber**

---

### 6.2 number

实际电话号码。

V0.1 不要求对电话号码做复杂国际化解析。

但保存时应尽量保留用户输入的真实号码。

---

### 6.3 label

手机号标签。

V0.1 可以支持：

- MOBILE：手机
- WORK：工作
- HOME：家庭
- BACKUP：备用
- OTHER：其他

标签仅用于帮助用户识别号码用途。

---

### 6.4 status

号码状态。

V0.1 至少支持：

- ACTIVE：当前使用
- INACTIVE：已停用 / 已废弃

已停用号码：

- 仍然保留在人物资料中
- 不进入默认拨号入口
- 不作为主号码

---

### 6.5 isPrimary

表示是否为该人物的主号码。

规则：

- 同一个人物最多只有一个主号码
- 人物不强制必须存在主号码，无主号码不影响人物正常存在
- 主号码如果存在，必须为 ACTIVE
- INACTIVE 号码不能成为主号码
- 主号码被标记为停用时，系统应提示用户重新选择主号码
- 如果该人物当前没有其他 ACTIVE 手机号，允许进入无主号码状态
- 没有主号码但存在 ACTIVE 号码时，可以展示第一个 ACTIVE 号码作为默认联系号码，但该号码不因此自动变成主号码

---

### 6.6 共享手机号规则

同一个电话号码允许出现在不同人物名下。

例如：

- 老年夫妻共用一个手机号
- 小孩使用父母联系方式
- 家庭成员共用一个联系电话

因此：

**PhoneNumber.number 不能作为全局唯一约束。**

系统后续可以提示：

“该号码已出现在其他人物资料中。”

但 V0.1：

- 不阻止保存
- 不自动合并人物
- 不自动判断谁是真实号码拥有者

---

## 7. Relationship

Relationship 表示两个人物之间的一条直接关系。

例如：

张三和李四之间存在父母子女关系。

Relationship 应该是程序可以理解和计算的结构化数据，而不是一句自然语言。

---

## 8. Relationship 基础字段

Relationship 至少包含：

- id
- sourcePersonId
- targetPersonId
- type
- createdAt
- updatedAt

未来可以扩展：

- startDate
- endDate
- relationStatus
- customTitle
- note

SIBLING 关系上允许可选记录：

- relativeAgeOrder（详见 9.5）

---

## 9. V0.1 基础关系类型

V0.1 底层至少支持以下基础关系：

- PARENT
- CHILD
- SPOUSE
- SIBLING

---

### 9.1 PARENT

表示：

sourcePerson 是 targetPerson 的父母之一。

例如：

张三 PARENT 李四

表示：

张三是李四的父母之一。

注意：这里是 Relationship 的存储写法（source type target），不是关系路径写法。

按第 11 节的关系路径写法，同一事实应写作：李四 → PARENT → 张三（从李四出发，沿 PARENT 到达其父母张三）。

再结合张三的 gender，可以展示为：

- 父亲
- 母亲

---

### 9.2 CHILD

表示：

sourcePerson 是 targetPerson 的子女之一。

PARENT 和 CHILD 是相反观察方向。

例如：

A PARENT B

则从 B 看 A：

A 是 B 的父母。

从 A 看 B：

B 是 A 的子女。

---

### 9.3 SPOUSE

表示配偶关系。

SPOUSE 是对称关系。

如果：

A SPOUSE B

则：

B SPOUSE A

V0.1 不要求实现复杂婚姻历史。

但领域模型不要假设所有用户都一定存在配偶。

---

### 9.4 SIBLING

表示兄弟姐妹关系。

SIBLING 是对称关系。

例如：

A SIBLING B

则：

B SIBLING A

结合：

- 性别
- 出生日期
- 可选的 relativeAgeOrder

可以进一步展示：

- 哥哥
- 弟弟
- 姐姐
- 妹妹

V0.1 不要求必须精确区分长幼。

---

### 9.5 relativeAgeOrder（可选）

SIBLING 关系上可以可选记录长幼顺序。

relativeAgeOrder 表示：

**sourcePerson 相对于 targetPerson 的长幼关系。**

relativeAgeOrder 是可空字段。

候选值（非 NULL 时）：

- OLDER：source 比 target 年长
- YOUNGER：source 比 target 年幼
- SAME：source 与 target 长幼相同，例如双胞胎且不区分先后时可以表达该状态

NULL 表示：

未记录或无法确定长幼关系。

规则：

- 仅对 SIBLING 关系有意义
- 为可选信息，缺失时不阻止建立 SIBLING 关系
- 对称反向关系需要保持一致：如果 A SIBLING B = OLDER，则从 B 看 A 应得到 YOUNGER
- 反向取值规则：OLDER ↔ YOUNGER，SAME ↔ SAME，NULL ↔ NULL
- 不要求用于 PARENT / CHILD / SPOUSE 或任意人物之间的一般年龄比较

---

## 10. 底层关系与展示称谓分离

家人侠必须明确区分：

**底层关系**

和

**用户看到的称谓**

例如：

底层关系：

PARENT

结合人物性别后，可以展示为：

父亲 / 母亲

再例如：

我  
→ PARENT  
→ 父亲  
→ SIBLING  
→ 某男性人物

结合长幼关系后，可以推导：

伯父 / 叔叔

因此：

“爷爷”“奶奶”“岳父”“岳母”“公公”“婆婆”“伯父”“叔叔”等，不应该作为 V0.1 底层基础关系类型直接保存。

---

## 11. 关系路径

关系路径用于描述：

一个人物通过哪些基础关系连接到另一个人物。

例如：

我  
→ PARENT  
→ 父亲  
→ PARENT  
→ 爷爷

关系路径可以表示为：

SELF  
→ PARENT  
→ PARENT

SELF 只是关系路径文档中的一个表示符号，表示：

**当前关系计算的观察者 / 起点人物。**

上面的路径表示：

从当前观察者出发，连续经过两层 PARENT 关系。

SELF：

- 不是 Relationship 类型
- 不写入 Relationship 数据
- 不需要单独创建实体
- 默认情况下可以对应 isSelf = true 的 Person
- 未来如果支持从任意人物视角查看关系，SELF 也可以表示当前被选中的观察人物

结合：

- 中间人物性别
- 目标人物性别
- 家庭分支
- 长幼关系

系统可以推导：

“爷爷”

---

## 12. 配偶父母

“岳父”“岳母”“公公”“婆婆”不作为底层关系类型。

例如：

我  
→ SPOUSE  
→ 配偶  
→ PARENT  
→ 配偶父亲

系统根据：

- 当前用户
- 配偶
- 目标人物性别

生成对应展示称谓。

这类称谓属于：

**派生关系称谓**

而不是基础 Relationship 类型。

---

## 13. 用户自定义称呼

中文家庭称呼存在明显地区差异和家庭习惯差异。

例如：

系统标准称谓：

伯父

家庭实际可能称呼：

- 大伯
- 伯伯
- 大爸

因此未来 Relationship 或人物关系展示层应允许存在：

`customTitle`

展示优先级可以考虑：

1. 用户自定义称呼
2. 系统推导称谓
3. 标准关系描述

V0.1 可以暂不正式实现 customTitle，但领域模型要预留这一方向。

---

## 14. ImportantDate

ImportantDate 表示与某个人物相关的重要日期。

例如：

- 生日
- 结婚纪念日
- 忌日
- 其他纪念日

V0.1 正式实现：

**生日**

其他日期类型作为后续扩展。

---

## 15. ImportantDate 字段

ImportantDate 至少可以包含：

- id
- personId
- type
- date（由 year、month、day 组成；year 可以为空，表示只知道月日）
- calendarType
- note
- createdAt
- updatedAt

---

### 15.1 type

生日按历法拆为两个类型：

- BIRTHDAY_SOLAR：阳历生日
- BIRTHDAY_LUNAR：农历生日（isLeapMonth 标记闰月）

PRD 中人物的“生日”在领域模型中通过以上两种 ImportantDate 表达；
同一个人物每种历法最多一条，合计最多两条生日，
两份生日分别编辑、删除，提醒偏好相互独立。

（数据库第 8 版前为单一 BIRTHDAY 类型，迁移时按 calendar_type 归入新类型。）

其他已实现类型：

- DEATH_ANNIVERSARY
- WEDDING_ANNIVERSARY / LICENSE_ANNIVERSARY / DATING_ANNIVERSARY
  （三种夫妻共享纪念日，`isCoupleShared`：一对夫妻每种类型共一条，
  挂任一方名下；双方详情页显示“相恋/领证/结婚 多久”并各自提醒）

后续可以扩展：

- CUSTOM

---

### 15.2 calendarType

支持：

- SOLAR：公历
- LUNAR：农历

生日行的 calendarType 必须与 type 槽位一致
（BIRTHDAY_SOLAR 对应 SOLAR，BIRTHDAY_LUNAR 对应 LUNAR）。

---

## 16. ReminderPreference

ReminderPreference 表示与某个重要日期对应的提醒偏好。

V0.1 主要用于生日提醒。

至少包含：

- id
- importantDateId
- enabled
- reminderOffsets
- createdAt
- updatedAt

规则：

- 每个 ImportantDate 最多对应一个 ReminderPreference
- ReminderPreference 允许不存在，表示该日期不设置提醒
- enabled = false 表示关闭该日期的提醒，但保留设置
- reminderOffsets 允许包含多个提醒时间，例如“当天 + 提前 1 天”

---

## 17. 生日提醒规则

V0.1 生日提醒遵循以下规则：

- 按年重复
- 用户可以开启或关闭
- 默认生日当天提醒
- 默认提前 1 天提醒
- 单个人物可以关闭提醒
- 没有生日信息则不创建生日提醒

后续支持：

- 提前 3 天
- 提前 7 天
- 自定义提前时间
- 自定义提醒时刻

领域模型只定义提醒偏好，不规定具体 Android 通知实现方式。

---

## 18. “我”作为观察中心

“我”在家人侠中具有特殊意义。

它不是一种关系类型，而是：

**当前用户在关系图中的默认观察节点。**

例如：

同一条关系：

A PARENT B

如果“我”是 B：

A 可能显示为：

父亲 / 母亲

如果“我”是其他人物：

A 的称谓可能完全不同。

因此：

关系称谓必须具有“观察者视角”。

不能简单把一个固定称谓永久写死在某个人物身上。

---

## 19. 关系推导

关系推导的目标是：

根据人物节点和基础关系路径，计算：

“目标人物与观察者之间是什么关系。”

例如：

我  
→ PARENT  
→ 父亲  
→ PARENT  
→ 男性人物

可以推导：

爷爷

再例如：

我  
→ SPOUSE  
→ 配偶  
→ PARENT  
→ 男性人物

可以推导：

岳父 / 公公等配偶父亲类称谓

具体推导规则不在本文件中展开。

后续单独建立：

`KINSHIP_ENGINE.md`

---

## 20. 数据一致性规则

V0.1 至少满足以下数据一致性要求：

### 人物

- 人物 ID 唯一
- 同名人物允许存在
- 整个本地数据最多只有一个 isSelf = true

---

### 手机号

- 一个人物允许多个手机号
- 一个手机号允许属于不同人物
- 同一人物不重复保存完全相同的号码（判断时忽略空格、短横线、括号等格式字符）
- 同一人物最多一个主号码
- 人物不强制存在主号码，无主号码不影响人物正常存在
- 主号码必须为 ACTIVE，INACTIVE 号码不能作为主号码
- INACTIVE 号码不能进入默认拨号入口
- 详情展示优先主号码；没有主号码但存在 ACTIVE 号码时，可以展示第一个 ACTIVE 号码作为默认联系号码，但该号码不因此自动变成主号码
- 人物没有任何 ACTIVE 号码时，不提供拨号快捷入口

---

### 关系

- 关系两端人物必须存在
- 不允许自己和自己建立 PARENT
- 不允许自己和自己建立 CHILD
- 不允许自己和自己建立 SPOUSE
- 不允许自己和自己建立 SIBLING
- 避免创建完全重复的关系
- 避免明显循环父母关系
- relativeAgeOrder 仅用于 SIBLING 关系，反向视角取值相反（OLDER ↔ YOUNGER，SAME ↔ SAME，NULL ↔ NULL）

例如：

A PARENT B

同时：

B PARENT A

属于明显冲突。

---

## 21. 删除规则

删除人物时：

- 删除与该人物直接关联的 Relationship
- 删除该人物的 PhoneNumber
- 删除该人物的 ImportantDate
- 删除对应 ReminderPreference
- 不影响其他无关人物

删除手机号时：

- 仅删除该号码记录
- 不删除人物
- 不删除其他号码

标记手机号为 INACTIVE：

- 不等于删除号码
- 历史记录继续保留
- 曾经真实使用过的号码，优先标记为 INACTIVE，而不是直接删除

删除“我”：

V0.1 不允许在普通人物编辑页面直接执行。

用户如果需要重新初始化，应通过：

“清空所有数据”

完成。

---

## 22. 领域模型概念关系

整体可以理解为：

```text
Person
 │
 ├── 0..n PhoneNumber
 │
 ├── 0..n ImportantDate
 │        │
 │        └── 0..1 ReminderPreference
 │
 └── 0..n Relationship
          │
          └── 连接另一个 Person
```