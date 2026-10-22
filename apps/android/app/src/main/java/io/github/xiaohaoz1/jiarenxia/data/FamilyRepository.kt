package io.github.xiaohaoz1.jiarenxia.data

import android.net.Uri
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.data.collab.CollabApply
import kotlinx.coroutines.flow.Flow

/**
 * 家庭数据的唯一读写入口（DEVELOPMENT_PLAN 2.1）。
 * 所有跨表写操作在实现内以单个 Room 事务完成。
 */
interface FamilyRepository {

    /** 观察“我”（不存在时为 null）。 */
    fun observeSelf(): Flow<Person?>

    /** 观察家庭关系图：人物 + 关系 + 各人物生日。 */
    fun observeFamilyGraph(): Flow<FamilyGraph>

    /** 观察人物详情聚合；人物不存在时为 null。 */
    fun observePersonDetail(personId: String): Flow<PersonDetail?>

    /** 观察提醒页条目（不含已故人物）。 */
    fun observeBirthdayEntries(): Flow<List<BirthdayEntry>>

    /** personId → 默认拨出号码（主号码优先，否则最早的 ACTIVE）。 */
    fun observeDefaultNumbers(): Flow<Map<String, String>>

    /** 一次性获取提醒页条目。 */
    suspend fun getBirthdayEntries(): List<BirthdayEntry>

    /** 创建“我”。已有“我”时返回 [FamilyError.SelfAlreadyExists]。 */
    suspend fun createSelf(draft: PersonDraft): FamilyResult<String>

    /**
     * 创建新人物，含义为“新人物是 anchor 的 [role]”。
     * 人物与关系在同一事务中写入。
     */
    suspend fun createRelative(draft: PersonDraft, anchorId: String, role: RelationRole): FamilyResult<String>

    /**
     * 称谓录入（DEVELOPMENT_PLAN 2.14）：一次事务创建新人物与多条关系。
     * [specs] 中以 [io.github.xiaohaoz1.jiarenxia.domain.NEW_PERSON] 占位新人物；
     * 任一规格校验失败整体回滚（人物也不落库）。
     * [customTitle] 非空时随写观察者=“我”的自定义称呼（“只记称呼”场景 specs 为空）。
     */
    suspend fun createRelative(
        draft: PersonDraft,
        specs: List<io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec>,
        customTitle: String? = null
    ): FamilyResult<String>

    /**
     * 同一事务写入多条直接关系：每条走与 addRelationship 相同的校验，
     * 任一失败整体回滚。供称谓录入补建共同父母等多条关系使用。
     * [customTitleFor]/[customTitle] 非空时在同一事务写观察者=“我”的
     * 自定义称呼（“只记称呼”场景 specs 可为空）；称呼校验失败同样回滚关系。
     */
    suspend fun addRelationships(
        specs: List<io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec>,
        customTitleFor: String? = null,
        customTitle: String? = null
    ): FamilyResult<Unit>

    /** 更新人物及其手机号 / 生日（见 DEVELOPMENT_PLAN 2.5、2.8）。 */
    suspend fun updatePerson(personId: String, draft: PersonDraft): FamilyResult<Unit>

    /** 删除人物；关系、手机号、生日与提醒随外键级联删除，头像文件随后清理。 */
    suspend fun deletePerson(personId: String): FamilyResult<Unit>

    /** 添加关系，含义为“other 是 person 的 [role]”。 */
    suspend fun addRelationship(personId: String, otherId: String, role: RelationRole): FamilyResult<String>

    /** 删除一条直接关系。 */
    suspend fun removeRelationship(relationshipId: String): FamilyResult<Unit>

    /**
     * 设置 SIBLING 关系的长幼。order 表示 subject 相对另一端的长幼；
     * 存储时按 source 相对 target 的方向换算，null 表示清除。
     */
    suspend fun setSiblingAgeOrder(
        relationshipId: String,
        subjectId: String,
        order: RelativeAgeOrder?
    ): FamilyResult<Unit>

    /**
     * 设置/清除某人某历法的生日（value=null 删除该槽位，偏好随级联删除）。
     * value.calendar 必须与 [calendar] 一致；保留既有行的 id 与提醒配置。
     */
    suspend fun setBirthday(
        personId: String,
        calendar: io.github.xiaohaoz1.jiarenxia.core.model.CalendarType,
        value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?
    ): FamilyResult<Unit>

    /**
     * 提醒开关：type 精确指定日期槽位（生日用 BIRTHDAY_SOLAR / BIRTHDAY_LUNAR，
     * 纪念日和忌日也复用同一偏好表）。没有对应日期时返回 [FamilyError.NoBirthday]。
     */
    suspend fun setReminderEnabled(
        personId: String, enabled: Boolean,
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
    ): FamilyResult<Unit>

    /**
     * 设置指定日期的提醒偏移（当天/提前1/3/7天）。允许值 {0,1,3,7}，必须非空；
     * 偏好不存在时按 enabled=true 补建。整组替换，事务写入。
     */
    suspend fun setReminderOffsets(
        personId: String, offsets: Set<Int>,
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
    ): FamilyResult<Unit>

    /** 忌日：value=null 删除；仅已故者可设。 */
    suspend fun setDeathAnniversary(
        personId: String,
        value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?
    ): FamilyResult<Unit>

    /** 结婚纪念日：一对夫妻只存一条（配偶已有则改那一条）。 */
    suspend fun setWeddingAnniversary(
        personId: String,
        value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?
    ): FamilyResult<Unit>

    /**
     * 夫妻共享纪念日（结婚 / 领证 / 恋爱）：一对夫妻每种类型只存一条，
     * 挂在任一方名下都算——配偶已有就改那一条，否则记在本方名下；
     * value=null 把双方名下的同类型记录都清掉。
     * 默认实现把结婚类型转给 [setWeddingAnniversary]，其余返回
     * [FamilyError.InvalidDateType]（测试桩无需逐个补实现）。
     */
    suspend fun setCoupleAnniversary(
        personId: String,
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType,
        value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?
    ): FamilyResult<Unit> =
        if (type == io.github.xiaohaoz1.jiarenxia.core.model
                .ImportantDateType.WEDDING_ANNIVERSARY
        ) setWeddingAnniversary(personId, value)
        else FamilyResult.Failure(FamilyError.InvalidDateType)

    /**
     * 共同父母推荐：此人无配偶，且恰有一位“同为一个孩子的父母”的异性、
     * 对方也无配偶 → 返回对方；否则 null（多位候选时不推荐）。
     */
    suspend fun coParentCandidate(personId: String):
        io.github.xiaohaoz1.jiarenxia.core.model.Person?

    /**
     * 结为夫妻后的“共同子女”候选（a、b 两边都查）：一方已录子女中，
     * 已录父母没有另一方、也没有与另一方同性别父母的人——
     * 如“已经有父亲的孩子，不再问新丈夫”。返回子女人物、已记录方
     * 与提问用称谓（“女儿”/“儿子”），a 侧子女在前。
     */
    suspend fun commonChildCandidates(a: String, b: String): List<CommonChildCandidate>

    /**
     * 同一事务：先建两人 SPOUSE 关系（走 addRelationship 同一校验），
     * 再写结婚纪念日。用在“共同父母直接记为夫妻”的场景。
     */
    suspend fun linkSpouseAndSetWeddingAnniversary(
        personId: String, spouseId: String,
        value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
    ): FamilyResult<Unit>

    /** 全部重要日期条目（提醒时间线合并用）。 */
    suspend fun getDateEntries(): List<DateEntry>

    /** 观察全部重要日期条目（时间线/详情页）。 */
    fun observeDateEntries(): kotlinx.coroutines.flow.Flow<List<DateEntry>>

    /** 导入并设置头像（图片经降采样、裁剪、缩放到 512×512 JPEG）。 */
    suspend fun setAvatar(personId: String, source: Uri): FamilyResult<Unit>

    /** 移除头像。 */
    suspend fun removeAvatar(personId: String): FamilyResult<Unit>

    /** 清空全部数据：所有表 + 头像目录（DEVELOPMENT_PLAN 2.6）。 */
    /** 观察“我”的自定义称呼表（targetPersonId → 称呼）。 */
    fun observeCustomTitles(): Flow<Map<String, String>>

    /**
     * 设置/清除对 [targetId] 的自定义称呼（观察者为“我”）。
     * null/空白 → 删除；否则 trim 后 1–12 字符，目标必须存在且非“我”。
     */
    suspend fun setCustomTitle(targetId: String, title: String?): FamilyResult<Unit>

    /** 观察“仅本机保留”人物 id 集（协作改版：不进名册快照/确认版/默认关系图）。 */
    fun observeLocalOnlyIds(): Flow<Set<String>>

    /** 观察“仅本机保留”完整行（含保留原因备注；家庭页标记用）。 */
    fun observeLocalOnly(): Flow<List<io.github.xiaohaoz1.jiarenxia.data.db.FamilyLocalOnlyEntity>>

    /**
     * 本机名册的规范快照 + 内容哈希 + id 映射（协作改版）：
     * 排除“仅本机保留”人物；人物/条目 id 经协作映射换算到规范空间；
     * 头像按文件内容哈希。协作服务做三方比较、发布前打包都用它。
     */
    suspend fun canonicalRosterSnapshot(): io.github.xiaohaoz1.jiarenxia.data.collab.CollabRoster

    /**
     * 离线协作应用（FAMILY_AFFAIRS §5.4，唯一写入口）：
     * 单事务内——复核本机名册哈希（[CollabApply.expectedRosterHash]，防接收
     * 期间本机改动）、复核草稿 PENDING 状态、按序写入人物/条目/关系、
     * 被移除人物的私人外键复核（有引用则落“仅本机保留”而非物理删除）、
     * 写回协作状态/发布索引/草稿记录。
     * 任一步失败整体回滚。头像文件由调用方在事务外处理（先写新文件、
     * 提交后清旧文件）。
     */
    suspend fun applyFamilyRevision(apply: CollabApply): FamilyResult<Unit>

    suspend fun clearAllData()
}
