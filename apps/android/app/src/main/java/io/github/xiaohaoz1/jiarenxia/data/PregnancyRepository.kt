package io.github.xiaohaoz1.jiarenxia.data

import androidx.room.withTransaction
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.Pregnancy
import io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.PregnancyBabyEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PregnancyEntity
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
}

@Singleton
class PregnancyRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val ids: IdGenerator,
    private val clock: TimeSource
) : PregnancyRepository {

    private val dao get() = db.pregnancyDao()
    private val babyDao get() = db.pregnancyBabyDao()
    private val taskDao get() = db.familyTaskDao()
    private val checklistDao get() = db.checklistDao()

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
}
