package io.github.xiaohaoz1.jiarenxia.navigation

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 分享 intent 解析：文本（EXTRA_TEXT → 记为家事）与文件（content Uri）互不干扰。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareIntentsTest {

    @Test
    fun `SEND 文本返回 EXTRA_TEXT 且前后空白被修剪`() {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "  明天记得买酱油  ")
        }
        assertEquals("明天记得买酱油", ShareIntents.sharedTextOf(i))
        assertNull(ShareIntents.shareUriOf(i))
    }

    @Test
    fun `SEND 空白文本返回 null`() {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "   ")
        }
        assertNull(ShareIntents.sharedTextOf(i))
    }

    @Test
    fun `SEND_MULTIPLE 多条文本按行拼接`() {
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putCharSequenceArrayListExtra(
                Intent.EXTRA_TEXT,
                arrayListOf<CharSequence>("明天买酱油", "周五接孩子")
            )
        }
        assertEquals("明天买酱油\n周五接孩子", ShareIntents.sharedTextOf(i))
    }

    @Test
    fun `SEND 无正文时兜底 EXTRA_SUBJECT`() {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "下月5号还信用卡")
        }
        assertEquals("下月5号还信用卡", ShareIntents.sharedTextOf(i))
    }

    @Test
    fun `VIEW 与 MAIN 不识别为文本`() {
        assertNull(ShareIntents.sharedTextOf(Intent(Intent.ACTION_VIEW)))
        assertNull(ShareIntents.sharedTextOf(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun `文件分享仍只认 content Uri 不受文本参数影响`() {
        val uri = Uri.parse("content://x/file.jrxb")
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        assertEquals(uri, ShareIntents.shareUriOf(i))
        assertNull(ShareIntents.sharedTextOf(i))
    }
}
