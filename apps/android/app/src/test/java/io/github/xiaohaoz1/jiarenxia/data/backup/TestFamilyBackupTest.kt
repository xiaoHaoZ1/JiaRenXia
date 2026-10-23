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

    /**
     * 同一家庭的英文名变体（`-PtestFamily=true` 时写
     * `testdata/jrx_test_family_en.jrxb`，密码同为 test1234）：
     * 全员改英文名、设为健在（并删掉爷爷的农历忌日），供 README/官网等
     * 对外真机截图使用，不对应真实家庭。
     */
    @Test
    fun `英文名测试家庭导出`() = runTest {
        Assume.assumeTrue(
            "英文名备份仅在 -PtestFamily=true 时生成 testdata/jrx_test_family_en.jrxb",
            System.getProperty("jrx.testFamily") == "true"
        )
        TestFamily.seed(repo, db)
        val persons = db.personDao().getAll()
        persons.forEach { p ->
            val en = ENGLISH_NAMES[p.name] ?: error("缺少英文名映射：${p.name}")
            db.personDao().update(
                p.copy(
                    name = en,
                    livingStatus = io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.ALIVE
                )
            )
        }
        db.importantDateDao().deleteByPersonAndType(
            persons.first { it.name == "杨福生" }.id,
            io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType.DEATH_ANNIVERSARY.name
        )
        val file = File(ctx.cacheDir, "jrx_test_family_en.jrxb")
        FileOutputStream(file).use {
            val summary = backup.export(it, TestFamily.PASSWORD.toCharArray())
            assertEquals(55, summary.persons)
        }
        val out = File(
            requireNotNull(File(System.getProperty("user.dir")).parentFile),
            "testdata/jrx_test_family_en.jrxb"
        )
        out.parentFile?.mkdirs()
        file.copyTo(out, overwrite = true)
        assertTrue(out.isFile && out.length() > 0)
    }

    private companion object {
        /** 中文姓名 → 英文名（覆盖 TestFamily 全员，含“我”）。 */
        val ENGLISH_NAMES = mapOf(
            "杨明" to "Alex", "李雪" to "Lily", "杨建国" to "David",
            "王秀兰" to "Emma", "杨福生" to "George", "李桂英" to "Grace",
            "杨老太爷" to "Walter", "杨福来" to "Frank", "杨福英" to "Dorothy",
            "李桂林" to "Victor", "杨建春" to "James", "杨建英" to "Alice",
            "周丽" to "Rose", "杨磊" to "Leo", "刘静" to "Anna",
            "杨朵朵" to "Ruby", "杨建华" to "Carl", "杨建业" to "Dennis",
            "赵红" to "Nancy", "张建明" to "Peter", "李秀梅" to "Betty",
            "王德福" to "Harold", "赵淑珍" to "Helen", "王志刚" to "Jack",
            "孙丽" to "Julia", "王磊" to "Ryan", "陈晨" to "Sophie",
            "王小宝" to "Ethan", "杨帆" to "Ben", "赵敏" to "Laura",
            "杨小帆" to "Noah", "钱多多" to "Chloe", "杨豆豆" to "Lucas",
            "杨小明" to "Sam", "吴佳" to "Emily", "吴建军" to "Edward",
            "郑红" to "Susan", "杨乐乐" to "Owen", "孙悦" to "Mia",
            "杨小小" to "Felix", "李国强" to "Henry", "陈美华" to "Margaret",
            "李老爷子" to "Arthur", "刘奶奶" to "Edith", "李国华" to "Tom",
            "马丽" to "Jane", "李国栋" to "Robert", "陈美玲" to "Carol",
            "李伟" to "Kevin", "李强" to "Brian", "何静" to "Karen",
            "李小龙" to "Max", "李老二" to "Oscar", "李国平" to "Paul",
            "赵老师" to "Oliver"
        )
    }
}
