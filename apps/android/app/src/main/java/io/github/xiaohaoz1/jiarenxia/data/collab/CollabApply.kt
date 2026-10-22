package io.github.xiaohaoz1.jiarenxia.data.collab

import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.data.db.FamilyCollabStateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.FamilyLocalOnlyEntity
import io.github.xiaohaoz1.jiarenxia.data.db.FamilyPublicationEntity
import io.github.xiaohaoz1.jiarenxia.data.db.FamilyReceiveDraftEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PhoneNumberEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilySnapshot

/**
 * 本机名册的规范快照结果（协作改版）：
 * [snap] 名册规范 id 空间的内容；[hash] 其规范化内容哈希；
 * [canonicalToLocal] 规范 id → 本机人物 id（未映射人物的规范 id = 本机 id，
 * 不出现在映射里）；[localOnlyIds] 被排除出快照的“仅本机保留”本机 id。
 */
data class CollabRoster(
    val snap: FamilySnapshot.Snap,
    val hash: String,
    val canonicalToLocal: Map<String, String>,
    val localOnlyIds: Set<String>
)

/**
 * 协作应用写入集（FAMILY_AFFAIRS §5.4）：FamilyCollaborationService 把
 * 规范空间的决议计划翻译为本机 id 的具体行写入，交给
 * [io.github.xiaohaoz1.jiarenxia.data.FamilyRepository.applyFamilyRevision]
 * 在单个事务里执行。
 *
 * 电话/日期条目用“upsert”语义：按（人物，规范化号码）/（人物，类型）匹配
 * 本机已有行保留行 id；没有则按实体自带 id 插入。
 */
data class CollabApply(
    /** 计划时刻的本机名册规范快照哈希：事务内重算对比，防接收期间本机改动。 */
    val expectedRosterHash: String,
    /** 新增人物行（本机 id 由服务预先分配；is_self 恒 false）。 */
    val addPersons: List<PersonEntity> = emptyList(),
    /** 字段改写行（avatarPath 已带新值；is_self/createdAt 由服务从旧行带出）。 */
    val updatePersons: List<PersonEntity> = emptyList(),
    /** 物理删除的本机人物 id（事务内复核私人外键：有引用则转“仅本机保留”）。 */
    val removePersons: List<String> = emptyList(),
    /** 标为“仅本机保留”（含因私人外键转标记的）。 */
    val markLocalOnly: List<FamilyLocalOnlyEntity> = emptyList(),
    /** 解除“仅本机保留”（重新加入名册的本机 id）。 */
    val unmarkLocalOnly: List<String> = emptyList(),
    /** 电话 upsert（匹配已有行保 id，否则插入）。 */
    val upsertPhones: List<PhoneNumberEntity> = emptyList(),
    /** 电话删除：本机人物 id → 规范化号码集合。 */
    val deletePhones: List<Pair<String, String>> = emptyList(),
    /** 日期 upsert（按 person+type 匹配已有行保 id，否则插入）。 */
    val upsertDates: List<ImportantDateEntity> = emptyList(),
    /** 日期删除：本机人物 id → 日期类型集合。 */
    val deleteDates: List<Pair<String, ImportantDateType>> = emptyList(),
    /** 新关系行（已按 RelationGraph 规则校验；事务内再逐条复核）。 */
    val insertRels: List<RelationshipEntity> = emptyList(),
    /** 删除的关系行 id（本机行）。 */
    val deleteRelIds: List<String> = emptyList(),
    /** 首次绑定：此本机人物成为“我”（其余人物 is_self 置 false）。 */
    val setSelfId: String? = null,
    /** 协作状态整行写回（id 恒 1）。 */
    val stateWrite: FamilyCollabStateEntity? = null,
    /** 新发布索引行（汇总者发布路径）。 */
    val publication: FamilyPublicationEntity? = null,
    /** 接收草稿/已处理记录写回（PENDING → APPLIED 或新草稿）。 */
    val draftWrite: FamilyReceiveDraftEntity? = null,
    /** 应用前必须处于 PENDING 的草稿 packageId（同包重复应用防护）。 */
    val expectPendingPackage: String? = null
)
