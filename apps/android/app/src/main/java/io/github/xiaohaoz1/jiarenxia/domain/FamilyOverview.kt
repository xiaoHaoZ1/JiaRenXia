package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * 单个人物的界面聚合：本人 + 推导称谓 + 生日。
 */
data class PersonOverview(
    val person: Person,
    val result: KinshipResult,
    /** 显示用：原始生日（含历法/闰月；阳历在前，最多两条）。 */
    val birthdays: List<BirthdayValue> = emptyList(),
    /** 长幼/排序用：双生日归并出的可确定公历出生日；冲突或未知为 null。 */
    val solarBirthday: PartialDate? = null,
    /** 自定义称呼（custom_titles，观察者为“我”）；覆盖显示，不影响推导。 */
    val customTitle: String? = null
) {
    /** 兼容旧单生日取法：第一个（阳历优先）。 */
    val birthday: BirthdayValue? get() = birthdays.firstOrNull()
    /** 显示优先级：自定义称呼 > 标准称谓 > 结构描述（DATABASE_SCHEMA 25.2）。 */
    val displayTitle: String get() = customTitle ?: result.displayTitle

    /**
     * “只记称呼”人物：有自定义称呼且与“我”没有任何关系路径。
     * 界面显示“关系待补充”；自定义称呼不参与血缘推导（DEVELOPMENT_PLAN 2.14）。
     */
    val titleOnly: Boolean
        get() = customTitle != null && result.kind == KinshipKind.UNRELATED
}

/**
 * 家庭页所需的整体视图：以“我”为观察者的全部称谓推导结果。
 * self 为“我”的聚合；core 为核心家人（有序）；others 为其余人物（有序）。
 */
data class FamilyOverview(
    val self: PersonOverview,
    val graph: FamilyGraph,
    val byId: Map<String, PersonOverview>,
    val core: List<PersonOverview>,
    val others: List<PersonOverview>
)

/**
 * 观察家庭整体视图：关系图变化 → 重新推导称谓并排序。
 * 没有“我”时输出 null（界面据此进入创建“我”的流程）。
 * 推导在 Dispatchers.Default 上进行。
 */
class ObserveFamilyOverview @Inject constructor(
    private val repository: FamilyRepository,
    private val engine: KinshipEngine
) {
    operator fun invoke(): Flow<FamilyOverview?> {
        // 称谓推导只在关系图变化时执行；distinctUntilChanged 防止无关表
        // 触发重建 FamilyGraph（data class 结构相等）时的重复推导
        val resolved = repository.observeFamilyGraph()
            .distinctUntilChanged()
            .map { graph ->
                val self = graph.persons.firstOrNull { it.isSelf }
                graph to self?.let { engine.resolveAll(graph, it.id) }
            }
        // 自定义称呼只影响展示：变化时复用推导结果重组装，
        // 不再重复执行 resolveAll
        return combine(resolved, repository.observeCustomTitles()) {
                (graph, results), titles ->
            build(graph, results, titles)
        }.flowOn(Dispatchers.Default)
    }

    private fun build(
        graph: FamilyGraph, results: Map<String, KinshipResult>?,
        customTitles: Map<String, String>
    ): FamilyOverview? {
        val self = graph.persons.firstOrNull { it.isSelf } ?: return null
        val results = results ?: return null
        val byId = graph.persons.associate { p ->
            p.id to PersonOverview(
                person = p,
                result = results.getValue(p.id),
                birthdays = graph.birthdayValues[p.id]
                    ?: graph.birthdays[p.id]?.let { listOf(BirthdayValue(it)) }
                        .orEmpty(),
                solarBirthday = graph.birthdays[p.id],
                customTitle = customTitles[p.id]
            )
        }
        val rest = byId.values.filter { it.person.id != self.id }
        return FamilyOverview(
            self = byId.getValue(self.id),
            graph = graph,
            byId = byId,
            core = FamilyOrdering.core(rest.filter { it.result.coreGroup != null }, byId),
            others = FamilyOrdering.others(rest.filter { it.result.coreGroup == null })
        )
    }
}
