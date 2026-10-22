package io.github.xiaohaoz1.jiarenxia.reminder

import io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask
import io.github.xiaohaoz1.jiarenxia.core.model.TaskKind
import io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat
import java.time.LocalDate

/**
 * 家庭事项排期（FAMILY_AFFAIRS 5.3、5.4）：
 * 当前期日期永远从首次日期按期数推算，月底/闰日不漂移；
 * 逾期事项不发通知，只在列表标红。
 */
object TaskSchedule {

    /** 第 [periodIndex] 期日期；NONE 恒为 anchor。 */
    fun dueDate(anchor: LocalDate, repeat: TaskRepeat, periodIndex: Int): LocalDate =
        when (repeat) {
            TaskRepeat.NONE -> anchor
            TaskRepeat.MONTHLY -> anchor.plusMonths(periodIndex.toLong())
            TaskRepeat.YEARLY -> anchor.plusYears(periodIndex.toLong())
        }

    /** 事项的当前期日期。 */
    fun dueDate(task: FamilyTask): LocalDate =
        dueDate(task.anchorDate, task.repeat, task.periodIndex)

    /** 距 today 的天数；逾期为负。 */
    fun daysUntil(due: LocalDate, today: LocalDate): Int =
        (due.toEpochDay() - today.toEpochDay()).toInt()

    fun daysUntil(task: FamilyTask, today: LocalDate): Int =
        daysUntil(dueDate(task), today)

    /**
     * 办完后的下一期数：至少推进一期；若已逾期多期，直接跳到今天之后的最近一期。
     * NONE 返回 null（由调用处置 done = true）。
     */
    fun nextPeriodIndex(task: FamilyTask, today: LocalDate): Int? {
        if (task.repeat == TaskRepeat.NONE) return null
        var next = task.periodIndex + 1
        while (dueDate(task.anchorDate, task.repeat, next) <= today) next++
        return next
    }

    /**
     * 到期通知集合：enabled 且未 done、daysUntil ∈ offsets 的事项；
     * 逾期（daysUntil < 0）不发。
     */
    fun dueNotifications(tasks: List<FamilyTask>, today: LocalDate): List<DueTask> =
        tasks.mapNotNull { t ->
            if (!t.enabled || t.done) return@mapNotNull null
            val days = daysUntil(t, today)
            if (days < 0 || days !in t.offsets) return@mapNotNull null
            DueTask(t, days)
        }

    /** 通知文案：当天“今天要办”、提前 1 天“明天要办”、其余“N 天后（M月D日）”。 */
    fun text(task: FamilyTask, daysUntil: Int): String = when (daysUntil) {
        0 -> "今天要办：" + task.title
        1 -> "明天要办：" + task.title
        else -> "" + daysUntil + " 天后（" + dueDate(task).monthValue + "月" +
            dueDate(task).dayOfMonth + "日）：" + task.title
    }
}

/** 一条要发的事项通知。 */
data class DueTask(val task: FamilyTask, val daysBefore: Int)

/**
 * 模板默认值（FAMILY_AFFAIRS 5.1 表）：重复周期、默认提前天数、提示文字、显示名。
 */
object TaskTemplates {

    data class Template(
        val kind: TaskKind,
        val displayName: String,
        val repeat: TaskRepeat,
        val offsets: Set<Int>,
        val hint: String
    )

    val ALL: List<Template> = listOf(
        Template(TaskKind.INSURANCE_PREMIUM, "保险缴费", TaskRepeat.YEARLY, setOf(15, 3, 0), ""),
        Template(
            TaskKind.MEDICAL_INSURANCE, "居民医保缴费", TaskRepeat.YEARLY, setOf(7, 0),
            "各地缴费期不同，请按当地通知填写日期"
        ),
        Template(
            TaskKind.PENSION_CERT, "养老金资格认证", TaskRepeat.YEARLY, setOf(30, 7),
            "按当地社保要求填写"
        ),
        Template(TaskKind.ID_EXPIRY, "证件到期", TaskRepeat.NONE, setOf(60, 30, 7), ""),
        Template(TaskKind.CHECKUP, "体检", TaskRepeat.YEARLY, setOf(7, 1), ""),
        Template(TaskKind.CUSTOM, "自定义", TaskRepeat.NONE, setOf(1, 0), "")
    )

    fun of(kind: TaskKind): Template = ALL.first { it.kind == kind }

    /**
     * 默认标题：选了家人用“称谓 + 的 + 显示名”，是“我”用“我的…”，
     * 没选人只用显示名；CUSTOM 恒为空（必须自填）。
     */
    fun defaultTitle(kind: TaskKind, personName: String?, personTitle: String?, isSelf: Boolean): String {
        if (kind == TaskKind.CUSTOM) return ""
        val name = of(kind).displayName
        return when {
            personName == null -> name
            isSelf -> "我的" + name
            else -> (personTitle ?: personName) + "的" + name
        }
    }
}
