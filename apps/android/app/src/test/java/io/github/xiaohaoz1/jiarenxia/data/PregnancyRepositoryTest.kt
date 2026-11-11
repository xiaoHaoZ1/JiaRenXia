package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.TaskKind
import io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 孕期仓库：资格校验、唯一 ACTIVE、end 归档产检、delete 清理、级联。
 */
class PregnancyRepositoryTest : RepoTestBase() {

    private lateinit var pregnancies: PregnancyRepositoryImpl
    private lateinit var tasks: FamilyTaskRepositoryImpl
    private var seq = 2000

    private val today = LocalDate.of(2026, 10, 2)
    private val due = today.plusDays(54) // 孕约 32 周

    @org.junit.Before
    fun setUpPreg() {
        pregnancies = PregnancyRepositoryImpl(
            db = db,
            ids = object : IdGenerator { override fun newId() = "pg-" + (++seq) },
            clock = object : TimeSource { override fun now() = now }
        )
        tasks = FamilyTaskRepositoryImpl(
            db = db,
            ids = object : IdGenerator { override fun newId() = "tid-" + (++seq) },
            clock = object : TimeSource { override fun now() = now }
        )
    }

    /** 在世女性（“我”的妹妹）。 */
    private suspend fun mom(): String {
        val me = repo.createSelf(PersonDraft("我", Gender.MALE)).ok()
        return repo.createRelative(
            PersonDraft("李雪", Gender.FEMALE), me, RelationRole.SISTER
        ).ok()
    }

    private fun taskDraft(
        personId: String,
        pregnancyId: String?,
        anchor: LocalDate,
        done: Boolean = false
    ) = TaskDraft(
        personId = personId, kind = TaskKind.CHECKUP, title = "产检",
        anchorDate = anchor, repeat = TaskRepeat.NONE,
        offsets = setOf(1, 0), pregnancyId = pregnancyId
    )

    @Test
    fun `创建与读取`() = runTest {
        val pid = mom()
        val id = pregnancies.create(pid, due, null, today).ok()
        val p = pregnancies.get(id)!!
        assertEquals(pid, p.personId)
        assertEquals(due, p.dueDate)
        assertNull(p.lmpDate)
        assertEquals(PregnancyStatus.ACTIVE, p.status)
        assertNull(p.endedOn)
        val list = pregnancies.observeForPerson(pid).first()
        assertEquals(1, list.size)
        assertEquals(id, list[0].id)
    }

    @Test
    fun `末次月经换算预产期`() = runTest {
        val pid = mom()
        val lmp = today.minusDays(226)
        val id = pregnancies.create(pid, due, lmp, today).ok()
        assertEquals(lmp, pregnancies.get(id)!!.lmpDate)
    }

    @Test
    fun `资格校验`() = runTest {
        val me = repo.createSelf(PersonDraft("我", Gender.MALE)).ok()
        val sister = repo.createRelative(
            PersonDraft("李雪", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val brother = repo.createRelative(
            PersonDraft("李明", Gender.MALE), me, RelationRole.BROTHER).ok()
        // 男性不可
        assertErr(
            pregnancies.create(brother, due, null, today),
            FamilyError.PregnancyPersonIneligible
        )
        // 已故女性不可
        val late = repo.createRelative(
            PersonDraft("王姨", Gender.FEMALE,
                livingStatus = io.github.xiaohaoz1.jiarenxia.core.model
                    .LivingStatus.DECEASED),
            me, RelationRole.SISTER).ok()
        assertErr(
            pregnancies.create(late, due, null, today),
            FamilyError.PregnancyPersonIneligible
        )
        // 人物不存在
        assertErr(
            pregnancies.create("ghost", due, null, today),
            FamilyError.PersonNotFound
        )
        // 女性可（对照）
        pregnancies.create(sister, due, null, today).ok()
    }

    @Test
    fun `日期范围校验`() = runTest {
        val pid = mom()
        // 预产期超出 [today−42, today+280]
        assertErr(
            pregnancies.create(pid, today.plusDays(281), null, today),
            FamilyError.PregnancyInvalidDate
        )
        assertErr(
            pregnancies.create(pid, today.minusDays(43), null, today),
            FamilyError.PregnancyInvalidDate
        )
        // 末次月经超出 [today−322, today]
        assertErr(
            pregnancies.create(pid, due, today.plusDays(1), today),
            FamilyError.PregnancyInvalidDate
        )
        assertErr(
            pregnancies.create(pid, due, today.minusDays(323), today),
            FamilyError.PregnancyInvalidDate
        )
        // 边界值可过
        pregnancies.create(pid, today.plusDays(280), null, today).ok()
        // 第二位女性（同一“我”下再建一位姐妹）
        val me = db.personDao().getSelf()!!
        val pid2 = repo.createRelative(
            PersonDraft("王芳", Gender.FEMALE), me.id,
            RelationRole.SISTER).ok()
        pregnancies.create(pid2, today.minusDays(42), null, today).ok()
    }

    @Test
    fun `同一人物只能有一条ACTIVE`() = runTest {
        val pid = mom()
        pregnancies.create(pid, due, null, today).ok()
        assertErr(
            pregnancies.create(pid, due.plusDays(10), null, today),
            FamilyError.PregnancyAlreadyActive
        )
        // 结束后可再建
        val id = pregnancies.observeForPerson(pid).first().single().id
        pregnancies.end(id, today).ok()
        pregnancies.create(pid, due, null, today).ok()
        assertEquals(2, pregnancies.observeForPerson(pid).first().size)
    }

    @Test
    fun `修改预产期仅限ACTIVE`() = runTest {
        val pid = mom()
        val id = pregnancies.create(pid, due, null, today).ok()
        pregnancies.updateDates(id, due.plusDays(7), null, today).ok()
        assertEquals(due.plusDays(7), pregnancies.get(id)!!.dueDate)
        pregnancies.end(id, today).ok()
        assertErr(
            pregnancies.updateDates(id, due, null, today),
            FamilyError.PregnancyNotFound
        )
        assertErr(
            pregnancies.updateDates("ghost", due, null, today),
            FamilyError.PregnancyNotFound
        )
    }

    @Test
    fun `结束孕期归档未完成产检`() = runTest {
        val pid = mom()
        val pregId = pregnancies.create(pid, due, null, today).ok()
        // 三种未完成产检：今天、已逾期、未来；一条已办；一条其他孕期/无孕期
        val dueToday = tasks.save(taskDraft(pid, pregId, today)).ok()
        val overdue = tasks.save(taskDraft(pid, pregId, today.minusDays(2))).ok()
        val future = tasks.save(taskDraft(pid, pregId, today.plusDays(10))).ok()
        val doneTask = tasks.save(taskDraft(pid, pregId, today.minusDays(1))).ok()
        tasks.markDone(doneTask, today.minusDays(1)).ok()
        // 无孕期事项不受影响
        val normal = tasks.save(
            TaskDraft(personId = pid, kind = TaskKind.CHECKUP, title = "体检",
                anchorDate = today, repeat = TaskRepeat.NONE,
                offsets = setOf(0))).ok()
        pregnancies.end(pregId, today).ok()

        // 家事页读路径不再看到归档事项
        val visible = tasks.getTasks().map { it.id }
        assertTrue(dueToday !in visible)
        assertTrue(overdue !in visible)
        assertTrue(future !in visible)
        assertTrue(doneTask in visible) // 已办不归档
        assertTrue(normal in visible)
        // 孕期历史仍能查到全部关联事项（含归档与已办）
        val linked = tasks.observeTasksForPregnancy(pregId).first()
        assertEquals(setOf(dueToday, overdue, future, doneTask),
            linked.map { it.id }.toSet())
        assertTrue(linked.filter { it.id != doneTask }.all { it.archived })
        assertFalse(linked.first { it.id == doneTask }.archived)
        // 状态变为 ENDED 且记了结束日期
        val p = pregnancies.get(pregId)!!
        assertEquals(PregnancyStatus.ENDED, p.status)
        assertEquals(today, p.endedOn)
    }

    @Test
    fun `删除孕期连产检一起删`() = runTest {
        val pid = mom()
        val pregId = pregnancies.create(pid, due, null, today).ok()
        val t1 = tasks.save(taskDraft(pid, pregId, today)).ok()
        tasks.save(taskDraft(pid, pregId, today.plusDays(5))).ok()
        pregnancies.delete(pregId).ok()
        assertNull(pregnancies.get(pregId))
        assertNull(tasks.getTask(t1))
        assertEquals(0, db.familyTaskDao().getAll().size)
    }

    @Test
    fun `删孕妈人物级联孕期与产检`() = runTest {
        val me = repo.createSelf(PersonDraft("我", Gender.MALE)).ok()
        val pid = repo.createRelative(
            PersonDraft("李雪", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val pregId = pregnancies.create(pid, due, null, today).ok()
        tasks.save(taskDraft(pid, pregId, today)).ok()
        repo.deletePerson(pid)
        assertNull(pregnancies.get(pregId))
        assertEquals(0, db.familyTaskDao().getAll().size)
    }

    @Test
    fun `结束不存在或非ACTIVE的孕期报错`() = runTest {
        assertErr(pregnancies.end("ghost", today), FamilyError.PregnancyNotFound)
        val pid = mom()
        val id = pregnancies.create(pid, due, null, today).ok()
        pregnancies.end(id, today).ok()
        assertErr(pregnancies.end(id, today), FamilyError.PregnancyNotFound)
    }

    @Test
    fun `observeActive只返回进行中的`() = runTest {
        val me = repo.createSelf(PersonDraft("我", Gender.MALE)).ok()
        val pid = repo.createRelative(
            PersonDraft("李雪", Gender.FEMALE), me, RelationRole.SISTER).ok()
        pregnancies.create(pid, due, null, today).ok()
        val pid2 = repo.createRelative(
            PersonDraft("王芳", Gender.FEMALE), me, RelationRole.SISTER).ok()
        pregnancies.create(pid2, due, null, today).ok()
        assertEquals(2, pregnancies.observeActive().first().size)
    }
}
