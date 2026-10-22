package io.github.xiaohaoz1.jiarenxia.core.model

/**
 * 基础关系类型。
 *
 * CHILD 只是领域层 / 路径方向的写法，数据库中不物理存储：
 * 父母子女事实统一以反向 PARENT 保存（见 DATABASE_SCHEMA 7.10）。
 *
 * COUSIN 是“申报堂表亲”记录（KINSHIP_ENGINE 37.1 修订）：用户在堂/表
 * 分支录入了两人是堂表亲并声明长幼，但堂表支系的真实路径是
 * “我 → 父母 → 父母的兄弟姐妹 → 堂表亲”，COUSIN 记录本身**不参与**
 * 路径遍历，只携带相对长幼供堂表称谓规则优先采用；
 * 无真实路径时兜底给出“堂表兄弟/姐妹”通用结果并接入 0 代。
 * 存储规范化与 SIBLING 相同：source < target，relativeAgeOrder
 * 表示 source 相对 target 的长幼。
 */
enum class RelationshipType { PARENT, CHILD, SPOUSE, SIBLING, COUSIN }

/**
 * 长幼顺序，仅用于 SIBLING / COUSIN 关系，表示 source 相对 target 的长幼。
 */
enum class RelativeAgeOrder {
    OLDER, YOUNGER, SAME;

    /** 取反：视角从另一端看时的长幼顺序。 */
    fun inverse(): RelativeAgeOrder = when (this) {
        OLDER -> YOUNGER
        YOUNGER -> OLDER
        SAME -> SAME
    }
}

/**
 * 一条直接关系记录。
 *
 * 语义约定：A PARENT B 表示“A 是 B 的父母”；relativeAgeOrder 仅出现在
 * SIBLING / COUSIN 上，表示 source 相对 target 的长幼。
 */
data class Relationship(
    val id: String,
    val sourcePersonId: String,
    val targetPersonId: String,
    val type: RelationshipType,
    val relativeAgeOrder: RelativeAgeOrder? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
