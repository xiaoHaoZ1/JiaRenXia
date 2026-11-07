package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 回家看看仓库：增删改、见面记录与级联（真实内存库）。 */
class HomeVisitRepositoryTest : RepoTestBase() {

    private var seq = 0
    private lateinit var homes: HomeVisitRepositoryImpl

    @Before
    fun setUpHomes() {
        homes = HomeVisitRepositoryImpl(
            db,
            object : IdGenerator { override fun newId() = "hid-" + (++seq) },
            object : TimeSource { override fun now() = now }
        )
    }

    /** “我” + 爸爸 + 妈妈（均在世）。 */
    private suspend fun family(): Triple<String, String, String> {
        val me = createSelf()
        val dad = repo.createRelative(
            PersonDraft("爸爸", Gender.MALE), me, RelationRole.FATHER).ok()
        val mom = repo.createRelative(
            PersonDraft("妈妈", Gender.FEMALE), me, RelationRole.MOTHER).ok()
        return Triple(me, dad, mom)
    }

    @Test
    fun addUpdateDelete往返() = runTest {
        val (_, dad, mom) = family()
        assertTrue(homes.observeHomes().first().isEmpty())

        val id = homes.addHome(listOf(dad, mom), 90)
        var list = homes.getHomes()
        assertEquals(1, list.size)
        assertEquals(id, list[0].id)
        assertEquals(listOf(dad, mom), list[0].memberIds)
        assertEquals(90, list[0].intervalDays)
        assertEquals(now, list[0].createdAt)

        homes.updateMembers(id, listOf(mom))
        homes.updateInterval(id, 180)
        list = homes.getHomes()
        assertEquals(listOf(mom), list[0].memberIds)
        assertEquals(180, list[0].intervalDays)

        homes.deleteHome(id)
        assertTrue(homes.getHomes().isEmpty())
        assertTrue(db.homeVisitMemberDao().getAll().isEmpty())
    }

    @Test
    fun addHome丢弃不存在的人物id() = runTest {
        val (_, dad, _) = family()
        val id = homes.addHome(listOf(dad, "ghost"), 30)
        assertEquals(listOf(dad), homes.getHomes().single().memberIds)
    }

    @Test
    fun updateMembers对不存在的计划静默返回() = runTest {
        val (_, dad, _) = family()
        // 计划已被并发删除：不抛异常，也不写成员行
        homes.updateMembers("no-such-plan", listOf(dad))
        assertTrue(homes.getHomes().isEmpty())
        assertTrue(db.homeVisitMemberDao().getAll().isEmpty())
    }

    @Test
    fun logHomeVisit只给有效成员插见面记录() = runTest {
        val (me, dad, mom) = family()
        // 已故成员不记；本人不记（爷爷锚在爸爸下，避免“两个爸爸”）
        val dead = repo.createRelative(
            PersonDraft("爷爷", Gender.MALE, livingStatus = LivingStatus.DECEASED),
            dad, RelationRole.FATHER).ok()
        val id = homes.addHome(listOf(dad, mom, dead, me), 90)

        homes.logHomeVisit(id, now)
        val logs = db.contactLogDao().getAll()
        assertEquals(setOf(dad, mom), logs.map { it.personId }.toSet())
        assertTrue(logs.all { it.method == ContactMethod.VISIT.name })
        assertTrue(logs.all { it.atMillis == now && it.note == null })
    }

    @Test
    fun 删人物级联移除成员行_删家保留联系记录() = runTest {
        val (_, dad, mom) = family()
        val id = homes.addHome(listOf(dad, mom), 90)
        homes.logHomeVisit(id, now)
        assertEquals(2, db.contactLogDao().getAll().size)

        // 删掉妈妈 → 她的成员行级联消失，计划与爸爸保留；
        // 她名下的见面记录也随人物级联删除（contact_logs 同规则），剩爸爸 1 条
        repo.deletePerson(mom)
        val members = db.homeVisitMemberDao().getAll()
        assertEquals(listOf(dad), members.map { it.personId })
        assertEquals(1, homes.getHomes().size)
        assertEquals(1, db.contactLogDao().getAll().size)

        // 删家 → 计划消失，联系记录保留（爸爸那条仍在）
        homes.deleteHome(id)
        assertTrue(homes.getHomes().isEmpty())
        assertEquals(1, db.contactLogDao().getAll().size)
    }

    @Test
    fun observeHomes随写入发出() = runTest {
        val (_, dad, _) = family()
        val flow = homes.observeHomes()
        assertTrue(flow.first().isEmpty())
        homes.addHome(listOf(dad), 60)
        val list = flow.first()
        assertEquals(1, list.size)
        assertEquals(60, list[0].intervalDays)
    }
}
