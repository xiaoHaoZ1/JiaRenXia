package io.github.xiaohaoz1.jiarenxia.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 生成可复用测试家庭备份（opt-in）：`-PtestFamily=true` 时写
 * `testdata/jrx_test_family.jrxb`（Android 工程根目录 apps/android，密码 test1234）。
 * 与 ReadmeShotsTest 一样，普通测试运行跳过生成；但仍走一遍导出+inspect
 * 自检（不写文件之外的部分始终执行）。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
class TestFamilyBackupTest : RepoTestBase() {

    private lateinit var backup: BackupManager
    private lateinit var ctx: Context

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
        override val fontScaleLevel = MutableStateFlow(
            io.github.xiaohaoz1.jiarenxia.data.settings.FontScaleLevel.STANDARD
        )
        override suspend fun setFontScaleLevel(
            l: io.github.xiaohaoz1.jiarenxia.data.settings.FontScaleLevel
        ) {}
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

    @Before
    fun setUpBackup() {
        ctx = ApplicationProvider.getApplicationContext()
        backup = BackupManager(ctx, db, FakeSettings())
    }

    @Test
    fun `测试家庭导出并可被 inspect`() = runTest {
        TestFamily.seed(repo, db)

        val dir = File(ctx.cacheDir, "testfamily").apply { mkdirs() }
        val file = File(dir, "jrx_test_family.jrxb")
        FileOutputStream(file).use {
            val summary = backup.export(it, TestFamily.PASSWORD.toCharArray())
            assertEquals(55, summary.persons)
            assertEquals(55, summary.relationships)
        }
        val plan = backup.inspect(
            FileInputStream(file), TestFamily.PASSWORD.toCharArray()
        ).getOrThrow()
        assertEquals(55, plan.summary.persons)
        assertEquals(55, plan.summary.relationships)
        assertEquals(TestFamily.SELF_NAME, plan.summary.selfName)
        // 事项标题回归（真机核对）：挂王秀兰的百万医疗缴费提醒
        val titles = db.familyTaskDao().getAll().map { it.title }
        assertTrue("妈妈的百万医疗险缴费" in titles)

        // opt-in：把文件落到 Android 工程根目录的 testdata/（Gradle 测试的 user.dir 是模块目录 app/）
        Assume.assumeTrue(
            "测试家庭备份仅在 -PtestFamily=true 时生成 testdata/jrx_test_family.jrxb",
            System.getProperty("jrx.testFamily") == "true"
        )
        val out = File(
            requireNotNull(File(System.getProperty("user.dir")).parentFile),
            "testdata/jrx_test_family.jrxb"
        )
        out.parentFile?.mkdirs()
        file.copyTo(out, overwrite = true)
        assertTrue(out.isFile && out.length() > 0)
    }
}
