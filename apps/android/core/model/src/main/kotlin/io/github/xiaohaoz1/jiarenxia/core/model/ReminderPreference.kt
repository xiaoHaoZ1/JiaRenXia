package io.github.xiaohaoz1.jiarenxia.core.model

/**
 * 单个重要日期的提醒偏好。
 *
 * @param reminderOffsets 提前天数集合：0 表示当天提醒，1 表示提前一天
 */
data class ReminderPreference(
    val id: String,
    val importantDateId: String,
    val enabled: Boolean = true,
    val reminderOffsets: Set<Int> = DEFAULT_OFFSETS,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    companion object {
        /** V0.1 默认提醒：当天 + 提前 1 天。 */
        val DEFAULT_OFFSETS: Set<Int> = setOf(0, 1)

        /** 各日期类型的默认提前天数：忌日 {3,0}、纪念日 {1,0}、生日 {0,1}。 */
        fun defaultOffsetsFor(type: ImportantDateType): Set<Int> = when (type) {
            ImportantDateType.DEATH_ANNIVERSARY -> setOf(3, 0)
            ImportantDateType.WEDDING_ANNIVERSARY,
            ImportantDateType.LICENSE_ANNIVERSARY,
            ImportantDateType.DATING_ANNIVERSARY -> setOf(1, 0)
            else -> DEFAULT_OFFSETS
        }
    }
}
