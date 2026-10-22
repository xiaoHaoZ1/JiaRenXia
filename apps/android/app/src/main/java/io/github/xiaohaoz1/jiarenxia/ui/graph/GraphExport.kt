package io.github.xiaohaoz1.jiarenxia.ui.graph

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import io.github.xiaohaoz1.jiarenxia.R
import java.io.File

/**
 * 关系图图片的输出（V0.9）：
 * 分享 → cacheDir/share 经 FileProvider ACTION_SEND；
 * 保存到相册 → MediaStore（API 29+，不需要存储权限）。
 */
object GraphExport {

    /** 相册相对目录（不申请存储权限；API 29+）。 */
    const val ALBUM_PATH = "Pictures/家人侠"
    const val MIME = "image/png"

    /** API 29+ 才显示“保存到相册”。 */
    fun canSaveToAlbum() = Build.VERSION.SDK_INT >= 29

    fun fileName(today: java.time.LocalDate) =
        "家人侠-关系图-" + today.toString().replace("-", "") + ".png"

    /** 写缓存并返回 FileProvider Uri。 */
    fun writeCache(context: Context, bmp: Bitmap, name: String): Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, name)
        f.outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".share-provider", f)
    }

    /** 调起分享面板（image/png）。 */
    fun shareIntent(uri: Uri): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = MIME
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "分享家庭关系图"
        )

    /** MediaStore 插入值（API 29+：RELATIVE_PATH + IS_PENDING）。 */
    fun albumValues(name: String, mime: String = MIME) = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM_PATH)
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }

    /** 保存到相册；返回插入的 Uri（失败为 null）。 */
    fun saveToAlbum(context: Context, bmp: Bitmap, name: String): Uri? {
        if (!canSaveToAlbum()) return null
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            albumValues(name)) ?: return null
        try {
            resolver.openOutputStream(uri)?.use {
                if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                    resolver.delete(uri, null, null)
                    return null
                }
            } ?: run {
                resolver.delete(uri, null, null); return null
            }
            val done = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
    }
}
