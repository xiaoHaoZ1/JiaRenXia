package io.github.xiaohaoz1.jiarenxia.ui.restore

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.data.AppEvents
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupCrypto
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupJson
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.backup.RestorePlan
import io.github.xiaohaoz1.jiarenxia.data.backup.TestFamily
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 共用恢复流程（ui/restore/RestoreFlow，FAMILY_AFFAIRS 16.1/16.2）：
 * 空库/有库恢复、purpose 分流、“我不是 X”改道。
 * ConscryptMode.OFF 同 BackupManagerTest。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
@OptIn(ExperimentalCoroutinesApi::class)
class RestoreFlowTest : RepoTestBase() {

    private lateinit var ctx: Context
    private lateinit var backup: BackupManager
    private lateinit var appEvents: AppEvents
    private val messages = mutableListOf<String>()
    private lateinit var flow: RestoreFlow

    @Before
    fun setUpFlow() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ctx = ApplicationProvider.getApplicationContext()
        backup = BackupManager(ctx, db, FakeSettingsRepository())
        appEvents = AppEvents()
        flow = RestoreFlow(
            scope = CoroutineScope(Dispatchers.Main),
            backup = backup,
            appEvents = appEvents,
            hasLocalData = { db.personDao().getAll().isNotEmpty() },
            onMessage = { messages += it }
        )
    }

    @After
    fun tearDownFlow() = Dispatchers.resetMain()

    /** 等流程离开 Working（解密/恢复都在 IO 上）。 */
    private fun waitDone(): RestoreStep {
        val deadline = System.currentTimeMillis() + 10_000
        while (flow.step.value is RestoreStep.Working &&
            System.currentTimeMillis() < deadline
        ) Thread.sleep(50)
        return flow.step.value
    }

    private suspend fun exportFile(name: String): File {
        val f = File(ctx.cacheDir, name)
        FileOutputStream(f).use {
            backup.export(it, "pw123456".toCharArray())
        }
        return f
    }

    /** 输入密码 → 等到 Confirm 或 Idle。 */
    private suspend fun decrypt(file: File): RestoreStep {
        flow.onSource(Uri.fromFile(file))
        flow.submitPassword("pw123456".toCharArray(), ctx)
        return waitDone()
    }

    @Test
    fun 空库恢复备份_有我() = runTest {
        // 先造数据导出，再清空模拟新装
        val selfId = createSelf()
        repo.createRelative(
            PersonDraft("爸爸", Gender.MALE), selfId,
            io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.FATHER
        ).ok()
        val src = exportFile("rf_self.jrxb")
        withContext(Dispatchers.IO) { db.clearAllTables() }

        val step = decrypt(src)
        assertTrue("期望 Confirm，实际 " + step, step is RestoreStep.Confirm)
        assertFalse("空库不应提示覆盖", (step as RestoreStep.Confirm).overwrites)

        flow.confirm()
        assertEquals(RestoreStep.Idle, waitDone())
        assertEquals(RestoreOutcome.RESTORED_WITH_SELF, flow.outcome.value)
        // 人物已写入（含恢复出的“我”）
        assertEquals(2, db.personDao().getAll().size)
        assertEquals(1, db.personDao().getAll().count { it.isSelf })
    }

    /**
     * “我不是 X”：备份改按家人共享处理——剥掉事项/人情/保单/联系/自定义
     * 称呼/设置，人物/手机号/关系/日期/提醒偏好保留，数据库不动。
     */
    @Test
    fun 我不是X_改按共享导入() = runTest {
        val ids = TestFamily.seed(repo, db)
        val selfId = ids.getValue(TestFamily.SELF_NAME)
        val src = exportFile("rf_notme.jrxb")
        val origPlan = backup.inspect(
            java.io.FileInputStream(src), "pw123456".toCharArray()
        ).getOrThrow()
        val idsBefore = db.personDao().getAll().map { it.id }.toSet()

        val step = decrypt(src)
        assertTrue(step is RestoreStep.Confirm)

        flow.notMe()
        assertEquals(RestoreStep.Idle, flow.step.value)
        assertTrue(flow.shareDetected.value)
        val plan = appEvents.pendingSharePlan
        assertTrue(plan != null)
        assertEquals("share", plan!!.purpose)
        assertEquals(selfId, plan.sharedBy)
        // 私密内容剥离
        assertTrue(plan.tasks.isEmpty())
        assertTrue(plan.contactLogs.isEmpty() && plan.contactPlans.isEmpty())
        assertTrue(plan.customTitles.isEmpty())
        assertNull(plan.settings)
        // 公共内容与文件一致
        assertEquals(origPlan.persons, plan.persons)
        assertEquals(origPlan.phones, plan.phones)
        assertEquals(origPlan.relationships, plan.relationships)
        assertEquals(origPlan.importantDates, plan.importantDates)
        assertEquals(origPlan.prefs, plan.prefs)
        // 数据库未被触碰
        assertEquals(idsBefore, db.personDao().getAll().map { it.id }.toSet())
    }

    /** 备份里没有“我”：不出“我不是 X”入口；恢复后去创建“我”。 */
    @Test
    fun 备份无我_notMe无效_恢复后建我() = runTest {
        // 手工造一份无“我”的备份文件
        val src = File(ctx.cacheDir, "rf_noself.jrxb")
        val zip = BackupJson.writeZip(
            persons = listOf(
                PersonEntity(
                    id = "p1", name = "张三", gender = Gender.MALE,
                    avatarPath = null, note = null,
                    livingStatus = LivingStatus.ALIVE,
                    isSelf = false, createdAt = 0, updatedAt = 0
                )
            ),
            phones = emptyList(), relationships = emptyList(),
            dates = emptyList(), prefs = emptyList(), customTitles = emptyList(),
            settings = null, avatars = emptyMap(),
            createdAt = 0L, appVersion = "test"
        )
        src.writeBytes(BackupCrypto.encrypt(zip, "pw123456".toCharArray()))

        val step = decrypt(src)
        assertTrue(step is RestoreStep.Confirm)
        assertNull((step as RestoreStep.Confirm).summary.selfName)

        // 无“我”→ notMe 不产生任何效果（仍停在 Confirm）
        flow.notMe()
        assertTrue(flow.step.value is RestoreStep.Confirm)
        assertFalse(flow.shareDetected.value)
        assertNull(appEvents.pendingSharePlan)

        flow.confirm()
        assertEquals(RestoreStep.Idle, waitDone())
        assertEquals(RestoreOutcome.RESTORED_WITHOUT_SELF, flow.outcome.value)
        assertEquals(1, db.personDao().getAll().size)
    }

    /** 共享文件从恢复流程进 → 分流去“我是谁”（不写库）。 */
    @Test
    fun 共享文件_分流共享导入() = runTest {
        val selfId = createSelf()
        val src = File(ctx.cacheDir, "rf_share.jrxb")
        FileOutputStream(src).use {
            backup.exportShare(
                it, "pw123456".toCharArray(), sharedBy = selfId
            )
        }
        val idsBefore = db.personDao().getAll().map { it.id }.toSet()

        assertEquals(RestoreStep.Idle, decrypt(src))
        assertTrue(flow.shareDetected.value)
        assertEquals("share", appEvents.pendingSharePlan?.purpose)
        assertEquals(idsBefore, db.personDao().getAll().map { it.id }.toSet())
    }
}
