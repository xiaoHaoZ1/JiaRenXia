package io.github.xiaohaoz1.jiarenxia.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.xiaohaoz1.jiarenxia.ui.family.FamilyScreen
import io.github.xiaohaoz1.jiarenxia.ui.mine.MineScreen
import io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderScreen
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors

/**
 * 主界面外壳：底部导航 3 个标签——家庭 / 家事 / 我的。
 * 守护已拆为独立应用「守护侠」（C3），不再有守护标签。
 */
private data class MainTab(
    val route: String,
    val label: String
)

private val tabs = listOf(
    MainTab(route = "family", label = "家庭"),
    MainTab(route = "reminder", label = "家事"),
    MainTab(route = "mine", label = "我的")
)

/**
 * 底部导航栏（「家书」改版 2.22）：三项等宽，只有文字，不用图标与 Material 指示器；
 * 选中项墨色粗体、下方一个朱砂圆点，未选中 outline 色；顶部一条发丝线。
 * 自己处理系统导航栏留白；抽出供截图测试直接渲染。
 */
@Composable
internal fun MainBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val jrx = LocalJrxColors.current
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    Column(
                        modifier = Modifier.weight(1f)
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onSelect(tab.route) }
                            )
                            .padding(top = 12.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            tab.label,
                            fontSize = 16.sp,
                            letterSpacing = 2.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier.size(5.dp).clip(CircleShape)
                                .background(if (selected) jrx.seal else Color.Transparent)
                        )
                    }
                }
            }
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
