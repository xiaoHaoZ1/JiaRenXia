package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.CoreGroup
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType

/**
 * 头像侧别环：按与“我”的亲缘侧取色（纯函数，可单测）。
 * PATERNAL=父系(主色)，MATERNAL=母系，SPOUSE=配偶侧，CHILDREN/SELF=主色，NONE=无环。
 */
enum class AvatarSide { SELF, PATERNAL, MATERNAL, SPOUSE, CHILDREN, NONE }

/** 由称谓结果推断头像环的侧别；非核心家人按路径第一步推断。 */
fun ringSideOf(o: PersonOverview, byId: Map<String, PersonOverview>): AvatarSide {
    if (o.person.isSelf || o.result.kind == KinshipKind.SELF) return AvatarSide.SELF
    when (o.result.coreGroup) {
        CoreGroup.PARENTS, CoreGroup.GRANDPARENTS -> {
            val parent = o.result.relationshipPath.firstOrNull()
                ?.let { byId[it.personId]?.person }
            return if (parent?.gender == Gender.FEMALE) AvatarSide.MATERNAL else AvatarSide.PATERNAL
        }
        CoreGroup.SPOUSE, CoreGroup.SPOUSE_PARENTS -> return AvatarSide.SPOUSE
        CoreGroup.CHILDREN -> return AvatarSide.CHILDREN
        null -> Unit
    }
    // 非核心：按路径第一步近似归类
    val first = o.result.relationshipPath.firstOrNull() ?: return AvatarSide.NONE
    return when (first.type) {
        RelationshipType.PARENT ->
            if (byId[first.personId]?.person?.gender == Gender.FEMALE) AvatarSide.MATERNAL
            else AvatarSide.PATERNAL
        RelationshipType.SPOUSE -> AvatarSide.SPOUSE
        RelationshipType.CHILD -> AvatarSide.CHILDREN
        // 申报堂表亲与亲兄妹一样不挂亲缘侧环
        RelationshipType.SIBLING, RelationshipType.COUSIN -> AvatarSide.NONE
    }
}
