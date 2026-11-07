package io.github.xiaohaoz1.jiarenxia.navigation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.xiaohaoz1.jiarenxia.ui.elder.ElderHomeScreen
import io.github.xiaohaoz1.jiarenxia.ui.elder.ElderPersonScreen
import kotlinx.coroutines.flow.StateFlow

/**
 * 长辈模式导航（FAMILY_AFFAIRS 21）：只有 elder/home 与 elder/person/{id} 两条只读路由，
 * 不渲染 MainShell 与任何编辑/设置页。系统返回键在首页走默认行为（退出 App）。
 *
 * 深链与分享的处理：
 * - pendingPersonId：人物存在则进只读详情（已故/不存在由详情页自行退回首页）；
 * - pendingTaskId / pendingTab：直接消费丢弃（长辈模式没有事项编辑与标签页）；
 * - pendingShareUri / pendingShareText：不进接收流程，弹“收到家人资料”提示并消费。
 */
@Composable
fun ElderNavHost(
    appNavViewModel: AppNavViewModel,
    pendingPersonId: StateFlow<String?>,
    onPendingConsumed: () -> Unit,
    pendingTaskId: StateFlow<String?> = kotlinx.coroutines.flow.MutableStateFlow(null),
    onTaskPendingConsumed: () -> Unit = {},
    pendingTab: StateFlow<String?> = kotlinx.coroutines.flow.MutableStateFlow(null),
    onTabPendingConsumed: () -> Unit = {},
    pendingShareUri: StateFlow<android.net.Uri?> =
        kotlinx.coroutines.flow.MutableStateFlow(null),
    onSharePendingConsumed: () -> Unit = {},
    pendingShareText: StateFlow<String?> =
        kotlinx.coroutines.flow.MutableStateFlow(null),
    onShareTextPendingConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val pending by pendingPersonId.collectAsStateWithLifecycle()
    val pendingTask by pendingTaskId.collectAsStateWithLifecycle()
    val pendingTabReq by pendingTab.collectAsStateWithLifecycle()
    val pendingShare by pendingShareUri.collectAsStateWithLifecycle()
    val pendingText by pendingShareText.collectAsStateWithLifecycle()
    /** 收到家人资料/文本分享时的提示（不进入接收流程）。 */
    var shareNotice by remember { mutableStateOf(false) }

    // 通知深链：人物存在才进详情；已故由详情页守卫退回首页
    LaunchedEffect(pending, currentRoute) {
        val id = pending ?: return@LaunchedEffect
        val route = currentRoute ?: return@LaunchedEffect
        if (!route.startsWith("elder/")) return@LaunchedEffect
        if (appNavViewModel.personExists(id)) {
            navController.navigate(Routes.elderPerson(id)) { launchSingleTop = true }
        }
        onPendingConsumed()
    }

    // 事项/标签深链：长辈模式没有对应页面，直接消费丢弃
    LaunchedEffect(pendingTask) {
        if (pendingTask != null) onTaskPendingConsumed()
    }
    LaunchedEffect(pendingTabReq) {
        if (pendingTabReq != null) onTabPendingConsumed()
    }

    // 家人共享文件/文本：不进接收流程，提示后退出长辈模式再重试
    LaunchedEffect(pendingShare, pendingText) {
        if (pendingShare != null || pendingText != null) {
            onSharePendingConsumed()
            onShareTextPendingConsumed()
            shareNotice = true
        }
    }

    NavHost(navController = navController, startDestination = Routes.ELDER_HOME) {
        composable(Routes.ELDER_HOME) {
            ElderHomeScreen(
                onOpenPerson = { navController.navigate(Routes.elderPerson(it)) }
            )
        }
        composable(
            Routes.ELDER_PERSON,
            arguments = listOf(navArgument("personId") { type = NavType.StringType })
        ) {
            ElderPersonScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }

    if (shareNotice) {
        AlertDialog(
            onDismissRequest = { shareNotice = false },
            title = { Text("收到家人资料") },
            text = {
                Text("请先长按页面最下方「退出长辈模式」3 秒，再重新打开这个文件。")
            },
            confirmButton = {
                TextButton(onClick = { shareNotice = false }) { Text("知道了") }
            }
        )
    }
}
