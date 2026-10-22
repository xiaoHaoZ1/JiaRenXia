package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 生日与提醒偏好的数据规则（DEVELOPMENT_PLAN 2.5；Stage C 含农历）。 */
class BirthdayRulesTest : RepoTestBase() {

    private suspend fun createDad2(birthday: BirthdayValue): String {
        val me = repo.observeSelf().first()!!.id
        return repo.createRelative(
            PersonDraft("妈", Gender.FEMALE, birthdays = PersonBirthdays().with(birthday.calendar, birthday)), me, RelationRole.MOTHER
        ).ok()
    }

    private suspend fun createDad(birthday: BirthdayValue): String {
        val me = createSelf()
        return repo.createRelative(
            PersonDraft("爸", Gender.MALE, birthdays = PersonBirthdays().with(birthday.calendar, birthday)), me, RelationRole.FATHER
        ).ok()
    }

    @Test
    fun 创建生日自动建默认偏好() = runTest {
        val dad = createDad(BirthdayValue(PartialDate(1960, 3, 15)))
        val detail = repo.observePersonDetail(dad).first()!!
        assertEquals(BirthdayValue(PartialDate(1960, 3, 15)), detail.birthday)
        assertTrue(detail.reminder!!.enabled)
        assertEquals(setOf(0, 1), detail.reminder!!.reminderOffsets)
    }

    @Test
    fun 改生日原地更新并保留关闭的偏好() = runTest {
        val dad = createDad(BirthdayValue(PartialDate(1960, 3, 15)))
        val dateIdBefore = db.importantDateDao().getBirthdays(dad).first().id
        repo.setReminderEnabled(dad, false, ImportantDateType.BIRTHDAY_SOLAR).ok()
        repo.updatePerson(
            dad, PersonDraft("爸", Gender.MALE, birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1960, 4, 1))))
        ).ok()
        val after = repo.observePersonDetail(dad).first()!!
        // 同一行生日：id 不变
        assertEquals(dateIdBefore, db.importantDateDao().getBirthdays(dad).first().id)
        assertEquals(BirthdayValue(PartialDate(1960, 4, 1)), after.birthday)
        assertFalse(after.reminder!!.enabled)
        assertEquals(setOf(0, 1), after.reminder!!.reminderOffsets)
    }

    @Test
    fun 删除生日级联偏好与偏移() = runTest {
        val dad = createDad(BirthdayValue(PartialDate(1960, 3, 15)))
        repo.updatePerson(dad, PersonDraft("爸", Gender.MALE, birthdays = PersonBirthdays())).ok()
        assertEquals(0, db.importantDateDao().count())
        assertEquals(0, db.reminderPreferenceDao().count())
        assertEquals(0, db.reminderOffsetDao().count())
        val detail = repo.observePersonDetail(dad).first()!!
        assertNull(detail.birthday)
        assertNull(detail.reminder)
    }

    @Test
    fun 生日日期校验() = runTest {
        val me = createSelf()
        assertErr(
            repo.createRelative(
                PersonDraft("x", Gender.MALE, birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1999, 2, 29)))),
                me, RelationRole.FATHER
            ),
            FamilyError.InvalidBirthday
        )
        assertErr(
            repo.createRelative(
                PersonDraft("x", Gender.MALE, birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1700, 1, 1)))),
                me, RelationRole.FATHER
            ),
            FamilyError.InvalidBirthday
        )
        val p = repo.createRelative(
            PersonDraft("x", Gender.MALE, birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(null, 2, 29)))),
            me, RelationRole.FATHER
        ).ok()
        val detail = repo.observePersonDetail(p).first()!!
        assertEquals(BirthdayValue(PartialDate(null, 2, 29)), detail.birthday)
    }

    @Test
    fun 农历生日往返含闰月() = runTest {
        val dad = createDad(
            BirthdayValue(PartialDate(1963, 8, 15), CalendarType.LUNAR)
        )
        val detail = repo.observePersonDetail(dad).first()!!
        assertEquals(
            BirthdayValue(PartialDate(1963, 8, 15), CalendarType.LUNAR),
            detail.birthday
        )
        // 闰月标记往返（2023 农历闰二月；测试时钟为 2023）
        val mom = createDad2(
            BirthdayValue(PartialDate(2023, 2, 5), CalendarType.LUNAR, leapMonth = true)
        )
        val d2 = repo.observePersonDetail(mom).first()!!
        assertTrue(d2.birthday!!.leapMonth)
    }

    @Test
    fun 农历校验_无此闰月与超天数() = runTest {
        val me = createSelf()
        // 2026 无闰六月 → 带年份即拒绝
        assertErr(
            repo.createRelative(
                PersonDraft(
                    "x", Gender.MALE,
                    birthdays = PersonBirthdays(lunar = BirthdayValue(
                        PartialDate(2026, 6, 1), CalendarType.LUNAR, leapMonth = true
                    ))
                ),
                me, RelationRole.FATHER
            ),
            FamilyError.InvalidBirthday
        )
        // 2025 闰六月只有 29 天 → 三十拒绝
        assertErr(
            repo.createRelative(
                PersonDraft(
                    "x", Gender.MALE,
                    birthdays = PersonBirthdays(lunar = BirthdayValue(
                        PartialDate(2025, 6, 30), CalendarType.LUNAR, leapMonth = true
                    ))
                ),
                me, RelationRole.FATHER
            ),
            FamilyError.InvalidBirthday
        )
        // 无年份时闰月可自由设置
        val p = repo.createRelative(
            PersonDraft(
                "x", Gender.MALE,
                birthdays = PersonBirthdays(lunar = BirthdayValue(
                    PartialDate(null, 6, 3), CalendarType.LUNAR, leapMonth = true
                ))
            ),
            me, RelationRole.FATHER
        )
        assertTrue(p is FamilyResult.Success)
    }

    @Test
    fun 无生日不能开关提醒() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        assertErr(repo.setReminderEnabled(dad, true, ImportantDateType.BIRTHDAY_SOLAR), FamilyError.NoBirthday)
    }

    @Test
    fun 已故人物不出现在提醒列表() = runTest {
        val me = createSelf()
        repo.createRelative(
            PersonDraft("爸", Gender.MALE, birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1960, 3, 15)))),
            me, RelationRole.FATHER
        ).ok()
        val gp = repo.createRelative(
            PersonDraft(
                "爷", Gender.MALE, birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1930, 1, 1))),
                livingStatus = LivingStatus.DECEASED
            ),
            me, RelationRole.MOTHER
        )
        assertTrue(gp is FamilyResult.Success)
        val entries = repo.getBirthdayEntries()
        assertEquals(1, entries.size)
        assertEquals("爸", entries[0].person.name)
        assertTrue(entries[0].reminderEnabled)
    }
}
