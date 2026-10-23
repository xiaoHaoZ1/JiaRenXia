package io.github.xiaohaoz1.jiarenxia.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.ui.addfamily.AddFamilyContent
import io.github.xiaohaoz1.jiarenxia.ui.addfamily.buildQuickEntries
import io.github.xiaohaoz1.jiarenxia.ui.family.FamilyContent
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranch
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphFamilyScope
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphContent
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphLayout
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphTextMeasure
import io.github.xiaohaoz1.jiarenxia.ui.mine.MineContent
import io.github.xiaohaoz1.jiarenxia.ui.main.MainBottomBar
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonEditCallbacks
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonEditContent
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonEditMode
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonEditUiState
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonDetailContent
import io.github.xiaohaoz1.jiarenxia.reminder.BirthdayRow
import io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderContent
import io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderUiState
import io.github.xiaohaoz1.jiarenxia.ui.theme.JiaRenXiaTheme
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * README 与适老化截图（opt-in）：仅在 `-PreadmeShots=true` 时生成文件。
 *
 * - 七张 README 截图：手机尺寸（412×915dp），缩小到 540px 宽写入 docs/images/。
 * - 公开截图统一使用英文合成姓名、健在人物，避免重名与状态标记造成误解。
 * - 大字号走查：fontScale 1.3 / 1.5 各一遍，写到临时目录供人工检查。
 * 普通测试运行全部跳过（不触碰 docs/images）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ReadmeShotsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun assume() =
        Assume.assumeTrue(
            "README/大字号截图仅在 -PreadmeShots=true 时生成",
            System.getProperty("jrx.readmeShots") == "true"
        )

    private fun render(dark: Boolean, route: String? = null, content: @Composable () -> Unit) {
        composeRule.setContent {
            JiaRenXiaTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    if (route == null) content() else Scaffold(
                        bottomBar = { MainBottomBar(route) {} }
                    ) { padding ->
                        Box(Modifier.padding(padding)) { content() }
                    }
                }
            }
        }
    }

    /** 抓帧 → Bitmap 缩放到 targetWidth 宽 → 写 PNG（Robolectric 影子实现）。 */
    private fun save(out: File, targetWidth: Int) {
        val tmp = File("build/outputs/roborazzi/_tmp_readme.png")
        tmp.parentFile.mkdirs()
        composeRule.onRoot().captureRoboImage(tmp.absolutePath)
        val src = android.graphics.BitmapFactory.decodeFile(tmp.absolutePath)
        val th = (src.height.toDouble() * targetWidth / src.width).toInt()
        val dst = android.graphics.Bitmap.createScaledBitmap(src, targetWidth, th, true)
        out.parentFile.mkdirs()
        out.outputStream().use { dst.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        println("README shot " + out.absolutePath + " size=" + out.length() / 1024 + "KB")
    }

    private fun raw(out: File) {
        out.parentFile.mkdirs()
        composeRule.onRoot().captureRoboImage(out.absolutePath)
        println("wrote ${out.absolutePath} ${out.length() / 1024}KB")
    }

    // ---------- README 截图 ----------

    @Test
    fun readmeHome() {
        assume()
        render(dark = false, route = "family") {
            FamilyContent(Demo.familyState(publicSample = true), Demo.lunar, {}, {}, {}, {}, {})
        }
        save(File("../../../docs/images/screenshot_home.png"), 540)
    }

    @Test
    fun readmeDetail() {
        assume()
        render(dark = false) {
            PersonDetailContent(
                lunar = Demo.lunar,
                st = Demo.detailOf("uncle", publicSample = true), today = Demo.today,
                snackbar = SnackbarHostState(),
                onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
                onDial = {}, onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> }, onSetCustomTitle = {}, onDeletePerson = {},
                onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
            )
        }
        save(File("../../../docs/images/screenshot_detail.png"), 540)
    }

    @Test
    fun readmeAnniversary() {
        assume()
        render(dark = false) {
            PersonDetailContent(
                lunar = Demo.lunar,
                st = Demo.selfDetail(publicSample = true), today = Demo.today,
                snackbar = SnackbarHostState(),
                onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
                onDial = {}, onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> }, onSetCustomTitle = {}, onDeletePerson = {},
                onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
            )
        }
        save(File("../../../docs/images/screenshot_anniversary.png"), 540)
    }

    @Test
    fun readmeGraph() {
        assume()
        render(dark = false) {
            val ov = Demo.demoOverview(publicSample = true)
            val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
            val visible = GraphFamilyScope.visibleIds(ov, GraphBranch.ALL)
            GraphContent(
                model = GraphLayout.build(
                    ov, gens, visibleIds = visible, sizer = GraphTextMeasure.sizer),
                byId = ov.byId, selfId = ov.self.person.id,
                onBack = {}, onOpenPerson = {},
                branch = GraphBranch.ALL,
                availableBranches = GraphFamilyScope.available(ov),
                familyTabs = GraphFamilyScope.tabs,
                scopeAnchorId = ov.self.person.id,
                connectedTotal = visible.size
            )
        }
        composeRule.onNodeWithText("本支总览").performClick()
        composeRule.waitForIdle()
        save(File("../../../docs/images/screenshot_graph.png"), 540)
    }

    @Test
    fun readmeReminder() {
        assume()
        render(dark = false) {
            ReminderDemo()
        }
        save(File("../../../docs/images/screenshot_reminder.png"), 540)
    }

    /** 家事页 README 图：页头（无分段，A6）+ 提醒内容。 */
    @Test
    fun readmeAffairs() {
        assume()
        render(dark = false, route = "reminder") {
            AffairsDemo()
        }
        save(File("../../../docs/images/screenshot_affairs.png"), 540)
    }

    @Test
    fun readmeHomeDark() {
        assume()
        render(dark = true, route = "family") {
            FamilyContent(Demo.familyState(publicSample = true), Demo.lunar, {}, {}, {}, {}, {})
        }
        save(File("../../../docs/images/screenshot_home_dark.png"), 540)
    }

    // ---------- 大字号走查（fontScale 1.3 / 1.5） ----------

    // fontScale 变更会引发配置变更销毁现有层次；在 setContent 前设定、@After 复位
    @org.junit.After
    fun resetFont() {
        RuntimeEnvironment.setFontScale(1.0f)
    }

    private fun fontShot(name: String, fontScale: Float, dark: Boolean = false,
                         content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        render(dark, content = content)
        val dir = File(System.getenv("JRX_SHOTS_DIR")
            ?: File(System.getProperty("java.io.tmpdir"), "jiarenxia_shots").path)
        val prefix = if (fontScale >= 1.4f) "f15_" else "f13_"
        raw(File(dir, "$prefix$name.png"))
    }

    /** 家事页：真实页头（标题 + 副标题 + 铃铛/更多行，无分段）+ 提醒内容。 */
    @Composable
    private fun AffairsDemo() {
        Column {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.AffairsHeader(
                subtitle = io.github.xiaohaoz1.jiarenxia.ui.reminder.reminderSubtitle(
                    reminderDemoState()
                ),
                onExport = {}
            )
            ReminderDemo()
        }
    }

    private fun reminderDemoState(): ReminderUiState {
        val ov = Demo.demoOverview(publicSample = true)
        val mom = ov.byId.getValue("mom")
        val gmd = ov.byId.getValue("gmd")
        val mil = ov.byId.getValue("mil")
        val today = LocalDate.of(2026, 10, 2)
        fun task(id: String, title: String, days: Int, label: String?, amt: Long?) =
            io.github.xiaohaoz1.jiarenxia.ui.reminder.TaskRow(
                task = io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask(
                    id = id, personId = null,
                    kind = io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.CUSTOM,
                    title = title,
                    anchorDate = today.plusDays(days.toLong()),
                    repeat = io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.YEARLY,
                    done = false, amountFen = amt, enabled = true, offsets = setOf(0, 3)
                ),
                dueDate = today.plusDays(days.toLong()),
                daysUntil = days, personLabel = label
            )
        return ReminderUiState(
            loading = false,
            today = listOf(
                BirthdayRow(mom.person, listOf(mom.birthday!!), mom.result.displayTitle, 0, true,
                    nextSolar = today)
            ),
            upcoming = listOf(
                BirthdayRow(gmd.person, listOf(gmd.birthday!!), gmd.result.displayTitle, 3, true,
                    nextSolar = LocalDate.of(2026, 10, 5)),
                BirthdayRow(mil.person, listOf(mil.birthday!!), mil.result.displayTitle, 18, true,
                    nextSolar = LocalDate.of(2026, 10, 20))
            ),
            defaultNumbers = mapOf("mom" to "13912345678"),
            // 演示事项：一条逾期 + 今天到期 + 未来 30 天
            tasksToday = listOf(
                task("t-od", "医保缴费", -2, "妈妈", 38000),
                task("t-td", "身份证到期换证", 0, null, null)
            ),
            tasksNext30 = listOf(
                task("t-n30", "养老金资格认证", 12, "爸爸", null)
            )
        )
    }

    @Composable
    private fun ReminderDemo() {
        ReminderContent(
            st = reminderDemoState(),
            notifEnabled = true,
            onOpenPerson = {}, onDial = {}, onNotifSettings = {}
        )
    }

    @Composable
    private fun PersonNewDemo() {
        PersonEditContent(
            st = PersonEditUiState(
                loaded = true, mode = PersonEditMode.NEW,
                anchorId = "dad", anchorName = "杨建国", anchorTitle = "父亲",
                roleOptions = listOf(
                    RelationRole.ELDER_BROTHER, RelationRole.YOUNGER_BROTHER,
                    RelationRole.ELDER_SISTER, RelationRole.YOUNGER_SISTER
                )
            ),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }

    @Composable
    private fun AddFamilyOnboardingDemo() {
        val emptySelf = Person(id = "me", name = "杨明", gender = Gender.MALE, isSelf = true)
        val emptyGraph = FamilyGraph(listOf(emptySelf), emptyList())
        val emptyOverview = FamilyOverview(
            self = PersonOverview(
                emptySelf, KinshipEngine().resolve(emptyGraph, "me", "me"), emptyList()
            ),
            graph = emptyGraph,
            byId = emptyMap(),
            core = emptyList(), others = emptyList()
        )
        AddFamilyContent(
            entries = buildQuickEntries(emptyOverview),
            onboarding = true, onEntry = {}, onHint = {}, onDone = {}, onBack = {}
        )
    }

    @Test
    fun fontFamily() {
        assume()
        for (fs in listOf(1.3f, 1.5f)) {
            fontShot("family_home", fs) {
                FamilyContent(Demo.familyState(), Demo.lunar, {}, {}, {}, {}, {})
            }
        }
    }

    @Test
    fun fontDetail() {
        assume()
        for (fs in listOf(1.3f, 1.5f)) {
            fontShot("detail_mom", fs) {
                PersonDetailContent(
                    lunar = Demo.lunar,
                    st = Demo.detailOf("mom"), today = Demo.today,
                    snackbar = SnackbarHostState(),
                    onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
                    onDial = {}, onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> }, onSetCustomTitle = {}, onDeletePerson = {},
                    onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
                )
            }
        }
    }

    @Test
    fun fontPersonNew() {
        assume()
        for (fs in listOf(1.3f, 1.5f)) {
            fontShot("person_new", fs) { PersonNewDemo() }
        }
    }

    @Test
    fun fontDualBirthdayDetail() {
        assume()
        for (fs in listOf(1.3f, 1.5f)) {
            fontShot("detail_dual_birthday", fs) {
                PersonDetailContent(
                    lunar = Demo.lunar,
                    st = Demo.dualBirthdayDetail(), today = Demo.today,
                    snackbar = SnackbarHostState(),
                    onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
                    onDial = {}, onSetReminder = { _, _ -> },
                    onSetReminderOffsets = { _, _ -> }, onSetCustomTitle = {},
                    onDeletePerson = {}, onSetSiblingOrder = { _, _, _ -> },
                    onRemoveRelation = {}
                )
            }
        }
    }

    @Test
    fun fontDualBirthdayEdit() {
        assume()
        for (fs in listOf(1.3f, 1.5f)) {
            fontShot("person_edit_dual_birthday", fs) {
                PersonEditContent(
                    st = Demo.dualBirthdayEditState(),
                    snackbar = SnackbarHostState(),
                    onDone = {}, onBack = {}, cb = PersonEditCallbacks(),
                    lunar = Demo.lunar
                )
            }
        }
    }

    @Test
    fun fontOnboardFamily() {
        assume()
        for (fs in listOf(1.3f, 1.5f)) {
            fontShot("onboard_family", fs) { AddFamilyOnboardingDemo() }
        }
    }

    @Test
    fun fontReminder() {
        assume()
        for (fs in listOf(1.3f, 1.5f)) {
            fontShot("reminder", fs) { ReminderDemo() }
        }
    }

    @Test
    fun fontMine() {
        assume()
        for (fs in listOf(1.3f, 1.5f)) {
            fontShot("mine", fs) {
                MineContent(self = Demo.demoOverview().self.person)
            }
        }
    }

    @Test
    fun fontGraph() {
        assume()
        for (fs in listOf(1.3f, 1.5f)) {
            fontShot("graph", fs) {
                val ov = Demo.demoOverview()
                val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
                GraphContent(
                    model = GraphLayout.build(
                        ov, gens, sizer = GraphTextMeasure.sizer),
                    byId = ov.byId, selfId = ov.self.person.id,
                    onBack = {}, onOpenPerson = {},
                    connectedTotal = gens.size
                )
            }
        }
    }

    /** 碰一碰角色选择页：大字号下两张角色卡与备用入口仍可达。 */

}
