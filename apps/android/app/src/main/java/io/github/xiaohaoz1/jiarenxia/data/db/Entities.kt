package io.github.xiaohaoz1.jiarenxia.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder

/** persons 表（DATABASE_SCHEMA 第 5 节）。 */
@Entity(
    tableName = "persons",
    indices = [Index("name"), Index("is_self")]
)
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val gender: Gender,
    @ColumnInfo(name = "avatar_path") val avatarPath: String?,
    val note: String?,
    @ColumnInfo(name = "living_status") val livingStatus: LivingStatus,
    @ColumnInfo(name = "is_self") val isSelf: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** phone_numbers 表（DATABASE_SCHEMA 第 6 节）。person_id 的覆盖由 UNIQUE(person_id, number) 提供。 */
@Entity(
    tableName = "phone_numbers",
    foreignKeys = [ForeignKey(
        entity = PersonEntity::class,
        parentColumns = ["id"],
        childColumns = ["person_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["person_id", "number"], unique = true),
        Index("number"),
        Index("person_id", "status")
    ]
)
data class PhoneNumberEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "person_id") val personId: String,
    val number: String,
    val label: PhoneLabel,
    val status: PhoneStatus,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** relationships 表（DATABASE_SCHEMA 第 7 节）。 */
@Entity(
    tableName = "relationships",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_person_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_person_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["source_person_id", "target_person_id", "type"], unique = true),
        Index("target_person_id"),
        Index("type")
    ]
)
data class RelationshipEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_person_id") val sourcePersonId: String,
    @ColumnInfo(name = "target_person_id") val targetPersonId: String,
    val type: RelationshipType,
    @ColumnInfo(name = "relative_age_order") val relativeAgeOrder: RelativeAgeOrder?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** important_dates 表（DATABASE_SCHEMA 第 8 节）。year 可空，表示只知道月日。 */
@Entity(
    tableName = "important_dates",
    foreignKeys = [ForeignKey(
        entity = PersonEntity::class,
        parentColumns = ["id"],
        childColumns = ["person_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["person_id", "type"], unique = true),
        Index("month", "day")
    ]
)
data class ImportantDateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "person_id") val personId: String,
    val type: ImportantDateType,
    val year: Int?,
    val month: Int,
    val day: Int,
    @ColumnInfo(name = "calendar_type") val calendarType: CalendarType,
    @ColumnInfo(name = "is_leap_month", defaultValue = "0") val isLeapMonth: Boolean = false,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** reminder_preferences 表（DATABASE_SCHEMA 第 9 节）：一个 ImportantDate 最多一条。 */
@Entity(
    tableName = "reminder_preferences",
    foreignKeys = [ForeignKey(
        entity = ImportantDateEntity::class,
        parentColumns = ["id"],
        childColumns = ["important_date_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["important_date_id"], unique = true)]
)
data class ReminderPreferenceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "important_date_id") val importantDateId: String,
    val enabled: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** reminder_offsets 表（DATABASE_SCHEMA 第 10 节）：提醒提前天数集合的规范化存储。 */
@Entity(
    tableName = "reminder_offsets",
    foreignKeys = [ForeignKey(
        entity = ReminderPreferenceEntity::class,
        parentColumns = ["id"],
        childColumns = ["reminder_preference_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["reminder_preference_id", "days_before"], unique = true)]
)
data class ReminderOffsetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "reminder_preference_id") val reminderPreferenceId: String,
    @ColumnInfo(name = "days_before") val daysBefore: Int
)

/**
 * custom_titles 表（DATABASE_SCHEMA 25.2）：自定义称呼。
 * 属于“观察者—目标”这对人物，仅覆盖显示，不参与称谓推导。
 */
@Entity(
    tableName = "custom_titles",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["observer_person_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_person_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["observer_person_id", "target_person_id"], unique = true),
        Index("target_person_id")
    ]
)
data class CustomTitleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "observer_person_id") val observerPersonId: String,
    @ColumnInfo(name = "target_person_id") val targetPersonId: String,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/**
 * family_tasks 表（DATABASE_SCHEMA v4）：家庭事项。
 * person_id 可空（全家的事）。v13（A6）去掉 policy_id：原挂保单的
 * 缴费事项保留为普通事项，字段与周期不变。
 */
@Entity(
    tableName = "family_tasks",
    foreignKeys = [ForeignKey(
        entity = PersonEntity::class,
        parentColumns = ["id"],
        childColumns = ["person_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("person_id")]
)
data class FamilyTaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "person_id") val personId: String?,
    val kind: String,
    val title: String,
    /** 首次（第 0 期）日期，epochDay。 */
    @ColumnInfo(name = "anchor_epoch_day") val anchorEpochDay: Long,
    val repeat: String,
    /** 已办到第几期；当前期日期 = 首次日期 + periodIndex × 周期。 */
    @ColumnInfo(name = "period_index") val periodIndex: Int = 0,
    /** 上次办理日期，epochDay。 */
    @ColumnInfo(name = "last_done_epoch_day") val lastDoneEpochDay: Long?,
    /** 仅对不重复事项：已办完置 1。 */
    val done: Boolean = false,
    /** 金额（分），可空。 */
    @ColumnInfo(name = "amount_fen") val amountFen: Long?,
    val note: String?,
    /** 提醒开关。 */
    val enabled: Boolean = true,
    /** 一天内提醒时刻（分钟数）；null = 跟随家事提醒时刻设置。 */
    @ColumnInfo(name = "remind_minutes") val remindMinutes: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** family_task_offsets 表：事项提前提醒天数集合（同 reminder_offsets 规范化）。 */
@Entity(
    tableName = "family_task_offsets",
    foreignKeys = [ForeignKey(
        entity = FamilyTaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["task_id", "days_before"], unique = true)]
)
data class FamilyTaskOffsetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "days_before") val daysBefore: Int
)

/**
 * 事项 + 提前天数的一次性一致快照（配合 @Transaction 查询）：
 * 不会出现“新事项配旧 offsets”的中间态。
 */
data class FamilyTaskWithOffsets(
    @Embedded val task: FamilyTaskEntity,
    @Relation(parentColumn = "id", entityColumn = "task_id")
    val offsets: List<FamilyTaskOffsetEntity>
)

/**
 * contact_logs：联系记录（FAMILY_AFFAIRS 联系提醒）。method 存枚举名：
 * CALL/VISIT/MESSAGE/VIDEO/OTHER。
 */
@Entity(
    tableName = "contact_logs",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("person_id", "at_millis")]
)
data class ContactLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "person_id") val personId: String,
    /** 联系发生时间（毫秒）。 */
    @ColumnInfo(name = "at_millis") val atMillis: Long,
    val method: String,
    /** 备注（可空，≤100 字）。 */
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/**
 * contact_plans：联系周期计划（按人一份）。interval_days ∈ {7,14,30,60,90}。
 */
@Entity(
    tableName = "contact_plans",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ContactPlanEntity(
    @PrimaryKey @ColumnInfo(name = "person_id") val personId: String,
    @ColumnInfo(name = "interval_days") val intervalDays: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/**
 * person_alias（v10，FAMILY_AFFAIRS 16.5 第 1 条）：
 * 对方人物 id → 本机人物 id 的对应关系（家人共享合并时保存）。
 * 只存本机，不进备份、不进共享文件；本机人物删除时级联清除；
 * 恢复备份、清空数据时随 persons 一起清掉（clearTables 显式删除）。
 * id 是随机 UUID，不同来源不会冲突；同源资料经别的亲戚转手后仍能认回。
 */
@Entity(
    tableName = "person_alias",
    foreignKeys = [ForeignKey(
        entity = PersonEntity::class,
        parentColumns = ["id"],
        childColumns = ["local_person_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("local_person_id")]
)
data class PersonAliasEntity(
    /** 对方（文件里的）人物 id。 */
    @PrimaryKey @ColumnInfo(name = "remote_person_id") val remotePersonId: String,
    /** 本机对应的人物 id。 */
    @ColumnInfo(name = "local_person_id") val localPersonId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

// ==================== 离线协作（FAMILY_AFFAIRS §5，v12） ====================

/**
 * family_collaboration_state（单行，id 恒 1）：本机绑定的名册序列。
 *
 * 一个设备只绑定一个系列（一个汇总者）。存确认版基准快照与
 * 规范 id↔本机 id 映射；规范 id 来自确认版文件，本机人物 id 保持不变。
 * 映射/基准为 JSON 文本（FamilySnapshot.toJson / 简单 Map 序列化）。
 */
@Entity(tableName = "family_collaboration_state")
data class FamilyCollabStateEntity(
    @PrimaryKey val id: Int,
    /** 名册系列 id（首次发布生成）。 */
    @ColumnInfo(name = "series_id") val seriesId: String,
    /** "coordinator"（汇总者）/ "member"（成员）。 */
    val role: String,
    /** 汇总者人物的名册规范 id。 */
    @ColumnInfo(name = "coordinator_person_id") val coordinatorPersonId: String,
    /** 已接受/已发布的确认版号（成员=最近应用的；汇总者=最近发布的）。 */
    @ColumnInfo(name = "accepted_revision") val acceptedRevision: Int,
    /** 该确认版快照哈希。 */
    @ColumnInfo(name = "accepted_hash") val acceptedHash: String,
    /** 基准快照 JSON（FamilySnapshot.toJson）。 */
    @ColumnInfo(name = "base_snapshot_json") val baseSnapshotJson: String,
    /** 规范 id → 本机人物 id（JSON 对象）。 */
    @ColumnInfo(name = "id_map_json") val idMapJson: String,
    /** 曾明确移除的规范人物 id（JSON 数组；阻止旧包悄悄复活家人）。 */
    @ColumnInfo(name = "removed_ids_json") val removedIdsJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/**
 * family_publications：汇总者发布索引（每确认版一行）。
 * 只存索引不存完整旧基准——成员补充包自带基准快照。
 */
@Entity(tableName = "family_publications")
data class FamilyPublicationEntity(
    @PrimaryKey val revision: Int,
    /** 该版逻辑文件标识：同 packageId 的文件不允许内容不同。 */
    @ColumnInfo(name = "package_id") val packageId: String,
    @ColumnInfo(name = "snapshot_hash") val snapshotHash: String,
    @ColumnInfo(name = "parent_revision") val parentRevision: Int,
    @ColumnInfo(name = "parent_hash") val parentHash: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/**
 * family_receive_drafts：接收草稿与已处理记录。
 * PENDING 行最多一个（服务层保证）；处理完转 APPLIED 留存，
 * 供“同包不重复应用/同 id 不同内容拒绝”判定。
 */
@Entity(tableName = "family_receive_drafts")
data class FamilyReceiveDraftEntity(
    /** 包 id（协作元数据里的逻辑标识）。 */
    @PrimaryKey @ColumnInfo(name = "package_id") val packageId: String,
    /** 规范化内容哈希（同一内容重加密后仍判同包）。 */
    @ColumnInfo(name = "content_hash") val contentHash: String,
    /** confirmed / contribution。 */
    val mode: String,
    @ColumnInfo(name = "series_id") val seriesId: String,
    /** confirmed：目标版本号；contribution：0。 */
    val revision: Int,
    /** 计划时刻的本机名册快照哈希（应用时复核，防中途改动）。 */
    @ColumnInfo(name = "roster_hash") val rosterHash: String,
    /** contribution 的基准版本/哈希（confirmed 为 null）。 */
    @ColumnInfo(name = "base_revision") val baseRevision: Int?,
    @ColumnInfo(name = "base_hash") val baseHash: String?,
    /** 用户已做的身份/冲突选择（JSON 对象：itemKey → wire）。 */
    @ColumnInfo(name = "decisions_json") val decisionsJson: String,
    /** 加密文件副本路径（应用时再解密，不持久化密码）。 */
    @ColumnInfo(name = "file_path") val filePath: String,
    /** PENDING / APPLIED。 */
    val status: String,
    /** 计划与身份裁决结果 JSON（重开草稿不再整包重算）。 */
    @ColumnInfo(name = "plan_cache_json") val planCacheJson: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/**
 * family_local_only：“仅本机保留”的人物——有私人记录或用户选择保留、
 * 未进确认版。不进名册快照、不进确认版/补充包、不显示在默认关系图；
 * 家庭页单独标出；普通部分分享可手动勾选。
 */
@Entity(
    tableName = "family_local_only",
    foreignKeys = [ForeignKey(
        entity = PersonEntity::class,
        parentColumns = ["id"],
        childColumns = ["person_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class FamilyLocalOnlyEntity(
    @PrimaryKey @ColumnInfo(name = "person_id") val personId: String,
    /** 对应的名册规范 id（曾进过确认版时被移除的）；纯本机新增为 null。 */
    @ColumnInfo(name = "canonical_id") val canonicalId: String?,
    /** 保留原因快照（如“有保单记录”）。 */
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
