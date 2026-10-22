package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.ContactLog
import io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** 测试用内存 ContactRepository（联系提醒）。 */
class FakeContactRepository : ContactRepository {
    val plans = MutableStateFlow(mutableMapOf<String, Int>())
    val planCreated = mutableMapOf<String, Long>()
    val logs = MutableStateFlow(mutableMapOf<String, MutableList<ContactLog>>())

    override fun observePlans(): Flow<Map<String, Int>> = plans

    override fun observePlanList(): Flow<List<ContactPlan>> =
        plans.map { m ->
            m.map { (pid, d) -> ContactPlan(pid, d, planCreated[pid] ?: 0L) }
        }

    override fun observeLogs(personId: String): Flow<List<ContactLog>> =
        logs.map { it[personId].orEmpty() }

    override fun observeAllLogs(): Flow<List<ContactLog>> =
        logs.map { m -> m.values.flatten() }

    override suspend fun getPlans(): Map<String, Int> = plans.value

    override suspend fun getPlanList(): List<ContactPlan> =
        plans.value.map { (pid, d) -> ContactPlan(pid, d, planCreated[pid] ?: 0L) }

    override suspend fun getLogs(): List<ContactLog> =
        logs.value.values.flatten()

    override suspend fun setPlan(personId: String, intervalDays: Int?) {
        val m = plans.value.toMutableMap()
        if (intervalDays == null) m.remove(personId) else m[personId] = intervalDays
        plans.value = m
    }

    override suspend fun logContact(
        personId: String, method: ContactMethod, note: String?, atMillis: Long
    ): FamilyResult<Unit> {
        val m = logs.value.toMutableMap()
        m.getOrPut(personId) { mutableListOf() }
            .add(
                ContactLog(
                    "log-" + (m.values.sumOf { it.size } + 1),
                    personId, atMillis, method,
                    note?.trim()?.takeIf { it.isNotBlank() }?.take(100)
                )
            )
        logs.value = m
        return FamilyResult.Success(Unit)
    }

    override suspend fun deleteLog(id: String) {
        val m = logs.value.toMutableMap()
        m.values.forEach { it.removeAll { l -> l.id == id } }
        logs.value = m
    }

    override suspend fun getPlanCreatedAt(personId: String): Long? =
        planCreated[personId]
}
