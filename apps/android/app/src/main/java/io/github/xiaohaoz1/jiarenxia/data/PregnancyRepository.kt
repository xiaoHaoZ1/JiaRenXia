package io.github.xiaohaoz1.jiarenxia.data

import androidx.room.withTransaction
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays
import io.github.xiaohaoz1.jiarenxia.core.model.Pregnancy
import io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.ReminderPreference
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.ChecklistEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ChecklistItemEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PregnancyBabyEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PregnancyEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ReminderOffsetEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ReminderPreferenceEntity
import io.github.xiaohaoz1.jiarenxia.domain.NEW_PERSON
import io.github.xiaohaoz1.jiarenxia.domain.checklist.ChecklistTemplates
import io.github.xiaohaoz1.jiarenxia.domain.pregnancy.PregnancyMath
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * 孕期记录仓库（FAMILY_AFFAIRS 26）。
 * 写入统一走 db.withTransaction；关联产检事项经 FamilyTaskRepository
 * 的 pregnancyId 维护（本仓库只在 end/delete 时直接动事项表）。
 */
interface PregnancyRepository {
    /** 该人物全部孕期（ACTIVE 在前，其余按 updated_at 倒序）。 */
    fun observeForPerson(personId: String): Flow<List<Pregnancy>>
    /** 全部 ACTIVE 孕期（家庭页状态用）。 */
    fun observeActive(): Flow<List<Pregnancy>>
    suspend fun get(id: String): Pregnancy?
    /**
     * 新建孕期记录。[today] 用于日期范围校验（预产期须在
     * [today−42, today+280]；末次月经须在 [today−322, today]）。
     */
    suspend fun create(
        personId: String,
        due: LocalDate,
        lmp: LocalDate?,
        today: LocalDate
    ): FamilyResult<String>
    /** 修改预产期/末次月经（仅 ACTIVE）；不动已记的产检日期。 */
    suspend fun updateDates(
        id: String,
        due: LocalDate,
        lmp: LocalDate?,
        today: LocalDate
    ): FamilyResult<Unit>
    /** 结束这条孕期记录：ACTIVE → ENDED，同事务归档未完成产检事项。 */
    suspend fun end(id: String, today: LocalDate): FamilyResult<Unit>
    /** 删除孕期记录：同事务删除关联产检事项与孕期（babies 行级联）。 */
    suspend fun delete(id: String): FamilyResult<Unit>
    /**
     * 「宝宝出生了」（FAMILY_AFFAIRS 26 第 3 批）：一个事务内建人/补关系/
     * 补阳历生日、写 pregnancy_babies、（非 append 时）孕期转 BORN 并归档
     * 未完成产检、生成或补齐 POSTNATAL 清单。任一步失败整体回滚。
     * [append] = true 为补记（孕期须已 BORN；不改状态与 ended）。
     */
    suspend fun recordBirth(
        pregnancyId: String,
        babies: List<BirthBaby>,
        otherParentId: String?,
        append: Boolean,
        today: LocalDate
    ): FamilyResult<BirthResult>
    /** 已关联过任何孕期的宝宝人物 id（出生表单候选排除用）。 */
    fun observeLinkedBabyIds(): Flow<Set<String>>
}

/** recordBirth 的一个宝宝：新建人物 或 已在家谱中的人物。 */
sealed interface BirthBaby {
    /** 新建宝宝：阳历生日 = 出生日期，生日提醒按手工录入默认开启。 */
    data class New(
        val name: String,
        val gender: Gender,
        val birthDate: LocalDate,
        val birthMinute: Int? = null
    ) : BirthBaby

    /** 已在家谱中：缺的亲子关系补上；无阳历生日且传了 birthDate 时补写。 */
    data class Existing(
        val personId: String,
        val birthDate: LocalDate? = null,
        val birthMinute: Int? = null
    ) : BirthBaby
}

/** recordBirth 结果：POSTNATAL 清单 id + 本次记录的宝宝人物 id（按表单顺序）。 */
data class BirthResult(
    val checklistId: String,
    val babyIds: List<String>
)

@Singleton
class PregnancyRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val ids: IdGenerator,
    private val clock: TimeSource,
    private val familyRepository: FamilyRepository
) : PregnancyRepository {

    private val dao get() = db.pregnancyDao()
    private val babyDao get() = db.pregnancyBabyDao()
    private val taskDao get() = db.familyTaskDao()
    private val checklistDao get() = db.checklistDao()
    private val checklistItemDao get() = db.checklistItemDao()

    override fun observeForPerson(personId: String): Flow<List<Pregnancy>> =
        combine(
            dao.observeForPerson(personId),
            babyDao.observeAll()
        ) { rows, babies ->
            val byPreg = babies.groupBy { it.pregnancyId }
            rows.map { it.toModelWithBabies(byPreg[it.id].orEmpty()) }
        }

    override fun observeActive(): Flow<List<Pregnancy>> =
        combine(dao.observeActive(), babyDao.observeAll()) { rows, babies ->
            val byPreg = babies.groupBy { it.pregnancyId }
            rows.map { it.toModelWithBabies(byPreg[it.id].orEmpty()) }
        }

    override fun observeLinkedBabyIds(): Flow<Set<String>> =
        babyDao.observeAll().map { rows -> rows.map { it.babyPersonId }.toSet() }

    override suspend fun get(id: String): Pregnancy? {
        val e = dao.get(id) ?: return null
        return e.toModel(babyDao.getForPregnancy(id).map { it.babyPersonId })
    }

    override suspend fun create(
        personId: String,
        due: LocalDate,
        lmp: LocalDate?,
        today: LocalDate
    ): FamilyResult<String> {
        if (!PregnancyMath.isDueInValidRange(due, today)) {
            return FamilyResult.Failure(FamilyError.PregnancyInvalidDate)
        }
        if (lmp != null && !PregnancyMath.isLmpInValidRange(lmp, today)) {
            return FamilyResult.Failure(FamilyError.PregnancyInvalidDate)
        }
        val now = clock.now()
        return db.withTransaction {
            val person = db.personDao().getById(personId)
                ?: return@withTransaction FamilyResult.Failure(FamilyError.PersonNotFound)
            if (person.livingStatus != LivingStatus.ALIVE ||
                person.gender != Gender.FEMALE
            ) {
                return@withTransaction FamilyResult.Failure(
                    FamilyError.PregnancyPersonIneligible)
            }
            // 同一人物同时只允许一条 ACTIVE
            val active = dao.getAll().any {
                it.personId == personId && it.status == PregnancyStatus.ACTIVE.name
            }
            if (active) {
                return@withTransaction FamilyResult.Failure(
                    FamilyError.PregnancyAlreadyActive)
            }
            val id = ids.newId()
            dao.upsert(
                PregnancyEntity(
                    id = id,
                    personId = personId,
                    dueEpochDay = due.toEpochDay(),
                    lmpEpochDay = lmp?.toEpochDay(),
                    status = PregnancyStatus.ACTIVE.name,
                    endedEpochDay = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
            FamilyResult.Success(id)
        }
    }

    override suspend fun updateDates(
        id: String,
        due: LocalDate,
        lmp: LocalDate?,
        today: LocalDate
    ): FamilyResult<Unit> {
        if (!PregnancyMath.isDueInValidRange(due, today)) {
            return FamilyResult.Failure(FamilyError.PregnancyInvalidDate)
        }
        if (lmp != null && !PregnancyMath.isLmpInValidRange(lmp, today)) {
            return FamilyResult.Failure(FamilyError.PregnancyInvalidDate)
        }
        return db.withTransaction {
            val e = dao.get(id)
                ?: return@withTransaction FamilyResult.Failure(FamilyError.PregnancyNotFound)
            if (e.status != PregnancyStatus.ACTIVE.name) {
                return@withTransaction FamilyResult.Failure(FamilyError.PregnancyNotFound)
            }
            dao.upsert(
                e.copy(
                    dueEpochDay = due.toEpochDay(),
                    lmpEpochDay = lmp?.toEpochDay(),
                    updatedAt = clock.now()
                )
            )
            FamilyResult.Success(Unit)
        }
    }

    /**
     * 把这次孕期关联的未完成且未归档事项整体归档。
     * 供 end() 使用；第 3 批“宝宝出生了”路径复用同一逻辑。
     */
    private suspend fun archiveUnfinishedTasks(pregnancyId: String, now: Long) {
        taskDao.archiveUnfinishedForPregnancy(pregnancyId, now)
    }

    override suspend fun end(id: String, today: LocalDate): FamilyResult<Unit> {
        val now = clock.now()
        return db.withTransaction {
            val e = dao.get(id)
                ?: return@withTransaction FamilyResult.Failure(FamilyError.PregnancyNotFound)
            if (e.status != PregnancyStatus.ACTIVE.name) {
                return@withTransaction FamilyResult.Failure(FamilyError.PregnancyNotFound)
            }
            dao.upsert(
                e.copy(
                    status = PregnancyStatus.ENDED.name,
                    endedEpochDay = today.toEpochDay(),
                    updatedAt = now
                )
            )
            archiveUnfinishedTasks(id, now)
            FamilyResult.Success(Unit)
        }
    }

    override suspend fun delete(id: String): FamilyResult<Unit> =
        db.withTransaction {
            val e = dao.get(id)
                ?: return@withTransaction FamilyResult.Failure(FamilyError.PregnancyNotFound)
            // 关联产检事项一并删除（不论状态）；babies 行经外键级联
            taskDao.deleteForPregnancy(id)
            // 待产包清单先删（条目级联）；其他种类的清单靠 pregnancy_id
            // 的 ON DELETE SET NULL 保留，为第 3 批做准备
            checklistDao.deleteBirthBagsForPregnancy(id)
            dao.delete(id)
            FamilyResult.Success(Unit)
        }

    /** recordBirth 内部中止：携带业务错误，整体回滚后由外层转回 Failure。 */
    private class TxAbort(val error: FamilyError) : Exception()

    override suspend fun recordBirth(
        pregnancyId: String,
        babies: List<BirthBaby>,
        otherParentId: String?,
        append: Boolean,
        today: LocalDate
    ): FamilyResult<BirthResult> {
        if (babies.isEmpty()) {
            return FamilyResult.Failure(FamilyError.BirthNoBabies)
        }
        if (babies.size > 3) {
            return FamilyResult.Failure(FamilyError.BirthTooManyBabies)
        }
        // 出生时刻 0..1439（分钟）
        val badMinute = babies.any {
            val m = when (it) {
                is BirthBaby.New -> it.birthMinute
                is BirthBaby.Existing -> it.birthMinute
            }
            m != null && m !in 0..1439
        }
        if (badMinute) {
            return FamilyResult.Failure(FamilyError.PregnancyInvalidDate)
        }
        val now = clock.now()
        return try {
            db.withTransaction {
                val preg = dao.get(pregnancyId)
                    ?: throw TxAbort(FamilyError.PregnancyNotFound)
                val expect = if (append) PregnancyStatus.BORN
                else PregnancyStatus.ACTIVE
                if (preg.status != expect.name) {
                    throw TxAbort(FamilyError.PregnancyNotFound)
                }
                // 另一位父母：只能是孕妈当前的配偶
                if (otherParentId != null) {
                    val isSpouse = db.relationshipDao()
                        .getSpouseRecords(preg.personId).any {
                            (it.sourcePersonId == preg.personId &&
                                it.targetPersonId == otherParentId) ||
                                (it.targetPersonId == preg.personId &&
                                    it.sourcePersonId == otherParentId)
                        }
                    if (!isSpouse ||
                        db.personDao().getById(otherParentId) == null
                    ) {
                        throw TxAbort(FamilyError.BirthOtherParentInvalid)
                    }
                }
                // 出生日期范围：[today−365, today]
                val minEpoch = today.minusDays(365).toEpochDay()
                babies.forEach { b ->
                    val d = when (b) {
                        is BirthBaby.New -> b.birthDate
                        is BirthBaby.Existing -> b.birthDate
                    }
                    if (d != null &&
                        (d.toEpochDay() < minEpoch || d.isAfter(today))
                    ) {
                        throw TxAbort(FamilyError.PregnancyInvalidDate)
                    }
                }

                // 逐个宝宝：建人/补关系/补阳历生日 → 收集 (personId, minute, 出生日期)
                val resolved = mutableListOf<ResolvedBaby>()
                val seenExisting = mutableSetOf<String>()
                for (b in babies) {
                    when (b) {
                        is BirthBaby.New -> {
                            val draft = PersonDraft(
                                name = b.name.trim(),
                                gender = b.gender,
                                birthdays = PersonBirthdays(
                                    solar = BirthdayValue(
                                        PartialDate(
                                            b.birthDate.year,
                                            b.birthDate.monthValue,
                                            b.birthDate.dayOfMonth
                                        )
                                    )
                                )
                            )
                            val specs = mutableListOf(
                                RelationshipSpec(
                                    preg.personId, NEW_PERSON,
                                    RelationshipType.PARENT, null
                                )
                            )
                            if (otherParentId != null) {
                                specs += RelationshipSpec(
                                    otherParentId, NEW_PERSON,
                                    RelationshipType.PARENT, null
                                )
                            }
                            when (val r = familyRepository.createRelative(draft, specs)) {
                                is FamilyResult.Success -> resolved += ResolvedBaby(
                                    r.value, b.birthMinute, b.birthDate
                                )
                                is FamilyResult.Failure -> throw TxAbort(r.error)
                            }
                        }
                        is BirthBaby.Existing -> {
                            val p = db.personDao().getById(b.personId)
                                ?: throw TxAbort(FamilyError.PersonNotFound)
                            if (p.isSelf || b.personId == preg.personId ||
                                !seenExisting.add(b.personId) ||
                                db.pregnancyBabyDao()
                                    .getForBaby(b.personId).isNotEmpty()
                            ) {
                                throw TxAbort(FamilyError.BirthBabyIneligible)
                            }
                            // 缺的亲子关系补上（已有的不动）
                            val parents = db.relationshipDao()
                                .getParentRecords(b.personId)
                                .mapTo(HashSet()) { it.sourcePersonId }
                            val specs = mutableListOf<RelationshipSpec>()
                            if (preg.personId !in parents) {
                                specs += RelationshipSpec(
                                    preg.personId, b.personId,
                                    RelationshipType.PARENT, null
                                )
                            }
                            if (otherParentId != null && otherParentId !in parents) {
                                specs += RelationshipSpec(
                                    otherParentId, b.personId,
                                    RelationshipType.PARENT, null
                                )
                            }
                            if (specs.isNotEmpty()) {
                                when (val r = familyRepository.addRelationships(specs)) {
                                    is FamilyResult.Success -> Unit
                                    is FamilyResult.Failure -> throw TxAbort(r.error)
                                }
                            }
                            // 没有阳历生日且传了 birthDate → 补写（已有不改）；
                            // 提醒按手工录入默认开启（首个生日）。
                            val solar = db.importantDateDao()
                                .getByPersonAndType(
                                    b.personId, ImportantDateType.BIRTHDAY_SOLAR.name
                                )
                            var effectiveDate: LocalDate? = solar?.let { e ->
                                e.year?.let { LocalDate.of(it, e.month, e.day) }
                            }
                            if (solar == null && b.birthDate != null) {
                                val dateId = ids.newId()
                                db.importantDateDao().insert(
                                    ImportantDateEntity(
                                        id = dateId,
                                        personId = b.personId,
                                        type = ImportantDateType.BIRTHDAY_SOLAR,
                                        year = b.birthDate.year,
                                        month = b.birthDate.monthValue,
                                        day = b.birthDate.dayOfMonth,
                                        calendarType = CalendarType.SOLAR,
                                        isLeapMonth = false,
                                        note = null,
                                        createdAt = now,
                                        updatedAt = now
                                    )
                                )
                                val prefId = ids.newId()
                                db.reminderPreferenceDao().insert(
                                    ReminderPreferenceEntity(
                                        id = prefId,
                                        importantDateId = dateId,
                                        enabled = true,
                                        createdAt = now,
                                        updatedAt = now
                                    )
                                )
                                db.reminderOffsetDao().insertAll(
                                    ReminderPreference.DEFAULT_OFFSETS.map {
                                        ReminderOffsetEntity(
                                            ids.newId(), prefId, it
                                        )
                                    }
                                )
                                effectiveDate = b.birthDate
                            }
                            resolved += ResolvedBaby(
                                b.personId, b.birthMinute, effectiveDate
                            )
                        }
                    }
                }

                // pregnancy_babies（含 birth_minute）
                babyDao.insertAll(
                    resolved.map {
                        PregnancyBabyEntity(
                            pregnancyId = pregnancyId,
                            babyPersonId = it.personId,
                            birthMinute = it.birthMinute
                        )
                    }
                )

                if (!append) {
                    // 孕期转 BORN；ended = 宝宝中最早的出生日期（都没有取今天）
                    val ended = resolved.mapNotNull { it.birthDate }
                        .minOrNull() ?: today
                    dao.upsert(
                        preg.copy(
                            status = PregnancyStatus.BORN.name,
                            endedEpochDay = ended.toEpochDay(),
                            updatedAt = now
                        )
                    )
                    archiveUnfinishedTasks(pregnancyId, now)
                }

                // POSTNATAL 清单：没有就新建（孕妈条目只此一次）；
                // 本次每个宝宝各追加一份宝宝条目。
                val cl = checklistDao.getForPregnancy(
                    pregnancyId, ChecklistKind.POSTNATAL.name
                )
                val clId: String
                var seq = if (cl == null) 0 else
                    (checklistItemDao.getForChecklist(cl.id)
                        .maxOfOrNull { it.sortOrder } ?: -1) + 1
                if (cl == null) {
                    clId = ids.newId()
                    checklistDao.upsert(
                        ChecklistEntity(
                            id = clId,
                            kind = ChecklistKind.POSTNATAL.name,
                            title = ChecklistTemplates.POSTNATAL_TITLE,
                            personId = preg.personId,
                            pregnancyId = pregnancyId,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                } else {
                    clId = cl.id
                }
                val newItems = mutableListOf<ChecklistItemEntity>()
                resolved.forEach { r ->
                    ChecklistTemplates.POSTNATAL_BABY_ITEMS.forEach { t ->
                        newItems += ChecklistItemEntity(
                            id = ids.newId(),
                            checklistId = clId,
                            groupName = t.groupName,
                            title = t.title,
                            note = t.note,
                            checked = false,
                            sortOrder = seq++,
                            personId = r.personId,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                }
                // 孕妈条目只在新建清单时生成一次
                if (cl == null) {
                    ChecklistTemplates.POSTNATAL_MOM_ITEMS.forEach { t ->
                        newItems += ChecklistItemEntity(
                            id = ids.newId(),
                            checklistId = clId,
                            groupName = t.groupName,
                            title = t.title,
                            note = t.note,
                            checked = false,
                            sortOrder = seq++,
                            personId = preg.personId,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                }
                if (newItems.isNotEmpty()) {
                    checklistItemDao.insertAll(newItems)
                }

                FamilyResult.Success(
                    BirthResult(
                        checklistId = clId,
                        babyIds = resolved.map { it.personId }
                    )
                )
            }
        } catch (e: TxAbort) {
            FamilyResult.Failure(e.error)
        }
    }

    private fun PregnancyEntity.toModelWithBabies(
        babies: List<PregnancyBabyEntity>
    ) = toModel(babies.map { it.babyPersonId })

    private fun PregnancyEntity.toModel(babyIds: List<String>) = Pregnancy(
        id = id,
        personId = personId,
        dueDate = LocalDate.ofEpochDay(dueEpochDay),
        lmpDate = lmpEpochDay?.let(LocalDate::ofEpochDay),
        status = PregnancyStatus.valueOf(status),
        endedOn = endedEpochDay?.let(LocalDate::ofEpochDay),
        babyIds = babyIds,
        createdAt = createdAt,
        updatedAt = updatedAt
    )


/** recordBirth 事务内解析出的宝宝（人物 id + 出生时刻 + 用于 ended 的日期）。 */
private data class ResolvedBaby(
    val personId: String,
    val birthMinute: Int?,
    val birthDate: LocalDate?
)
}
