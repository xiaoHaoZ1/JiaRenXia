# 家人侠 数据库结构设计

版本：V0.1  
日期：2026-10-01  
项目名称：家人侠  
文档类型：Database Schema

> V0.1 实现阶段的补充决策见 DEVELOPMENT_PLAN.md 第 2 节；与本文冲突时，以该节为准。
>
> **历史文档**：V0.1（2026-10-01）早期设计，与当前实现有出入（例如人情/保单表已于 DB v13 删除、守护已拆为独立应用「守护侠」）。当前数据库结构以 apps/android/app/schemas/ 的 Room 导出为准，产品计划以 ROADMAP.md 为准。

---

## 1. 文档目的

本文档用于定义“家人侠”V0.1 的本地 Room 数据库结构。

本文档建立在以下基线文档之上：

- PRD.md
- DOMAIN_MODEL.md
- KINSHIP_ENGINE.md
- ARCHITECTURE.md

本文档主要定义：

- 数据表
- 字段
- 主键
- 外键
- 索引
- 唯一约束
- 删除规则
- 领域对象与数据库之间的映射

本文档暂时不包含：

- Kotlin Entity 代码
- DAO 代码
- Repository 实现
- Migration 代码
- SQLCipher 等数据库加密方案

---

## 2. V0.1 数据库范围

V0.1 至少包含以下核心表：

1. persons
2. relationships
3. phone_numbers
4. important_dates
5. reminder_preferences
6. reminder_offsets

这些表分别对应：

```text
Person
Relationship
PhoneNumber
ImportantDate
ReminderPreference
ReminderPreference.reminderOffsets（拆分为独立表）
```

---

## 3. 数据库设计原则

V0.1 数据库设计遵循以下原则。

稳定 ID

所有核心实体使用稳定唯一 ID。

不使用：

- 姓名
- 手机号
- 生日

作为主键。

Local First

所有核心数据保存在本地 Room 数据库。

V0.1 不依赖远程数据库。

领域模型优先

数据库结构必须能够表达 DOMAIN_MODEL.md 中已经定义的领域规则。

不能为了数据库实现方便改变领域含义。

删除规则明确

删除 Person 时，必须明确清理：

- PhoneNumber
- ImportantDate
- ReminderPreference
- Relationship

避免产生孤儿数据。

历史信息尽量保留

例如：

手机号停用时，应优先标记为：

INACTIVE

而不是立即删除。

命名分层

本文档中的表名、列名使用 snake_case（例如 is_self、relative_age_order、important_date_id），对应 DOMAIN_MODEL.md 中使用 camelCase 的领域字段（例如 isSelf、relativeAgeOrder、importantDateId）。

二者是不同层的命名，指同一概念。本文档在描述领域规则时也会直接引用领域字段名。

---

## 4. ID 类型

V0.1 建议核心实体 ID 使用 UUID 字符串。

概念类型：

String UUID

例如：

550e8400-e29b-41d4-a716-446655440000

适用于：

- personId
- relationshipId
- phoneNumberId
- importantDateId
- reminderPreferenceId

Room 底层最终使用 TEXT 保存即可。

---

## 5. persons 表

表名：

persons

作用：

保存人物基础信息。

### 5.1 字段

建议字段：

| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| id | TEXT | 否 | Person 唯一 ID |
| name | TEXT | 否 | 人物姓名 |
| gender | TEXT | 否 | 性别 |
| avatar_path | TEXT | 是 | 头像本地路径 |
| note | TEXT | 是 | 人物备注 |
| living_status | TEXT | 否 | ALIVE / DECEASED |
| is_self | INTEGER / BOOLEAN | 否 | 是否为“我” |
| created_at | INTEGER | 否 | 创建时间 |
| updated_at | INTEGER | 否 | 更新时间 |

---

### 5.2 主键

PRIMARY KEY(id)

---

### 5.3 gender

V0.1 gender 为必填。

数据库存储值应该使用稳定枚举值，而不是直接把 UI 中文文本写入数据库。

例如：

MALE  
FEMALE

具体枚举名称以后在代码阶段统一。

---

### 5.4 living_status

建议存储：

ALIVE  
DECEASED

不要直接保存：

在世  
已故

中文属于展示层。

---

### 5.5 is_self

规则：

整个数据库最多只能存在一个：

is_self = true

这是业务层和数据库层都需要保证的约束。

至少必须通过 Repository / Transaction 保证：

COUNT(is_self = true) <= 1

建议方案：

Repository / Transaction 保证为主，同时可以评估使用 SQLite partial unique index 作为数据库层兜底。

V0.1 不强制锁死具体实现方式。

---

### 5.6 索引

建议至少建立：

INDEX(name)  
INDEX(is_self)

注意：

姓名不能 UNIQUE。

现实中允许存在同名人物。

---

## 6. phone_numbers 表

表名：

phone_numbers

作用：

保存人物的多个电话号码。

### 6.1 字段

| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| id | TEXT | 否 | PhoneNumber ID |
| person_id | TEXT | 否 | 所属 Person |
| number | TEXT | 否 | 电话号码 |
| label | TEXT | 否 | MOBILE / WORK / HOME / BACKUP / OTHER |
| status | TEXT | 否 | ACTIVE / INACTIVE |
| is_primary | INTEGER / BOOLEAN | 否 | 是否为主号码 |
| note | TEXT | 是 | 号码备注 |
| created_at | INTEGER | 否 | 创建时间 |
| updated_at | INTEGER | 否 | 更新时间 |

---

### 6.2 主键

PRIMARY KEY(id)

---

### 6.3 外键

person_id  
→ persons.id

删除 Person 时：

ON DELETE CASCADE

即：

删除人物后，其手机号一起删除。

---

### 6.4 手机号不能全局唯一

禁止：

UNIQUE(number)

原因：

同一个手机号允许出现在不同人物名下。

例如：

- 老人夫妻共用手机号
- 儿童联系方式填写父母手机号
- 家庭公共号码

---

### 6.5 是否允许同一人物重复录入同一号码

V0.1 规定（与第 22 节数据完整性原则一致）：

同一个人物下，不重复保存完全相同的号码。

可以建立组合唯一约束：

UNIQUE(person_id, number)

这样：

同一个号码可以属于不同人物，  
但不能在同一个人物资料里重复添加两次。

---

### 6.6 主号码规则

同一个 Person：

最多一个 is_primary = true

并且：

is_primary = true

时必须满足：

status = ACTIVE

V0.1 不要求每个人一定存在主号码。

允许：

0 个主号码

---

### 6.7 主号码唯一实现原则

SQLite 普通 UNIQUE 无法简单表达：

“同一个 person_id 只有 is_primary=true 时才唯一”。

因此 V0.1 可以采用：

业务事务约束

设置某号码为主号码时：

1. 开启事务
2. 将该人物其他号码全部设为 is_primary = false
3. 检查目标号码为 ACTIVE
4. 将目标号码设置为 is_primary = true
5. 提交事务

后续也可以评估 SQLite partial unique index。

但 V0.1 不要求提前复杂化。

---

### 6.8 主号码停用

如果：

is_primary = true

的号码被改为：

INACTIVE

则必须同时：

is_primary = false

然后：

- 如果存在其他 ACTIVE 号码，提示用户可以选择新主号码
- 如果不存在其他 ACTIVE 号码，则进入无主号码状态

---

### 6.9 默认联系号码

查询默认联系号码时：

优先：

ACTIVE + is_primary = true

如果没有主号码：

取：

第一个 ACTIVE 号码

但这个号码：

不能因为被展示就自动变成主号码。

---

### 6.10 索引

建议：

INDEX(person_id)  
INDEX(number)  
INDEX(person_id, status)

number 建普通索引即可，不建立 UNIQUE。

---

## 7. relationships 表

表名：

relationships

作用：

保存人物之间的基础关系。

### 7.1 字段

| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| id | TEXT | 否 | Relationship ID |
| source_person_id | TEXT | 否 | 关系起点 |
| target_person_id | TEXT | 否 | 关系终点 |
| type | TEXT | 否 | PARENT / SPOUSE / SIBLING / COUSIN（CHILD 不物理存储，见 7.10、7.12；COUSIN 见 7.18） |
| relative_age_order | TEXT | 是 | OLDER / YOUNGER / SAME，NULL 表示未记录或无法确定长幼；仅 SIBLING / COUSIN 有意义 |
| created_at | INTEGER | 否 | 创建时间 |
| updated_at | INTEGER | 否 | 更新时间 |

---

### 7.2 主键

PRIMARY KEY(id)

---

### 7.3 外键

source_person_id  
→ persons.id

以及：

target_person_id  
→ persons.id

两者都：

ON DELETE CASCADE

因此删除人物时：

所有与该人物关联的 Relationship 都删除。

---

### 7.4 type

领域层 V0.1 基础关系类型为：

PARENT  
CHILD  
SPOUSE  
SIBLING

数据库 type 列 V0.1 只允许物理存储：

PARENT  
SPOUSE  
SIBLING

后续版本增加（仍属基础关系类型）：

COUSIN —— 申报堂表亲记录（见 7.18，不参与引擎路径遍历）

CHILD 不作为物理存储值，统一以反向 PARENT 存储（见 7.10、7.12）。

禁止把以下称谓保存成 type：

FATHER  
MOTHER  
GRANDFATHER  
GRANDMOTHER  
UNCLE  
AUNT  
岳父  
岳母  
伯父  
叔叔

这些属于推导结果。

---

### 7.5 source / target 语义

必须严格遵守 DOMAIN_MODEL：

A PARENT B

表示：

A 是 B 的父母之一

即：

source = A  
target = B  
type = PARENT

再例如：

A CHILD B

表示：

A 是 B 的子女之一

该语义在数据库中统一存储为 B PARENT A（见 7.10）。

---

### 7.6 relative_age_order

仅在：

type = SIBLING

或：

type = COUSIN

时有意义。

值（非 NULL 时）：

OLDER  
YOUNGER  
SAME

NULL 表示：

未记录或无法确定长幼。

语义：

source_person  
相对于  
target_person

例如：

A SIBLING B  
relative_age_order = OLDER

表示：

A 比 B 年长

---

### 7.7 非 SIBLING / COUSIN 关系

如果：

type != SIBLING

且：

type != COUSIN

则：

relative_age_order

必须为 NULL。

业务层必须保证。

数据库层可以暂不建立复杂 CHECK 约束。

---

### 7.8 对称关系

SPOUSE

如果：

A SPOUSE B

则从业务语义上：

B SPOUSE A

也成立。

SIBLING

如果：

A SIBLING B

则：

B SIBLING A

也成立。

COUSIN

如果：

A COUSIN B

则：

B COUSIN A

也成立（“堂/表”属同一申报关系，不区分方向）。

---

### 7.9 对称关系的存储策略

V0.1 规定（与第 22 节一致）：

只保存一条事实关系记录。

不要为了方便查询同时保存：

A SPOUSE B

和：

B SPOUSE A

两条重复数据。

业务层查询时将：

- source
- target

两边都考虑进去。

这样可以减少数据冲突。

---

### 7.10 PARENT / CHILD 存储策略

PARENT 和 CHILD 是同一现实关系的两个观察方向。

V0.1 确定一个“规范存储方向”：

父母子女事实统一保存为 PARENT。

即只保存：

父母 PARENT 子女

而不再额外保存：

子女 CHILD 父母

CHILD 可以作为：

- Domain 层反向视图
- Kinship Engine 路径语义

而不是必须物理保存一条 CHILD 数据。

---

### 7.11 为什么规范化存储

如果同时保存：

A PARENT B  
B CHILD A

则很容易出现：

其中一条删除、另一条还存在。

因此规范存储能够降低数据不一致风险。

---

### 7.12 CHILD 的定位

虽然 DOMAIN_MODEL 中定义了：

CHILD

基础关系语义，

但数据库层 V0.1 规范化为：

只物理保存 PARENT

查询和领域层再提供 CHILD 反向关系。

需要明确：

**数据库规范存储方向和领域遍历方向是两回事。**

- 物理上只保存 PARENT 一条事实
- 写入时：以 CHILD 语义输入的关系（例如“X 是 Y 的子女”），由 Repository / Domain 层转换为 Y PARENT X 后再保存
- 领域层遍历时同一条记录双向可用：从 target 看 source 是 PARENT 关系，从 source 看 target 是 CHILD 语义

因此：

SELF → CHILD 等路径仍然可以表达，不会因为数据库只保存 PARENT 而失去 CHILD 查询能力。

这一点属于：

数据库存储优化，不改变领域模型。

---

### 7.13 SIBLING 规范存储

SIBLING 是对称关系。

V0.1 只保存一条。

例如：

A SIBLING B

为防止重复：

B SIBLING A

不应再次创建。

可以在业务层生成规范顺序。

例如：

按 UUID 字典序确定：

min(personId)  
max(personId)

再保存。

规范化时必须同步处理 relative_age_order。

例如原始输入：

B SIBLING A  
relative_age_order = YOUNGER

如果规范顺序要求保存为：

A SIBLING B

则必须同时把 relative_age_order 转换为：

OLDER

SAME 在两个方向含义相同，不需要转换；NULL 在两个方向同样保持 NULL。

该转换由 Repository / Domain 层在写入时完成。

不能只在数据库层排序 source / target，而忘记翻转长幼语义。

---

### 7.14 SPOUSE 规范存储

SPOUSE 同样只保存一条。

避免：

A SPOUSE B  
B SPOUSE A

同时出现。

也可以采用与 SIBLING 相同的规范顺序（例如按 personId 排序）用于去重。

SPOUSE 没有方向性字段，规范顺序不影响任何语义。

---

### 7.15 relationship 唯一性

建议避免完全重复关系。

概念唯一组合：

(source_person_id, target_person_id, type)

数据库层：

UNIQUE(source_person_id, target_person_id, type) 可以阻止完全相同的重复记录。

但对于 SIBLING / SPOUSE：

A-B 与 B-A 是同一条关系，普通 UNIQUE 无法识别。

因此对称关系去重不能只依赖数据库 UNIQUE。

职责划分：

- 数据库 UNIQUE 负责完全重复记录
- 规范顺序生成与反向重复检查由 Repository / Transaction 在写入前完成

---

### 7.16 禁止自关系

禁止：

source_person_id = target_person_id

至少针对：

- PARENT
- CHILD
- SPOUSE
- SIBLING

都不允许。

可以考虑数据库 CHECK：

source_person_id != target_person_id

---

### 7.17 索引

建议：

INDEX(target_person_id)  
INDEX(type)  
INDEX(source_person_id, target_person_id)

说明：

- 组合索引 (source_person_id, target_person_id) 的最左前缀已经可以覆盖单独按 source_person_id 的查询，不再单独建立 INDEX(source_person_id)
- INDEX(target_person_id) 单独需要，因为对称关系查询会按 target 反查
- type 基数低（仅 4 种值），索引收益有限，V0.1 保留用于关系类型筛选即可

---

### 7.18 COUSIN（申报堂表亲）

COUSIN 记录“两人是堂/表亲”这一用户申报事实，
以及所申报的长幼（relative_age_order，可空）。

用途：

- 录入“我是他的哥哥 / 弟弟（堂/表）”时，除挂接中间人父母边外，
  在两人之间写一条 COUSIN 记录保留申报的长幼；
- 亲属引擎堂表亲长幼先读该记录，再比双方完整生日
  （KINSHIP_ENGINE 37.1、40）；
- 两人间无其他路径时，由它兜底给“堂表哥 / 堂表弟 / 堂表姐妹”
  等通用称谓，并使对方在关系图中与我同代（不计入未关联）；
- 合并 / 分享导入时作为结构认人线索之一参与候选匹配
  （与 SPOUSE / SIBLING 同列）。

限制：

- COUSIN 不含中间链条信息，**不参与引擎的普通路径遍历**，
  不用于区分堂 / 表（堂表仍由真实 PARENT → SIBLING → CHILD 判定）。
- 与 SIBLING 同为对称关系：规范存储、无序对去重、端点交换时翻转
  relative_age_order 均与 SIBLING 一致（7.9、7.13、7.15、7.16）。

---

## 8. important_dates 表

表名：

important_dates

作用：

保存人物相关的重要日期。

已实现的类型：

- BIRTHDAY_SOLAR / BIRTHDAY_LUNAR — 生日（V0.1 起为单一 BIRTHDAY；
  第 8 版起按历法拆为两个类型，每人每种历法最多一条，见 25.7）。
- WEDDING_ANNIVERSARY — 结婚纪念日（V0.8）；LICENSE_ANNIVERSARY —
  领证纪念日、DATING_ANNIVERSARY — 恋爱纪念日（V1.x）。三种统称
  夫妻共享纪念日（`ImportantDateType.isCoupleShared`）：一对夫妻每种
  类型只存一条，挂在其中一人名下；两人详情页都显示。设置时该人
  必须有 SPOUSE 关系；配偶名下已有同类型记录时改那一条；清除时
  双方名下的同类型记录一并删掉。删除夫妻关系后记录保留在原人名下。
- DEATH_ANNIVERSARY — 忌日（V0.8）。只能设在已故人物上；
  有去世年份才能算“享年 / N 周年”。

UNIQUE(person_id, type)：每人每种日期最多一条。
提醒偏好复用 reminder_preferences / reminder_offsets（按 important_date_id
关联）；默认提前天数：生日 {0,1}、纪念日 {1,0}、忌日 {3,0}。

### 8.1 字段

| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| id | TEXT | 否 | ImportantDate ID |
| person_id | TEXT | 否 | 所属人物 |
| type | TEXT | 否 | BIRTHDAY_SOLAR / BIRTHDAY_LUNAR / WEDDING_ANNIVERSARY / LICENSE_ANNIVERSARY / DATING_ANNIVERSARY / DEATH_ANNIVERSARY（第 8 版前生日为 BIRTHDAY） |
| year | INTEGER | 是 | 年 |
| month | INTEGER | 否 | 月 |
| day | INTEGER | 否 | 日 |
| calendar_type | TEXT | 否 | SOLAR / LUNAR；生日行必须与 type 槽位一致 |
| note | TEXT | 是 | 备注 |
| created_at | INTEGER | 否 | 创建时间 |
| updated_at | INTEGER | 否 | 更新时间 |

---

### 8.2 为什么生日拆 year / month / day

生日并不一定知道完整年份。

现实中可能：

知道：

3 月 15 日

但不知道准确出生年份。

因此数据库设计应允许：

year = NULL

同时：

month/day

仍然可用来进行每年生日提醒。

注意：

- year 为空仍然可以进行每年的生日提醒
- year 为空时不能用于精确年龄计算或精确长幼比较
- 两个人生日都只有 month/day 而没有 year 时，不能据此判断谁年长；此时长幼判断应依赖 SIBLING 的 relative_age_order，或按 KINSHIP_ENGINE 规则降级

---

### 8.3 month/day

V0.1：

month  
day

必填。

范围：

month: 1..12

day 的合法性由业务层校验。

---

### 8.4 calendar_type

V0.1：

SOLAR

未来：

LUNAR

V0.1 不实现农历换算。

---

### 8.5 每人每种历法最多一个生日

第 8 版起生日按历法拆为 BIRTHDAY_SOLAR / BIRTHDAY_LUNAR 两个类型，
同一个 Person 每种历法最多一条，合计最多两条生日：

UNIQUE(person_id, type)

两份生日分别编辑、删除，各自挂独立的提醒偏好与偏移。
农历行的 is_leap_month 标记闰月。

这是当前 schema 假设：

未来如果某些 ImportantDate 类型允许一人多条同类型记录，需要通过 Migration 调整唯一策略。

---

### 8.6 外键

person_id  
→ persons.id  
ON DELETE CASCADE

---

### 8.7 索引

建议：

INDEX(person_id)  
INDEX(month, day)

month, day 可以用于查找近期生日。

说明：

V0.1 中 type 只有 BIRTHDAY 一个值，INDEX(type) 基本没有筛选收益，暂不单独建立；未来类型增多时再评估。

---

## 9. reminder_preferences 表

表名：

reminder_preferences

作用：

保存某个 ImportantDate 的提醒配置。

### 9.1 字段

| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| id | TEXT | 否 | ReminderPreference ID |
| important_date_id | TEXT | 否 | 对应 ImportantDate |
| enabled | INTEGER / BOOLEAN | 否 | 是否启用 |
| created_at | INTEGER | 否 | 创建时间 |
| updated_at | INTEGER | 否 | 更新时间 |

提醒 offset 不建议直接塞成单个整数，因为：

V0.1 默认就需要：

提前 1 天  
生日当天

多个提醒点。

---

## 10. reminder_offsets 表

为了支持一个生日拥有多个提醒时间点，建议增加：

reminder_offsets

虽然 DOMAIN_MODEL 将其表达为：

ReminderPreference.reminderOffsets

数据库层可以规范化拆表。

这不改变领域模型。

### 10.1 字段

| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| id | TEXT | 否 | ID |
| reminder_preference_id | TEXT | 否 | ReminderPreference |
| days_before | INTEGER | 否 | 提前多少天 |

例如：

生日当天：

days_before = 0

提前一天：

days_before = 1

---

### 10.2 V0.1 默认数据

默认：

0  
1

即：

- 当天
- 提前 1 天

---

### 10.3 外键

reminder_preference_id  
→ reminder_preferences.id  
ON DELETE CASCADE

---

### 10.4 唯一约束

同一 ReminderPreference 下：

days_before

不能重复。

建议：

UNIQUE(reminder_preference_id, days_before)

---

## 11. ReminderPreference 唯一性

一个 ImportantDate 最多拥有一个 ReminderPreference。

因此：

UNIQUE(important_date_id)

### 11.1 ReminderPreference 是否必须存在

不必须。

允许：

ImportantDate

存在，

但：

ReminderPreference

不存在。

语义：

用户记录了生日，但尚未启用或配置提醒。

---

### 11.2 enabled=false

如果 ReminderPreference 存在：

enabled = false

表示：

用户明确关闭了该日期提醒。

---

## 12. 默认生日提醒创建策略

创建生日时：

产品默认规则为：

- 当天提醒
- 提前 1 天提醒

数据库层不应该自行偷偷创建业务数据。

建议由 Domain / Repository 层的事务负责：

1. 创建 ImportantDate(BIRTHDAY)
2. 根据产品默认规则创建 ReminderPreference
3. 创建两个 reminder_offsets：
   - 0
   - 1

这样默认行为显式可测试。

双生日补充规则（第 8 版起）：

- 给已有生日的人补第二份生日时，新偏好默认 enabled = false，
  原生日的提醒配置原样保留（不替用户额外开提醒）。
- 两个历法槽位的提醒开关与提前天数完全独立。

需要区分“默认产品行为”和“数据库允许状态”：

- 数据库允许 ImportantDate 存在但没有 ReminderPreference（0..1）
- 产品上创建生日时默认创建 ReminderPreference 及 offsets 0、1

两者不矛盾：

默认创建是产品行为，0..1 是领域模型允许的范围。

---

## 13. 删除链

删除 Person：

```text
Person
│
├── PhoneNumber
├── ImportantDate
│    └── ReminderPreference
│         └── ReminderOffset
│
└── Relationship
```

全部清理。

### 13.1 外键级联建议

推荐：

persons  
↓ CASCADE  
phone_numbers

persons  
↓ CASCADE  
important_dates

important_dates  
↓ CASCADE  
reminder_preferences

reminder_preferences  
↓ CASCADE  
reminder_offsets

relationships 的：

source_person_id  
target_person_id

任一 Person 被删除时：

对应关系删除。

---

### 13.2 头像文件清理

数据库只能删除 persons 表中的 avatar_path 记录。

Room 无法删除文件系统中的头像图片。

头像文件清理由 Repository / 文件管理层负责，在删除人物时一并处理。

---

## 14. 时间字段

V0.1：

created_at  
updated_at

建议统一使用：

Unix Epoch Milliseconds

数据库类型：

INTEGER

避免数据库层保存格式化中文日期字符串。

---

## 15. 枚举存储

以下字段建议统一使用稳定英文值：

- gender
- living_status
- phone label
- phone status
- relationship type
- relative_age_order
- important date type
- calendar type

例如：

MALE  
FEMALE

ALIVE  
DECEASED

ACTIVE  
INACTIVE

PARENT  
SPOUSE  
SIBLING

OLDER  
YOUNGER  
SAME

BIRTHDAY

SOLAR

---

## 16. 不直接保存推导称谓

数据库禁止把：

爷爷  
奶奶  
伯父  
叔叔  
岳父

当成 Relationship.type 保存。

这些由：

Kinship Engine

动态推导。

### 16.1 structuralDescription

例如：

父亲的哥哥

也不要求永久保存。

可以实时计算。

---

### 16.2 standardTitle

例如：

伯父

同样默认实时推导。

---

### 16.3 confidence

例如：

EXACT  
PARTIAL

属于一次关系推导的结果。

V0.1 不要求保存进数据库。

---

### 16.4 missingInformation

例如：

SIBLING_AGE_ORDER

同样属于推导结果。

不永久存储。

---

## 17. SELF 不存数据库

SELF 是：

关系计算路径中的逻辑符号。

不能创建：

self 表

也不能创建：

relationship.type = SELF

当前默认观察者通过：

persons.is_self = true

获得。

---

## 18. relativeAgeOrder 数据一致性

例如：

数据库保存：

A SIBLING B  
relative_age_order = OLDER

则语义为：

A 比 B 年长

因为 V0.1 只保存一条 SIBLING 关系，

反向查看：

B 相对于 A

时，领域层自动转换为：

YOUNGER

数据库不需要再保存第二条关系。

与 7.13 对应：

写入时的规范化排序和读取时的反向解释，都必须配套 relative_age_order 的方向转换。

---

## 19. birthday 与 relativeAgeOrder

Kinship Engine 的长幼判断优先级：

1. 双方生日完整且可以比较
2. SIBLING.relativeAgeOrder
3. 无法判断，返回 PARTIAL

数据库只负责提供：

- ImportantDate(BIRTHDAY)
- relative_age_order

不负责做亲属称谓推导。

注意：

生日 year 为空的记录只能用于按月日提醒，不能作为完整生日比较依据（详见 8.2）。

---

## 20. 查询场景

V0.1 数据库至少需要支持以下高频查询。

### 20.1 获取“我”

查询：

is_self = true

---

### 20.2 获取人物详情

需要：

Person  
+  
PhoneNumbers  
+  
Birthday  
+  
ReminderPreference

---

### 20.3 获取人物有效号码

条件：

person_id = ?  
AND status = ACTIVE

排序：

is_primary DESC  
created_at ASC

---

### 20.4 获取某人的全部关系

需要查：

source_person_id = ?  
OR  
target_person_id = ?

---

### 20.5 获取家庭图

需要：

- persons
- relationships

然后由 Domain 层构造图结构。

---

### 20.6 查询近期生日

根据：

month  
day

查找近期生日人物。

具体跨年算法放在 Domain / Reminder 层。

---

## 21. Transaction 场景

以下操作应使用数据库事务。

### 21.1 创建人物及手机号

如果初始化人物时同时录入手机号：

创建 Person  
+  
创建 PhoneNumber

应保持一致。

---

### 21.2 设置主号码

必须事务：

1. 验证目标号码属于该人物
2. 验证目标号码 ACTIVE
3. 清除该人物现有主号码
4. 设置目标号码为主号码

---

### 21.3 停用主号码

事务：

1. 将 status 设为 INACTIVE
2. 将 is_primary 设为 false

---

### 21.4 创建生日及默认提醒

事务：

ImportantDate(BIRTHDAY)  
+  
ReminderPreference  
+  
ReminderOffset(0)  
+  
ReminderOffset(1)

---

### 21.5 创建 SIBLING

创建前检查：

- 两个人都存在
- 不是同一人物
- 没有反向重复关系

如果采用规范顺序存储，还需在同一事务中：

- 按规范顺序确定 source / target
- 如发生了方向交换，同步转换 relative_age_order（OLDER ↔ YOUNGER）

---

## 22. 数据完整性原则

数据库和 Repository 必须共同保证：

- Person.id 唯一
- 最多一个 isSelf
- Person.name 必填
- Person.gender 必填
- 手机号不能全局唯一
- 同人物不能重复相同手机号
- 每人物最多一个主号码
- 主号码必须 ACTIVE
- ImportantDate(BIRTHDAY) 每人最多一个
- ReminderPreference 每 ImportantDate 最多一个
- Relationship 不允许自关系
- SPOUSE / SIBLING 不允许正反重复
- relativeAgeOrder 只用于 SIBLING
- SIBLING 规范化存储方向改变时，relativeAgeOrder 同步转换

---

## 23. 不使用姓名建立关系

Relationship 必须使用：

personId

不能使用：

杨建国  
王丽

等姓名作为关系连接键。

原因：

- 姓名可重复
- 姓名可修改

---

## 24. 不使用手机号识别人

手机号不能作为人物唯一身份。

原因：

- 手机号可能停用或更换
- 同一手机号允许属于不同人物
- 人物身份必须使用稳定 ID（personId）识别

---

## 25. V0.2 结构变更

### 25.1 第 2 版：农历生日

important_dates 新增 `is_leap_month INTEGER NOT NULL DEFAULT 0`；calendar_type 允许 SOLAR、LUNAR。详见 DEVELOPMENT_PLAN 6.2。

### 25.2 第 3 版：custom_titles 表（自定义称呼）

用户可以为某位家人设置自己家里的叫法（例如把“伯父”叫作“大伯”，把“姥爷”叫作“外公”）。

自定义称呼属于“观察者—目标”这一对人物，而不是某条关系；它只是显示用的称呼，**不改变**称谓推导引擎计算出的结构关系和标准称谓。V0.2 中观察者始终是“我”。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | TEXT 主键 | UUID |
| observer_person_id | TEXT NOT NULL | 观察者（V0.2 始终为“我”），外键 persons.id，ON DELETE CASCADE |
| target_person_id | TEXT NOT NULL | 被称呼的人，外键 persons.id，ON DELETE CASCADE |
| title | TEXT NOT NULL | 自定义称呼，去除首尾空白后 1–12 个字符 |
| created_at / updated_at | INTEGER NOT NULL | 毫秒时间戳 |

约束与索引：

- `UNIQUE(observer_person_id, target_person_id)`：同一对人物最多一个自定义称呼。
- 为 target_person_id 建索引。
- observer 与 target 不能是同一人（由 Repository 校验）。

迁移 Migration(2, 3)：

```sql
CREATE TABLE IF NOT EXISTS custom_titles (
  id TEXT NOT NULL PRIMARY KEY,
  observer_person_id TEXT NOT NULL,
  target_person_id TEXT NOT NULL,
  title TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY(observer_person_id) REFERENCES persons(id) ON DELETE CASCADE,
  FOREIGN KEY(target_person_id) REFERENCES persons(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS index_custom_titles_observer_person_id_target_person_id
  ON custom_titles(observer_person_id, target_person_id);
CREATE INDEX IF NOT EXISTS index_custom_titles_target_person_id
  ON custom_titles(target_person_id);
```

实际 SQL 以 Room 导出的 3.json 为准，迁移测试负责校验两者一致。

显示优先级：自定义称呼 > 标准称谓 > 结构描述。凡是显示称谓的地方（首页、详情、关系图、搜索、提醒页、通知、守护联系人列表），都按这个优先级取值；详情页在设置了自定义称呼时，另外显示标准称谓或结构描述，方便对照。

备份：backup.json 的 schemaVersion 升为 3，新增 customTitles 数组。导入时接受 schemaVersion 2（视为没有自定义称呼）和 3。

### 25.3 第 4 版：family_tasks / family_task_offsets 表（家庭事项）

家庭事项（缴费、医保、养老金认证、证件到期等，FAMILY_AFFAIRS 5.5）：

```sql
CREATE TABLE IF NOT EXISTS family_tasks (
  id TEXT NOT NULL PRIMARY KEY,
  person_id TEXT,                       -- 可空：NULL 表示全家的事
  kind TEXT NOT NULL,                   -- INSURANCE_PREMIUM/MEDICAL_INSURANCE/PENSION_CERT/ID_EXPIRY/CHECKUP/CUSTOM
  title TEXT NOT NULL,
  anchor_epoch_day INTEGER NOT NULL,    -- 首次（第 0 期）日期
  repeat TEXT NOT NULL,                 -- NONE/MONTHLY/YEARLY
  period_index INTEGER NOT NULL,        -- 已推进到第几期；当前期 = anchor + periodIndex × repeat
  last_done_epoch_day INTEGER,          -- 上次办理日期
  done INTEGER NOT NULL,                -- 仅对 NONE：办完后归档
  amount_fen INTEGER,                   -- 金额（分），可空
  note TEXT,
  enabled INTEGER NOT NULL,             -- 提醒开关
  policy_id TEXT,                       -- 预留：保单联动（V0.5 第 4 批）
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY(person_id) REFERENCES persons(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_family_tasks_person_id ON family_tasks(person_id);
CREATE INDEX IF NOT EXISTS index_family_tasks_policy_id ON family_tasks(policy_id);

CREATE TABLE IF NOT EXISTS family_task_offsets (
  id TEXT NOT NULL PRIMARY KEY,
  task_id TEXT NOT NULL,
  days_before INTEGER NOT NULL,
  FOREIGN KEY(task_id) REFERENCES family_tasks(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS index_family_task_offsets_task_id_days_before
  ON family_task_offsets(task_id, days_before);
```

- 提前天数与 reminder_offsets 同样规范化存子表，可取值 {0,1,3,7,15,30,60}。
- policy_id 只建索引不建外键，等保单表落地后再关联。
- 删除人物级联删除其事项；清空数据一并清两表。
- 备份 schemaVersion 升为 4，新增 familyTasks 数组与 settings.taskRemindersEnabled；v2/v3 备份恢复时事项为空、开关保留当前值。

实际 SQL 以 Room 导出的 4.json 为准，迁移测试负责校验两者一致。

### 25.4 第 5 版：gift_events / gift_records 表（人情往来）

一本账（FAMILY_AFFAIRS 6）：“我家”（我和配偶）与其他人家之间的礼尚往来。

```sql
CREATE TABLE IF NOT EXISTS gift_events (
  id TEXT NOT NULL PRIMARY KEY,
  occasion TEXT NOT NULL,                -- WEDDING/BIRTH/HOUSEWARMING/BIRTHDAY_FEAST/SCHOOL/FUNERAL/SICK_VISIT/SPRING_FESTIVAL/OTHER
  title TEXT NOT NULL,
  date_epoch_day INTEGER NOT NULL,       -- 礼簿日期
  note TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS index_gift_events_date_epoch_day ON gift_events(date_epoch_day);

CREATE TABLE IF NOT EXISTS gift_records (
  id TEXT NOT NULL PRIMARY KEY,
  event_id TEXT,                         -- 可空：挂礼簿（CASCADE 删除）
  direction TEXT NOT NULL,               -- GIVEN/RECEIVED；挂礼簿时恒为 RECEIVED
  person_id TEXT,                        -- 可空：对方不是家人；ON DELETE SET NULL——删人保留记录与名字快照
  counterpart_name TEXT NOT NULL,        -- 名字快照（选人时也落盘）
  amount_fen INTEGER,                    -- 金额（分），与 gift_desc 至少填一个
  gift_desc TEXT,                        -- 礼品描述（如“两瓶酒”）
  date_epoch_day INTEGER NOT NULL,
  occasion TEXT NOT NULL,
  note TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY(event_id) REFERENCES gift_events(id) ON DELETE CASCADE,
  FOREIGN KEY(person_id) REFERENCES persons(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS index_gift_records_person_id ON gift_records(person_id);
CREATE INDEX IF NOT EXISTS index_gift_records_event_id ON gift_records(event_id);
CREATE INDEX IF NOT EXISTS index_gift_records_date_epoch_day ON gift_records(date_epoch_day);
```

- 挂礼簿的记录由 Repository 强制方向为 RECEIVED、日期/事由取礼簿的。
- 人物删除时记录保留（SET NULL），名字快照 counterpart_name 继续显示。
- 备份 schemaVersion 升为 5，新增 giftEvents/giftRecords（日期用 ISO 字符串）与
  settings.giftModuleEnabled；v2–v4 备份恢复时两张表为空、开关保留当前值。

实际 SQL 以 Room 导出的 5.json 为准，迁移测试负责校验两者一致。

### 25.5 第 6 版：insurance_policies 表（保障一览）

只客观记录保单（FAMILY_AFFAIRS 7），不评价不推荐。

```sql
CREATE TABLE IF NOT EXISTS insurance_policies (
  id TEXT NOT NULL PRIMARY KEY,
  insured_person_id TEXT NOT NULL,   -- 被保险人；CASCADE
  holder_person_id TEXT,             -- 投保人；SET NULL
  beneficiary TEXT,
  insurer TEXT NOT NULL,             -- 保险公司（必填）
  product_name TEXT,
  category TEXT NOT NULL,            -- PolicyCategory 枚举名
  sum_insured_fen INTEGER,           -- 保额（分）
  premium_fen INTEGER,               -- 保费（分）
  pay_frequency TEXT NOT NULL,       -- YEARLY/MONTHLY/SINGLE
  pay_years INTEGER,
  start_epoch_day INTEGER,
  end_epoch_day INTEGER,             -- lifelong 时忽略
  lifelong INTEGER NOT NULL,
  policy_no TEXT, agent_name TEXT, agent_phone TEXT,
  insurer_phone TEXT, note TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX index_insurance_policies_insured_person_id ON insurance_policies(insured_person_id);
CREATE INDEX index_insurance_policies_holder_person_id ON insurance_policies(holder_person_id);
```

- “下次缴费日”不在保单表：缴费提醒是一条 family_tasks 记录（kind=INSURANCE_PREMIUM、
  policy_id=保单 id），policy_id 无外键，由仓库在同一事务里保证一致。
- 删除被保险人 → 保单 CASCADE、关联事项随 person_id CASCADE，不留孤儿。
- 备份 schemaVersion 6 新增 `policies`（日期 ISO）与
  settings.insuranceModuleEnabled；familyTasks.policyId 必须能解析到 policies。
- schemaVersion 保持兼容：v2–v5 备份恢复时保单为空、开关保留当前值。

实际 SQL 以 Room 导出的 6.json 为准。

### 25.6 第 7 版：contact_logs / contact_plans 表（联系提醒）

记录“常联系”功能（FAMILY_AFFAIRS）的联系日志与每人联系周期；均随人物
级联删除，属私密数据不进家人共享。备份 schemaVersion 8 起随备份导出。
实际 SQL 以 Room 导出的 7.json 为准。

### 25.7 第 8 版：生日按历法拆类型（双生日）

为支持“同一人同时记阳历和农历生日”，生日不再共用一个 `BIRTHDAY` 类型，
而是拆为 `BIRTHDAY_SOLAR` / `BIRTHDAY_LUNAR` 两个类型：

- `UNIQUE(person_id, type)` 不变，因此每人每种历法最多一条生日，
  天然得到“最多一个阳历 + 一个农历”，且两份生日可分别编辑、删除。
- 迁移只做两条 `UPDATE`（按原 `calendar_type` 改写 `type`），表结构与
  索引不变：行 id、提醒偏好与偏移全部原样保留，忌日/结婚纪念日约束
  不受影响。

```sql
UPDATE important_dates SET type = 'BIRTHDAY_LUNAR'
  WHERE type = 'BIRTHDAY' AND calendar_type = 'LUNAR';
UPDATE important_dates SET type = 'BIRTHDAY_SOLAR'
  WHERE type = 'BIRTHDAY';
```

- 新增的第二份生日默认不额外开启提醒（`enabled = false`），
  原生日的提醒配置原样保留。
- 备份 schemaVersion 升为 9：写出新类型名；解析时兼容旧备份中的
  `BIRTHDAY`（按 `calendarType` 归入新槽位），并校验生日 type 与历法
  一致、`(personId, type)` 不重复。
- 实际 schema 以 Room 导出的 8.json 为准。

### 25.8 第 10 版：person_alias 表（家人共享对应关系）

家人共享“合并到我的家庭”写入时保存的对应关系（FAMILY_AFFAIRS 16.5）：

```sql
CREATE TABLE person_alias (
  remote_person_id TEXT NOT NULL PRIMARY KEY,
  local_person_id  TEXT NOT NULL
    REFERENCES persons(id) ON DELETE CASCADE,
  created_at       INTEGER NOT NULL
);
CREATE INDEX index_person_alias_local_person_id
  ON person_alias (local_person_id);
```

- `remote_person_id` = 共享文件里（对方那台机器上）的人物 id，
  `local_person_id` = 本机对应的人物 id。下一次导入同源资料（经别的
  亲戚转手也一样）时直接认回，不再走“可能是同一人”候选。
- 只存本机：不进备份、不进共享文件；`clearTables`（恢复/清空数据）
  显式删除；本机人物被删时对应行外键级联作废。
- 与碰一碰的 `tap_person_map` 不同：那边是“已配对设备”的持久身份，
  这边只是导入辅助，不建立任何配对语义。

### 25.9 备份 schemaVersion 10：恋爱 / 领证纪念日

- `important_dates` 类型新增 `LICENSE_ANNIVERSARY`、`DATING_ANNIVERSARY`，
  表结构不变（类型按名字存储，`UNIQUE(person_id, type)` 天然生效），
  数据库无需迁移。
- 备份 schemaVersion 升为 10：写出新类型名；旧版本备份导入照常
  （旧文件没有这两种类型，恢复后对应行为空）。

（第 9 版为碰一碰的 `spouse_pairing` / `tap_person_map` / `tap_device`
三表，见 FAMILY_AFFAIRS 第 10 节与 Room 导出的 9.json。）
