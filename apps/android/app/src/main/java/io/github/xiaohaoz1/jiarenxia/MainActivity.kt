package io.github.xiaohaoz1.jiarenxia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import io.github.xiaohaoz1.jiarenxia.navigation.AppNavHost
import io.github.xiaohaoz1.jiarenxia.navigation.ShareIntents
import io.github.xiaohaoz1.jiarenxia.navigation.StartViewModel
import io.github.xiaohaoz1.jiarenxia.ui.theme.JiaRenXiaTheme
import io.github.xiaohaoz1.jiarenxia.ui.theme.isJrxDarkTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 主 Activity：系统启动页（仅图标）保持到起始路由就绪；老用户（已有“我”）直达首页，
 * 仅首次使用时才播放 Compose 启动动画、随后进入引导。
 * 生日提醒通知携带 personId 深链到人物详情（singleTop，onNewIntent 处理）。
 * 外观模式默认浅色，先以 LIGHT 起帧避免启动时闪深色，再跟随设置。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    /** 起始路由判定：就绪前为 null，系统启动页由此保持。 */
    private val startVm: StartViewModel by viewModels()

    /** 待消费的通知深链目标（人物详情）。 */
    private val pendingPersonId = MutableStateFlow<String?>(null)
    private val pendingShareUri = MutableStateFlow<Uri?>(null)

    /** 待消费的文本分享（微信等 → 记为家事，预填事项编辑页）。 */
    private val pendingShareText = MutableStateFlow<String?>(null)

    /** 待消费的事项通知深链目标（事项编辑页）。 */
    private val pendingTaskId = MutableStateFlow<String?>(null)

    /** 待消费的底部标签切换请求（桌面插件头部点按 → 家事页）。 */
    private val pendingTab = MutableStateFlow<String?>(null)

    /** 外观模式（默认 LIGHT，DataStore 值到达后立即生效）。 */
    private val themeMode = MutableStateFlow(ThemeMode.LIGHT)

    /** 字体大小级别（默认标准，与 themeMode 同样的 StateFlow 方式避免闪屏）。 */
    private val fontLevel =
        MutableStateFlow(io.github.xiaohaoz1.jiarenxia.data.settings.FontScaleLevel.STANDARD)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // 路由未就绪前保持系统启动页；就绪后按 startRoute 直达首页或进入启动动画
        splash.setKeepOnScreenCondition { startVm.startRoute.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingPersonId.value = intent.getStringExtra(EXTRA_PERSON_ID)
        pendingTaskId.value = intent.getStringExtra(EXTRA_TASK_ID)
        pendingTab.value = intent.getStringExtra(EXTRA_TAB)
        resolveShareIntent(intent)
        lifecycleScope.launch { settings.themeMode.collect { themeMode.value = it } }
        lifecycleScope.launch { settings.fontScaleLevel.collect { fontLevel.value = it } }
        setContent {
            val mode by themeMode.collectAsStateWithLifecycle()
            val level by fontLevel.collectAsStateWithLifecycle()
            val dark = isJrxDarkTheme(mode)
            // 系统栏图标对比度随实际深浅切换（edge-to-edge 下状态栏/导航栏透明）
            androidx.compose.runtime.SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                )
            }
            // 应用内字号：在系统 fontScale 之上乘级别系数（上限 2.0）
            CompositionLocalProvider(
                LocalDensity provides with(LocalDensity.current) {
                    Density(density, (fontScale * level.factor).coerceAtMost(2.0f))
                }
            ) {
            JiaRenXiaTheme(mode = mode) {
                val startRoute by startVm.startRoute.collectAsStateWithLifecycle()
                startRoute?.let { route ->
                    AppNavHost(
                        startRoute = route,
                        pendingPersonId = pendingPersonId,
                        onPendingConsumed = { pendingPersonId.value = null },
                        pendingTaskId = pendingTaskId,
                        onTaskPendingConsumed = { pendingTaskId.value = null },
                        pendingTab = pendingTab,
                        onTabPendingConsumed = { pendingTab.value = null },
                        pendingShareUri = pendingShareUri,
                        onSharePendingConsumed = {
                            pendingShareUri.value = null
                        },
                        pendingShareText = pendingShareText,
                        onShareTextPendingConsumed = {
                            pendingShareText.value = null
                        }
                    )
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_PERSON_ID)?.let { pendingPersonId.value = it }
        intent.getStringExtra(EXTRA_TASK_ID)?.let { pendingTaskId.value = it }
        intent.getStringExtra(EXTRA_TAB)?.let { pendingTab.value = it }
        resolveShareIntent(intent)
    }

    /**
     * 分享 intent 归口：纯文本 → pendingShareText；家人共享文件 → pendingShareUri；
     * 微信聊天记录 zip（application/zip + EXTRA_STREAM）→ 本机解压取文本后
     * 走 pendingShareText；zip 里没有文本时回退当文件走导入流程。
     * 冷启动（onCreate）与热启动（onNewIntent）都从这里进。
     */
    private fun resolveShareIntent(i: Intent) {
        sharedTextOf(i)?.let { pendingShareText.value = it; return }
        if (ShareIntents.isZipShare(i)) {
            resolveChatZip(i)
            return
        }
        shareUriOf(i)?.let { pendingShareUri.value = it }
    }

    /** 读 zip 里的文本条目（IO 线程）；取不到文本则按共享文件回退导入页。 */
    private fun resolveChatZip(i: Intent) {
        val uri = ShareIntents.zipUriOf(i) ?: return
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val text = runCatching {
                contentResolver.openInputStream(uri)?.use { stream ->
                    io.github.xiaohaoz1.jiarenxia.core.model.ChatRecordZip
                        .extractText(stream)
                }
            }.getOrNull()
            if (text != null) pendingShareText.value = text
            else pendingShareUri.value = uri
        }
    }

    private fun shareUriOf(i: Intent): Uri? =
        io.github.xiaohaoz1.jiarenxia.navigation.ShareIntents.shareUriOf(i)

    private fun sharedTextOf(i: Intent): String? =
        io.github.xiaohaoz1.jiarenxia.navigation.ShareIntents.sharedTextOf(i)

    companion object {
        const val EXTRA_PERSON_ID = "personId"
        const val EXTRA_TASK_ID = "taskId"
        /** 桌面插件打开指定底部标签（值为 MainShell 的 tab route）。 */
        const val EXTRA_TAB = "openTab"
        const val TAB_REMINDER = "reminder"
    }
}
