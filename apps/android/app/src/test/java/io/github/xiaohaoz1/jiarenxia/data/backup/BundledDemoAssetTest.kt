package io.github.xiaohaoz1.jiarenxia.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 内置演示家庭校验：debug 包 assets 里的 ming_yongle_family.jrxb
 * （testdata 同名文件的拷贝）必须能解密并整库恢复出 25 人 / 34 条关系，
 * 防止 assets 拷贝与源文件脱节或漏打进包。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
class BundledDemoAssetTest : RepoTestBase() {

    private lateinit var backup: BackupManager
    private lateinit var ctx: Context

    @Before
    fun setUpBackup() {
        ctx = ApplicationProvider.getApplicationContext()
        backup = BackupManager(ctx, db, TestFamilyBackupTest.FakeSettings())
    }

    @Test
    fun `assets 里的演示家庭可整库恢复`() = runTest {
        val plan = ctx.assets.open("ming_yongle_family.jrxb").use { input ->
            backup.inspect(input, MingFamily.PASSWORD.toCharArray()).getOrThrow()
        }
        assertEquals(65, plan.summary.persons)
        assertEquals(90, plan.summary.relationships)
        assertEquals(MingFamily.SELF_NAME, plan.summary.selfName)

        backup.restore(plan)
        assertEquals(65, db.personDao().count())
        assertEquals(90, db.relationshipDao().count())
    }
}
