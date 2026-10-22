package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType

/**
 * 路径中的一步。
 *
 * @param type 遍历方向：PARENT 表示向上到一位父母，CHILD 表示向下到一位子女
 * @param personId 这一步到达的人物
 * @param implicit 是否为隐式兄弟姐妹边（因共享父母而推导，无显式 SIBLING 记录）
 */
data class PathStep(
    val type: RelationshipType,
    val personId: String,
    val implicit: Boolean = false
)

/** 推导结果种类（DEVELOPMENT_PLAN 2.4 一）。 */
enum class KinshipKind { SELF, DERIVED, STRUCTURAL, UNRELATED }

/** 规则信息完整度：EXACT 信息完整；PARTIAL 路径明确但缺信息。 */
enum class Confidence { EXACT, PARTIAL }

/** 缺失信息说明。BIRTH_DATES：堂表亲长幼需双方完整生日（V0.2，KINSHIP_ENGINE 37）。 */
enum class MissingInformation { SIBLING_AGE_ORDER, NO_PATH, BIRTH_DATES }

/** 家庭页“核心家人”分组（DEVELOPMENT_PLAN 2.2）。 */
enum class CoreGroup { PARENTS, GRANDPARENTS, SPOUSE, CHILDREN, SPOUSE_PARENTS }

/**
 * 一次关系推导的逻辑结果（KINSHIP_ENGINE 3、28）。
 */
data class KinshipResult(
    val observerId: String,
    val targetId: String,
    val relationshipPath: List<PathStep>,
    val structuralDescription: String,
    val standardTitle: String?,
    val customTitle: String? = null,
    val confidence: Confidence,
    val missingInformation: Set<MissingInformation>,
    val kind: KinshipKind
) {
    /** 界面显示称谓：自定义称谓优先，其次标准称谓，最后退化为结构关系。 */
    val displayTitle: String
        get() = customTitle ?: standardTitle ?: structuralDescription

    /**
     * 所属“核心家人”分组；不含隐式兄弟姐妹步骤时才按路径类型判定，
     * 不在五类路径内（或存在隐式步骤、为空路径）返回 null。
     */
    val coreGroup: CoreGroup?
        get() {
            if (relationshipPath.any { it.implicit }) return null
            return when (relationshipPath.map { it.type }) {
                listOf(RelationshipType.PARENT) -> CoreGroup.PARENTS
                listOf(RelationshipType.PARENT, RelationshipType.PARENT) -> CoreGroup.GRANDPARENTS
                listOf(RelationshipType.SPOUSE) -> CoreGroup.SPOUSE
                listOf(RelationshipType.CHILD) -> CoreGroup.CHILDREN
                listOf(RelationshipType.SPOUSE, RelationshipType.PARENT) -> CoreGroup.SPOUSE_PARENTS
                else -> null
            }
        }
}
