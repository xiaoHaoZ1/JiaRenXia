package io.github.xiaohaoz1.jiarenxia.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.data.avatar.AvatarStorageImpl
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 头像导入 / 解析 / 删除（Robolectric 位图支持验证）。 */
class AvatarStorageTest : RepoTestBase() {

    @Test
    fun 导入生成512正方形并支持删除() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // 生成 800×600 PNG 到文件
        val src = File(context.cacheDir, "src.png")
        val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        FileOutputStream(src).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val rel = storage.import("p1", Uri.fromFile(src))
        val file = storage.resolve(rel)
        assertTrue(file.exists())
        assertTrue(rel.startsWith("avatars/"))

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        assertEquals(512, opts.outWidth)
        assertEquals(512, opts.outHeight)

        storage.delete(rel)
        assertFalse(file.exists())
        storage.deleteAll()
        assertFalse(File(context.filesDir, "avatars").exists())
    }
}
