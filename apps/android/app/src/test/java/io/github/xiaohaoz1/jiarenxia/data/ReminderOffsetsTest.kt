package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.reminder.ReminderPlanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 提前 3 / 7 天提醒：repo 的偏移读写与校验 + 引擎侧按偏移到期。 */
class ReminderOffsetsTest : RepoTestBase() {

    private suspend fun personWithBirthday(): String {
        val me = createSelf()
        return repo.createRelative(
            PersonDraft("妈", Gender.FEMALE, birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1964, 10, 2)))),
            me, RelationRole.MOTHER
        ).ok()
    }

    @Test
    fun 偏移合法集合保存成功() = runTest {
        val p = personWithBirthday()
        repo.setReminderOffsets(p, setOf(0, 3, 7), ImportantDateType.BIRTHDAY_SOLAR).ok()
        val detail = repo.observePersonDetail(p).first()!!
        val r = detail.reminder!!
        assertEquals(setOf(0, 3, 7), r.reminderOffsets)
        assertEquals(true, r.enabled)
    }

    @Test
    fun 空集合被拒() = runTest {
        val p = personWithBirthday()
        assertErr(repo.setReminderOffsets(p, emptySet(), ImportantDateType.BIRTHDAY_SOLAR), FamilyError.InvalidReminderOffsets)
    }

    @Test
    fun 非法偏移被拒() = runTest {
        val p = personWithBirthday()
        assertErr(repo.setReminderOffsets(p, setOf(0, 2), ImportantDateType.BIRTHDAY_SOLAR), FamilyError.InvalidReminderOffsets)
        assertErr(repo.setReminderOffsets(p, setOf(5), ImportantDateType.BIRTHDAY_SOLAR), FamilyError.InvalidReminderOffsets)
    }

    @Test
    fun 无生日被拒() = runTest {
        val me = createSelf()
        val p = repo.createRelative(PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        assertErr(repo.setReminderOffsets(p, setOf(0, 7), ImportantDateType.BIRTHDAY_SOLAR), FamilyError.NoBirthday)
    }

    @Test
    fun 生日原地更新保留偏移() = runTest {
        val p = personWithBirthday()
        repo.setReminderOffsets(p, setOf(0, 3, 7), ImportantDateType.BIRTHDAY_SOLAR).ok()
        repo.updatePerson(
            p, PersonDraft("妈", Gender.FEMALE, birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1964, 10, 3))))
        ).ok()
        val detail = repo.observePersonDetail(p).first()!!
        assertEquals(setOf(0, 3, 7), detail.reminder!!.reminderOffsets)
    }

    @Test
    fun 引擎按偏移到期() = runTest {
        val p = personWithBirthday()
        repo.setReminderEnabled(p, true, ImportantDateType.BIRTHDAY_SOLAR)
        repo.setReminderOffsets(p, setOf(0, 3, 7), ImportantDateType.BIRTHDAY_SOLAR)
        val lunar = io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar()
        // 10月2日生日 → 9月25日（-7）、9月29日（-3）、10月2日（0）到期；相邻日不触发
        for ((date, want) in listOf(
            LocalDate.of(2026, 9, 24) to null,
            LocalDate.of(2026, 9, 25) to 7,
            LocalDate.of(2026, 9, 28) to null,
            LocalDate.of(2026, 9, 29) to 3,
            LocalDate.of(2026, 10, 1) to null,
            LocalDate.of(2026, 10, 2) to 0
        )) {
            val dues = ReminderPlanner.dueNotifications(
                repo.getBirthdayEntries(), emptyMap(), date, lunar
            ).filter { it.personId == p }
            if (want == null) assertEquals(date.toString(), emptyList<Any>(), dues)
            else assertEquals(date.toString(), want, dues.single().daysBefore)
        }
    }

    @Test
    fun 农历生日的偏移到期与文案日期() = runTest {
        val me = createSelf()
        // 奶奶：农历八月十五 → 2026-09-25（公历，库换算）
        val g = repo.createRelative(
            PersonDraft("奶", Gender.FEMALE,
                birthdays = PersonBirthdays(lunar = BirthdayValue(PartialDate(null, 8, 15), CalendarType.LUNAR))),
            me, RelationRole.MOTHER
        ).ok()
        repo.setReminderEnabled(g, true, ImportantDateType.BIRTHDAY_LUNAR)
        repo.setReminderOffsets(g, setOf(0, 3), ImportantDateType.BIRTHDAY_LUNAR)
        val lunar = io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar()
        // 找出 2026 年农历八月十五对应的公历日期
        // 农历八月十五 → 下一次公历发生日（BirthdayMath 负责闰月换算）
        val birthday = repo.observePersonDetail(g).first()!!.birthday!!
        val solar = io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
            .nextOccurrence(birthday, LocalDate.of(2026, 1, 1), lunar)!!
        val today = solar.minusDays(3)
        val dues = ReminderPlanner.dueNotifications(
            repo.getBirthdayEntries(), emptyMap(), today, lunar
        )
        assertEquals(1, dues.size)
        assertEquals(3, dues[0].daysBefore)
        // 文案带日期：3天后（M月D日）
        val text = ReminderPlanner.text(dues[0], false)
        val expect = "3天后（" + solar.monthValue + "月" + solar.dayOfMonth + "日）是奶的生日"
        assertEquals(expect, text)
    }

    @Test
    fun 文案_提前N天带日期() {
        val d = io.github.xiaohaoz1.jiarenxia.reminder.DueReminder(
            "a", "张三", "父亲", 7, LocalDate.of(2026, 10, 9)
        )
        assertEquals("7天后（10月9日）是父亲张三的生日", ReminderPlanner.text(d, false))
        val d2 = io.github.xiaohaoz1.jiarenxia.reminder.DueReminder(
            "me", "我", null, 3, LocalDate.of(2026, 10, 5)
        )
        assertEquals("3天后（10月5日）是你的生日", ReminderPlanner.text(d2, true))
    }
}
