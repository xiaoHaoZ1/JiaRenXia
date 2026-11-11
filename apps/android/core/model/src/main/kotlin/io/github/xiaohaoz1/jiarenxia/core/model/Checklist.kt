package io.github.xiaohaoz1.jiarenxia.core.model

/**
 * 清单类型（FAMILY_AFFAIRS 26）：待产包、出生后要办的事。
 * kind 存 TEXT 便于扩展；备份遇到不认识的 kind 跳过该清单（不 fail）。
 */
enum class ChecklistKind { BIRTH_BAG, POSTNATAL }

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
 * [personId]：出生后要办的事按宝宝/孕妈写；待产包条目恒 null。
 * [taskId]：「定日期」转成的家庭事项 id（无日期为 null）。
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
    val taskId: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
