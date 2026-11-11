package io.github.xiaohaoz1.jiarenxia.core.model

/**
 * 清单类型（FAMILY_AFFAIRS 26）：本批只有待产包。
 * 后续批次可扩展（出生后要办的事等），kind 存 TEXT 便于扩展。
 */
enum class ChecklistKind { BIRTH_BAG }

/**
 * 一张清单（挂孕期或人物；本批由孕期生成待产包）。
 * [personId] 为清单归属人物（孕妈），[pregnancyId] 为来源孕期
 * （孕期删除时 SET NULL，清单保留）。
 */
data class Checklist(
    val id: String,
    val kind: ChecklistKind,
    val title: String,
    val personId: String?,
    val pregnancyId: String?,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

/**
 * 清单条目：组内顺序 = 模板顺序，组内再按 [sortOrder]。
 * [personId] 为条目关联人物（第 3 批按宝宝用；本批恒为孕妈/空）。
 */
data class ChecklistItem(
    val id: String,
    val checklistId: String,
    val groupName: String,
    val title: String,
    val note: String?,
    val checked: Boolean = false,
    val sortOrder: Int,
    val personId: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
