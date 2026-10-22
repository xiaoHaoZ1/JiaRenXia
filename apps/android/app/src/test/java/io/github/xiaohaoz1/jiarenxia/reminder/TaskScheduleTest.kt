package io.github.xiaohaoz1.jiarenxia.reminder

import io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask
import io.github.xiaohaoz1.jiarenxia.core.model.TaskKind
import io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** TaskSchedule 纯函数：期日推算、办完推进、通知筛选与文案。 */
class TaskScheduleTest {

    private fun task(
        repeat: TaskRepeat = TaskRepeat.YEARLY,
        anchor: LocalDate = LocalDate.of(2025, 1, 15),
        periodIndex: Int = 0,
        done: Boolean = false,
        enabled: Boolean = true,
        offsets: Set<Int> = setOf(0, 7)
    ) = FamilyTask(
        id = "t", personId = null, kind = TaskKind.CUSTOM, title = "测试",
        anchorDate = anchor, repeat = repeat, periodIndex = periodIndex,
        done = done, enabled = enabled, offsets = offsets
    )

    @Test
    fun 每月从首期推算不漂移() {
        val anchor = LocalDate.of(2025, 1, 31)
        assertEquals(LocalDate.of(2025, 2, 28), TaskSchedule.dueDate(anchor, TaskRepeat.MONTHLY, 1))
        assertEquals(LocalDate.of(2025, 3, 31), TaskSchedule.dueDate(anchor, TaskRepeat.MONTHLY, 2))
    }

    @Test
    fun 闰日每年平年落到228() {
        val anchor = LocalDate.of(2024, 2, 29)
        assertEquals(LocalDate.of(2025, 2, 28), TaskSchedule.dueDate(anchor, TaskRepeat.YEARLY, 1))
        assertEquals(LocalDate.of(2028, 2, 29), TaskSchedule.dueDate(anchor, TaskRepeat.YEARLY, 4))
    }

    @Test
    fun 提前办完至少推进一期() {
        val t = task(periodIndex = 0)
        // 到期日前办完：下一期 = periodIndex + 1
        assertEquals(1, TaskSchedule.nextPeriodIndex(t, LocalDate.of(2025, 1, 1)))
    }

    @Test
    fun 逾期多期办完跳到下一未来期() {
        val t = task(periodIndex = 0)
        // 2027-06 办完：越过 2025/2026/2027-01 三期，落到 2028-01-15（periodIndex=3）
        assertEquals(3, TaskSchedule.nextPeriodIndex(t, LocalDate.of(2027, 6, 1)))
    }

    @Test
    fun 不重复办完返回null() {
        assertNull(TaskSchedule.nextPeriodIndex(task(TaskRepeat.NONE), LocalDate.now()))
    }

    @Test
    fun daysUntil逾期为负() {
        val t = task(anchor = LocalDate.of(2025, 6, 10))
        assertEquals(-5, TaskSchedule.daysUntil(t, LocalDate.of(2025, 6, 15)))
    }

    @Test
    fun 通知筛选_enabled_未办_提前天数匹配_逾期不发() {
        val today = LocalDate.of(2025, 6, 10)
        val due = TaskSchedule.dueNotifications(
            listOf(
                task(anchor = today, offsets = setOf(0)),                    // 当天
                task(anchor = today.plusDays(7), offsets = setOf(7)),        // 7 天后
                task(anchor = today.minusDays(1), offsets = setOf(0)),       // 逾期不发
                task(anchor = today, enabled = false),                       // 关开关
                task(anchor = today, done = true),                           // 已办
                task(anchor = today.plusDays(5), offsets = setOf(7))         // 不在偏移里
            ),
            today
        )
        assertEquals(2, due.size)
        assertEquals(setOf(0, 7), due.map { it.daysBefore }.toSet())
    }

    @Test
    fun 通知文案() {
        val t = task(anchor = LocalDate.of(2025, 10, 15))
        assertEquals("今天要办：测试", TaskSchedule.text(t, 0))
        assertEquals("明天要办：测试", TaskSchedule.text(t, 1))
        assertEquals("3 天后（10月15日）：测试", TaskSchedule.text(t, 3))
    }
}

/** TaskTemplates 默认值与默认标题。 */
class TaskTemplatesTest {

    @Test
    fun 六模板与默认值() {
        assertEquals(6, TaskTemplates.ALL.size)
        assertEquals(setOf(15, 3, 0), TaskTemplates.of(TaskKind.INSURANCE_PREMIUM).offsets)
        assertEquals(setOf(7, 0), TaskTemplates.of(TaskKind.MEDICAL_INSURANCE).offsets)
        assertEquals(setOf(30, 7), TaskTemplates.of(TaskKind.PENSION_CERT).offsets)
        assertEquals(setOf(60, 30, 7), TaskTemplates.of(TaskKind.ID_EXPIRY).offsets)
        assertEquals(setOf(7, 1), TaskTemplates.of(TaskKind.CHECKUP).offsets)
        assertEquals(setOf(1, 0), TaskTemplates.of(TaskKind.CUSTOM).offsets)
        assertEquals(TaskRepeat.NONE, TaskTemplates.of(TaskKind.ID_EXPIRY).repeat)
        assertEquals(TaskRepeat.YEARLY, TaskTemplates.of(TaskKind.CHECKUP).repeat)
    }

    @Test
    fun 默认标题() {
        assertEquals(
            "妈妈的居民医保缴费",
            TaskTemplates.defaultTitle(TaskKind.MEDICAL_INSURANCE, "王某", "妈妈", false)
        )
        assertEquals(
            "我的体检",
            TaskTemplates.defaultTitle(TaskKind.CHECKUP, "杨某", null, true)
        )
        // 无称谓回退姓名
        assertEquals(
            "李某的保险缴费",
            TaskTemplates.defaultTitle(TaskKind.INSURANCE_PREMIUM, "李某", null, false)
        )
        // 全家的事：只有模板名
        assertEquals("保险缴费", TaskTemplates.defaultTitle(TaskKind.INSURANCE_PREMIUM, null, null, false))
        // 自定义恒空
        assertEquals("", TaskTemplates.defaultTitle(TaskKind.CUSTOM, "王某", "妈妈", false))
    }
}
