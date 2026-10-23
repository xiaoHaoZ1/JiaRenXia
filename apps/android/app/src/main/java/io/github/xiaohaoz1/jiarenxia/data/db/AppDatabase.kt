package io.github.xiaohaoz1.jiarenxia.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 应用数据库（DATABASE_SCHEMA）。v1 六表；v2 +is_leap_month；v3 +custom_titles；
 * v4 +family_tasks / family_task_offsets（家庭事项，FAMILY_AFFAIRS 5.5）；
 * v5 +gift_events / gift_records；v6 +insurance_policies；
 * v7 +contact_logs / contact_plans（联系提醒）；
 * v8 生日拆历法类型 BIRTHDAY_SOLAR / BIRTHDAY_LUNAR（纯数据改写，表结构不变）；
 * v9 +spouse_pairing / tap_person_map / tap_device（碰一碰配对与设备偏好，
 *   设备本地状态：不进备份，persons 清空时配对与映射经外键级联自动失效）；
 * v10 +person_alias（家人共享合并的对方人物→本机人物对应关系，
 *   不进备份/共享，persons 级联失效）；
 * v11 +family_tasks.remind_minutes（事项单日提醒时刻，可空）；
 * v12 +family_collaboration_state / family_publications /
 *   family_receive_drafts / family_local_only（离线协作：名册绑定、
 *   发布索引、接收草稿/已处理记录、仅本机保留人物）。
 * v13 退出人情与保障（A6）：删 gift_events / gift_records /
 *   insurance_policies 表；family_tasks 去掉 policy_id 列与索引，
 *   其余字段（id、周期、期数、办理记录、提醒）原样保留。
 */
@Database(
    entities = [
        PersonEntity::class,
        PhoneNumberEntity::class,
        RelationshipEntity::class,
        ImportantDateEntity::class,
        ReminderPreferenceEntity::class,
        ReminderOffsetEntity::class,
        CustomTitleEntity::class,
        FamilyTaskEntity::class,
        FamilyTaskOffsetEntity::class,
        ContactLogEntity::class,
        ContactPlanEntity::class,
        SpousePairingEntity::class,
        TapPersonMapEntity::class,
        TapDeviceEntity::class,
        PersonAliasEntity::class,
        FamilyCollabStateEntity::class,
        FamilyPublicationEntity::class,
        FamilyReceiveDraftEntity::class,
        FamilyLocalOnlyEntity::class
    ],
    version = 15,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun phoneNumberDao(): PhoneNumberDao
    abstract fun relationshipDao(): RelationshipDao
    abstract fun importantDateDao(): ImportantDateDao
    abstract fun reminderPreferenceDao(): ReminderPreferenceDao
    abstract fun reminderOffsetDao(): ReminderOffsetDao
    abstract fun customTitleDao(): CustomTitleDao
    abstract fun familyTaskDao(): FamilyTaskDao
    abstract fun familyTaskOffsetDao(): FamilyTaskOffsetDao
    abstract fun contactLogDao(): ContactLogDao
    abstract fun contactPlanDao(): ContactPlanDao
    abstract fun tapPairingDao(): TapPairingDao
    abstract fun personAliasDao(): PersonAliasDao
    abstract fun familyCollabDao(): FamilyCollabDao
    abstract fun familyPublicationDao(): FamilyPublicationDao
    abstract fun familyReceiveDraftDao(): FamilyReceiveDraftDao
    abstract fun familyLocalOnlyDao(): FamilyLocalOnlyDao
}

/**
 * 按外键依赖顺序清空全部业务表（子表 → 父表）。
 * 备份恢复、共享导入、“清空所有数据”共用这份表清单；
 * 必须由调用方包在 withTransaction 中执行。
 * 碰一碰配对与来源映射随 persons 一起清（恢复的资料不算“已配对的
 * 另一台手机”）；设备级连接偏好 tap_device 保留——它是本机硬件
 * 相关设置，不属于家庭资料。
 */
internal suspend fun AppDatabase.clearTables() {
    customTitleDao().deleteAll()
    contactLogDao().deleteAll()
    contactPlanDao().deleteAll()
    familyTaskOffsetDao().deleteAll()
    familyTaskDao().deleteAll()
    reminderOffsetDao().deleteAll()
    reminderPreferenceDao().deleteAll()
    importantDateDao().deleteAll()
    relationshipDao().deleteAll()
    phoneNumberDao().deleteAll()
    // 配对与映射显式先清（persons 级联是兜底）；设备偏好不随资料清除
    // （tap_device 不在清空列表里，本就不写不删）
    tapPairingDao().clearMap()
    tapPairingDao().deletePairing()
    // 家人共享的对方→本机对应关系随资料清掉（本机人物都没了）
    personAliasDao().deleteAll()
    // 协作表：绑定/发布索引/草稿/仅本机标记全部随资料清掉
    // （恢复旧备份=重建协作绑定，不走旧绑定；接收草稿随之作废）
    familyReceiveDraftDao().deleteAll()
    familyPublicationDao().deleteAll()
    familyCollabDao().deleteAll()
    familyLocalOnlyDao().deleteAll()
    personDao().deleteAll()
}
