package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 人物详情的生日提醒：必须精确对应“生日”这一行（发现 3），
 * 且提前天数是真实的响应式依赖（发现 4）。
 */
class PersonDetailReminderTest : RepoTestBase() {

    /** “我” + 配偶（妻）；妻名下：生日 + 结婚纪念日（纪念日落在“我”名下）。 */
    private suspend fun wifeWithTwoDates(): Pair<String, String> {
        val me = createSelf()
        val wife = repo.createRelative(
            PersonDraft(
                "妻", Gender.FEMALE,
                birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1990, 6, 8)))
            ),
            me, RelationRole.WIFE
        ).ok()
        repo.setWeddingAnniversary(
            me, BirthdayValue(PartialDate(2018, 9, 9))).ok()
        return me to wife
    }

    @Test
    fun 同一人物多种日期时读到的是生日配置() = runTest {
        val (me, wife) = wifeWithTwoDates()
        // 先开纪念日提醒（“我”名下），再开妻子生日提醒：插入顺序与生日不同
        repo.setReminderEnabled(me, true, ImportantDateType.WEDDING_ANNIVERSARY).ok()
        repo.setReminderOffsets(me, setOf(7), ImportantDateType.WEDDING_ANNIVERSARY).ok()
        repo.setReminderEnabled(wife, true, ImportantDateType.BIRTHDAY_SOLAR).ok()
        repo.setReminderOffsets(wife, setOf(0, 3), ImportantDateType.BIRTHDAY_SOLAR).ok()

        val detail = repo.observePersonDetail(wife).first()!!
        // 必须拿到生日（BIRTHDAY）行的配置，不是先查到的纪念日行
        assertNotNull(detail.reminder)
        val r = detail.reminder!!
        assertEquals(true, r.enabled)
        assertEquals(setOf(0, 3), r.reminderOffsets)
    }

    @Test
    fun 生日提醒开关独立于纪念日() = runTest {
        val (me, wife) = wifeWithTwoDates()
        // 纪念日改成非默认的 {7}；生日偏好是建人时自动创建的（默认 {0,1}）
        repo.setReminderEnabled(me, true, ImportantDateType.WEDDING_ANNIVERSARY).ok()
        repo.setReminderOffsets(me, setOf(7), ImportantDateType.WEDDING_ANNIVERSARY).ok()
        val detail = repo.observePersonDetail(wife).first()!!
        assertNotNull(detail.reminder)
        // 生日的提前天数必须是自己的 {0,1}，不能借来纪念日的 {7}
        assertEquals(setOf(0, 1), detail.reminder!!.reminderOffsets)
        // 关掉生日开关只影响生日，且读回 false
        repo.setReminderEnabled(wife, false, ImportantDateType.BIRTHDAY_SOLAR).ok()
        val d2 = repo.observePersonDetail(wife).first()!!
        val r2 = d2.reminder!!
        assertEquals(false, r2.enabled)
        assertEquals(setOf(0, 1), r2.reminderOffsets)
    }

    @Test
    fun 仅修改提前天数会收到新配置() = runTest {
        val (_, wife) = wifeWithTwoDates()
        repo.setReminderEnabled(wife, true, ImportantDateType.BIRTHDAY_SOLAR).ok()
        repo.setReminderOffsets(wife, setOf(0, 3), ImportantDateType.BIRTHDAY_SOLAR).ok()

        val seen = CopyOnWriteArrayList<Set<Int>?>()
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            repo.observePersonDetail(wife).collect {
                seen.add(it?.reminder?.reminderOffsets)
            }
        }
        try {
            // 只动 offsets 表：连续添加/取消，最终集合必须出现在发射里
            repo.setReminderOffsets(wife, setOf(0, 7), ImportantDateType.BIRTHDAY_SOLAR).ok()
            repo.setReminderOffsets(wife, setOf(0), ImportantDateType.BIRTHDAY_SOLAR).ok()
            repo.setReminderOffsets(wife, setOf(0, 3, 7), ImportantDateType.BIRTHDAY_SOLAR).ok()
            val deadline = System.currentTimeMillis() + 10_000
            while (seen.none { it == setOf(0, 3, 7) } &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(25)
            }
            assertTrue("未收到 offsets 更新: $seen", seen.any { it == setOf(0, 3, 7) })
        } finally {
            scope.cancel()
        }
        // 与数据库一致
        assertEquals(
            setOf(0, 3, 7),
            repo.observePersonDetail(wife).first()!!.reminder!!.reminderOffsets
        )
    }

    @Test
    fun 删除生日后不借用其他日期的配置() = runTest {
        val (me, wife) = wifeWithTwoDates()
        repo.setReminderEnabled(me, true, ImportantDateType.WEDDING_ANNIVERSARY).ok()
        repo.setReminderEnabled(wife, true, ImportantDateType.BIRTHDAY_SOLAR).ok()
        // 删掉妻子生日（保留人物与纪念日）
        repo.updatePerson(wife, PersonDraft("妻", Gender.FEMALE)).ok()
        val detail = repo.observePersonDetail(wife).first()!!
        assertNull(detail.birthday)
        assertNull(detail.reminder)
    }
}
