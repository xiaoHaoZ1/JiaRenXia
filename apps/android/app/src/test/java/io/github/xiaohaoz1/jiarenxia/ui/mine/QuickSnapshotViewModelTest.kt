package io.github.xiaohaoz1.jiarenxia.ui.mine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.backup.QuickSnapshotStore
import io.github.xiaohaoz1.jiarenxia.domain.ClearAllDataUseCase
import io.github.xiaohaoz1.jiarenxia.ui.restore.RestoreOutcome
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 测试工具 ViewModel 测试：存档/读档流程、版本号连点开关、
 * “清空数据但保留存档”。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
@OptIn(ExperimentalCoroutinesApi::class)
class QuickSnapshotViewModelTest : RepoTestBase() {

    private lateinit var ctx: Context
    private lateinit var store: QuickSnapshotStore
    private lateinit var settings: FakeSettingsRepository
    private lateinit var vm: QuickSnapshotViewModel

    private val snapDir get() = File(ctx.filesDir, "quick_snapshots")

    @Before
    fun setUpVm() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ctx = ApplicationProvider.getApplicationContext()
        settings = FakeSettingsRepository()
        val backup = BackupManager(ctx, db, settings)
        store = QuickSnapshotStore(ctx, db, backup)
        vm = QuickSnapshotViewModel(store, settings, clearer())
    }

    @After
    fun tearDownVm() {
        Dispatchers.resetMain()
        snapDir.deleteRecursively()
    }

    private fun clearer() = ClearAllDataUseCase(
        ctx, repo, settings, SnapshotFakeScheduler,
        io.github.xiaohaoz1.jiarenxia.data.tap.RoomSpousePairingStore(db),
        store
    )

    /** 等某个条件成立（存档读写走真实 IO 线程）。 */
    private fun waitFor(desc: String, cond: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!kotlinx.coroutines.runBlocking { cond() } &&
            System.currentTimeMillis() < deadline
        ) Thread.sleep(50)
        assertTrue("等待超时: $desc", kotlinx.coroutines.runBlocking { cond() })
    }

    @Test
    fun 版本号连点七次_开启再关闭() = runTest {
        assertFalse(settings.testToolsEnabled.first())
        repeat(6) { vm.onVersionTap() }
        assertFalse("不到 7 次不应开启", settings.testToolsEnabled.first())
        vm.onVersionTap()
        assertTrue(settings.testToolsEnabled.first())
        vm.onMessageShown()
        repeat(7) { vm.onVersionTap() }
        assertFalse("再连点 7 次应关闭", settings.testToolsEnabled.first())
    }

    @Test
    fun 快速存档后列表可见_读档恢复() = runTest {
        createSelf("杨明")
        vm.save()
        waitFor("存档") { store.list().isNotEmpty() }
        waitFor("提示") { vm.message.value != null }

        vm.openList()
        waitFor("列表") { vm.snapshots.value.isNotEmpty() }
        val snap = vm.snapshots.value.first()
        assertEquals(1, snap.persons)
        assertEquals("杨明", snap.selfName)

        repo.clearAllData()
        assertTrue(db.personDao().getAll().isEmpty())

        vm.askRestore(snap)
        vm.confirmRestore()
        waitFor("恢复完成") { vm.outcome.value != null }
        assertEquals(RestoreOutcome.RESTORED_WITH_SELF, vm.outcome.value)
        assertEquals(1, db.personDao().getAll().size)
        assertFalse(vm.listOpen.value)
    }

    @Test
    fun 读档到没有我的存档_提示去创建我() = runTest {
        // 空库直接存档（没有“我”）
        vm.save()
        waitFor("存档") { store.list().isNotEmpty() }
        val snap = store.list().first()

        createSelf("杨明")
        vm.askRestore(snap)
        vm.confirmRestore()
        waitFor("恢复完成") { vm.outcome.value != null }
        assertEquals(RestoreOutcome.RESTORED_WITHOUT_SELF, vm.outcome.value)
        assertTrue(db.personDao().getAll().isEmpty())
    }

    @Test
    fun 非本机存档_读档提示且不覆盖() = runTest {
        createSelf("杨明")
        vm.save()
        waitFor("存档") { store.list().isNotEmpty() }
        vm.onMessageShown()   // 清掉“已存为快速存档”提示，避免干扰断言
        val snap = store.list().first()
        val meta = File(snapDir, snap.id + ".meta")
        meta.writeText(
            JSONObject(meta.readText()).put("deviceId", "other-device").toString()
        )

        vm.askRestore(snap)
        vm.confirmRestore()
        waitFor("提示") { vm.message.value != null }
        assertEquals("这份存档不是本机保存的", vm.message.value)
        assertNull(vm.outcome.value)
        assertEquals(1, db.personDao().getAll().size)
    }

    @Test
    fun 清空数据但保留存档_且保留入口开关() = runTest {
        createSelf("杨明")
        vm.save()
        waitFor("存档") { store.list().isNotEmpty() }
        settings.setTestToolsEnabled(true)

        vm.clearDataKeepSnapshots()
        waitFor("清空完成") { vm.cleared.value }

        assertTrue(db.personDao().getAll().isEmpty())
        assertEquals(1, store.list().size)
        assertTrue("测试工具开关应保留", settings.testToolsEnabled.first())
    }

    @Test
    fun 清空所有数据_存档一并删除() = runTest {
        createSelf("杨明")
        vm.save()
        waitFor("存档") { store.list().isNotEmpty() }

        clearer().run()
        assertTrue(store.list().isEmpty())
        assertFalse(snapDir.exists())
    }
}

private object SnapshotFakeScheduler : io.github.xiaohaoz1.jiarenxia.reminder.ReminderScheduler {
    override suspend fun reconcile() {}
    override fun cancelAll() {}
    override fun enqueueDebugRun() {}
}
