package io.github.xiaohaoz1.jiarenxia.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneNumber
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.core.model.ReminderPreference
import io.github.xiaohaoz1.jiarenxia.data.PersonDetail
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOrdering
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.reminder.BirthdayRow
import io.github.xiaohaoz1.jiarenxia.ui.addfamily.AddFamilyContent
import io.github.xiaohaoz1.jiarenxia.ui.addfamily.buildQuickEntries
import io.github.xiaohaoz1.jiarenxia.ui.family.FamilyContent
import io.github.xiaohaoz1.jiarenxia.ui.family.FamilyUiState
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranch
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranches
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphContent
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphLayout
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphTextMeasure
import io.github.xiaohaoz1.jiarenxia.ui.launch.LaunchContent
import io.github.xiaohaoz1.jiarenxia.ui.mine.MineContent
import io.github.xiaohaoz1.jiarenxia.ui.mine.ModulesContent
import io.github.xiaohaoz1.jiarenxia.ui.mine.PrivacyContent
import io.github.xiaohaoz1.jiarenxia.ui.person.AddRelationContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.ui.person.CustomTitleDialog
import io.github.xiaohaoz1.jiarenxia.ui.person.CustomTitleDialogBody
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonDetailContent
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonDetailUiState
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonEditCallbacks
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonEditContent
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonEditField
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonEditMode
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonEditUiState
import io.github.xiaohaoz1.jiarenxia.ui.person.PhoneRowState
import io.github.xiaohaoz1.jiarenxia.ui.person.RelationRow
import io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderContent
import io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderUiState
import io.github.xiaohaoz1.jiarenxia.ui.search.SearchContent
import io.github.xiaohaoz1.jiarenxia.ui.theme.JiaRenXiaTheme
import java.io.File
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi 截图：用真实 KinshipEngine + GraphLayout 构造演示数据，
 * 以固定日期 2026-10-02（周五）19:00 渲染所有页面，输出到 build/outputs/roborazzi/。
 * 长页用更高的限定符截图（方法级 @Config 覆盖）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenShotsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            JiaRenXiaTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                // 截图脱离 MainShell：自带底色与内容色，避免默认黑字
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                ) { content() }
            }
        }
        composeRule.onRoot().captureRoboImage(
            File("build/outputs/roborazzi/b_$name.png").absolutePath
        )
    }

    /**
     * 大字号（×1.3，约等于应用内“特大”级别）截图：用 LocalDensity 覆盖模拟
     * 应用内字号系数（同 MainActivity 的 density 覆盖方式），文件名加 _lg。
     */
    private fun shotLarge(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(d.density, d.fontScale * 1.3f)
            ) {
                JiaRenXiaTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                    ) { content() }
                }
            }
        }
        composeRule.onRoot().captureRoboImage(
            File("build/outputs/roborazzi/b_${name}_lg.png").absolutePath
        )
    }

    /** 截图后断言当前层次里存在指定文案（自定义称呼覆盖的回归防线）。 */
    private fun assertTexts(vararg texts: String) {
        // fetchSemanticsNode 在节点缺失时直接抛错 → 相当于断言存在
        texts.forEach {
            // 至少有 1 个节点包含该子串（允许重复出现，如“今天”标题与徽章）
            check(
                composeRule.onAllNodes(hasText(it, substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            ) { "assertTexts: 未找到包含文本的节点: " + it }
        }
    }

    /** 断言存在带指定 contentDescription 的节点（无障碍描述回归防线）。 */
    private fun assertDescs(vararg descs: String) {
        descs.forEach {
            check(
                composeRule.onAllNodes(
                    androidx.compose.ui.test.hasContentDescription(it)
                ).fetchSemanticsNodes().isNotEmpty()
            ) { "assertDescs: 未找到 contentDescription: " }
        }
    }

    /** 断言不存在包含指定文案的节点（不应显示内容的回归防线）。 */
    private fun assertNoTexts(vararg texts: String) {
        texts.forEach {
            check(
                composeRule.onAllNodes(hasText(it, substring = true))
                    .fetchSemanticsNodes().isEmpty()
            ) { "assertNoTexts: 不应出现文本: " + it }
        }
    }

    // ---------- 截图（演示数据在 Demo.kt）----------

    // ---------- 截图 ----------

    @Test
    fun launch() = shot("launch") { LaunchContent() }

    @Test
    @Config(qualifiers = "w412dp-h1400dp-xxhdpi")
    fun onboardSelf() = shot("onboard_self") {
        PersonEditContent(
            st = PersonEditUiState(loaded = true, mode = PersonEditMode.SELF),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h1400dp-xxhdpi")
    fun onboardFamily() = shot("onboard_family") {
        // 空家庭 → 由真实构建器产出入口
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
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun family() = shot("family_home") {
        FamilyContent(Demo.familyState(), Demo.lunar, {}, {}, {}, {}, {})
    }.also {
        // 自定义称呼覆盖：伯父 杨建春 → “大伯”
        assertTexts("大伯", "核心家人", "家庭关系图")
        // 标题下文字入口（不再是图标按钮）：查找家人、添加家人均可点击
        composeRule.onNodeWithText("查找家人").assertHasClickAction()
        composeRule.onNodeWithText("添加家人").assertHasClickAction()
    }

    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun familyBirthdayToday() = shot("family_birthday_today") {
        FamilyContent(Demo.familyState(), Demo.lunar, {}, {}, {}, {}, {})
    }.also {
        assertTexts("今天是", "打个电话")
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun detailMom() = shot("detail_mom") {
        PersonDetailContent(
            lunar = Demo.lunar,
            st = Demo.detailOf("mom"), today = Demo.today, snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onDial = {}, onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> }, onSetCustomTitle = {}, onDeletePerson = {},
            onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
        )
    }.also {
        assertTexts("母亲", "1964年10月2日")
        assertDescs("给王秀兰打电话", "更多操作：王秀兰")
        // 在世的人不标“状态”
        assertNoTexts("在世")
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun detailSelfAnniversary() = shot("detail_self_anniversary") {
        // 夫妻共享纪念日只属于“我”：“我”有配偶时重要日期卡显示
        // 三种纪念日入口与已有记录；年数由页面从夹具数据推出
        val base = Demo.detailOf("me")
        val wedding = io.github.xiaohaoz1.jiarenxia.data.DateEntry(
            type = io.github.xiaohaoz1.jiarenxia.core.model
                .ImportantDateType.WEDDING_ANNIVERSARY,
            person = base.detail!!.person,
            date = io.github.xiaohaoz1.jiarenxia.core.model
                .BirthdayValue(
                    io.github.xiaohaoz1.jiarenxia.core.model
                        .PartialDate(2016, 10, 18)),
            reminderEnabled = true,
            reminderOffsets = setOf(1, 0),
            partnerId = "wife", partnerName = "李雪"
        )
        val ageText = io.github.xiaohaoz1.jiarenxia.domain.AgeMath
            .ageText(
                listOfNotNull(base.detail.birthday), base.detail.person.livingStatus,
                null, Demo.today, Demo.lunar)
        PersonDetailContent(
            lunar = Demo.lunar,
            st = base.copy(ageText = ageText,
                coupleDates = mapOf(
                    io.github.xiaohaoz1.jiarenxia.core.model
                        .ImportantDateType.WEDDING_ANNIVERSARY to wedding)),
            today = Demo.today, snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onDial = {}, onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> },
            onSetCustomTitle = {}, onDeletePerson = {},
            onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
        )
    }.also {
        assertTexts("重要日期", "结婚纪念日（与李雪）", "36 岁",
            "添加恋爱纪念日", "添加领证纪念日")
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun detailDadNoAnniversary() = shot("detail_dad_no_anniversary") {
        // 其他人：即使库里有这条纪念日，详情页也不显示这组日期
        val base = Demo.detailOf("dad")
        val wedding = io.github.xiaohaoz1.jiarenxia.data.DateEntry(
            type = io.github.xiaohaoz1.jiarenxia.core.model
                .ImportantDateType.WEDDING_ANNIVERSARY,
            person = base.detail!!.person,
            date = io.github.xiaohaoz1.jiarenxia.core.model
                .BirthdayValue(
                    io.github.xiaohaoz1.jiarenxia.core.model
                        .PartialDate(1986, 10, 18)),
            reminderEnabled = true,
            reminderOffsets = setOf(1, 0),
            partnerId = "mom", partnerName = "王秀兰"
        )
        PersonDetailContent(
            lunar = Demo.lunar,
            st = base.copy(
                coupleDates = mapOf(
                    io.github.xiaohaoz1.jiarenxia.core.model
                        .ImportantDateType.WEDDING_ANNIVERSARY to wedding)),
            today = Demo.today, snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onDial = {}, onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> },
            onSetCustomTitle = {}, onDeletePerson = {},
            onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
        )
    }.also {
        assertNoTexts("结婚纪念日", "添加恋爱纪念日", "添加领证纪念日", "在世")
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun detailGrandpaDeath() = shot("detail_grandpa_death") {
        // 已故：享年 + 忌日行；享年由 AgeMath 从夹具的生卒日期实算
        val base = Demo.detailOf("gfd")
        val birth = io.github.xiaohaoz1.jiarenxia.core.model
            .BirthdayValue(
                io.github.xiaohaoz1.jiarenxia.core.model
                    .PartialDate(1940, 3, 1))
        val death = io.github.xiaohaoz1.jiarenxia.data.DateEntry(
            type = io.github.xiaohaoz1.jiarenxia.core.model
                .ImportantDateType.DEATH_ANNIVERSARY,
            person = base.detail!!.person,
            date = io.github.xiaohaoz1.jiarenxia.core.model
                .BirthdayValue(
                    io.github.xiaohaoz1.jiarenxia.core.model
                        .PartialDate(2016, 8, 25),
                    io.github.xiaohaoz1.jiarenxia.core.model
                        .CalendarType.LUNAR),
            reminderEnabled = true,
            reminderOffsets = setOf(3, 0)
        )
        val ageText = io.github.xiaohaoz1.jiarenxia.domain.AgeMath
            .ageText(
                listOf(birth), base.detail.person.livingStatus,
                death.date, Demo.today, Demo.lunar)
        PersonDetailContent(
            lunar = Demo.lunar,
            st = base.copy(
                detail = base.detail.copy(birthdays = mapOf(
                    io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.SOLAR to
                        io.github.xiaohaoz1.jiarenxia.data.BirthdayDetail("d-b", birth, null)
                )),
                ageText = ageText,
                deathDate = death
            ),
            today = Demo.today, snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onDial = {}, onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> },
            onSetCustomTitle = {}, onDeletePerson = {},
            onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
        )
    }.also {
        // 1940-03-01 ~ 2016年农历八月廿五（公历约 9 月末）→ 享年 76 岁
        assertTexts("重要日期", "忌日", "享年 76 岁")
        // 已故才标“状态”行
        assertTexts("状态", "已故")
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun detailUncle() = shot("detail_uncle") {
        PersonDetailContent(
            lunar = Demo.lunar,
            st = Demo.detailOf("uncle"), today = Demo.today, snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onDial = {}, onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> }, onSetCustomTitle = {}, onDeletePerson = {},
            onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
        )
    }.also {
        assertTexts("大伯", "标准称谓：伯父")
        // 大伯有配偶（周丽）：纪念日组只对“我”显示，这里不出现
        assertNoTexts("添加结婚纪念日")
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun detailLunar() = shot("detail_Demo.lunar") {
        // 奶奶：农历八月十五（未填年份）→ “农历 八月十五（未填年份）”+ 公历提示
        PersonDetailContent(
            lunar = Demo.lunar,
            st = Demo.detailOf("gmd"), today = Demo.today, snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onDial = {}, onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> }, onSetCustomTitle = {}, onDeletePerson = {},
            onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun personNew() = shot("person_new") {
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
    }.also {
        // 姓名在关系选择之前（父亲试用：先写姓名再选关系）
        assertTexts("基本信息", "姓名", "关联", "TA 是杨建国的")
    }

    /** 保存被拒后的新增表单状态：姓名/性别/称谓三处缺项。 */
    private fun personNewErrorState() = PersonEditUiState(
        loaded = true, mode = PersonEditMode.NEW,
        saveAttempted = true,
        fieldErrors = setOf(
            PersonEditField.NAME, PersonEditField.GENDER, PersonEditField.ROLE
        ),
        anchorId = "dad", anchorName = "杨建国", anchorTitle = "父亲",
        roleOptions = listOf(
            RelationRole.ELDER_BROTHER, RelationRole.YOUNGER_BROTHER,
            RelationRole.ELDER_SISTER, RelationRole.YOUNGER_SISTER
        )
    )

    @Composable
    private fun PersonNewErrorContent() {
        PersonEditContent(
            st = personNewErrorState(),
            snackbar = SnackbarHostState(),
            missingText = "还差：姓名、性别、称谓",
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun personNewErrors() = shot("person_new_errors") {
        PersonNewErrorContent()
    }.also {
        // 缺项就地提示：每个错误字段旁边都有文字提示，不只改颜色
        assertTexts("请填写姓名", "请选择性别", "请选择关系", "还差：姓名、性别、称谓")
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun personNewErrorsDark() = shot("person_new_errors_dark", dark = true) {
        PersonNewErrorContent()
    }.also {
        assertTexts("请填写姓名", "请选择性别", "请选择关系")
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun personNewErrorsLarge() = shotLarge("person_new_errors") {
        PersonNewErrorContent()
    }.also {
        assertTexts("请填写姓名", "请选择性别", "请选择关系")
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun personEditLunar() = shot("person_edit_Demo.lunar") {
        // 编辑模式 + 农历生日（八月十五，未知年份）
        PersonEditContent(
            st = PersonEditUiState(
                loaded = true, mode = PersonEditMode.EDIT,
                name = "李桂英", gender = io.github.xiaohaoz1.jiarenxia.core.model.Gender.FEMALE,
                lunarBirthday = io.github.xiaohaoz1.jiarenxia.ui.person
                    .BirthdayFormState(on = true, yearUnknown = true, month = 8, day = 15),
                anchorTitle = "奶奶", isSelf = false
            ),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks(),
            lunar = Demo.lunar
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun detailDualBirthday() = shot("detail_dual_birthday") {
        // 妈妈同时记了阳历与农历生日：两槽独立展示、独立提醒开关
        PersonDetailContent(
            lunar = Demo.lunar,
            st = Demo.dualBirthdayDetail(), today = Demo.today,
            snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onDial = {}, onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> },
            onSetCustomTitle = {}, onDeletePerson = {},
            onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
        )
    }.also {
        assertTexts("阳历生日", "农历")
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun personEditDualBirthday() = shot("person_edit_dual_birthday") {
        // 编辑页两个生日槽位均已填写（换算一致）
        PersonEditContent(
            st = Demo.dualBirthdayEditState(),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks(),
            lunar = Demo.lunar
        )
    }.also {
        assertTexts("阳历生日", "农历生日")
    }

    /** 年份选择第一步：年代网格（新的在前），替代原来的百项长下拉。 */
    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun personEditYearDecade() = shot("person_edit_year_decade") {
        Column(Modifier.padding(24.dp)) {
            Text("选择年代", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            io.github.xiaohaoz1.jiarenxia.ui.person.YearPickerContent(
                decade = null, selectedYear = null,
                from = 1900, to = 2026,
                onDecade = {}, onYear = {}
            )
        }
    }.also {
        assertTexts("2020年代", "1960年代", "1900年代")
    }

    /** 年份选择第二步：该年代的年份网格（已选高亮，可返回换年代）。 */
    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun personEditYearPick() = shot("person_edit_year_pick") {
        Column(Modifier.padding(24.dp)) {
            Text("选择年份 · 1960年代", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            io.github.xiaohaoz1.jiarenxia.ui.person.YearPickerContent(
                decade = 1960, selectedYear = 1965,
                from = 1900, to = 2026,
                onDecade = {}, onYear = {}
            )
        }
    }.also {
        assertTexts("换个年代", "1969", "1965", "1960")
    }

    // ---------- 阶段A·电话录入改版场景（标准 + 大字号各一张） ----------

    private fun phoneEditState(phones: List<PhoneRowState>) = PersonEditUiState(
        loaded = true, mode = PersonEditMode.EDIT,
        name = "杨建国", gender = Gender.MALE,
        anchorTitle = "父亲", isSelf = false,
        phones = phones
    )

    /** 场景 1a：手动添加一条空电话行——行级操作与卡级两个入口都可见。 */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun a1PhoneBlankRow() = shot("f1_phone_blank_row") {
        PersonEditContent(
            st = phoneEditState(listOf(PhoneRowState(rowKey = "new:0"))),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }.also {
        assertTexts("电话（可稍后补充）", "手动填写", "从联系人选择",
            "删除这条电话", "更多号码设置")
    }

    /** 场景 1a·大字号。 */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun a1PhoneBlankRowLarge() = shotLarge("f1_phone_blank_row") {
        PersonEditContent(
            st = phoneEditState(listOf(PhoneRowState(rowKey = "new:0"))),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }

    /** 场景 1b：空行被联系人选择填入（复用空行、不追加）。 */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun a1PhoneContactFilled() = shot("f1_phone_contact_filled") {
        PersonEditContent(
            st = phoneEditState(listOf(
                PhoneRowState(rowKey = "new:0", number = "138 0000 0002")
            )),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }

    /** 场景 1b·大字号。 */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun a1PhoneContactFilledLarge() = shotLarge("f1_phone_contact_filled") {
        PersonEditContent(
            st = phoneEditState(listOf(
                PhoneRowState(rowKey = "new:0", number = "138 0000 0002")
            )),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }

    /** 场景 2：同号码重复选择——已有行就地提示，不追加新行。 */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun a2PhoneDupNotice() = shot("f2_phone_dup_notice") {
        PersonEditContent(
            st = phoneEditState(listOf(
                PhoneRowState(
                    rowKey = "new:0", number = "13800000001",
                    notice = "这个号码已经添加了")
            )),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }.also { assertTexts("这个号码已经添加了") }

    /** 场景 2·大字号。 */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun a2PhoneDupNoticeLarge() = shotLarge("f2_phone_dup_notice") {
        PersonEditContent(
            st = phoneEditState(listOf(
                PhoneRowState(
                    rowKey = "new:0", number = "13800000001",
                    notice = "这个号码已经添加了")
            )),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }

    /** 场景 3：清空已有号码——行内错误 + 该行为定位目标。 */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun a3PhoneClearedError() = shot("f3_phone_cleared_error") {
        PersonEditContent(
            st = phoneEditState(listOf(
                PhoneRowState(
                    rowKey = "db:ph1", id = "ph1", number = "",
                    error = "请填写号码，或删除这条电话"),
                PhoneRowState(rowKey = "db:ph2", id = "ph2",
                    number = "13911112222")
            )).copy(focusPhoneKey = "db:ph1"),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }.also { assertTexts("请填写号码，或删除这条电话") }

    /** 场景 3·大字号。 */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun a3PhoneClearedErrorLarge() = shotLarge("f3_phone_cleared_error") {
        PersonEditContent(
            st = phoneEditState(listOf(
                PhoneRowState(
                    rowKey = "db:ph1", id = "ph1", number = "",
                    error = "请填写号码，或删除这条电话"),
                PhoneRowState(rowKey = "db:ph2", id = "ph2",
                    number = "13911112222")
            )).copy(focusPhoneKey = "db:ph1"),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }

    /**
     * 场景 4：键盘弹起时的固定保存按钮——用短视口（等价于键盘顶起后剩余高度）
     * 验证“保存”不随表单滚走（实现为 imePadding + 固定 bottomBar）。
     */
    @Test
    @Config(qualifiers = "w412dp-h620dp-xxhdpi")
    fun a4SaveBarShortViewport() = shot("f4_save_bar_ime") {
        PersonEditContent(
            st = phoneEditState(listOf(
                PhoneRowState(rowKey = "new:0", number = "13800000001")
            )),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }.also {
        composeRule.onNodeWithText("保存").assertHasClickAction()
    }

    /** 场景 4·大字号。 */
    @Test
    @Config(qualifiers = "w412dp-h620dp-xxhdpi")
    fun a4SaveBarShortViewportLarge() = shotLarge("f4_save_bar_ime") {
        PersonEditContent(
            st = phoneEditState(listOf(
                PhoneRowState(rowKey = "new:0", number = "13800000001")
            )),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }

    /** 功能介绍与管理（A6 后）：只有基础功能介绍 + 守护/共享入口，没有人情/保障开关。 */
    @Test
    fun a6Modules() = shot("f5_modules") {
        ModulesContent(onBack = {}, onOpenShareConnect = {})
    }.also {
        assertTexts("功能介绍与管理", "记家人", "查关系", "记生日", "共享与连接")
        assertNoTexts("人情往来", "保障一览", "家事页的分段")
    }

    /** 功能介绍与管理·大字号。 */
    @Test
    fun a6ModulesLarge() = shotLarge("f5_modules") {
        ModulesContent(onBack = {}, onOpenShareConnect = {})
    }

    /** A6 后人物详情不再有“记人情和保单”推广入口与人情/保障摘要行。 */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun a6DetailNoDiscovery() = shot("f5_detail_no_discovery") {
        PersonDetailContent(
            lunar = Demo.lunar,
            st = Demo.detailOf("mom"),
            today = Demo.today, snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onDial = {}, onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> },
            onSetCustomTitle = {}, onDeletePerson = {},
            onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
        )
    }.also {
        assertNoTexts("还可以记人情和保单", "人情往来", "保障：")
    }

    @Test
    fun addRelation() = shot("add_relation") {
        val ov = Demo.demoOverview()
        AddRelationContent(
            candidates = ov.byId.values.filter { it.person.id != "me" }.toList(),
            otherId = "bro", otherName = "杨帆", thisName = "杨明",
            role = RelationRole.ELDER_BROTHER,
            roleOptions = listOf(RelationRole.ELDER_BROTHER, RelationRole.YOUNGER_BROTHER),
            saving = false, canSave = true,
            lineageState = io.github.xiaohaoz1.jiarenxia.ui.person.LineageUiState(
                lineage = io.github.xiaohaoz1.jiarenxia.domain.SiblingLineage.REAL,
                sharedParents = io.github.xiaohaoz1.jiarenxia.domain.SharedParents.BOTH,
                anchorName = "杨明",
                fatherCandidates = listOf(ov.byId.getValue("dad").person),
                motherCandidates = listOf(ov.byId.getValue("mom").person),
                pickedFatherId = "dad", pickedMotherId = "mom",
                fatherUnsure = false, motherUnsure = false,
                linkFather = true, linkMother = true,
                middleCandidates = emptyList(),
                middleId = null, middleNone = false,
                titleOnlyText = "哥哥",
                subjectGender = Gender.MALE
            ),
            lineageCb = io.github.xiaohaoz1.jiarenxia.ui.person.LineageCallbacks(),
            anchorAsParent = null,
            onBack = {}, onPick = {}, onRole = {}, onSave = {}
        )
    }

    /** 姑姑录入：关联人=父亲，兄妹称谓显示“姑姑”；亲/堂表/只记称呼三级细分。 */
    @Test
    @Config(qualifiers = "w412dp-h1600dp-xxhdpi")
    fun personNewAunt() = shot("person_new_aunt") {
        PersonEditContent(
            st = PersonEditUiState(
                loaded = true, mode = PersonEditMode.NEW,
                anchorId = "dad", anchorName = "杨建国", anchorTitle = "父亲",
                anchorAsParent = Gender.MALE, selfName = "杨明",
                roleOptions = listOf(
                    RelationRole.ELDER_SISTER, RelationRole.YOUNGER_SISTER,
                    RelationRole.SISTER
                ),
                role = RelationRole.SISTER,
                gender = Gender.FEMALE,
                // 已确认状态：亲兄弟姐妹 + 同父同母 + 勾选补建爷爷
                lineage = io.github.xiaohaoz1.jiarenxia.domain.SiblingLineage.REAL,
                sharedParents = io.github.xiaohaoz1.jiarenxia.domain.SharedParents.BOTH,
                fatherCandidates = listOf(
                    Person(id = "gfd", name = "杨福生", gender = Gender.MALE)),
                pickedFatherId = "gfd", linkFather = true,
                titleOnlyText = "姑姑"
            ),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }.also {
        assertTexts("姑姑（长幼未知）", "亲兄弟姐妹", "堂 / 表兄弟姐妹",
            "保存并继续添加", "TA 是杨明的：")
    }

    /** 姑姑录入初态：细分与共同父母均未预选（称呼不推断血缘），须用户显式选。 */
    @Test
    @Config(qualifiers = "w412dp-h1600dp-xxhdpi")
    fun personNewAuntUnconfirmed() = shot("person_new_aunt_unconfirmed") {
        PersonEditContent(
            st = PersonEditUiState(
                loaded = true, mode = PersonEditMode.NEW,
                anchorId = "dad", anchorName = "杨建国", anchorTitle = "父亲",
                anchorAsParent = Gender.MALE, selfName = "杨明",
                roleOptions = listOf(
                    RelationRole.ELDER_SISTER, RelationRole.YOUNGER_SISTER,
                    RelationRole.SISTER
                ),
                role = RelationRole.SISTER,
                gender = Gender.FEMALE,
                fatherCandidates = listOf(
                    Person(id = "gfd", name = "杨福生", gender = Gender.MALE)),
                // lineage/sharedParents 均为 null，唯一祖辈候选也不默认勾选
                pickedFatherId = "gfd",
                titleOnlyText = "姑姑"
            ),
            snackbar = SnackbarHostState(),
            onDone = {}, onBack = {}, cb = PersonEditCallbacks()
        )
    }.also {
        assertTexts("姑姑（长幼未知）", "亲兄弟姐妹", "堂 / 表兄弟姐妹",
            "其他关系，只记称呼", "称谓本身不代表血缘关系", "TA 是杨明的：")
    }

    /** 只记称呼人物：详情页显示“关系待补充”。 */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun detailTitleOnly() = shot("detail_title_only") {
        val base = Demo.detailOf("other")
        PersonDetailContent(
            lunar = Demo.lunar,
            st = base.copy(
                overview = base.overview!!.copy(customTitle = "赵老师")
            ),
            today = Demo.today, snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onDial = {}, onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> },
            onSetCustomTitle = {}, onDeletePerson = {},
            onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
        )
    }.also {
        assertTexts("关系待补充")
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun customTitleDialog() = shot("custom_title_dialog") {
        // 详情页自定义称呼对话框（AlertDialog 弹层在测试中不进入待机，
        // 这里直接渲染同款卡片内容：标题 + 输入正文 + 按钮行）
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color(0x66000000)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                modifier = Modifier.padding(28.dp).fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("自定义称呼", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    CustomTitleDialogBody("杨建春", "大伯") {}
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {}) {
                            Text("恢复默认", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = {}) { Text("取消") }
                        Button(onClick = {}) { Text("保存") }
                    }
                }
            }
        }
    }

    @Test
    fun search() = shot("search") {
        // 拼音查询："bf" 命中称谓“伯父”（杨建春）
        val ov = Demo.demoOverview()
        SearchContent(
            query = "bf",
            results = io.github.xiaohaoz1.jiarenxia.domain.PersonSearch.filter(
                ov.byId.values.toList(), "bf"),
            onQuery = {}, onBack = {}, onOpenPerson = {}
        )
    }.also {
        assertTexts("杨建春")
    }

    @Test
    fun graph() = shot("graph") {
        val ov = Demo.demoOverview()
        val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
        GraphContent(
            model = GraphLayout.build(ov, gens, sizer = GraphTextMeasure.sizer),
            byId = ov.byId, selfId = ov.self.person.id,
            onBack = {}, onOpenPerson = {},
            connectedTotal = gens.size
        )
    }.also {
        assertTexts("大伯", "身边的家人", "回到我", "展开家人", "查看总览")
    }

    /** 范围行 + 聚焦高亮节点（wife 聚焦，分支筛选已收进“更多→查看范围”）。 */
    @Test
    fun graphBranches() = shot("graph_branches") {
        val ov = Demo.demoOverview()
        val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
        GraphContent(
            model = GraphLayout.build(ov, gens, sizer = GraphTextMeasure.sizer)
                .copy(unconnectedCount = ov.graph.persons.count { it.id !in gens.keys }),
            byId = ov.byId, selfId = ov.self.person.id,
            onBack = {}, onOpenPerson = {},
            branch = GraphBranch.ALL,
            availableBranches = GraphBranches.available(ov),
            focusedId = "wife",
            connectedTotal = gens.size,
            onSelectBranch = {}, onLocate = {}
        )
    }.also {
        // 画布上方不再有整排分支 FilterChip；顶部保留查找/更多，底栏三按钮
        assertTexts("查找", "已定位 · ", "回到我", "展开家人", "查看总览")
        assertDescs("更多")
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-xxhdpi")
    fun graphLandscape() = shot("graph_landscape") {
        val ov = Demo.demoOverview()
        val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
        GraphContent(
            model = GraphLayout.build(ov, gens, sizer = GraphTextMeasure.sizer),
            byId = ov.byId, selfId = ov.self.person.id,
            onBack = {}, onOpenPerson = {},
            landscape = true, onToggleLandscape = {},
            connectedTotal = gens.size
        )
    }.also {
        // 横屏全屏：无标题栏、信息行与操作按钮（缩放回中全靠手势），
        // 只剩画布上缘一条悬浮条（返回+查找/添加/菜单），总览收进菜单
        assertNoTexts("家庭关系图", "查看总览", "回到我", "展开家人")
        assertDescs("返回", "查找", "添加家人", "更多")
    }

    // ---------- 阶段B：图谱可见层（收起/展开/全展开/双方视角） ----------

    /** 与 GraphViewModel 同一口径：可见层结果 → 布局模型。 */
    private fun graphModel(
        ov: FamilyOverview,
        expanded: Set<io.github.xiaohaoz1.jiarenxia.ui.graph
            .GraphVisibility.Expand>,
        expandAll: Boolean = false
    ): io.github.xiaohaoz1.jiarenxia.ui.graph.GraphModel {
        val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
        val vis = if (expandAll) {
            io.github.xiaohaoz1.jiarenxia.ui.graph.GraphVisibility.Result(
                gens.keys.toSet(), emptyList(), emptyList(), 0)
        } else {
            io.github.xiaohaoz1.jiarenxia.ui.graph.GraphVisibility.compute(
                ov, gens, expanded)
        }
        return GraphLayout.build(
            ov, gens, visibleIds = vis.visibleIds,
            collapsedCount = vis.collapsedCount, expandChips = vis.chips,
            // 与运行期同一份真实字体测量，渲染校验与真机一致
            sizer = GraphTextMeasure.sizer)
            .copy(unconnectedCount =
                ov.graph.persons.count { it.id !in gens.keys })
    }

    /** 默认核心视图：锚点夫妻 + 双方父母 + 直接子女，旁支收起带提示。 */
    @Test
    fun graphCollapsed() = shot("graph_collapsed") {
        val ov = Demo.demoOverview()
        GraphContent(
            model = graphModel(ov, emptySet()),
            byId = ov.byId, selfId = ov.self.person.id,
            onBack = {}, onOpenPerson = {},
            availableBranches = GraphBranches.available(ov),
            connectedTotal =
                KinshipEngine().generations(ov.graph, ov.self.person.id).size
        )
    }.also {
        assertTexts("身边的家人", "回到我", "展开家人", "查看总览")
    }

    /** 大字号下的默认核心视图（卡片与通道随字号放大）。 */
    @Test
    fun graphCollapsedLarge() = shotLarge("graph_collapsed") {
        val ov = Demo.demoOverview()
        GraphContent(
            model = graphModel(ov, emptySet()),
            byId = ov.byId, selfId = ov.self.person.id,
            onBack = {}, onOpenPerson = {},
            availableBranches = GraphBranches.available(ov),
            connectedTotal =
                KinshipEngine().generations(ov.graph, ov.self.person.id).size
        )
    }.also {
        assertTexts("身边的家人")
    }

    /** 展开一层：dad 全方向揭示祖辈一家与伯叔姑旁支。 */
    @Test
    fun graphExpandStep() = shot("graph_expand_step") {
        val ov = Demo.demoOverview()
        GraphContent(
            model = graphModel(ov, setOf(
                io.github.xiaohaoz1.jiarenxia.ui.graph.GraphVisibility
                    .Expand("dad",
                        io.github.xiaohaoz1.jiarenxia.ui.graph
                            .GraphVisibility.Dir.ALL))),
            byId = ov.byId, selfId = ov.self.person.id,
            onBack = {}, onOpenPerson = {},
            hasExpanded = true,
            availableBranches = GraphBranches.available(ov),
            connectedTotal =
                KinshipEngine().generations(ov.graph, ov.self.person.id).size
        )
    }.also {
        assertTexts("展开家人", "查看总览")
    }

    /** 全展开：演示家庭全部可达成员，同代多行与家庭 block 一并呈现。 */
    @Test
    fun graphExpandAll() = shot("graph_expand_all") {
        val ov = Demo.demoOverview()
        GraphContent(
            model = graphModel(ov, emptySet(), expandAll = true),
            byId = ov.byId, selfId = ov.self.person.id,
            onBack = {}, onOpenPerson = {},
            hasExpanded = true,
            availableBranches = GraphBranches.available(ov),
            connectedTotal =
                KinshipEngine().generations(ov.graph, ov.self.person.id).size
        )
    }.also {
        assertTexts("查看总览")
    }

    /** 原生家庭分组：父系姑姑、母系姨舅与表姐妹的女儿，全部使用合成资料。 */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
    fun graphFamilyGroups() {
        val ov = io.github.xiaohaoz1.jiarenxia.ui.graph.GraphFamilyGroupingTest.overview()
        shot("graph_family_groups_reading") {
            GraphContent(
                model = graphModel(ov, emptySet(), expandAll = true),
                byId = ov.byId, selfId = ov.self.person.id,
                onBack = {}, onOpenPerson = {}, connectedTotal = 18,
                hasExpanded = true)
        }
        composeRule.onNodeWithText("查看总览").performClick()
        composeRule.waitForIdle()
        capture("graph_family_groups_overview")
    }

    /** 同一多家庭结构的大字号总览，验证旁支子女仍跟随自己的父母。 */
    @Test
    fun graphFamilyGroupsLarge() {
        val ov = io.github.xiaohaoz1.jiarenxia.ui.graph.GraphFamilyGroupingTest.overview()
        shotLarge("graph_family_groups") {
            GraphContent(
                model = graphModel(ov, emptySet(), expandAll = true),
                byId = ov.byId, selfId = ov.self.person.id,
                onBack = {}, onOpenPerson = {}, connectedTotal = 18)
        }
        composeRule.onNodeWithText("查看总览").performClick()
        composeRule.waitForIdle()
        capture("graph_family_groups_overview_lg")
    }

    /** 原始亲子方向与辈分冲突时不倒画，保留显式核对入口及人物导航。 */
    @Test
    fun graphParentConflict() {
        val ov = io.github.xiaohaoz1.jiarenxia.ui.graph.GraphFamilyGroupingTest.overview(reversedParent = true)
        var opened: String? = null
        shot("graph_parent_conflict") {
            GraphContent(
                model = graphModel(ov, emptySet(), expandAll = true),
                byId = ov.byId, selfId = ov.self.person.id,
                onBack = {}, onOpenPerson = { opened = it }, connectedTotal = 18)
        }
        composeRule.onNodeWithText("有 1 条亲子关系待核对 · 查看").performClick()
        composeRule.waitForIdle()
        capture("graph_parent_conflict_dialog")
        composeRule.onNodeWithText("许思宁的子女：许文成\n查看许思宁的关系").performClick()
        composeRule.runOnIdle { org.junit.Assert.assertEquals("paternalCousin", opened) }
    }

    /** 长幼三排仍属于同一辈，晚辈必须在整个同辈区域下方。 */
    @Test
    fun graphSeniorityRows() {
        val ov = io.github.xiaohaoz1.jiarenxia.ui.graph.GraphFamilyGroupingTest.overview(ageGroups = true)
        shot("graph_seniority_rows") {
            GraphContent(model = graphModel(ov, emptySet(), expandAll = true),
                byId = ov.byId, selfId = ov.self.person.id,
                onBack = {}, onOpenPerson = {}, connectedTotal = ov.graph.persons.size)
        }
        composeRule.onNodeWithText("查看总览").performClick()
        composeRule.waitForIdle()
        capture("graph_seniority_rows_overview")
    }

    @Test
    fun graphSeniorityRowsLarge() {
        val ov = io.github.xiaohaoz1.jiarenxia.ui.graph.GraphFamilyGroupingTest.overview(ageGroups = true)
        shotLarge("graph_seniority_rows") {
            GraphContent(model = graphModel(ov, emptySet(), expandAll = true),
                byId = ov.byId, selfId = ov.self.person.id,
                onBack = {}, onOpenPerson = {}, connectedTotal = ov.graph.persons.size)
        }
        composeRule.onNodeWithText("查看总览").performClick()
        composeRule.waitForIdle()
        capture("graph_seniority_rows_overview_lg")
    }

    /** 长称谓场景：堂哥挂 9 字长称谓，验证折行完整、卡片随实高增高。 */
    @Test
    fun graphLongTitle() = shot("graph_long_title") {
        val base = Demo.demoOverview()
        val byId = base.byId.mapValues { (id, po) ->
            if (id == "cousin")
                po.copy(customTitle = "外曾祖父的弟弟的长子")
            else po
        }
        val ov = base.copy(byId = byId)
        GraphContent(
            model = graphModel(ov, emptySet(), expandAll = true),
            byId = ov.byId, selfId = ov.self.person.id,
            onBack = {}, onOpenPerson = {},
            hasExpanded = true,
            availableBranches = GraphBranches.available(ov),
            connectedTotal =
                KinshipEngine().generations(ov.graph, ov.self.person.id).size
        )
    }.also {
        assertTexts("外曾祖父的弟弟的长子")
    }

    /** 女方视角：self=李雪，男方（杨明）整族在左、她的家人在右。 */
    @Test
    fun graphFemaleSelf() = shot("graph_female_self") {
        val base = Demo.demoGraph()
        val graph = base.copy(persons = base.persons.map {
            it.copy(isSelf = it.id == "wife")
        })
        val engine = KinshipEngine()
        val results = engine.resolveAll(graph, "wife")
        val byId = graph.persons.associate { person ->
            person.id to PersonOverview(
                person, results.getValue(person.id),
                graph.birthdayValues[person.id] ?: emptyList(),
                solarBirthday = graph.birthdays[person.id],
                customTitle = Demo.customTitles[person.id]
            )
        }
        val ov = FamilyOverview(
            self = byId.getValue("wife"), graph = graph, byId = byId,
            core = emptyList(), others = emptyList())
        GraphContent(
            model = graphModel(ov, emptySet(), expandAll = true),
            byId = ov.byId, selfId = ov.self.person.id,
            onBack = {}, onOpenPerson = {},
            connectedTotal =
                KinshipEngine().generations(graph, "wife").size
        )
    }.also {
        assertTexts("家庭关系图")
    }

    /** 事项行样例：标题 / 距今天数（负=逾期）/ 关联人胶囊 / 金额分。 */
    private fun taskRow(
        id: String, title: String, daysFromToday: Int,
        personLabel: String?, amountFen: Long? = null,
        kind: io.github.xiaohaoz1.jiarenxia.core.model.TaskKind =
            io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.CUSTOM,
        done: Boolean = false
    ) = io.github.xiaohaoz1.jiarenxia.ui.reminder.TaskRow(
        task = io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask(
            id = id, personId = null, kind = kind, title = title,
            anchorDate = LocalDate.of(2026, 10, 2).plusDays(daysFromToday.toLong()),
            repeat = io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.YEARLY,
            done = done, amountFen = amountFen, enabled = true, offsets = setOf(0, 7)
        ),
        dueDate = LocalDate.of(2026, 10, 2).plusDays(daysFromToday.toLong()),
        daysUntil = daysFromToday,
        personLabel = personLabel
    )

    /** 同一人双生日在不同日期到期时，两条时间线必须同时可渲染。 */
    @Test
    fun reminderDualBirthdayDates() {
        val person = Demo.demoOverview().byId.getValue("gmd")
        val birthday = person.birthday!!
        shot("reminder_dual_birthday_dates") {
            ReminderContent(
                st = ReminderUiState(
                    loading = false,
                    upcoming = listOf(
                        BirthdayRow(person.person,
                            listOf(birthday.copy(calendar = io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.SOLAR)),
                            person.displayTitle, 3, true, LocalDate.of(2026, 10, 5)),
                        BirthdayRow(person.person,
                            listOf(birthday.copy(calendar = io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.LUNAR)),
                            person.displayTitle, 18, true, LocalDate.of(2026, 10, 20))
                    )
                ),
                notifEnabled = true,
                onOpenPerson = {}, onDial = {}, onNotifSettings = {},
                onOpenTask = {}, onMarkDone = {}
            )
        }
        check(composeRule.onAllNodes(hasText(person.person.name, substring = true))
            .fetchSemanticsNodes().size == 2) { "同一人的两个生日应各有一行" }
    }

    private fun reminderSampleContent(): @Composable () -> Unit = {
        val ov = Demo.demoOverview()
        val mom = ov.byId.getValue("mom")
        val gmd = ov.byId.getValue("gmd")
        val mil = ov.byId.getValue("mil")
        ReminderContent(
            st = ReminderUiState(
                loading = false,
                today = listOf(
                    BirthdayRow(mom.person, listOf(mom.birthday!!), mom.displayTitle, 0, true,
                        nextSolar = LocalDate.of(2026, 10, 2))
                ),
                upcoming = listOf(
                    BirthdayRow(gmd.person, listOf(gmd.birthday!!), gmd.displayTitle, 3, true,
                        nextSolar = LocalDate.of(2026, 10, 5)),
                    BirthdayRow(mil.person, listOf(mil.birthday!!), mil.displayTitle, 18, true,
                        nextSolar = LocalDate.of(2026, 10, 20))
                ),
                defaultNumbers = mapOf("mom" to "13912345678"),
                tasksToday = listOf(
                    taskRow("t-od", "爷爷的养老金资格认证", -2, "爷爷"),
                    taskRow("t-td", "妈妈的居民医保缴费", 0, "妈妈", amountFen = 38000,
                        kind = io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.MEDICAL_INSURANCE)
                ),
                tasksNext30 = listOf(
                    taskRow("t-n30", "爸爸的保险缴费", 9, "爸爸", amountFen = 120000)
                ),
                tasksLater = listOf(
                    taskRow("t-late", "家里宽带缴费", 40, null, amountFen = 12000)
                ),
                tasksDone = listOf(
                    taskRow("t-done", "我的体检", -30, "我", done = true)
                )
            ),
            notifEnabled = true,
            onOpenPerson = {}, onDial = {}, onNotifSettings = {},
            onOpenTask = {}, onMarkDone = {}
        )
    }

    @Test
    fun reminder() = shot("reminder", content = reminderSampleContent())
        .also {
            assertTexts("今天", "王秀兰", "未来 30 天（3）", "已逾期 2 天", "380 元")
            assertDescs("标记已办")
        }

    /** 家事页整屏（A6 后）：页头 + 铃铛/更多行 + 提醒时间线，不再有分段 tab。 */
    @Test
    fun affairsTimeline() = shot("affairs_timeline") {
        val sample = reminderSampleContent()
        Column(Modifier.fillMaxSize()) {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.AffairsHeader(
                subtitle = "未来 30 天有 2 个生日、3 件事",
                onExport = {}
            )
            Box(Modifier.weight(1f)) { sample() }
        }
    }.also {
        assertTexts("家事", "未来 30 天（3）", "王秀兰", "10/05")
        assertNoTexts("人情", "保障")
        assertDescs("提醒设置", "更多")
    }

    /** 家事页·深色。 */
    @Test
    fun affairsTimelineDark() = shot("affairs_timeline_dark", dark = true) {
        val sample = reminderSampleContent()
        Column(Modifier.fillMaxSize()) {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.AffairsHeader(
                subtitle = "未来 30 天有 2 个生日、3 件事",
                onExport = {}
            )
            Box(Modifier.weight(1f)) { sample() }
        }
    }

    /** 家事页·大字号 1.3。 */
    @Test
    fun affairsTimelineLarge() = shotLarge("affairs_timeline") {
        val sample = reminderSampleContent()
        Column(Modifier.fillMaxSize()) {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.AffairsHeader(
                subtitle = "未来 30 天有 2 个生日、3 件事",
                onExport = {}
            )
            Box(Modifier.weight(1f)) { sample() }
        }
    }

    /** 回归：今天只有纪念日时，「今天」分组仍要显示这条纪念日。 */
    @Test
    fun affairsTodayAnniversaryOnly() = shot("affairs_today_anniversary_only") {
        io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderUiState(
                loading = false,
                dateRowsToday = listOf(
                    io.github.xiaohaoz1.jiarenxia.domain.DateRow(
                        kind = io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
                            .WEDDING_ANNIVERSARY,
                        personId = "dad", label = "爸爸妈妈的结婚纪念日", daysUntil = 0,
                        occurrence = LocalDate.of(2026, 10, 9), yearLabel = "第 41 年"
                    )
                )
            ),
            notifEnabled = true,
            onOpenPerson = {}, onDial = {}, onNotifSettings = {}
        )
    }.also { assertTexts("今天", "爸爸妈妈的结婚纪念日") }

    // ---------- T05：家事页头 / 三段菜单 / 导出范围 ----------

    /** 渲染后不截图，交给调用方先做交互（如点开菜单）再 [capture]。 */
    private fun setShotContent(dark: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            JiaRenXiaTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    contentColor = androidx.compose.material3.MaterialTheme
                        .colorScheme.onBackground
                ) { content() }
            }
        }
    }

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage(
            File("build/outputs/roborazzi/b_$name.png").absolutePath
        )
    }

    /** 家事页头（A6 后无分段）：页头 + 铃铛/更多行；“更多”菜单展开仍是“导出表格”。 */
    @Test
    fun affairsMenuRemind() {
        setShotContent {
            Column {
                io.github.xiaohaoz1.jiarenxia.ui.reminder.AffairsHeader(
                    subtitle = "未来 30 天有 2 个生日、3 件事",
                    onExport = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.waitForIdle()
        capture("affairs_menu_remind")
        assertTexts("导出表格")
    }

    @Test
    fun affairsTop() = shot("affairs_top") {
        Column {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.AffairsHeader(
                subtitle = "未来 30 天有 2 个生日、3 件事",
                onExport = {}
            )
        }
    }.also {
        assertDescs("更多", "提醒设置")
        assertNoTexts("人情", "保障")
    }

    @Test
    fun reminderExportDialog() = shot("reminder_export_dialog") {
        // 导出范围对话框：默认未来 30 天 + 含逾期
        io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderExportDialog(
            onConfirm = { _, _, _ -> }, onDismiss = {}
        )
    }.also {
        assertTexts("导出表格", "未来 30 天", "未来 90 天", "自定义",
            "包含未完成逾期事项")
    }

    @Test
    fun reminderExportDialogCustom() = shot("reminder_export_custom") {
        // 自定义范围：显示起止日期行
        io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderExportDialogBody(
            preset = io.github.xiaohaoz1.jiarenxia.ui.reminder.ExportRangePreset.CUSTOM,
            onPreset = {},
            customFrom = java.time.LocalDate.of(2026, 10, 1),
            customTo = java.time.LocalDate.of(2026, 12, 31),
            includeOverdue = true, onToggleOverdue = {},
            onPickFrom = {}, onPickTo = {}
        )
    }.also { assertTexts("2026-10-01", "2026-12-31", "至") }

    // ---------- 空态说明：稳定左右留白 + 多行居中 ----------

    /** 空时间线（生日/纪念日/事项/联系全无）→ 只渲染一套空态。 */
    private fun reminderEmptyContent(): @Composable () -> Unit = {
        ReminderContent(
            st = ReminderUiState(loading = false),
            notifEnabled = true,
            onOpenPerson = {}, onDial = {}, onNotifSettings = {},
            onOpenTask = {}, onMarkDone = {}
        )
    }

    @Test
    fun reminderEmpty() = shot("reminder_empty", content = reminderEmptyContent())
        .also { assertTexts("未来 30 天内没有生日和事项", "这里会按时间排好") }

    @Test
    fun taskTemplatePicker() = shot("task_template_picker") {
        // 新建事项先选模板：六模板 + 图标 + 说明
        io.github.xiaohaoz1.jiarenxia.ui.reminder.TemplatePickerDialog(
            onPick = {}, onDismiss = {}
        )
    }

    @Test
    fun postCallPrompt() = shot("post_call_prompt") {
        // 打完电话回来的“联系上了吗”对话框：称谓 + 关闭钮 + 两个结果
        io.github.xiaohaoz1.jiarenxia.ui.common.PostCallPromptDialog(
            label = "爷爷", onConnected = {}, onDismiss = {})
    }.also {
        assertTexts("联系上了吗？", "刚才给爷爷打了电话", "没联系上", "联系上了")
        assertDescs("关闭")
    }

    @Test
    fun taskEdit() = shot("task_edit") {
        // 新建居民医保缴费：预填标题/日期/偏移
        io.github.xiaohaoz1.jiarenxia.ui.task.TaskEditContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.task.TaskEditUiState(
                taskId = null,
                kind = io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.MEDICAL_INSURANCE,
                personId = "mom",
                title = "妈妈的居民医保缴费",
                anchorDate = LocalDate.of(2026, 10, 15),
                repeat = io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.YEARLY,
                offsets = setOf(7, 0),
                amountText = "380",
                persons = listOf(
                    io.github.xiaohaoz1.jiarenxia.ui.task.TaskPersonOption(
                        "mom", "王秀兰", "王秀兰（妈妈）")
                )
            ),
            cb = io.github.xiaohaoz1.jiarenxia.ui.task.TaskEditCallbacks(),
            onDone = {}
        )
    }

    @Test
    fun detailMomTasks() = shot("detail_mom_tasks") {
        // “TA 的事”卡片：未完成前 3 条 + 添加事项；A6 后不再有人情/保障摘要卡
        val base = Demo.detailOf("mom")
        PersonDetailContent(
            lunar = Demo.lunar,
            st = base.copy(
                tasks = listOf(
                    taskRow("p-t1", "妈妈的居民医保缴费", 0, null).task,
                    taskRow("p-t2", "妈妈的体检", 20, null).task
                )
            ),
            today = Demo.today, snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onOpenAffairs = {}, onAddTask = {}, onOpenTaskDetail = {},
            onDial = {}, onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> }, onSetCustomTitle = {}, onDeletePerson = {},
            onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
        )
    }.also {
        assertTexts(
            "TA 的事", "添加事项", "妈妈的居民医保缴费", "每年 · 第1期")
        assertNoTexts("人情往来", "还可以记人情和保单")
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun mine() = shot("mine") {
        MineContent(self = Demo.demoOverview().self.person)
    }.also {
        assertTexts("家庭资料备份", "数据管理", "外观与字号", "功能介绍与管理", "帮助与关于", "检查更新")
    }

    @Test
    fun mineUpdateBadge() = shot("mine_update_badge") {
        MineContent(
            self = Demo.demoOverview().self.person,
            updateSubtitle = "发现新版本 1.2.0",
            updateBadge = true
        )
    }.also { assertTexts("检查更新", "发现新版本 1.2.0", "新版") }

    // ---------- 三项底栏（C3：家庭 / 家事 / 我的，守护已移除） ----------

    /** 页面内容 + 真实三项底栏（与 MainShell 同一实现）。 */
    private fun navShot(
        name: String, route: String, dark: Boolean = false,
        content: @Composable () -> Unit
    ) = shot(name, dark = dark) {
        androidx.compose.material3.Scaffold(
            bottomBar = { io.github.xiaohaoz1.jiarenxia.ui.main.MainBottomBar(route) {} }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h1400dp-xxhdpi")
    fun familyWithNav() = navShot("family_with_nav", "family") {
        FamilyContent(Demo.familyState(), Demo.lunar, {}, {}, {}, {}, {})
    }.also { assertTexts("家庭", "家事", "我的", "核心家人"); assertNoTexts("守护") }

    @Test
    fun affairsWithNav() = navShot("affairs_with_nav", "reminder") {
        val sample = reminderSampleContent()
        Column(Modifier.fillMaxSize()) {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.AffairsHeader(
                subtitle = "未来 30 天有 2 个生日、3 件事", onExport = {})
            Box(Modifier.weight(1f)) { sample() }
        }
    }.also { assertTexts("家事", "我的"); assertDescs("提醒设置"); assertNoTexts("守护") }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun mineWithNav() = navShot("mine_with_nav", "mine") {
        MineContent(self = Demo.demoOverview().self.person)
    }.also { assertTexts("家庭", "家事", "我的", "功能介绍与管理"); assertNoTexts("守护") }

    @Test
    fun navBarDark() = shot("nav_bar_dark", dark = true) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            io.github.xiaohaoz1.jiarenxia.ui.main.MainBottomBar("family") {}
        }
    }.also { assertTexts("家庭", "家事", "我的"); assertNoTexts("守护") }

    @Test
    fun navBarLarge() = shotLarge("nav_bar") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            io.github.xiaohaoz1.jiarenxia.ui.main.MainBottomBar("mine") {}
        }
    }.also { assertTexts("家庭", "家事", "我的"); assertNoTexts("守护") }

    @Test
    fun dataManage() = shot("data_manage") {
        io.github.xiaohaoz1.jiarenxia.ui.mine.DataManageContent(onBack = {})
    }.also {
        assertTexts("备份到文件", "从备份恢复", "清空所有数据")
        assertNoTexts("历史资料")
    }

    @Test
    @Config(qualifiers = "w412dp-h1700dp-xxhdpi")
    fun shareConnect() = shot("share_connect") {
        io.github.xiaohaoz1.jiarenxia.ui.mine.ShareConnectContent(onBack = {})
    }.also {
        assertTexts(
            "分享给家人", "接收家人分享", "结为夫妻", "祝福语",
            "碰一碰 · 实验功能", "尚未完成双机实测"
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h1700dp-xxhdpi")
    fun shareConnectDark() = shot("share_connect_dark", dark = true) {
        io.github.xiaohaoz1.jiarenxia.ui.mine.ShareConnectContent(onBack = {})
    }.also {
        assertTexts("碰一碰 · 实验功能", "尚未完成双机实测", "结为夫妻", "祝福语")
    }

    @Test
    @Config(qualifiers = "w412dp-h2100dp-xxhdpi")
    fun shareConnectLarge() = shotLarge("share_connect") {
        io.github.xiaohaoz1.jiarenxia.ui.mine.ShareConnectContent(onBack = {})
    }.also {
        assertTexts("碰一碰 · 实验功能", "尚未完成双机实测", "结为夫妻", "祝福语")
    }

    @Test
    fun appearance() = shot("appearance") {
        io.github.xiaohaoz1.jiarenxia.ui.mine.AppearanceContent(
            themeMode = io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode.LIGHT,
            widgetThemeMode = io.github.xiaohaoz1.jiarenxia.data.settings.WidgetThemeMode.SYSTEM,
            fontScaleLevel = io.github.xiaohaoz1.jiarenxia.data.settings.FontScaleLevel.STANDARD,
            onThemeMode = {}, onWidgetThemeMode = {}, onFontScale = {}, onBack = {}
        )
    }.also { assertTexts("浅色", "标准", "桌面插件配色") }

    @Test
    fun about() = shot("about") {
        io.github.xiaohaoz1.jiarenxia.ui.mine.AboutContent(
            onBack = {}, onPrivacy = {}, onIntro = {}
        )
    }.also { assertTexts("新手引导", "更新日志", "隐私说明", "问题反馈", "家人侠官网", "推荐给家人") }

    @Test
    fun aboutChangelog() = shot("about_changelog") {
        io.github.xiaohaoz1.jiarenxia.ui.mine.ChangelogDialog(onDismiss = {})
    }.also { assertTexts("更新日志", "1.1.7", "1.1") }

    @Test
    fun aboutUpdateOn() = shot("about_update_on") {
        io.github.xiaohaoz1.jiarenxia.ui.mine.AboutContent(
            onBack = {}, onPrivacy = {}, onIntro = {},
            updateSection = {
                io.github.xiaohaoz1.jiarenxia.ui.mine.UpdateSwitchCard(
                    enabled = true, onCheckedChange = {}
                )
            }
        )
    }.also { assertTexts("联网检查更新") }

    @Test
    fun aboutUpdateOff() = shot("about_update_off") {
        io.github.xiaohaoz1.jiarenxia.ui.mine.AboutContent(
            onBack = {}, onPrivacy = {}, onIntro = {},
            updateSection = {
                io.github.xiaohaoz1.jiarenxia.ui.mine.UpdateSwitchCard(
                    enabled = false, onCheckedChange = {}
                )
            }
        )
    }.also { assertTexts("联网检查更新") }

    @Test
    fun reminderSettings() = shot("reminder_settings") {
        io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderSettingsContent(
            state = io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderSettingsUi(),
            onBack = {}
        )
    }.also { assertTexts("生日与纪念日", "家庭事项", "联系家人") }

    @Test
    fun welcome() = shot("welcome") {
        io.github.xiaohaoz1.jiarenxia.ui.welcome.WelcomeContent(
            onNewFamily = {}, onPickFile = {}
        )
    }.also {
        assertTexts(
            "欢迎使用家人侠", "新建我的家庭", "开始填写",
            "用已有资料开始", "选择文件", "用其他应用打开"
        )
    }

    // ---------- 新手引导（单页版，首启一次） ----------

    @Test
    fun intro() = shot("intro") {
        io.github.xiaohaoz1.jiarenxia.ui.welcome.IntroContent(onDone = {})
    }.also {
        assertTexts("家人、日子、家事", "记家人、查关系", "记日子、办家事",
            "只在这台手机里", "开始使用")
    }

    @Test
    fun restoreConfirm() = shot("restore_confirm") {
        // 恢复确认正文：备份时间/规模/“恢复后我是谁”/覆盖警告/“我不是 X”入口
        io.github.xiaohaoz1.jiarenxia.ui.restore.RestoreConfirmBody(
            summary = io.github.xiaohaoz1.jiarenxia.data.backup.BackupSummary(
                createdAt = 1791325200000L,   // 2026-10-02 19:00
                appVersion = "0.1.0",
                persons = 13, relationships = 15, birthdays = 8,
                anniversaries = 2, tasks = 3,
                avatars = 2, selfName = "杨明"
            ),
            overwrites = true,
            onNotMe = {}
        )
    }.also {
        assertTexts(
            "恢复后，“我”是杨明。", "恢复将覆盖当前全部数据，且无法撤销。",
            "我不是杨明，按家人共享导入"
        )
    }

    @Test
    fun privacy() = shot("privacy") { PrivacyContent(onBack = {}) }

    // ---------- 家人共享（V0.6 第 6 批）----------

    /** 选“我是谁” + 导入摘要（称谓经真实引擎从分享者视角计算）。 */
    @Test
    fun sharePickMe() {
        val plan = sharePlanFixture()
        val labels = io.github.xiaohaoz1.jiarenxia.domain.ShareImport
            .candidates(
                plan, io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine(),
                io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar()
            ).map { it.label }
        shot("share_pick_me") {
        val candidates = io.github.xiaohaoz1.jiarenxia.domain.ShareImport
            .candidates(
                plan, io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine(),
                io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar()
            )
        io.github.xiaohaoz1.jiarenxia.ui.share.ShareImportContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.share.ShareImportUiState(
                stage = io.github.xiaohaoz1.jiarenxia.ui.share
                    .ShareImportUiState.Stage.CONFIRM,
                plan = plan,
                candidates = candidates,
                hasExisting = false
            ),
            onOpen = {}, onSelect = {}, onConfirm = {}, onDone = {}
        )
        }
        // 夹具里 王秀兰 是分享者本人，杨明 是儿子（称谓以引擎实际结果为准）
        assertTexts("来自 王秀兰 的家人共享", "你是谁？（请选你自己）",
            "王秀兰（分享者本人）", *labels.toTypedArray())
    }

    /** “资料里没有我”表单（16.3）：姓名 + “我是 [资料里的家人] 的 [角色]”。 */
    @Test
    fun shareNewSelf() = shot("share_new_self") {
        io.github.xiaohaoz1.jiarenxia.ui.share.NewSelfForm(
            name = "新进", onName = {},
            anchorLabel = "王秀兰（分享者本人）", onPickAnchor = {},
            role = io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.SON,
            onRole = {}, error = null
        )
    }.also {
        assertTexts("姓名", "我是", "的", "父亲", "儿子", "丈夫", "妻子")
    }

    /** “以 X 的身份导入？”预览正文：真实引擎算 sharePlanFixture 的称谓。 */
    @Test
    fun sharePreview() = shot("share_preview") {
        val plan = sharePlanFixture()
        // 接收方选了“杨明”（王秀兰的儿子）→ 从他的角度看
        val pv = io.github.xiaohaoz1.jiarenxia.domain.ShareImport
            .perspectivePreview(
                plan, "me",
                io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine(),
                io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar()
            )
        io.github.xiaohaoz1.jiarenxia.ui.share.PerspectivePreviewBody(pv)
    }.also {
        assertTexts("从你的角度看：", "王秀兰 · 母亲")
    }

    /** 合并预览（16.7）：候选 / 新增家人 / 补充内容 / 不一致 / 关系矛盾。 */
    @Test
    fun mergePreview() = shot("merge_preview") {
        fun person(id: String, name: String,
            g: io.github.xiaohaoz1.jiarenxia.core.model.Gender) =
            io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity(
                id = id, name = name, gender = g, avatarPath = null,
                note = null,
                livingStatus =
                    io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.ALIVE,
                isSelf = false, createdAt = 1, updatedAt = 1)
        val plan = io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge.Plan(
            idMap = mapOf("r-me" to "me"),
            newPersons = listOf(
                person("gp", "杨德福",
                    io.github.xiaohaoz1.jiarenxia.core.model.Gender.MALE),
                person("rx", "某某",
                    io.github.xiaohaoz1.jiarenxia.core.model.Gender.FEMALE)),
            newRelationships = emptyList(),
            newPhones = listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.PhoneNumberEntity(
                    id = "ph1", personId = "mom", number = "13800000000",
                    label = io.github.xiaohaoz1.jiarenxia.core.model
                        .PhoneLabel.MOBILE,
                    status = io.github.xiaohaoz1.jiarenxia.core.model
                        .PhoneStatus.ACTIVE,
                    isPrimary = true, note = null,
                    createdAt = 1, updatedAt = 1)),
            newDates = emptyList(),
            newPrefs = emptyList(), newOffsets = emptyList(),
            personUpdates = emptyList(), dateUpdates = emptyList(),
            avatarAdds = listOf(
                io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge
                    .AvatarAdd("gp", "gp.jpg", true)),
            candidates = listOf(
                io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge
                    .Candidate("r-mom", "mom", "王秀兰", "妈妈",
                        "杨明的母亲", nameDiffers = true,
                        conflictIfDeclined = false),
                io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge
                    .Candidate("r-gm", "gm", "李秀英", "外婆",
                        "妈妈的母亲", nameDiffers = false,
                        conflictIfDeclined = true)),
            conflicts = listOf(
                io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge
                    .FieldConflict(
                        io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge
                            .ConflictKey("mom", "name"),
                        "妈妈", "姓名", "妈妈", "王秀兰", takeRemote = false)),
            relationConflicts = listOf(
                io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge
                    .RelationConflict("gm", "rx",
                        "杨德福 和 某某 是夫妻", "杨德福已经有配偶了")),
            mapping = mapOf("r-me" to "me", "gp" to "gp", "rx" to "rx")
        )
        io.github.xiaohaoz1.jiarenxia.ui.share.MergePreviewContent(
            sharerName = "王秀兰",
            plan = plan,
            newTitles = mapOf("gp" to "爷爷（已故）", "rx" to "暂无称谓"),
            manualNames = emptyMap(),
            localItems = emptyList(), localGenders = emptyMap(),
            newAliases = 2,
            canReplace = true, busy = false,
            onToggleCandidate = {},
            onPickAsLocal = { _, _ -> }, onUnpickAsLocal = {},
            onChooseConflict = { _, _ -> },
            onConfirm = {}, onFinish = {},
            onReplace = {}, onBack = {}
        )
    }.also {
        assertTexts(
            "来自 王秀兰 的家人资料",
            "可能是同一人",
            "资料里的「王秀兰」就是你家的「妈妈」？",
            "名字不同",
            "不合并会产生关系矛盾",
            "新增家人",
            "杨德福 · 爷爷（已故）",
            "是我家的…",
            "补充内容",
            "手机号 1 个、头像 1 张",
            "不一致",
            "保留我的：妈妈",
            "用对方的：王秀兰",
            "关系矛盾（不会写入）",
            "杨德福已经有配偶了",
            "确认合并",
            "整体替换…"
        )
    }

    /** 共享测试 plan：2 个人物（分享者王秀兰 + 杨明）。 */
    private fun sharePlanFixture(): io.github.xiaohaoz1.jiarenxia.data.backup.RestorePlan {
        fun p(id: String, name: String, self: Boolean) =
            io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity(
                id = id, name = name,
                gender = io.github.xiaohaoz1.jiarenxia.core.model.Gender.FEMALE,
                avatarPath = null, note = null,
                livingStatus = io.github.xiaohaoz1.jiarenxia.core.model
                    .LivingStatus.ALIVE,
                isSelf = self, createdAt = 0, updatedAt = 0
            )
        return io.github.xiaohaoz1.jiarenxia.data.backup.RestorePlan(
            summary = io.github.xiaohaoz1.jiarenxia.data.backup.BackupSummary(
                createdAt = 0, appVersion = "0.6.0", persons = 2, relationships = 0,
                birthdays = 0, avatars = 0, selfName = "王秀兰"
            ),
            persons = listOf(p("sharer", "王秀兰", true), p("me", "杨明", false)),
            phones = emptyList(),
            // 王秀兰是杨明的母亲（PARENT: 父/母→子）
            relationships = listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity(
                    id = "r1", sourcePersonId = "sharer", targetPersonId = "me",
                    type = io.github.xiaohaoz1.jiarenxia.core.model
                        .RelationshipType.PARENT,
                    relativeAgeOrder = null, createdAt = 0, updatedAt = 0
                )
            ),
            importantDates = emptyList(), prefs = emptyList(),
            settings = null,
            avatars = emptyMap(), purpose = "share", sharedBy = "sharer"
        )
    }

    // ---------------- 碰一碰（V0.7） ----------------

    @Test
    fun tapNoNfc() = shot("tap_no_nfc") {
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState(
                stage = io.github.xiaohaoz1.jiarenxia.ui.tap
                    .TapUiState.Stage.NO_NFC))
    }.also {
        assertTexts("这部手机不支持 NFC（或不支持卡片模拟）",
            "改用家人共享")
    }

    @Test
    fun tapNfcOff() = shot("tap_nfc_off") {
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState(
                stage = io.github.xiaohaoz1.jiarenxia.ui.tap
                    .TapUiState.Stage.NFC_OFF))
    }.also {
        assertTexts("手机的 NFC 没有打开", "去打开 NFC")
    }

    @Test
    fun tapChooseRole() = shot("tap_choose_role") {
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState(
                stage = io.github.xiaohaoz1.jiarenxia.ui.tap
                    .TapUiState.Stage.CHOOSE_ROLE, roleReady = true))
    }.also {
        assertTexts("先选这部手机的连接方式", "这部手机主动感应",
            "这部手机等待感应")
    }

    @Test
    fun tapWaitReader() = shot("tap_wait_reader") {
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState(
                stage = io.github.xiaohaoz1.jiarenxia.ui.tap
                    .TapUiState.Stage.WAITING, isMale = true,
                isReader = true, roleChosen = true))
    }.also {
        assertTexts("靠近一点，成为一家人", "本机：主动感应中",
            "交换连接方式", "预演模式")
    }

    @Test
    fun tapWaitCard() = shot("tap_wait_card") {
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState(
                stage = io.github.xiaohaoz1.jiarenxia.ui.tap
                    .TapUiState.Stage.WAITING, isMale = false,
                isReader = false, roleChosen = true))
    }.also {
        assertTexts("靠近一点，成为一家人", "本机：等待感应",
            "交换连接方式", "预演模式")
    }

    @Test
    fun tapWaitPaired() = shot("tap_wait_paired") {
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState(
                stage = io.github.xiaohaoz1.jiarenxia.ui.tap
                    .TapUiState.Stage.WAITING, isMale = true,
                isReader = true, roleChosen = true,
                paired = true, selfSpouseName = "李雪"))
    }.also {
        assertTexts("和你，再心动一次", "碰一碰 · 纪念",
            "更新家人资料", "预演模式")
    }

    @Test
    fun tapConfirmSpouse() = shot("tap_confirm_spouse") {
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState(
                stage = io.github.xiaohaoz1.jiarenxia.ui.tap
                    .TapUiState.Stage.CONFIRMING, roleReady = true,
                confirm = io.github.xiaohaoz1.jiarenxia.ui.tap
                    .TapUiState.Confirm.SPOUSE_CANDIDATE,
                confirmName = "杨明", peerName = "杨明"))
    }.also {
        assertTexts("对方「杨明」就是你已录入的配偶吗？", "是，就是我的配偶",
            "不是")
    }

    @Test
    fun tapUpdatePreview() = shot("tap_update_preview") {
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState(
                stage = io.github.xiaohaoz1.jiarenxia.ui.tap
                    .TapUiState.Stage.UPDATE_PREVIEW, roleReady = true,
                paired = true, updateNew = 1, updateNewRels = 1,
                updateMatched = 6,
                updateConflicts = listOf(
                    "杨明 的生日不一致：本机 公历 1990年1月1日，" +
                        "对方 公历 1990年1月2日")))
    }.also {
        assertTexts("家人资料更新预览", "确认更新（本机）",
            "两边不一致（本机保留原样）：")
    }

    @Test
    fun tapExchange() = shot("tap_exchange") {
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState(
                stage = io.github.xiaohaoz1.jiarenxia.ui.tap
                    .TapUiState.Stage.EXCHANGING,
                peerName = "李雪", peerFamilyCount = 4))
    }.also {
        assertTexts("正在交换家人资料…", "对方：李雪（4 位家人）",
            "请保持两部手机贴在一起")
    }

    @Test
    fun tapWaitNotice() = shot("tap_wait_notice") {
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState(
                stage = io.github.xiaohaoz1.jiarenxia.ui.tap
                    .TapUiState.Stage.WAITING, isMale = true,
                isReader = true, roleChosen = true, roleReady = true,
                notice = "连接断开了，再贴一下继续"))
    }.also {
        assertTexts("连接断开了，再贴一下继续",
            "两部手机都打开这个页面，背靠背贴在一起")
    }

    @Test
    fun tapErrorRetry() = shot("tap_error_retry") {
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState(
                stage = io.github.xiaohaoz1.jiarenxia.ui.tap
                    .TapUiState.Stage.ERROR, roleReady = true,
                error = "碰一碰失败：资料包校验失败"))
    }.also {
        assertTexts("碰一碰失败：资料包校验失败", "重试")
    }

    private fun tapShowLabels() = listOf(
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState
            .FamilyLabel("妻子", "李雪"),
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState
            .FamilyLabel("岳父", "李国强"),
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState
            .FamilyLabel("岳母", "周淑芬"),
        io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState
            .FamilyLabel("小姨子", "李晴")
    )

    @Test
    fun tapShowLeft() {
        shot("tap_show_left") {
            io.github.xiaohaoz1.jiarenxia.ui.tap.HeartShowFrame(
                timeMs = 20_000L, startAt = 0L,
                selfName = "杨明", peerName = "李雪",
                leftHalf = true,
                labels = tapShowLabels(), moreCount = 0,
                rehearsal = false,
                date = java.time.LocalDate.of(2026, 10, 4))
        }
        // 男方左半心：右缘（接缝）是心色，左缘是背景
        assertShowEdge(
            "build/outputs/roborazzi/b_tap_show_left.png",
            heartOnRight = true)
    }

    @Test
    fun tapShowRight() {
        shot("tap_show_right") {
            io.github.xiaohaoz1.jiarenxia.ui.tap.HeartShowFrame(
                timeMs = 20_000L, startAt = 0L,
                selfName = "李雪", peerName = "杨明",
                leftHalf = false,
                labels = tapShowLabels(), moreCount = 0,
                rehearsal = false,
                date = java.time.LocalDate.of(2026, 10, 4))
        }
        // 女方右半心：左缘（接缝）是心色，右缘是背景
        assertShowEdge(
            "build/outputs/roborazzi/b_tap_show_right.png",
            heartOnRight = false)
    }

    @Test
    fun tapShowReveal() {
        shot("tap_show_reveal") {
            io.github.xiaohaoz1.jiarenxia.ui.tap.HeartShowFrame(
                timeMs = 1_800L, startAt = 0L,
                selfName = "杨明", peerName = "李雪",
                leftHalf = true,
                labels = tapShowLabels(), moreCount = 0,
                rehearsal = false,
                date = java.time.LocalDate.of(2026, 10, 4))
        }
        // 新家人飘入途中：画面已渲染（心 + 飞行中的称谓）
        assertDescs("杨明 与 李雪 喜结连理")
    }

    @Test
    fun tapShowCustomWords() {
        shot("tap_show_words") {
            io.github.xiaohaoz1.jiarenxia.ui.tap.HeartShowFrame(
                timeMs = 20_000L, startAt = 0L,
                selfName = "杨明", peerName = "李雪",
                leftHalf = true,
                labels = emptyList(), moreCount = 0,
                rehearsal = false,
                date = java.time.LocalDate.of(2026, 10, 4),
                weddingWords = "永结" to "同心")
        }
        // 自定义祝词：无障碍描述跟随“左词+右词”
        assertDescs("杨明 与 李雪 永结同心")
    }

    @Test
    fun tapShowMemorial() {
        shot("tap_show_memorial") {
            io.github.xiaohaoz1.jiarenxia.ui.tap.HeartShowFrame(
                timeMs = 20_000L, startAt = 0L,
                selfName = "杨明", peerName = "李雪",
                leftHalf = true,
                labels = emptyList(), moreCount = 0,
                rehearsal = false,
                date = java.time.LocalDate.of(2026, 10, 4),
                memorialLine = "结婚 10 周年快乐",
                anniversary = true)
        }
        assertDescs("杨明 与 李雪 的纪念日：结婚 10 周年快乐")
    }

    /** 断言合心截图边缘像素：接缝侧为心色（red>200），外侧为背景（red<160）。 */
    private fun assertShowEdge(path: String, heartOnRight: Boolean) {
        val bmp = android.graphics.BitmapFactory.decodeFile(path)
        val midY = bmp.height / 2
        val leftPx = bmp.getPixel(2, midY)
        val rightPx = bmp.getPixel(bmp.width - 3, midY)
        if (heartOnRight) {
            org.junit.Assert.assertTrue("右缘应为心色",
                android.graphics.Color.red(rightPx) > 200)
            org.junit.Assert.assertTrue("左缘应为背景",
                android.graphics.Color.red(leftPx) < 160)
        } else {
            org.junit.Assert.assertTrue("左缘应为心色",
                android.graphics.Color.red(leftPx) > 200)
            org.junit.Assert.assertTrue("右缘应为背景",
                android.graphics.Color.red(rightPx) < 160)
        }
    }

    @Test
    fun shareSpouseOption() = shot("share_spouse_option") {
        val plan = sharePlanFixture()
        val candidates = io.github.xiaohaoz1.jiarenxia.domain.ShareImport
            .candidates(
                plan, io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine(),
                io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar()
            )
        io.github.xiaohaoz1.jiarenxia.ui.share.ShareImportContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.share.ShareImportUiState(
                stage = io.github.xiaohaoz1.jiarenxia.ui.share
                    .ShareImportUiState.Stage.CONFIRM,
                plan = plan, candidates = candidates,
                mergeEligible = true, mergePreviewNew = 3),
            onOpen = {}, onSelect = {}, onConfirm = {}, onDone = {})
    }.also {
        assertTexts("和我结为夫妻（合并两家）")
    }

    // ---------- 协作（FAMILY_AFFAIRS §5）----------

    @Test
    fun collabConnect() = shot("collab_connect") {
        io.github.xiaohaoz1.jiarenxia.ui.mine.ShareConnectContent(
            onBack = {},
            collabStatus = io.github.xiaohaoz1.jiarenxia.data.collab
                .FamilyCollaborationService.Status(
                    bound = true, role = "coordinator", seriesId = "s1",
                    revision = 2, pendingDraftPackageId = "pkg1",
                    pendingDraftMode = "contribution", localOnlyCount = 1,
                    hasPrereceive = false),
            localOnlyCount = 1
        )
    }.also {
        assertTexts("发布确认版", "发回补充", "仅本机保留", "待办", "汇总者")
    }

    private fun collabItem(key: String, state: String, text: String,
                           person: String) =
        io.github.xiaohaoz1.jiarenxia.data.collab.FamilyCollaborationService
            .CachedItem(key, state, text, person)

    /** 协作接收页演示状态：确认版含全部五类条目 + 我是谁。 */
    private fun collabReviewFixture() =
        io.github.xiaohaoz1.jiarenxia.ui.share.CollabViewModel.ReviewUi(
            loading = false,
            packageId = "pkg1", mode = "confirmed", revision = 3,
            coordinatorName = "张明",
            applyItems = listOf(
                collabItem("pa1", "APPLY", "新增 李华（弟弟）", "c_li_hua"),
                collabItem("ph1", "APPLY", "张伟 · 电话 138****1234",
                    "c_zhang_wei")),
            decisionItems = listOf(
                collabItem("fd1", "CONFLICT",
                    "王芳 · 生日：本机 1985-03-02 ↔ 收到 1985-03-03",
                    "c_wang_fang"),
                io.github.xiaohaoz1.jiarenxia.data.collab
                    .FamilyCollaborationService.CachedItem(
                        "id1", "IDENTITY", "「赵磊」可能是你家已有的人",
                        "c_zhao_lei",
                        listOf(Triple("l_zhao", "赵磊", "同名"),
                            Triple("l_lei", "赵雷", "共同亲属"))),
                collabItem("rp1", "REAPPEAR", "「刘敏」曾移除，这次又出现了",
                    "c_liu_min")),
            blockedItems = listOf(
                collabItem("bl1", "BLOCKED", "「我」不能移除：本机已保留",
                    "c_me")),
            pendingItems = listOf(
                collabItem("pd1", "PENDING", "陈静 · 你本机新增的备忘",
                    "c_chen_jing")),
            pendingNames = mapOf("c_chen_jing" to "陈静"),
            decisions = mapOf("fd1" to "keep", "id1" to "same:l_zhao"),
            hasLocalSelf = false,
            remoteRoster = listOf("c_zhang_ming" to "张明",
                "c_li_hua" to "李华", "c_zhao_lei" to "赵磊"),
            selfChoice = null,
            canSendContribution = true
        )

    @Test
    @Config(qualifiers = "w412dp-h1400dp-xxhdpi")
    fun collabReviewConfirmed() = shot("collab_review_confirmed") {
        io.github.xiaohaoz1.jiarenxia.ui.share.CollabReviewContent(
            ui = collabReviewFixture(), onBack = {})
    }.also {
        assertTexts("张明 整理的家庭确认版 · 第 3 版", "需要确认", "自动更新",
            "家庭资料以这份为准", "先把补充发给汇总者",
            "这份资料里的哪位是你", "确认接收", "还有 2 项需要确认")
    }

    @Test
    @Config(qualifiers = "w412dp-h1400dp-xxhdpi")
    fun collabReviewConfirmedLarge() = shotLarge("collab_review_confirmed") {
        io.github.xiaohaoz1.jiarenxia.ui.share.CollabReviewContent(
            ui = collabReviewFixture(), onBack = {})
    }

    /**
     * 身份裁决并入后的页态：远端“张华”被判与本机“张华”同一人，
     * 身份项消失，新产生的字段分歧出现（关系项进自动更新）。
     * [noteDecided]=true 时备注分歧已选“保留本机”，全部确认完毕。
     */
    private fun collabIdentityFixture(noteDecided: Boolean) =
        io.github.xiaohaoz1.jiarenxia.ui.share.CollabViewModel.ReviewUi(
            loading = false,
            packageId = "pkg1", mode = "confirmed", revision = 1,
            coordinatorName = "张华",
            applyItems = listOf(
                collabItem("a1", "APPLY", "新增家人「张爸」", "c_dad"),
                collabItem("a2", "APPLY", "新增家人「张妈」", "c_mom"),
                collabItem("a3", "APPLY", "新增关系「张爸 是 张华 的父母」",
                    "c_dad"),
                collabItem("a4", "APPLY", "新增关系「张妈 是 张华 的父母」",
                    "c_mom")),
            decisionItems = listOf(
                collabItem("f:c_self:note", "CONFLICT",
                    "张华 的备注：你的「本机备注」与收到的「远端备注」不一致",
                    "c_self")),
            decisions = mapOf("add:c_self" to "same:l_self") +
                if (noteDecided) mapOf("f:c_self:note" to "keep")
                else emptyMap(),
            hasLocalSelf = true,
            remoteRoster = listOf("c_zh" to "张华", "c_dad" to "张爸",
                "c_mom" to "张妈"))

    @Test
    @Config(qualifiers = "w412dp-h1400dp-xxhdpi")
    fun collabReviewIdentityNewConflict() =
        shot("collab_review_identity_conflict") {
            io.github.xiaohaoz1.jiarenxia.ui.share.CollabReviewContent(
                ui = collabIdentityFixture(noteDecided = false), onBack = {})
        }.also {
            assertTexts("张华 的备注", "还有 1 项需要确认", "确认接收")
            assertNoTexts("是我家的", "都不包括我")
            composeRule.onNodeWithText("确认接收").assertIsNotEnabled()
        }

    @Test
    @Config(qualifiers = "w412dp-h1400dp-xxhdpi")
    fun collabReviewIdentityNewConflictLarge() =
        shotLarge("collab_review_identity_conflict") {
            io.github.xiaohaoz1.jiarenxia.ui.share.CollabReviewContent(
                ui = collabIdentityFixture(noteDecided = false), onBack = {})
        }

    @Test
    @Config(qualifiers = "w412dp-h1400dp-xxhdpi")
    fun collabReviewIdentityReady() = shot("collab_review_identity_ready") {
        io.github.xiaohaoz1.jiarenxia.ui.share.CollabReviewContent(
            ui = collabIdentityFixture(noteDecided = true), onBack = {})
    }.also {
        assertTexts("确认接收", "保留本机")
        assertNoTexts("还有")
        composeRule.onNodeWithText("确认接收").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "w412dp-h1400dp-xxhdpi")
    fun collabReviewIdentityReadyLarge() =
        shotLarge("collab_review_identity_ready") {
            io.github.xiaohaoz1.jiarenxia.ui.share.CollabReviewContent(
                ui = collabIdentityFixture(noteDecided = true), onBack = {})
        }

    @Test
    fun collabReviewContribution() = shot("collab_review_contribution") {
        io.github.xiaohaoz1.jiarenxia.ui.share.CollabReviewContent(
            ui = io.github.xiaohaoz1.jiarenxia.ui.share.CollabViewModel
                .ReviewUi(
                    loading = false, packageId = "p2", mode = "contribution",
                    senderName = "王芳",
                    applyItems = listOf(
                        collabItem("ph1", "APPLY", "张伟 · 电话 139****5678",
                            "c_zhang_wei")),
                    decisionItems = listOf(
                        collabItem("fd1", "CONFLICT",
                            "王芳 · 备注：本机有内容 ↔ 收到新内容",
                            "c_wang_fang")),
                    hasLocalSelf = true
                ),
            onBack = {})
    }.also {
        assertTexts("来自 王芳 的补充", "采用收到的", "保留本机")
    }
}
