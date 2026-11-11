package io.github.xiaohaoz1.jiarenxia.core.model

/**
 * 宝宝满月 / 百天庆祝（FAMILY_AFFAIRS 26 第 3 批）。
 * 存 baby_celebrations.kind（TEXT）；备份遇到不认识的 kind 跳过该条。
 */
enum class BabyCelebrationKind { FULL_MONTH, HUNDRED_DAYS }

/** 一条宝宝庆祝日期覆盖（baby_celebrations 行；epochDay 为事件日）。 */
data class BabyCelebration(
    val personId: String,
    val kind: BabyCelebrationKind,
    val epochDay: Long
)
