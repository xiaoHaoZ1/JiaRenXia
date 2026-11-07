package io.github.xiaohaoz1.jiarenxia.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 生成「演示版」两份内置家庭备份（opt-in）：`-PtestFamily=true` 时写
 * `testdata/demo_groom_zhudi.jrxb` 与 `testdata/demo_bride_xu.jrxb`
 * （Android 工程根目录 apps/android，密码 test1234），再由人工复制进
 * `app/src/demo/assets/`。导出 + inspect 自检始终执行，落盘仅在 opt-in 时。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
class DemoFamilyBackupTest : RepoTestBase() {

    private lateinit var backup: BackupManager
    private lateinit var ctx: Context

    @Before
    fun setUpBackup() {
        ctx = ApplicationProvider.getApplicationContext()
        backup = BackupManager(ctx, db, TestFamilyBackupTest.FakeSettings())
    }

    @Test
    fun `新郎演示家庭导出并可被 inspect`() = runTest {
        MingFamily.seed(repo, db, DemoFamilies.GROOM_KEEP)

        val dir = File(ctx.cacheDir, "demofamily").apply { mkdirs() }
        val file = File(dir, DemoFamilies.GROOM_ASSET)
        FileOutputStream(file).use {
            val summary = backup.export(it, DemoFamilies.PASSWORD.toCharArray())
            assertEquals(36, summary.persons)
            assertEquals(42, summary.relationships)
        }
        val plan = backup.inspect(
            FileInputStream(file), DemoFamilies.PASSWORD.toCharArray()
        ).getOrThrow()
        assertEquals(36, plan.summary.persons)
        assertEquals(42, plan.summary.relationships)
        assertEquals(MingFamily.SELF_NAME, plan.summary.selfName)

        writeIfOptIn(file, DemoFamilies.GROOM_ASSET)
    }

    @Test
    fun `新娘演示家庭导出并可被 inspect`() = runTest {
        DemoFamilies.seedBride(repo, db)

        val dir = File(ctx.cacheDir, "demofamily").apply { mkdirs() }
        val file = File(dir, DemoFamilies.BRIDE_ASSET)
        FileOutputStream(file).use {
            val summary = backup.export(it, DemoFamilies.PASSWORD.toCharArray())
            assertEquals(9, summary.persons)
            assertEquals(9, summary.relationships)
        }
        val plan = backup.inspect(
            FileInputStream(file), DemoFamilies.PASSWORD.toCharArray()
        ).getOrThrow()
        assertEquals(9, plan.summary.persons)
        assertEquals(9, plan.summary.relationships)
        assertEquals(DemoFamilies.BRIDE_SELF, plan.summary.selfName)

        writeIfOptIn(file, DemoFamilies.BRIDE_ASSET)
    }

    /** opt-in：把文件落到 Android 工程根目录的 testdata/（user.dir 是模块目录 app/）。 */
    private fun writeIfOptIn(file: File, name: String) {
        Assume.assumeTrue(
            "演示家庭备份仅在 -PtestFamily=true 时生成 testdata/" + name,
            System.getProperty("jrx.testFamily") == "true"
        )
        val out = File(
            requireNotNull(File(System.getProperty("user.dir")).parentFile),
            "testdata/$name"
        )
        out.parentFile?.mkdirs()
        file.copyTo(out, overwrite = true)
        assertTrue(out.isFile && out.length() > 0)
    }
}
