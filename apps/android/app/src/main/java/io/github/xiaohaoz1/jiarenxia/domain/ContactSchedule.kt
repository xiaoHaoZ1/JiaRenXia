package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.model.ContactLog
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import java.time.LocalDate

/**
 * 联系提醒的到期计算（FAMILY_AFFAIRS 联系提醒），纯 JVM。
 *
 * - 最近联系 = 该人所有日志里 atMillis 的最大值；
 * - 起算日 = 最近联系日；从未联系过的用计划创建日；
 * - 逾期天数 = 今天 − 起算日 − 周期；≥0 即到期；
 * - 提醒时机：到期当天提醒一次；之后每 7 天再提醒一次。
 */
object ContactSchedule {

    /** 重复提醒间隔（天）。 */
    const val REPEAT_DAYS = 7

    /** “打完电话提示记录”的窗口：10 分钟内回到 App 才提示。 */
    const val PROMPT_WINDOW_MILLIS = 10L * 60 * 1000

    /** 拨号出去 atMillis 时刻，nowMillis 回来，是否仍在 10 分钟窗口内。 */
    fun promptVisible(callAtMillis: Long, nowMillis: Long): Boolean =
        nowMillis - callAtMillis in 0 until PROMPT_WINDOW_MILLIS

    /** 只提醒在世、非本人的家人；已故或“我”即使留有周期也不显示、不提醒。 */
    fun eligible(person: Person?): Boolean =
        person != null && !person.isSelf &&
            person.livingStatus == LivingStatus.ALIVE

    /**
     * 某人的联系状态。
     * @param lastAt 最近一次联系（毫秒），从未联系为 null
     * @param daysSinceLast 距上次联系的天数（从未联系为 null）
     * @param overdueDays 逾期天数，<0 未到期
     */
    data class Status(
        val lastAt: Long?,
        val daysSinceLast: Int?,
        val overdueDays: Int
    ) {
        val due: Boolean get() = overdueDays >= 0
    }

    /** 今天是否该提醒（到期当天、或之后每 REPEAT_DAYS 天）。 */
    fun remindToday(status: Status, today: LocalDate): Boolean =
        status.due && status.overdueDays % REPEAT_DAYS == 0

    /**
     * 计算状态。
     * @param logs 该人的联系记录（任意顺序）
     * @param intervalDays 周期；未设计划返回 null
     * @param planCreatedAt 计划创建时间（毫秒），从未联系时起算用
     */
    fun status(
        logs: List<ContactLog>,
        intervalDays: Int,
        planCreatedAt: Long,
        today: LocalDate
    ): Status {
        val lastAt = logs.maxOfOrNull { it.atMillis }
        val startDay = if (lastAt != null) {
            java.time.Instant.ofEpochMilli(lastAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        } else {
            java.time.Instant.ofEpochMilli(planCreatedAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        }
        val elapsed =
            java.time.temporal.ChronoUnit.DAYS.between(startDay, today).toInt()
        val overdue = elapsed - intervalDays
        return Status(
            lastAt = lastAt,
            daysSinceLast = if (lastAt != null) elapsed else null,
            overdueDays = overdue
        )
    }

    /**
     * 提醒文案：
     * “已经 15 天没联系妈妈了，打个电话吧”；
     * 从未联系过 → “还没有记录过和妈妈的联系”。
     */
    fun notifyText(status: Status, titleOrName: String): String =
        if (status.daysSinceLast == null) {
            "还没有记录过和" + titleOrName + "的联系，打个电话吧"
        } else {
            "已经 " + status.daysSinceLast + " 天没联系" + titleOrName +
                "了，打个电话吧"
        }
}
