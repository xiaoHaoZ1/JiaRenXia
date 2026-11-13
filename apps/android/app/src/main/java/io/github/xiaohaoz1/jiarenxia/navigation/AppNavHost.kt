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
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    /** 家庭 → 共享与连接（分享、接收、碰一碰）。 */
    const val SHARE_CONNECT = "share/connect"
    /** 家事 → 提醒设置（四类提醒的开关与时刻）。 */
    const val SETTINGS_REMINDERS = "settings/reminders"
    /** 提醒设置 → 上坟提醒（上坟的日子与家人）。 */
    const val SETTINGS_TOMB = "settings/tomb"
    /** 提醒设置 → 回家看看（家与周期、节前提醒）。 */
    const val SETTINGS_HOME = "settings/home"
    /** 长辈模式：首页（只读）。 */
    const val ELDER_HOME = "elder/home"
    /** 长辈模式：人物只读详情。 */
    const val ELDER_PERSON = "elder/person/{personId}"
    fun elderPerson(id: String) = "elder/person/" + id
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
        "task/edit?taskId={taskId}&kind={kind}&personId={personId}&shared={shared}&pregnancyId={pregnancyId}"

    /** 清单页（待产包等，FAMILY_AFFAIRS 26）。 */
    const val CHECKLIST = "checklist/{checklistId}"
    fun checklist(id: String) = "checklist/" + id

    /** 「宝宝出生了」页；append=true 为孕期历史里的补记。 */
    const val BIRTH = "pregnancy/{pregnancyId}/birth?append={append}"
    fun birth(pregnancyId: String, append: Boolean = false) =
        "pregnancy/" + pregnancyId + "/birth?append=" + append

    /** 用药编辑页（FAMILY_AFFAIRS 23）：medId 空 = 新建；personId 必填。 */
    const val MED_EDIT = "med/edit?medId={medId}&personId={personId}"
    fun medEdit(medId: String = "", personId: String) =
        "med/edit?medId=" + medId + "&personId=" + personId

    /** 「给医生看」页：只读用药摘要。 */
    const val MED_DOCTOR = "med/doctor/{personId}"
    fun medDoctor(personId: String) = "med/doctor/" + personId

    /** 事项编辑页：taskId 空 = 新建；kind/personId/shared/pregnancyId 为新建预填参数。 */
        fun shareImport(uri: String?): String =
        if (uri == null) "share/import" else "share/import?uri=" + uri

fun taskEdit(
        taskId: String = "", kind: String = "", personId: String = "",
        shared: String = "", pregnancyId: String = ""
    ): String = "task/edit?taskId=" + taskId + "&kind=" + kind +
        "&personId=" + personId + "&shared=" + shared +
        "&pregnancyId=" + pregnancyId

    /** 家人共享导入（FAMILY_AFFAIRS 9.4）：uri 为可选来源（外部 intent）。 */
    const val SHARE_IMPORT = "share/import?uri={uri}"
    /** 分享选人页（FAMILY_AFFAIRS 9）：branch 为预设分支名（GraphBranch）。 */
    const val SHARE_SCOPE = "share/scope?branch={branch}"
    /** 碰一碰：kind = HELLO 用途（0 结为夫妻 / 1 互加亲人），由入口带入。 */
    const val TAP = "tap?kind={kind}"
    fun tap(kind: Int) = "tap?kind=" + kind
    /** 半颗心调试预览：left=true 男方左半边。 */
    const val TAP_HEART = "tap/heart?left={left}"
    fun tapHeart(left: Boolean) = "tap/heart?left=" + left
    /** 分享选人页：branch 为 GraphBranch 名（缺省全部）。 */
    fun shareScope(branch: String = "") =
        "share/scope?branch=" + branch
}


@Composable
fun AppNavHost(
    startRoute: String,
    /** 长辈模式开关（MainActivity 收集 settings.elderMode）；true 且已有“我”时走独立长辈导航。 */
    elderMode: StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false),
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
    onShareTextPendingConsumed: () -> Unit = {},
    /** 「打完电话记一笔」通知点开：落到该人详情页后恢复“联系上了吗”待提示。 */
    pendingPostCall: StateFlow<io.github.xiaohaoz1.jiarenxia.data.ContactPrompt?> =
        kotlinx.coroutines.flow.MutableStateFlow(null),
    onPostCallPendingConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    // 碰一碰备用入口尚无文件，先选择对方发来的共享文件；取消不改变页面。
    val tapSharePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            navController.navigate(Routes.shareImport(android.net.Uri.encode(uri.toString()))) {
                launchSingleTop = true
            }
        }
    }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val pending by pendingPersonId.collectAsStateWithLifecycle()
    val pendingTask by pendingTaskId.collectAsStateWithLifecycle()
    val pendingTabReq by pendingTab.collectAsStateWithLifecycle()
    val pendingShare by pendingShareUri.collectAsStateWithLifecycle()
    val pendingText by pendingShareText.collectAsStateWithLifecycle()
    val pendingPostCallReq by pendingPostCall.collectAsStateWithLifecycle()
    val appNavViewModel: AppNavViewModel = hiltViewModel()
    /** 详情页 → 底部标签切换请求（“查看全部”去家事页）。 */
    val tabRequests = remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }

    // 长辈模式：已有“我”时整站切换为只读长辈导航（不渲染 MainShell 与编辑/设置路由）；
    // 退出（elderMode=false）自动回到正常主界面
    val elder by elderMode.collectAsStateWithLifecycle()
    val hasSelf by appNavViewModel.hasSelfFlow.collectAsStateWithLifecycle()
    if (elder && hasSelf) {
        ElderNavHost(
            appNavViewModel = appNavViewModel,
            pendingPersonId = pendingPersonId,
            onPendingConsumed = onPendingConsumed,
            pendingTaskId = pendingTaskId,
            onTaskPendingConsumed = onTaskPendingConsumed,
            pendingTab = pendingTab,
            onTabPendingConsumed = onTabPendingConsumed,
            pendingShareUri = pendingShareUri,
            onSharePendingConsumed = onSharePendingConsumed,
            pendingShareText = pendingShareText,
            onShareTextPendingConsumed = onShareTextPendingConsumed
        )
        return
    }

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

    // 「打完电话记一笔」通知点开：深链先经 pendingPersonId 导航，
    // 这里等路由真正落到该人详情页才 rearm——强制待提示只会被那页
    // 拿到（若提早发射，途中其它页会先消费）；人物已删则直接丢弃
    LaunchedEffect(pendingPostCallReq, currentEntry) {
        val p = pendingPostCallReq ?: return@LaunchedEffect
        val entry = currentEntry ?: return@LaunchedEffect
        val route = entry.destination.route ?: return@LaunchedEffect
        if (route == Routes.LAUNCH || route == Routes.WELCOME ||
            route == Routes.INTRO ||
            route.startsWith("onboarding")) return@LaunchedEffect
        if (!appNavViewModel.personExists(p.personId)) {
            onPostCallPendingConsumed()
            return@LaunchedEffect
        }
        if (route != Routes.PERSON ||
            entry.arguments?.getString("personId") != p.personId
        ) return@LaunchedEffect
        onPostCallPendingConsumed()
        appNavViewModel.rearmPostCall(p)
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
                },
                onOpenTombSettings = {
                    navController.navigate(Routes.SETTINGS_TOMB)
                },
                onOpenHomeSettings = {
                    navController.navigate(Routes.SETTINGS_HOME)
                }
            )
        }

        composable(
            Routes.PERSON,
            arguments = listOf(navArgument("personId") { type = NavType.StringType })
        ) {
            val personId = it.arguments?.getString("personId").orEmpty()
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
                onAddPrenatalCheckup = { personId, pregnancyId ->
                    navController.navigate(
                        Routes.taskEdit(personId = personId, pregnancyId = pregnancyId)
                    )
                },
                onOpenTaskDetail = { navController.navigate(Routes.taskEdit(taskId = it)) },
                onOpenChecklist = { navController.navigate(Routes.checklist(it)) },
                onOpenBirth = { pregId, append ->
                    navController.navigate(Routes.birth(pregId, append))
                },
                onOpenMedEdit = { medId ->
                    navController.navigate(Routes.medEdit(medId ?: "", personId))
                },
                onOpenMedDoctor = {
                    navController.navigate(Routes.medDoctor(personId))
                }
            )
        }

        composable(
            Routes.MED_EDIT,
            arguments = listOf(
                navArgument("medId") {
                    type = NavType.StringType; defaultValue = ""
                },
                navArgument("personId") { type = NavType.StringType }
            )
        ) {
            io.github.xiaohaoz1.jiarenxia.ui.med.MedEditScreen(
                onDone = { navController.popBackStack() }
            )
        }

        composable(
            Routes.MED_DOCTOR,
            arguments = listOf(navArgument("personId") { type = NavType.StringType })
        ) {
            io.github.xiaohaoz1.jiarenxia.ui.med.MedDoctorScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.CHECKLIST,
            arguments = listOf(navArgument("checklistId") { type = NavType.StringType })
        ) {
            io.github.xiaohaoz1.jiarenxia.ui.checklist.ChecklistScreen(
                onDone = { navController.popBackStack() },
                onOpenTask = { navController.navigate(Routes.taskEdit(taskId = it)) }
            )
        }

        composable(
            Routes.BIRTH,
            arguments = listOf(
                navArgument("pregnancyId") { type = NavType.StringType },
                navArgument("append") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            io.github.xiaohaoz1.jiarenxia.ui.pregnancy.BirthScreen(
                onBack = { navController.popBackStack() },
                onOpenChecklist = { navController.navigate(Routes.checklist(it)) },
                onShareFamily = { navController.navigate(Routes.shareScope()) },
                onShareNews = { text ->
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, text)
                    runCatching {
                        context.startActivity(
                            android.content.Intent.createChooser(send, null)
                        )
                    }
                }
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

        composable(
            Routes.TAP_HEART,
            arguments = listOf(
                androidx.navigation.navArgument("left") {
                    type = androidx.navigation.NavType.BoolType
                    defaultValue = true
                }
            )
        ) { e ->
            // 调试预览：全屏拼心展示（只从 debug 我的页进入）
            io.github.xiaohaoz1.jiarenxia.ui.tap.HeartShowPreview(
                leftHalf = e.arguments?.getBoolean("left") != false)
        }

        composable(
            Routes.TAP,
            arguments = listOf(
                navArgument("kind") {
                    type = NavType.IntType
                    defaultValue =
                        io.github.xiaohaoz1.jiarenxia.nfc.TapProtocol
                            .KIND_SPOUSE.toInt()
                }
            )
        ) {
            io.github.xiaohaoz1.jiarenxia.ui.tap.TapScreen(
                onBack = { navController.popBackStack() },
                onDone = {
                    navController.navigate(Routes.graph()) {
                        launchSingleTop = true
                    }
                },
                onShare = {
                    tapSharePicker.launch(arrayOf("*/*"))
                }
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
                onHeartPreview = { left ->
                    navController.navigate(Routes.tapHeart(left))
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
                onBack = { navController.popBackStack() },
                onOpenTombSettings = { navController.navigate(Routes.SETTINGS_TOMB) },
                onOpenHomeSettings = { navController.navigate(Routes.SETTINGS_HOME) }
            )
        }

        composable(Routes.SETTINGS_TOMB) {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.TombSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS_HOME) {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitScreen(
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
                onTap = { kind ->
                    navController.navigate(Routes.tap(kind.toInt())) {
                        launchSingleTop = true
                    }
                },
                onNeedsSelf = {
                    navController.navigate(Routes.ONBOARD_SELF) {
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
                navArgument("shared") { type = NavType.StringType; defaultValue = "" },
                navArgument("pregnancyId") { type = NavType.StringType; defaultValue = "" }
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
    val events: io.github.xiaohaoz1.jiarenxia.data.AppEvents,
    private val postCall: io.github.xiaohaoz1.jiarenxia.reminder.PostCallNotifier
) : ViewModel() {
    suspend fun personExists(id: String): Boolean =
        repository.observePersonDetail(id).first() != null

    /** 「打完电话记一笔」通知点开：把待提示恢复为强制态（含「只问一次」重建）。 */
    suspend fun rearmPostCall(p: io.github.xiaohaoz1.jiarenxia.data.ContactPrompt) =
        postCall.rearm(p)

    suspend fun taskExists(id: String): Boolean = taskRepository.getTask(id) != null

    /** 是否已有“我”（共享文件消费策略：新装允许直接进导入页）。 */
    suspend fun hasSelf(): Boolean =
        repository.observeSelf().first() != null

    /** “我”是否存在的持续流（长辈模式导航门控；清空数据后自动退出长辈导航）。 */
    val hasSelfFlow: kotlinx.coroutines.flow.StateFlow<Boolean> =
        repository.observeSelf()
            .map { it != null }
            .stateIn(
                viewModelScope,
                kotlinx.coroutines.flow.SharingStarted.Eagerly, true
            )
}
