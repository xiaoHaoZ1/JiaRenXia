package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType

/**
 * “其他家人”分组（DEVELOPMENT_PLAN 家庭页改版）。
 * 依据称谓推导路径的第一步分组，组内保持 FamilyOrdering 的顺序。
 * 核心家人不进入这里（调用方传入的 others 已排除）。
 */
object FamilyGrouping {

    /** 分组（展示顺序即枚举顺序）。 */
    enum class OtherGroup(val label: String) {
        SIBLINGS("兄弟姐妹"),           // 第一步 SIBLING 且路径长度为 1
        COUSINS("堂表兄弟姐妹"),         // 第一步 COUSIN（申报堂表亲，无真实路径）
        SIBLING_FAMILY("兄弟姐妹的家庭"), // 第一步 SIBLING，路径更长
        PATERNAL("父系亲属"),           // 第一步 PARENT 到男性（父亲侧）
        MATERNAL("母系亲属"),           // 第一步 PARENT 到女性（母亲侧）
        SPOUSE_FAMILY("配偶的亲属"),     // 第一步 SPOUSE
        CHILD_FAMILY("子女的家庭"),      // 第一步 CHILD
        UNRELATED("未关联到我")          // kind == UNRELATED，兜底也放这里
    }

    /** 单个人物归入哪一组。 */
    fun groupOf(o: PersonOverview, byId: Map<String, PersonOverview>): OtherGroup {
        if (o.result.kind == KinshipKind.UNRELATED) return OtherGroup.UNRELATED
        val first = o.result.relationshipPath.firstOrNull() ?: return OtherGroup.UNRELATED
        return when (first.type) {
            RelationshipType.SIBLING ->
                if (o.result.relationshipPath.size == 1) OtherGroup.SIBLINGS
                else OtherGroup.SIBLING_FAMILY
            RelationshipType.PARENT -> {
                val parent = byId[first.personId]?.person
                if (parent?.gender == Gender.FEMALE) OtherGroup.MATERNAL
                else OtherGroup.PATERNAL
            }
            RelationshipType.SPOUSE -> OtherGroup.SPOUSE_FAMILY
            RelationshipType.CHILD -> OtherGroup.CHILD_FAMILY
            RelationshipType.COUSIN -> OtherGroup.COUSINS
        }
    }

    /** 分组并保序：返回非空分组 [(组, 成员)]，按枚举顺序。 */
    fun group(
        others: List<PersonOverview>,
        byId: Map<String, PersonOverview>
    ): List<Pair<OtherGroup, List<PersonOverview>>> =
        OtherGroup.entries.mapNotNull { g ->
            val members = others.filter { groupOf(it, byId) == g }
            if (members.isEmpty()) null else g to members
        }
}
