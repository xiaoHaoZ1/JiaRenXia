package io.github.xiaohaoz1.jiarenxia.ui.graph

import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.ui.theme.JiaRenXiaTheme
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 真实文字排版回归（主审探针转正）：
 * - 卡片宽高由 [GraphTextMeasure]（Android 文字管线）实测，
 *   GraphNodeItem 不再截断——姓名/称谓必须完整落在卡内；
 * - “回到我”/总览点人必须恢复 1.0 阅读比例；
 * - 芯片不扩大默认横向布局，放不下时不上画布但分组入口保留。
 * 全部使用合成资料。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class GraphTextMeasureTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val engine = KinshipEngine()
    private var seq = 0

    private fun r(src: String, tgt: String, type: RelationshipType,
        o: RelativeAgeOrder? = null) =
        Relationship("r" + (++seq), src, tgt, type, o)

    /**
     * 中文合成家庭：双方父母、本人夫妻、一个子女，另有隐藏祖辈
     * （dad 上方可展开）与一位兄弟（me 旁可展开）→ 默认视图带芯片。
     * “岳母”用拼音长名 WangFang 复现 device-v2/22-overview 的溢出场景。
     */
    private fun familyOverview(
        customTitles: Map<String, String> = emptyMap()
    ): Pair<FamilyOverview, Map<String, Int>> {
        fun person(id: String, name: String, g: Gender, self: Boolean = false) =
            Person(id = id, name = name, gender = g, isSelf = self)
        val persons = listOf(
            person("me", "陈志强", Gender.MALE, self = true),
            person("dad", "陈国栋", Gender.MALE),
            person("mom", "王秀英", Gender.FEMALE),
            person("pgf", "陈广田", Gender.MALE),      // 隐藏祖辈
            person("pgm", "刘桂芳", Gender.FEMALE),
            person("wife", "李雪", Gender.FEMALE),
            person("wDad", "李国强", Gender.MALE),
            person("wMom", "WangFang", Gender.FEMALE),  // 岳母·拼音长名
            person("bro", "陈志豪", Gender.MALE),
            person("son", "陈小北", Gender.MALE)
        )
        val rels = listOf(
            r("dad", "me", RelationshipType.PARENT),
            r("mom", "me", RelationshipType.PARENT),
            r("dad", "mom", RelationshipType.SPOUSE),
            r("pgf", "dad", RelationshipType.PARENT),
            r("pgm", "dad", RelationshipType.PARENT),
            r("me", "wife", RelationshipType.SPOUSE),
            r("wDad", "wife", RelationshipType.PARENT),
            r("wMom", "wife", RelationshipType.PARENT),
            r("wDad", "wMom", RelationshipType.SPOUSE),
            r("bro", "me", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
            r("me", "son", RelationshipType.PARENT),
            r("wife", "son", RelationshipType.PARENT)
        )
        val graph = FamilyGraph(persons, rels)
        val results = engine.resolveAll(graph, "me")
        val byId = persons.associate { pp ->
            pp.id to PersonOverview(
                pp, results.getValue(pp.id), emptyList(),
                customTitle = customTitles[pp.id])
        }
        val ov = FamilyOverview(
            self = byId.getValue("me"), graph = graph, byId = byId,
            core = emptyList(), others = emptyList())
        return ov to engine.generations(graph, "me")
    }

    /** 默认核心可见层（与 ViewModel 同一口径），布局用真实字体测量。 */
    private fun defaultModel(ov: FamilyOverview, gens: Map<String, Int>) =
        GraphVisibility.compute(ov, gens, emptySet()).let { vis ->
            Triple(
                GraphLayout.build(
                    ov, gens, visibleIds = vis.visibleIds,
                    collapsedCount = vis.collapsedCount,
                    expandChips = vis.chips,
                    sizer = GraphTextMeasure.sizer),
                vis, gens)
            }

    /** 渲染 GraphContent，可叠加系统字号（fontScale）。 */
    private fun setGraphContent(
        model: GraphModel, byId: Map<String, PersonOverview>,
        selfId: String, connectedTotal: Int, fontScale: Float = 1f
    ) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(d.density, fontScale)) {
                JiaRenXiaTheme {
                    Surface {
                        GraphContent(
                            model, byId, selfId,
                            onBack = {}, onOpenPerson = {},
                            connectedTotal = connectedTotal)
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    /** 取指定文本节点的真实排版结果（有且仅有一个）。 */
    private fun textLayout(text: String): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(out) }
        assertEquals("应取得真实文字排版结果: $text", 1, out.size)
        return out.single()
    }

    /**
     * 文本节点所在的卡片节点：沿父链找带 OnClick 的语义节点
     * （卡片 mergeDescendants + clickable）。找不到返回 null。
     */
    private fun cardAncestorOf(
        node: androidx.compose.ui.semantics.SemanticsNode
    ): androidx.compose.ui.semantics.SemanticsNode? {
        var p = node.parent
        while (p != null) {
            if (p.config.contains(SemanticsActions.OnClick)) return p
            p = p.parent
        }
        return null
    }

    // ---------- 一、真实文字测量：称谓/姓名完整显示，不截断 ----------

    /**
     * “完整落在卡内”的可靠判定（Robolectric 下 hasVisualOverflow
     * 恒 true，不可用；boundsInRoot 会被窗口裁掉，改用 positionInRoot+size）：
     * 1) 文本节点矩形完整落在卡片节点矩形内（不被挤出/裁掉）；
     * 2) 排版宽度 ≤ 卡内文字槽位宽（不可断行词也不会横向溢出）；
     * 3) 逐行真实边界：末行底 ≤ 文字区域高、每行左右不超文字区宽、
     *    末行覆盖完整文本、无省略号截断。
     * 调用处仅允许在默认视口（scale=1.0）下使用——positionInRoot
     * 含图层变换而 size 不含，缩放后二者不能直接比较。
     */
    private fun assertNoOverflow(
        text: String, note: String, sizeSp: Float, fontScale: Float) {
        // 同名字符串可能有多处（如“我”=卡片称谓+右上角标记），逐个核对
        val nodes = rule.onAllNodesWithText(text, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("$note「$text」未找到文本节点", nodes.isNotEmpty())
        val pxUnit = 3f * fontScale               // 测试环境 density=3
        for (sn in nodes) {
            val card = cardAncestorOf(sn)
            assertNotNull("$note「$text」应在卡片节点内", card)
            card!!
            val innerPx = (card.size.width / pxUnit -
                GraphGeom.CARD_PAD * 2f) * pxUnit
            val tTop = sn.positionInRoot.y
            val tBot = tTop + sn.size.height
            val tLeft = sn.positionInRoot.x
            val tRight = tLeft + sn.size.width
            val cTop = card.positionInRoot.y
            val cBot = cTop + card.size.height
            val cLeft = card.positionInRoot.x
            val cRight = cLeft + card.size.width
            assertTrue(
                "$note「$text」越出卡片 text=[$tLeft,$tTop,$tRight,$tBot] " +
                    "card=[$cLeft,$cTop,$cRight,$cBot]",
                tTop >= cTop - 1f && tBot <= cBot + 1f &&
                    tLeft >= cLeft - 1f && tRight <= cRight + 1f)
            val out = mutableListOf<TextLayoutResult>()
            if (sn.config.contains(SemanticsActions.GetTextLayoutResult)) {
                sn.config[SemanticsActions.GetTextLayoutResult]
                    .action?.invoke(out)
            }
            for (r in out) {
                assertTrue(
                    "$note「$text」排版宽 ${r.size.width}px 超过槽位 " +
                        "${innerPx}px",
                    r.size.width <= innerPx + 1f)
                // 行坐标在段落坐标系（multiParagraph.width/height =
                // 折行约束区域）；节点 size 可能因包裹内容而更小，
                // 行间边界必须与段落约束比较而非节点宽
                val last = r.lineCount - 1
                // 末行底部不超过文字区域高度（真实裁切判定）
                assertTrue(
                    "$note「$text」末行底 ${r.getLineBottom(last)} 超出" +
                        "文字区域高 ${r.size.height}",
                    r.getLineBottom(last) <= r.size.height + 1f)
                for (i in 0..last) {
                    assertFalse(
                        "$note「$text」第 $i 行被省略号截断",
                        r.isLineEllipsized(i))
                    assertTrue(
                        "$note「$text」第 $i 行右界 ${r.getLineRight(i)}" +
                            " 超出可用宽 ${r.multiParagraph.width}",
                        r.getLineRight(i) <= r.multiParagraph.width + 1f)
                    assertTrue(
                        "$note「$text」第 $i 行左界 ${r.getLineLeft(i)} 越界",
                        r.getLineLeft(i) >= -1f)
                }
                // 最后一行必须覆盖到文本末尾（无字符被丢弃）
                assertEquals(
                    "$note「$text」末行未覆盖完整文本",
                    text.length, r.getLineEnd(last))
            }
        }
    }

    /**
     * 对照组：完全无约束的短文本，逐行边界检查必须自洽——
     * 证明上面的边界断言有效（hasVisualOverflow 在本环境恒 true，
     * 不作依据，但真实几何边界必须正常）。
     */
    @Test
    fun 对照组_无约束短文本边界自洽() {
        rule.setContent {
            JiaRenXiaTheme { Surface { Text("Control") } }
        }
        rule.waitForIdle()
        val r = textLayout("Control")
        assertTrue(r.getLineBottom(r.lineCount - 1) <=
            r.size.height + 1f)
        assertEquals(7, r.getLineEnd(r.lineCount - 1))
        assertFalse(r.isLineEllipsized(0))
        assertTrue(r.getLineRight(0) <= r.size.width + 1f)
    }

    /** 长中文称谓在三种系统字号下都完整折行显示在卡内。 */
    @Test
    fun 长称谓完整显示_1点0() = assertLongTitle(1f)

    @Test
    fun 长称谓完整显示_1点3() = assertLongTitle(1.3f)

    @Test
    fun 长称谓完整显示_1点6() = assertLongTitle(1.6f)

    private fun assertLongTitle(fontScale: Float) {
        val title = "外曾祖父的弟弟的长子"
        val (ov, gens) = familyOverview(mapOf("wDad" to title))
        val (model, _, _) = defaultModel(ov, gens)
        setGraphContent(model, ov.byId, "me", gens.size, fontScale)
        // 长称谓至少要折成 2 行（证明没有单行裁掉）
        val r = textLayout(title)
        assertTrue("长称谓应折行显示，实际 ${r.lineCount} 行",
            r.lineCount >= 2)
        assertNoOverflow(title, "称谓", GraphGeom.TITLE_SIZE, fontScale)
        assertNoOverflow("李国强", "姓名", GraphGeom.NAME_SIZE, fontScale)
    }

    /** 拼音长名折行后称谓仍完整（device-v2/22-overview 的回归）。 */
    @Test
    fun 拼音长名_称谓完整显示_1点0() = assertWangFang(1f)

    @Test
    fun 拼音长名_称谓完整显示_1点3() = assertWangFang(1.3f)

    @Test
    fun 拼音长名_称谓完整显示_1点6() = assertWangFang(1.6f)

    private fun assertWangFang(fontScale: Float) {
        val (ov, gens) = familyOverview()
        val (model, _, _) = defaultModel(ov, gens)
        setGraphContent(model, ov.byId, "me", gens.size, fontScale)
        assertNoOverflow("WangFang", "姓名", GraphGeom.NAME_SIZE, fontScale)
        assertNoOverflow("岳母", "称谓", GraphGeom.TITLE_SIZE, fontScale)
    }

    /** 默认视图所有卡片上的姓名/称谓在三种字号下都不溢出。 */
    @Test
    fun 默认视图全部文字不溢出_1点3() = assertAllTexts(1.3f)

    @Test
    fun 默认视图全部文字不溢出_1点6() = assertAllTexts(1.6f)

    private fun assertAllTexts(fontScale: Float) {
        val (ov, gens) = familyOverview()
        val (model, _, _) = defaultModel(ov, gens)
        setGraphContent(model, ov.byId, "me", gens.size, fontScale)
        for (n in model.nodes) {
            val po = ov.byId.getValue(n.personId)
            assertNoOverflow(po.person.name,
                po.person.name + " 姓名", GraphGeom.NAME_SIZE, fontScale)
            if (po.displayTitle.isNotEmpty()) {
                assertNoOverflow(po.displayTitle,
                    po.person.name + " 称谓", GraphGeom.TITLE_SIZE, fontScale)
            }
        }
    }

    /** 测量器本身：卡高 = 衬距 + 头像 + 真实折行行数 × 显式行高。 */
    @Test
    fun 测量器_卡高容纳真实折行文本() {
        fun paint(sizeSp: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG)
            .apply { textSize = sizeSp }
        fun wrapLines(s: String, p: TextPaint, w: Float) =
            StaticLayout.Builder.obtain(
                s, 0, s.length, p, ceil(w).toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(true).build().lineCount

        // 覆盖：短名、长中文名、拼音长名、超长称谓、无称谓
        listOf(
            "杨明" to "儿子",
            "欧阳建国伟业长青" to "外曾祖父的弟弟的长子",
            "WangFang" to "岳母",
            "陈志强" to "",
            "AlexandraChristina" to "外曾祖父的弟弟的长子之外甥孙女"
        ).forEach { (name, title) ->
            val (w, h) = GraphTextMeasure.cardOf(name, title)
            val inner = w - GraphGeom.CARD_PAD * 2
            val nameLines = wrapLines(
                name, paint(GraphGeom.NAME_SIZE), inner)
            val titleLines = if (title.isEmpty()) 0 else
                wrapLines(title, paint(GraphGeom.TITLE_SIZE), inner)
            // 卡高 = 衬距 + 头像 + 行数×显式行高（与屏幕 lineHeight 一致）
            val need = 10f + GraphGeom.AVATAR + 6f +
                nameLines * GraphGeom.NAME_LH +
                (if (titleLines > 0) 4f + titleLines * GraphGeom.TITLE_LH
                 else 0f) + 11f
            assertTrue(
                "$name/$title 卡高 $h 应容纳文本栈 $need", h >= need - 0.01f)
            assertTrue(w >= GraphGeom.CARD_MIN_W - 0.01f)
            assertTrue(w <= GraphGeom.CARD_MAX_W + 0.01f)
        }
    }

    // ---------- 二、默认视图与芯片优先级 ----------

    @Test
    fun 芯片不扩大横向布局_且不遮挡卡片() {
        val (ov, gens) = familyOverview()
        val (model, vis, _) = defaultModel(ov, gens)
        // 核心人物全部入图
        val ids = model.nodes.map { it.personId }.toSet()
        assertTrue(ids.containsAll(
            listOf("me", "wife", "dad", "mom", "wDad", "wMom", "son")))
        // 收起旁支确有芯片候选（祖辈、平辈）
        assertTrue("应有隐藏祖辈/平辈芯片候选", vis.chips.isNotEmpty())
        val cardL = model.nodes.minOf { it.x }
        val cardR = model.nodes.maxOf { it.right }
        for (c in model.expandChips) {
            // 芯片横向必须完整落在卡片包围范围内
            assertTrue("芯片横向越界: $c",
                c.x >= cardL - 1e-4f && c.right <= cardR + 1e-4f)
            // 不遮挡任何卡片
            val hit = model.nodes.any { n ->
                c.x < n.right && c.right > n.x &&
                    c.y < n.bottom && c.bottom > n.y
            }
            assertFalse("芯片遮挡卡片: $c", hit)
        }
        // 芯片数量只减不增——放不下的不上画布，但分组入口在面板
        assertTrue(model.expandChips.size <= vis.chips.size)
        assertTrue("被裁芯片的分组入口必须保留在展开家人面板",
            vis.groups.isNotEmpty())
    }

    @Test
    fun 芯片与路径相交被路由剔除_面板入口保留() {
        val (ov, gens) = familyOverview()
        val (model, vis, _) = defaultModel(ov, gens)
        // 人为放一个压在夫妻连线上的芯片（与路径必然相交）
        val me = model.nodes.first { it.personId == "me" }
        val wife = model.nodes.first { it.personId == "wife" }
        val fake = PlacedChip(
            dir = GraphVisibility.Dir.SAME,
            align = PlacedChip.Align.BELOW,
            count = 1,
            expands = setOf(GraphVisibility.Expand(
                "me", GraphVisibility.Dir.SAME)),
            reveals = setOf("bro"),
            blockId = me.blockId,
            x = (me.right + wife.x) / 2f - 30f,
            y = minOf(me.cy, wife.cy) - 24f,
            w = 60f, h = 48f)
        val m2 = model.copy(expandChips = model.expandChips + fake)
        val routing = GraphEdgeRouting.route(m2)
        // 与连线相交的芯片被剔除
        assertTrue("与连线相交的芯片必须被剔除", routing.chips.none { it === fake })
        // 留下的芯片都不与路径段相交（2dp 余量，与路由同一判定）
        fun segHit(p0: GraphEdgeRouting.Pt, p1: GraphEdgeRouting.Pt,
            r: FloatRect): Boolean {
            if (p0.x == p1.x) {
                if (p0.x < r.left || p0.x > r.right) return false
                val lo = minOf(p0.y, p1.y); val hi = maxOf(p0.y, p1.y)
                return lo <= r.bottom && hi >= r.top
            }
            if (p0.y == p1.y) {
                if (p0.y < r.top || p0.y > r.bottom) return false
                val lo = minOf(p0.x, p1.x); val hi = maxOf(p0.x, p1.x)
                return lo <= r.right && hi >= r.left
            }
            return false
        }
        for (c in routing.chips) {
            val r = FloatRect(c.x - 2f, c.y - 2f, c.right + 2f, c.bottom + 2f)
            val hit = routing.paths.any { p ->
                (1 until p.points.size).any { i ->
                    segHit(p.points[i - 1], p.points[i], r)
                }
            }
            assertFalse("芯片与连线相交: $c", hit)
        }
        // 被剔芯片的分组入口不受影响（面板分组与画布芯片无关）
        assertTrue(vis.groups.isNotEmpty())
    }

    // ---------- 三、恢复阅读比例的交互回归 ----------

    /** 直接读画布当前视口缩放（语义探针）。 */
    private fun canvasScale(): Float =
        rule.onNodeWithTag("graphCanvas").fetchSemanticsNode()
            .config[GraphViewportScaleKey]

    /**
     * 画布内双指外扩（放大）。指尖选画布顶部空白带并水平外扩，
     * 避开卡片/芯片的点击手势吃事件；用手动 down/moveTo 序列。
     */
    private fun pinchZoomIn() {
        rule.onNodeWithTag("graphCanvas").performTouchInput {
            down(0, Offset(400f, 60f))
            down(1, Offset(680f, 60f))
            repeat(8) { i ->
                moveTo(0, Offset(400f - (i + 1) * 30f, 60f))
                moveTo(1, Offset(680f + (i + 1) * 30f, 60f))
                advanceEventTime(16)
            }
            up(0); up(1)
        }
        rule.waitForIdle()
    }

    /**
     * 卡片完整落在画布可视区内（未被屏幕边缘裁掉）。
     * boundsInRoot 会被窗口裁剪，改用 positionInRoot+size 的真实矩形。
     * 仅用于 scale=1.0 场景（坐标与尺寸同单位）。
     */
    private fun assertCardFullyVisible(name: String, note: String) {
        val sn = rule.onNodeWithText(name).fetchSemanticsNode()
        val canvas = rule.onNodeWithTag("graphCanvas")
            .fetchSemanticsNode().boundsInRoot
        val left = sn.positionInRoot.x
        val top = sn.positionInRoot.y
        assertTrue(
            "$note「$name」卡片未完整可见 " +
                "card=[$left,$top,${left + sn.size.width},${top + sn.size.height}] " +
                "canvas=$canvas",
            left >= canvas.left - 1f && left + sn.size.width <= canvas.right + 1f &&
                top >= canvas.top - 1f && top + sn.size.height <= canvas.bottom + 1f)
    }

    @Test
    fun 手动放大后_回到我_恢复1倍() {
        val (ov, gens) = familyOverview()
        val (model, _, _) = defaultModel(ov, gens)
        setGraphContent(model, ov.byId, "me", gens.size)

        assertEquals(1f, canvasScale(), 0.001f)
        pinchZoomIn()
        assertTrue("捏合应放大画布（scale=${canvasScale()}）",
            canvasScale() > 1.3f)

        rule.onNodeWithText("回到我").performClick()
        rule.waitForIdle()
        assertEquals("回到我后应恢复 1.0 阅读比例",
            1f, canvasScale(), 0.001f)
        assertCardFullyVisible("陈志强", "回到我")
    }

    @Test
    fun 手动放大后_总览点人_恢复1倍() {
        val (ov, gens) = familyOverview()
        val (model, _, _) = defaultModel(ov, gens)
        setGraphContent(model, ov.byId, "me", gens.size)

        pinchZoomIn()
        assertTrue(canvasScale() > 1.3f)

        rule.onNodeWithText("查看总览").performClick()
        rule.waitForIdle()
        // 总览里点一个人（岳父卡）→ 返回阅读
        rule.onNodeWithText("李国强").performClick()
        rule.waitForIdle()
        assertEquals("总览点人后应恢复 1.0 阅读比例",
            1f, canvasScale(), 0.001f)
        assertCardFullyVisible("李国强", "总览点人")
    }

    // ---------- 四、界面重建：旧相机请求不重放，视口/焦点保持 ----------

    /** 平辈展开后的模型（bro 入图），供重建回归用。 */
    private fun expandedModel(
        ov: FamilyOverview, gens: Map<String, Int>
    ): GraphModel {
        val vis = GraphVisibility.compute(
            ov, gens, setOf(GraphVisibility.Expand(
                "me", GraphVisibility.Dir.SAME)))
        return GraphLayout.build(
            ov, gens, visibleIds = vis.visibleIds,
            collapsedCount = vis.collapsedCount,
            expandChips = vis.chips,
            sizer = GraphTextMeasure.sizer)
    }

    private fun textPos(name: String): Offset =
        rule.onNodeWithText(name).fetchSemanticsNode().positionInRoot

    /** 当前视口 (scale, panX, panY) —— 画布语义探针。 */
    private fun viewport(): Triple<Float, Float, Float> =
        rule.onNodeWithTag("graphCanvas").fetchSemanticsNode().config.let {
            Triple(it[GraphViewportScaleKey], it[GraphViewportPanXKey],
                it[GraphViewportPanYKey])
        }

    /**
     * 主审探针转正：展开定位兄弟 → 回到我 → 保存恢复界面状态。
     * 已消费的相机请求（cameraSeq）不得因重建重放——本人位置不变。
     */
    @Test
    fun 回到我后重建_旧展开定位不重放() {
        val (ov, gens) = familyOverview()
        val model = expandedModel(ov, gens)
        val restore = StateRestorationTester(rule)
        restore.setContent {
            JiaRenXiaTheme { Surface {
                GraphContent(
                    model, ov.byId, "me",
                    onBack = {}, onOpenPerson = {},
                    connectedTotal = gens.size,
                    cameraSeq = 1, cameraTarget = "bro")
            } }
        }
        rule.waitForIdle()
        rule.onNodeWithText("回到我").performClick()
        rule.waitForIdle()
        assertEquals(1f, canvasScale(), 0.001f)
        val before = textPos("陈志强")
        restore.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        val after = textPos("陈志强")
        assertEquals("重建后旧相机请求不得重放（X）",
            before.x, after.x, 1f)
        assertEquals("重建后旧相机请求不得重放（Y）",
            before.y, after.y, 1f)
        assertEquals(1f, canvasScale(), 0.001f)
        assertCardFullyVisible("陈志强", "重建后回到我")
    }

    /**
     * 展开定位后重建：旧请求不重放、已恢复的视口不被初始化
     * 效果覆盖——兄弟家庭仍在原处可见（焦点与范围一致）。
     */
    @Test
    fun 展开定位后重建_视口与焦点保持() {
        val (ov, gens) = familyOverview()
        val model = expandedModel(ov, gens)
        val restore = StateRestorationTester(rule)
        restore.setContent {
            JiaRenXiaTheme { Surface {
                GraphContent(
                    model, ov.byId, "me",
                    onBack = {}, onOpenPerson = {},
                    connectedTotal = gens.size,
                    cameraSeq = 1, cameraTarget = "bro")
            } }
        }
        rule.waitForIdle()
        // 展开请求已消费：镜头在兄弟块上
        val before = textPos("陈志豪")
        val vpBefore = viewport()
        restore.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        val after = textPos("陈志豪")
        assertEquals("重建后视口不得改变（panX）",
            vpBefore.second, viewport().second, 1f)
        assertEquals("重建后视口不得改变（panY）",
            vpBefore.third, viewport().third, 1f)
        assertEquals("重建不得重放已消费的展开定位（X）",
            before.x, after.x, 1f)
        assertEquals("重建不得重放已消费的展开定位（Y）",
            before.y, after.y, 1f)
        assertCardFullyVisible("陈志豪", "重建后展开焦点")
    }

    /** 总览点人后重建：点选高亮与视口保持，旧请求不重放。 */
    @Test
    fun 总览点人后重建_焦点与视口保持() {
        val (ov, gens) = familyOverview()
        val (model, _, _) = defaultModel(ov, gens)
        val restore = StateRestorationTester(rule)
        restore.setContent {
            JiaRenXiaTheme { Surface {
                GraphContent(
                    model, ov.byId, "me",
                    onBack = {}, onOpenPerson = {},
                    connectedTotal = gens.size)
            } }
        }
        rule.waitForIdle()
        rule.onNodeWithText("查看总览").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("李国强").performClick()
        rule.waitForIdle()
        assertEquals(1f, canvasScale(), 0.001f)
        val before = textPos("李国强")
        restore.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        val after = textPos("李国强")
        assertEquals("总览点人后重建不得移位（X）",
            before.x, after.x, 1f)
        assertEquals("总览点人后重建不得移位（Y）",
            before.y, after.y, 1f)
        assertCardFullyVisible("李国强", "重建后总览点人")
    }

    /**
     * 重建时切换系统字号（相当于旋转/字号切换的几何变化路径）：
     * 展开范围保留、阅读比例不被动改变、本人家庭仍居中可见。
     */
    @Test
    fun 重建切大字号_展开与焦点保持() = assertRecreatedFontScale(1.3f)

    @Test
    fun 重建切特大字号_展开与焦点保持() = assertRecreatedFontScale(1.6f)

    private fun assertRecreatedFontScale(newFontScale: Float) {
        val (ov, gens) = familyOverview()
        val model = expandedModel(ov, gens)
        var fs = 1f
        val restore = StateRestorationTester(rule)
        restore.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(3f, fs)) {
                JiaRenXiaTheme { Surface {
                    GraphContent(
                        model, ov.byId, "me",
                        onBack = {}, onOpenPerson = {},
                        connectedTotal = gens.size,
                        cameraSeq = 1, cameraTarget = "bro")
                } }
            }
        }
        rule.waitForIdle()
        fs = newFontScale
        restore.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        // 字号变化属普通定位：保留阅读比例，本人家庭回到视野中央
        assertEquals("字号切换不得改变阅读比例",
            1f, canvasScale(), 0.001f)
        assertCardFullyVisible("陈志强", "重建${newFontScale}x 本人")
    }
}
