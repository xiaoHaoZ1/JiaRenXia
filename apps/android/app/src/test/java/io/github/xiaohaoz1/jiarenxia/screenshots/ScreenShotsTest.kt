package io.github.xiaohaoz1.jiarenxia.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
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
import io.github.xiaohaoz1.jiarenxia.core.model.FactCategory
import io.github.xiaohaoz1.jiarenxia.core.model.PersonFact
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
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
import io.github.xiaohaoz1.jiarenxia.ui.person.FactEditorBody
import io.github.xiaohaoz1.jiarenxia.ui.person.FactEditorMode
import io.github.xiaohaoz1.jiarenxia.ui.person.PregnancyCard
import io.github.xiaohaoz1.jiarenxia.ui.person.PregnancyDateDialog
import io.github.xiaohaoz1.jiarenxia.ui.person.PregnancyEndDialog
import io.github.xiaohaoz1.jiarenxia.ui.person.PregnancyHistoryCard
import io.github.xiaohaoz1.jiarenxia.ui.person.PregnancyHistoryDialog
import io.github.xiaohaoz1.jiarenxia.ui.person.PregnancyInputMode
import io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeDueRow
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

    private fun shot(
        name: String,
        dark: Boolean = false,
        style: io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle =
            io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.JIASHU,
        content: @Composable () -> Unit
    ) {
        composeRule.setContent {
            JiaRenXiaTheme(
                mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                style = style
            ) {
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
     * 大字号（×1.3，约等于应用内“特大”级别）截图：文件名加 _lg。
     * AlertDialog 在独立窗口渲染，CompositionLocal 的 LocalDensity 传不进去；
     * 直接改本 Activity 的 Resources 配置 fontScale（对话框窗口共用同一
     * Resources），截图后恢复，不影响其他用例。
     * [scale] 默认 1.3；需要验证字号上限时传 1.6（外观与字号滑杆的最大值）。
     */
    private fun shotLarge(
        name: String,
        dark: Boolean = false,
        scale: Float = 1.3f,
        style: io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle =
            io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.JIASHU,
        content: @Composable () -> Unit
    ) {
        val res = composeRule.activity.resources
        val cfg = android.content.res.Configuration(res.configuration)
        cfg.fontScale = scale
        res.updateConfiguration(cfg, res.displayMetrics)
        try {
            composeRule.setContent {
                JiaRenXiaTheme(
                    mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                    style = style
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                    ) { content() }
                }
            }
            composeRule.onRoot().captureRoboImage(
                File("build/outputs/roborazzi/b_${name}_lg.png").absolutePath
            )
        } finally {
            cfg.fontScale = 1.0f
            res.updateConfiguration(cfg, res.displayMetrics)
        }
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
    fun family() = shot(
        "family_home",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        FamilyContent(Demo.familyState(), Demo.lunar, {}, {}, {}, {}, {})
    }.also {
        // 自定义称呼覆盖：伯父 杨建春 → “大伯”
        assertTexts("大伯", "核心家人", "家庭关系图")
        // 标题下文字入口（不再是图标按钮）：查找家人、添加家人均可点击
        composeRule.onNodeWithText("查找家人").assertHasClickAction()
        composeRule.onNodeWithText("添加家人").assertHasClickAction()
        // 核心家人两列各自左对齐：同列每格内容的左缘一致（居中排列时参差不齐）
        listOf(
            listOf("杨建国", "杨福生", "李雪", "杨小雨", "李国强"),
            listOf("王秀兰", "李桂英", "陈美华")
        ).forEach { column ->
            column.map { composeRule.onNodeWithText(it).getBoundsInRoot().left.value }
                .zipWithNext { a, b ->
                    org.junit.Assert.assertEquals(
                        "$column 左缘应对齐", a.toDouble(), b.toDouble(), 0.5
                    )
                }
        }
    }

    /** 「家书」家庭页：「杨家」标题+家字印、今日惦记、至亲行式名单。 */
    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun familyJiashu() = shot("family_home_js") {
        FamilyContent(
            Demo.familyState().copy(
                contactOverdue = mapOf("fil" to 23),
                generationCount = 4
            ),
            Demo.lunar, {}, {}, {}, {}, {}
        )
    }.also {
        assertTexts("杨家", "18 位家人 · 4代同堂", "今日惦记",
            "今天是母亲的生日", "打个电话", "23 天没联系了", "至亲",
            "家庭关系图", "共享与连接", "大伯")
        composeRule.onNodeWithText("查找").assertHasClickAction()
        composeRule.onNodeWithText("添加").assertHasClickAction()
        // 家书页不再有经典版文案
        assertNoTexts("核心家人", "查找家人")
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
        assertNoTexts("关于TA")
    }

    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun detailAboutEmpty() = shot("detail_about_empty") {
        PersonDetailContent(
            lunar = Demo.lunar,
            st = Demo.detailOf("mom").copy(facts = emptyList()),
            today = Demo.today, snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onDial = {}, onSetReminder = { _, _ -> },
            onSetReminderOffsets = { _, _ -> }, onSetCustomTitle = {},
            onDeletePerson = {}, onSetSiblingOrder = { _, _, _ -> },
            onRemoveRelation = {}
        )
    }.also {
        assertTexts("关于TA", "记下TA的尺码、口味和心愿")
    }

    private fun aboutFacts(): List<PersonFact> {
        fun fact(
            id: String,
            category: FactCategory,
            label: String?,
            content: String,
            day: LocalDate = LocalDate.of(2026, 9, 1),
            createdAt: Long
        ) = PersonFact(id, "mom", category, label, content, day, createdAt, createdAt)
        return listOf(
            fact("f1", FactCategory.SIZE, "鞋码", "37", createdAt = 1),
            fact("f2", FactCategory.SIZE, "上衣", "L", createdAt = 2),
            fact("f3", FactCategory.FOOD_LIKE, null, "红烧肉", createdAt = 3),
            fact("f4", FactCategory.FOOD_LIKE, null, "清蒸鲈鱼", createdAt = 4),
            fact("f5", FactCategory.FOOD_AVOID, null, "不吃香菜", createdAt = 5),
            fact("f6", FactCategory.HEALTH, null, "血压偏高，少放盐", createdAt = 6),
            fact("f7", FactCategory.HOBBY, null, "广场舞", createdAt = 7),
            fact(
                "f8", FactCategory.WISH, null, "按摩椅",
                day = LocalDate.of(2024, 9, 1), createdAt = 8
            ),
            fact("f9", FactCategory.CUSTOM, "常去的店", "楼下张记水果店", createdAt = 9)
        )
    }

    @Composable
    private fun DetailAboutContent() {
        PersonDetailContent(
            lunar = Demo.lunar,
            st = Demo.detailOf("mom").copy(facts = aboutFacts()),
            today = Demo.today, snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onDial = {}, onSetReminder = { _, _ -> },
            onSetReminderOffsets = { _, _ -> }, onSetCustomTitle = {},
            onDeletePerson = {}, onSetSiblingOrder = { _, _, _ -> },
            onRemoveRelation = {}
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun detailAbout() = shot("detail_about") { DetailAboutContent() }.also {
        assertTexts(
            "关于TA", "尺码", "鞋码 37", "上衣 L", "按摩椅 · 2024年",
            "常去的店", "楼下张记水果店"
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun detailAboutLarge() = shotLarge("detail_about") { DetailAboutContent() }

    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun detailAboutDeceased() = shot("detail_about_deceased") {
        val base = Demo.detailOf("mom")
        PersonDetailContent(
            lunar = Demo.lunar,
            st = base.copy(
                detail = base.detail!!.copy(
                    person = base.detail.person.copy(livingStatus = LivingStatus.DECEASED)
                ),
                facts = emptyList()
            ),
            today = Demo.today, snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onDial = {}, onSetReminder = { _, _ -> },
            onSetReminderOffsets = { _, _ -> }, onSetCustomTitle = {},
            onDeletePerson = {}, onSetSiblingOrder = { _, _, _ -> },
            onRemoveRelation = {}
        )
    }.also {
        assertTexts("留作念想")
        assertNoTexts("尺码", "想要的")
    }

    @Composable
    private fun AboutEditorAddSizeContent() {
        Column(Modifier.padding(24.dp)) {
            FactEditorBody(
                mode = FactEditorMode.ADD,
                availableCategories = FactCategory.entries,
                category = FactCategory.SIZE,
                sizeChoice = "鞋码",
                otherSizeLabel = "",
                customLabel = "",
                content = "38",
                originalContent = "37",
                recordedOn = null,
                onCategoryChange = {}, onSizeChoiceChange = {},
                onOtherSizeLabelChange = {}, onCustomLabelChange = {},
                onContentChange = {}
            )
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun aboutEditorAddSize() = shot("about_editor_add_size") {
        AboutEditorAddSizeContent()
    }.also {
        assertTexts("分类", "哪一项", "原来记的是 37，保存后会替换")
    }

    /** 字号上限 160%：弹框正文最高的一种组合，验证换行与弹框内滚动的必要性。 */
    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun aboutEditorAddSizeLarge() = shotLarge("about_editor_add_size", scale = 1.6f) {
        AboutEditorAddSizeContent()
    }

    /** 编辑尺码：不显示尺码项选择，输入框标签就是该项名称。 */
    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun aboutEditorEditSize() = shot("about_editor_edit_size") {
        Column(Modifier.padding(24.dp)) {
            FactEditorBody(
                mode = FactEditorMode.EDIT,
                availableCategories = FactCategory.entries,
                category = FactCategory.SIZE,
                sizeChoice = "鞋码",
                otherSizeLabel = "",
                customLabel = "",
                content = "37",
                originalContent = null,
                recordedOn = LocalDate.of(2026, 9, 1),
                onCategoryChange = {}, onSizeChoiceChange = {},
                onOtherSizeLabelChange = {}, onCustomLabelChange = {},
                onContentChange = {}
            )
        }
    }.also {
        assertTexts("鞋码", "记录于 2026年9月")
        assertNoTexts("哪一项", "分类", "上衣")
    }

    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun aboutEditorEditCustom() = shot("about_editor_edit_custom") {
        Column(Modifier.padding(24.dp)) {
            FactEditorBody(
                mode = FactEditorMode.EDIT,
                availableCategories = FactCategory.entries,
                category = FactCategory.CUSTOM,
                sizeChoice = "鞋码",
                otherSizeLabel = "",
                customLabel = "常去的店",
                content = "楼下张记水果店",
                originalContent = null,
                recordedOn = LocalDate.of(2026, 9, 1),
                onCategoryChange = {}, onSizeChoiceChange = {},
                onOtherSizeLabelChange = {}, onCustomLabelChange = {},
                onContentChange = {}
            )
        }
    }.also {
        assertTexts("分类名", "记录于 2026年9月")
        assertNoTexts("哪一项")
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

    /** 完整对话框（含标题与按钮）的年份页：回归 2026-11-08 的「换个年代」残影
     * ——YearPickerContent 未包 Column 时在 AlertDialog text 槽位重叠绘制，
     * 按钮文字从年份色块缝隙透出。单列截图覆盖不到，必须走 AlertDialog。 */
    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun personEditYearDialog() = shot("person_edit_year_dialog") {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("选择年份 · 2020年代") },
            text = {
                io.github.xiaohaoz1.jiarenxia.ui.person.YearPickerContent(
                    decade = 2020, selectedYear = null,
                    from = 1900, to = 2026,
                    onDecade = {}, onYear = {}
                )
            },
            confirmButton = { TextButton(onClick = {}) { Text("取消") } }
        )
    }.also {
        assertTexts("换个年代", "2026", "2020")
    }

    /** 同对话框 1.3 倍大字号。 */
    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun personEditYearDialogLarge() = shotLarge("person_edit_year_dialog", scale = 1.3f) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("选择年份 · 2020年代") },
            text = {
                io.github.xiaohaoz1.jiarenxia.ui.person.YearPickerContent(
                    decade = 2020, selectedYear = null,
                    from = 1900, to = 2026,
                    onDecade = {}, onYear = {}
                )
            },
            confirmButton = { TextButton(onClick = {}) { Text("取消") } }
        )
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
        // 画布上方不再有整排分支 FilterChip；查找收进“更多”菜单，
        // 顶栏只留添加家人/更多，底栏胶囊按钮
        assertTexts("已定位 · ", "回到我", "展开家人", "查看总览")
        assertDescs("添加家人", "更多")
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land-xxhdpi")
    fun graphLandscape() = shot("graph_landscape") {
        val ov = Demo.demoOverview()
        val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
        GraphContent(
            model = GraphLayout.build(ov, gens, sizer = GraphTextMeasure.sizer),
            byId = ov.byId, selfId = ov.self.person.id,
            onBack = {}, onOpenPerson = {},
            onToggleLandscape = {},
            connectedTotal = gens.size
        )
    }.also {
        // 横屏全屏：无标题栏、信息行与操作按钮（缩放回中全靠手势），
        // 只剩画布上缘一条悬浮条（返回+分支+竖屏/添加/菜单），总览收进菜单
        assertNoTexts("家庭关系图", "查看总览", "回到我", "展开家人")
        assertTexts("恢复竖屏")
        assertDescs("返回", "添加家人", "更多")
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

    @Test
    fun reminderGuideBanner() {
        shot("reminder_guide") {
            ReminderContent(
                st = ReminderUiState(
                    loading = false,
                    showReminderGuide = true,
                    upcoming = listOf(
                        BirthdayRow(
                            Demo.demoOverview().byId.getValue("mom").person,
                            listOf(Demo.demoOverview().byId.getValue("mom").birthday!!),
                            "妈妈", 3, true, LocalDate.of(2026, 10, 5))
                    )
                ),
                notifEnabled = true,
                onOpenPerson = {}, onDial = {}, onNotifSettings = {},
                onDismissGuide = {}
            )
        }
        check(composeRule.onAllNodes(hasText("上坟祭扫", substring = true))
            .fetchSemanticsNodes().isNotEmpty()) { "引导条应点名上坟祭扫" }
        check(composeRule.onAllNodes(hasText("知道了"))
            .fetchSemanticsNodes().isNotEmpty()) { "引导条应有关闭按钮" }
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
                contactDue = listOf(
                    io.github.xiaohaoz1.jiarenxia.ui.reminder.ContactDueRow(
                        "gmd", "奶奶李桂英", 23, 9)
                ),
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
    fun reminder() = shot(
        "reminder",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC,
        content = reminderSampleContent()
    ).also {
        assertTexts("今天", "王秀兰", "未来 30 天（3）", "已逾期 2 天", "380 元")
        // 今天生日行（他人+有号码）：发祝福与拨号两个图标
        assertDescs("发祝福", "拨打", "标记已办")
    }

    /** 家事页整屏·经典（A6 后）：页头 + 铃铛/更多行 + 卡片时间线。 */
    @Test
    fun affairsTimeline() = shot(
        "affairs_timeline",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        val sample = reminderSampleContent()
        Column(Modifier.fillMaxSize()) {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.AffairsHeader(
                subtitle = "未来 30 天有 2 个生日、3 件事",
                onExport = {}
            )
            Box(Modifier.weight(1f)) { sample() }
        }
    }.also {
        assertTexts("家事", "未来 30 天（3）", "王秀兰", "10/05", "提醒")
        assertNoTexts("人情", "保障")
        assertDescs("更多")
    }

    /** 家事页整屏·家书：去卡片行式时间线 + 大号日左列 + 朱砂印 + 右上「新建」。 */
    @Test
    fun affairsJiashu() = shot("affairs_timeline_js") {
        val sample = reminderSampleContent()
        Column(Modifier.fillMaxSize()) {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.AffairsHeader(
                subtitle = "未来 30 天有 2 个生日、3 件事",
                onExport = {},
                onNewTask = {}
            )
            Box(Modifier.weight(1f)) { sample() }
        }
    }.also {
        assertTexts("家事", "今天", "逾期 2 天", "记一次", "该联系奶奶李桂英了",
            "未来 30 天", "3 天后", "18 天后", "9 天后", "新建", "提醒")
        assertNoTexts("已逾期", "人情", "保障")
        assertDescs("更多", "发祝福", "拨打")
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
        assertTexts("提醒")
        assertDescs("更多")
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
    fun mine() = shot(
        "mine",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        MineContent(self = Demo.demoOverview().self.person)
    }.also {
        assertTexts("家庭资料备份", "数据管理", "外观与字号", "功能介绍与管理", "帮助与关于", "检查更新")
    }

    /** 我的页·家书：标题双线、方角头像、备份块、发丝线入口、「侠」印落款。 */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun mineJiashu() = shot("mine_js") {
        MineContent(self = Demo.demoOverview().self.person)
    }.also {
        assertTexts("我的", "备份", "需要设置密码", "查看我的资料",
            "数据管理", "外观与字号", "功能介绍与管理", "帮助与关于", "检查更新",
            "资料只留在你的手机里")
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
    }.also { assertTexts("家庭", "家事", "我的", "至亲"); assertNoTexts("守护") }

    @Test
    fun affairsWithNav() = navShot("affairs_with_nav", "reminder") {
        val sample = reminderSampleContent()
        Column(Modifier.fillMaxSize()) {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.AffairsHeader(
                subtitle = "未来 30 天有 2 个生日、3 件事", onExport = {})
            Box(Modifier.weight(1f)) { sample() }
        }
    }.also { assertTexts("家事", "我的", "提醒"); assertNoTexts("守护") }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun mineWithNav() = navShot("mine_with_nav", "mine") {
        // 隐藏守护侠推荐卡：本截图只验底栏，「守护」断言防的是守护标签复活
        MineContent(
            self = Demo.demoOverview().self.person,
            guardianPromoDismissed = true
        )
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
            "碰一碰 · 实验功能", "如果连接不上，请按提示交换连接方式"
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h1700dp-xxhdpi")
    fun shareConnectDark() = shot("share_connect_dark", dark = true) {
        io.github.xiaohaoz1.jiarenxia.ui.mine.ShareConnectContent(onBack = {})
    }.also {
        assertTexts("碰一碰 · 实验功能", "如果连接不上，请按提示交换连接方式", "结为夫妻", "祝福语")
    }

    @Test
    @Config(qualifiers = "w412dp-h2100dp-xxhdpi")
    fun shareConnectLarge() = shotLarge("share_connect") {
        io.github.xiaohaoz1.jiarenxia.ui.mine.ShareConnectContent(onBack = {})
    }.also {
        assertTexts("碰一碰 · 实验功能", "如果连接不上，请按提示交换连接方式", "结为夫妻", "祝福语")
    }

    @Test
    fun appearance() = shot("appearance") {
        io.github.xiaohaoz1.jiarenxia.ui.mine.AppearanceContent(
            themeMode = io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode.LIGHT,
            themeStyle = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.JIASHU,
            widgetThemeMode = io.github.xiaohaoz1.jiarenxia.data.settings.WidgetThemeMode.SYSTEM,
            fontScaleFactor = 1.0f,
            elderMode = false,
            onThemeMode = {}, onThemeStyle = {}, onWidgetThemeMode = {},
            onFontScale = {}, onElderMode = {}, onBack = {}
        )
    }.also { assertTexts("家书", "经典", "浅色", "字号 100%", "桌面插件配色", "长辈模式") }

    /** 外观页·经典：分段按钮保持不变。 */
    @Test
    fun appearanceClassic() = shot(
        "appearance_classic",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        io.github.xiaohaoz1.jiarenxia.ui.mine.AppearanceContent(
            themeMode = io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode.LIGHT,
            themeStyle = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC,
            widgetThemeMode = io.github.xiaohaoz1.jiarenxia.data.settings.WidgetThemeMode.SYSTEM,
            fontScaleFactor = 1.0f,
            elderMode = false,
            onThemeMode = {}, onThemeStyle = {}, onWidgetThemeMode = {},
            onFontScale = {}, onElderMode = {}, onBack = {}
        )
    }.also { assertTexts("家书", "经典", "浅色", "字号 100%", "桌面插件配色", "长辈模式") }

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
            state = io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderSettingsUi(
                tombSummary = "清明、中元、寒衣、忌日、做七 · 2 位家人",
                homeSummary = "爸爸妈妈 每季度；岳父岳母 每半年"),
            onBack = {}
        )
    }.also {
        assertTexts("生日与纪念日", "家庭事项", "联系家人", "上坟提醒",
            "上坟的日子与家人", "回家看看", "爸爸妈妈 每季度")
    }

    /** 提醒设置·经典：四类开关 + 「上坟提醒」「回家看看」入口组。 */
    @Test
    fun reminderSettingsClassic() = shot(
        "reminder_settings_classic",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderSettingsContent(
            state = io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderSettingsUi(
                tombSummary = "清明、中元、寒衣、忌日、做七 · 2 位家人",
                homeSummary = "爸爸妈妈 每季度；岳父岳母 每半年"),
            onBack = {}
        )
    }.also {
        assertTexts("生日与纪念日", "家庭事项", "联系家人", "上坟提醒",
            "上坟的日子与家人", "回家看看", "爸爸妈妈 每季度")
    }

    // ---------- 上坟提醒设置（FAMILY_AFFAIRS 19） ----------

    /** 上坟设置演示数据：三位已故家人——爷爷默认开、奶奶被关掉、大伯（旁系）默认关。 */
    private fun tombSettingsState(
        mourners: List<io.github.xiaohaoz1.jiarenxia.domain.tomb.TombMourner> =
            listOf(
                io.github.xiaohaoz1.jiarenxia.domain.tomb.TombMourner(
                    "gfd", "爷爷", "杨福生", defaultOn = true),
                io.github.xiaohaoz1.jiarenxia.domain.tomb.TombMourner(
                    "gmd", "奶奶", "李桂英", defaultOn = true),
                io.github.xiaohaoz1.jiarenxia.domain.tomb.TombMourner(
                    "old", "大伯", "杨建春", defaultOn = false)
            ),
        excluded: Set<String> = setOf("gmd")
    ) = io.github.xiaohaoz1.jiarenxia.ui.reminder.TombSettingsUi(
        days = io.github.xiaohaoz1.jiarenxia.domain.tomb.TombPrefs.DEFAULT_DAYS +
            io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay.CHUXI,
        zhongyuanDay = 15,
        excluded = excluded,
        mourners = mourners
    )

    @Test
    fun tombSettings() = shot("tomb_settings") {
        io.github.xiaohaoz1.jiarenxia.ui.reminder.TombSettingsContent(
            state = tombSettingsState(), onBack = {}
        )
    }.also {
        assertTexts("哪些日子提醒", "清明节", "中元节", "中元节按哪天",
            "寒衣节", "除夕", "冬至", "忌日", "做七与百日", "给谁上坟",
            "爷爷", "李桂英", "大伯",
            "直系亲属默认开启")
    }

    /** 上坟设置·经典。 */
    @Test
    fun tombSettingsClassic() = shot(
        "tomb_settings_classic",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        io.github.xiaohaoz1.jiarenxia.ui.reminder.TombSettingsContent(
            state = tombSettingsState(), onBack = {}
        )
    }.also { assertTexts("哪些日子提醒", "给谁上坟", "爷爷") }

    /** 上坟设置空态：没有已故家人。 */
    @Test
    fun tombSettingsEmpty() = shot("tomb_settings_empty") {
        io.github.xiaohaoz1.jiarenxia.ui.reminder.TombSettingsContent(
            state = tombSettingsState(mourners = emptyList(), excluded = emptySet()),
            onBack = {}
        )
    }.also {
        assertTexts("哪些日子提醒", "家里还没有记为已故的家人")
    }

    /** 上坟设置空态·经典。 */
    @Test
    fun tombSettingsEmptyClassic() = shot(
        "tomb_settings_empty_classic",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        io.github.xiaohaoz1.jiarenxia.ui.reminder.TombSettingsContent(
            state = tombSettingsState(mourners = emptyList(), excluded = emptySet()),
            onBack = {}
        )
    }.also { assertTexts("家里还没有记为已故的家人") }

    // ---------- 家事页上的上坟日（FAMILY_AFFAIRS 19） ----------

    /**
     * 家事页含上坟日：今天一条中元节；未来 30 天一条“爷爷的三七”与一条清明节。
     *（演示数据：中元节=今天、三七=10/03、清明节=10/20）
     */
    private fun reminderTombContent(): @Composable () -> Unit = {
        val st = ReminderUiState(
            loading = false,
            tombToday = listOf(
                io.github.xiaohaoz1.jiarenxia.domain.tomb.TombEvent(
                    day = io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay.ZHONGYUAN,
                    date = LocalDate.of(2026, 10, 2),
                    title = "中元节", lunarText = "农历七月十五",
                    personIds = listOf("gfd", "gmd"),
                    names = listOf("爷爷", "奶奶"))
            ),
            tombNext30 = listOf(
                io.github.xiaohaoz1.jiarenxia.domain.tomb.TombEvent(
                    day = io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay.MOURNING,
                    date = LocalDate.of(2026, 10, 3),
                    title = "爷爷的三七", lunarText = null,
                    personIds = listOf("gfd"), names = listOf("爷爷"),
                    mourningDayNumber = 21),
                io.github.xiaohaoz1.jiarenxia.domain.tomb.TombEvent(
                    day = io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay.QINGMING,
                    date = LocalDate.of(2026, 10, 20),
                    title = "清明节", lunarText = null,
                    personIds = listOf("gfd", "gmd"),
                    names = listOf("爷爷", "奶奶"))
            )
        )
        Column(Modifier.fillMaxSize()) {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.AffairsHeader(
                subtitle = io.github.xiaohaoz1.jiarenxia.ui.reminder
                    .reminderSubtitle(st),
                onExport = {}
            )
            Box(Modifier.weight(1f)) {
                ReminderContent(
                    st = st,
                    notifEnabled = true,
                    onOpenPerson = {}, onDial = {}, onNotifSettings = {},
                    onOpenTask = {}, onMarkDone = {}
                )
            }
        }
    }

    @Test
    fun reminderTomb() = shot(
        "reminder_tomb",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC,
        content = reminderTombContent()
    ).also {
        assertTexts("中元节", "农历七月十五 · 给爷爷、奶奶上坟",
            "爷爷的三七", "去世第 21 天", "清明节",
            "给爷爷、奶奶上坟")
    }

    /** 家事页含上坟日·家书。 */
    @Test
    fun reminderTombJiashu() = shot("reminder_tomb_js",
        content = reminderTombContent()).also {
        assertTexts("中元节", "爷爷的三七", "清明节", "去世第 21 天")
    }

    // ---------- 回家看看（FAMILY_AFFAIRS 20） ----------

    /** 回家看看设置演示数据：爸妈家（每季度，上次 95 天前）+ 岳父母家（每半年，还没记录）。 */
    private fun homeVisitState(
        homes: List<io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitRow> =
            listOf(
                io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitRow(
                    planId = "hv1", label = "爸爸妈妈",
                    memberIds = listOf("p1", "p2"),
                    memberValue = "爸爸、妈妈",
                    intervalDays = 90, intervalLabel = "每季度",
                    lastVisitText = "95 天前"),
                io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitRow(
                    planId = "hv2", label = "岳父岳母",
                    memberIds = listOf("p3", "p4"),
                    memberValue = "岳父、岳母",
                    intervalDays = 180, intervalLabel = "每半年",
                    lastVisitText = "还没有记录")
            )
    ) = io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitUi(
        holidayNudge = true,
        homes = homes,
        candidates = listOf(
            io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitCandidate(
                "p1", "爸爸（杨建国）", isParent = true),
            io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitCandidate(
                "p2", "妈妈（李秀兰）", isParent = true),
            io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitCandidate(
                "p5", "老婆（陈静）", isParent = false),
            io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitCandidate(
                "p3", "岳父（周建军）", isParent = false),
            io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitCandidate(
                "p4", "岳母（王秀英）", isParent = false)
        )
    )

    @Test
    fun homeVisitSettings() = shot("home_visit_settings") {
        io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitContent(
            state = homeVisitState(), onBack = {}
        )
    }.also {
        assertTexts("爸爸妈妈", "家里有谁", "多久回去一次", "上次回家",
            "不再提醒这个家", "再添加一个家", "节前提醒",
            "只有“见面”记录算回过家")
    }

    /** 回家看看设置·经典。 */
    @Test
    fun homeVisitSettingsClassic() = shot(
        "home_visit_settings_classic",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitContent(
            state = homeVisitState(), onBack = {}
        )
    }.also { assertTexts("爸爸妈妈", "岳父岳母", "节前提醒") }

    /** 回家看看设置空态：还没有“家”。 */
    @Test
    fun homeVisitSettingsEmpty() = shot("home_visit_settings_empty") {
        io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitContent(
            state = homeVisitState(homes = emptyList()), onBack = {}
        )
    }.also {
        assertTexts("选好家里有谁、多久回去一次", "添加一个家", "节前提醒")
    }

    /** 回家看看设置空态·经典。 */
    @Test
    fun homeVisitSettingsEmptyClassic() = shot(
        "home_visit_settings_empty_classic",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        io.github.xiaohaoz1.jiarenxia.ui.reminder.HomeVisitContent(
            state = homeVisitState(homes = emptyList()), onBack = {}
        )
    }.also { assertTexts("添加一个家", "节前提醒") }

    /** 家事页“今天”有一条回家看看（经典）。 */
    @Test
    fun reminderHomeVisit() = shot(
        "reminder_home_visit",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC,
        content = reminderHomeVisitContent()
    ).also {
        assertTexts("该回家看看爸爸妈妈了", "已 95 天没回家", "回家了",
            "1 个家该回去看看了")
    }

    /** 家事页“今天”有一条回家看看·家书。 */
    @Test
    fun reminderHomeVisitJiashu() = shot("reminder_home_visit_js",
        content = reminderHomeVisitContent()).also {
        assertTexts("该回家看看爸爸妈妈了", "已 95 天没回家", "回家了")
    }

    /** 家事页回家看看演示：今天一条“该回家看看爸爸妈妈了”（已 95 天）。 */
    private fun reminderHomeVisitContent(): @Composable () -> Unit = {
        val st = ReminderUiState(
            loading = false,
            homeDue = listOf(
                HomeDueRow(planId = "hv1", label = "爸爸妈妈",
                    daysSinceLast = 95)
            )
        )
        Column(Modifier.fillMaxSize()) {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.AffairsHeader(
                subtitle = io.github.xiaohaoz1.jiarenxia.ui.reminder
                    .reminderSubtitle(st),
                onExport = {}
            )
            Box(Modifier.weight(1f)) {
                ReminderContent(
                    st = st,
                    notifEnabled = true,
                    onOpenPerson = {}, onDial = {}, onNotifSettings = {},
                    onOpenTask = {}, onMarkDone = {}
                )
            }
        }
    }

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
                io.github.xiaohaoz1.jiarenxia.domain.lunar.TableLunarCalendar()
            ).map { it.label }
        shot("share_pick_me") {
        val candidates = io.github.xiaohaoz1.jiarenxia.domain.ShareImport
            .candidates(
                plan, io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine(),
                io.github.xiaohaoz1.jiarenxia.domain.lunar.TableLunarCalendar()
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
                io.github.xiaohaoz1.jiarenxia.domain.lunar.TableLunarCalendar()
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
                io.github.xiaohaoz1.jiarenxia.domain.lunar.TableLunarCalendar()
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

    // ---------- 长辈模式（FAMILY_AFFAIRS 21）：截图按 ×1.3 字号渲染（shotLarge） ----------

    /** 长辈首页演示数据：今天孙子生日（有号码）、+5 天忌日、+20 天清明节；6 位家人。 */
    private fun elderHomeState(
        emptyUpcoming: Boolean = false
    ): io.github.xiaohaoz1.jiarenxia.ui.elder.ElderUiState {
        val t = java.time.LocalDate.now()
        val upcoming = if (emptyUpcoming) emptyList()
        else listOf(
            io.github.xiaohaoz1.jiarenxia.domain.elder.ElderHome.UpcomingRow(
                kind = io.github.xiaohaoz1.jiarenxia.domain.elder
                    .ElderHome.UpcomingKind.BIRTHDAY,
                date = t, dayLabel = "今天",
                title = "孙子的生日", subtitle = "小宝 · 农历八月十五 · 6 岁",
                personId = "sun"),
            io.github.xiaohaoz1.jiarenxia.domain.elder.ElderHome.UpcomingRow(
                kind = io.github.xiaohaoz1.jiarenxia.domain.elder
                    .ElderHome.UpcomingKind.DATE,
                date = t.plusDays(5), dayLabel = "5天后",
                title = "爷爷的忌日", subtitle = "3 周年",
                personId = "gf"),
            io.github.xiaohaoz1.jiarenxia.domain.elder.ElderHome.UpcomingRow(
                kind = io.github.xiaohaoz1.jiarenxia.domain.elder
                    .ElderHome.UpcomingKind.TOMB,
                date = t.plusDays(20), dayLabel = "20天后",
                title = "清明节", subtitle = "给爷爷、奶奶上坟")
        )
        val members = listOf(
            Triple("dad", "爸爸", "杨建国"),
            Triple("mom", "妈妈", "李秀英"),
            Triple("gf", "爷爷", "杨德福"),
            Triple("gm", "奶奶", "王秀兰"),
            Triple("sun", "孙子", "小宝"),
            Triple("uncle", "杨国强", "杨国强")
        ).mapIndexed { i, (id, label, name) ->
            io.github.xiaohaoz1.jiarenxia.domain.elder.ElderHome.MemberRow(
                personId = id, label = label, name = name,
                hasTitle = i < 5, avatarPath = null)
        }
        return io.github.xiaohaoz1.jiarenxia.ui.elder.ElderUiState(
            loading = false,
            headerLine = "11月7日 星期六 · 农历九月廿九",
            upcoming = upcoming,
            members = members,
            defaultNumbers = mapOf("dad" to "13800001111", "mom" to "13900002222",
                "sun" to "13700003333")
        )
    }

    @Test
    fun elderHome() = shotLarge("elder_home") {
        io.github.xiaohaoz1.jiarenxia.ui.elder.ElderHomeContent(
            state = elderHomeState(), onOpenPerson = {}, onCall = {}, onExit = {})
    }.also {
        // 退出控件在长列表底部（1.3 倍字号下需滚动），不断言其存在
        assertTexts("我的家人", "最近的日子", "孙子的生日", "爷爷的忌日",
            "清明节", "爸爸", "孙子")
    }

    @Test
    fun elderHomeClassic() = shotLarge(
        "elder_home_classic",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        io.github.xiaohaoz1.jiarenxia.ui.elder.ElderHomeContent(
            state = elderHomeState(), onOpenPerson = {}, onCall = {}, onExit = {})
    }.also { assertTexts("我的家人", "孙子的生日", "打电话") }

    /** 长辈首页「今天要吃的药」演示：08:00 已过置灰、12:00 高亮、20:00 正常。 */
    private fun elderHomeMedsState() = elderHomeState().copy(
        medPlan = listOf(
            io.github.xiaohaoz1.jiarenxia.ui.elder.ElderMedPlan(
                480, "二甲双胍 1 片"),
            io.github.xiaohaoz1.jiarenxia.ui.elder.ElderMedPlan(
                720, "阿司匹林 1 片、布洛芬 1 粒"),
            io.github.xiaohaoz1.jiarenxia.ui.elder.ElderMedPlan(
                1200, "维生素C 1 片（疗程待确认）")
        )
    )

    // 现在 12:10 → 阈值 11:40 → 12:00 行（中间）高亮
    private val elderMedsNow = java.time.LocalDateTime.of(2026, 11, 11, 12, 10)

    @Test
    fun elderHomeMeds() = shotLarge(
        "elder_home_meds",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        io.github.xiaohaoz1.jiarenxia.ui.elder.ElderHomeContent(
            state = elderHomeMedsState(), onOpenPerson = {}, onCall = {},
            onExit = {}, now = elderMedsNow)
    }.also {
        assertTexts("今天要吃的药", "08:00", "二甲双胍 1 片",
            "12:00", "阿司匹林 1 片、布洛芬 1 粒",
            "20:00", "维生素C 1 片（疗程待确认）")
    }

    @Test
    fun elderHomeMedsJiashu() = shotLarge("elder_home_meds_jiashu") {
        io.github.xiaohaoz1.jiarenxia.ui.elder.ElderHomeContent(
            state = elderHomeMedsState(), onOpenPerson = {}, onCall = {},
            onExit = {}, now = elderMedsNow)
    }.also {
        assertTexts("今天要吃的药", "08:00", "12:00", "20:00")
    }

    @Test
    fun elderHomeEmpty() = shotLarge("elder_home_empty") {
        io.github.xiaohaoz1.jiarenxia.ui.elder.ElderHomeContent(
            state = elderHomeState(emptyUpcoming = true),
            onOpenPerson = {}, onCall = {}, onExit = {})
    }.also { assertTexts("最近 30 天没有要记的日子") }

    @Test
    fun elderHomeEmptyClassic() = shotLarge(
        "elder_home_empty_classic",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        io.github.xiaohaoz1.jiarenxia.ui.elder.ElderHomeContent(
            state = elderHomeState(emptyUpcoming = true),
            onOpenPerson = {}, onCall = {}, onExit = {})
    }.also { assertTexts("最近 30 天没有要记的日子") }

    @Test
    fun elderPerson() = shotLarge("elder_person") {
        io.github.xiaohaoz1.jiarenxia.ui.elder.ElderPersonContent(
            state = io.github.xiaohaoz1.jiarenxia.ui.elder.ElderPersonUiState(
                loading = false, invalid = false,
                name = "杨秀英", label = "妈妈", hasTitle = true,
                phones = listOf(
                    io.github.xiaohaoz1.jiarenxia.ui.elder.ElderPhoneRow(
                        "13900002222", true),
                    io.github.xiaohaoz1.jiarenxia.ui.elder.ElderPhoneRow(
                        "13600004444", false)
                ),
                birthdays = listOf(
                    io.github.xiaohaoz1.jiarenxia.ui.elder.ElderBirthdayRow(
                        "农历八月十五", "还有 42 天"),
                    io.github.xiaohaoz1.jiarenxia.ui.elder.ElderBirthdayRow(
                        "阳历 5月20日，1960 年生", "今天")
                )
            ),
            onBack = {})
    }.also {
        assertTexts("妈妈", "杨秀英", "电话", "生日", "主号码", "打电话",
            "农历八月十五", "阳历 5月20日，1960 年生")
    }

    @Test
    fun elderPersonClassic() = shotLarge(
        "elder_person_classic",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        io.github.xiaohaoz1.jiarenxia.ui.elder.ElderPersonContent(
            state = io.github.xiaohaoz1.jiarenxia.ui.elder.ElderPersonUiState(
                loading = false, invalid = false,
                name = "杨秀英", label = "妈妈", hasTitle = true,
                phones = listOf(
                    io.github.xiaohaoz1.jiarenxia.ui.elder.ElderPhoneRow(
                        "13900002222", true),
                    io.github.xiaohaoz1.jiarenxia.ui.elder.ElderPhoneRow(
                        "13600004444", false)
                ),
                birthdays = listOf(
                    io.github.xiaohaoz1.jiarenxia.ui.elder.ElderBirthdayRow(
                        "农历八月十五", "还有 42 天"),
                    io.github.xiaohaoz1.jiarenxia.ui.elder.ElderBirthdayRow(
                        "阳历 5月20日，1960 年生", "今天")
                )
            ),
            onBack = {})
    }.also { assertTexts("妈妈", "电话", "生日") }

    /** 外观与字号页（含长辈模式组）。 */
    @Test
    fun elderAppearance() = shotLarge("elder_appearance") {
        io.github.xiaohaoz1.jiarenxia.ui.mine.AppearanceContent(
            themeMode = io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode.LIGHT,
            themeStyle = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.JIASHU,
            widgetThemeMode = io.github.xiaohaoz1.jiarenxia.data.settings.WidgetThemeMode.SYSTEM,
            fontScaleFactor = 1.0f, elderMode = false,
            onThemeMode = {}, onThemeStyle = {}, onWidgetThemeMode = {},
            onFontScale = {}, onElderMode = {}, onBack = {})
    }.also { assertTexts("长辈模式", "字体大小") }

    @Test
    fun elderAppearanceClassic() = shotLarge(
        "elder_appearance_classic",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        io.github.xiaohaoz1.jiarenxia.ui.mine.AppearanceContent(
            themeMode = io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode.LIGHT,
            themeStyle = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC,
            widgetThemeMode = io.github.xiaohaoz1.jiarenxia.data.settings.WidgetThemeMode.SYSTEM,
            fontScaleFactor = 1.0f, elderMode = false,
            onThemeMode = {}, onThemeStyle = {}, onWidgetThemeMode = {},
            onFontScale = {}, onElderMode = {}, onBack = {})
    }.also { assertTexts("长辈模式", "字体大小") }

    /** 交互验证：点「长辈模式」行弹出确认框，「开启」回调 onElderMode(true)。 */
    @Test
    fun elderConfirmDialogOpens() {
        var confirmed: Boolean? = null
        composeRule.setContent {
            JiaRenXiaTheme(
                style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.JIASHU
            ) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                ) {
                    io.github.xiaohaoz1.jiarenxia.ui.mine.AppearanceContent(
                        themeMode = io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode.LIGHT,
                        themeStyle = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.JIASHU,
                        widgetThemeMode = io.github.xiaohaoz1.jiarenxia.data.settings.WidgetThemeMode.SYSTEM,
                        fontScaleFactor = 1.0f, elderMode = false,
                        onThemeMode = {}, onThemeStyle = {}, onWidgetThemeMode = {},
                        onFontScale = {}, onElderMode = { confirmed = it }, onBack = {})
                }
            }
        }
        // 组标题与行标题同为“长辈模式”：点有点击动作的那行
        composeRule.onNode(hasText("长辈模式") and hasClickAction()).performClick()
        composeRule.onNodeWithText("开启长辈模式？").assertExists()
        composeRule.onNodeWithText("开启").performClick()
        composeRule.waitForIdle()
        check(confirmed == true) { "点「开启」应回调 onElderMode(true)，实际: $confirmed" }
    }

    /** 长辈模式开启确认框截图：AlertDialog 弹层无法被截图，渲染同款卡片内容。 */
    private fun elderConfirmShot(
        name: String,
        style: io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle,
        large: Boolean = true
    ) {
        // 同 customTitleDialog 的做法：遮罩 + 圆角卡片 + 标题/正文/按钮行
        val card: @Composable () -> Unit = {
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
                        Text(
                            "开启长辈模式？",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.height(16.dp))
                        io.github.xiaohaoz1.jiarenxia.ui.mine
                            .ElderModeConfirmDialogBody()
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {}) { Text("取消") }
                            TextButton(onClick = {}) { Text("开启") }
                        }
                    }
                }
            }
        }
        if (large) shotLarge(name, style = style, content = card)
        else shot(name, style = style, content = card)
        assertTexts("开启长辈模式？", "只保留看家人", "退出长辈模式",
            "取消", "开启")
    }

    @Test
    fun elderConfirmDialog() = elderConfirmShot(
        "elder_confirm",
        io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.JIASHU)

    @Test
    fun elderConfirmDialogClassic() = elderConfirmShot(
        "elder_confirm_classic",
        io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC)

    // ---------- 生日祝福对话框（FAMILY_AFFAIRS 22） ----------

    /**
     * 祝福对话框截图：AlertDialog 弹层无法被截图，渲染同款卡片内容
     * （同 customTitleDialog / elderConfirmShot 的做法）。
     */
    private fun blessingDialogShot(
        name: String,
        style: io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle,
        number: String?
    ) {
        shot(name, style = style) {
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
                        Text(
                            "给妈妈发祝福",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.height(16.dp))
                        io.github.xiaohaoz1.jiarenxia.ui.reminder
                            .BlessingDialogBody(
                                number = number,
                                text = io.github.xiaohaoz1.jiarenxia.domain
                                    .BirthdayBlessing.defaultText("妈妈", true),
                                onTextChange = {}
                            )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (number != null) {
                                TextButton(onClick = {}) { Text("复制") }
                            }
                            TextButton(onClick = {}) { Text("取消") }
                            TextButton(onClick = {}) {
                                Text(if (number != null) "发短信" else "复制")
                            }
                        }
                    }
                }
            }
        }.also {
            assertTexts("给妈妈发祝福", "生日快乐", "取消")
        }
    }

    @Test
    fun blessingDialog() = blessingDialogShot(
        "blessing_dialog",
        io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.JIASHU,
        number = "13912345678")

    @Test
    fun blessingDialogClassic() = blessingDialogShot(
        "blessing_dialog_classic",
        io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC,
        number = "13912345678")

    @Test
    fun blessingDialogNoPhone() = blessingDialogShot(
        "blessing_dialog_nophone",
        io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.JIASHU,
        number = null)

    @Test
    fun blessingDialogNoPhoneClassic() = blessingDialogShot(
        "blessing_dialog_nophone_classic",
        io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC,
        number = null)

    // ---------- 孕期记录截图（FAMILY_AFFAIRS 26）----------

    /** 演示孕期：wife（李雪，我的配偶）的一次孕期记录。 */
    private fun demoPregnancy(
        due: LocalDate,
        lmp: LocalDate? = null,
        status: io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus =
            io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus.ACTIVE,
        endedOn: LocalDate? = null
    ) = io.github.xiaohaoz1.jiarenxia.core.model.Pregnancy(
        id = "pg-demo", personId = "wife", dueDate = due, lmpDate = lmp,
        status = status, endedOn = endedOn, createdAt = 1L, updatedAt = 1L
    )

    /** 产检事项样例（不重复，到期日 = anchorDate）。 */
    private fun prenatalTask(
        id: String, anchor: LocalDate,
        done: Boolean = false, archived: Boolean = false
    ) = io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask(
        id = id, personId = "wife",
        kind = io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.CHECKUP,
        title = "妻子的产检", anchorDate = anchor,
        repeat = io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.NONE,
        periodIndex = 0, done = done, enabled = true,
        offsets = setOf(0), pregnancyId = "pg-demo", archived = archived
    )

    @Test
    fun pregnancyCard() {
        // 孕 32 周 3 天：预产期 = 今天 + 53 天；末次月经 = 预产期 - 280 天
        val due = Demo.today.plusDays(53)
        shot("pregnancy_card") {
            Column(Modifier.padding(16.dp)) {
                PregnancyCard(
                    pregnancy = demoPregnancy(due, due.minusDays(280)),
                    nextCheckup = prenatalTask("pt1", Demo.today.plusDays(3)),
                    today = Demo.today,
                    onEditDates = {}, onEnd = {}, onDelete = {},
                    onOpenTask = {}, onAddCheckup = {}
                )
            }
        }.also {
            assertTexts("孕 32 周 3 天", "下次产检", "记下次产检")
        }
    }

    @Test
    fun pregnancyCardOverdue() {
        // 已过预产期 3 天；无末次月经；下次产检已逾期 2 天
        shot("pregnancy_card_overdue") {
            Column(Modifier.padding(16.dp)) {
                PregnancyCard(
                    pregnancy = demoPregnancy(Demo.today.minusDays(3)),
                    nextCheckup = prenatalTask("pt1", Demo.today.minusDays(2)),
                    today = Demo.today,
                    onEditDates = {}, onEnd = {}, onDelete = {},
                    onOpenTask = {}, onAddCheckup = {}
                )
            }
        }.also {
            assertTexts("孕 40 周 3 天", "已过 3 天", "已逾期 2 天")
            assertNoTexts("末次月经")
        }
    }

    @Test
    fun pregnancyCardNoneCheckup() {
        val due = Demo.today.plusDays(53)
        shot("pregnancy_card_none_checkup") {
            Column(Modifier.padding(16.dp)) {
                PregnancyCard(
                    pregnancy = demoPregnancy(due, due.minusDays(280)),
                    nextCheckup = null,
                    today = Demo.today,
                    onEditDates = {}, onEnd = {}, onDelete = {},
                    onOpenTask = {}, onAddCheckup = {}
                )
            }
        }.also {
            assertTexts("孕 32 周 3 天", "还没有记下次产检")
        }
    }

    @Test
    fun pregnancyDialogDue() {
        // 新建「记录孕期」：填预产期，已选合法日期 → 预览孕周
        val due = Demo.today.plusDays(53)
        shot("pregnancy_dialog_due") {
            PregnancyDateDialog(
                title = "记录孕期",
                mode = PregnancyInputMode.PRE_DUE,
                existing = null, today = Demo.today,
                onSave = { _, _ -> }, onDismiss = {},
                initialPicked = due
            )
        }.also {
            assertTexts("记录孕期", "填预产期", "填末次月经",
                "现在孕 32 周 3 天", "保存")
        }
    }

    @Test
    fun pregnancyDialogLmpError() {
        // 填末次月经：选了未来日期 → 非法提示
        shot("pregnancy_dialog_lmp_error") {
            PregnancyDateDialog(
                title = "记录孕期",
                mode = PregnancyInputMode.LMP,
                existing = null, today = Demo.today,
                onSave = { _, _ -> }, onDismiss = {},
                initialPicked = Demo.today.plusDays(1)
            )
        }.also {
            assertTexts("末次月经要在 46 周前到今天之间")
        }
    }

    @Test
    fun pregnancyEndDialog() {
        shot("pregnancy_end_dialog") {
            PregnancyEndDialog(onConfirm = {}, onDismiss = {})
        }.also {
            assertTexts("结束这条孕期记录？", "结束记录", "取消")
        }
    }

    @Test
    fun pregnancyHistoryCard() {
        // 孕期记录卡：一条已结束 + 一条已出生
        val ended = demoPregnancy(
            Demo.today.minusDays(200), status =
                io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus.ENDED,
            endedOn = Demo.today.minusDays(30))
        val born = demoPregnancy(
            Demo.today.minusDays(400), status =
                io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus.BORN,
            endedOn = Demo.today.minusDays(120))
        shot("pregnancy_history_card") {
            Column(Modifier.padding(16.dp)) {
                PregnancyHistoryCard(
                    pregnancies = listOf(ended, born), onOpen = {})
            }
        }.also {
            assertTexts("孕期记录", "预产期", "已结束", "宝宝已出生")
        }
    }

    @Test
    fun pregnancyHistoryDialog() {
        // 历史详情：已结束孕期 + 已办/已归档产检行
        val ended = demoPregnancy(
            Demo.today.minusDays(200), lmp = Demo.today.minusDays(480),
            status = io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus.ENDED,
            endedOn = Demo.today.minusDays(30))
        shot("pregnancy_history_dialog") {
            PregnancyHistoryDialog(
                pregnancy = ended,
                tasks = listOf(
                    prenatalTask("h1", Demo.today.minusDays(60), done = true),
                    prenatalTask("h2", Demo.today.minusDays(40), archived = true)
                ),
                today = Demo.today,
                onDelete = {}, onDismiss = {}
            )
        }.also {
            assertTexts("孕期记录", "已结束于", "产检", "已办", "已归档",
                "删除这条记录", "关闭")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun importantDatesAddDue() = shot("important_dates_add_due") {
        // “我”的配偶（李雪）详情页：「重要日期」卡末尾出现“添加预产期”
        val base = Demo.detailOf("wife")
        PersonDetailContent(
            lunar = Demo.lunar,
            st = base.copy(canRecordPregnancy = true),
            today = Demo.today, snackbar = SnackbarHostState(),
            onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
            onDial = {}, onSetReminder = { _, _ -> },
            onSetReminderOffsets = { _, _ -> }, onSetCustomTitle = {},
            onDeletePerson = {},
            onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
        )
    }.also {
        assertTexts("重要日期", "添加预产期")
    }

    @Test
    fun taskEditPrenatal() = shot("task_edit_prenatal") {
        // 产检事项编辑：模板锁“产检”、关联人锁定、隐藏重复、顶部说明
        io.github.xiaohaoz1.jiarenxia.ui.task.TaskEditContent(
            st = io.github.xiaohaoz1.jiarenxia.ui.task.TaskEditUiState(
                taskId = null,
                kind = io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.CHECKUP,
                personId = "wife",
                pregnancyId = "pg-demo",
                title = "妻子的产检",
                anchorDate = Demo.today.plusDays(10),
                repeat = io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.NONE,
                offsets = setOf(1, 0),
                persons = listOf(
                    io.github.xiaohaoz1.jiarenxia.ui.task.TaskPersonOption(
                        "wife", "李雪", "李雪（妻子）")
                )
            ),
            cb = io.github.xiaohaoz1.jiarenxia.ui.task.TaskEditCallbacks(),
            onDone = {}
        )
    }.also {
        assertTexts("产检", "这条产检属于孕期记录，孕期结束后会自动归档。")
        assertNoTexts("重复")
    }

    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun familyCorePregnancy() {
        // 经典家庭页：配偶行显示孕周状态（primary 色）
        val preg = demoPregnancy(Demo.today.plusDays(53))
        shot(
            "family_core_pregnancy",
            style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
        ) {
            FamilyContent(
                Demo.familyState().copy(
                    pregnancyByPerson = mapOf("wife" to preg)
                ),
                Demo.lunar, {}, {}, {}, {}, {}
            )
        }.also {
            assertTexts("孕 32 周 3 天")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun familyCorePregnancyJiashu() {
        // 家书家庭页：同一孕周状态
        val preg = demoPregnancy(Demo.today.plusDays(53))
        shot("family_core_pregnancy_jiashu") {
            FamilyContent(
                Demo.familyState().copy(
                    pregnancyByPerson = mapOf("wife" to preg)
                ),
                Demo.lunar, {}, {}, {}, {}, {}
            )
        }.also {
            assertTexts("孕 32 周 3 天")
        }
    }

    @Test
    fun pregnancyCardLg() {
        val due = Demo.today.plusDays(53)
        shotLarge("pregnancy_card") {
            Column(Modifier.padding(16.dp)) {
                PregnancyCard(
                    pregnancy = demoPregnancy(due, due.minusDays(280)),
                    nextCheckup = prenatalTask("pt1", Demo.today.plusDays(3)),
                    today = Demo.today,
                    onEditDates = {}, onEnd = {}, onDelete = {},
                    onOpenTask = {}, onAddCheckup = {}
                )
            }
        }
    }

    @Test
    fun pregnancyDialogDueLg() {
        val due = Demo.today.plusDays(53)
        shotLarge("pregnancy_dialog_due") {
            PregnancyDateDialog(
                title = "记录孕期",
                mode = PregnancyInputMode.PRE_DUE,
                existing = null, today = Demo.today,
                onSave = { _, _ -> }, onDismiss = {},
                initialPicked = due
            )
        }
    }

    // ---------- 清单 / 待产包截图（FAMILY_AFFAIRS 26 第 2 批）----------

    /** 由模板生成演示待产包：checked 下标的条目置已勾；dropTitles 删除指定条目。 */
    private fun demoBirthBag(
        checked: Set<Int> = emptySet(),
        dropTitles: Set<String> = emptySet()
    ): io.github.xiaohaoz1.jiarenxia.data.ChecklistWithItemsModel {
        var i = 0
        val items = io.github.xiaohaoz1.jiarenxia.domain.checklist
            .ChecklistTemplates.BIRTH_BAG_ITEMS
            .filter { it.title !in dropTitles }
            .map { t ->
                val idx = i++
                io.github.xiaohaoz1.jiarenxia.core.model.ChecklistItem(
                    id = "ci-" + idx, checklistId = "cl-demo",
                    groupName = t.groupName, title = t.title, note = t.note,
                    checked = idx in checked, sortOrder = idx,
                    personId = "wife", createdAt = 1, updatedAt = 1
                )
            }
        return io.github.xiaohaoz1.jiarenxia.data.ChecklistWithItemsModel(
            checklist = io.github.xiaohaoz1.jiarenxia.core.model.Checklist(
                id = "cl-demo",
                kind = io.github.xiaohaoz1.jiarenxia.core.model
                    .ChecklistKind.BIRTH_BAG,
                title = "待产包", personId = "wife",
                pregnancyId = "pg-demo", createdAt = 1, updatedAt = 1
            ),
            items = items
        )
    }

    private fun checklistState(
        model: io.github.xiaohaoz1.jiarenxia.data.ChecklistWithItemsModel
    ) = io.github.xiaohaoz1.jiarenxia.ui.checklist.ChecklistUiState(
        loading = false, model = model
    )

    @Test
    @Config(qualifiers = "w412dp-h2400dp-xxhdpi")
    fun checklistBirthBag() {
        // 前 12 条已勾（跨三个组、含多条 note 行），进度“已备 12 / 28”
        shot("checklist_birth_bag") {
            io.github.xiaohaoz1.jiarenxia.ui.checklist.ChecklistContent(
                st = checklistState(demoBirthBag(checked = (0..11).toSet())),
                cb = io.github.xiaohaoz1.jiarenxia.ui.checklist
                    .ChecklistCallbacks(),
                onDone = {}
            )
        }.also {
            assertTexts("待产包", "已备 12 / 28", "证件资料", "妈妈用品",
                "宝宝用品", "查看出处", "母子健康手册", "各地名称可能不同")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h2400dp-xxhdpi")
    fun checklistBirthBagDone() {
        shot("checklist_birth_bag_done") {
            io.github.xiaohaoz1.jiarenxia.ui.checklist.ChecklistContent(
                st = checklistState(
                    demoBirthBag(checked = (0..27).toSet())),
                cb = io.github.xiaohaoz1.jiarenxia.ui.checklist
                    .ChecklistCallbacks(),
                onDone = {}
            )
        }.also {
            assertTexts("待产包", "都备齐了")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h2400dp-xxhdpi")
    fun checklistEmptyGroup() {
        // 「证件资料」整组删空：卡内只剩“这一组还没有东西”+“添加”
        shot("checklist_empty_group") {
            io.github.xiaohaoz1.jiarenxia.ui.checklist.ChecklistContent(
                st = checklistState(
                    demoBirthBag(dropTitles = setOf(
                        "夫妻双方身份证", "医保卡", "母子健康手册",
                        "产科病历和孕期检查资料", "住院押金"))),
                cb = io.github.xiaohaoz1.jiarenxia.ui.checklist
                    .ChecklistCallbacks(),
                onDone = {}
            )
        }.also {
            assertTexts("证件资料", "这一组还没有东西", "妈妈用品", "宝宝用品")
        }
    }

    @Test
    fun checklistAddDialog() {
        // AlertDialog 弹层 + 输入框在 Robolectric 下无法进入 idle
        // （同 blessingDialogShot 的做法）：渲染同款卡片内容。
        shot("checklist_add_dialog") {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0x66000000)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape
                        .RoundedCornerShape(28.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier.padding(28.dp).fillMaxWidth()
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            "添加到「证件资料」",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(16.dp))
                        // 初始空输入：保存禁用（与真对话框初始态一致）
                        androidx.compose.material3.OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            label = { Text("名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {}) { Text("取消") }
                            TextButton(enabled = false, onClick = {}) {
                                Text("保存")
                            }
                        }
                    }
                }
            }
        }.also {
            assertTexts("添加到「证件资料」", "保存", "取消")
        }
    }

    @Test
    fun checklistDeleteDialog() {
        shot("checklist_delete_dialog") {
            io.github.xiaohaoz1.jiarenxia.ui.checklist.ChecklistDeleteDialog(
                title = "待产包",
                onConfirm = {}, onDismiss = {}
            )
        }.also {
            assertTexts("删除待产包清单？", "删除", "取消")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h2400dp-xxhdpi")
    fun checklistBirthBagLg() {
        shotLarge("checklist_birth_bag") {
            io.github.xiaohaoz1.jiarenxia.ui.checklist.ChecklistContent(
                st = checklistState(demoBirthBag(checked = (0..11).toSet())),
                cb = io.github.xiaohaoz1.jiarenxia.ui.checklist
                    .ChecklistCallbacks(),
                onDone = {}
            )
        }
    }

    private fun demoBagProgress(
        checked: Int, total: Int = 28
    ) = io.github.xiaohaoz1.jiarenxia.data.ChecklistProgress(
        checklist = io.github.xiaohaoz1.jiarenxia.core.model.Checklist(
            id = "cl-demo",
            kind = io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind
                .BIRTH_BAG,
            title = "待产包", personId = "wife", pregnancyId = "pg-demo",
            createdAt = 1, updatedAt = 1
        ),
        checkedCount = checked, totalCount = total
    )

    @Test
    fun pregnancyCardBagNone() {
        // 还没有清单：值“还没准备”
        val due = Demo.today.plusDays(53)
        shot("pregnancy_card_bag_none") {
            Column(Modifier.padding(16.dp)) {
                PregnancyCard(
                    pregnancy = demoPregnancy(due, due.minusDays(280)),
                    nextCheckup = null, today = Demo.today,
                    onEditDates = {}, onEnd = {}, onDelete = {},
                    onOpenTask = {}, onAddCheckup = {},
                    birthBag = null
                )
            }
        }.also {
            assertTexts("待产包", "还没准备")
        }
    }

    @Test
    fun pregnancyCardBagProgress() {
        val due = Demo.today.plusDays(53)
        shot("pregnancy_card_bag_progress") {
            Column(Modifier.padding(16.dp)) {
                PregnancyCard(
                    pregnancy = demoPregnancy(due, due.minusDays(280)),
                    nextCheckup = null, today = Demo.today,
                    onEditDates = {}, onEnd = {}, onDelete = {},
                    onOpenTask = {}, onAddCheckup = {},
                    birthBag = demoBagProgress(12)
                )
            }
        }.also {
            assertTexts("待产包", "已备 12 / 28")
        }
    }

    @Test
    fun pregnancyCardBagDone() {
        val due = Demo.today.plusDays(53)
        shot("pregnancy_card_bag_done") {
            Column(Modifier.padding(16.dp)) {
                PregnancyCard(
                    pregnancy = demoPregnancy(due, due.minusDays(280)),
                    nextCheckup = null, today = Demo.today,
                    onEditDates = {}, onEnd = {}, onDelete = {},
                    onOpenTask = {}, onAddCheckup = {},
                    birthBag = demoBagProgress(28)
                )
            }
        }.also {
            assertTexts("待产包", "都备齐了")
        }
    }

    @Test
    fun pregnancyHistoryDialogBag() {
        // 历史详情：产检小节后出现「待产包」入口
        val ended = demoPregnancy(
            Demo.today.minusDays(200), lmp = Demo.today.minusDays(480),
            status = io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus
                .ENDED,
            endedOn = Demo.today.minusDays(30))
        shot("pregnancy_history_dialog_bag") {
            PregnancyHistoryDialog(
                pregnancy = ended,
                tasks = listOf(
                    prenatalTask("h1", Demo.today.minusDays(60), done = true)
                ),
                today = Demo.today,
                birthBag = demoBagProgress(12),
                onDelete = {}, onDismiss = {}
            )
        }.also {
            assertTexts("孕期记录", "产检", "待产包",
                "查看待产包（已备 12 / 28）")
        }
    }

    // ---------- 第 3 批：宝宝出生了 / 新生宝宝 / 出生后要办的事 ----------

    /** 演示宝宝人物：杨明与李雪的儿子，小宝，2026-09-15 出生（今天“出生第 18 天”）。 */
    private fun babyGraph(): FamilyGraph {
        val base = Demo.demoGraph()
        val baby = Demo.p("baby", "小宝", Gender.MALE)
        return FamilyGraph(
            base.persons + baby,
            base.relationships +
                Demo.rel("me", "baby", RelationshipType.PARENT) +
                Demo.rel("wife", "baby", RelationshipType.PARENT),
            base.birthdays + ("baby" to PartialDate(2026, 9, 15)),
            base.birthdayValues + ("baby" to listOf(
                BirthdayValue(PartialDate(2026, 9, 15))))
        )
    }

    /** 与 [Demo.demoOverview] 同一组装方式，数据换成含宝宝的图。 */
    private fun babyOverview(): FamilyOverview {
        val graph = babyGraph()
        val engine = KinshipEngine()
        val self = graph.persons.first { it.isSelf }
        val results = engine.resolveAll(graph, self.id)
        val byId = graph.persons.associate { person ->
            person.id to PersonOverview(
                person, results.getValue(person.id),
                graph.birthdayValues[person.id] ?: emptyList(),
                solarBirthday = graph.birthdays[person.id]
            )
        }
        val rest = byId.values.filter { it.person.id != self.id }
        return FamilyOverview(
            self = byId.getValue(self.id),
            graph = graph,
            byId = byId,
            core = FamilyOrdering.core(
                rest.filter { it.result.coreGroup != null }, byId),
            others = FamilyOrdering.others(
                rest.filter { it.result.coreGroup == null })
        )
    }

    /** 宝宝“我”的儿子”的详情态：页头年龄经 AgeMath → BabyMath 得出。 */
    private fun babyDetail(): PersonDetailUiState {
        val ov = babyOverview()
        val o = ov.byId.getValue("baby")
        val birthdays = (ov.graph.birthdayValues["baby"] ?: emptyList())
            .associate { v ->
                val dateId = "d-baby-" + v.calendar.name
                v.calendar to io.github.xiaohaoz1.jiarenxia.data
                    .BirthdayDetail(
                        dateId = dateId, value = v,
                        reminder = ReminderPreference(
                            "rp-$dateId", dateId, enabled = true)
                    )
            }
        val rels = ov.graph.relationships.filter {
            it.sourcePersonId == "baby" || it.targetPersonId == "baby"
        }
        val engine = KinshipEngine()
        val rows = rels.map { r ->
            val otherId = if (r.sourcePersonId == "baby") r.targetPersonId
            else r.sourcePersonId
            RelationRow(
                r, ov.graph.persons.first { it.id == otherId },
                engine.directRelationLabel(ov.graph, "baby", r)
            )
        }
        val ageText = io.github.xiaohaoz1.jiarenxia.domain.AgeMath.ageText(
            o.birthdays, o.person.livingStatus, null, Demo.today, Demo.lunar)
        // 新生宝宝卡：与 newbornCardOf 同一口径（出生第 18 天，在窗口内、
        // 无产后清单 → 不显示「出生后要办的事」行）
        val birth = java.time.LocalDate.of(2026, 9, 15)
        val babyMath = io.github.xiaohaoz1.jiarenxia.domain.baby.BabyMath
        return PersonDetailUiState(
            loading = false,
            detail = PersonDetail(
                person = o.person, phones = emptyList(),
                birthdays = birthdays, relationships = rels
            ),
            overview = o,
            familyById = ov.byId,
            relationRows = rows,
            relatedIds = rels.flatMap {
                listOf(it.sourcePersonId, it.targetPersonId)
            }.toSet(),
            ageText = ageText,
            newbornCard = io.github.xiaohaoz1.jiarenxia.ui.person
                .NewbornCardState(
                    birthDate = birth,
                    fullMonth = babyMath.fullMonthDate(birth),
                    hundredDays = babyMath.hundredDaysDate(birth),
                    fullMonthByBirth = babyMath.fullMonthDate(birth),
                    hundredByBirth = babyMath.hundredDaysDate(birth),
                    fullMonthOverride = false,
                    hundredOverride = false,
                    postnatalChecklistId = null,
                    postnatalDone = 0, postnatalTotal = 0
                )
        )
    }

    private fun birthUiState(
        babies: List<io.github.xiaohaoz1.jiarenxia.ui.pregnancy
            .BirthBabyDraft>,
        spouseChecked: Boolean = false,
        pickingKey: Int? = null,
        pickerItems: List<io.github.xiaohaoz1.jiarenxia.domain
            .PersonPickItem> = emptyList(),
        done: List<io.github.xiaohaoz1.jiarenxia.ui.pregnancy.DoneBaby>? =
            null,
        checklistId: String? = null,
        kinLines: List<String> = emptyList()
    ) = io.github.xiaohaoz1.jiarenxia.ui.pregnancy.BirthUiState(
        loading = false,
        babies = babies,
        momId = "wife", momName = "李雪",
        spouseId = "me", spouseLabel = "杨明（丈夫）",
        spouseIsFather = true, spouseChecked = spouseChecked,
        pickingKey = pickingKey, pickerItems = pickerItems,
        done = done, checklistId = checklistId, kinLines = kinLines
    )

    private fun newBabyDraft(
        key: Int, name: String, gender: Gender,
        date: LocalDate = Demo.today, minute: Int? = null
    ) = io.github.xiaohaoz1.jiarenxia.ui.pregnancy.BirthBabyDraft(
        key = key, isNew = true, name = name, gender = gender,
        birthDate = date, birthMinute = minute
    )

    @Test
    fun pregnancyCardBirthButton() {
        shot("pregnancy_card_birth_button") {
            Column(Modifier.padding(16.dp)) {
                PregnancyCard(
                    pregnancy = demoPregnancy(Demo.today.plusDays(53),
                        Demo.today.plusDays(53).minusDays(280)),
                    nextCheckup = null, today = Demo.today,
                    onEditDates = {}, onEnd = {}, onDelete = {},
                    onOpenTask = {}, onAddCheckup = {}
                )
            }
        }.also {
            assertTexts("孕 32 周 3 天", "记下次产检", "宝宝出生了")
        }
    }

    @Test
    fun birthFormNew() {
        // 一个新建宝宝已填全（含时刻 08:30），勾选另一位父母
        shot("birth_form_new") {
            io.github.xiaohaoz1.jiarenxia.ui.pregnancy.BirthContent(
                st = birthUiState(
                    babies = listOf(newBabyDraft(
                        0, "小宝", Gender.MALE, minute = 8 * 60 + 30)),
                    spouseChecked = true
                ),
                cb = io.github.xiaohaoz1.jiarenxia.ui.pregnancy
                    .BirthCallbacks(),
                onBack = {},
                snackbar = SnackbarHostState()
            )
        }.also {
            assertTexts("宝宝出生了", "新建", "已经在家谱中",
                "名字（可以先写小名）", "出生日期", "出生时刻",
                "也是宝宝的父亲", "完成")
        }
    }

    @Test
    fun birthFormTwins() {
        // 新建 + 已在家谱中各一个（第二个宝宝卡带“去掉”）
        shot("birth_form_twins") {
            io.github.xiaohaoz1.jiarenxia.ui.pregnancy.BirthContent(
                st = birthUiState(
                    babies = listOf(
                        newBabyDraft(0, "小宝", Gender.MALE,
                            minute = 8 * 60 + 30),
                        io.github.xiaohaoz1.jiarenxia.ui.pregnancy
                            .BirthBabyDraft(
                                key = 1, isNew = false,
                                existingId = "baby2",
                                existingLabel = "小贝（女儿）",
                                birthDate = Demo.today.minusDays(1),
                                existingHasSolar = false
                            )
                    )
                ),
                cb = io.github.xiaohaoz1.jiarenxia.ui.pregnancy
                    .BirthCallbacks(),
                onBack = {},
                snackbar = SnackbarHostState()
            )
        }.also {
            assertTexts("宝宝", "第 2 个宝宝", "去掉", "小贝（女儿）",
                "选择家人")
        }
    }

    @Test
    fun birthFormPickDialog() {
        // 「已经在家谱中」选人对话框（候选 ≤10 时不出搜索框，可正常渲染）
        shot("birth_form_pick_dialog") {
            io.github.xiaohaoz1.jiarenxia.ui.pregnancy.BirthContent(
                st = birthUiState(
                    babies = listOf(
                        newBabyDraft(0, "小宝", Gender.MALE),
                        io.github.xiaohaoz1.jiarenxia.ui.pregnancy
                            .BirthBabyDraft(
                                key = 1, isNew = false,
                                birthDate = Demo.today
                            )
                    ),
                    pickingKey = 1,
                    pickerItems = listOf(
                        io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem(
                            "baby2", "小贝", "小贝（女儿）"),
                        io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem(
                            "dau", "杨小雨", "杨小雨（女儿）"),
                        io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem(
                            "bro", "杨帆", "杨帆（弟弟）"),
                        io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem(
                            "cousin", "杨磊", "杨磊（堂哥）")
                    )
                ),
                cb = io.github.xiaohaoz1.jiarenxia.ui.pregnancy
                    .BirthCallbacks(),
                onBack = {},
                snackbar = SnackbarHostState()
            )
        }.also {
            assertTexts("选择家人", "小贝（女儿）", "杨小雨（女儿）",
                "杨帆（弟弟）", "杨磊（堂哥）", "取消")
        }
    }

    @Test
    fun birthDone() {
        shot("birth_done") {
            io.github.xiaohaoz1.jiarenxia.ui.pregnancy.BirthContent(
                st = birthUiState(
                    babies = emptyList(),
                    done = listOf(
                        io.github.xiaohaoz1.jiarenxia.ui.pregnancy.DoneBaby(
                            "小宝", "儿子", Demo.today, 8 * 60 + 30)
                    ),
                    checklistId = "cl-post",
                    // 与 ViewModel 同一函数：以宝宝为观察者的真实称谓行
                    kinLines = io.github.xiaohaoz1.jiarenxia.ui.pregnancy
                        .babyKinLines(babyGraph(), "baby")
                ),
                cb = io.github.xiaohaoz1.jiarenxia.ui.pregnancy
                    .BirthCallbacks(),
                onBack = {},
                snackbar = SnackbarHostState()
            )
        }.also {
            assertTexts("欢迎宝宝来到我们家！", "小宝（儿子）",
                "从宝宝的角度看", "父亲 · 杨明", "母亲 · 李雪",
                "爷爷 · 杨建国", "姥爷 · 李国强", "报喜",
                "看看出生后要办的事", "分享给家人", "完成")
        }
    }

    @Test
    fun birthDoneLg() {
        shotLarge("birth_done") {
            io.github.xiaohaoz1.jiarenxia.ui.pregnancy.BirthContent(
                st = birthUiState(
                    babies = emptyList(),
                    done = listOf(
                        io.github.xiaohaoz1.jiarenxia.ui.pregnancy.DoneBaby(
                            "小宝", "儿子", Demo.today, 8 * 60 + 30)
                    ),
                    checklistId = "cl-post",
                    kinLines = io.github.xiaohaoz1.jiarenxia.ui.pregnancy
                        .babyKinLines(babyGraph(), "baby")
                ),
                cb = io.github.xiaohaoz1.jiarenxia.ui.pregnancy
                    .BirthCallbacks(),
                onBack = {},
                snackbar = SnackbarHostState()
            )
        }
    }

    @Test
    fun birthFormNewLg() {
        shotLarge("birth_form_new") {
            io.github.xiaohaoz1.jiarenxia.ui.pregnancy.BirthContent(
                st = birthUiState(
                    babies = listOf(newBabyDraft(
                        0, "小宝", Gender.MALE, minute = 8 * 60 + 30)),
                    spouseChecked = true
                ),
                cb = io.github.xiaohaoz1.jiarenxia.ui.pregnancy
                    .BirthCallbacks(),
                onBack = {},
                snackbar = SnackbarHostState()
            )
        }
    }

    /** 报喜对话框同款卡片（AlertDialog + 输入框在 Robolectric 下不 idle）。 */
    private fun birthNewsShot(
        name: String,
        text: String,
        weight: String,
        showWeight: Boolean
    ) {
        shot(name) {
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
                        Text("报喜", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        io.github.xiaohaoz1.jiarenxia.ui.pregnancy
                            .BirthNewsFields(
                                text = text, onText = {},
                                weight = weight, onWeight = {},
                                showWeight = showWeight
                            )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {}) { Text("复制") }
                            TextButton(onClick = {}) { Text("取消") }
                            TextButton(onClick = {}) { Text("分享…") }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun birthNewsDialogSingle() {
        // 单宝宝 + 出生时刻 + 体重：正文预填含“08:30 出生，体重 6斤8两”
        val baby = io.github.xiaohaoz1.jiarenxia.ui.pregnancy.DoneBaby(
            "小宝", "儿子", Demo.today, 8 * 60 + 30)
        birthNewsShot(
            "birth_news_dialog_single",
            text = io.github.xiaohaoz1.jiarenxia.ui.pregnancy.BirthNewsText
                .body(listOf(baby), "6斤8两"),
            weight = "6斤8两", showWeight = true
        ).also {
            assertTexts("报喜", "08:30", "体重", "6斤8两", "分享…")
        }
    }

    @Test
    fun birthNewsDialogTwinsNoTime() {
        // 双胞胎且都没填时刻：正文不写时刻，无体重行
        val babies = listOf(
            io.github.xiaohaoz1.jiarenxia.ui.pregnancy.DoneBaby(
                "小宝", "儿子", Demo.today, null),
            io.github.xiaohaoz1.jiarenxia.ui.pregnancy.DoneBaby(
                "小贝", "女儿", Demo.today.minusDays(1), null)
        )
        birthNewsShot(
            "birth_news_dialog_twins_no_time",
            text = io.github.xiaohaoz1.jiarenxia.ui.pregnancy.BirthNewsText
                .body(babies, null),
            weight = "", showWeight = false
        ).also {
            assertTexts("报喜", "小宝于", "小贝于", "分享…")
        }
    }

    /** 出生后要办的事演示清单：双胞胎各 5 条 + 孕妈 2 条，其中 4 条已“定日期”。 */
    private fun demoPostnatal(): io.github.xiaohaoz1.jiarenxia.ui.checklist
        .ChecklistUiState {
        val clId = "cl-post"
        val tasks = mutableListOf<io.github.xiaohaoz1.jiarenxia.core.model
            .FamilyTask>()
        fun task(
            id: String, personId: String, title: String,
            anchor: LocalDate, done: Boolean
        ) {
            tasks += io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask(
                id = id, personId = personId,
                kind = io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.CUSTOM,
                title = title, anchorDate = anchor,
                repeat = io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.NONE,
                periodIndex = 0, done = done, enabled = true,
                offsets = setOf(0, 1), pregnancyId = null, archived = false
            )
        }
        // 有日期条目：未办（3 天后）/ 已办（5 天后）/ 逾期（2 天）/ 孕妈复查（10 天后）
        task("t-vac1", "baby1", "小宝的疫苗接种", Demo.today.plusDays(3), false)
        task("t-card1", "baby1", "小宝的预防接种证",
            Demo.today.minusDays(2), false)
        task("t-cert2", "baby2", "小贝的出生医学证明",
            Demo.today.plusDays(5), true)
        task("t-check2", "wife", "李雪的产后复查",
            Demo.today.plusDays(10), false)
        val dated = mapOf(
            "t-vac1" to "疫苗接种",
            "t-card1" to "预防接种证",
            "t-cert2" to "出生医学证明",
            "t-check2" to "产后复查"
        )
        val items = mutableListOf<io.github.xiaohaoz1.jiarenxia.core.model
            .ChecklistItem>()
        var seq = 0
        fun addItems(
            personId: String,
            list: List<io.github.xiaohaoz1.jiarenxia.domain.checklist
                .ChecklistTemplates.TemplateItem>
        ) {
            list.forEach { t ->
                val taskId = dated.entries.firstOrNull {
                    it.value == t.title && when (personId) {
                        "baby1" -> it.key in setOf("t-vac1", "t-card1")
                        "baby2" -> it.key == "t-cert2"
                        else -> it.key == "t-check2"
                    }
                }?.key
                items += io.github.xiaohaoz1.jiarenxia.core.model.ChecklistItem(
                    id = "pi-" + seq, checklistId = clId,
                    groupName = t.groupName, title = t.title, note = t.note,
                    checked = when {
                        taskId != null -> false
                        personId == "baby1" && t.title == "出生医学证明" -> true
                        personId == "baby2" && t.title == "预防接种证" -> true
                        else -> false
                    },
                    sortOrder = seq, personId = personId,
                    taskId = taskId, createdAt = 1, updatedAt = 1
                )
                seq++
            }
        }
        addItems("baby1",
            io.github.xiaohaoz1.jiarenxia.domain.checklist.ChecklistTemplates
                .POSTNATAL_BABY_ITEMS)
        addItems("baby2",
            io.github.xiaohaoz1.jiarenxia.domain.checklist.ChecklistTemplates
                .POSTNATAL_BABY_ITEMS)
        addItems("wife",
            io.github.xiaohaoz1.jiarenxia.domain.checklist.ChecklistTemplates
                .POSTNATAL_MOM_ITEMS)
        return io.github.xiaohaoz1.jiarenxia.ui.checklist.ChecklistUiState(
            loading = false,
            model = io.github.xiaohaoz1.jiarenxia.data.ChecklistWithItemsModel(
                checklist = io.github.xiaohaoz1.jiarenxia.core.model.Checklist(
                    id = clId,
                    kind = io.github.xiaohaoz1.jiarenxia.core.model
                        .ChecklistKind.POSTNATAL,
                    title = "出生后要办的事", personId = "wife",
                    pregnancyId = "pg-demo", createdAt = 1, updatedAt = 1
                ),
                items = items
            ),
            tasks = tasks.associateBy { it.id },
            personNames = mapOf(
                "baby1" to "小宝", "baby2" to "小贝", "wife" to "李雪"),
            personTitles = mapOf("wife" to "妻子"),
            selfId = "me",
            birthDates = mapOf(
                "baby1" to Demo.today.minusDays(18),
                "baby2" to Demo.today.minusDays(18)),
            today = Demo.today
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h2400dp-xxhdpi")
    fun checklistPostnatal() {
        shot("checklist_postnatal") {
            io.github.xiaohaoz1.jiarenxia.ui.checklist.ChecklistContent(
                st = demoPostnatal(),
                cb = io.github.xiaohaoz1.jiarenxia.ui.checklist
                    .ChecklistCallbacks(),
                onDone = {}
            )
        }.also {
            assertTexts("出生后要办的事", "已办 3 / 12", "小宝", "小贝",
                "妈妈", "疫苗接种", "3 天后", "已逾期 2 天",
                "产后复查", "打开国家政务服务平台")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h2400dp-xxhdpi")
    fun checklistPostnatalLg() {
        shotLarge("checklist_postnatal") {
            io.github.xiaohaoz1.jiarenxia.ui.checklist.ChecklistContent(
                st = demoPostnatal(),
                cb = io.github.xiaohaoz1.jiarenxia.ui.checklist
                    .ChecklistCallbacks(),
                onDone = {}
            )
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h2400dp-xxhdpi")
    fun checklistPostnatalItemMenu() {
        // 有日期条目的 ⋮ 菜单（「产后复查」标题唯一，便于定位节点）
        setShotContent {
            io.github.xiaohaoz1.jiarenxia.ui.checklist.ChecklistContent(
                st = demoPostnatal(),
                cb = io.github.xiaohaoz1.jiarenxia.ui.checklist
                    .ChecklistCallbacks(),
                onDone = {}
            )
        }
        composeRule.onNodeWithContentDescription("条目操作：产后复查")
            .performClick()
        composeRule.waitForIdle()
        capture("checklist_postnatal_item_menu")
        assertTexts("打开事项", "改名", "删除")
    }

    private fun newbornCardState(
        fullMonth: LocalDate,
        fullMonthByBirth: LocalDate,
        fullMonthOverride: Boolean,
        postnatal: Boolean = true
    ) = io.github.xiaohaoz1.jiarenxia.ui.person.NewbornCardState(
        birthDate = Demo.today.minusDays(18),
        fullMonth = fullMonth,
        hundredDays = Demo.today.minusDays(18).plusDays(99),
        fullMonthByBirth = fullMonthByBirth,
        hundredByBirth = Demo.today.minusDays(18).plusDays(99),
        fullMonthOverride = fullMonthOverride,
        hundredOverride = false,
        postnatalChecklistId = if (postnatal) "cl-post" else null,
        postnatalDone = 4, postnatalTotal = 12
    )

    @Test
    fun newbornCard() {
        // 满月还有 12 天（2026-10-14），百天 2026-12-31，含产后清单行
        shot("newborn_card") {
            Column(Modifier.padding(16.dp)) {
                io.github.xiaohaoz1.jiarenxia.ui.person.NewbornCard(
                    card = newbornCardState(
                        fullMonth = Demo.today.plusDays(12),
                        fullMonthByBirth = Demo.today.plusDays(12),
                        fullMonthOverride = false
                    ),
                    today = Demo.today,
                    onSetCelebration = { _, _ -> },
                    onOpenChecklist = {}
                )
            }
        }.also {
            assertTexts("新生宝宝", "满月", "百天", "还有 12 天",
                "出生后要办的事", "已办 4 / 12")
        }
    }

    @Test
    fun newbornCardOverride() {
        // 满月另设 10-20（按出生日算 10-14）：值显示庆祝日 + 小字对照
        shot("newborn_card_override") {
            Column(Modifier.padding(16.dp)) {
                io.github.xiaohaoz1.jiarenxia.ui.person.NewbornCard(
                    card = newbornCardState(
                        fullMonth = Demo.today.plusDays(18),
                        fullMonthByBirth = Demo.today.plusDays(12),
                        fullMonthOverride = true
                    ),
                    today = Demo.today,
                    onSetCelebration = { _, _ -> },
                    onOpenChecklist = {}
                )
            }
        }.also {
            assertTexts("新生宝宝", "满月", "按出生日算", "百天")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun personHeaderBabyAge() {
        shot("person_header_baby_age") {
            PersonDetailContent(
                lunar = Demo.lunar,
                st = babyDetail(),
                today = Demo.today, snackbar = SnackbarHostState(),
                onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {},
                onDial = {}, onSetReminder = { _, _ -> },
                onSetReminderOffsets = { _, _ -> },
                onSetCustomTitle = {}, onDeletePerson = {},
                onSetSiblingOrder = { _, _, _ -> }, onRemoveRelation = {}
            )
        }.also {
            assertTexts("小宝", "出生第 18 天", "儿子")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun familyCoreBabyAge() {
        shot(
            "family_core_baby_age",
            style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
        ) {
            FamilyContent(
                FamilyUiState(
                    overview = babyOverview(),
                    defaultNumbers = mapOf("me" to "13800138000"),
                    today = Demo.today
                ),
                Demo.lunar, {}, {}, {}, {}, {}
            )
        }.also {
            assertTexts("小宝", "出生第 18 天")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun familyCoreBabyAgeJiashu() {
        shot("family_core_baby_age_jiashu") {
            FamilyContent(
                FamilyUiState(
                    overview = babyOverview(),
                    defaultNumbers = mapOf("me" to "13800138000"),
                    today = Demo.today
                ),
                Demo.lunar, {}, {}, {}, {}, {}
            )
        }.also {
            assertTexts("小宝", "出生第 18 天")
        }
    }

    @Test
    fun pregnancyHistoryDialogBorn() {
        // BORN 孕期历史：宝宝行 + 补记入口 + 产后清单入口
        val born = demoPregnancy(
            Demo.today.minusDays(400), lmp = Demo.today.minusDays(680),
            status = io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus
                .BORN,
            endedOn = Demo.today.minusDays(120))
        shot("pregnancy_history_dialog_born") {
            PregnancyHistoryDialog(
                pregnancy = born,
                tasks = listOf(
                    prenatalTask("h1", Demo.today.minusDays(200),
                        archived = true)
                ),
                today = Demo.today,
                birthBag = null, onDelete = {}, onDismiss = {},
                babyNames = listOf("小宝", "小贝"),
                postnatal = io.github.xiaohaoz1.jiarenxia.data
                    .ChecklistProgress(
                        checklist = io.github.xiaohaoz1.jiarenxia.core.model
                            .Checklist(
                                id = "cl-post",
                                kind = io.github.xiaohaoz1.jiarenxia.core.model
                                    .ChecklistKind.POSTNATAL,
                                title = "出生后要办的事", personId = "wife",
                                pregnancyId = "pg-demo",
                                createdAt = 1, updatedAt = 1
                            ),
                        checkedCount = 3, totalCount = 12
                    ),
                onOpenPostnatal = {}
            )
        }.also {
            assertTexts("孕期记录", "宝宝出生于", "宝宝：小宝、小贝",
                "补记一个宝宝", "产检", "出生后要办的事",
                "查看出生后要办的事（已办 3 / 12）")
        }
    }

    // ---------- 用药记录（FAMILY_AFFAIRS 23） ----------

    private fun demoMed(
        id: String, name: String,
        usage: io.github.xiaohaoz1.jiarenxia.core.model.MedUsage,
        courseDays: Int? = null,
        start: LocalDate = Demo.today.minusDays(30),
        refill: LocalDate? = null,
        meal: io.github.xiaohaoz1.jiarenxia.core.model.MedMeal? = null,
        dose: String? = null, spec: String? = null,
        purpose: String? = null, note: String? = null,
        times: List<Int> = emptyList(),
        stopped: LocalDate? = null
    ) = io.github.xiaohaoz1.jiarenxia.core.model.Medication(
        id = id, personId = "me", name = name, dose = dose, spec = spec,
        meal = meal, usage = usage, courseDays = courseDays,
        startEpochDay = start.toEpochDay(), purpose = purpose, note = note,
        refillEpochDay = refill?.toEpochDay(),
        stoppedEpochDay = stopped?.toEpochDay(), times = times)

    /** med_card 演示数据：五种在用状态各一 + 两条已停用（收起）。 */
    private fun demoMeds() = listOf(
        // 短期第 3 天（9-30 开始，7 天疗程）
        demoMed("m1", "阿莫西林胶囊",
            io.github.xiaohaoz1.jiarenxia.core.model.MedUsage.SHORT_COURSE,
            courseDays = 7, start = Demo.today.minusDays(2),
            dose = "1 粒", spec = "0.25g/粒",
            meal = io.github.xiaohaoz1.jiarenxia.core.model.MedMeal.AFTER,
            times = listOf(480, 1200)),
        // 短期待确认
        demoMed("m2", "氯雷他定片",
            io.github.xiaohaoz1.jiarenxia.core.model.MedUsage.SHORT_COURSE,
            courseDays = null, start = Demo.today.minusDays(1),
            dose = "1 片"),
        // 记录疗程已到期（9-25 开始 7 天，最后一天 10-1 < 今天）
        demoMed("m3", "布洛芬缓释胶囊",
            io.github.xiaohaoz1.jiarenxia.core.model.MedUsage.SHORT_COURSE,
            courseDays = 7, start = Demo.today.minusDays(7)),
        // 长期吃 + 下次拿药日 11-4
        demoMed("m4", "二甲双胍片",
            io.github.xiaohaoz1.jiarenxia.core.model.MedUsage.LONG_TERM,
            start = Demo.today.minusDays(60),
            refill = Demo.today.plusDays(33),
            dose = "1 片",
            meal = io.github.xiaohaoz1.jiarenxia.core.model.MedMeal.WITH,
            times = listOf(480, 1200)),
        // 需要时吃
        demoMed("m5", "蒙脱石散",
            io.github.xiaohaoz1.jiarenxia.core.model.MedUsage.AS_NEEDED,
            purpose = "腹泻"),
        // 以前吃过 ×2
        demoMed("m6", "感冒灵颗粒",
            io.github.xiaohaoz1.jiarenxia.core.model.MedUsage.SHORT_COURSE,
            courseDays = 3, start = Demo.today.minusDays(40),
            stopped = Demo.today.minusDays(30)),
        demoMed("m7", "硫酸亚铁片",
            io.github.xiaohaoz1.jiarenxia.core.model.MedUsage.LONG_TERM,
            start = Demo.today.minusDays(200),
            stopped = Demo.today.minusDays(100))
    )

    @androidx.compose.runtime.Composable
    private fun medCardContent(
        meds: List<io.github.xiaohaoz1.jiarenxia.core.model.Medication>,
        onTimeEnabled: Boolean = false,
        onTimeSwitchEnabled: Boolean = true,
        onTimeHint: io.github.xiaohaoz1.jiarenxia.ui.person.MedOnTimeHint =
            io.github.xiaohaoz1.jiarenxia.ui.person.MedOnTimeHint.NONE
    ) = Column(Modifier.padding(16.dp)) {
        io.github.xiaohaoz1.jiarenxia.ui.person.MedicationCard(
            medications = meds, today = Demo.today,
            courseRefillEnabled = true,
            onSetCourseRefill = {}, onOpenEdit = {}, onOpenDoctor = {},
            onTimeEnabled = onTimeEnabled,
            onTimeSwitchEnabled = onTimeSwitchEnabled,
            onTimeHint = onTimeHint,
            onSetOnTime = {}
        )
    }

    @Test
    fun medCard() {
        shot("med_card") { medCardContent(demoMeds()) }.also {
            assertTexts("用药", "阿莫西林胶囊",
                "短期疗程 · 记录疗程 7 天，按开始日期计算今天是第 3 天",
                "氯雷他定片", "疗程待确认，请向医生或药师核对",
                "布洛芬缓释胶囊", "记录疗程已到期（7 天），实际停用待确认",
                "二甲双胍片", "长期吃 · 下次拿药 11月4日",
                "蒙脱石散", "需要时吃",
                "以前吃过（2）", "记一种药", "给医生看",
                "疗程到期与续药提醒")
        }
    }

    @Test
    fun medCardOnTime() {
        // 按时吃开 + 缺精确闹钟权限 → 「提醒可能延迟」提示
        shot("med_card_on_time") {
            medCardContent(
                demoMeds(), onTimeEnabled = true,
                onTimeHint = io.github.xiaohaoz1.jiarenxia.ui.person
                    .MedOnTimeHint.EXACT_ALARM_MISSING
            )
        }.also {
            assertTexts("按时吃提醒", "部分手机需要在系统设置里允许家人侠自启动",
                "提醒可能延迟", "允许准时提醒", "疗程到期与续药提醒")
        }
    }

    @Test
    fun medCardNotifOff() {
        // 通知不可用 → 「通知未开启，提醒不会响」（优先于精确闹钟提示）
        shot("med_card_notif_off") {
            medCardContent(
                demoMeds(), onTimeEnabled = true,
                onTimeHint = io.github.xiaohaoz1.jiarenxia.ui.person
                    .MedOnTimeHint.NOTIFICATIONS_OFF
            )
        }.also {
            assertTexts("按时吃提醒", "通知未开启，提醒不会响", "去开启")
            assertNoTexts("提醒可能延迟")
        }
    }

    @Test
    fun medCardOnTimeDisabled() {
        // 「提醒」里的用药提醒或按时吃提醒已关闭 → 开关行置灰 + 替换副标题
        shot("med_card_on_time_disabled") {
            medCardContent(
                demoMeds(), onTimeEnabled = true, onTimeSwitchEnabled = false
            )
        }.also {
            assertTexts("按时吃提醒",
                "「提醒」里的用药提醒或按时吃提醒已关闭")
            assertNoTexts("部分手机需要在系统设置里允许家人侠自启动")
        }
    }

    @Test
    fun medCardEmpty() {
        shot("med_card_empty") { medCardContent(emptyList()) }.also {
            assertTexts("用药", "还没有记录在用的药", "记一种药")
            assertNoTexts("给医生看", "疗程到期与续药提醒")
        }
    }

    @Test
    fun medCardHistoryOpen() {
        // 点开「以前吃过」：两条停用记录展开
        setShotContent { medCardContent(demoMeds()) }
        composeRule.onNodeWithText("以前吃过（2）").performClick()
        composeRule.waitForIdle()
        capture("med_card_history_open")
        assertTexts("收起", "感冒灵颗粒", "8月23日开始", "9月2日记录停用")
    }

    @Test
    fun medEditNew() {
        shot("med_edit_new") {
            io.github.xiaohaoz1.jiarenxia.ui.med.MedEditContent(
                st = io.github.xiaohaoz1.jiarenxia.ui.med.MedEditUiState(
                    personId = "me", loaded = true, startDate = Demo.today),
                today = Demo.today,
                cb = io.github.xiaohaoz1.jiarenxia.ui.med.MedEditCallbacks(),
                onDone = {}
            )
        }.also {
            assertTexts("记一种药", "拿药时问清楚",
                "吃多久？能不能长期吃？", "药名", "治什么（可不填）",
                "每次用量（可不填，如 1 片）", "药品规格（可不填，如 500mg/片）",
                "怎么吃", "短期疗程", "长期吃", "需要时吃",
                "开始日期", "饭前饭后", "不填", "饭前", "饭后", "随餐",
                "医嘱备注（可不填）", "保存")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h2400dp-xxhdpi")
    fun medEditShortPending() {
        // 短期疗程 + 疗程待确认：天数禁用 + warning 文案；时刻卡出现
        shot("med_edit_short_pending") {
            io.github.xiaohaoz1.jiarenxia.ui.med.MedEditContent(
                st = io.github.xiaohaoz1.jiarenxia.ui.med.MedEditUiState(
                    personId = "me", loaded = true, startDate = Demo.today,
                    name = "阿莫西林胶囊",
                    usage = io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
                        .SHORT_COURSE,
                    coursePending = true,
                    times = listOf(480, 1200),
                    meal = io.github.xiaohaoz1.jiarenxia.core.model.MedMeal.AFTER
                ),
                today = Demo.today,
                cb = io.github.xiaohaoz1.jiarenxia.ui.med.MedEditCallbacks(),
                onDone = {}
            )
        }.also {
            assertTexts("记一种药", "记录的疗程天数",
                "疗程待确认（还没问清楚吃几天）",
                "疗程待确认，请向医生或药师核对",
                "每天几点吃（可不填）", "08:00", "20:00", "添加时刻")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h2400dp-xxhdpi")
    fun medEditLong() {
        // 长期吃：下次拿药日 + 时刻 chips + 饭后
        shot("med_edit_long") {
            io.github.xiaohaoz1.jiarenxia.ui.med.MedEditContent(
                st = io.github.xiaohaoz1.jiarenxia.ui.med.MedEditUiState(
                    personId = "me", loaded = true, startDate = Demo.today,
                    name = "二甲双胍片",
                    usage = io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
                        .LONG_TERM,
                    refillDate = Demo.today.plusDays(33),
                    times = listOf(480, 1200),
                    meal = io.github.xiaohaoz1.jiarenxia.core.model.MedMeal.WITH
                ),
                today = Demo.today,
                cb = io.github.xiaohaoz1.jiarenxia.ui.med.MedEditCallbacks(),
                onDone = {}
            )
        }.also {
            assertTexts("下次拿药日（可不填）", "11月4日",
                "每天几点吃（可不填）", "08:00", "20:00",
                "添加时刻", "保存")
        }
    }

    @Test
    fun medLongConfirmDialog() {
        // 长期确认弹窗（新建选长期吃时保存前先弹）
        shot("med_long_confirm_dialog") {
            io.github.xiaohaoz1.jiarenxia.ui.med.MedEditContent(
                st = io.github.xiaohaoz1.jiarenxia.ui.med.MedEditUiState(
                    personId = "me", loaded = true, startDate = Demo.today,
                    name = "二甲双胍片",
                    usage = io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
                        .LONG_TERM,
                    confirmLongTerm = true),
                today = Demo.today,
                cb = io.github.xiaohaoz1.jiarenxia.ui.med.MedEditCallbacks(),
                onDone = {}
            )
        }.also {
            assertTexts("请确认这是医生交代的长期用药",
                "长期吃的药会按“下次拿药日”提醒拿药。",
                "是医生交代的", "再想想")
        }
    }

    @Test
    fun medEditCourseEnded() {
        // 编辑已到期记录：状态卡 + 「记录已停用」「医生让继续吃」
        val ended = demoMed("m3", "布洛芬缓释胶囊",
            io.github.xiaohaoz1.jiarenxia.core.model.MedUsage.SHORT_COURSE,
            courseDays = 7, start = Demo.today.minusDays(7),
            dose = "1 粒", meal = io.github.xiaohaoz1.jiarenxia.core.model
                .MedMeal.AFTER)
        shot("med_edit_course_ended") {
            io.github.xiaohaoz1.jiarenxia.ui.med.MedEditContent(
                st = io.github.xiaohaoz1.jiarenxia.ui.med.MedEditUiState(
                    medId = "m3", personId = "me", existing = ended,
                    loaded = true,
                    name = ended.name, dose = ended.dose ?: "",
                    usage = ended.usage, courseDaysText = "7",
                    startDate = LocalDate.ofEpochDay(ended.startEpochDay),
                    meal = ended.meal),
                today = Demo.today,
                cb = io.github.xiaohaoz1.jiarenxia.ui.med.MedEditCallbacks(),
                onDone = {}
            )
        }.also {
            assertTexts("编辑用药", "记录疗程已到期，实际停用待确认",
                "记录已停用", "医生让继续吃", "拿药时问清楚",
                "记录的疗程天数")
        }
    }

    private fun medDoctorState(
        meds: List<io.github.xiaohaoz1.jiarenxia.core.model.Medication>
    ) = io.github.xiaohaoz1.jiarenxia.ui.med.MedDoctorUiState(
        loading = false, personName = "杨明", ageText = "62 岁",
        medications = meds)

    @Test
    fun medDoctorView() {
        // 给医生看：未停用（含到期）逐卡；药名 headlineSmall + 逐行 bodyLarge
        val meds = demoMeds().filter { it.stoppedEpochDay == null }.map {
            it.copy(purpose = it.purpose ?: when (it.id) {
                "m1" -> "扁桃体发炎"; "m4" -> "二型糖尿病"; else -> null
            }, note = when (it.id) {
                "m1" -> "饭后半小时吃，整粒吞"; else -> null
            })
        }
        shot("med_doctor_view") {
            io.github.xiaohaoz1.jiarenxia.ui.med.MedDoctorContent(
                st = medDoctorState(meds), today = Demo.today, onBack = {}
            )
        }.also {
            assertTexts("给医生看", "杨明（62 岁）",
                "以下为家人记录，不是服药记录",
                "阿莫西林胶囊", "怎么吃：短期疗程 · 记录疗程 7 天",
                "治什么：扁桃体发炎", "记录的开始日期：2026年9月30日",
                "医嘱：饭后半小时吃，整粒吞",
                "记录疗程已到期（7 天），实际停用待确认",
                "长期吃 · 下次拿药 11月4日")
        }
    }

    @Test
    fun reminderSettingsMed() {
        // 提醒设置页第五类「用药提醒」（副标题「疗程到期与续药」，时刻 20:30）
        shot("reminder_settings_med") {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderSettingsContent(
                state = io.github.xiaohaoz1.jiarenxia.ui.reminder
                    .ReminderSettingsUi(medMinute = 20 * 60 + 30),
                onBack = {}
            )
        }.also {
            assertTexts("生日与纪念日", "家庭事项", "联系家人", "上坟提醒",
                "用药提醒", "疗程到期与续药", "20:30")
        }
    }

    @Test
    fun reminderSettingsMedOnTime() {
        // 提醒设置页：用药提醒组内「按时吃提醒」子行
        shot("reminder_settings_med_on_time") {
            io.github.xiaohaoz1.jiarenxia.ui.reminder.ReminderSettingsContent(
                state = io.github.xiaohaoz1.jiarenxia.ui.reminder
                    .ReminderSettingsUi(medMinute = 20 * 60 + 30),
                onBack = {}
            )
        }.also {
            assertTexts("用药提醒", "疗程到期与续药", "20:30",
                "按时吃提醒", "到点提醒吃药，默认只提醒“我”的药")
        }
    }

    // ---------- 用药截图·大字号 ----------

    @Test
    fun medCardLg() {
        shotLarge("med_card") { medCardContent(demoMeds()) }.also {
            assertTexts("用药", "阿莫西林胶囊", "以前吃过（2）",
                "记一种药", "给医生看")
        }
    }

    @Test
    fun medDoctorViewLg() {
        val meds = demoMeds().filter { it.stoppedEpochDay == null }
        shotLarge("med_doctor_view") {
            io.github.xiaohaoz1.jiarenxia.ui.med.MedDoctorContent(
                st = medDoctorState(meds), today = Demo.today, onBack = {}
            )
        }.also {
            assertTexts("给医生看", "杨明（62 岁）",
                "以下为家人记录，不是服药记录", "阿莫西林胶囊")
        }
    }

    @Test
    fun medEditNewLg() {
        shotLarge("med_edit_new") {
            io.github.xiaohaoz1.jiarenxia.ui.med.MedEditContent(
                st = io.github.xiaohaoz1.jiarenxia.ui.med.MedEditUiState(
                    personId = "me", loaded = true, startDate = Demo.today),
                today = Demo.today,
                cb = io.github.xiaohaoz1.jiarenxia.ui.med.MedEditCallbacks(),
                onDone = {}
            )
        }.also {
            assertTexts("记一种药", "拿药时问清楚", "药名", "怎么吃", "保存")
        }
    }

    // ---------- 「问一问」截图（FAMILY_AFFAIRS §25 方案 C） ----------

    /**
     * 「问一问」对话框截图：AlertDialog 弹层无法被截图，渲染同款卡片内容
     * （同 blessingDialogShot 的做法）：标题 + [AskDialogBody] + 按钮行。
     */
    private fun askDialogShot(
        name: String,
        text: String,
        large: Boolean = false
    ) {
        val content: @Composable () -> Unit = {
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
                        io.github.xiaohaoz1.jiarenxia.ui.designsystem
                            .DialogTitle("问一问")
                        Spacer(Modifier.height(16.dp))
                        io.github.xiaohaoz1.jiarenxia.ui.common.AskDialogBody(
                            text = text, onTextChange = {})
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {}) { Text("复制") }
                            TextButton(onClick = {}) { Text("取消") }
                            TextButton(onClick = {}) { Text("分享…") }
                        }
                    }
                }
            }
        }
        if (large) shotLarge(name, content = content)
        else shot(name, content = content)
        assertTexts("问一问", "问题", "拼好的问题可以发给 AI 应用",
            "分享…", "复制", "取消")
    }

    /** 用药「问一问」示例文本：短期 + 疗程待确认（规格例句）。 */
    private fun askMedText(): String {
        val med = io.github.xiaohaoz1.jiarenxia.core.model.Medication(
            id = "m-ask", personId = "dad",
            name = "头孢克肟分散片", dose = "1 片", spec = null,
            meal = null,
            usage = io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
                .SHORT_COURSE,
            courseDays = null,
            startEpochDay = LocalDate.of(2026, 10, 20).toEpochDay(),
            purpose = "支气管炎", note = null, refillEpochDay = null,
            stoppedEpochDay = null, times = emptyList(),
            createdAt = 0L, updatedAt = 0L
        )
        return io.github.xiaohaoz1.jiarenxia.domain.ask.AskText
            .forMedication("我父亲", "70 多岁", med, Demo.today)
    }

    @Test
    fun askDialogMed() {
        askDialogShot("ask_dialog_med", askMedText()).also {
            assertTexts("我父亲，70 多岁", "记录的药是头孢克肟分散片",
                "疗程还没问清楚", "这个药一般吃多久？能不能长期吃？")
        }
    }

    @Test
    fun askDialogPregnancy() {
        // 与 pregnancyCard 同一孕周：预产期 = 今天 + 53 天 → 32 周
        val due = Demo.today.plusDays(53)
        askDialogShot(
            "ask_dialog_pregnancy",
            io.github.xiaohaoz1.jiarenxia.domain.ask.AskText.forPregnancy(
                "我妻子", due, Demo.today)
        ).also {
            assertTexts("我妻子怀孕 32 周。这个阶段要注意什么？")
        }
    }

    @Test
    fun askDialogNewborn() {
        // 出生第 19 天（出生当天算第 1 天；与 newborn_card 同一出生日）
        askDialogShot(
            "ask_dialog_newborn",
            io.github.xiaohaoz1.jiarenxia.domain.ask.AskText.forNewborn(
                Demo.today.minusDays(18), Demo.today)
        ).also {
            assertTexts("我家宝宝出生 19 天。",
                "妈妈坐月子要注意什么？")
        }
    }

    @Test
    fun askDialogMedLg() {
        askDialogShot("ask_dialog_med", askMedText(), large = true).also {
            assertTexts("我父亲，70 多岁", "疗程还没问清楚")
        }
    }

    @Test
    fun pregnancyCardMenu() {
        // 孕期卡 ⋮ 菜单展开：「问一问」在首位（真实 DropdownMenu）
        val due = Demo.today.plusDays(53)
        setShotContent {
            Column(Modifier.padding(16.dp)) {
                PregnancyCard(
                    pregnancy = demoPregnancy(due, due.minusDays(280)),
                    nextCheckup = null,
                    today = Demo.today,
                    onEditDates = {}, onEnd = {}, onDelete = {},
                    onOpenTask = {}, onAddCheckup = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("孕期操作")
            .performClick()
        composeRule.waitForIdle()
        capture("pregnancy_card_menu")
        assertTexts("问一问", "改预产期", "结束这条孕期记录", "删除这条记录")
    }

    // ---------- 第 7 批：守护侠推荐 + 打电话要问的事 ----------

    private fun shotCallQuestion(
        id: String, text: String, keep: Boolean = false
    ) = io.github.xiaohaoz1.jiarenxia.data.db.CallQuestionEntity(
        id = id, personId = "mom", text = text, keep = keep,
        createdAt = 1, updatedAt = 1)

    @Composable
    private fun callQuestionsCardContent(
        questions: List<io.github.xiaohaoz1.jiarenxia.data.db.CallQuestionEntity>
    ) = Column(Modifier.padding(16.dp)) {
        io.github.xiaohaoz1.jiarenxia.ui.person.CallQuestionsCard(
            questions = questions,
            onAdd = { _, _ -> }, onUpdate = { _, _, _ -> }, onDelete = {})
    }

    /** 「我的」页守护侠推荐卡（经典）。 */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun mineGuardianPromo() = shot(
        "mine_guardian_promo",
        style = io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.CLASSIC
    ) {
        MineContent(self = Demo.demoOverview().self.person)
    }.also {
        assertTexts("给爸妈的手机装上「守护侠」", "去看看", "不再显示")
    }

    /** 「我的」页守护侠推荐卡（家书）。 */
    @Test
    @Config(qualifiers = "w412dp-h1500dp-xxhdpi")
    fun mineGuardianPromoJiashu() = shot("mine_guardian_promo_jiashu") {
        MineContent(self = Demo.demoOverview().self.person)
    }.also {
        assertTexts("给爸妈的手机装上「守护侠」", "去看看", "不再显示")
    }

    /** 「不再显示」后推荐卡整卡隐藏（本机设置，不进备份）。 */
    @Test
    fun mineGuardianPromoDismissed() = shot("mine_guardian_promo_dismissed") {
        MineContent(
            self = Demo.demoOverview().self.person,
            guardianPromoDismissed = true
        )
    }.also {
        assertNoTexts("给爸妈的手机装上「守护侠」")
    }

    /** 「帮助与关于」常驻守护侠入口行。 */
    @Test
    fun aboutGuardianRow() = shot("about_guardian_row") {
        io.github.xiaohaoz1.jiarenxia.ui.mine.AboutContent(
            onBack = {}, onPrivacy = {}, onIntro = {}
        )
    }.also {
        assertTexts("守护侠", "装在独居长辈手机上的守护应用")
    }

    /** 长辈模式确认框末尾的守护侠推荐段。 */
    @Test
    fun elderConfirmGuardian() {
        elderConfirmShot(
            "elder_confirm_guardian",
            io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle.JIASHU,
            large = false
        )
        assertTexts("建议在这台手机上同时安装「守护侠」")
    }

    /** 人物页「打电话要问的事」卡：2 条，其中 1 条「每次都问」。 */
    @Test
    fun callQuestionsCard() = shot("call_questions_card") {
        callQuestionsCardContent(listOf(
            shotCallQuestion("q1", "有没有按时吃药"),
            shotCallQuestion("q2", "血压量了吗", keep = true)
        ))
    }.also {
        assertTexts("打电话要问的事", "有没有按时吃药", "血压量了吗",
            "每次都问", "添加")
        assertDescs("要问的事操作：有没有按时吃药", "要问的事操作：血压量了吗")
    }

    /** 「打电话要问的事」卡空态。 */
    @Test
    fun callQuestionsCardEmpty() = shot("call_questions_card_empty") {
        callQuestionsCardContent(emptyList())
    }.also {
        assertTexts("打电话要问的事",
            "想在电话里问的事记在这里，从家人侠拨号前会提醒你。", "添加")
    }

    /**
     * 对话框截图：AlertDialog 弹层无法被截图（同 elderConfirmShot 做法），
     * 渲染同款卡片：遮罩 + 圆角卡片 + 标题/正文/按钮行。
     */
    private fun dialogCardShot(
        name: String,
        large: Boolean = false,
        title: @Composable () -> Unit,
        body: @Composable () -> Unit,
        actions: @Composable () -> Unit
    ) {
        val card: @Composable () -> Unit = {
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
                        title()
                        Spacer(Modifier.height(16.dp))
                        body()
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) { actions() }
                    }
                }
            }
        }
        if (large) shotLarge(name, content = card)
        else shot(name, content = card)
    }

    /** 「添加要问的事」对话框初始态（空输入、保存禁用）。 */
    @Test
    fun callQuestionDialog() {
        dialogCardShot(
            "call_question_dialog",
            title = {
                io.github.xiaohaoz1.jiarenxia.ui.designsystem.DialogTitle(
                    "添加要问的事")
            },
            body = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        label = { Text("要问什么") },
                        placeholder = { Text("比如：有没有按时吃药") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = false, onCheckedChange = {})
                        Text("每次打电话都问")
                    }
                }
            },
            actions = {
                TextButton(onClick = {}) { Text("取消") }
                TextButton(enabled = false, onClick = {}) { Text("保存") }
            }
        )
        // placeholder 不进语义树，不参与断言（截图里可见）
        assertTexts("添加要问的事", "要问什么",
            "每次打电话都问", "取消", "保存")
    }

    /** 拨号前「打给…之前」夹具：3 条要问的事（含 1 条每次都问，不加标注）。 */
    private val shotPreCallQuestions = listOf(
        shotCallQuestion("q1", "有没有按时吃药"),
        shotCallQuestion("q2", "血压量了吗", keep = true),
        shotCallQuestion("q3", "上次说的复查去了吗")
    )

    private fun precallDialogShot(large: Boolean) {
        dialogCardShot(
            "precall_dialog",
            large = large,
            title = {
                io.github.xiaohaoz1.jiarenxia.ui.designsystem.DialogTitle(
                    "打给妈妈之前")
            },
            body = {
                io.github.xiaohaoz1.jiarenxia.ui.common.PreCallQuestionsBody(
                    shotPreCallQuestions)
            },
            actions = {
                TextButton(onClick = {}) { Text("取消") }
                TextButton(onClick = {}) { Text("去拨号") }
            }
        )
        assertTexts("打给妈妈之前", "记得问：", "· 有没有按时吃药",
            "· 血压量了吗", "· 上次说的复查去了吗", "取消", "去拨号")
    }

    @Test
    fun precallDialog() = precallDialogShot(large = false)

    @Test
    fun precallDialogLg() = precallDialogShot(large = true)

    /** 「联系上了吗」+ 「问过的就划掉」勾选区（只问一次条目默认全勾）。 */
    @Test
    fun postcallDialogQuestions() {
        dialogCardShot(
            "postcall_dialog_questions",
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("联系上了吗？", modifier = Modifier.weight(1f))
                    androidx.compose.material3.IconButton(onClick = {}) {
                        androidx.compose.material3.Icon(
                            Icons.Rounded.Close,
                            contentDescription = "关闭")
                    }
                }
            },
            body = {
                Column {
                    Text("刚才给妈妈打了电话。" +
                        "联系上了记一笔，没联系上提醒还会留着。")
                    Text(
                        "问过的就划掉：",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    listOf("有没有按时吃药", "上次说的复查去了吗")
                        .forEach { q ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = true, onCheckedChange = {})
                                Text(q, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                }
            },
            actions = {
                TextButton(onClick = {}) { Text("没联系上") }
                TextButton(onClick = {}) { Text("联系上了") }
            }
        )
        assertTexts("联系上了吗？", "刚才给妈妈打了电话。",
            "问过的就划掉：", "有没有按时吃药", "上次说的复查去了吗",
            "没联系上", "联系上了")
        assertDescs("关闭")
    }

}
