package io.github.xiaohaoz1.jiarenxia.ui.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Man
import androidx.compose.material.icons.rounded.Woman
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole

/** 角色 → 图标：配偶用心形，其余按性别取人像。 */
fun roleIcon(role: RelationRole): ImageVector = when (role) {
    RelationRole.HUSBAND, RelationRole.WIFE -> Icons.Rounded.Favorite
    else -> if (role.gender == Gender.FEMALE) Icons.Rounded.Woman else Icons.Rounded.Man
}
