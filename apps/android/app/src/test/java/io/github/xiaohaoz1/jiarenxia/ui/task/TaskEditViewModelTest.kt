package io.github.xiaohaoz1.jiarenxia.ui.task

import androidx.lifecycle.SavedStateHandle
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.TaskKind
import io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.IdGenerator
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.TaskDraft
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 事项编辑：防重复提交的进行中/终态守卫与失败重试。 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditViewModelTest : RepoTestBase() {

    private lateinit var tasks: FamilyTaskRepositoryImpl
    private var seq = 0

    @org.junit.After
    fun tearDownMain() = Dispatchers.resetMain()

    @org.junit.Before
    fun setUpTaskVm() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ids = object : IdGenerator { override fun newId() = "id-" + (++seq) }
        val clock = object : TimeSource { override fun now() = now }
        tasks = FamilyTaskRepositoryImpl(db, ids, clock)
    }

    private fun vm(args: Map<String, Any> = emptyMap()) = TaskEditViewModel(
        SavedStateHandle(args),
        tasks,
        repo,
        ObserveFamilyOverview(repo, KinshipEngine())
    )

    @Test
    fun 保存进行中重复调用只写一次() = runTest {
        createSelf()
        val vm = vm()
        vm.ui.first { it.persons.isNotEmpty() }
        vm.setTitle("给妈妈体检")
        vm.save()
        // 第一次写入未完成（saving=true）时再次提交必须被挡住
        vm.save()
        vm.ui.first { it.saved }
        assertEquals(1, db.familyTaskDao().count())
    }

    @Test
    fun 保存成功后终态拒绝再次保存() = runTest {
        createSelf()
        val vm = vm()
        vm.ui.first { it.persons.isNotEmpty() }
        vm.setTitle("给妈妈体检")
        vm.save()
        vm.ui.first { it.saved }
        // 导航完成前再次触发：saving 已复位，但 saved 终态挡下第二笔
        vm.save()
        assertEquals(1, db.familyTaskDao().count())
    }

    @Test
    fun 分享文本预填标题与日期并可保存() = runTest {
        createSelf()
        val vm = vm(mapOf("shared" to "明天记得去幼儿园接小宝"))
        val st = vm.ui.first { it.persons.isNotEmpty() }
        assertEquals("去幼儿园接小宝", st.title)
        assertEquals(java.time.LocalDate.now().plusDays(1), st.anchorDate)
        assertNull(st.remindMinutes)
        // 与手工新建同一条保存路径
        vm.save()
        vm.ui.first { it.saved }
        assertEquals(1, db.familyTaskDao().count())
    }

    @Test
    fun 分享文本预填提醒时刻() = runTest {
        createSelf()
        val vm = vm(mapOf("shared" to "明天中午提醒我吃板面"))
        val st = vm.ui.first { it.persons.isNotEmpty() }
        assertEquals("吃板面", st.title)
        assertEquals(java.time.LocalDate.now().plusDays(1), st.anchorDate)
        assertEquals(12 * 60, st.remindMinutes)
        // 时刻随保存落库
        vm.save()
        vm.ui.first { it.saved }
        assertEquals(12 * 60, db.familyTaskDao().getAll().first().remindMinutes)
    }

    @Test
    fun 标题非法失败后重试成功() = runTest {
        createSelf()
        val vm = vm()
        vm.ui.first { it.persons.isNotEmpty() }
        vm.setTitle(" ")
        vm.save()
        vm.ui.first { it.error != null && !it.saving }
        // 失败保留输入语义：改正标题后重试成功
        vm.setTitle("给妈妈体检")
        vm.save()
        vm.ui.first { it.saved }
        assertEquals(1, db.familyTaskDao().count())
    }

    @Test
    fun 不重复事项标记已办连同改动直接办完() = runTest {
        createSelf()
        val id = tasks.save(
            TaskDraft(
                kind = TaskKind.CUSTOM, title = "交物业费",
                anchorDate = LocalDate.now(), repeat = TaskRepeat.NONE
            )
        ).ok()
        val vm = vm(mapOf("taskId" to id))
        vm.ui.first { it.persons.isNotEmpty() }
        vm.setNote("已缴")
        vm.markDone()
        // 不重复事项不弹确认框，直接保存+办完
        assertNull(vm.ui.value.doneConfirm)
        vm.ui.first { it.saved }
        assertNull(vm.ui.value.doneConfirm)
        val e = db.familyTaskDao().get(id)!!
        assertTrue(e.done)
        assertEquals(LocalDate.now().toEpochDay(), e.lastDoneEpochDay)
        assertEquals("已缴", e.note)
    }

    @Test
    fun 重复事项先确认再办完() = runTest {
        createSelf()
        val anchor = LocalDate.now().minusDays(3)
        val id = tasks.save(
            TaskDraft(
                kind = TaskKind.CHECKUP, title = "年度体检",
                anchorDate = anchor, repeat = TaskRepeat.YEARLY
            )
        ).ok()
        val vm = vm(mapOf("taskId" to id))
        vm.ui.first { it.persons.isNotEmpty() }
        vm.markDone()
        // 先弹确认：本期 = anchor，下一期 = anchor+1 年；此时不写库
        assertEquals(DoneConfirm(anchor, anchor.plusYears(1)), vm.ui.value.doneConfirm)
        assertFalse(vm.ui.value.saved)
        var e = db.familyTaskDao().get(id)!!
        assertEquals(0, e.periodIndex)
        assertNull(e.lastDoneEpochDay)
        vm.confirmMarkDone()
        vm.ui.first { it.saved }
        e = db.familyTaskDao().get(id)!!
        assertEquals(1, e.periodIndex)
        assertFalse(e.done)
        assertEquals(LocalDate.now().toEpochDay(), e.lastDoneEpochDay)
    }

    @Test
    fun 取消确认不写库() = runTest {
        createSelf()
        val anchor = LocalDate.now().minusDays(3)
        val id = tasks.save(
            TaskDraft(
                kind = TaskKind.CHECKUP, title = "年度体检",
                anchorDate = anchor, repeat = TaskRepeat.YEARLY
            )
        ).ok()
        val vm = vm(mapOf("taskId" to id))
        vm.ui.first { it.persons.isNotEmpty() }
        vm.markDone()
        assertEquals(DoneConfirm(anchor, anchor.plusYears(1)), vm.ui.value.doneConfirm)
        vm.dismissDoneConfirm()
        assertNull(vm.ui.value.doneConfirm)
        assertFalse(vm.ui.value.saved)
        val e = db.familyTaskDao().get(id)!!
        assertEquals(0, e.periodIndex)
        assertNull(e.lastDoneEpochDay)
    }

    @Test
    fun 金额非法时标记已办报错不写库() = runTest {
        createSelf()
        val id = tasks.save(
            TaskDraft(
                kind = TaskKind.CUSTOM, title = "交物业费",
                anchorDate = LocalDate.now(), repeat = TaskRepeat.NONE
            )
        ).ok()
        val vm = vm(mapOf("taskId" to id))
        vm.ui.first { it.persons.isNotEmpty() }
        vm.setAmount("1.2.3")
        vm.markDone()
        // 与保存同口径的校验错误；不弹确认、不写库
        assertEquals("金额不正确", vm.ui.value.error)
        assertNull(vm.ui.value.doneConfirm)
        assertFalse(vm.ui.value.saved)
        assertFalse(db.familyTaskDao().get(id)!!.done)
    }

    @Test
    fun 新建与已办完的事项不能标记已办() = runTest {
        createSelf()
        // 新建（taskId == null）：canMarkDone == false，markDone 直接返回
        val vmNew = vm()
        vmNew.ui.first { it.persons.isNotEmpty() }
        assertFalse(vmNew.ui.value.canMarkDone)
        vmNew.markDone()
        assertFalse(vmNew.ui.value.saved)
        assertEquals(0, db.familyTaskDao().count())

        // 已办完的不重复事项：canMarkDone == false，markDone 不写库
        val id = tasks.save(
            TaskDraft(
                kind = TaskKind.CUSTOM, title = "交物业费",
                anchorDate = LocalDate.now(), repeat = TaskRepeat.NONE
            )
        ).ok()
        tasks.markDone(id, LocalDate.now()).ok()
        val vmDone = vm(mapOf("taskId" to id))
        vmDone.ui.first { it.persons.isNotEmpty() }
        assertFalse(vmDone.ui.value.canMarkDone)
        vmDone.markDone()
        assertFalse(vmDone.ui.value.saved)
        val e = db.familyTaskDao().get(id)!!
        assertTrue(e.done)
        assertEquals(0, e.periodIndex)
        assertEquals(LocalDate.now().toEpochDay(), e.lastDoneEpochDay)
    }
}
