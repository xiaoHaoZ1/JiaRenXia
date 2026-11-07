package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 测试用内存 HomeVisitRepository（回家看看）。
 * [people] 提供“在世且非本人”判定（logHomeVisit 只给有效成员记见面）；
 * [onVisit] 非空时每次有效见面回调一次（personId, atMillis），
 * 测试可借此把 VISIT 记录写进 FakeContactRepository.logs。
 */
class FakeHomeVisitRepository(
    private val people: () -> List<Person> = { emptyList() },
    private val onVisit: ((personId: String, atMillis: Long) -> Unit)? = null,
    /** 计划 createdAt（毫秒）；默认当前时间，测试可回拨制造“从未见面已超期”。 */
    private val createdAt: () -> Long = { System.currentTimeMillis() }
) : HomeVisitRepository {

    private val _homes = MutableStateFlow<List<HomePlan>>(emptyList())
    private var seq = 0

    override fun observeHomes(): Flow<List<HomePlan>> = _homes

    override suspend fun getHomes(): List<HomePlan> = _homes.value

    override suspend fun addHome(memberIds: List<String>, intervalDays: Int): String {
        val id = "home-" + (++seq)
        _homes.value = _homes.value + HomePlan(
            id = id, memberIds = memberIds.distinct(),
            intervalDays = intervalDays, createdAt = createdAt())
        return id
    }

    override suspend fun updateMembers(id: String, memberIds: List<String>) {
        _homes.value = _homes.value.map {
            if (it.id == id) it.copy(memberIds = memberIds.distinct()) else it
        }
    }

    override suspend fun updateInterval(id: String, intervalDays: Int) {
        _homes.value = _homes.value.map {
            if (it.id == id) it.copy(intervalDays = intervalDays) else it
        }
    }

    override suspend fun deleteHome(id: String) {
        _homes.value = _homes.value.filter { it.id != id }
    }

    override suspend fun logHomeVisit(id: String, atMillis: Long) {
        val plan = _homes.value.firstOrNull { it.id == id } ?: return
        plan.memberIds.forEach { pid ->
            val person = people().firstOrNull { it.id == pid } ?: return@forEach
            if (!ContactSchedule.eligible(person)) return@forEach
            onVisit?.invoke(pid, atMillis)
        }
    }
}
