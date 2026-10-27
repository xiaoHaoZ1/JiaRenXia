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
 * 生成明朝演示家庭备份（opt-in）：`-PtestFamily=true` 时写
 * `testdata/ming_yongle_family.jrxb`（Android 工程根目录 apps/android，密码 test1234）。
 * 导出 + inspect 自检始终执行，落盘仅在 opt-in 时。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
class MingFamilyBackupTest : RepoTestBase() {

    private lateinit var backup: BackupManager
    private lateinit var ctx: Context

    @Before
    fun setUpBackup() {
        ctx = ApplicationProvider.getApplicationContext()
        backup = BackupManager(ctx, db, TestFamilyBackupTest.FakeSettings())
    }

    @Test
    fun `明朝家庭导出并可被 inspect`() = runTest {
        MingFamily.seed(repo, db)

        val dir = File(ctx.cacheDir, "mingfamily").apply { mkdirs() }
        val file = File(dir, "ming_yongle_family.jrxb")
        FileOutputStream(file).use {
            val summary = backup.export(it, MingFamily.PASSWORD.toCharArray())
            assertEquals(65, summary.persons)
            assertEquals(90, summary.relationships)
        }
        val plan = backup.inspect(
            FileInputStream(file), MingFamily.PASSWORD.toCharArray()
        ).getOrThrow()
        assertEquals(65, plan.summary.persons)
        assertEquals(90, plan.summary.relationships)
        assertEquals(MingFamily.SELF_NAME, plan.summary.selfName)

        // opt-in：把文件落到 Android 工程根目录的 testdata/（测试 user.dir 是模块目录 app/）
        Assume.assumeTrue(
            "明朝家庭备份仅在 -PtestFamily=true 时生成 testdata/ming_yongle_family.jrxb",
            System.getProperty("jrx.testFamily") == "true"
        )
        val out = File(
            requireNotNull(File(System.getProperty("user.dir")).parentFile),
            "testdata/ming_yongle_family.jrxb"
        )
        out.parentFile?.mkdirs()
        file.copyTo(out, overwrite = true)
        assertTrue(out.isFile && out.length() > 0)
    }
}
