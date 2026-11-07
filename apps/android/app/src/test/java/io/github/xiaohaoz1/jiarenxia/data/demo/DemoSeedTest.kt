package io.github.xiaohaoz1.jiarenxia.data.demo

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.backup.QuickSnapshotStore
import io.github.xiaohaoz1.jiarenxia.data.db.PersonAliasEntity
import io.github.xiaohaoz1.jiarenxia.data.db.TapDeviceEntity
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import io.github.xiaohaoz1.jiarenxia.data.tap.RoomSpousePairingStore
import io.github.xiaohaoz1.jiarenxia.domain.ClearAllDataUseCase
import io.github.xiaohaoz1.jiarenxia.reminder.ReminderScheduler
import io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 演示版首启预装与重置（demo_groom_zhudi.jrxb / demo_bride_xu.jrxb，
 * 密码 test1234；资产经 debug 合并 assets 供单测读取）。
 * ConscryptMode.OFF 同 BackupManagerTest（Robolectric 下 Conscrypt 不可用）。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
class DemoSeedTest : RepoTestBase() {

    private lateinit var ctx: Context
    private lateinit var settings: SettingsRepositoryImpl
    private lateinit var pairingStore: RoomSpousePairingStore
    private lateinit var seed: DemoSeed
    private lateinit var prefFile: File

    @Before
    fun setUpSeed() {
        ctx = ApplicationProvider.getApplicationContext()
        prefFile = File(ctx.filesDir, "demo_seed_test_${System.nanoTime()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(produceFile = { prefFile })
        settings = SettingsRepositoryImpl(store)
        val backup = BackupManager(ctx, db, settings)
        pairingStore = RoomSpousePairingStore(db)
        seed = DemoSeedImpl(
            ctx, repo, backup, settings,
            ClearAllDataUseCase(
                ctx, repo, settings, NoopScheduler, pairingStore,
                QuickSnapshotStore(ctx, db, backup)
            ),
            db
        )
    }

    @After
    fun tearDownSeed() { prefFile.delete() }

    @Test
    fun `空库导入新郎演示家庭`() = runTest {
        assertTrue(seed.seedIfEmpty("groom"))
        assertEquals(36, db.personDao().getAll().size)
        assertEquals("朱棣", repo.observeSelf().first()?.name)
        assertTrue(settings.introSeen.first())
    }

    @Test
    fun `空库导入新娘演示家庭`() = runTest {
        assertTrue(seed.seedIfEmpty("bride"))
        assertEquals(9, db.personDao().getAll().size)
        assertEquals("徐皇后", repo.observeSelf().first()?.name)
    }

    @Test
    fun `非空库不导入`() = runTest {
        createSelf("已有的我")
        assertFalse(seed.seedIfEmpty("groom"))
        assertEquals(1, db.personDao().getAll().size)
        assertEquals("已有的我", repo.observeSelf().first()?.name)
    }

    @Test
    fun `空角色不导入`() = runTest {
        // 显式空角色 + debug 变体默认（BuildConfig.DEMO_ROLE 为空）都不导入
        assertFalse(seed.seedIfEmpty(""))
        assertFalse(seed.seedIfEmpty())
        assertTrue(db.personDao().getAll().isEmpty())
    }

    @Test
    fun `重置恢复演示数据并保留外观设置`() = runTest {
        assertTrue(seed.seedIfEmpty("groom"))
        val before = db.personDao().getAll().size
        val father = db.personDao().getAll().first { it.name == "朱元璋" }

        // 改动数据：删一位无子女的人物（梅顺昌）
        val leaf = db.personDao().getAll().first { it.name == "梅顺昌" }
        repo.deletePerson(leaf.id).ok()
        // 造碰一碰痕迹：配对 + 来源映射 + 设备偏好 + 家人别名
        val me = repo.observeSelf().first()!!
        pairingStore.recordMerge(
            pairingId = "p1", peerSelfId = "r-self",
            localSpouseId = father.id, peerName = "对方",
            mapping = mapOf("r-self" to father.id),
            sessionId = 1, payloadSha = "aa", isReader = true
        )
        db.tapPairingDao().upsertDevice(TapDeviceEntity(1, true))
        db.personAliasDao().upsertAll(
            listOf(PersonAliasEntity("remote-x", me.id, 1))
        )
        // 外观改成非常规值，重置后必须原样保留
        settings.setThemeMode(ThemeMode.DARK)
        settings.setThemeStyle(JrxStyle.CLASSIC)
        settings.setFontScaleFactor(1.3f)

        assertTrue(seed.resetDemo("groom"))
        assertEquals(before, db.personDao().getAll().size)
        assertEquals("朱棣", repo.observeSelf().first()?.name)
        // 碰一碰与协作痕迹全部清掉（含通用清空刻意保留的 tap_device）
        assertNull(db.tapPairingDao().getPairing())
        assertTrue(db.tapPairingDao().getMap().isEmpty())
        assertNull(db.tapPairingDao().getDevice())
        assertTrue(db.personAliasDao().getAll().isEmpty())
        // 外观设置不变
        assertEquals(ThemeMode.DARK, settings.themeMode.first())
        assertEquals(JrxStyle.CLASSIC, settings.themeStyle.first())
        assertEquals(1.3f, settings.fontScaleFactor.first(), 0.001f)
    }

    @Test
    fun `重置在空角色下不动数据`() = runTest {
        createSelf("已有的我")
        assertFalse(seed.resetDemo(""))
        assertEquals(1, db.personDao().getAll().size)
    }
}

private object NoopScheduler : ReminderScheduler {
    override suspend fun reconcile() {}
    override fun cancelAll() {}
    override fun enqueueDebugRun() {}
}
