package io.github.xiaohaoz1.jiarenxia.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun settingsRepository(): SettingsRepository
}

private fun settings(context: Context): SettingsRepository =
    EntryPointAccessors.fromApplication(context.applicationContext, SettingsEntryPoint::class.java)
        .settingsRepository()

/**
 * 通知权限申请器（DEVELOPMENT_PLAN 2.7）。
 * 自动询问只发生一次：未授权且未问过 → 先弹说明，再请求系统权限；
 * 之后靠提醒页横幅引导。API < 33 直接放行。
 */
class NotificationPermissionRequester(
    private val context: Context,
    private val scope: CoroutineScope,
    private val showDialog: () -> Unit,
    private val setPending: ((() -> Unit)?) -> Unit
) {
    /** 需要权限时调用；onFinished 在申请结束或无需申请时回调。 */
    fun request(onFinished: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            onFinished()
            return
        }
        scope.launch {
            if (settings(context).notificationPermissionRequested.first()) {
                onFinished()
            } else {
                setPending(onFinished)
                showDialog()
            }
        }
    }
}

/**
 * 组合函数：返回请求器；当需要时渲染说明对话框。
 */
@Composable
fun rememberNotificationPermissionRequester(): NotificationPermissionRequester {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        scope.launch { settings(context).setNotificationPermissionRequested(true) }
        pending?.invoke()
        pending = null
    }

    val requester = remember {
        NotificationPermissionRequester(
            context = context,
            scope = scope,
            showDialog = { showDialog = true },
            setPending = { pending = it }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                scope.launch { settings(context).setNotificationPermissionRequested(true) }
                pending?.invoke()
                pending = null
            },
            title = { Text("开启通知") },
            text = { Text("开启通知后，家人生日当天和前一天会提醒你。") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("去开启") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    scope.launch { settings(context).setNotificationPermissionRequested(true) }
                    pending?.invoke()
                    pending = null
                }) { Text("暂不") }
            }
        )
    }
    return requester
}
