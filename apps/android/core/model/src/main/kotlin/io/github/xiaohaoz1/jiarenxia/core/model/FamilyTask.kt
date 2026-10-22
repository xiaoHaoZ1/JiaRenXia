package io.github.xiaohaoz1.jiarenxia.core.model

import java.time.LocalDate

/**
 * 家庭事项模板（FAMILY_AFFAIRS 5.1）：缴费 / 医保 / 资格认证 / 证件到期 / 体检 / 自定义。
 */
enum class TaskKind {
    INSURANCE_PREMIUM, MEDICAL_INSURANCE, PENSION_CERT, ID_EXPIRY, CHECKUP, CUSTOM
}

/** 事项重复周期：不重复 / 每月 / 每年。 */
enum class TaskRepeat { NONE, MONTHLY, YEARLY }

/** 提前提醒可选天数集合（多选）：当天、1、3、7、15、30、60 天。 */
val TASK_OFFSETS: Set<Int> = setOf(0, 1, 3, 7, 15, 30, 60)

/**
 * 家庭事项：一条要在某日办理的事（缴费、认证、证件到期……）。
 *
 * 当前这一期日期 = [anchorDate] + [periodIndex] × [repeat] 周期（见 TaskSchedule.dueDate）。
 * [personId] 为 null 表示全家的事；[done] 只对不重复事项有意义（重复事项靠推进期数前进）。
 */
data class FamilyTask(
    val id: String,
    val personId: String?,
    val kind: TaskKind,
    val title: String,
    /** 首次（第 0 期）日期。 */
    val anchorDate: LocalDate,
    val repeat: TaskRepeat,
    val periodIndex: Int = 0,
    val lastDoneDate: LocalDate? = null,
    val done: Boolean = false,
    /** 金额（分），可空。 */
    val amountFen: Long? = null,
    val note: String? = null,
    /** 提醒开关（与通知相关，不影响列表展示）。 */
    val enabled: Boolean = true,
    val offsets: Set<Int> = setOf(0),
    /**
     * 一天内的提醒时刻（分钟数），null 表示跟随「家事提醒时刻」设置。
     * 对每个到期提醒日生效（提前 N 天的提醒也在该时刻发）。
     */
    val remindMinutes: Int? = null
)
