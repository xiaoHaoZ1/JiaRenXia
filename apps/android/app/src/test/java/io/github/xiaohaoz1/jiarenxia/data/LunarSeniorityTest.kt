package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 农历生日经换算进入长幼比较（graph.birthdays 为公历）。 */
class LunarSeniorityTest : RepoTestBase() {

    @Test
    fun 农历生日换算为公历进入图谱() = runTest {
        val me = createSelf()
        repo.createRelative(
            PersonDraft(
                "哥", Gender.MALE,
                birthdays = PersonBirthdays(lunar = BirthdayValue(
                    PartialDate(1985, 8, 15), CalendarType.LUNAR
                ))
            ),
            me, RelationRole.BROTHER
        ).ok()
        val graph = repo.observeFamilyGraph().first()
        val solar = IcuLunarCalendar().toSolar(1985, 8, 15, false)!!
        val bro = graph.persons.first { it.name == "哥" }
        assertEquals(
            PartialDate(solar.year, solar.monthValue, solar.dayOfMonth),
            graph.birthdays[bro.id]
        )
        // birthdayValues 保留原始农历取值
        assertEquals(
            CalendarType.LUNAR,
            graph.birthdayValues[bro.id]!!.single().calendar
        )
    }

    @Test
    fun 农历有年份的兄弟按公历长幼排序() = runTest {
        val me = repo.createSelf(
            PersonDraft(
                name = "我", gender = Gender.MALE,
                birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1990, 5, 12)))
            )
        ).ok()
        // 弟（农历九月大）却先录入、哥（农历三月）后录入 —— 应按换算公历定长幼
        repo.createRelative(
            PersonDraft(
                "弟", Gender.MALE,
                birthdays = PersonBirthdays(lunar = BirthdayValue(PartialDate(1993, 9, 1), CalendarType.LUNAR))
            ),
            me, RelationRole.BROTHER
        ).ok()
        repo.createRelative(
            PersonDraft(
                "哥", Gender.MALE,
                birthdays = PersonBirthdays(lunar = BirthdayValue(PartialDate(1990, 3, 1), CalendarType.LUNAR))
            ),
            me, RelationRole.BROTHER
        ).ok()
        val ov = repo.observeFamilyGraph().first()
        val overview = io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine()
            .resolveAll(ov, me)
        val elder = ov.persons.first { it.name == "哥" }
        assertEquals("哥哥", overview.getValue(elder.id).displayTitle)
        // 无年份的农历生日不参与长幼
        repo.createRelative(
            PersonDraft(
                "友", Gender.MALE,
                birthdays = PersonBirthdays(lunar = BirthdayValue(PartialDate(null, 5, 5), CalendarType.LUNAR))
            ),
            me, RelationRole.BROTHER
        ).ok()
        val g2 = repo.observeFamilyGraph().first()
        val noYear = g2.persons.first { it.name == "友" }
        assertNull(g2.birthdays[noYear.id])
    }
}
