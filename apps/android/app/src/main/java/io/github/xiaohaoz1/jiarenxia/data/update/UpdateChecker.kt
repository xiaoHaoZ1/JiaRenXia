package io.github.xiaohaoz1.jiarenxia.data.update

import io.github.xiaohaoz1.jiarenxia.BuildConfig
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「联网检查更新」的远端版本信息（官网 `jiarenxia-version.json`，
 * 由 apps/web 构建时从 site.ts 生成，两者是同一个事实来源）。
 */
data class LatestRelease(
    val versionCode: Int,
    val versionName: String,
    val sizeBytes: Long,
    val sha256: String,
    /** 安装包下载地址（官网绝对 URL）。 */
    val url: String,
    /** 更新说明，可空。 */
    val notes: String,
)

sealed interface CheckResult {
    /** 已是最新版。 */
    data object UpToDate : CheckResult
    /** 发现新版本。 */
    data class Update(val release: LatestRelease) : CheckResult
    /** 检查失败（网络、服务不可用、清单格式不对等）。 */
    data class Failed(val reason: String) : CheckResult
}

/** 拉取版本清单的原始结果（网络成功但格式不对也算 Error）。 */
sealed interface ManifestFetch {
    data class Ok(val release: LatestRelease) : ManifestFetch
    data class Error(val reason: String) : ManifestFetch
}

/**
 * 联网检查更新。所有联网代码都受 [SettingsRepository.updateCheckEnabled]
 * 门控：开关关着时（默认）不发起任何请求。
 *
 * 只读取官网的版本清单 JSON，不上传任何资料。
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val settings: SettingsRepository,
) {
    /** 时钟与清单拉取实现：internal 可注入便于单测。 */
    internal var clock: () -> Long = { System.currentTimeMillis() }
    internal var fetchLatest: suspend () -> ManifestFetch = { fetchLatestHttp() }

    /**
     * 启动自动检查发现新版本时发出的提醒事件（进程内）。replay=1 保证
     * UI 晚于检查完成才挂载时仍能拿到这一次结果；UI 展示或关闭后调
     * [clearUpdatePrompt] 清掉缓存，避免重组/转屏后重复弹窗。
     * 失败、未更新、被「不再提示」跳过的版本都不会发事件。
     */
    private val _updatePrompt = MutableSharedFlow<LatestRelease>(replay = 1)
    val updatePrompt: SharedFlow<LatestRelease> = _updatePrompt.asSharedFlow()

    fun clearUpdatePrompt() = _updatePrompt.resetReplayCache()

    /** 启动时自动检查：开关未开或距上次检查不足 [AUTO_INTERVAL] 则跳过。 */
    suspend fun autoCheckIfDue() {
        if (!settings.updateCheckEnabled.first()) return
        val last = settings.updateLastCheckAt.first()
        if (clock() - last < AUTO_INTERVAL) return
        // 先落尝试时刻：失败也按节流间隔算，避免断网时每次启动都撞墙
        settings.setUpdateLastCheckAt(clock())
        when (val r = fetchLatest()) {
            is ManifestFetch.Ok -> {
                record(r.release)
                val skipped = settings.updateSkippedCode.first()
                // 「不再提示」按 versionCode 记：只压这一个版本，更高的仍提醒
                if (isNewer(r.release, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME) &&
                    r.release.versionCode != skipped
                ) {
                    _updatePrompt.tryEmit(r.release)
                }
            }
            is ManifestFetch.Error -> { /* 自动检查静默失败，下次再试 */ }
        }
    }

    /** 手动检查（UI 触发）；开关未开时返回失败，不发起请求。 */
    suspend fun checkNow(): CheckResult {
        if (!settings.updateCheckEnabled.first()) {
            return CheckResult.Failed("联网检查更新未开启")
        }
        settings.setUpdateLastCheckAt(clock())
        return when (val r = fetchLatest()) {
            is ManifestFetch.Error -> CheckResult.Failed(r.reason)
            is ManifestFetch.Ok -> {
                record(r.release)
                if (isNewer(r.release, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) {
                    CheckResult.Update(r.release)
                } else CheckResult.UpToDate
            }
        }
    }

    private suspend fun record(r: LatestRelease) {
        settings.setUpdateKnownCode(r.versionCode)
        settings.setUpdateKnownName(r.versionName)
        settings.setUpdateKnownNotes(r.notes)
    }

    private suspend fun fetchLatestHttp(): ManifestFetch = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(MANIFEST_URL).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.requestMethod = "GET"
                // 清单不含个人信息，也不要带 Cookie/账号
                conn.setRequestProperty("Accept", "application/json")
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    return@runCatching ManifestFetch.Error("服务器返回 ${conn.responseCode}")
                }
                val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                parseManifest(body)
            } finally {
                conn.disconnect()
            }
        }.getOrElse { ManifestFetch.Error("无法连接服务器") }
    }

    companion object {
        /** 官网版本清单（构建时由 site.ts 生成到站点根目录）。 */
        const val MANIFEST_URL = "https://jiarenxia.com/jiarenxia-version.json"
        /** 官网下载页（浏览器兜底入口）。 */
        const val DOWNLOAD_PAGE_URL = "https://jiarenxia.com/download"
        /**
         * 自动检查节流：每次启动（冷启动）都查，但距上次尝试不足此值则跳过，
         * 防的是短时间内反复开 App 打重复请求。清单只有几百字节，值取小。
         */
        const val AUTO_INTERVAL = 10L * 60 * 1000

        /** 判断远端版本是否比本机新：versionCode 为准，缺 code 时退化为版本名逐段比较。 */
        fun isNewer(r: LatestRelease, localCode: Int, localName: String): Boolean {
            if (r.versionCode > 0) return r.versionCode > localCode
            val a = nameParts(r.versionName)
            val b = nameParts(localName)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun nameParts(name: String): List<Int> =
            name.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }

        /** 解析版本清单；字段缺失或格式不对都算失败。 */
        fun parseManifest(body: String): ManifestFetch {
            return try {
                val o = JSONObject(body)
                val r = LatestRelease(
                    versionCode = o.optInt("versionCode", -1),
                    versionName = o.getString("versionName"),
                    sizeBytes = o.getLong("sizeBytes"),
                    sha256 = o.getString("sha256").lowercase(),
                    url = o.getString("url"),
                    notes = o.optString("notes", ""),
                )
                if (r.versionName.isBlank() || r.sha256.length != 64 || r.url.isBlank()) {
                    ManifestFetch.Error("版本清单格式不对")
                } else ManifestFetch.Ok(r)
            } catch (e: Exception) {
                ManifestFetch.Error("版本清单格式不对")
            }
        }
    }
}
