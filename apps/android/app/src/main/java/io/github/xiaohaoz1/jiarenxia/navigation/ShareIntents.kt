package io.github.xiaohaoz1.jiarenxia.navigation

import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * 家人共享文件 Uri 解析（FAMILY_AFFAIRS 9.3）：
 * ACTION_VIEW（微信等“用其他应用打开”）→ intent.data；
 * ACTION_SEND → EXTRA_STREAM。只认这两个 action。
 */
object ShareIntents {
    fun shareUriOf(i: Intent): Uri? {
        if (i.action != Intent.ACTION_VIEW && i.action != Intent.ACTION_SEND)
            return null
        val uri = when (i.action) {
            Intent.ACTION_VIEW -> i.data
            else -> if (Build.VERSION.SDK_INT >= 33) {
                i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
        }
        // 只接受 content scheme（file:// 等一律忽略，接收加固）
        return uri?.takeIf { it.scheme == "content" }
    }

    /**
     * 文本分享（微信等 → 记为家事）：ACTION_SEND 取 EXTRA_TEXT（CharSequence），
     * ACTION_SEND_MULTIPLE（多选转发）取 EXTRA_TEXT 列表按行拼接；
     * 两者皆空时兜底 EXTRA_SUBJECT。空白返回 null；
     * 与 shareUriOf 互不干扰（文件分享无文本字段时各自为 null）。
     */
    fun sharedTextOf(i: Intent): String? {
        val raw: CharSequence? = when (i.action) {
            Intent.ACTION_SEND ->
                i.getCharSequenceExtra(Intent.EXTRA_TEXT)
                    ?: i.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
            Intent.ACTION_SEND_MULTIPLE ->
                i.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
                    ?.joinToString("\n")
                    ?: i.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
            else -> null
        }
        return raw?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * 是否为 zip 文件分享（微信「转发 → 其他应用」的聊天记录导出是
     * application/zip；收下来后由调用方解压取文本，见 core:model ChatRecordZip）。
     * 实测微信走 ACTION_SEND_MULTIPLE，两种 action 都认。
     */
    fun isZipShare(i: Intent): Boolean =
        (i.action == Intent.ACTION_SEND || i.action == Intent.ACTION_SEND_MULTIPLE) &&
            (i.type == "application/zip" || i.type == "application/x-zip-compressed")

    /**
     * zip 分享里的内容 Uri：SEND 取 EXTRA_STREAM；SEND_MULTIPLE 取
     * EXTRA_STREAM 列表首项或 ClipData 首项（微信放 ClipData）。
     * 只接受 content scheme。
     */
    fun zipUriOf(i: Intent): Uri? {
        val uri: Uri? = if (i.action == Intent.ACTION_SEND_MULTIPLE) {
            val list = if (Build.VERSION.SDK_INT >= 33) {
                i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            list?.firstOrNull() ?: i.clipData?.let { c ->
                if (c.itemCount > 0) c.getItemAt(0).uri else null
            }
        } else {
            shareUriOf(i)
        }
        return uri?.takeIf { it.scheme == "content" }
    }
}
