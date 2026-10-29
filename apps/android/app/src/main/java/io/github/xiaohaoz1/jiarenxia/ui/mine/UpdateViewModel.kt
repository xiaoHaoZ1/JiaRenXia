package io.github.xiaohaoz1.jiarenxia.ui.mine

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xiaohaoz1.jiarenxia.BuildConfig
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.update.CheckResult
import io.github.xiaohaoz1.jiarenxia.data.update.LatestRelease
import io.github.xiaohaoz1.jiarenxia.data.update.UpdateChecker
import io.github.xiaohaoz1.jiarenxia.data.update.UpdateDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 「我的」页检查更新行与「帮助与关于」页联网开关共用的状态。
 * 联网动作全部走 [UpdateChecker]/[UpdateDownloader]，两者都受「联网检查更新」开关门控。
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val checker: UpdateChecker,
    private val downloader: UpdateDownloader,
) : ViewModel() {

    /** 「联网检查更新」开关（默认关）。 */
    val enabled: StateFlow<Boolean> =
        settings.updateCheckEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 已知远端版本比本机新时的版本名（行副标题/角标用）。 */
    val knownNewVersion: StateFlow<String?> = combine(
        settings.updateKnownCode, settings.updateKnownName
    ) { code, name ->
        if (code > BuildConfig.VERSION_CODE && name.isNotBlank()) name else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 启动自动检查发现新版的提醒事件（由应用级弹窗展示，见 UpdatePromptHost）。 */
    val updatePrompt: SharedFlow<LatestRelease> = checker.updatePrompt

    /** 检查中 / 检查结果（发现新版、失败原因）、下载进度、待安装文件。 */
    val checking = MutableStateFlow(false)
    val foundRelease = MutableStateFlow<LatestRelease?>(null)
    val checkError = MutableStateFlow<String?>(null)
    val toast = MutableStateFlow<String?>(null)
    val downloadProgress = MutableStateFlow<UpdateDownloader.Progress?>(null)
    val downloadedApk = MutableStateFlow<File?>(null)

    fun setEnabled(v: Boolean) {
        viewModelScope.launch { settings.setUpdateCheckEnabled(v) }
    }

    /** 弹承诺确认后开启：先落开关再立刻查一次（同协程保证顺序）。 */
    fun enableAndCheck() {
        viewModelScope.launch {
            settings.setUpdateCheckEnabled(true)
            doCheck()
        }
    }

    /** 手动点「检查更新」（开关已开时）。关着时由 UI 弹联网承诺框，不走这里。 */
    fun checkNow() {
        if (checking.value) return
        checking.value = true
        viewModelScope.launch { doCheck(); checking.value = false }
    }

    private suspend fun doCheck() {
        when (val r = checker.checkNow()) {
            is CheckResult.Update -> foundRelease.value = r.release
            CheckResult.UpToDate -> toast.value = "已是最新版本"
            is CheckResult.Failed -> checkError.value = r.reason
        }
    }

    /** 浏览器兜底：打开官网下载页。 */
    fun openDownloadPage() {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, UpdateChecker.DOWNLOAD_PAGE_URL.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private var downloadJob: kotlinx.coroutines.Job? = null

    fun startDownload(release: LatestRelease) {
        if (downloadJob?.isActive == true) return
        downloadProgress.value = UpdateDownloader.Progress(
            downloadedBytes = 0L,
            totalBytes = release.sizeBytes.takeIf { it > 0 },
            bytesPerSecond = 0L)
        downloadJob = viewModelScope.launch {
            try {
                val f = downloader.download(release) { downloadProgress.value = it }
                downloadProgress.value = null
                downloadedApk.value = f
            } catch (e: kotlinx.coroutines.CancellationException) {
                downloadProgress.value = null
            } catch (e: Exception) {
                downloadProgress.value = null
                toast.value = e.message ?: "下载失败"
            }
        }
    }

    /** 已下好的包交给系统安装器；未授权时跳「安装未知应用」设置页。 */
    fun installDownloaded() {
        val f = downloadedApk.value ?: return
        if (downloader.canInstall()) {
            context.startActivity(downloader.installIntent(f))
            downloadedApk.value = null
        } else {
            // 先引导授权，回来后文件还在，再点一次即装
            context.startActivity(downloader.unknownSourcesIntent())
        }
    }

    /** 启动提醒「不再提示」：按 versionCode 记录，只压这一个版本。 */
    fun skipVersion(release: LatestRelease) {
        viewModelScope.launch {
            settings.setUpdateSkippedCode(release.versionCode)
            checker.clearUpdatePrompt()
        }
    }

    /** 启动提醒「稍后」：只清本次事件，不写任何持久状态，下次检查仍会提醒。 */
    fun dismissUpdatePrompt() {
        checker.clearUpdatePrompt()
    }

    fun consumeToast() { toast.value = null }
    fun dismissFound() { foundRelease.value = null }
    fun dismissError() { checkError.value = null }
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloadProgress.value = null
    }
    fun dismissDownloaded() { downloadedApk.value = null }
}
