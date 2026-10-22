package io.github.xiaohaoz1.jiarenxia.core.model

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/**
 * 聊天记录 zip → 文本提取（微信「转发 → 选择其他应用」发的是
 * 聊天记录_yyyyMMdd_HHmmss.zip）。取包内第一个文本类条目
 * （.txt/.html/.md/.eml/.csv），HTML 剥标签，编码 UTF-8 优先、GB18030 兜底。
 * 全程本机解析；包内没有文本条目时返回 null。
 */
object ChatRecordZip {

    /** 单条目最多读 1MB，防爆内存。 */
    private const val ENTRY_MAX = 1_000_000

    fun extractText(input: InputStream): String? {
        ZipInputStream(input.buffered()).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                if (!e.isDirectory && isTextEntry(e.name)) {
                    val bytes = zis.readCapped()
                    if (bytes.isNotEmpty()) return clean(decode(bytes), e.name)
                }
            }
        }
        return null
    }

    private fun isTextEntry(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".txt") || n.endsWith(".html") || n.endsWith(".htm") ||
            n.endsWith(".md") || n.endsWith(".eml") || n.endsWith(".csv")
    }

    /** 带上限读取当前条目（ZipInputStream 上 limit 语义，不能 readNBytes 到别的流）。 */
    private fun ZipInputStream.readCapped(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < ENTRY_MAX) {
            val n = read(buf, 0, minOf(buf.size, ENTRY_MAX - total))
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    /** UTF-8 解码，含非法字节时回退 GB18030（微信老版本导出常见）。 */
    private fun decode(bytes: ByteArray): String {
        val utf8 = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
        return utf8 ?: bytes.toString(Charset.forName("GB18030"))
    }

    /** HTML 条目剥标签与常见实体；纯文本原样（仅去零宽/BOM）。 */
    private fun clean(text: String, name: String): String {
        val isHtml = name.lowercase().let { it.endsWith(".html") || it.endsWith(".htm") }
        var t = text.replace("﻿", "")
        if (isHtml) {
            t = t.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
        }
        // 微信导出的每条消息带「·」之类项目符号前缀，逐行剥掉
        t = t.lineSequence()
            .joinToString("\n") { line -> line.trimStart('·', '•', '・', '●', '▪', '-', '–', '—', ' ', '　') }
        return t.trim()
    }
}
