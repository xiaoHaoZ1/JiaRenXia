package io.github.xiaohaoz1.jiarenxia.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.xiaohaoz1.jiarenxia.ui.family.FamilyScreen
import io.github.xiaohaoz1.jiarenxia.ui.mine.MineScreen
import io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderScreen

/**
 * 主界面外壳：底部导航 3 个标签——家庭 / 家事 / 我的。
 * 守护已拆为独立应用「守护侠」（C3），不再有守护标签。
 */
private data class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val tabs = listOf(
    MainTab(route = "family", label = "家庭", icon = Icons.Rounded.Home),
    MainTab(route = "reminder", label = "家事", icon = Icons.Rounded.EventNote),
    MainTab(route = "mine", label = "我的", icon = Icons.Rounded.Person)
)

/** 底部导航栏：三项等宽，沿用原图标与选中样式；抽出供截图测试直接渲染。 */
@Composable
internal fun MainBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onSelect(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

@Composable
fun MainShell(
    onOpenPerson: (String) -> Unit,
    onAddFamily: () -> Unit,
    onSearch: () -> Unit,
    onOpenGraph: () -> Unit,
    /** 打开事项编辑：taskId 空 = 新建；kind/personId 为新建预填。 */
    onOpenTaskEdit: (String?, String?, String?) -> Unit = { _, _, _ -> },
    /** 外部请求切换底部标签（“查看全部”→ 家事）。 */
    tabRequests: kotlinx.coroutines.flow.MutableStateFlow<String?>? = null,
    /** 我的页的四个二级入口（2026-10-09 界面改版）。 */
    onDataManage: () -> Unit = {},
    onAppearance: () -> Unit = {},
    onModules: () -> Unit = {},
    onAbout: () -> Unit = {},
    /** 家庭页「共享与连接」：分享、接收、碰一碰。 */
    onShareConnect: () -> Unit = {},
    /** 家事页顶栏铃铛：三类提醒的开关与时刻。 */
    onReminderSettings: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {}
) {
    val navController = rememberNavController()
    // 消费标签切换请求（详情页“查看全部”跳家事页）
    LaunchedEffect(tabRequests) {
        tabRequests?.collect { route ->
            if (route != null) {
                navController.navigate(route) { launchSingleTop = true }
                tabRequests.value = null
            }
        }
    }
    Scaffold(
        // material3 1.4 起 contentWindowInsets 默认含状态栏/导航栏全量；
        // 顶部留白由各页 PageHeader 自己负责，这里只保留水平向
        // （横屏刘海），底部由 NavigationBar 自身处理
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            MainBottomBar(currentRoute) { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "family",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("family") {
                FamilyScreen(
                    onOpenPerson = onOpenPerson,
                    onAddFamily = onAddFamily,
                    onSearch = onSearch,
                    onOpenGraph = onOpenGraph,
                    onShareConnect = onShareConnect
                )
            }
            composable("reminder") {
                ReminderScreen(
                    onOpenPerson = onOpenPerson,
                    onOpenTask = { onOpenTaskEdit(it, null, null) },
                    onNewTask = { onOpenTaskEdit(null, it, null) },
                    onReminderSettings = onReminderSettings
                )
            }
            composable("mine") {
                MineScreen(
                    onOpenPerson = onOpenPerson,
                    onDataManage = onDataManage,
                    onAppearance = onAppearance,
                    onModules = onModules,
                    onAbout = onAbout
                )
            }
        }
    }
}
