package io.github.xiaohaoz1.jiarenxia.data

import androidx.room.withTransaction
import io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.ContactLogEntity
import io.github.xiaohaoz1.jiarenxia.data.db.HomeVisitMemberEntity
import io.github.xiaohaoz1.jiarenxia.data.db.HomeVisitPlanEntity
import io.github.xiaohaoz1.jiarenxia.data.db.toModel
import io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 回家周期可选值（天）：每月/每两个月/每季度/每半年/每年。 */
object HomeIntervals {
    const val MONTH = 30
    const val TWO_MONTHS = 60
    const val QUARTER = 90
    const val HALF_YEAR = 180
    const val YEAR = 365
    val ALL = listOf(MONTH, TWO_MONTHS, QUARTER, HALF_YEAR, YEAR)

    fun label(days: Int): String = when (days) {
        MONTH -> "每月"
        TWO_MONTHS -> "每两个月"
        QUARTER -> "每季度"
        HALF_YEAR -> "每半年"
        YEAR -> "每年"
        else -> "每 " + days + " 天"
    }
}

/** 一个“家”：一组成员 + 回家周期（成员见面记录算回过家）。 */
data class HomePlan(
    val id: String,
    val memberIds: List<String>,
    val intervalDays: Int,
    val createdAt: Long
)

/** 回家看看计划与记录（FAMILY_AFFAIRS 20，永久免费）。 */
interface HomeVisitRepository {
    fun observeHomes(): Flow<List<HomePlan>>
    suspend fun getHomes(): List<HomePlan>
    /** 新建一个家，返回计划 id。 */
    suspend fun addHome(memberIds: List<String>, intervalDays: Int): String
    suspend fun updateMembers(id: String, memberIds: List<String>)
    suspend fun updateInterval(id: String, intervalDays: Int)
    /** 删除计划；已记下的见面记录保留（contact_logs 不动）。 */
    suspend fun deleteHome(id: String)
    /**
     * 记一次回家：给该家每位“在世且非本人”的成员各插一条
     * VISIT 联系记录（note=null），同一事务。
     */
    suspend fun logHomeVisit(id: String, atMillis: Long)
}

@Singleton
class HomeVisitRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val ids: IdGenerator,
    private val clock: TimeSource
) : HomeVisitRepository {

    private val planDao get() = db.homeVisitPlanDao()
    private val memberDao get() = db.homeVisitMemberDao()

    override fun observeHomes(): Flow<List<HomePlan>> =
        combine(planDao.observeAll(), memberDao.observeAll()) { plans, members ->
            toPlans(plans, members)
        }

    override suspend fun getHomes(): List<HomePlan> =
        toPlans(planDao.getAll(), memberDao.getAll())

    /** 计划 + 成员行 → HomePlan 列表（按创建时间排序，成员保持插入序）。 */
    private fun toPlans(
        plans: List<HomeVisitPlanEntity>,
        members: List<HomeVisitMemberEntity>
    ): List<HomePlan> {
        val byPlan = members.groupBy { it.planId }
        return plans.sortedBy { it.createdAt }.map { p ->
            HomePlan(
                id = p.id,
                memberIds = byPlan[p.id].orEmpty().map { it.personId },
                intervalDays = p.intervalDays,
                createdAt = p.createdAt
            )
        }
    }

    /** 只保留库里真实存在的人物 id（防外键异常；去重保序）。 */
    private suspend fun existingIds(memberIds: List<String>): List<String> {
        val out = ArrayList<String>()
        memberIds.distinct().forEach { pid ->
            if (db.personDao().getById(pid) != null) out += pid
        }
        return out
    }

    override suspend fun addHome(memberIds: List<String>, intervalDays: Int): String {
        val now = clock.now()
        val id = ids.newId()
        db.withTransaction {
            planDao.insert(
                HomeVisitPlanEntity(id, intervalDays, now, now))
            existingIds(memberIds).forEach { pid ->
                memberDao.insertAll(
                    listOf(HomeVisitMemberEntity(planId = id, personId = pid)))
            }
        }
        return id
    }

    override suspend fun updateMembers(id: String, memberIds: List<String>) {
        db.withTransaction {
            // 先确认计划还在：并发删除后不写成员（避免撞外键）
            val plan = planDao.get(id) ?: return@withTransaction
            memberDao.deleteForPlan(id)
            existingIds(memberIds).forEach { pid ->
                memberDao.insertAll(
                    listOf(HomeVisitMemberEntity(planId = id, personId = pid)))
            }
            planDao.update(plan.copy(updatedAt = clock.now()))
        }
    }

    override suspend fun updateInterval(id: String, intervalDays: Int) {
        db.withTransaction {
            planDao.get(id)?.let {
                planDao.update(it.copy(intervalDays = intervalDays, updatedAt = clock.now()))
            }
        }
    }

    override suspend fun deleteHome(id: String) {
        db.withTransaction {
            // 成员行外键也会级联；显式先删保证顺序清晰
            memberDao.deleteForPlan(id)
            planDao.delete(id)
        }
    }

    override suspend fun logHomeVisit(id: String, atMillis: Long) {
        db.withTransaction {
            val plan = planDao.get(id) ?: return@withTransaction
            val memberIds = memberDao.getForPlan(id).map { it.personId }.toSet()
            if (memberIds.isEmpty()) return@withTransaction
            memberIds.forEach { pid ->
                val person = db.personDao().getById(pid)?.toModel() ?: return@forEach
                // 与联系提醒同一口径：只给在世、非本人的成员记见面
                if (!ContactSchedule.eligible(person)) return@forEach
                db.contactLogDao().insert(
                    ContactLogEntity(
                        id = ids.newId(), personId = pid,
                        atMillis = atMillis, method = ContactMethod.VISIT.name,
                        note = null, createdAt = clock.now()
                    )
                )
            }
        }
    }
}
