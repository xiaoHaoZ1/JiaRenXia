package io.github.xiaohaoz1.jiarenxia.ui.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupCrypto
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 备份导出共用流程（ui/backup/BackupExportFlow）：我的页与关系图共用。
 * 成功写 SettingsRepository.lastBackupAt；保存对话框取消时清空暂存密码并复位；
 * 导出失败给出提示。ConscryptMode.OFF 同 BackupManagerTest。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
@OptIn(ExperimentalCoroutinesApi::class)
class BackupExportFlowTest : RepoTestBase() {

    private lateinit var ctx: Context
    private lateinit var settings: FakeSettingsRepository
    private val messages = mutableListOf<String>()
    private lateinit var flow: BackupExportFlow

    @Before
    fun setUpFlow() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ctx = ApplicationProvider.getApplicationContext()
        settings = FakeSettingsRepository()
        flow = BackupExportFlow(
            scope = CoroutineScope(Dispatchers.Main),
            backup = BackupManager(ctx, db, settings),
            settings = settings,
            onMessage = { messages += it }
        )
    }

    @After
    fun tearDownFlow() = Dispatchers.resetMain()

    /** 等导出协程收尾（加密写盘在 IO 上）。 */
    private fun waitDone(): BackupExportStep {
        val deadline = System.currentTimeMillis() + 10_000
        while (flow.step.value is BackupExportStep.Working &&
            System.currentTimeMillis() < deadline
        ) Thread.sleep(50)
        return flow.step.value
    }

    @Test
    fun 导出成功_写入上次备份时间() = runTest {
        createSelf()
        assertNull(settings.lastBackupAt.first())
        val out = File(ctx.cacheDir, "be_ok.jrxb")
        out.delete()

        flow.start()
        assertEquals(BackupExportStep.SetPassword, flow.step.value)
        flow.submitPassword("pw123456".toCharArray())
        assertEquals(BackupExportStep.PickExportTarget, flow.step.value)
        flow.onTarget(Uri.fromFile(out), ctx)
        assertEquals(BackupExportStep.Idle, waitDone())

        assertTrue("导出文件未生成", out.isFile && out.length() > 0)
        BackupCrypto.decrypt(out.readBytes(), "pw123456".toCharArray())
        assertNotNull("成功导出后应写入 lastBackupAt", settings.lastBackupAt.first())
        assertTrue("已备份 1 位家人" in messages)
    }

    /** 保存对话框取消（uri=null）：暂存密码清空、状态复位、不写备份时间。 */
    @Test
    fun 保存对话框取消_清空密码并复位() = runTest {
        createSelf()
        flow.submitPassword("pw123456".toCharArray())
        assertEquals(BackupExportStep.PickExportTarget, flow.step.value)

        flow.onTarget(null, ctx)
        assertEquals(BackupExportStep.Idle, flow.step.value)
        assertNull(settings.lastBackupAt.first())

        // 暂存密码已清空：之后即使直接给目标 uri 也不会导出文件
        val out = File(ctx.cacheDir, "be_cancel.jrxb")
        out.delete()
        flow.onTarget(Uri.fromFile(out), ctx)
        assertEquals(BackupExportStep.Idle, flow.step.value)
        assertFalse("取消后不应写出备份文件", out.exists())
        assertNull(settings.lastBackupAt.first())
    }

    @Test
    fun 导出失败_给出提示() = runTest {
        createSelf()
        flow.submitPassword("pw123456".toCharArray())
        // 打不开的 content Uri → openOutputStream 抛异常
        flow.onTarget(Uri.parse("content://jrx.invalid.test/nope.jrxb"), ctx)
        assertEquals(BackupExportStep.Idle, waitDone())

        assertTrue("备份失败，请重试" in messages)
        assertNull("失败的导出不写 lastBackupAt", settings.lastBackupAt.first())
    }

    // ---------- lastBackupSubtitle / backupFileName ----------

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 10, 6)
    private fun millisOf(day: LocalDate): Long =
        day.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun 副标题_从没备份过() {
        assertEquals("还没有备份过", lastBackupSubtitle(null, today, zone))
    }

    @Test
    fun 副标题_当天与N天前() {
        assertEquals(
            "上次备份：2026-10-06（今天）",
            lastBackupSubtitle(millisOf(LocalDate.of(2026, 10, 6)), today, zone)
        )
        assertEquals(
            "上次备份：2026-10-05（1 天前）",
            lastBackupSubtitle(millisOf(LocalDate.of(2026, 10, 5)), today, zone)
        )
        assertEquals(
            "上次备份：2026-09-29（7 天前）",
            lastBackupSubtitle(millisOf(LocalDate.of(2026, 9, 29)), today, zone)
        )
    }

    @Test
    fun 默认文件名格式() {
        assertTrue(
            backupFileName().matches(Regex("家人侠备份_\\d{8}_\\d{4}\\.jrxb"))
        )
    }
}
