package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.TaskKind
import io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 家庭事项仓库：CRUD、整组替换 offsets、办完推进/撤销、校验、级联与清空。 */
class FamilyTaskRepositoryTest : RepoTestBase() {

    private lateinit var tasks: FamilyTaskRepositoryImpl
    private var seq = 1000

    @org.junit.Before
    fun setUpTasks() {
        tasks = FamilyTaskRepositoryImpl(
            db = db,
            ids = object : IdGenerator { override fun newId() = "tid-" + (++seq) },
            clock = object : TimeSource { override fun now() = now }
        )
    }

    private fun draft(
        personId: String? = null,
        kind: TaskKind = TaskKind.CHECKUP,
        title: String = "体检",
        anchor: LocalDate = LocalDate.of(2025, 10, 20),
        repeat: TaskRepeat = TaskRepeat.YEARLY,
        offsets: Set<Int> = setOf(7, 1)
    ) = TaskDraft(
        personId = personId, kind = kind, title = title,
        anchorDate = anchor, repeat = repeat, offsets = offsets
    )

    @Test
    fun 新增与读取() = runTest {
        val id = tasks.save(draft(title = "妈妈的体检")).ok()
        val t = tasks.getTask(id)!!
        assertEquals("妈妈的体检", t.title)
        assertEquals(TaskKind.CHECKUP, t.kind)
        assertEquals(setOf(7, 1), t.offsets)
        assertEquals(0, t.periodIndex)
        assertEquals(true, t.enabled)
        assertEquals(1, tasks.getTasks().size)
    }

    @Test
    fun 更新整组替换offsets() = runTest {
        val id = tasks.save(draft(offsets = setOf(7, 1))).ok()
        tasks.save(
            draft(title = "改过的体检", anchor = LocalDate.of(2026, 1, 1)).copy(
                id = id, repeat = TaskRepeat.MONTHLY
            ).let { it.copy(offsets = setOf(0)) }
        )
        val t = tasks.getTask(id)!!
        assertEquals("改过的体检", t.title)
        assertEquals(TaskRepeat.MONTHLY, t.repeat)
        assertEquals(setOf(0), t.offsets)
        // 旧偏移行被替换而非累加
        assertEquals(1, db.familyTaskOffsetDao().getForTask(id).size)
    }

    @Test
    fun 删除() = runTest {
        val id = tasks.save(draft()).ok()
        tasks.delete(id)
        assertNull(tasks.getTask(id))
        assertEquals(0, db.familyTaskOffsetDao().getAll().size)
    }

    @Test
    fun 校验() = runTest {
        assertErr(tasks.save(draft(title = "")), FamilyError.InvalidTaskTitle)
        assertErr(tasks.save(draft(title = "太" + "长".repeat(40))),
            FamilyError.InvalidTaskTitle)
        assertErr(tasks.save(draft(offsets = emptySet())),
            FamilyError.InvalidReminderOffsets)
        assertErr(tasks.save(draft(offsets = setOf(2))),
            FamilyError.InvalidReminderOffsets)
        assertErr(tasks.save(draft(personId = "不存在")),
            FamilyError.PersonNotFound)
        assertErr(
            tasks.save(draft().copy(amountFen = -5)),
            FamilyError.InvalidTaskAmount
        )
        assertErr(
            tasks.save(draft().copy(note = "长".repeat(201))),
            FamilyError.InvalidTaskNote
        )
    }

    @Test
    fun 办完_不重复进已办() = runTest {
        val id = tasks.save(
            draft(kind = TaskKind.ID_EXPIRY, repeat = TaskRepeat.NONE)
        ).ok()
        val today = LocalDate.of(2025, 10, 25)
        tasks.markDone(id, today).ok()
        val t = tasks.getTask(id)!!
        assertEquals(true, t.done)
        assertEquals(today, t.lastDoneDate)
    }

    @Test
    fun 办完_每年推进到下一期() = runTest {
        val id = tasks.save(draft(anchor = LocalDate.of(2025, 10, 20))).ok()
        // 2025-10-25 办完 → 当前期仍是 2025-10-20（已过）→ 下一期 2026-10-20
        tasks.markDone(id, LocalDate.of(2025, 10, 25)).ok()
        val t = tasks.getTask(id)!!
        assertEquals(false, t.done)
        assertEquals(1, t.periodIndex)
        assertEquals(LocalDate.of(2026, 10, 20), io.github.xiaohaoz1.jiarenxia.reminder.TaskSchedule.dueDate(t))
        assertEquals(LocalDate.of(2025, 10, 25), t.lastDoneDate)
    }

    @Test
    fun 办完_提前办理至少进一期() = runTest {
        val id = tasks.save(draft(anchor = LocalDate.of(2025, 10, 20))).ok()
        // 还没到 10-20 就办完 → 推进到 periodIndex=1（2026-10-20）
        tasks.markDone(id, LocalDate.of(2025, 10, 1)).ok()
        assertEquals(1, tasks.getTask(id)!!.periodIndex)
    }

    @Test
    fun 撤销恢复原状() = runTest {
        val id = tasks.save(
            draft(kind = TaskKind.ID_EXPIRY, repeat = TaskRepeat.NONE)
        ).ok()
        val before = tasks.getTask(id)!!
        tasks.markDone(id, LocalDate.now()).ok()
        tasks.restore(before).ok()
        val after = tasks.getTask(id)!!
        assertEquals(false, after.done)
        assertNull(after.lastDoneDate)
        assertEquals(before.offsets, after.offsets)
    }

    @Test
    fun observeForPerson只看本人() = runTest {
        val pid = repo.createSelf(PersonDraft(name = "我", gender = Gender.MALE)).ok()
        tasks.save(draft(personId = pid, title = "我的事"))
        tasks.save(draft(title = "全家的事"))
        val mine = tasks.observeTasksForPerson(pid).first()
        assertEquals(1, mine.size)
        assertEquals("我的事", mine[0].title)
    }

    @Test
    fun 删人物级联删事项() = runTest {
        val me = repo.createSelf(PersonDraft(name = "我", gender = Gender.MALE)).ok()
        val pid = repo.createRelative(
            PersonDraft(name = "妈", gender = Gender.FEMALE), me,
            io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.MOTHER
        ).ok()
        val tid = tasks.save(draft(personId = pid)).ok()
        repo.deletePerson(pid)
        assertNull(tasks.getTask(tid))
        assertEquals(0, db.familyTaskOffsetDao().getAll().size)
    }

    @Test
    fun 清空所有数据连事项一起清() = runTest {
        val pid = repo.createSelf(PersonDraft(name = "我", gender = Gender.MALE)).ok()
        tasks.save(draft(personId = pid))
        repo.clearAllData()
        assertEquals(0, tasks.getTasks().size)
        assertEquals(0, db.familyTaskOffsetDao().getAll().size)
    }

    @Test
    fun markDone不存在返回TaskNotFound() = runTest {
        assertErr(tasks.markDone("ghost", LocalDate.now()), FamilyError.TaskNotFound)
    }

    @Test
    fun 编辑已删除的事项不复活() = runTest {
        val id = tasks.save(draft()).ok()
        tasks.delete(id)
        assertErr(
            tasks.save(draft().copy(id = id)),
            FamilyError.TaskNotFound
        )
        assertNull(tasks.getTask(id))
    }

    @Test
    fun 撤销已删除的事项不复活() = runTest {
        val id = tasks.save(draft()).ok()
        val before = tasks.getTask(id)!!
        tasks.delete(id)
        // 撤销拿着提交前的旧快照；目标已删时不允许重建
        assertErr(tasks.restore(before), FamilyError.TaskNotFound)
        assertNull(tasks.getTask(id))
    }

    @Test
    fun 编辑保留办完推进的期数() = runTest {
        val id = tasks.save(draft(anchor = LocalDate.of(2025, 10, 20))).ok()
        // 办完推进到第 1 期后再编辑：periodIndex/lastDone 必须按事务内
        // 最新行保留，不能被编辑前的旧快照覆盖
        tasks.markDone(id, LocalDate.of(2025, 10, 25)).ok()
        tasks.save(draft(title = "改过的体检").copy(id = id)).ok()
        val t = tasks.getTask(id)!!
        assertEquals(1, t.periodIndex)
        assertEquals(LocalDate.of(2025, 10, 25), t.lastDoneDate)
        assertEquals(false, t.done)
    }

    @Test
    fun 撤销不覆盖期间的标题修改() = runTest {
        val id = tasks.save(
            draft(kind = TaskKind.ID_EXPIRY, repeat = TaskRepeat.NONE)
        ).ok()
        val before = tasks.getTask(id)!!
        tasks.markDone(id, LocalDate.now()).ok()
        // 办完快照之后、撤销之前的编辑必须保留
        tasks.save(
            draft(kind = TaskKind.ID_EXPIRY, title = "换证件",
                  repeat = TaskRepeat.NONE).copy(id = id)
                .let { it.copy(offsets = setOf(3)) }
        ).ok()
        tasks.restore(before).ok()
        val t = tasks.getTask(id)!!
        assertEquals(false, t.done)
        assertNull(t.lastDoneDate)
        // 期间的新标题与 offsets 不被旧快照覆盖
        assertEquals("换证件", t.title)
        assertEquals(setOf(3), t.offsets)
    }
}
