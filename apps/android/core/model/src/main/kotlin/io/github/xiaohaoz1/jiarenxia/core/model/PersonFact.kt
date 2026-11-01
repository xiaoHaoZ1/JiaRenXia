package io.github.xiaohaoz1.jiarenxia.core.model

import java.time.LocalDate

/** 「关于TA」分类（FAMILY_AFFAIRS §18）；label 为界面显示名，顺序即卡片显示顺序。 */
enum class FactCategory(val label: String) {
    SIZE("尺码"),
    FOOD_LIKE("爱吃"),
    FOOD_AVOID("忌口"),
    HEALTH("过敏与身体"),
    HOBBY("爱好"),
    WISH("想要的"),
    CUSTOM("自定义");

    /** 需要小标题的分类：尺码（鞋码/上衣…）与自定义（分类名）。 */
    val needsLabel: Boolean get() = this == SIZE || this == CUSTOM
}

/** 尺码预设项，顺序即显示顺序。 */
val FACT_SIZE_LABELS: List<String> = listOf("鞋码", "上衣", "裤子", "戒指")

/** 内容录入上限。 */
const val FACT_CONTENT_MAX = 40

/** 小标题录入上限。 */
const val FACT_LABEL_MAX = 8

/** 「关于TA」的一条记录。recordedOn = 最近一次新建或修改内容的日期。 */
data class PersonFact(
    val id: String,
    val personId: String,
    val category: FactCategory,
    /** SIZE：尺码项名；CUSTOM：分类名；其他分类恒为 null。 */
    val label: String?,
    val content: String,
    val recordedOn: LocalDate,
    val createdAt: Long,
    val updatedAt: Long
)
