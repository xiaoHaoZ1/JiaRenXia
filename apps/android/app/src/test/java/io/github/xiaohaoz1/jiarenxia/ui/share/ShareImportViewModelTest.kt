package io.github.xiaohaoz1.jiarenxia.ui.share

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.data.AppEvents
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 共享导入页 ViewModel（FAMILY_AFFAIRS 16.3）：
 * “资料里没有我”增补流程与选人切换的状态清理。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
@OptIn(ExperimentalCoroutinesApi::class)
class ShareImportViewModelTest : RepoTestBase() {

    private lateinit var ctx: Context
    private lateinit var backup: BackupManager
    private lateinit var appEvents: AppEvents
    private lateinit var vm: ShareImportViewModel

    private val pw = "pw123456".toCharArray()

    @Before
    fun setUpVm() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ctx = ApplicationProvider.getApplicationContext()
        backup = BackupManager(ctx, db, FakeSettingsRepository())
        appEvents = AppEvents()
    }

    @After
    fun tearDownVm() = Dispatchers.resetMain()

    /**
     * 分享者视角：王秀兰（“我”）+ 丈夫杨建国。
     * 共享文件经 appEvents.pendingSharePlan 注入（等价“从备份恢复”分流）。
     */
    private suspend fun seedSharePlan() {
        val momId = repo.createSelf(
            PersonDraft("王秀兰", Gender.FEMALE)).ok()
        repo.createRelative(
            PersonDraft("杨建国", Gender.MALE), momId, RelationRole.HUSBAND).ok()
        val out = ByteArrayOutputStream()
        backup.exportShare(out, pw, sharedBy = momId)
        appEvents.pendingSharePlan = backup.inspect(
            ByteArrayInputStream(out.toByteArray()), pw).getOrThrow()
    }

    private fun newVm(): ShareImportViewModel {
        // 注入 plan 后再构造（init 里读取并清空 pendingSharePlan）
        vm = ShareImportViewModel(
            context = ctx, backup = backup, db = db, appEvents = appEvents,
            engine = KinshipEngine(), lunar = IcuLunarCalendar(),
            savedStateHandle = SavedStateHandle()
        )
        return vm
    }

    /** 等 busy/预览计算收尾（withContext(Default) 不在测试调度器上）。 */
    private fun waitQuiet() {
        val deadline = System.currentTimeMillis() + 10_000
        while (vm.ui.value.busy && System.currentTimeMillis() < deadline)
            Thread.sleep(50)
        assertFalse(vm.ui.value.busy)
    }

    /** init 里读库是异步的：等 present 完成（进入选人页）。 */
    private fun waitPresent() {
        val deadline = System.currentTimeMillis() + 10_000
        while (vm.ui.value.plan == null &&
            System.currentTimeMillis() < deadline
        ) Thread.sleep(50)
        assertNotNull("共享 plan 未呈现", vm.ui.value.plan)
        assertEquals(ShareImportUiState.Stage.CONFIRM, vm.ui.value.stage)
    }

    /**
     * 先“资料里没有我”再改选文件里的人：临时新人物不能残留
     * （importPlan/锚点/角色被 select 清掉），导入的“我”是所选的人。
     */
    @Test
    fun 增补后改选他人_临时人物不写入() = runTest {
        seedSharePlan()
        kotlinx.coroutines.withContext(Dispatchers.IO) { db.clearAllTables() }
        newVm()
        waitPresent()

        val momId = appEvents0MomId()
        vm.confirmNewSelf("新进", momId, RelationRole.SON)
        assertNotNull(vm.ui.value.importPlan)

        // 改选文件里的杨建国 → 增补状态应被清掉
        val dadId = vm.ui.value.plan!!.persons
            .first { it.name == "杨建国" }.id
        vm.select(dadId)
        assertNull(vm.ui.value.importPlan)

        vm.confirmImport()
        waitQuiet()
        // 无补全询问（杨建国无兄弟姐妹路径、无共同父母问题）
        assertNull(vm.ui.value.adoptSibling)
        assertNull(vm.ui.value.coParentId)
        assertNotNull(vm.ui.value.preview)
        vm.confirmPreview()
        waitQuiet()
        assertTrue(vm.ui.value.done)

        val persons = db.personDao().getAll()
        assertEquals(dadId, persons.single { it.isSelf }.id)
        assertTrue(persons.none { it.name == "新进" })
    }

    /**
     * 端到端合并（FAMILY_AFFAIRS 16.3/16.7）：妈妈的共享文件 → 选“我”→
     * “合并到我的家庭” → 妈妈与外婆两个候选 → 全勾 → 确认 →
     * 库里人数不变、person_alias 三行（纯认人、无其他新内容也能确认）；
     * 重放同一文件 → “没有新内容”。
     */
    @Test
    fun 已有数据合并_对应关系持久化() = runTest {
        // 文件方（妈妈手机）：王秀兰 + 儿子杨明 + 母亲李秀英
        val rMom = repo.createSelf(PersonDraft("王秀兰", Gender.FEMALE)).ok()
        repo.createRelative(PersonDraft("杨明", Gender.MALE),
            rMom, RelationRole.SON).ok()
        repo.createRelative(PersonDraft("李秀英", Gender.FEMALE),
            rMom, RelationRole.MOTHER).ok()
        val out = ByteArrayOutputStream()
        backup.exportShare(out, pw, sharedBy = rMom)
        val sharePlan = backup.inspect(
            ByteArrayInputStream(out.toByteArray()), pw).getOrThrow()

        // 本机（接收方）：我（杨明）+ 妈妈 + 外婆，id 与文件全不同
        kotlinx.coroutines.withContext(Dispatchers.IO) { db.clearAllTables() }
        val meId = repo.createSelf(PersonDraft("杨明", Gender.MALE)).ok()
        val momId = repo.createRelative(PersonDraft("王秀兰", Gender.FEMALE),
            meId, RelationRole.MOTHER).ok()
        // 外婆挂在妈妈下面，双方结构对称（妈妈/外婆是结构候选）
        repo.createRelative(PersonDraft("李秀英", Gender.FEMALE),
            momId, RelationRole.MOTHER).ok()

        appEvents.pendingSharePlan = sharePlan
        newVm()
        waitPresent()

        val remoteMeId = vm.ui.value.plan!!.persons
            .first { it.name == "杨明" }.id
        vm.select(remoteMeId)
        vm.enterMerge()
        waitQuiet()
        assertEquals(ShareImportUiState.Stage.MERGE, vm.ui.value.stage)
        val plan1 = vm.ui.value.mergePlan
        assertNotNull(plan1)
        // 杨明由 anchors 认回；妈妈与外婆是结构候选
        assertEquals(2, plan1!!.candidates.size)

        plan1.candidates.forEach {
            vm.toggleCandidate(it.remoteId)
            waitQuiet()
        }
        val plan2 = vm.ui.value.mergePlan!!
        assertTrue(plan2.newPersons.isEmpty())
        // 纯认人：没有库内改动，但有 3 条新对应关系 → 可确认
        assertFalse(plan2.hasWrites())
        assertEquals(3, vm.ui.value.mergeNewAliases)

        vm.confirmMergeImport()
        waitQuiet()
        assertTrue(vm.ui.value.done)

        // 人数不变、无重复；对方→本机对应关系 3 行
        assertEquals(3, db.personDao().getAll().size)
        val aliases = db.personAliasDao().getAll()
        assertEquals(3, aliases.size)
        assertEquals(meId, aliases
            .first { it.remotePersonId == remoteMeId }.localPersonId)

        // 重放同一文件：person_alias 全认回 → “没有新内容”
        appEvents.pendingSharePlan = sharePlan
        newVm()
        waitPresent()
        vm.select(remoteMeId)
        vm.enterMerge()
        waitQuiet()
        assertTrue(vm.ui.value.mergePlan!!.nothingToShow())
        assertEquals(0, vm.ui.value.mergeNewAliases)
        vm.finishMerge()
        assertTrue(vm.ui.value.done)
    }

    /** 分享文件里“我”（王秀兰）的 personId。 */
    private fun appEvents0MomId(): String =
        vm.ui.value.plan!!.persons.first { it.name == "王秀兰" }.id
}
