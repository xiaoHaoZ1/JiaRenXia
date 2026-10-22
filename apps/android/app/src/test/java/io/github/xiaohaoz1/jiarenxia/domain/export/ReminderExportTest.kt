package io.github.xiaohaoz1.jiarenxia.domain.export

import android.os.Build
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.TaskKind
import io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat
import io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry
import io.github.xiaohaoz1.jiarenxia.data.DateEntry
import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 家事提醒导出行构建（范围、逾期、周年、历法说明、排序）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ReminderExportTest {

    private val lunar = IcuLunarCalendar()
    private val today = LocalDate.of(2026, 3, 1)
    private val from = today
    private val to = today.plusDays(30) // 2026-03-31

    private fun p(id: String, name: String) = Person(id, name, Gender.MALE)

    private fun solarB(month: Int, day: Int, year: Int? = null) =
        BirthdayValue(PartialDate(year, month, day))

    private fun lunarB(month: Int, day: Int, year: Int? = null) =
        BirthdayValue(PartialDate(year, month, day), CalendarType.LUNAR)

    @Test
    fun 同人双生日同日导出一行且保留两个历法说明() {
        val occurrence = LocalDate.of(2026, 3, 15)
        val ld = lunar.fromSolar(occurrence)
        val person = p("dad", "王志刚")
        val rows = build(birthdays = listOf(
            BirthdayEntry(person, solarB(3, 15), true),
            BirthdayEntry(person, BirthdayValue(
                PartialDate(null, ld.month, ld.day), CalendarType.LUNAR, ld.leap), false)
        ))
        assertEquals(1, rows.size)
        assertEquals(occurrence, rows.single().date)
        assertTrue(rows.single().calendarNote.contains("阳历和农历同一天"))
        assertTrue(rows.single().calendarNote.contains("公历"))
        assertTrue(rows.single().calendarNote.contains("农历"))
    }

    @Test
    fun 同人双生日不同日分别导出且不合并同名不同人() {
        val ld = lunar.fromSolar(LocalDate.of(2026, 3, 20))
        val person = p("dad", "王志刚")
        val rows = build(birthdays = listOf(
            BirthdayEntry(person, solarB(3, 15), true),
            BirthdayEntry(person, BirthdayValue(
                PartialDate(null, ld.month, ld.day), CalendarType.LUNAR, ld.leap), true),
            BirthdayEntry(p("other", "王志刚"), solarB(3, 15), true)
        ))
        assertEquals(3, rows.size)
        assertEquals(2, rows.count { it.date == LocalDate.of(2026, 3, 15) })
        assertEquals(1, rows.count { it.date == LocalDate.of(2026, 3, 20) })
    }

    private fun build(
        birthdays: List<BirthdayEntry> = emptyList(),
        dates: List<DateEntry> = emptyList(),
        tasks: List<FamilyTask> = emptyList(),
        includeOverdue: Boolean = true,
        from: LocalDate = this.from,
        to: LocalDate = this.to
    ) = ReminderExport.build(
        birthdays, dates, tasks,
        labelOf = { pid -> mapOf("dad" to "爸爸", "mom" to "妈妈")[pid] },
        from = from, to = to, includeOverdue = includeOverdue,
        today = today, lunar = lunar
    )

    @Test
    fun 范围内公历生日一次() {
        val rows = build(
            birthdays = listOf(
                BirthdayEntry(p("dad", "王志刚"), solarB(3, 15), true)
            )
        )
        assertEquals(1, rows.size)
        val r = rows[0]
        assertEquals(LocalDate.of(2026, 3, 15), r.date)
        assertEquals("爸爸", r.person)
        assertEquals("生日", r.type)
        assertEquals("爸爸的生日", r.title)
        assertEquals("", r.status)
        assertEquals("公历", r.calendarNote)
    }

    @Test
    fun 称谓查不到回退姓名() {
        val rows = build(
            birthdays = listOf(BirthdayEntry(p("x", "张三"), solarB(3, 5), true))
        )
        assertEquals("张三", rows[0].person)
        assertEquals("张三的生日", rows[0].title)
    }

    @Test
    fun 范围外不导出_跨年范围各一次() {
        val b = BirthdayEntry(p("dad", "王志刚"), solarB(3, 15), true)
        // 只在范围外（4 月）→ 0 行
        assertEquals(0, build(birthdays = listOf(b), to = LocalDate.of(2026, 3, 14)).size)
        // 跨两年（2026-03 到 2027-06）→ 2026、2027 各一次，不无限展开
        val rows = build(birthdays = listOf(b), to = LocalDate.of(2027, 6, 30))
        assertEquals(listOf(LocalDate.of(2026, 3, 15), LocalDate.of(2027, 3, 15)),
            rows.map { it.date })
    }

    @Test
    fun 结婚纪念日_双方与第几年() {
        val e = DateEntry(
            type = ImportantDateType.WEDDING_ANNIVERSARY,
            person = p("dad", "王志刚"), date = solarB(3, 20, year = 1990),
            reminderEnabled = true, partnerId = "mom", partnerName = "李梅"
        )
        val r = build(dates = listOf(e))[0]
        assertEquals("爸爸、妈妈", r.person)
        assertEquals("结婚纪念日", r.type)
        assertEquals("爸爸妈妈的结婚纪念日（第 36 年）", r.title)
        assertEquals("", r.status)
    }

    @Test
    fun 忌日_周年口径() {
        val e = DateEntry(
            type = ImportantDateType.DEATH_ANNIVERSARY,
            person = p("gp", "王老"), date = solarB(3, 10, year = 2020),
            reminderEnabled = true
        )
        val r = build(dates = listOf(e))[0]
        assertEquals("忌日", r.type)
        assertEquals("王老的忌日（6 周年）", r.title)
    }

    @Test
    fun 农历生日_历法说明带原文() {
        // 农历 1964 年八月十五：落在范围内的公历日由 BirthdayMath 计算
        val rows = build(
            birthdays = listOf(
                BirthdayEntry(p("dad", "王志刚"), lunarB(8, 15, year = 1964), true)
            ),
            from = LocalDate.of(2026, 9, 1), to = LocalDate.of(2026, 10, 31)
        )
        assertEquals(1, rows.size)
        assertEquals(lunar.toSolar(2026, 8, 15, false), rows[0].date)
        assertEquals("农历1964年八月十五", rows[0].calendarNote)
    }

    @Test
    fun 逾期事项_默认包含且标天数() {
        val t = FamilyTask(
            id = "t1", personId = "dad", kind = TaskKind.CHECKUP,
            title = "体检", anchorDate = LocalDate.of(2026, 2, 20),
            repeat = TaskRepeat.NONE
        )
        val rows = build(tasks = listOf(t))
        assertEquals(1, rows.size)
        assertEquals(LocalDate.of(2026, 2, 20), rows[0].date)
        assertEquals("已逾期 9 天", rows[0].status)
    }

    @Test
    fun 逾期事项_关闭后排除() {
        val t = FamilyTask(
            id = "t1", personId = null, kind = TaskKind.CHECKUP,
            title = "体检", anchorDate = LocalDate.of(2026, 2, 20),
            repeat = TaskRepeat.NONE
        )
        assertEquals(0, build(tasks = listOf(t), includeOverdue = false).size)
    }

    @Test
    fun 待办与已办() {
        val open = FamilyTask(
            id = "t1", personId = null, kind = TaskKind.ID_EXPIRY,
            title = "换身份证", anchorDate = LocalDate.of(2026, 3, 10),
            repeat = TaskRepeat.NONE
        )
        val done = FamilyTask(
            id = "t2", personId = "dad", kind = TaskKind.CUSTOM,
            title = "买米", anchorDate = LocalDate.of(2026, 3, 12),
            repeat = TaskRepeat.NONE, done = true
        )
        val rows = build(tasks = listOf(open, done))
        assertEquals(2, rows.size)
        assertEquals("待办", rows[0].status)
        assertEquals("全家", rows[0].person)
        assertEquals("证件到期", rows[0].type)
        assertEquals("已办", rows[1].status)
        assertEquals("爸爸", rows[1].person)
    }

    @Test
    fun 重复事项只出当前实例() {
        val t = FamilyTask(
            id = "t1", personId = null, kind = TaskKind.MEDICAL_INSURANCE,
            title = "居民医保", anchorDate = LocalDate.of(2025, 11, 1),
            repeat = TaskRepeat.YEARLY, periodIndex = 1 // 当前期 2026-11-01
        )
        // 当前期不在范围内 → 0 行（不会展开未来多期）
        assertEquals(0, build(tasks = listOf(t)).size)
        val rows = build(tasks = listOf(t), to = LocalDate.of(2026, 12, 31))
        assertEquals(listOf(LocalDate.of(2026, 11, 1)), rows.map { it.date })
    }

    @Test
    fun 同一天先重要日期后事项() {
        val t = FamilyTask(
            id = "t1", personId = null, kind = TaskKind.CUSTOM,
            title = "缴费", anchorDate = LocalDate.of(2026, 3, 15),
            repeat = TaskRepeat.NONE
        )
        val b = BirthdayEntry(p("dad", "王志刚"), solarB(3, 15), true)
        val rows = build(birthdays = listOf(b), tasks = listOf(t))
        assertEquals("生日", rows[0].type)
        assertEquals("自定义", rows[1].type)
    }

    @Test
    fun remindersCsv_空列表也有表头() {
        val csv = CsvExport.remindersCsv(emptyList())
        assertTrue(csv.startsWith(CsvExport.BOM))
        assertEquals(
            CsvExport.BOM + "日期,关联家人,类型,标题,状态,历法说明",
            csv.trim()
        )
    }

    @Test
    fun remindersCsv_中文转义与列序() {
        val csv = CsvExport.remindersCsv(
            listOf(
                ReminderExport.Row(
                    LocalDate.of(2026, 3, 15), "爸爸", "生日",
                    "带,逗号的\"备注\"\n第二行", "", "公历"
                )
            )
        )
        val lines = csv.trim().split("\r\n")
        assertEquals(
            "2026-03-15,爸爸,生日,\"带,逗号的\"\"备注\"\"\n第二行\",,公历",
            lines[1]
        )
    }
}
