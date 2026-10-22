package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.domain.pinyin.PinyinIndex

/**
 * 人物选择器统一条目（真机修正：选择器行显示“姓名（称谓）”）：
 * - “我”显示为“姓名（我）”；无外推称谓者只显示姓名；已故追加“（已故）”。
 * - 排序：FamilyOrdering 核心家人分组顺序，组内按拼音。
 */
data class PersonPickItem(
    val personId: String,
    /** 姓名（用于写入 counterpartName 等原文场景）。 */
    val name: String,
    /** 展示标签：“姓名（称谓）” / “姓名（我）” / “姓名（已故）”。 */
    val label: String,
    /** 引擎结果（搜索/排序用；测试夹具可留空）。 */
    val overview: PersonOverview? = null
)

object PersonPickList {

    /**
     * 由 FamilyOverview 生成选择器条目。
     * [filter] 过滤可选项（如记一笔排除“我”与配偶）。
     */
    fun build(
        overview: FamilyOverview?,
        filter: (PersonOverview) -> Boolean = { true }
    ): List<PersonPickItem> {
        val ov = overview ?: return emptyList()
        return ov.byId.values
            .filter(filter)
            .map { o ->
                val name = o.person.name
                val deceased = o.person.livingStatus == LivingStatus.DECEASED
                val title = when {
                    o.result.kind == KinshipKind.SELF -> "我"
                    o.result.kind == KinshipKind.UNRELATED -> null
                    else -> o.displayTitle
                }
                // 已故并入括号：“杨福生（爷爷 · 已故）”；无称谓者“X（已故）”
                val inside = listOfNotNull(title,
                    if (deceased) "已故" else null).joinToString(" · ")
                PersonPickItem(
                    personId = o.person.id,
                    name = name,
                    label = name + if (inside.isNotEmpty())
                        "（" + inside + "）" else "",
                    overview = o
                )
            }
            .sortedWith { a, b ->
                val ga = a.overview?.result?.coreGroup
                    ?.let { FamilyOrdering.groupRankOf(it) } ?: 99
                val gb = b.overview?.result?.coreGroup
                    ?.let { FamilyOrdering.groupRankOf(it) } ?: 99
                if (ga != gb) ga - gb
                else PinyinIndex.of(a.name).full.compareTo(PinyinIndex.of(b.name).full)
            }
    }
}
