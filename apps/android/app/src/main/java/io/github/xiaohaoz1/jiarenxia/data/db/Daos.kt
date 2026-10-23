package io.github.xiaohaoz1.jiarenxia.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Upsert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Insert suspend fun insert(entity: PersonEntity)
    @Update suspend fun update(entity: PersonEntity)
    @Query("UPDATE persons SET avatar_path = :path, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateAvatar(id: String, path: String?, updatedAt: Long): Int
    @Query("DELETE FROM persons WHERE id = :id") suspend fun deleteById(id: String)
    @Query("SELECT * FROM persons WHERE id = :id") suspend fun getById(id: String): PersonEntity?
    @Query("SELECT * FROM persons WHERE id = :id") fun observeById(id: String): Flow<PersonEntity?>
    @Query("SELECT * FROM persons WHERE is_self = 1 LIMIT 1") suspend fun getSelf(): PersonEntity?
    @Query("SELECT * FROM persons WHERE is_self = 1 LIMIT 1") fun observeSelf(): Flow<PersonEntity?>
    @Query("SELECT * FROM persons") fun observeAll(): Flow<List<PersonEntity>>
    @Query("SELECT * FROM persons") suspend fun getAll(): List<PersonEntity>
    @Query("SELECT COUNT(*) FROM persons") suspend fun count(): Int
    @Query("DELETE FROM persons") suspend fun deleteAll()
}

@Dao
interface PhoneNumberDao {
    @Insert suspend fun insert(entity: PhoneNumberEntity)
    @Update suspend fun update(entity: PhoneNumberEntity)
    @Query("DELETE FROM phone_numbers WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>)
    @Query("SELECT * FROM phone_numbers WHERE person_id = :personId") suspend fun getForPerson(personId: String): List<PhoneNumberEntity>
    @Query("SELECT * FROM phone_numbers WHERE person_id = :personId ORDER BY is_primary DESC, created_at, id")
    fun observeForPerson(personId: String): Flow<List<PhoneNumberEntity>>
    @Query("SELECT * FROM phone_numbers ORDER BY is_primary DESC, created_at, id")
    fun observeAll(): Flow<List<PhoneNumberEntity>>
    @Query("SELECT * FROM phone_numbers") suspend fun getAll(): List<PhoneNumberEntity>
    @Query("SELECT COUNT(*) FROM phone_numbers") suspend fun count(): Int
    @Query("DELETE FROM phone_numbers") suspend fun deleteAll()
}

@Dao
interface RelationshipDao {
    @Insert suspend fun insert(entity: RelationshipEntity)
    @Query("DELETE FROM relationships WHERE id = :id") suspend fun deleteById(id: String)
    @Query("SELECT * FROM relationships WHERE id = :id") suspend fun getById(id: String): RelationshipEntity?
    @Query("SELECT * FROM relationships") fun observeAll(): Flow<List<RelationshipEntity>>
    @Query("SELECT * FROM relationships") suspend fun getAll(): List<RelationshipEntity>
    @Query("SELECT * FROM relationships WHERE source_person_id = :personId OR target_person_id = :personId")
    suspend fun getForPerson(personId: String): List<RelationshipEntity>
    @Query("SELECT * FROM relationships WHERE source_person_id = :personId OR target_person_id = :personId")
    fun observeForPerson(personId: String): Flow<List<RelationshipEntity>>
    @Query(
        "SELECT * FROM relationships WHERE " +
            "(source_person_id = :a AND target_person_id = :b) OR " +
            "(source_person_id = :b AND target_person_id = :a)"
    )
    suspend fun getBetween(a: String, b: String): List<RelationshipEntity>
    /** 某人物作为 target 的全部 PARENT 记录（即其父母）。 */
    @Query("SELECT * FROM relationships WHERE type = 'PARENT' AND target_person_id = :personId")
    suspend fun getParentRecords(personId: String): List<RelationshipEntity>
    /** 某人物作为 source 的全部 PARENT 记录（即其子女）。 */
    @Query("SELECT * FROM relationships WHERE type = 'PARENT' AND source_person_id = :personId")
    suspend fun getChildRecords(personId: String): List<RelationshipEntity>
    /** 某人物作为端点的全部 SPOUSE 记录。 */
    @Query("SELECT * FROM relationships WHERE type = 'SPOUSE' AND (source_person_id = :personId OR target_person_id = :personId)")
    suspend fun getSpouseRecords(personId: String): List<RelationshipEntity>
    @Query("UPDATE relationships SET relative_age_order = :order, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateAgeOrder(id: String, order: RelativeAgeOrder?, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM relationships") suspend fun count(): Int
    @Query("DELETE FROM relationships") suspend fun deleteAll()
}

@Dao
interface ImportantDateDao {
    @Insert suspend fun insert(entity: ImportantDateEntity)
    @Update suspend fun update(entity: ImportantDateEntity)
    @Query("SELECT * FROM important_dates WHERE id = :id") suspend fun getById(id: String): ImportantDateEntity?
    /** 某人的全部生日（最多阳历+农历两条；type 降序即阳历在前）。 */
    @Query("SELECT * FROM important_dates WHERE person_id = :personId AND type IN ('BIRTHDAY_SOLAR','BIRTHDAY_LUNAR') ORDER BY type DESC")
    suspend fun getBirthdays(personId: String): List<ImportantDateEntity>
    @Query("SELECT * FROM important_dates WHERE person_id = :personId AND type IN ('BIRTHDAY_SOLAR','BIRTHDAY_LUNAR') ORDER BY type DESC")
    fun observeBirthdays(personId: String): Flow<List<ImportantDateEntity>>
    @Query("SELECT * FROM important_dates WHERE type IN ('BIRTHDAY_SOLAR','BIRTHDAY_LUNAR')")
    fun observeBirthdays(): Flow<List<ImportantDateEntity>>
    @Query("SELECT * FROM important_dates WHERE type IN ('BIRTHDAY_SOLAR','BIRTHDAY_LUNAR')")
    suspend fun getBirthdays(): List<ImportantDateEntity>
    @Query("SELECT * FROM important_dates WHERE person_id = :personId AND type = :type")
    suspend fun getByPersonAndType(personId: String, type: String): ImportantDateEntity?
    @Query("DELETE FROM important_dates WHERE person_id = :personId AND type = :type")
    suspend fun deleteByPersonAndType(personId: String, type: String)
    /** 全部重要日期（备份/共享导出用；引擎仍只用生日行）。 */
    @Query("SELECT * FROM important_dates")
    suspend fun getAllDates(): List<ImportantDateEntity>
    @Query("SELECT * FROM important_dates")
    fun observeAllDates(): Flow<List<ImportantDateEntity>>
    @Query("SELECT COUNT(*) FROM important_dates") suspend fun count(): Int
    @Query("DELETE FROM important_dates") suspend fun deleteAll()
}

@Dao
interface ReminderPreferenceDao {
    @Insert suspend fun insert(entity: ReminderPreferenceEntity)
    @Update suspend fun update(entity: ReminderPreferenceEntity)
    @Query("SELECT * FROM reminder_preferences WHERE important_date_id = :dateId")
    suspend fun getForDate(dateId: String): ReminderPreferenceEntity?
    /** 人物生日提醒偏好（阳历/农历各一条）：精确限定生日类型，不会读到纪念日/忌日的配置。 */
    @Query(
        "SELECT p.* FROM reminder_preferences p " +
            "INNER JOIN important_dates d ON p.important_date_id = d.id " +
            "WHERE d.person_id = :personId AND d.type IN ('BIRTHDAY_SOLAR','BIRTHDAY_LUNAR')"
    )
    fun observeBirthdayPreferences(personId: String): Flow<List<ReminderPreferenceEntity>>
    @Query("SELECT * FROM reminder_preferences") fun observeAll(): Flow<List<ReminderPreferenceEntity>>
    @Query("SELECT * FROM reminder_preferences") suspend fun getAll(): List<ReminderPreferenceEntity>
    @Query("SELECT COUNT(*) FROM reminder_preferences") suspend fun count(): Int
    @Query("DELETE FROM reminder_preferences") suspend fun deleteAll()
}

@Dao
interface ReminderOffsetDao {
    @Insert suspend fun insertAll(entities: List<ReminderOffsetEntity>)
    @Query("SELECT * FROM reminder_offsets WHERE reminder_preference_id = :preferenceId")
    suspend fun getForPreference(preferenceId: String): List<ReminderOffsetEntity>
    /** 人物生日提醒的提前天数（详情页响应式依赖；随 offsets 表更新而重发）。 */
    @Query(
        "SELECT o.* FROM reminder_offsets o " +
            "INNER JOIN reminder_preferences p ON o.reminder_preference_id = p.id " +
            "INNER JOIN important_dates d ON p.important_date_id = d.id " +
            "WHERE d.person_id = :personId AND d.type IN ('BIRTHDAY_SOLAR','BIRTHDAY_LUNAR')"
    )
    fun observeBirthdayForPerson(personId: String): Flow<List<ReminderOffsetEntity>>
    @Query("SELECT * FROM reminder_offsets") suspend fun getAll(): List<ReminderOffsetEntity>
    @Query("SELECT * FROM reminder_offsets") fun observeAll(): kotlinx.coroutines.flow.Flow<List<ReminderOffsetEntity>>
    @Query("SELECT COUNT(*) FROM reminder_offsets") suspend fun count(): Int
    @Query("DELETE FROM reminder_offsets WHERE reminder_preference_id = :preferenceId")
    suspend fun deleteForPreference(preferenceId: String)
    @Query("DELETE FROM reminder_offsets") suspend fun deleteAll()
}

/** custom_titles DAO：观察者视角的自定义称呼。 */
@Dao
interface CustomTitleDao {
    @Upsert suspend fun upsert(entity: CustomTitleEntity)
    @Query("DELETE FROM custom_titles WHERE observer_person_id = :observerId AND target_person_id = :targetId")
    suspend fun delete(observerId: String, targetId: String)
    @Query("SELECT * FROM custom_titles WHERE observer_person_id = :observerId AND target_person_id = :targetId")
    suspend fun getForPair(observerId: String, targetId: String): CustomTitleEntity?
    @Query("SELECT * FROM custom_titles") fun observeAll(): Flow<List<CustomTitleEntity>>
    @Query("SELECT * FROM custom_titles") suspend fun getAll(): List<CustomTitleEntity>
    @Query("SELECT COUNT(*) FROM custom_titles") suspend fun count(): Int
    @Query("DELETE FROM custom_titles") suspend fun deleteAll()
}

/** family_tasks DAO（DATABASE_SCHEMA v4）：家庭事项。 */
@Dao
interface FamilyTaskDao {
    @Upsert suspend fun upsert(entity: FamilyTaskEntity)
    @Insert suspend fun insertAll(entities: List<FamilyTaskEntity>)
    @Query("DELETE FROM family_tasks WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM family_tasks WHERE id = :id") suspend fun get(id: String): FamilyTaskEntity?
    @Query("SELECT * FROM family_tasks") fun observeAll(): Flow<List<FamilyTaskEntity>>
    @Query("SELECT * FROM family_tasks") suspend fun getAll(): List<FamilyTaskEntity>
    /** 事项+offsets 一致快照（@Transaction 包裹的两表查询）。 */
    @Transaction
    @Query("SELECT * FROM family_tasks")
    fun observeAllWithOffsets(): Flow<List<FamilyTaskWithOffsets>>
    @Transaction
    @Query("SELECT * FROM family_tasks")
    suspend fun getAllWithOffsets(): List<FamilyTaskWithOffsets>
    @Query("SELECT * FROM family_tasks WHERE person_id = :personId")
    fun observeForPerson(personId: String): Flow<List<FamilyTaskEntity>>
    @Transaction
    @Query("SELECT * FROM family_tasks WHERE person_id = :personId")
    fun observeForPersonWithOffsets(personId: String): Flow<List<FamilyTaskWithOffsets>>
    @Transaction
    @Query("SELECT * FROM family_tasks WHERE id = :id")
    suspend fun getWithOffsets(id: String): FamilyTaskWithOffsets?
    @Query("SELECT COUNT(*) FROM family_tasks") suspend fun count(): Int
    @Query("DELETE FROM family_tasks") suspend fun deleteAll()
}

/** family_task_offsets DAO：事项提前天数集合。 */
@Dao
interface FamilyTaskOffsetDao {
    @Insert suspend fun insertAll(entities: List<FamilyTaskOffsetEntity>)
    @Query("SELECT * FROM family_task_offsets WHERE task_id = :taskId")
    suspend fun getForTask(taskId: String): List<FamilyTaskOffsetEntity>
    @Query("SELECT * FROM family_task_offsets") suspend fun getAll(): List<FamilyTaskOffsetEntity>
    @Query("SELECT * FROM family_task_offsets") fun observeAll(): Flow<List<FamilyTaskOffsetEntity>>
    @Query("DELETE FROM family_task_offsets WHERE task_id = :taskId")
    suspend fun deleteForTask(taskId: String)
    @Query("DELETE FROM family_task_offsets") suspend fun deleteAll()
}

/** contact_logs DAO：联系记录。 */
@Dao
interface ContactLogDao {
    @Insert suspend fun insert(entity: ContactLogEntity)
    @Insert suspend fun insertAll(entities: List<ContactLogEntity>)
    @Query("DELETE FROM contact_logs WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM contact_logs") fun observeAll(): Flow<List<ContactLogEntity>>
    @Query("SELECT * FROM contact_logs") suspend fun getAll(): List<ContactLogEntity>
    @Query("SELECT * FROM contact_logs WHERE person_id = :personId ORDER BY at_millis DESC")
    fun observeForPerson(personId: String): Flow<List<ContactLogEntity>>
    @Query("SELECT * FROM contact_logs WHERE person_id = :personId ORDER BY at_millis DESC LIMIT 1")
    suspend fun latestForPerson(personId: String): ContactLogEntity?
    @Query("DELETE FROM contact_logs") suspend fun deleteAll()
}

/** contact_plans DAO：联系周期计划。 */
@Dao
interface ContactPlanDao {
    @Upsert suspend fun upsert(entity: ContactPlanEntity)
    @Insert suspend fun insertAll(entities: List<ContactPlanEntity>)
    @Query("DELETE FROM contact_plans WHERE person_id = :personId")
    suspend fun delete(personId: String)
    @Query("SELECT * FROM contact_plans") fun observeAll(): Flow<List<ContactPlanEntity>>
    @Query("SELECT * FROM contact_plans") suspend fun getAll(): List<ContactPlanEntity>
    @Query("SELECT * FROM contact_plans WHERE person_id = :personId")
    suspend fun get(personId: String): ContactPlanEntity?
    @Query("DELETE FROM contact_plans") suspend fun deleteAll()
}

/**
 * 碰一碰配对 DAO：单行配对记录 + 来源人物映射 + 设备角色偏好。
 * 挂起函数在 withTransaction 内被调用时会自动加入当前事务——
 * 合并写入与配对写入因此能成为一次本地事务。
 */
@Dao
interface TapPairingDao {
    @Query("SELECT * FROM spouse_pairing WHERE id = 1")
    suspend fun getPairing(): SpousePairingEntity?
    @Upsert suspend fun upsertPairing(e: SpousePairingEntity)
    @Query("DELETE FROM spouse_pairing") suspend fun deletePairing()

    @Query("SELECT * FROM tap_person_map") suspend fun getMap(): List<TapPersonMapEntity>
    @Upsert suspend fun upsertMap(entries: List<TapPersonMapEntity>)
    @Query("DELETE FROM tap_person_map") suspend fun clearMap()

    @Query("SELECT * FROM tap_device WHERE id = 1") suspend fun getDevice(): TapDeviceEntity?
    @Upsert suspend fun upsertDevice(e: TapDeviceEntity)
    @Query("DELETE FROM tap_device") suspend fun deleteDevice()
}

/**
 * person_alias：对方人物 id → 本机人物 id 的对应关系（家人共享合并）。
 * 只存本机、不进备份；写操作包在调用方事务里随家人资料一起提交。
 */
@Dao
interface PersonAliasDao {
    @Query("SELECT * FROM person_alias")
    suspend fun getAll(): List<PersonAliasEntity>
    @Upsert suspend fun upsertAll(entries: List<PersonAliasEntity>)
    @Query("DELETE FROM person_alias") suspend fun deleteAll()
}

// ==================== 离线协作 DAO（v12） ====================

/** family_collaboration_state：单行协作状态。 */
@Dao
interface FamilyCollabDao {
    @Query("SELECT * FROM family_collaboration_state WHERE id = 1")
    suspend fun get(): FamilyCollabStateEntity?
    @Query("SELECT * FROM family_collaboration_state WHERE id = 1")
    fun observe(): Flow<FamilyCollabStateEntity?>
    @Upsert suspend fun upsert(e: FamilyCollabStateEntity)
    @Query("DELETE FROM family_collaboration_state") suspend fun deleteAll()
}

/** family_publications：确认版发布索引。 */
@Dao
interface FamilyPublicationDao {
    @Query("SELECT * FROM family_publications ORDER BY revision")
    suspend fun getAll(): List<FamilyPublicationEntity>
    @Query("SELECT * FROM family_publications WHERE revision = :rev")
    suspend fun get(rev: Int): FamilyPublicationEntity?
    @Query("SELECT * FROM family_publications WHERE package_id = :packageId")
    suspend fun getByPackageId(packageId: String): FamilyPublicationEntity?
    @Query("SELECT MAX(revision) FROM family_publications")
    suspend fun latestRevision(): Int?
    @Insert suspend fun insert(e: FamilyPublicationEntity)
    @Query("DELETE FROM family_publications") suspend fun deleteAll()
}

/** family_receive_drafts：接收草稿（PENDING≤1）与已处理记录。 */
@Dao
interface FamilyReceiveDraftDao {
    @Query("SELECT * FROM family_receive_drafts WHERE package_id = :packageId")
    suspend fun get(packageId: String): FamilyReceiveDraftEntity?
    @Query("SELECT * FROM family_receive_drafts WHERE status = 'PENDING' LIMIT 1")
    suspend fun getPending(): FamilyReceiveDraftEntity?
    @Query("SELECT * FROM family_receive_drafts WHERE status = 'PENDING' LIMIT 1")
    fun observePending(): Flow<FamilyReceiveDraftEntity?>
    @Query("SELECT * FROM family_receive_drafts")
    suspend fun getAll(): List<FamilyReceiveDraftEntity>
    @Upsert suspend fun upsert(e: FamilyReceiveDraftEntity)
    /**
     * 仅当草稿仍是 PENDING 时更新选择 JSON（单语句条件更新）：
     * 草稿先读 PENDING、应用事务随后落 APPLIED 的交错下，晚到的
     * 保存命中不了 WHERE，不会把状态整行写回 PENDING。
     */
    @Query("UPDATE family_receive_drafts SET decisions_json = :choicesJson, updated_at = :updatedAt WHERE package_id = :packageId AND status = 'PENDING'")
    suspend fun updateChoicesIfPending(
        packageId: String, choicesJson: String, updatedAt: Long): Int
    /** 同上的续审缓存整体刷写（计划缓存+选择+名册哈希）。 */
    @Query("UPDATE family_receive_drafts SET plan_cache_json = :planCacheJson, decisions_json = :choicesJson, roster_hash = :rosterHash, updated_at = :updatedAt WHERE package_id = :packageId AND status = 'PENDING'")
    suspend fun updateReviewIfPending(
        packageId: String, planCacheJson: String?, choicesJson: String,
        rosterHash: String, updatedAt: Long): Int
    @Query("DELETE FROM family_receive_drafts WHERE package_id = :packageId")
    suspend fun delete(packageId: String)
    @Query("DELETE FROM family_receive_drafts WHERE status = 'PENDING'")
    suspend fun deletePending()
    @Query("DELETE FROM family_receive_drafts") suspend fun deleteAll()
}

/** family_local_only：仅本机保留人物。 */
@Dao
interface FamilyLocalOnlyDao {
    @Query("SELECT * FROM family_local_only")
    suspend fun getAll(): List<FamilyLocalOnlyEntity>
    @Query("SELECT * FROM family_local_only")
    fun observeAll(): Flow<List<FamilyLocalOnlyEntity>>
    @Query("SELECT person_id FROM family_local_only")
    fun observeIds(): Flow<List<String>>
    @Query("SELECT person_id FROM family_local_only")
    suspend fun getIds(): List<String>
    @Upsert suspend fun upsert(e: FamilyLocalOnlyEntity)
    @Upsert suspend fun upsertAll(e: List<FamilyLocalOnlyEntity>)
    @Query("DELETE FROM family_local_only WHERE person_id = :personId")
    suspend fun delete(personId: String)
    @Query("DELETE FROM family_local_only") suspend fun deleteAll()
}
