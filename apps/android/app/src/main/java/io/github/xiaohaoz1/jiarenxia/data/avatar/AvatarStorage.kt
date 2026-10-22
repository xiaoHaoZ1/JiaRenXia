package io.github.xiaohaoz1.jiarenxia.data.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 头像文件存储（DEVELOPMENT_PLAN 2.9）。
 * 文件保存在 filesDir/avatars，数据库只记录相对路径 avatars/…。
 */
interface AvatarStorage {
    /**
     * 从 [source] 导入图片：降采样解码 → 校正 EXIF 方向 → 居中裁正方形 → 512×512 → JPEG 85。
     * 返回相对路径 avatars/<文件名>。
     */
    suspend fun import(personId: String, source: Uri): String

    /** 由相对路径解析为实际文件。 */
    fun resolve(relativePath: String): File

    /** 删除相对路径对应的文件；null 或不存在时静默忽略。 */
    fun delete(relativePath: String?)

    /** 删除整个头像目录。 */
    fun deleteAll()
}

@Singleton
class AvatarStorageImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AvatarStorage {

    private val avatarDir: File get() = File(context.filesDir, "avatars")

    override suspend fun import(personId: String, source: Uri): String = withContext(Dispatchers.IO) {
        val bounds = decodeBounds(source)
        val sample = sampleSizeFor(bounds, TARGET_SIZE * 2)
        val decoded = decode(source, sample)
        val square = centerCrop(decoded)
        val scaled = Bitmap.createScaledBitmap(square, TARGET_SIZE, TARGET_SIZE, true)
        avatarDir.mkdirs()
        val file = File(avatarDir, personId + "_" + System.currentTimeMillis() + ".jpg")
        val tmp = File(avatarDir, file.name + ".tmp")
        try {
            FileOutputStream(tmp).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        "avatars/" + file.name
    }

    override fun resolve(relativePath: String): File = File(context.filesDir, relativePath)

    override fun delete(relativePath: String?) {
        if (relativePath.isNullOrBlank()) return
        resolve(relativePath).delete()
    }

    override fun deleteAll() {
        avatarDir.deleteRecursively()
    }

    private fun decodeBounds(source: Uri): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            throw IllegalArgumentException("无法解码图片")
        }
        return opts.outWidth to opts.outHeight
    }

    /** 计算 2 的幂降采样倍数：边长不超过 target 的两倍以内。 */
    private fun sampleSizeFor(bounds: Pair<Int, Int>, target: Int): Int {
        var sample = 1
        while (bounds.first / (sample * 2) >= target || bounds.second / (sample * 2) >= target) {
            sample *= 2
        }
        return sample
    }

    private fun decode(source: Uri, sample: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder 自动按 EXIF 方向旋转；个别环境（如 Robolectric）不支持时退回 BitmapFactory
            try {
                return ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.contentResolver, source)
                ) { decoder, _, _ ->
                    decoder.setTargetSampleSize(sample)
                    decoder.isMutableRequired = true
                }
            } catch (e: Exception) {
                // fall through
            }
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw IllegalArgumentException("无法解码图片")
        return rotateByExif(source, bmp)
    }

    /** API 26/27 的手动 EXIF 方向校正。 */
    private fun rotateByExif(source: Uri, bmp: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(source)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bmp
        }
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun centerCrop(bmp: Bitmap): Bitmap {
        val side = minOf(bmp.width, bmp.height)
        val x = (bmp.width - side) / 2
        val y = (bmp.height - side) / 2
        return Bitmap.createBitmap(bmp, x, y, side, side)
    }

    private companion object {
        const val TARGET_SIZE = 512
    }
}
