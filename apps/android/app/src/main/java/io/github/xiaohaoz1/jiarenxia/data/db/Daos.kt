package io.github.xiaohaoz1.jiarenxia.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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
    /** 孕期关联事项（含归档与已办；孕期记录/历史用）。 */
    @Transaction
    @Query("SELECT * FROM family_tasks WHERE pregnancy_id = :pregnancyId")
    fun observeForPregnancyWithOffsets(pregnancyId: String): Flow<List<FamilyTaskWithOffsets>>
    /**
     * 归档未完成事项：孕期结束/出生后，把这次孕期关联的
     * 未办（done=0）且未归档的事项整体转 archived。
     */
    @Query(
        "UPDATE family_tasks SET archived = 1, updated_at = :updatedAt " +
            "WHERE pregnancy_id = :pregnancyId AND done = 0 AND archived = 0"
    )
    suspend fun archiveUnfinishedForPregnancy(pregnancyId: String, updatedAt: Long)
    @Query("DELETE FROM family_tasks WHERE pregnancy_id = :pregnancyId")
    suspend fun deleteForPregnancy(pregnancyId: String)
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

/** person_facts DAO：「关于TA」记录。 */
@Dao
interface PersonFactDao {
    @Insert suspend fun insert(entity: PersonFactEntity)
    @Insert suspend fun insertAll(entities: List<PersonFactEntity>)
    @Update suspend fun update(entity: PersonFactEntity)
    @Query("SELECT * FROM person_facts WHERE id = :id")
    suspend fun getById(id: String): PersonFactEntity?
    @Query("SELECT * FROM person_facts WHERE person_id = :personId ORDER BY created_at, id")
    fun observeForPerson(personId: String): Flow<List<PersonFactEntity>>
    @Query("SELECT * FROM person_facts") suspend fun getAll(): List<PersonFactEntity>
    @Query("DELETE FROM person_facts WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM person_facts") suspend fun deleteAll()
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

/** home_visit_plans DAO：回家看看计划。 */
@Dao
interface HomeVisitPlanDao {
    @Insert suspend fun insert(entity: HomeVisitPlanEntity)
    @Update suspend fun update(entity: HomeVisitPlanEntity)
    @Query("SELECT * FROM home_visit_plans") fun observeAll(): Flow<List<HomeVisitPlanEntity>>
    @Query("SELECT * FROM home_visit_plans") suspend fun getAll(): List<HomeVisitPlanEntity>
    @Query("SELECT * FROM home_visit_plans WHERE id = :id")
    suspend fun get(id: String): HomeVisitPlanEntity?
    @Query("DELETE FROM home_visit_plans WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM home_visit_plans") suspend fun deleteAll()
}

/** home_visit_members DAO：计划成员（两端外键级联）。 */
@Dao
interface HomeVisitMemberDao {
    @Insert suspend fun insertAll(entities: List<HomeVisitMemberEntity>)
    @Query("DELETE FROM home_visit_members WHERE plan_id = :planId")
    suspend fun deleteForPlan(planId: String)
    @Query("SELECT * FROM home_visit_members") fun observeAll(): Flow<List<HomeVisitMemberEntity>>
    @Query("SELECT * FROM home_visit_members") suspend fun getAll(): List<HomeVisitMemberEntity>
    @Query("SELECT * FROM home_visit_members WHERE plan_id = :planId")
    suspend fun getForPlan(planId: String): List<HomeVisitMemberEntity>
    @Query("DELETE FROM home_visit_members") suspend fun deleteAll()
}

/** pregnancies DAO：孕期记录。 */
@Dao
interface PregnancyDao {
    @Upsert suspend fun upsert(entity: PregnancyEntity)
    @Insert suspend fun insertAll(entities: List<PregnancyEntity>)
    @Query("SELECT * FROM pregnancies WHERE id = :id")
    suspend fun get(id: String): PregnancyEntity?
    /** 该人物全部孕期：ACTIVE 在前，其余按 updated_at 倒序。 */
    @Query(
        "SELECT * FROM pregnancies WHERE person_id = :personId " +
            "ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, " +
            "updated_at DESC"
    )
    fun observeForPerson(personId: String): Flow<List<PregnancyEntity>>
    /** 全部 ACTIVE 孕期（家庭页状态用）。 */
    @Query("SELECT * FROM pregnancies WHERE status = 'ACTIVE'")
    fun observeActive(): Flow<List<PregnancyEntity>>
    @Query("SELECT * FROM pregnancies WHERE status = 'ACTIVE'")
    suspend fun getActive(): List<PregnancyEntity>
    @Query("SELECT * FROM pregnancies") suspend fun getAll(): List<PregnancyEntity>
    @Query("SELECT * FROM pregnancies") fun observeAll(): Flow<List<PregnancyEntity>>
    @Query("DELETE FROM pregnancies WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM pregnancies") suspend fun deleteAll()
}

/** pregnancy_babies DAO：孕期 ↔ 宝宝人物关联（两端级联）。 */
@Dao
interface PregnancyBabyDao {
    @Insert suspend fun insertAll(entities: List<PregnancyBabyEntity>)
    @Query("SELECT * FROM pregnancy_babies WHERE pregnancy_id = :pregnancyId")
    suspend fun getForPregnancy(pregnancyId: String): List<PregnancyBabyEntity>
    /** 该宝宝已关联的孕期行（「已关联过任何孕期的人不能再关联」校验用）。 */
    @Query("SELECT * FROM pregnancy_babies WHERE baby_person_id = :babyPersonId")
    suspend fun getForBaby(babyPersonId: String): List<PregnancyBabyEntity>
    @Query("SELECT * FROM pregnancy_babies") suspend fun getAll(): List<PregnancyBabyEntity>
    @Query("SELECT * FROM pregnancy_babies") fun observeAll(): Flow<List<PregnancyBabyEntity>>
    @Query("DELETE FROM pregnancy_babies WHERE pregnancy_id = :pregnancyId")
    suspend fun deleteForPregnancy(pregnancyId: String)
    @Query("DELETE FROM pregnancy_babies") suspend fun deleteAll()
}

/** checklists DAO：清单（待产包等）。 */
@Dao
interface ChecklistDao {
    @Upsert suspend fun upsert(entity: ChecklistEntity)
    @Insert suspend fun insertAll(entities: List<ChecklistEntity>)
    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun get(id: String): ChecklistEntity?
    @Query("SELECT * FROM checklists WHERE id = :id")
    fun observe(id: String): Flow<ChecklistEntity?>
    /** 该孕期某类清单（至多一张；createFromTemplate 幂等判断用）。 */
    @Query("SELECT * FROM checklists WHERE pregnancy_id = :pregnancyId AND kind = :kind")
    suspend fun getForPregnancy(pregnancyId: String, kind: String): ChecklistEntity?
    @Query("SELECT * FROM checklists WHERE pregnancy_id = :pregnancyId AND kind = :kind")
    fun observeForPregnancy(pregnancyId: String, kind: String): Flow<ChecklistEntity?>
    /** 36 周提醒用：该孕期的清单（任意 kind）。 */
    @Query("SELECT * FROM checklists WHERE pregnancy_id = :pregnancyId")
    suspend fun getAllForPregnancy(pregnancyId: String): List<ChecklistEntity>
    /** 删除孕期时显式删 BIRTH_BAG 清单（其他 kind 靠 SET NULL 保留）。 */
    @Query("DELETE FROM checklists WHERE pregnancy_id = :pregnancyId AND kind = 'BIRTH_BAG'")
    suspend fun deleteBirthBagsForPregnancy(pregnancyId: String)
    @Query("SELECT * FROM checklists") suspend fun getAll(): List<ChecklistEntity>
    @Query("SELECT * FROM checklists") fun observeAll(): Flow<List<ChecklistEntity>>
    @Query("DELETE FROM checklists WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM checklists") suspend fun deleteAll()
}

/** checklist_items DAO：清单条目（checklist / person 两端级联）。 */
@Dao
interface ChecklistItemDao {
    @Upsert suspend fun upsert(entity: ChecklistItemEntity)
    @Insert suspend fun insertAll(entities: List<ChecklistItemEntity>)
    @Query("SELECT * FROM checklist_items WHERE id = :id")
    suspend fun get(id: String): ChecklistItemEntity?
    @Query("SELECT * FROM checklist_items WHERE checklist_id = :checklistId")
    suspend fun getForChecklist(checklistId: String): List<ChecklistItemEntity>
    /** 组内末尾追加：取该组最大 sort_order。 */
    @Query(
        "SELECT COALESCE(MAX(sort_order), -1) FROM checklist_items " +
            "WHERE checklist_id = :checklistId AND group_name = :groupName"
    )
    suspend fun maxSortOrder(checklistId: String, groupName: String): Int
    @Query("SELECT * FROM checklist_items") suspend fun getAll(): List<ChecklistItemEntity>
    @Query("SELECT * FROM checklist_items") fun observeAll(): Flow<List<ChecklistItemEntity>>
    @Query("DELETE FROM checklist_items WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM checklist_items WHERE checklist_id = :checklistId")
    suspend fun deleteForChecklist(checklistId: String)
    /** 「定日期」写回条目关联的事项；「再定一次日期」替换旧值。 */
    @Query(
        "UPDATE checklist_items SET task_id = :taskId, updated_at = :now " +
            "WHERE id = :id"
    )
    suspend fun updateTaskId(id: String, taskId: String?, now: Long)
    /** 事项被删后清掉悬空引用（仓库在事项删除路径调用）。 */
    @Query("UPDATE checklist_items SET task_id = NULL, updated_at = :now WHERE task_id = :taskId")
    suspend fun clearTaskId(taskId: String, now: Long)
    @Query("DELETE FROM checklist_items") suspend fun deleteAll()
}

/** baby_celebrations DAO：宝宝满月/百天另设庆祝日期。 */
@Dao
interface BabyCelebrationDao {
    @Upsert suspend fun upsert(entity: BabyCelebrationEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BabyCelebrationEntity>)
    @Query("SELECT * FROM baby_celebrations WHERE person_id = :personId")
    suspend fun getForPerson(personId: String): List<BabyCelebrationEntity>
    @Query("SELECT * FROM baby_celebrations WHERE person_id = :personId")
    fun observeForPerson(personId: String): Flow<List<BabyCelebrationEntity>>
    @Query("SELECT * FROM baby_celebrations") suspend fun getAll(): List<BabyCelebrationEntity>
    @Query("SELECT * FROM baby_celebrations") fun observeAll(): Flow<List<BabyCelebrationEntity>>
    @Query("DELETE FROM baby_celebrations WHERE person_id = :personId AND kind = :kind")
    suspend fun delete(personId: String, kind: String)
    @Query("DELETE FROM baby_celebrations") suspend fun deleteAll()
}

/** medications DAO：用药记录（FAMILY_AFFAIRS 23）。时刻在 medication_times 表。 */
@Dao
interface MedicationDao {
    @Upsert suspend fun upsert(entity: MedicationEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MedicationEntity>)
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun get(id: String): MedicationEntity?
    @Query("SELECT * FROM medications WHERE person_id = :personId ORDER BY created_at DESC")
    fun observeForPerson(personId: String): Flow<List<MedicationEntity>>
    @Query("SELECT * FROM medications WHERE person_id = :personId ORDER BY created_at DESC")
    suspend fun getForPerson(personId: String): List<MedicationEntity>
    @Query("SELECT * FROM medications") fun observeAll(): Flow<List<MedicationEntity>>
    @Query("SELECT * FROM medications") suspend fun getAll(): List<MedicationEntity>
    @Query("UPDATE medications SET stopped_epoch_day = :stoppedDay, updated_at = :now WHERE id = :id")
    suspend fun markStopped(id: String, stoppedDay: Long, now: Long): Int
    @Query("UPDATE medications SET stopped_epoch_day = NULL, updated_at = :now WHERE id = :id")
    suspend fun clearStopped(id: String, now: Long): Int
    @Query("DELETE FROM medications WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM medications") suspend fun deleteAll()
}

/** medication_times DAO：每药每天服用时刻（主键 medication_id+minute_of_day）。 */
@Dao
interface MedicationTimeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MedicationTimeEntity>)
    @Query("SELECT * FROM medication_times WHERE medication_id = :medicationId ORDER BY minute_of_day")
    suspend fun getForMedication(medicationId: String): List<MedicationTimeEntity>
    @Query("SELECT * FROM medication_times WHERE medication_id = :medicationId ORDER BY minute_of_day")
    fun observeForMedication(medicationId: String): Flow<List<MedicationTimeEntity>>
    @Query("SELECT * FROM medication_times") suspend fun getAll(): List<MedicationTimeEntity>
    @Query("SELECT * FROM medication_times") fun observeAll(): Flow<List<MedicationTimeEntity>>
    @Query("DELETE FROM medication_times WHERE medication_id = :medicationId")
    suspend fun deleteForMedication(medicationId: String)
    @Query("DELETE FROM medication_times") suspend fun deleteAll()
}

/** medication_person_prefs DAO：用药区按人开关（每人一行）。 */
@Dao
interface MedicationPersonPrefsDao {
    @Upsert suspend fun upsert(entity: MedicationPersonPrefsEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MedicationPersonPrefsEntity>)
    @Query("SELECT * FROM medication_person_prefs WHERE person_id = :personId")
    suspend fun get(personId: String): MedicationPersonPrefsEntity?
    @Query("SELECT * FROM medication_person_prefs WHERE person_id = :personId")
    fun observeForPerson(personId: String): Flow<MedicationPersonPrefsEntity?>
    @Query("SELECT * FROM medication_person_prefs") fun observeAll(): Flow<List<MedicationPersonPrefsEntity>>
    @Query("SELECT * FROM medication_person_prefs") suspend fun getAll(): List<MedicationPersonPrefsEntity>
    @Query("DELETE FROM medication_person_prefs") suspend fun deleteAll()
}
