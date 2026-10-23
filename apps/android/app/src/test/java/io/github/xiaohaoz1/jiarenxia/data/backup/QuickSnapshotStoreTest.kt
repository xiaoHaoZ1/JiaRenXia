package io.github.xiaohaoz1.jiarenxia.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.backup.QuickSnapshotStore.RestoreResult
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.domain.ClearAllDataUseCase
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 快速存档（测试工具）数据层测试。
 * ConscryptMode.OFF 同 BackupManagerTest（Robolectric 下用 JDK 自带 GCM）。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
class QuickSnapshotStoreTest : RepoTestBase() {

    private lateinit var ctx: Context
    private lateinit var store: QuickSnapshotStore
    private lateinit var backup: BackupManager

    private val snapDir get() = File(ctx.filesDir, "quick_snapshots")

    @Before
    fun setUpStore() {
        ctx = ApplicationProvider.getApplicationContext()
        backup = BackupManager(ctx, db, FakeSettingsRepository())
        store = QuickSnapshotStore(ctx, db, backup)
    }

    @After
    fun tearDownStore() {
        snapDir.deleteRecursively()
    }

    /** 造一个小家庭：我 + 女儿 + 父女关系 + 我的生日。 */
    private suspend fun seedSmallFamily(): String {
        val self = createSelf("杨明")
        db.personDao().insert(
            PersonEntity("p-child", "安安", Gender.FEMALE, null, null,
                LivingStatus.ALIVE, false, 0, 0)
        )
        db.relationshipDao().insert(rel(self, "p-child", RelationshipType.PARENT))
        db.importantDateDao().insert(
            ImportantDateEntity("d1", self, ImportantDateType.BIRTHDAY_SOLAR,
                1990, 1, 1, CalendarType.SOLAR, false, null, 0, 0)
        )
        return self
    }

    @Test
    fun 存档后读档_人物关系日期一致() = runTest {
        val self = seedSmallFamily()
        val snap = store.save().getOrThrow()
        assertEquals(2, snap.persons)
        assertEquals(1, snap.relationships)
        assertEquals("杨明", snap.selfName)

        repo.clearAllData()
        assertTrue(db.personDao().getAll().isEmpty())

        val r = store.restore(snap.id)
        assertTrue(r is RestoreResult.Ok)
        r as RestoreResult.Ok
        assertEquals(2, r.persons)
        assertTrue(r.hasSelf)
        assertEquals(setOf(self, "p-child"),
            db.personDao().getAll().map { it.id }.toSet())
        assertEquals(1, db.relationshipDao().getAll().size)
        assertEquals(1, db.importantDateDao().getAllDates().size)
    }

    @Test
    fun 存档保留最近五份() = runTest {
        createSelf()
        repeat(QuickSnapshotStore.KEEP + 2) { store.save().getOrThrow() }
        val list = store.list()
        assertEquals(QuickSnapshotStore.KEEP, list.size)
        // 磁盘上同样只剩 5 对文件
        assertEquals(QuickSnapshotStore.KEEP,
            snapDir.listFiles { f -> f.extension == "jrxs" }!!.size)
        assertEquals(QuickSnapshotStore.KEEP,
            snapDir.listFiles { f -> f.extension == "meta" }!!.size)
    }

    @Test
    fun 本机标识不符_拒绝恢复() = runTest {
        seedSmallFamily()
        val snap = store.save().getOrThrow()
        // 篡改 meta 的本机标识：模拟从别的设备复制来的存档
        val meta = File(snapDir, snap.id + ".meta")
        meta.writeText(
            JSONObject(meta.readText()).put("deviceId", "other-device").toString()
        )
        assertEquals(RestoreResult.NotFromThisDevice, store.restore(snap.id))
        // 本机数据未被覆盖
        assertEquals(2, db.personDao().getAll().size)
    }

    @Test
    fun 密钥不符_拒绝恢复() = runTest {
        seedSmallFamily()
        val snap = store.save().getOrThrow()
        // 覆盖本机密钥：模拟存档来自另一台安装
        File(snapDir, ".key").writeBytes(ByteArray(32) { 9 })
        assertEquals(RestoreResult.NotFromThisDevice, store.restore(snap.id))
        assertEquals(2, db.personDao().getAll().size)
    }

    @Test
    fun 清空所有数据_连同存档删除() = runTest {
        seedSmallFamily()
        store.save().getOrThrow()
        assertEquals(1, store.list().size)

        clearer().run()
        assertTrue(store.list().isEmpty())
        assertFalse(snapDir.exists())
        assertTrue(db.personDao().getAll().isEmpty())
    }

    @Test
    fun 清空数据但保留存档() = runTest {
        seedSmallFamily()
        val snap = store.save().getOrThrow()

        clearer().run(keepSnapshots = true)
        assertTrue(db.personDao().getAll().isEmpty())
        assertEquals(1, store.list().size)
        // 存档仍可恢复
        val r = store.restore(snap.id)
        assertTrue(r is RestoreResult.Ok)
        assertEquals(2, db.personDao().getAll().size)
    }

    private fun clearer() = ClearAllDataUseCase(
        ctx, repo, FakeSettingsRepository(), SnapshotFakeScheduler,
        io.github.xiaohaoz1.jiarenxia.data.tap.RoomSpousePairingStore(db),
        store
    )
}

private object SnapshotFakeScheduler : io.github.xiaohaoz1.jiarenxia.reminder.ReminderScheduler {
    override suspend fun reconcile() {}
    override fun cancelAll() {}
    override fun enqueueDebugRun() {}
}
