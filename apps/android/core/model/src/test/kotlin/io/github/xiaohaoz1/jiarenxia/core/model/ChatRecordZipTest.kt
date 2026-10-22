package io.github.xiaohaoz1.jiarenxia.core.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 聊天记录 zip → 文本提取：txt/html 取首个文本条目、剥标签、编码回退。 */
class ChatRecordZipTest {

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return ByteArrayInputStream(out.toByteArray())
    }

    @Test
    fun `取包内 txt 条目文本`() {
        val zip = zipOf("聊天记录.txt" to "明天记得买酱油\n周五接孩子".toByteArray())
        assertEquals(
            "明天记得买酱油\n周五接孩子",
            ChatRecordZip.extractText(zip)
        )
    }

    @Test
    fun `html 条目剥标签与实体`() {
        val html = "<html><body><p>明晚8点接娃</p><p>带外套&nbsp;小心凉</p></body></html>"
        val zip = zipOf("chat.html" to html.toByteArray())
        assertEquals("明晚8点接娃\n带外套 小心凉", ChatRecordZip.extractText(zip))
    }

    @Test
    fun `跳过非文本条目取第一个文本文件`() {
        val zip = zipOf(
            "voice/1.mp3" to byteArrayOf(1, 2, 3),
            "图片.png" to byteArrayOf(4, 5),
            "记录.txt" to "10月20号交物业费".toByteArray()
        )
        assertEquals("10月20号交物业费", ChatRecordZip.extractText(zip))
    }

    @Test
    fun `包内无文本条目返回 null`() {
        val zip = zipOf("a.png" to byteArrayOf(1), "b.mp4" to byteArrayOf(2))
        assertNull(ChatRecordZip.extractText(zip))
    }

    @Test
    fun `剥离微信导出的项目符号前缀`() {
        val zip = zipOf("聊天记录.txt" to "·晚上聊会吗\n· 明天买牛奶".toByteArray())
        assertEquals("晚上聊会吗\n明天买牛奶", ChatRecordZip.extractText(zip))
    }

    @Test
    fun `GBK 编码文本回退解码`() {
        val gbk = "下周三开会带材料".toByteArray(Charset.forName("GB18030"))
        val zip = zipOf("record.txt" to gbk)
        assertEquals("下周三开会带材料", ChatRecordZip.extractText(zip))
    }

    @Test
    fun `损坏输入返回 null 不抛异常`() {
        assertNull(ChatRecordZip.extractText(ByteArrayInputStream("not a zip".toByteArray())))
    }
}
