package io.github.xiaohaoz1.jiarenxia.domain.med

import io.github.xiaohaoz1.jiarenxia.core.model.MedMeal
import io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
import io.github.xiaohaoz1.jiarenxia.core.model.Medication
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 用药记录的状态与文案（FAMILY_AFFAIRS 23，纯 JVM）。
 *
 * 口径：App 只知道「记录」，不知道「实际服用」——天数一律按
 * [Medication.startEpochDay] 推算，文案不得出现「已经吃了 N 天」
 * 「已停药」这类服药事实。
 */
object MedSchedule {

    enum class Status { ACTIVE, COURSE_ENDED, STOPPED }

    /** 今日到期事件类型：疗程最后一天 / 续药提醒。 */
    enum class DueKind { COURSE_END, REFILL }

    /** 今日到期的一条提醒（epochDay 为事件所属日，daysBefore 为提前天数）。 */
    data class Due(
        val kind: DueKind,
        /** 事件日：疗程最后一天或拿药日。 */
        val epochDay: Long,
        /** 提前天数：疗程到期恒 0；续药为 3 或 0。 */
        val daysBefore: Int
    )

    /** 卡片/「给医生看」共用的两行摘要。 */
    data class Summary(
        val status: Status,
        /** SHORT_COURSE 且未填疗程天数（且未停用）。 */
        val coursePending: Boolean,
        /** today − start + 1；today 早于开始日为 null。 */
        val dayNumber: Int?,
        /** 短期疗程最后一天（start + N − 1）；无天数或非短期为 null。 */
        val lastDay: LocalDate?,
        /** 长期吃的下次拿药日；其他为 null。 */
        val refillDay: LocalDate?,
        /** 第一行主摘要（逐字文案见规格）。 */
        val mainLine: String,
        /** 第二行（有才拼，" · " 连接）；无内容为 null。 */
        val detailLine: String?
    )

    /** 记录状态：已停用 > 疗程到期 > 进行中。 */
    fun statusOf(med: Medication, today: LocalDate): Status {
        if (med.stoppedEpochDay != null) return Status.STOPPED
        if (med.usage == MedUsage.SHORT_COURSE && med.courseDays != null) {
            val last = lastDayOf(med)
            if (today.isAfter(last)) return Status.COURSE_ENDED
        }
        return Status.ACTIVE
    }

    /** 短期待确认：SHORT_COURSE、未停用、未填疗程天数。 */
    fun coursePending(med: Medication): Boolean =
        med.usage == MedUsage.SHORT_COURSE && med.courseDays == null &&
            med.stoppedEpochDay == null

    /** 出生第几天式的用药天数：today − start + 1；未开始为 null。 */
    fun dayNumber(med: Medication, today: LocalDate): Int? {
        val start = LocalDate.ofEpochDay(med.startEpochDay)
        if (today.isBefore(start)) return null
        return ChronoUnit.DAYS.between(start, today).toInt() + 1
    }

    /** 短期疗程最后一天（start + N − 1）；非短期或无天数为 null。 */
    fun lastDayOf(med: Medication): LocalDate? {
        val n = med.courseDays ?: return null
        if (med.usage != MedUsage.SHORT_COURSE) return null
        return LocalDate.ofEpochDay(med.startEpochDay).plusDays((n - 1).toLong())
    }

    /** 长期吃的下次拿药日；其他用法为 null。 */
    fun refillDayOf(med: Medication): LocalDate? =
        med.refillEpochDay?.let { LocalDate.ofEpochDay(it) }
            ?.takeIf { med.usage == MedUsage.LONG_TERM }

    /** 「M月D日」短日期（不带年份）。 */
    fun shortDate(day: LocalDate): String = "${day.monthValue}月${day.dayOfMonth}日"

    /** 主摘要 + 第二行（规格 §3 逐字）。 */
    fun summaryOf(med: Medication, today: LocalDate): Summary {
        val status = statusOf(med, today)
        val pending = coursePending(med)
        val dayNum = dayNumber(med, today)
        val lastDay = lastDayOf(med)
        val refillDay = refillDayOf(med)
        val main = when {
            status == Status.STOPPED -> "记录已停用"
            med.usage == MedUsage.SHORT_COURSE && pending ->
                "短期疗程 · 疗程待确认，请向医生或药师核对"
            med.usage == MedUsage.SHORT_COURSE && med.courseDays != null -> {
                val n = med.courseDays
                if (status == Status.COURSE_ENDED) {
                    "记录疗程已到期（$n 天），实际停用待确认"
                } else if (dayNum == null) {
                    "短期疗程 · 记录疗程 $n 天，" +
                        shortDate(LocalDate.ofEpochDay(med.startEpochDay)) + "开始"
                } else {
                    "短期疗程 · 记录疗程 $n 天，按开始日期计算今天是第 $dayNum 天"
                }
            }
            med.usage == MedUsage.LONG_TERM ->
                if (refillDay != null) "长期吃 · 下次拿药 ${shortDate(refillDay)}"
                else "长期吃"
            else -> "需要时吃"
        }
        return Summary(
            status = status,
            coursePending = pending,
            dayNumber = dayNum,
            lastDay = lastDay,
            refillDay = refillDay,
            mainLine = main,
            detailLine = detailLineOf(med)
        )
    }

    /** 第二行：每次用量 · 规格 · 时刻 · 饭前/饭后/随餐（有才拼）。 */
    fun detailLineOf(med: Medication): String? {
        val parts = mutableListOf<String>()
        med.dose?.takeIf { it.isNotBlank() }?.let { parts += "每次 $it" }
        med.spec?.takeIf { it.isNotBlank() }?.let { parts += it }
        if (med.times.isNotEmpty()) {
            parts += med.times.sorted().joinToString("、") { minute ->
                "%02d:%02d".format(minute / 60, minute % 60)
            }
        }
        med.meal?.let { m ->
            parts += when (m) {
                MedMeal.BEFORE -> "饭前"
                MedMeal.AFTER -> "饭后"
                MedMeal.WITH -> "随餐"
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /**
     * 「长期吃」确认弹窗判定（规格 §6.2）：新建（[originalUsage] 为 null）
     * 或编辑时把「怎么吃」从别的用法改成「长期吃」→ 需要先确认；
     * 已是长期吃的普通编辑不弹。
     */
    fun needsLongTermConfirm(
        originalUsage: MedUsage?,
        selectedUsage: MedUsage?
    ): Boolean = selectedUsage == MedUsage.LONG_TERM &&
        originalUsage != MedUsage.LONG_TERM

    /**
     * 今日到期提醒集合（规格 §3 末）：
     * - 疗程到期：SHORT_COURSE 有天数、未停用、today == 最后一天；
     * - 续药：LONG_TERM、未停用、有下次拿药日、today == 拿药日−3 或 == 拿药日；
     * - SHORT_COURSE（含待确认）与 AS_NEEDED 永远不产生续药。
     */
    fun duesOn(med: Medication, today: LocalDate): List<Due> {
        if (med.stoppedEpochDay != null) return emptyList()
        val dues = mutableListOf<Due>()
        if (med.usage == MedUsage.SHORT_COURSE && med.courseDays != null) {
            lastDayOf(med)?.let { last ->
                if (today == last) {
                    dues += Due(DueKind.COURSE_END, last.toEpochDay(), 0)
                }
            }
        }
        if (med.usage == MedUsage.LONG_TERM) {
            refillDayOf(med)?.let { refill ->
                val diff = ChronoUnit.DAYS.between(today, refill)
                if (diff == 3L) dues += Due(DueKind.REFILL, refill.toEpochDay(), 3)
                if (diff == 0L) dues += Due(DueKind.REFILL, refill.toEpochDay(), 0)
            }
        }
        return dues
    }
}
