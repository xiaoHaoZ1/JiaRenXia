package io.github.xiaohaoz1.jiarenxia.reminder

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.data.CallQuestionRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.ContactPrompt
import io.github.xiaohaoz1.jiarenxia.data.ContactRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.IdGenerator
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.domain.call.CallQuestionPrompt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/**
 * 「打完电话记一笔」通知器：发通知/收通知与待提示的同步、
 * 通知动作记一笔（去重与「只问一次」划掉）、rearm 强制恢复。
 * 真实内存库 + Robolectric（通知经 ShadowNotificationManager 断言）。
 */
class PostCallNotifierTest : RepoTestBase() {

    private var seq = 0
    private lateinit var contacts: ContactRepositoryImpl
    private lateinit var questions: CallQuestionRepositoryImpl
    private lateinit var notifier: PostCallNotifier
    private lateinit var nm: NotificationManager

    @Before
    fun setUpNotifier() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        contacts = ContactRepositoryImpl(
            db, ids(), object : TimeSource { override fun now() = now })
        questions = CallQuestionRepositoryImpl(
            db, ids(), object : TimeSource { override fun now() = now })
        notifier = PostCallNotifier(context, contacts, questions)
        nm = context.getSystemService(NotificationManager::class.java)
    }

    private fun ids() = object : IdGenerator { override fun newId() = "n-" + (++seq) }

    private fun posted() = shadowOf(nm).getNotification(
        null, PostCallNotifier.NOTIFICATION_ID)

    /** “我” + 妈妈；返回妈妈 id。 */
    private suspend fun mom(): String {
        val me = createSelf()
        return repo.createRelative(
            PersonDraft("妈妈", Gender.FEMALE), me, RelationRole.MOTHER).ok()
    }

    private fun prompt(personId: String, at: Long = now) =
        ContactPrompt(personId, "妈妈", at)

    @Test
    fun 拨号写待提示并发通知_展示消费后通知仍在() = runTest {
        val m = mom()
        notifier.dialMade(prompt(m))
        assertEquals(m, notifier.pending.value?.prompt?.personId)
        assertFalse(notifier.pending.value!!.force)
        assertNotNull(posted())

        notifier.consumeForDisplay()
        assertNull(notifier.pending.value)
        assertNotNull("弹窗展示后通知要留着，仍可动作记一笔", posted())
    }

    @Test
    fun 答复后待提示与通知一起收掉() = runTest {
        notifier.dialMade(prompt(mom()))
        notifier.answered()
        assertNull(notifier.pending.value)
        assertNull(posted())
    }

    @Test
    fun logConnected按拨号时刻记电话_只删勾选的只问一次() = runTest {
        val m = mom()
        val q1 = questions.add(m, "吃药了吗", keep = false).ok()
        val q2 = questions.add(m, "复查去了吗", keep = false).ok()
        val q3 = questions.add(m, "血压量了吗", keep = true).ok()
        val p = prompt(m, at = now - 5_000).copy(
            oneTimeQuestions = listOf(
                CallQuestionPrompt(q1, "吃药了吗"),
                CallQuestionPrompt(q2, "复查去了吗")))

        notifier.logConnected(p, checkedQuestionIds = setOf(q1))

        val log = contacts.getLogs().single()
        assertEquals(m, log.personId)
        assertEquals(ContactMethod.CALL, log.method)
        assertEquals(now - 5_000, log.atMillis)
        val rest = questions.getForPerson(m).map { it.id }
        assertEquals(listOf(q2, q3), rest)
        assertNull(notifier.pending.value)
    }

    @Test
    fun 通知联系上了_有待提示按快照删() = runTest {
        val m = mom()
        val asked = questions.add(m, "问过的那条", keep = false).ok()
        questions.add(m, "每次都问", keep = true)
        notifier.dialMade(prompt(m).copy(
            oneTimeQuestions = listOf(CallQuestionPrompt(asked, "问过的那条"))))

        assertTrue(notifier.logConnectedFromNotification(m, now))

        val log = contacts.getLogs().single()
        assertEquals(ContactMethod.CALL, log.method)
        assertEquals(now, log.atMillis)
        // 快照里的只问一次删了，每次都问保留
        assertTrue(questions.getForPerson(m).all { it.keep })
        assertNull(notifier.pending.value)
        assertNull(posted())
    }

    @Test
    fun 通知联系上了_无快照按当前只问一次删() = runTest {
        val m = mom()
        questions.add(m, "只问一次甲", keep = false)
        questions.add(m, "每次都问乙", keep = true)
        // 进程被回收：待提示已丢，但通知还在 → 动作仍能记

        assertTrue(notifier.logConnectedFromNotification(m, now))

        assertEquals(1, contacts.getLogs().size)
        val rest = questions.getForPerson(m)
        assertEquals(1, rest.size)
        assertTrue(rest.single().keep)
    }

    @Test
    fun logConnected同时刻已有记录不再重复插() = runTest {
        val m = mom()
        val p = prompt(m)
        notifier.logConnected(p, emptySet())
        notifier.logConnected(p, emptySet())
        assertEquals(1, contacts.getLogs().size)
    }

    @Test
    fun 通知联系上了_已记过不再重复记() = runTest {
        val m = mom()
        notifier.dialMade(prompt(m))
        assertTrue(notifier.logConnectedFromNotification(m, now))

        assertFalse(notifier.logConnectedFromNotification(m, now))
        assertEquals(1, contacts.getLogs().size)
    }

    @Test
    fun 通知联系上了_人物已删不记() = runTest {
        assertFalse(notifier.logConnectedFromNotification("ghost", now))
        assertTrue(contacts.getLogs().isEmpty())
    }

    @Test
    fun rearm匹配沿用快照_不匹配按当前只问一次重建并带force() = runTest {
        val m = mom()
        questions.add(m, "现在的只问一次", keep = false)
        // 匹配：内存快照优先（哪怕与库里不同也沿用拨号时的条目）
        notifier.dialMade(prompt(m).copy(
            oneTimeQuestions = listOf(CallQuestionPrompt("s1", "快照条目"))))
        notifier.rearm(ContactPrompt(m, "妈妈", now))
        assertEquals(
            listOf(CallQuestionPrompt("s1", "快照条目")),
            notifier.pending.value?.prompt?.oneTimeQuestions)
        assertTrue(notifier.pending.value!!.force)

        // 不匹配：待提示没了 → 按该人当前「只问一次」重建
        notifier.consumeForDisplay()
        notifier.rearm(ContactPrompt(m, "妈妈", now))
        val rebuilt = notifier.pending.value?.prompt
        assertTrue(rebuilt!!.oneTimeQuestions.single().text == "现在的只问一次")
        assertTrue(notifier.pending.value!!.force)
    }
}
