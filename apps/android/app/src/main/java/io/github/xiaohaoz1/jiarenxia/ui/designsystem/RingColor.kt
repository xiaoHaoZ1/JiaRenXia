package io.github.xiaohaoz1.jiarenxia.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.xiaohaoz1.jiarenxia.domain.AvatarSide
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.ringSideOf
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors

/**
 * 侧别 → 环色（SELF/CHILDREN 用朱砂，NONE 无环）。
 * 「家书」改版后主色是墨青，这里不跟主色：沿用改版前的红色，侧别读法不变。
 */
@Composable
fun ringColorOf(o: PersonOverview, byId: Map<String, PersonOverview>): Color? {
    val jrx = LocalJrxColors.current
    return when (ringSideOf(o, byId)) {
        AvatarSide.PATERNAL -> jrx.paternal
        AvatarSide.MATERNAL -> jrx.maternal
        AvatarSide.SPOUSE -> jrx.spouseSide
        AvatarSide.CHILDREN, AvatarSide.SELF -> jrx.seal
        AvatarSide.NONE -> null
    }
}
