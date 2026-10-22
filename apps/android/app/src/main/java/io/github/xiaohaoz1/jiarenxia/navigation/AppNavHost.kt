package io.github.xiaohaoz1.jiarenxia.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.xiaohaoz1.jiarenxia.ui.addfamily.AddFamilyScreen
import io.github.xiaohaoz1.jiarenxia.ui.launch.LaunchScreen
import io.github.xiaohaoz1.jiarenxia.ui.main.MainShell
import io.github.xiaohaoz1.jiarenxia.ui.person.AddRelationScreen
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonDetailScreen
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonEditMode
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonEditScreen
import io.github.xiaohaoz1.jiarenxia.ui.search.SearchScreen

/**
 * 顶层导航（DEVELOPMENT_PLAN 2.10 / FAMILY_AFFAIRS 16.1）。
 *
 * 路由约定：launch →（无“我”）welcome → onboarding/self → onboarding/family
 * → main；人物相关页（详情 / 编辑 / 新建 / 加关系）在 main 之上整页覆盖。
 * person/new 参数：anchorId 为空表示锚点可选；roles 为 RelationRole 逗号列表；
 * anchors 为可选锚点候选 id 列表（缺省为全部人物）。
 */
object Routes {
    const val LAUNCH = "launch"
    /** 欢迎页（FAMILY_AFFAIRS 16.1）：无“我”时的新建/从文件开始入口。 */
    const val WELCOME = "welcome"
    /**
     * 新手引导（四页轮播）：fromMine=true 表示从我的页重看，完成返回上一层；
     * false 为首启路径（启动页 → 无“我”且未看过），完成去欢迎页。
     */
    const val INTRO = "intro?fromMine={fromMine}"
    fun intro(fromMine: Boolean = false) = "intro?fromMine=$fromMine"
    const val ONBOARD_SELF = "onboarding/self"
    const val ONBOARD_FAMILY = "onboarding/family"
    const val MAIN = "main"
    const val ADD_FAMILY = "add-family"
    const val SEARCH = "search"
    const val GRAPH = "graph?focus={focus}"
    const val PRIVACY = "privacy"

    /** 我的 → 二级页（2026-10-09 界面改版：一级页只放入口）。 */
    const val SETTINGS_DATA = "settings/data"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_MODULES = "settings/modules"
    const val SETTINGS_ABOUT = "settings/about"
    /** 家庭 → 共享与连接（分享、接收）。 */
    const val SHARE_CONNECT = "share/connect"
    /** 家事 → 提醒设置（三类提醒的开关与时刻）。 */
    const val SETTINGS_REMINDERS = "settings/reminders"
    const val PERSON = "person/{personId}"
    const val PERSON_EDIT = "person/{personId}/edit"
    const val PERSON_RELATE = "person/{personId}/relate"
    const val PERSON_NEW = "person/new?anchorId={anchorId}&roles={roles}&anchors={anchors}"

    /** 关系图；focus 非空时定位并高亮该人。 */
    fun graph(focus: String = "") =
        "graph" + if (focus.isBlank()) "" else "?focus=" + android.net.Uri.encode(focus)

    fun person(id: String) = "person/" + id
    fun personEdit(id: String) = "person/" + id + "/edit"
    fun personRelate(id: String) = "person/" + id + "/relate"
    fun personNew(
        anchorId: String = "",
        roles: List<String> = emptyList(),
        anchors: List<String> = emptyList()
    ) = "person/new?anchorId=" + anchorId +
        "&roles=" + roles.joinToString(",") +
        "&anchors=" + anchors.joinToString(",")
    const val TASK_EDIT =
        "task/edit?taskId={taskId}&kind={kind}&personId={personId}&shared={shared}"

    /** 事项编辑页：taskId 空 = 新建；kind/personId/shared 为新建预填参数。 */
        fun shareImport(uri: String?): String =
        if (uri == null) "share/import" else "share/import?uri=" + uri

fun taskEdit(
        taskId: String = "", kind: String = "", personId: String = "",
        shared: String = ""
    ): String = "task/edit?taskId=" + taskId + "&kind=" + kind +
        "&personId=" + personId + "&shared=" + shared

    /** 家人共享导入（FAMILY_AFFAIRS 9.4）：uri 为可选来源（外部 intent）。 */
    const val SHARE_IMPORT = "share/import?uri={uri}"
    /** 分享选人页（FAMILY_AFFAIRS 9）：branch 为预设分支名（GraphBranch）。 */
    const val SHARE_SCOPE = "share/scope?branch={branch}"
    /** 协作接收审查页（FAMILY_AFFAIRS §5）：包经 AppEvents.pendingCollab 或待办草稿进入。 */
    const val SHARE_COLLAB = "share/collab"
    /** 分享选人页：branch 为 GraphBranch 名（缺省全部）。 */
    fun shareScope(branch: String = "") =
        "share/scope?branch=" + branch
}


@Composable
fun AppNavHost(
    startRoute: String,
    pendingPersonId: StateFlow<String?>,
    onPendingConsumed: () -> Unit,
    pendingTaskId: StateFlow<String?> = kotlinx.coroutines.flow.MutableStateFlow(null),
    onTaskPendingConsumed: () -> Unit = {},
    /** 桌面插件打开指定底部标签（值为 MainShell 的 tab route，如 "reminder"）。 */
    pendingTab: StateFlow<String?> = kotlinx.coroutines.flow.MutableStateFlow(null),
    onTabPendingConsumed: () -> Unit = {},
    /** 家人共享文件 Uri（VIEW/SEND intent）；MAIN 或新装引导页时消费。 */
    pendingShareUri: StateFlow<android.net.Uri?> =
        kotlinx.coroutines.flow.MutableStateFlow(null),
    onSharePendingConsumed: () -> Unit = {},
    /** 文本分享（SEND text/plain → EXTRA_TEXT）；正常页面时消费，预填事项编辑页。 */
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
    val appNavViewModel: AppNavViewModel = hiltViewModel()
    /** 详情页 → 底部标签切换请求（“查看全部”去家事页）。 */
    val tabRequests = remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }

    // 通知深链：路由完成（已有“我”）后，从任意页面跳到对应人物详情；
    // 启动页/欢迎页/引导页期间不消费
    LaunchedEffect(pending, currentRoute) {
        val id = pending ?: return@LaunchedEffect
        val route = currentRoute ?: return@LaunchedEffect
        if (route == Routes.LAUNCH || route == Routes.WELCOME ||
            route == Routes.INTRO ||
            route.startsWith("onboarding")) return@LaunchedEffect
        if (appNavViewModel.personExists(id)) {
            navController.navigate(Routes.person(id)) { launchSingleTop = true }
        }
        onPendingConsumed()
    }

    // 事项通知深链：启动页/欢迎页/引导页期间不消费；事项不存在就忽略
    LaunchedEffect(pendingTask, currentRoute) {
        val id = pendingTask ?: return@LaunchedEffect
        val route = currentRoute ?: return@LaunchedEffect
        if (route == Routes.LAUNCH || route == Routes.WELCOME ||
            route == Routes.INTRO ||
            route.startsWith("onboarding")) return@LaunchedEffect
        if (appNavViewModel.taskExists(id)) {
            navController.navigate(Routes.taskEdit(taskId = id)) { launchSingleTop = true }
        }
        onTaskPendingConsumed()
    }

    // 插件「打开家事页」深链：不在 main 上时先弹回 main，再由 tabRequests 切标签；
    // 启动页/欢迎页/引导页期间不消费
    LaunchedEffect(pendingTabReq, currentRoute) {
        val tab = pendingTabReq ?: return@LaunchedEffect
        val route = currentRoute ?: return@LaunchedEffect
        if (route == Routes.LAUNCH || route == Routes.WELCOME ||
            route == Routes.INTRO ||
            route.startsWith("onboarding")) return@LaunchedEffect
        if (route != Routes.MAIN &&
            !navController.popBackStack(Routes.MAIN, inclusive = false)) {
            onTabPendingConsumed()
            return@LaunchedEffect
        }
        tabRequests.value = tab
        onTabPendingConsumed()
    }

    // 家人共享文件：启动页/引导期之外不消费（新装——还没有“我”——例外允许），
    // 进入 MAIN 或 WELCOME/ONBOARD_SELF（没装数据的新用户）时打开导入页
    LaunchedEffect(pendingShare, currentRoute) {
        val uri = pendingShare ?: return@LaunchedEffect
        val route = currentRoute ?: return@LaunchedEffect
        val hasSelf = appNavViewModel.hasSelf()
        val allowed = route == Routes.MAIN ||
            ((route == Routes.WELCOME || route == Routes.ONBOARD_SELF) && !hasSelf)
        if (!allowed) return@LaunchedEffect
        onSharePendingConsumed()
        navController.navigate(
            Routes.shareImport(android.net.Uri.encode(uri.toString()))
        ) { launchSingleTop = true }
    }
    // 文本分享「记为家事」：启动页/欢迎页/引导页期间不消费（数据层未就绪），
    // 正常页面时带 shared 参数打开事项编辑页，由用户确认后保存
    LaunchedEffect(pendingText, currentRoute) {
        val text = pendingText ?: return@LaunchedEffect
        val route = currentRoute ?: return@LaunchedEffect
        if (route == Routes.LAUNCH || route == Routes.WELCOME ||
            route == Routes.INTRO ||
            route.startsWith("onboarding")) return@LaunchedEffect
        onShareTextPendingConsumed()
        navController.navigate(
            Routes.taskEdit(shared = android.net.Uri.encode(text))
        ) { launchSingleTop = true }
    }

    // 恢复流程（我的/欢迎页）识别出共享文件 → 由各自 onShareImport 回调导航
    // （plan 已解密，经 appEvents.pendingSharePlan 传给导入页 ViewModel）

    // 清空所有数据（我的页两段确认 / 锁屏忘记密码共用此出口）→ 回到欢迎页
    LaunchedEffect(Unit) {
        appNavViewModel.events.dataCleared.collect {
            navController.navigate(Routes.WELCOME) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = startRoute) {

        composable(Routes.LAUNCH) {
            LaunchScreen(onFinished = { hasSelf, introSeen ->
                navController.navigate(
                    when {
                        hasSelf -> Routes.MAIN
                        // 未看过引导的新用户先看四页新手引导
                        introSeen -> Routes.WELCOME
                        else -> Routes.intro()
                    }
                ) {
                    popUpTo(Routes.LAUNCH) { inclusive = true }
                }
            })
        }

        composable(
            Routes.INTRO,
            arguments = listOf(
                navArgument("fromMine") {
                    type = NavType.BoolType; defaultValue = false
                }
            )
        ) { entry ->
            io.github.xiaohaoz1.jiarenxia.ui.welcome.IntroScreen(
                onDone = {
                    if (entry.arguments?.getBoolean("fromMine") == true) {
                        // 我的页重看：返回上一层
                        navController.popBackStack()
                    } else {
                        // 首启路径：去欢迎页，引导页出栈（系统返回不会弹回引导）
                        navController.navigate(Routes.WELCOME) {
                            popUpTo(Routes.INTRO) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.WELCOME) {
            io.github.xiaohaoz1.jiarenxia.ui.welcome.WelcomeScreen(
                // “开始填写”不清栈：系统返回弹回欢迎页
                onNewFamily = { navController.navigate(Routes.ONBOARD_SELF) },
                onShareImport = {
                    navController.navigate(Routes.shareImport(null)) {
                        launchSingleTop = true
                    }
                },
                onCollabDetected = {
                    navController.navigate(Routes.SHARE_COLLAB) {
                        launchSingleTop = true
                    }
                },
                onRestored = { hasSelf ->
                    navController.navigate(
                        if (hasSelf) Routes.MAIN else Routes.ONBOARD_SELF
                    ) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ONBOARD_SELF) {
            PersonEditScreen(
                mode = PersonEditMode.SELF,
                onDone = {
                    navController.navigate(Routes.ONBOARD_FAMILY) {
                        popUpTo(Routes.ONBOARD_SELF) { inclusive = true }
                    }
                },
                // SELF 模式无返回键且 BackHandler 不拦截：
                // 从欢迎页进入时系统返回弹回欢迎页；清空数据进入（popUpTo(0)）
                // 是唯一栈顶 → 不弹（防线：避免弹空出现白屏），由系统退出应用
                onBack = {
                    if (navController.previousBackStackEntry != null)
                        navController.popBackStack()
                }
            )
        }

        composable(Routes.ONBOARD_FAMILY) {
            AddFamilyScreen(
                onboarding = true,
                onEntry = { entry ->
                    navController.navigate(
                        Routes.personNew(entry.anchorId, entry.roleNames, entry.anchorCandidates)
                    )
                },
                onHint = { entry ->
                    // 缺前置（如“请先添加父亲”）→ 跳到前置人物表单
                    navController.navigate(
                        Routes.personNew(entry.hintAnchorId, entry.hintRoles.map { it.name }, emptyList())
                    )
                },
                onDone = {
                    navController.navigate(Routes.MAIN) { popUpTo(0) { inclusive = true } }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MAIN) {
            MainShell(
                onOpenPerson = { navController.navigate(Routes.person(it)) },
                onAddFamily = { navController.navigate(Routes.ADD_FAMILY) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onOpenGraph = { navController.navigate(Routes.graph()) },
                tabRequests = tabRequests,
                onOpenTaskEdit = { taskId, kind, personId ->
                    navController.navigate(
                        Routes.taskEdit(taskId ?: "", kind ?: "", personId ?: "")
                    )
                },
                onDataManage = { navController.navigate(Routes.SETTINGS_DATA) },
                onAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                onModules = { navController.navigate(Routes.SETTINGS_MODULES) },
                onAbout = { navController.navigate(Routes.SETTINGS_ABOUT) },
                onShareConnect = { navController.navigate(Routes.SHARE_CONNECT) },
                onReminderSettings = {
                    navController.navigate(Routes.SETTINGS_REMINDERS)
                }
            )
        }

        composable(
            Routes.PERSON,
            arguments = listOf(navArgument("personId") { type = NavType.StringType })
        ) {
            PersonDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.personEdit(it)) },
                onRelate = { navController.navigate(Routes.personRelate(it)) },
                onOpenPerson = { navController.navigate(Routes.person(it)) },
                onOpenGraph = { navController.navigate(Routes.graph(it)) },
                onOpenAffairs = {
                    tabRequests.value = "reminder"
                    navController.popBackStack()
                },
                onAddTask = { navController.navigate(Routes.taskEdit(personId = it)) },
                onOpenTaskDetail = { navController.navigate(Routes.taskEdit(taskId = it)) }
            )
        }

        composable(
            Routes.PERSON_EDIT,
            arguments = listOf(navArgument("personId") { type = NavType.StringType })
        ) {
            PersonEditScreen(
                mode = PersonEditMode.EDIT,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.PERSON_RELATE,
            arguments = listOf(navArgument("personId") { type = NavType.StringType })
        ) {
            AddRelationScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Routes.PERSON_NEW,
            arguments = listOf(
                navArgument("anchorId") { type = NavType.StringType; defaultValue = "" },
                navArgument("roles") { type = NavType.StringType; defaultValue = "" },
                navArgument("anchors") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            PersonEditScreen(
                mode = PersonEditMode.NEW,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.SHARE_IMPORT,
            arguments = listOf(
                navArgument("uri") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                }
            )
        ) {
            io.github.xiaohaoz1.jiarenxia.ui.share.ShareImportScreen(
                onDone = { name ->
                    navController.navigate(Routes.MAIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onCollabDetected = {
                    navController.navigate(Routes.SHARE_COLLAB) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            Routes.SHARE_SCOPE,
            arguments = listOf(
                navArgument("branch") {
                    type = NavType.StringType
                    defaultValue = io.github.xiaohaoz1.jiarenxia.ui.graph
                        .GraphBranch.ALL.name
                }
            )
        ) {
            io.github.xiaohaoz1.jiarenxia.ui.share.ShareScopeScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADD_FAMILY) {
            AddFamilyScreen(
                onboarding = false,
                onEntry = { entry ->
                    navController.navigate(
                        Routes.personNew(entry.anchorId, entry.roleNames, entry.anchorCandidates)
                    )
                },
                onHint = { entry ->
                    navController.navigate(
                        Routes.personNew(entry.hintAnchorId, entry.hintRoles.map { it.name }, emptyList())
                    )
                },
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onOpenGraph = { navController.navigate(Routes.graph()) }
            )
        }

        composable(
            Routes.GRAPH,
            arguments = listOf(
                navArgument("focus") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            io.github.xiaohaoz1.jiarenxia.ui.graph.GraphScreen(
                onBack = { navController.popBackStack() },
                onOpenPerson = { navController.navigate(Routes.person(it)) },
                onShareScope = { branch ->
                    navController.navigate(Routes.shareScope(branch.name))
                },
                onAddFamily = { navController.navigate(Routes.ADD_FAMILY) }
            )
        }

        composable(Routes.PRIVACY) {
            io.github.xiaohaoz1.jiarenxia.ui.mine.PrivacyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ---------- 设置二级页（2026-10-09 界面改版）----------

        composable(Routes.SETTINGS_DATA) {
            io.github.xiaohaoz1.jiarenxia.ui.mine.DataManageScreen(
                onBack = { navController.popBackStack() },
                onDataCleared = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                // 恢复的数据里没有“我” → 创建“我”
                onNeedsSelf = {
                    navController.navigate(Routes.ONBOARD_SELF) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onShareImport = {
                    navController.navigate(Routes.shareImport(null)) {
                        launchSingleTop = true
                    }
                },
                onCollabDetected = {
                    navController.navigate(Routes.SHARE_COLLAB) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.SETTINGS_APPEARANCE) {
            io.github.xiaohaoz1.jiarenxia.ui.mine.AppearanceScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS_MODULES) {
            io.github.xiaohaoz1.jiarenxia.ui.mine.ModulesScreen(
                onBack = { navController.popBackStack() },
                onOpenShareConnect = { navController.navigate(Routes.SHARE_CONNECT) }
            )
        }

        composable(Routes.SETTINGS_ABOUT) {
            io.github.xiaohaoz1.jiarenxia.ui.mine.AboutScreen(
                onBack = { navController.popBackStack() },
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
                onIntro = { navController.navigate(Routes.intro(fromMine = true)) }
            )
        }

        composable(Routes.SETTINGS_REMINDERS) {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SHARE_CONNECT) {
            io.github.xiaohaoz1.jiarenxia.ui.mine.ShareConnectScreen(
                onBack = { navController.popBackStack() },
                onShareScope = {
                    navController.navigate(
                        Routes.shareScope(
                            io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranch.ALL.name
                        )
                    )
                },
                onShareImport = {
                    navController.navigate(Routes.shareImport(null)) {
                        launchSingleTop = true
                    }
                },
                onNeedsSelf = {
                    navController.navigate(Routes.ONBOARD_SELF) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onCollabDetected = {
                    navController.navigate(Routes.SHARE_COLLAB) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // 协作接收审查页：确认版/补充包的差异核对、身份裁决、应用
        composable(Routes.SHARE_COLLAB) {
            io.github.xiaohaoz1.jiarenxia.ui.share.CollabReviewScreen(
                onBack = { navController.popBackStack() },
                onDone = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenPerson = { navController.navigate(Routes.person(it)) }
            )
        }

        composable(
            Routes.TASK_EDIT,
            arguments = listOf(
                navArgument("taskId") { type = NavType.StringType; defaultValue = "" },
                navArgument("kind") { type = NavType.StringType; defaultValue = "" },
                navArgument("personId") { type = NavType.StringType; defaultValue = "" },
                navArgument("shared") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            io.github.xiaohaoz1.jiarenxia.ui.task.TaskEditScreen(
                onDone = { navController.popBackStack() }
            )
        }
    }

    // 启动自动检查发现新版时的应用级提醒弹窗（含「不再提示此版本」）
    io.github.xiaohaoz1.jiarenxia.ui.mine.UpdatePromptHost()
}

/** NavHost 级 ViewModel：深链目标人物存在性检查。 */
@dagger.hilt.android.lifecycle.HiltViewModel
class AppNavViewModel @javax.inject.Inject constructor(
    private val repository: FamilyRepository,
    private val taskRepository: io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepository,
    val events: io.github.xiaohaoz1.jiarenxia.data.AppEvents
) : ViewModel() {
    suspend fun personExists(id: String): Boolean =
        repository.observePersonDetail(id).first() != null

    suspend fun taskExists(id: String): Boolean = taskRepository.getTask(id) != null

    /** 是否已有“我”（共享文件消费策略：新装允许直接进导入页）。 */
    suspend fun hasSelf(): Boolean =
        repository.observeSelf().first() != null
}
