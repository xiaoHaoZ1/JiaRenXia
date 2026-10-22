package io.github.xiaohaoz1.jiarenxia.ui.share

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.backup.TestFamily
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranch
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * “选择要分享的家人”ViewModel（FAMILY_AFFAIRS 9 部分分享）：
 * 分支预设、“我”不可取消、断开提醒、生成的文件只含所选人物且默认无备注。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
@OptIn(ExperimentalCoroutinesApi::class)
class ShareScopeViewModelTest : RepoTestBase() {

    private lateinit var ctx: Context
    private lateinit var backup: BackupManager
    private lateinit var vm: ShareScopeViewModel
    private lateinit var ids: Map<String, String>

    private val pw = "pw123456".toCharArray()

    @Before
    fun setUpVm() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ctx = ApplicationProvider.getApplicationContext()
        backup = BackupManager(ctx, db, FakeSettingsRepository())
    }

    @After
    fun tearDownVm() = Dispatchers.resetMain()

    private suspend fun seed() {
        ids = TestFamily.seed(repo, db)
    }

    private fun newVm(branch: GraphBranch = GraphBranch.ALL): ShareScopeViewModel {
        vm = ShareScopeViewModel(
            context = ctx,
            observeOverview = ObserveFamilyOverview(
                repo, KinshipEngine()
            ),
            repository = repo,
            backup = backup,
            savedStateHandle = SavedStateHandle(mapOf("branch" to branch.name))
        )
        return vm
    }

    /** 总览加载是异步的（Dispatchers.Default）：等 loading 结束。 */
    private fun waitLoaded() {
        val deadline = System.currentTimeMillis() + 10_000
        while (vm.ui.value.loading && System.currentTimeMillis() < deadline)
            Thread.sleep(50)
        assertFalse(vm.ui.value.loading)
    }

    /** 生成文件协程在 IO 上：等到 Ready 并取出文件。 */
    private fun waitReady(): java.io.File {
        val deadline = System.currentTimeMillis() + 10_000
        while (vm.ui.value.step !is ShareScopeStep.Ready &&
            System.currentTimeMillis() < deadline
        ) Thread.sleep(50)
        val step = vm.ui.value.step
        assertTrue("期望 Ready，实际 " + step, step is ShareScopeStep.Ready)
        return (step as ShareScopeStep.Ready).file
    }

    private suspend fun inspect(file: java.io.File) =
        backup.inspect(FileInputStream(file), pw).getOrThrow()

    @Test
    fun 母系分支预设正确() = runTest {
        seed()
        newVm(GraphBranch.MOTHER)
        waitLoaded()
        val expected = ShareScope.preset(
            ObserveFamilyOverview(
                repo, KinshipEngine()
            )().first()!!,
            GraphBranch.MOTHER
        )
        assertEquals(expected, vm.ui.value.selected)
        assertEquals(9, vm.ui.value.selected.size)
        assertEquals(GraphBranch.MOTHER, vm.ui.value.branch)
        // 列表条目是全部人物（可勾选的范围不受分支筛选限制）
        assertEquals(ids.size, vm.ui.value.items.size)
    }

    @Test
    fun 默认全部分支选中所有人() = runTest {
        seed()
        newVm()
        waitLoaded()
        assertEquals(ids.values.toSet(), vm.ui.value.selected)
    }

    @Test
    fun 我始终勾选不可取消() = runTest {
        seed()
        newVm()
        waitLoaded()
        val selfId = ids.getValue(TestFamily.SELF_NAME)
        vm.toggle(selfId)
        vm.toggle(selfId)
        assertTrue(selfId in vm.ui.value.selected)
    }

    @Test
    fun 取消中间人物给出断开提醒() = runTest {
        seed()
        newVm()
        waitLoaded()
        // 取消“王秀兰”（我的母亲）：母系一整支随之与“我”断开
        vm.toggle(ids.getValue("王秀兰"))
        vm.next()
        val step = vm.ui.value.step
        assertTrue("期望 Disconnected，实际 " + step,
            step is ShareScopeStep.Disconnected)
        val gone = (step as ShareScopeStep.Disconnected).ids
        // 母系一支 7 人全部断开（王秀兰是唯一的桥）；
        // “赵老师”本就与“我”无关联，恒在断开集合内
        assertEquals(
            setOf("王德福", "赵淑珍", "王志刚", "孙丽", "王磊", "陈晨",
                "王小宝", "赵老师").map { ids.getValue(it) }.toSet(),
            gone
        )
        // 父系仍然连着“我”，不在断开列表里
        assertFalse(ids.getValue("杨建国") in gone)

        // 返回补选：回到勾选且勾选不变
        vm.backToPicking()
        assertEquals(ShareScopeStep.Picking, vm.ui.value.step)
        assertFalse(ids.getValue("王秀兰") in vm.ui.value.selected)

        // 仍然分享：进密码步骤
        vm.next(); vm.shareAnyway()
        assertEquals(ShareScopeStep.SetPassword, vm.ui.value.step)
    }

    @Test
    fun 密码取消回到勾选() = runTest {
        seed()
        newVm()
        waitLoaded()
        // 取消与“我”无关联的“赵老师”，否则下一步先弹断开提醒
        vm.toggle(ids.getValue("赵老师"))
        vm.next()
        assertEquals(ShareScopeStep.SetPassword, vm.ui.value.step)
        vm.dismissPassword()
        assertEquals(ShareScopeStep.Picking, vm.ui.value.step)
    }

    @Test
    fun 生成文件只含所选人物且默认无备注() = runTest {
        seed()
        // 给所选内的人（“我”）加备注，验证默认剔除
        val selfId = ids.getValue(TestFamily.SELF_NAME)
        db.personDao().update(
            db.personDao().getById(selfId)!!.copy(note = "本人私密备注"))
        newVm()
        waitLoaded()
        // 只留母系一支 + 我
        vm.selectBranch(GraphBranch.MOTHER)
        vm.submitPassword(pw)
        val file = waitReady()

        // 文件名带“部分”
        assertTrue(file.name.contains("-部分-"))
        val plan = inspect(file)
        assertEquals("share", plan.purpose)
        assertEquals(selfId, plan.sharedBy)
        assertEquals(vm.ui.value.selected, plan.persons.map { it.id }.toSet())
        // 默认不含备注；部分分享不带事项
        assertTrue(plan.persons.all { it.note == null })
        assertTrue(plan.phones.all { it.note == null })
        assertTrue(plan.importantDates.all { it.note == null })
        assertTrue(plan.tasks.isEmpty())
        // 自定义称呼不带
        assertTrue(plan.customTitles.isEmpty())

        vm.shareSent()
        assertEquals(ShareScopeStep.Picking, vm.ui.value.step)
    }

    @Test
    fun 仅本机保留的人不预选但可手勾() = runTest {
        seed()
        // 把“赵老师”标为仅本机保留
        val zhao = ids.getValue("赵老师")
        db.familyLocalOnlyDao().upsert(
            io.github.xiaohaoz1.jiarenxia.data.db.FamilyLocalOnlyEntity(
                personId = zhao, canonicalId = null,
                note = "测试", createdAt = 1L))
        newVm()
        waitLoaded()
        // 预选里没有仅本机的人（其余人都在）
        assertFalse(zhao in vm.ui.value.selected)
        assertEquals(ids.values.toSet() - zhao, vm.ui.value.selected)
        assertEquals(setOf(zhao), vm.ui.value.localOnlyIds)
        // 手动勾选 = 知情选择，仍可分享
        vm.toggle(zhao)
        assertTrue(zhao in vm.ui.value.selected)
    }

    @Test
    fun 全选生成文件名不带部分标记() = runTest {
        seed()
        newVm()
        waitLoaded()
        // 取消与“我”无关联的“赵老师”前会弹断开提醒；直接仍然分享
        vm.next()
        when (vm.ui.value.step) {
            is ShareScopeStep.Disconnected -> vm.shareAnyway()
            else -> {}
        }
        assertEquals(ShareScopeStep.SetPassword, vm.ui.value.step)
        vm.submitPassword(pw)
        val file = waitReady()
        // 覆盖了全部非仅本机人物 → 不带“部分”
        assertFalse(file.name.contains("-部分-"))
        assertTrue(file.name.contains("家人侠-家人共享-"))
    }

    @Test
    fun 断开集合提交时不报错且集合外不导出() = runTest {
        seed()
        newVm()
        waitLoaded()
        vm.toggle(ids.getValue("王秀兰"))
        vm.next()
        vm.shareAnyway()
        vm.submitPassword(pw)
        val plan = inspect(waitReady())
        // 断开的人也写进了文件（用户选择“仍然分享”）
        assertTrue(
            ids.getValue("王磊") in plan.persons.map { it.id })
        assertFalse(
            ids.getValue("王秀兰") in plan.persons.map { it.id })
    }
}
