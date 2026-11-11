package io.github.xiaohaoz1.jiarenxia.data

import androidx.room.withTransaction
import io.github.xiaohaoz1.jiarenxia.core.model.MedMeal
import io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
import io.github.xiaohaoz1.jiarenxia.core.model.Medication
import io.github.xiaohaoz1.jiarenxia.core.model.MedicationPersonPrefs
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.MedicationEntity
import io.github.xiaohaoz1.jiarenxia.data.db.MedicationPersonPrefsEntity
import io.github.xiaohaoz1.jiarenxia.data.db.MedicationTimeEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** 用药编辑草稿：id 为 null 表示新建。 */
data class MedicationDraft(
    val id: String?,
    val personId: String,
    val name: String,
    val dose: String?,
    val spec: String?,
    val meal: MedMeal?,
    val usage: MedUsage,
    /** SHORT_COURSE 的疗程天数（1..365）；null = 疗程待确认。 */
    val courseDays: Int?,
    /** 记录的开始日期（epochDay）。 */
    val startEpochDay: Long,
    val purpose: String?,
    val note: String?,
    /** LONG_TERM 的下次拿药日（epochDay）；其他用法忽略。 */
    val refillEpochDay: Long?,
    /** 每天服用时刻（0..1439，至多 4 个）；AS_NEEDED 忽略。 */
    val times: List<Int>
)

/**
 * 用药记录仓库（FAMILY_AFFAIRS 23）。
 * 写入统一走 db.withTransaction；usage 与字段的匹配由仓库归一化：
 * 非 SHORT_COURSE 清空 course_days，非 LONG_TERM 清空 refill，
 * AS_NEEDED 清空时刻。
 */
interface MedicationRepository {
    /** 某人的用药记录（新的在前；时刻随药带出）。 */
    fun observeForPerson(personId: String): Flow<List<Medication>>
    /** 全部用药记录（提醒 worker / 同步用；变化触发 reconcile）。 */
    fun observeAll(): Flow<List<Medication>>
    suspend fun getAll(): List<Medication>
    suspend fun get(id: String): Medication?
    /** 新建 / 编辑；返回用药 id。 */
    suspend fun save(draft: MedicationDraft): FamilyResult<String>
    /** 「记录已停用」：写 stopped_epoch_day = 当天。 */
    suspend fun markStopped(id: String, today: LocalDate): FamilyResult<Unit>
    /** 「重新开始吃」：清除停用标记（不改开始日期）。 */
    suspend fun clearStopped(id: String): FamilyResult<Unit>
    suspend fun delete(id: String): FamilyResult<Unit>
    /** 全部按人开关（无记录的行按默认处理）。 */
    fun observePrefs(): Flow<List<MedicationPersonPrefs>>
    /** 「疗程到期与续药提醒」开关；personId 无行时补默认行。 */
    suspend fun setCourseRefillEnabled(personId: String, enabled: Boolean): FamilyResult<Unit>
}

@Singleton
class MedicationRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val ids: IdGenerator,
    private val clock: TimeSource
) : MedicationRepository {

    private val medDao get() = db.medicationDao()
    private val timeDao get() = db.medicationTimeDao()
    private val prefsDao get() = db.medicationPersonPrefsDao()

    override fun observeForPerson(personId: String): Flow<List<Medication>> =
        combine(
            medDao.observeForPerson(personId),
            timeDao.observeAll()
        ) { meds, times ->
            val byMed = times.groupBy { it.medicationId }
            meds.map { it.toModel(byMed[it.id].orEmpty()) }
        }

    override fun observeAll(): Flow<List<Medication>> =
        combine(medDao.observeAll(), timeDao.observeAll()) { meds, times ->
            val byMed = times.groupBy { it.medicationId }
            meds.map { it.toModel(byMed[it.id].orEmpty()) }
        }

    override suspend fun getAll(): List<Medication> {
        val times = timeDao.getAll().groupBy { it.medicationId }
        return medDao.getAll().map { it.toModel(times[it.id].orEmpty()) }
    }

    override suspend fun get(id: String): Medication? {
        val e = medDao.get(id) ?: return null
        return e.toModel(timeDao.getForMedication(id))
    }

    override suspend fun save(draft: MedicationDraft): FamilyResult<String> {
        // ---------- 校验（先做不写库的部分） ----------
        val name = draft.name.trim()
        if (name.isEmpty() || name.length > 30) {
            return FamilyResult.Failure(FamilyError.InvalidMedName)
        }
        val dose = draft.dose?.trim()?.ifEmpty { null }
        if (dose != null && dose.length > 20) {
            return FamilyResult.Failure(FamilyError.InvalidMedDose)
        }
        val spec = draft.spec?.trim()?.ifEmpty { null }
        if (spec != null && spec.length > 20) {
            return FamilyResult.Failure(FamilyError.InvalidMedSpec)
        }
        val purpose = draft.purpose?.trim()?.ifEmpty { null }
        if (purpose != null && purpose.length > 30) {
            return FamilyResult.Failure(FamilyError.InvalidMedPurpose)
        }
        val note = draft.note?.trim()?.ifEmpty { null }
        if (note != null && note.length > 200) {
            return FamilyResult.Failure(FamilyError.InvalidMedNote)
        }
        // usage 与字段的匹配：非 SHORT 清空天数、非 LONG 清空拿药日、
        // AS_NEEDED 清空时刻；SHORT 的天数 1..365（null = 待确认）。
        val courseDays = if (draft.usage == MedUsage.SHORT_COURSE) {
            draft.courseDays?.also {
                if (it !in 1..365) {
                    return FamilyResult.Failure(FamilyError.InvalidMedCourseDays)
                }
            }
        } else null
        val refill = if (draft.usage == MedUsage.LONG_TERM) draft.refillEpochDay else null
        val times = if (draft.usage == MedUsage.AS_NEEDED) {
            emptyList()
        } else {
            draft.times.map { it }.distinct().sorted()
        }
        if (times.size > 4 || times.any { it !in 0..1439 }) {
            return FamilyResult.Failure(FamilyError.InvalidMedTimes)
        }
        // 开始日期必须是合法公历日
        runCatching { LocalDate.ofEpochDay(draft.startEpochDay) }
            .getOrElse { return FamilyResult.Failure(FamilyError.InvalidMedStartDate) }
        refill?.let {
            runCatching { LocalDate.ofEpochDay(it) }
                .getOrElse { return FamilyResult.Failure(FamilyError.InvalidMedRefillDate) }
        }

        return db.withTransaction {
            // 人物须存在且在世
            val person = db.personDao().getById(draft.personId)
                ?: return@withTransaction FamilyResult.Failure(FamilyError.PersonNotFound)
            if (person.livingStatus !=
                io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.ALIVE
            ) {
                return@withTransaction FamilyResult.Failure(FamilyError.MedPersonDeceased)
            }
            val now = clock.now()
            val medId = draft.id ?: ids.newId()
            // 编辑时 id 必须存在且属于同一人物（防串号）
            val old = draft.id?.let { medDao.get(it) }
            if (draft.id != null && old == null) {
                return@withTransaction FamilyResult.Failure(FamilyError.MedicationNotFound)
            }
            if (old != null && old.personId != draft.personId) {
                return@withTransaction FamilyResult.Failure(FamilyError.PersonNotFound)
            }
            medDao.upsert(
                MedicationEntity(
                    id = medId,
                    personId = draft.personId,
                    name = name,
                    dose = dose,
                    spec = spec,
                    meal = draft.meal?.name,
                    usage = draft.usage.name,
                    courseDays = courseDays,
                    startEpochDay = draft.startEpochDay,
                    purpose = purpose,
                    note = note,
                    refillEpochDay = refill,
                    // 编辑保留原停用标记与创建时间；新建恒 null/now
                    stoppedEpochDay = old?.stoppedEpochDay,
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now
                )
            )
            // 时刻整组替换
            timeDao.deleteForMedication(medId)
            timeDao.insertAll(
                times.map { MedicationTimeEntity(medId, it) }
            )
            FamilyResult.Success(medId)
        }
    }

    override suspend fun markStopped(
        id: String,
        today: LocalDate
    ): FamilyResult<Unit> = db.withTransaction {
        if (medDao.get(id) == null) {
            return@withTransaction FamilyResult.Failure(FamilyError.MedicationNotFound)
        }
        medDao.markStopped(id, today.toEpochDay(), clock.now())
        FamilyResult.Success(Unit)
    }

    override suspend fun clearStopped(id: String): FamilyResult<Unit> =
        db.withTransaction {
            if (medDao.get(id) == null) {
                return@withTransaction FamilyResult.Failure(FamilyError.MedicationNotFound)
            }
            medDao.clearStopped(id, clock.now())
            FamilyResult.Success(Unit)
        }

    override suspend fun delete(id: String): FamilyResult<Unit> =
        db.withTransaction {
            medDao.delete(id) // 时刻经 CASCADE 删除
            FamilyResult.Success(Unit)
        }

    override fun observePrefs(): Flow<List<MedicationPersonPrefs>> =
        prefsDao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun setCourseRefillEnabled(
        personId: String,
        enabled: Boolean
    ): FamilyResult<Unit> = db.withTransaction {
        val old = prefsDao.get(personId)
        prefsDao.upsert(
            MedicationPersonPrefsEntity(
                personId = personId,
                courseRefillEnabled = enabled,
                onTimeEnabled = old?.onTimeEnabled
            )
        )
        FamilyResult.Success(Unit)
    }

    private fun MedicationEntity.toModel(times: List<MedicationTimeEntity>) = Medication(
        id = id,
        personId = personId,
        name = name,
        dose = dose,
        spec = spec,
        meal = meal?.let { runCatching { MedMeal.valueOf(it) }.getOrNull() },
        usage = runCatching { MedUsage.valueOf(usage) }
            .getOrDefault(MedUsage.AS_NEEDED),
        courseDays = courseDays,
        startEpochDay = startEpochDay,
        purpose = purpose,
        note = note,
        refillEpochDay = refillEpochDay,
        stoppedEpochDay = stoppedEpochDay,
        times = times.map { it.minuteOfDay }.sorted(),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MedicationPersonPrefsEntity.toModel() = MedicationPersonPrefs(
        personId = personId,
        courseRefillEnabled = courseRefillEnabled,
        onTimeEnabled = onTimeEnabled
    )
}
