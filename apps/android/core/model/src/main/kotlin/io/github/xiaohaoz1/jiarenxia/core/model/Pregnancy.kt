package io.github.xiaohaoz1.jiarenxia.core.model

import java.time.LocalDate

/** 孕期状态：进行中 / 宝宝已出生 / 已结束（FAMILY_AFFAIRS 26）。 */
enum class PregnancyStatus { ACTIVE, BORN, ENDED }

/**
 * 孕期记录：挂在一位女性人物名下的一次怀孕。
 *
 * [dueDate] 为推导或录入的预产期；[lmpDate] 为末次月经第一天（可空，
 * 预产期 = 末次月经 + 280 天，见 PregnancyMath）。
 * [endedOn] 在 BORN / ENDED 时记录结束日期，ACTIVE 时为 null。
 * [babyIds] 为关联宝宝人物 id 列表（本批只建数据结构，不提供写入入口；
 * 一对多覆盖双胞胎）。
 */
data class Pregnancy(
    val id: String,
    val personId: String,
    val dueDate: LocalDate,
    val lmpDate: LocalDate?,
    val status: PregnancyStatus,
    val endedOn: LocalDate?,
    val babyIds: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
