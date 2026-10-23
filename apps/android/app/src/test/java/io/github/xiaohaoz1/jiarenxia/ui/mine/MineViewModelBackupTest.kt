package io.github.xiaohaoz1.jiarenxia.ui.mine

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.domain.ClearAllDataUseCase
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupCrypto
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupJson
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import io.github.xiaohaoz1.jiarenxia.reminder.ReminderScheduler
import io.github.xiaohaoz1.jiarenxia.ui.backup.BackupExportStep
import io.github.xiaohaoz1.jiarenxia.ui.restore.RestoreStep
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 备份密码数组别名回归（V0.2 缺陷）。
 * 对话框提交后立即 `fill(' ')` 清空它持有的数组；ViewModel 必须 copyOf 自保，
 * 否则随后加密时拿到的是全空格密码（老版本备份全是这样导出的）。
 * ConscryptMode.OFF 同 BackupManagerTest。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
@OptIn(ExperimentalCoroutinesApi::class)
class MineViewModelBackupTest : RepoTestBase() {

    private lateinit var vm: MineViewModel
    private lateinit var ctx: Context
    private lateinit var appEvents: io.github.xiaohaoz1.jiarenxia.data.AppEvents

    private class FakeSettings : SettingsRepository {
        override val remindersEnabled: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setRemindersEnabled(enabled: Boolean) {}
        override val taskRemindersEnabled = MutableStateFlow(true)
    override val contactRemindersEnabled = MutableStateFlow(true)
        override suspend fun setTaskRemindersEnabled(enabled: Boolean) {
            taskRemindersEnabled.value = enabled
        }
    override suspend fun setContactRemindersEnabled(enabled: Boolean) {
        contactRemindersEnabled.value = enabled
    }


        override val notificationPermissionRequested: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setNotificationPermissionRequested(requested: Boolean) {}
        override val themeMode: Flow<ThemeMode> = MutableStateFlow(ThemeMode.LIGHT)
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override val fontScaleLevel =
            MutableStateFlow(io.github.xiaohaoz1.jiarenxia.data.settings.FontScaleLevel.STANDARD)
        override suspend fun setFontScaleLevel(l: io.github.xiaohaoz1.jiarenxia.data.settings.FontScaleLevel) {}
        override val reminderMinuteOfDay = kotlinx.coroutines.flow.MutableStateFlow(540)
        override suspend fun setReminderMinuteOfDay(minute: Int) {
            reminderMinuteOfDay.value = minute
        }
        override val taskReminderMinuteOfDay = kotlinx.coroutines.flow.MutableStateFlow(540)
        override suspend fun setTaskReminderMinuteOfDay(minute: Int) {
            taskReminderMinuteOfDay.value = minute
        }
        override val contactReminderMinuteOfDay = kotlinx.coroutines.flow.MutableStateFlow(540)
        override suspend fun setContactReminderMinuteOfDay(minute: Int) {
            contactReminderMinuteOfDay.value = minute
        }
        override val lastBackupAt = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
        override suspend fun setLastBackupAt(atMillis: Long) { lastBackupAt.value = atMillis }
        override suspend fun clear() {}
    }

    private object FakeScheduler : ReminderScheduler {
        override suspend fun reconcile() {}
        override fun cancelAll() {}
        override fun enqueueDebugRun() {}
    }

    @Before
    fun setUpVm() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ctx = ApplicationProvider.getApplicationContext()
        val settings = FakeSettings()
        appEvents = io.github.xiaohaoz1.jiarenxia.data.AppEvents()
        vm = MineViewModel(
            repository = repo,
            settings = settings,
            scheduler = FakeScheduler,
            clearAll = ClearAllDataUseCase(
                ctx, repo, settings, FakeScheduler,
                io.github.xiaohaoz1.jiarenxia.data.tap
                    .RoomSpousePairingStore(db),
                io.github.xiaohaoz1.jiarenxia.data.backup
                    .QuickSnapshotStore(ctx, db, BackupManager(ctx, db, settings))
            ),
            backup = BackupManager(ctx, db, settings),
            appEvents = appEvents
        )
    }

    @After
    fun tearDownVm() = Dispatchers.resetMain()

    /** 等导出协程收尾（withContext(IO) 不在测试调度器上）。 */
    private fun waitIdle() {
        val deadline = System.currentTimeMillis() + 10_000
        while (vm.backupExport.step.value != BackupExportStep.Idle &&
            System.currentTimeMillis() < deadline
        ) Thread.sleep(50)
        assertEquals(BackupExportStep.Idle, vm.backupExport.step.value)
    }

    /** 等恢复协程离开 Working（解密/恢复都在 IO 上）。 */
    private fun waitRestoreDone(): RestoreStep {
        val deadline = System.currentTimeMillis() + 10_000
        while (vm.restore.step.value is RestoreStep.Working &&
            System.currentTimeMillis() < deadline
        ) Thread.sleep(50)
        return vm.restore.step.value
    }

    @Test
    fun 导出密码不受对话框清空影响() = runTest {
        repo.createSelf(PersonDraft("我", Gender.MALE)).ok()
        val out = File(ctx.cacheDir, "alias_test.jrxb")
        out.delete()

        val callerArr = "pw123456".toCharArray()
        vm.backupExport.submitPassword(callerArr)
        callerArr.fill(' ')   // 复现 ExportPasswordDialog 的清空行为
        vm.backupExport.onTarget(Uri.fromFile(out), ctx)
        waitIdle()

        assertTrue("导出文件未生成: " + vm.ui.value.message, out.isFile && out.length() > 0)
        // 原密码必须能解密
        BackupCrypto.decrypt(out.readBytes(), "pw123456".toCharArray())
        // 而空格密码必须解不开（若 VM 复用了被清空的数组，这里反而能解开）
        try {
            BackupCrypto.decrypt(out.readBytes(), CharArray(8) { ' ' })
            fail("空格密码不应能解密新备份")
        } catch (expected: Exception) { /* 期望失败 */ }
    }

    @Test
    fun 恢复密码不受对话框清空影响() = runTest {
        // 先用正常密码导出一份备份（等价于正确生成的新版备份文件）
        repo.createSelf(PersonDraft("我", Gender.MALE)).ok()
        val src = File(ctx.cacheDir, "alias_restore.jrxb")
        java.io.FileOutputStream(src).use {
            BackupManager(ctx, db, FakeSettings()).export(it, "pw123456".toCharArray())
        }

        vm.restore.onSource(Uri.fromFile(src))
        assertEquals(RestoreStep.AskPassword, vm.restore.step.value)

        val callerArr = "pw123456".toCharArray()
        vm.restore.submitPassword(callerArr, ctx)
        callerArr.fill(' ')   // 复现 PasswordDialog 的清空行为

        // 旧实现解密的是被清空的数组 → 校验失败回到 Idle；修复后应停在 Confirm
        val step = waitRestoreDone()
        assertTrue("期望 Confirm，实际 " + step, step is RestoreStep.Confirm)
        // 普通备份不触发共享导入跳转
        assertFalse(vm.restore.shareDetected.value)
        vm.restore.dismiss()
        assertEquals(RestoreStep.Idle, vm.restore.step.value)
    }

    /** purpose 不是 backup/share 的文件：提示无法识别，不进 Confirm 也不走共享导入。 */
    @Test
    fun 恢复流程拒绝未知用途文件() = runTest {
        repo.createSelf(PersonDraft("我", Gender.MALE)).ok()
        val src = File(ctx.cacheDir, "unknown_purpose.jrxb")
        val zip = BackupJson.writeZip(
            persons = emptyList(), phones = emptyList(),
            relationships = emptyList(), dates = emptyList(),
            prefs = emptyList(), customTitles = emptyList(),
            settings = null, avatars = emptyMap(),
            createdAt = 0L, appVersion = "test",
            purpose = "future"
        )
        src.writeBytes(BackupCrypto.encrypt(zip, "pw123456".toCharArray()))
        val idsBefore = db.personDao().getAll().map { it.id }.toSet()

        vm.restore.onSource(Uri.fromFile(src))
        assertEquals(RestoreStep.AskPassword, vm.restore.step.value)
        vm.restore.submitPassword("pw123456".toCharArray(), ctx)
        assertEquals(RestoreStep.Idle, waitRestoreDone())

        assertEquals(
            "无法识别这个文件，请确认是家人侠的备份或共享文件",
            vm.ui.first { it.message != null }.message
        )
        assertFalse(vm.restore.shareDetected.value)
        assertNull(appEvents.pendingSharePlan)
        assertEquals(idsBefore, db.personDao().getAll().map { it.id }.toSet())
    }
}

