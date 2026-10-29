package io.github.xiaohaoz1.jiarenxia.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * 更新包下载与安装：下载到 [Context.getCacheDir]/update/，边下边算 SHA-256，
 * 与清单里的校验值核对后才允许拉起系统安装器。
 */
@javax.inject.Singleton
class UpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * 下载进度：已下载字节、总字节（未知为 null）、当前平均速度（字节/秒）。
     * [attempt] 为进行中的尝试序号（1 起）；[retrying] 表示上次尝试失败、
     * 正在退避等待下一次连接，此间速度无意义。
     */
    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val bytesPerSecond: Long,
        val attempt: Int = 1,
        val retrying: Boolean = false,
    )

    /**
     * 下载安装包并校验；成功返回本地文件，失败抛异常并清理残件。
     * 网络类失败自动重试 [MAX_ATTEMPTS] 次（官网走隧道回源，偶发抖动是常态），
     * 重试经 HTTP Range 从已下载处续传，进度不回退（dl Worker 支持范围请求）；
     * 校验值对不上说明包与清单不符，重试无意义、直接失败。
     * [onProgress] 收整体进度（含已续传字节；主线程回调由调用方决定）。
     */
    suspend fun download(
        release: LatestRelease,
        onProgress: (Progress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        // 清掉旧版本的包/残件，磁盘里只留当前这一个
        dir.listFiles()?.filter { it.name.startsWith("jrx-") }?.forEach { it.delete() }
        val target = File(dir, "jrx-${release.versionName}.apk")
        val tmp = File(dir, "${target.name}.part")
        var last: Exception? = null
        var got = 0L
        var total: Long? = null
        for (i in 0 until MAX_ATTEMPTS) {
            try {
                return@withContext attempt(release, tmp, target, got) { p ->
                    got = p.downloadedBytes
                    total = p.totalBytes
                    onProgress(p)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                tmp.delete()
                throw e
            } catch (e: Exception) {
                last = e
                if (e is UpdateException && !e.retryable) {
                    tmp.delete(); target.delete()
                    throw e
                }
            }
            if (i < MAX_ATTEMPTS - 1) {
                // 退避期间也要让用户看见在重试，而不是进度条无声停住
                onProgress(
                    Progress(got, total, 0L, attempt = i + 2, retrying = true))
                delay(RETRY_DELAYS_MS[i])
            }
        }
        tmp.delete()
        throw last ?: UpdateException("下载失败")
    }

    /**
     * 单次下载尝试：连接→写盘→边下边算校验值。
     * [resumeFrom] 为此前尝试已写入 [tmp] 的字节数：非零且服务端认 Range
     * （返回 206）时续写，否则从头覆盖。
     */
    private suspend fun attempt(
        release: LatestRelease,
        tmp: File,
        target: File,
        resumeFrom: Long,
        onProgress: (Progress) -> Unit,
    ): File {
        val conn = URL(release.url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 45_000
            if (resumeFrom > 0 && tmp.isFile && tmp.length() == resumeFrom) {
                conn.setRequestProperty("Range", "bytes=" + resumeFrom + "-")
            }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK &&
                code != HttpURLConnection.HTTP_PARTIAL) {
                throw UpdateException("下载失败（" + code + "）")
            }
            // 服务端认 Range 才续传；忽略 Range 回 200 则从头重写
            val resuming = resumeFrom > 0 && code == HttpURLConnection.HTTP_PARTIAL
            var got = if (resuming) resumeFrom else 0L
            val digest = MessageDigest.getInstance("SHA-256")
            // 续传段之前的字节先喂进摘要，保证校验覆盖完整文件
            if (resuming) digest.update(tmp.readBytes())
            val total = release.sizeBytes.takeIf { it > 0 }
                ?: if (resuming) resumeFrom + conn.contentLength.toLong()
                else conn.contentLength.toLong().takeIf { it > 0 }
            val startedNanos = System.nanoTime()
            conn.inputStream.use { input ->
                java.io.FileOutputStream(tmp, resuming).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        got += n
                        val elapsed = System.nanoTime() - startedNanos
                        // 速度只按本次尝试实际传输的字节计，续传段不算
                        val fresh = got - (if (resuming) resumeFrom else 0L)
                        val bps =
                            if (elapsed > 0) (fresh * 1_000_000_000L / elapsed)
                            else 0L
                        onProgress(Progress(got, total, bps))
                    }
                }
            }
            if (total != null && got != total) throw UpdateException("下载不完整")
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (!hex.equals(release.sha256, ignoreCase = true)) {
                throw UpdateException("安装包校验失败", retryable = false)
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target
        } catch (e: UpdateException) {
            throw e
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw UpdateException("下载失败", e)
        } finally {
            conn.disconnect()
        }
    }

    /** 系统是否已允许本应用安装未知来源应用（REQUEST_INSTALL_PACKAGES 的运行时门）。 */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** 跳系统「安装未知应用」授权页的 Intent。 */
    fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** 拉起系统安装器的 Intent；调用前先确保 [canInstall]。 */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.share-provider", apk
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** [retryable] 为 false 时不再自动重试（如校验值不符——重试只会拿到同一个错包）。 */
    class UpdateException(
        message: String,
        cause: Throwable? = null,
        val retryable: Boolean = true,
    ) : Exception(message, cause)

    companion object {
        /** 下载最大尝试次数与逐次退避。 */
        private const val MAX_ATTEMPTS = 3
        private val RETRY_DELAYS_MS = longArrayOf(1_000, 2_500)
    }
}
