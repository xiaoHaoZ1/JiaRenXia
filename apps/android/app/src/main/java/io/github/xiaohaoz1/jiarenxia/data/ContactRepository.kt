package io.github.xiaohaoz1.jiarenxia.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import io.github.xiaohaoz1.jiarenxia.core.model.ContactLog
import io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.ContactLogEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ContactPlanEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 联系周期可选值（天）：每周/每两周/每月/每两个月/每季度。 */
object ContactIntervals {
    const val WEEK = 7
    const val TWO_WEEKS = 14
    const val MONTH = 30
    const val TWO_MONTHS = 60
    const val QUARTER = 90
    val ALL = listOf(WEEK, TWO_WEEKS, MONTH, TWO_MONTHS, QUARTER)

    fun label(days: Int): String = when (days) {
        WEEK -> "每周"
        TWO_WEEKS -> "每两周"
        MONTH -> "每月"
        TWO_MONTHS -> "每两个月"
        QUARTER -> "每季度"
        else -> "每 " + days + " 天"
    }
}

/** 联系周期计划（含创建时间，提醒调度用）。 */
data class ContactPlan(
    val personId: String,
    val intervalDays: Int,
    val createdAt: Long
)

/** 联系计划与记录（FAMILY_AFFAIRS 联系提醒，永久免费）。 */
interface ContactRepository {
    fun observePlans(): Flow<Map<String, Int>>
    fun observePlanList(): Flow<List<ContactPlan>>
    fun observeLogs(personId: String): Flow<List<ContactLog>>
    fun observeAllLogs(): Flow<List<ContactLog>>
    suspend fun getPlans(): Map<String, Int>
    suspend fun getPlanList(): List<ContactPlan>
    suspend fun getLogs(): List<ContactLog>
    /** 设定联系周期（天）；null 关闭。 */
    suspend fun setPlan(personId: String, intervalDays: Int?)
    /** 记一次联系。 */
    suspend fun logContact(
        personId: String,
        method: ContactMethod,
        note: String?,
        atMillis: Long
    ): FamilyResult<Unit>
    suspend fun deleteLog(id: String)
    /** 计划创建时间（从未联系时起算）。 */
    suspend fun getPlanCreatedAt(personId: String): Long?
}

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val ids: IdGenerator,
    private val clock: TimeSource
) : ContactRepository {

    private val logDao get() = db.contactLogDao()
    private val planDao get() = db.contactPlanDao()

    override fun observePlans(): Flow<Map<String, Int>> =
        planDao.observeAll().map {
            it.associate { p -> p.personId to p.intervalDays }
        }

    override fun observePlanList(): Flow<List<ContactPlan>> =
        planDao.observeAll().map { l ->
            l.map { ContactPlan(it.personId, it.intervalDays, it.createdAt) }
        }

    override fun observeLogs(personId: String): Flow<List<ContactLog>> =
        logDao.observeForPerson(personId).map { l -> l.map { it.toModel() } }

    override fun observeAllLogs(): Flow<List<ContactLog>> =
        logDao.observeAll().map { l -> l.map { it.toModel() } }

    override suspend fun getPlans(): Map<String, Int> =
        planDao.getAll().associate { it.personId to it.intervalDays }

    override suspend fun getPlanList(): List<ContactPlan> =
        planDao.getAll().map { ContactPlan(it.personId, it.intervalDays, it.createdAt) }

    override suspend fun getLogs(): List<ContactLog> =
        logDao.getAll().map { it.toModel() }

    override suspend fun setPlan(personId: String, intervalDays: Int?) {
        val now = clock.now()
        // 读取旧 createdAt 与写回同事务，避免与并发删除/重设交错
        db.withTransaction {
            if (intervalDays == null) {
                planDao.delete(personId)
            } else {
                // 保留原 createdAt（重设周期不重置起算语义里的“从未联系”基准）
                val created = planDao.get(personId)?.createdAt ?: now
                planDao.upsert(
                    ContactPlanEntity(personId, intervalDays, created, now))
            }
        }
    }

    override suspend fun logContact(
        personId: String,
        method: ContactMethod,
        note: String?,
        atMillis: Long
    ): FamilyResult<Unit> {
        // 人物存在性校验与插入同事务；并发删人报明确错误而非外键异常逃逸
        return try {
            db.withTransaction {
                if (db.personDao().getById(personId) == null) {
                    return@withTransaction FamilyResult.Failure(FamilyError.PersonNotFound)
                }
                logDao.insert(
                    ContactLogEntity(
                        id = ids.newId(), personId = personId,
                        atMillis = atMillis, method = method.name,
                        note = note?.trim()?.takeIf { it.isNotBlank() }
                            ?.take(100),
                        createdAt = clock.now()
                    )
                )
                FamilyResult.Success(Unit)
            }
        } catch (e: SQLiteConstraintException) {
            FamilyResult.Failure(FamilyError.PersonNotFound)
        }
    }

    override suspend fun deleteLog(id: String) = logDao.delete(id)

    override suspend fun getPlanCreatedAt(personId: String): Long? =
        planDao.get(personId)?.createdAt
}

private fun ContactLogEntity.toModel() = ContactLog(
    id = id, personId = personId, atMillis = atMillis,
    method = runCatching { ContactMethod.valueOf(method) }
        .getOrDefault(ContactMethod.OTHER),
    note = note
)
