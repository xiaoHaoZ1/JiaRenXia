package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.data.backup.TestFamily
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** GraphLayout 阶段B：家庭 block + 世代 band + 同辈多行 + 男左女右。 */
class GraphLayoutTest {

    private val engine = KinshipEngine()
    private var seq = 0

    private fun p(id: String, gender: Gender = Gender.MALE, self: Boolean = false) =
        Person(id = id, name = id, gender = gender, isSelf = self)

    private fun r(src: String, tgt: String, type: RelationshipType, o: RelativeAgeOrder? = null) =
        Relationship("r" + (++seq), src, tgt, type, o)

    private fun overview(
        persons: List<Person>,
        rels: List<Relationship>,
        birthdays: Map<String, PartialDate> = emptyMap()
    ): Pair<FamilyOverview, Map<String, Int>> {
        val graph = FamilyGraph(persons, rels, birthdays)
        val self = persons.first { it.isSelf }
        val results = engine.resolveAll(graph, self.id)
        val byId = persons.associate { pp ->
            pp.id to PersonOverview(
                pp, results.getValue(pp.id),
                birthdays[pp.id]?.let { listOf(io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue(it)) } ?: emptyList(),
                solarBirthday = birthdays[pp.id]
            )
        }
        val gens = engine.generations(graph, self.id)
        val ov = FamilyOverview(
            self = byId.getValue(self.id), graph = graph, byId = byId,
            core = emptyList(), others = emptyList()
        )
        return ov to gens
    }

    private fun layout(
        ov: FamilyOverview, gens: Map<String, Int>,
        visible: Set<String> = gens.keys
    ) = GraphLayout.build(ov, gens, visibleIds = visible)

    private fun GraphModel.at(id: String) = nodes.first { it.personId == id }

    private fun assertX(expected: Float, actual: Float) =
        assertEquals(expected.toDouble(), actual.toDouble(), 1e-4)

    private fun overviewOf(graph: FamilyGraph): Pair<FamilyOverview, Map<String, Int>> {
        val self = graph.persons.first { it.isSelf }
        val results = engine.resolveAll(graph, self.id)
        val byId = graph.persons.associate { pp ->
            pp.id to PersonOverview(
                pp, results.getValue(pp.id),
                graph.birthdayValues[pp.id] ?: emptyList(),
                solarBirthday = graph.birthdays[pp.id]
            )
        }
        return FamilyOverview(
            self = byId.getValue(self.id), graph = graph, byId = byId,
            core = emptyList(), others = emptyList()
        ) to engine.generations(graph, self.id)
    }

    /** 卡片包络（布局单位）。 */
    private data class Rect(val l: Float, val t: Float, val r: Float, val b: Float)
    private fun rect(n: GraphNode) =
        Rect(n.x, n.y, n.right, n.bottom)
    private fun overlap(a: Rect, b: Rect) =
        a.l < b.r - 1e-4f && b.l < a.r - 1e-4f &&
            a.t < b.b - 1e-4f && b.t < a.b - 1e-4f

    /** 全图卡片两两不重叠。 */
    private fun assertNoOverlap(m: GraphModel) {
        val rects = m.nodes.associateBy({ it.personId }, ::rect)
        val bad = StringBuilder()
        val ids = rects.keys.toList()
        for (i in ids.indices) for (j in i + 1 until ids.size) {
            if (overlap(rects.getValue(ids[i]), rects.getValue(ids[j]))) {
                bad.append(ids[i]).append("×").append(ids[j]).append(" ")
            }
        }
        assertTrue("节点卡片重叠: " + bad, bad.isEmpty())
    }

    // ---------- 基本几何 ----------

    @Test
    fun 真实generation与视觉y分离_同代多行() {
        // 父亲的 4 位兄弟（各自单人 block）：左区一行放不下 4 列，
        // 同真实世代分多个展示行 → 同 generation、不同 y
        val persons = mutableListOf(p("me", self = true), p("dad"), p("mom", Gender.FEMALE))
        val rels = mutableListOf(
            r("dad", "me", RelationshipType.PARENT),
            r("mom", "me", RelationshipType.PARENT),
            r("dad", "mom", RelationshipType.SPOUSE)
        )
        repeat(4) { i ->
            persons += p("u" + i)
            rels += r("u" + i, "dad", RelationshipType.SIBLING)
        }
        val (ov, gens) = overview(persons, rels)
        val m = layout(ov, gens)
        val uncles = m.nodes.filter { it.personId.startsWith("u") }
        assertEquals(4, uncles.size)
        assertTrue(uncles.all { it.generation == -1 })
        // 超过 2 列 → 同 band 出现多个展示行
        assertTrue(uncles.map { it.y }.distinct().size > 1)
        val band = m.bands.single { it.generation == -1 }
        assertTrue(band.rows > 1)
        assertNoOverlap(m)
    }

    @Test
    fun 下一代在整个上一band之后() {
        val (ov, gens) = overview(
            listOf(
                p("me", self = true), p("dad"), p("mom", Gender.FEMALE),
                p("u1"), p("u2"), p("u3"), p("son"), p("dau", Gender.FEMALE)
            ),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("dad", "mom", RelationshipType.SPOUSE),
                r("u1", "dad", RelationshipType.SIBLING),
                r("u2", "dad", RelationshipType.SIBLING),
                r("u3", "dad", RelationshipType.SIBLING),
                r("me", "son", RelationshipType.PARENT),
                r("me", "dau", RelationshipType.PARENT)
            )
        )
        val m = layout(ov, gens)
        val b1 = m.bands.single { it.generation == -1 }
        val b0 = m.bands.single { it.generation == 0 }
        val bp1 = m.bands.single { it.generation == 1 }
        // 下一代 band 起点在上一个 band 全部展示行之后
        assertTrue(b0.top >= b1.bottom)
        assertTrue(bp1.top >= b0.bottom)
        // band 内所有节点 y 都 >= band.top
        m.nodes.forEach { n ->
            val b = m.bands.single { it.generation == n.generation }
            assertTrue(n.y >= b.top - 1e-4f && n.y < b.bottom)
        }
        assertNoOverlap(m)
    }

    @Test
    fun 主锚夫妻block居中男左女右() {
        val (ov, gens) = overview(
            listOf(p("me", self = true), p("wife", Gender.FEMALE)),
            listOf(r("me", "wife", RelationshipType.SPOUSE))
        )
        val m = layout(ov, gens)
        assertEquals(m.at("me").y, m.at("wife").y)
        assertTrue(m.at("me").x < m.at("wife").x)
        // 主锚块包络居中于 0
        val l = minOf(m.at("me").x, m.at("wife").x)
        val rr = maxOf(m.at("me").right, m.at("wife").right)
        assertTrue(abs((l + rr) / 2f) < 1f)
        // 夫妻相邻（成员间隔 MEMBER_GAP）
        assertX(GraphGeom.MEMBER_GAP, m.at("wife").x - m.at("me").right)
    }

    @Test
    fun 单身我居中且子孙居中() {
        val (ov, gens) = overview(
            listOf(p("me", self = true), p("son"), p("dau", Gender.FEMALE)),
            listOf(
                r("me", "son", RelationshipType.PARENT),
                r("me", "dau", RelationshipType.PARENT)
            )
        )
        val m = layout(ov, gens)
        // 无配偶单锚：卡心居中在 0 附近
        assertTrue(abs(m.at("me").cx) < 1f)
        // 直接子女在居中区（卡心绝对值有限，不在两侧侧区）
        assertTrue(abs(m.at("son").cx) < 60f)
        assertTrue(abs(m.at("dau").cx) < 60f)
        assertNoOverlap(m)
    }

    @Test
    fun 男左女右_男方整族左妻系右() {
        // 我（男）+ 妻：男方整族（含自己的父母与双方旁支）在左、妻系在右
        val (ov, gens) = overview(
            listOf(
                p("me", self = true), p("dad"), p("mom", Gender.FEMALE),
                p("pUncle"), p("mAunt", Gender.FEMALE),
                p("wife", Gender.FEMALE), p("wBro")
            ),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("dad", "mom", RelationshipType.SPOUSE),
                r("pUncle", "dad", RelationshipType.SIBLING),
                r("mAunt", "mom", RelationshipType.SIBLING),
                r("me", "wife", RelationshipType.SPOUSE),
                r("wBro", "wife", RelationshipType.SIBLING)
            )
        )
        val m = layout(ov, gens)
        // 夫妻 block 内男左女右
        assertTrue(m.at("dad").x < m.at("mom").x)
        // 男方整族（含母亲一侧）都在左区，妻系在右区
        for (id in listOf("dad", "mom", "pUncle", "mAunt")) {
            assertEquals(id, GraphSide.LEFT, m.blockOf(id)!!.side)
            assertTrue(id + " 应在左区", m.at(id).right < 0f)
        }
        assertEquals(GraphSide.RIGHT, m.blockOf("wBro")!!.side)
        assertTrue(m.at("wBro").x > m.at("wife").x)
        assertNoOverlap(m)
    }

    @Test
    fun 共同父母无配偶记录也成块() {
        val (ov, gens) = overview(
            listOf(p("me", self = true), p("dad"), p("mom", Gender.FEMALE)),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT)
            )
        )
        val m = layout(ov, gens)
        // 同代明确共同父母组成 block：相邻且男左女右
        assertEquals(m.at("dad").y, m.at("mom").y)
        assertX(GraphGeom.MEMBER_GAP, m.at("mom").x - m.at("dad").right)
        // 但不虚构夫妻边
        assertTrue(m.edges.none { it.type == RelationshipType.SPOUSE })
    }

    @Test
    fun 不虚构配偶_多余的配偶异常不丢人不丢边() {
        val (ov, gens) = overview(
            listOf(
                p("me", self = true), p("w1", Gender.FEMALE),
                p("w2", Gender.FEMALE)
            ),
            listOf(
                r("me", "w1", RelationshipType.SPOUSE),
                r("me", "w2", RelationshipType.SPOUSE)   // 异常数据：两个配偶
            )
        )
        val m = layout(ov, gens)
        assertEquals(3, m.nodes.size)
        assertEquals(2, m.edges.count { it.type == RelationshipType.SPOUSE })
        assertNoOverlap(m)
        val routing = GraphEdgeRouting.route(m)
        // 两条配偶边都有路径
        val spousePaths = routing.paths.count {
            it.style == GraphEdgeRouting.RouteStyle.SPOUSE
        }
        assertEquals(2, spousePaths)
    }

    @Test
    fun 跨代异常_保留世代与记录_逆向亲子边明确待核对() {
        // dad→gson 的错记边让引擎把 gson 归到 0 代（dad 的子女）；
        // son→gson 记录保留但逆向，不反画成父母子女关系
        val (ov, gens) = overview(
            listOf(
                p("me", self = true), p("dad"), p("son"), p("gson")
            ),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("me", "son", RelationshipType.PARENT),
                r("son", "gson", RelationshipType.PARENT),
                r("dad", "gson", RelationshipType.PARENT)
            )
        )
        val m = layout(ov, gens)
        assertEquals(-1, m.at("dad").generation)
        assertEquals(0, m.at("gson").generation)
        assertNoOverlap(m)
        val routing = GraphEdgeRouting.route(m)
        val conflict = m.edges.single { it.fromId == "son" && it.toId == "gson" }
        assertEquals(listOf(conflict), m.parentGenerationConflicts)
        assertTrue(routing.paths.none { it.edge == conflict })
        assertTrue(ov.graph.relationships.any {
            it.sourcePersonId == "son" && it.targetPersonId == "gson"
        })
        // dad→gson 是同代相邻的父母边，经家庭单元表达
        val dad = m.at("dad")
        assertTrue(routing.paths.any { p ->
            p.edge == null && p.points.isNotEmpty() &&
                abs(p.points[0].x - dad.cx) < 1e-3f &&
                abs(p.points[0].y - dad.bottom) < 1e-3f
        })
    }

    // ---------- 边集合 ----------

    @Test
    fun 共同父母已表达的兄弟不画兄弟边_记录保留() {
        val (ov, gens) = overview(
            listOf(
                p("me", self = true), p("dad"), p("bro")
            ),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("dad", "bro", RelationshipType.PARENT),
                r("bro", "me", RelationshipType.SIBLING, RelativeAgeOrder.OLDER)
            )
        )
        val m = layout(ov, gens)
        // 共同父亲可见 → SIBLING 边被视觉抑制（不画虚线）
        assertTrue(m.edges.none { it.type == RelationshipType.SIBLING })
        // 记录仍在 overview.graph 中（抑制只是渲染层）
        assertTrue(ov.graph.relationships.any {
            it.type == RelationshipType.SIBLING
        })
        // 共同父母不可见（被收起）→ SIBLING 边要画出来
        val m2 = layout(ov, gens, visible = setOf("me", "bro"))
        assertTrue(m2.edges.any { it.type == RelationshipType.SIBLING })
        val routing = GraphEdgeRouting.route(m2)
        assertTrue(routing.paths.any {
            it.style == GraphEdgeRouting.RouteStyle.DASHED &&
                it.edge?.type == RelationshipType.SIBLING
        })
    }

    @Test
    fun 堂表亲虚线保留() {
        val (ov, gens) = overview(
            listOf(p("me", self = true), p("cousin")),
            listOf(r("cousin", "me", RelationshipType.COUSIN))
        )
        val m = layout(ov, gens)
        assertTrue(m.edges.any { it.type == RelationshipType.COUSIN })
        val routing = GraphEdgeRouting.route(m)
        assertTrue(routing.paths.any {
            it.edge?.type == RelationshipType.COUSIN &&
                it.style == GraphEdgeRouting.RouteStyle.DASHED
        })
    }

    @Test
    fun 未关联人物不计入节点() {
        val (ov, gens) = overview(
            listOf(p("me", self = true), p("dad"), p("stranger")),
            listOf(r("dad", "me", RelationshipType.PARENT))
        )
        val m = layout(ov, gens)
        assertEquals(2, m.nodes.size)
        assertEquals(1, m.unconnectedCount)
        assertTrue(m.nodes.none { it.personId == "stranger" })
    }

    @Test
    fun 收起人数与展开提示透传() {
        val (ov, gens) = overview(
            listOf(p("me", self = true), p("dad"), p("mom", Gender.FEMALE)),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT)
            )
        )
        val m = GraphLayout.build(
            ov, gens, visibleIds = setOf("me"),
            collapsedCount = 2, expandChips = listOf(
                GraphVisibility.Chip(
                    blockId = "me", dir = GraphVisibility.Dir.UP,
                    count = 2,
                    expands = setOf(GraphVisibility.Expand(
                        "me", GraphVisibility.Dir.UP)),
                    reveals = setOf("dad", "mom"))))
        assertEquals(1, m.nodes.size)
        assertEquals(2, m.collapsedCount)
        // 单人视图卡片包围只有一卡宽：芯片比卡宽 → 横向放不进包围内，
        // 不上画布（“展开家人”面板仍保留该组入口，见 vis.groups）
        assertTrue(m.expandChips.isEmpty())
    }

    /**
     * 默认核心家庭带芯片：双方父母+夫妻+子女齐全，隐藏祖辈/兄弟只出芯片；
     * 芯片横向不越出卡片包围、不遮卡；放不下的组保留在面板分组里。
     */
    @Test
    fun 默认核心家庭_芯片横向不越界_分组人数不丢() {
        val (ov, gens) = overview(
            listOf(
                p("me", self = true), p("dad"), p("mom", Gender.FEMALE),
                p("pgf"), p("pgm", Gender.FEMALE),
                p("wife", Gender.FEMALE),
                p("wDad"), p("wMom", Gender.FEMALE),
                p("bro"), p("son")
            ),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("dad", "mom", RelationshipType.SPOUSE),
                r("pgf", "dad", RelationshipType.PARENT),
                r("pgm", "dad", RelationshipType.PARENT),
                r("me", "wife", RelationshipType.SPOUSE),
                r("wDad", "wife", RelationshipType.PARENT),
                r("wMom", "wife", RelationshipType.PARENT),
                r("wDad", "wMom", RelationshipType.SPOUSE),
                r("bro", "me", RelationshipType.SIBLING),
                r("me", "son", RelationshipType.PARENT),
                r("wife", "son", RelationshipType.PARENT)
            )
        )
        val vis = GraphVisibility.compute(ov, gens, emptySet())
        val m = GraphLayout.build(
            ov, gens, visibleIds = vis.visibleIds,
            collapsedCount = vis.collapsedCount, expandChips = vis.chips)
        // 核心人物全部入图
        val ids = m.nodes.map { it.personId }.toSet()
        assertTrue(ids.containsAll(
            listOf("me", "wife", "dad", "mom", "wDad", "wMom", "son")))
        assertTrue("隐藏祖辈/兄弟应产生芯片候选", vis.chips.isNotEmpty())
        // 画上画布的芯片：横向完整落在卡片包围内且不遮挡卡片
        val cardL = m.nodes.minOf { it.x }
        val cardR = m.nodes.maxOf { it.right }
        for (c in m.expandChips) {
            assertTrue("芯片横向越界: $c",
                c.x >= cardL - 1e-4f && c.right <= cardR + 1e-4f)
            val hit = m.nodes.any { n ->
                c.x < n.right && c.right > n.x &&
                    c.y < n.bottom && c.bottom > n.y
            }
            assertTrue("芯片遮挡卡片: $c", !hit)
            assertTrue("芯片触控高度应≥48dp", c.h >= 48f)
        }
        // 放不下的芯片不丢分组/人数：全部芯片的展开键都覆盖在面板分组内
        val groupKeys = vis.groups.flatMap { it.expands }.toSet()
        for (c in vis.chips) {
            assertTrue("芯片 $c 的分组入口缺失",
                groupKeys.containsAll(c.expands))
        }
    }

    // ---------- 两侧与大家庭 ----------

    /**
     * 模拟真机家庭结构（合成姓名）：爷爷奶奶下挂 父亲 与 姑姑一家，
     * 姥爷姥姥下挂 母亲 与 舅舅一家。
     */
    private fun deviceLikeGraph(): FamilyGraph {
        val persons = listOf(
            p("杨永", self = true),
            p("杨林"), p("马春", Gender.FEMALE),
            p("杨桓"), p("王淑", Gender.FEMALE),
            p("马丰"), p("姥妮", Gender.FEMALE),
            p("杨秀", Gender.FEMALE), p("王宝"),
            p("王蓓", Gender.FEMALE), p("杨超"),
            p("安女", Gender.FEMALE),
            p("马舅"), p("马革")
        )
        val rels = listOf(
            r("杨林", "杨永", RelationshipType.PARENT),
            r("马春", "杨永", RelationshipType.PARENT),
            r("杨林", "马春", RelationshipType.SPOUSE),
            r("杨桓", "杨林", RelationshipType.PARENT),
            r("王淑", "杨林", RelationshipType.PARENT),
            r("杨桓", "王淑", RelationshipType.SPOUSE),
            r("杨桓", "杨秀", RelationshipType.PARENT),
            r("王淑", "杨秀", RelationshipType.PARENT),
            r("马丰", "马春", RelationshipType.PARENT),
            r("姥妮", "马春", RelationshipType.PARENT),
            r("马丰", "姥妮", RelationshipType.SPOUSE),
            r("马丰", "马舅", RelationshipType.PARENT),
            r("姥妮", "马舅", RelationshipType.PARENT),
            r("马舅", "马革", RelationshipType.PARENT),
            r("王宝", "杨秀", RelationshipType.SPOUSE),
            r("王宝", "王蓓", RelationshipType.PARENT),
            r("杨秀", "王蓓", RelationshipType.PARENT),
            r("杨超", "王蓓", RelationshipType.SPOUSE),
            r("杨超", "安女", RelationshipType.PARENT),
            r("王蓓", "安女", RelationshipType.PARENT)
        )
        return FamilyGraph(persons, rels, emptyMap(), emptyMap())
    }

    @Test
    fun 模拟真机家庭_男左女右且卡片不重叠() {
        val (ov, gens) = overviewOf(deviceLikeGraph())
        val m = layout(ov, gens)
        // 父系一家在左、母系在右
        assertTrue(m.at("杨林").x < m.at("马春").x)
        assertTrue(m.at("杨秀").x < 0f)
        assertTrue(m.at("王宝").x < 0f)
        assertTrue(m.at("马舅").x > 0f)
        assertTrue(m.at("马革").x > 0f)
        // 祖辈：爷爷家在左、姥爷家在右
        assertTrue(m.at("杨桓").x < 0f)
        assertTrue(m.at("马丰").x > 0f)
        // 姑姑家的外甥女跟随父系一侧
        assertTrue(m.at("王蓓").x < 0f)
        // 同真实世代同 block 的夫妻相邻（男左女右、成员间隔）
        assertX(GraphGeom.MEMBER_GAP, m.at("杨秀").x - m.at("王宝").right)
        assertX(GraphGeom.MEMBER_GAP, m.at("王蓓").x - m.at("杨超").right)
        assertNoOverlap(m)
    }

    @Test
    fun 测试家庭55人_每侧每行至多四block且卡片不重叠() {
        val (ov, gens) = overviewOf(TestFamily.graph())
        val m = layout(ov, gens)
        // 少量家庭组保持同排；较大支系每侧最多四块后才续行。
        m.blocks.groupBy { Triple(it.generation, it.side, it.row) }
            .forEach { (k, v) ->
                assertTrue("$k 有 ${v.size} 个 block", v.size <= 4)
            }
        assertNoOverlap(m)
        // 子孙居中：直接子女不在两侧侧区
        m.blocks.filter { it.generation == 1 }.forEach { b ->
            assertTrue(
                b.memberIds.toString() + " 应居中",
                b.side == GraphSide.CENTER || b.memberIds.none {
                    it in GraphStructure.assign(
                        ov.graph,
                        GraphStructure.anchorsOf(ov.graph, ov.self.person.id),
                        ov.byId
                    ).filterValues { a -> a.side == GraphSide.CENTER }
                }
            )
        }
    }

    /**
     * 接收方视角（共享导入后“我”= 父辈）：儿子与儿媳居中，
     * 外甥女一家在父系一侧、落在姑姑/姑父的列附近。
     */
    @Test
    fun 接收方视角_外甥女跟随姑姑一侧() {
        val persons = listOf(
            p("杨永"),
            p("杨林", self = true), p("马春", Gender.FEMALE),
            p("杨桓"), p("王淑", Gender.FEMALE),
            p("马丰"), p("姥妮", Gender.FEMALE),
            p("杨秀", Gender.FEMALE), p("王宝"),
            p("王蓓", Gender.FEMALE), p("杨超"),
            p("安女", Gender.FEMALE),
            p("马舅"), p("马革")
        )
        val rels = listOf(
            r("杨林", "杨永", RelationshipType.PARENT),
            r("马春", "杨永", RelationshipType.PARENT),
            r("杨林", "马春", RelationshipType.SPOUSE),
            r("杨桓", "杨林", RelationshipType.PARENT),
            r("王淑", "杨林", RelationshipType.PARENT),
            r("杨桓", "王淑", RelationshipType.SPOUSE),
            r("杨桓", "杨秀", RelationshipType.PARENT),
            r("王淑", "杨秀", RelationshipType.PARENT),
            r("马丰", "马春", RelationshipType.PARENT),
            r("姥妮", "马春", RelationshipType.PARENT),
            r("马丰", "姥妮", RelationshipType.SPOUSE),
            r("马丰", "马舅", RelationshipType.PARENT),
            r("姥妮", "马舅", RelationshipType.PARENT),
            r("马舅", "马革", RelationshipType.PARENT),
            r("王宝", "杨秀", RelationshipType.SPOUSE),
            r("王宝", "王蓓", RelationshipType.PARENT),
            r("杨秀", "王蓓", RelationshipType.PARENT),
            r("杨超", "王蓓", RelationshipType.SPOUSE),
            r("杨超", "安女", RelationshipType.PARENT),
            r("王蓓", "安女", RelationshipType.PARENT)
        )
        val (ov, gens) = overviewOf(FamilyGraph(persons, rels, emptyMap(), emptyMap()))
        val m = layout(ov, gens)
        // 儿子（直接子女）居中
        assertTrue(abs(m.at("杨永").cx) < 60f)
        // 外甥女在 +1 代、跟随父系（左）
        assertEquals(1, m.at("王蓓").generation)
        assertTrue(m.at("王蓓").x < 0f)
        // 外甥女家的列贴近姑姑/姑父家（同侧），而非贴着儿子
        val guBlock = m.blockOf("杨秀")!!
        val nieceBlock = m.blockOf("王蓓")!!
        assertTrue(
            "外甥女侧廊应贴近姑姑家: " + nieceBlock.corridorX + " vs " +
                guBlock.corridorX,
            abs(nieceBlock.corridorX - guBlock.corridorX) <
                abs(nieceBlock.corridorX - m.blockOf("杨永")!!.corridorX)
        )
        assertNoOverlap(m)
    }

    // ---------- 各边的关系词 ----------

    private fun GraphModel.labelBetween(a: String, b: String) =
        edges.firstOrNull {
            (it.fromId == a && it.toId == b) || (it.fromId == b && it.toId == a)
        }?.label

    @Test
    fun 关系词_父母配偶子女兄弟姐妹() {
        val me = p("me", Gender.MALE, self = true)
        val dad = p("dad")
        val mom = p("mom", Gender.FEMALE)
        val wife = p("wife", Gender.FEMALE)
        val bro = p("bro")
        val son = p("son")
        val uncle = p("uncle")
        val aunt = p("aunt", Gender.FEMALE)
        val cousin = p("cousin")
        val (ov, gens) = overview(
            listOf(me, dad, mom, wife, bro, son, uncle, aunt, cousin),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("me", "wife", RelationshipType.SPOUSE),
                r("bro", "me", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
                r("me", "son", RelationshipType.PARENT),
                r("uncle", "dad", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
                r("uncle", "aunt", RelationshipType.SPOUSE),
                r("uncle", "cousin", RelationshipType.PARENT),
            )
        )
        val m = layout(ov, gens)
        assertEquals("父子", m.labelBetween("dad", "me"))
        assertEquals("母子", m.labelBetween("mom", "me"))
        assertEquals("夫妻", m.labelBetween("me", "wife"))
        assertEquals("兄弟", m.labelBetween("bro", "me"))
        assertEquals("父子", m.labelBetween("me", "son"))
        assertEquals("兄弟", m.labelBetween("uncle", "dad"))
        assertEquals("夫妻", m.labelBetween("uncle", "aunt"))
        assertEquals("父子", m.labelBetween("uncle", "cousin"))
    }

    @Test
    fun 关系词_女性视角() {
        val me = p("me", Gender.FEMALE, self = true)
        val broO = p("broO")
        val broY = p("broY")
        val mom = p("mom", Gender.FEMALE)
        val dau = p("dau", Gender.FEMALE)
        val (ov, gens) = overview(
            listOf(me, broO, broY, mom, dau),
            listOf(
                r("broO", "me", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
                r("broY", "me", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
                r("mom", "me", RelationshipType.PARENT),
                r("me", "dau", RelationshipType.PARENT),
            )
        )
        val m = layout(ov, gens)
        assertEquals("兄妹", m.labelBetween("broO", "me"))
        assertEquals("姐弟", m.labelBetween("broY", "me"))
        assertEquals("母女", m.labelBetween("mom", "me"))
        assertEquals("母女", m.labelBetween("me", "dau"))
    }

    @Test
    fun 关系词_CHILD记录与未知长幼() {
        val me = p("me", Gender.MALE, self = true)
        val son = p("son")
        val bro = p("bro")
        val (ov, gens) = overview(
            listOf(me, son, bro),
            listOf(
                r("son", "me", RelationshipType.CHILD),
                r("bro", "me", RelationshipType.SIBLING),
            )
        )
        val m = layout(ov, gens)
        assertEquals("父子", m.labelBetween("son", "me"))
        assertEquals("兄弟", m.labelBetween("bro", "me"))
    }

    /** 关系词落点：由路由产出、不压卡片。 */
    @Test
    fun 关系词落点_都在卡片外且每词一个锚点() {
        val (ov, gens) = overview(
            listOf(
                p("me", Gender.MALE, self = true), p("dad"),
                p("mom", Gender.FEMALE), p("wife", Gender.FEMALE),
                p("son")
            ),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("me", "wife", RelationshipType.SPOUSE),
                r("me", "son", RelationshipType.PARENT)
            )
        )
        val m = layout(ov, gens)
        val routing = GraphEdgeRouting.route(m)
        val pts = GraphEdgeLabels.compute(routing)
        assertEquals(4, pts.size)   // 父子/母子/夫妻/父子(me→son)
        // 锚点不落在任何卡片内部
        pts.forEach { lp ->
            val inside = m.nodes.any { n ->
                lp.x > n.x + 1e-3f && lp.x < n.right - 1e-3f &&
                    lp.y > n.y + 1e-3f && lp.y < n.bottom - 1e-3f
            }
            assertTrue("标签压在卡片上: $lp", !inside)
        }
    }

    // ---------- 主审探针场景：紧凑布局 / 实测卡片 / 芯片入界 ----------

    /**
     * 双方父母 + 本人夫妻 + 子女的默认核心家庭：两代块在上方左右并列、
     * 主锚夫妻居中、子女在下，全部卡片横向跨度保持紧凑
     * （普通手机 360dp 宽即可完整呈现，不留未展开旁支的预留空白）。
     */
    @Test
    fun 默认核心家庭_紧凑布局_双边父母在上子女在下() {
        val (ov, gens) = overview(
            listOf(
                p("me", self = true), p("dad"), p("mom", Gender.FEMALE),
                p("wife", Gender.FEMALE),
                p("wDad"), p("wMom", Gender.FEMALE),
                p("son")
            ),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("dad", "mom", RelationshipType.SPOUSE),
                r("me", "wife", RelationshipType.SPOUSE),
                r("wDad", "wife", RelationshipType.PARENT),
                r("wMom", "wife", RelationshipType.PARENT),
                r("wDad", "wMom", RelationshipType.SPOUSE),
                r("me", "son", RelationshipType.PARENT),
                r("wife", "son", RelationshipType.PARENT)
            )
        )
        val m = layout(ov, gens)
        // 双方父母各成一块，都落在本人夫妻上方（上一代 band）
        val dadB = m.blockOf("dad")!!; val wDadB = m.blockOf("wDad")!!
        assertEquals(-1, dadB.generation)
        assertEquals(-1, wDadB.generation)
        assertTrue(m.at("dad").bottom <= m.at("me").y)
        assertTrue(m.at("wDad").bottom <= m.at("wife").y)
        // 男方支系在左半区、女方支系在右半区
        assertEquals(GraphSide.LEFT, dadB.side)
        assertEquals(GraphSide.RIGHT, wDadB.side)
        assertTrue(m.at("dad").cx < 0f)
        assertTrue(m.at("wDad").cx > 0f)
        // 子女在下一代 band、位于居中区
        assertEquals(1, m.at("son").generation)
        assertTrue(m.at("son").y >= m.at("me").bottom)
        // 紧凑：全部卡片横向跨度 <= 400dp（72×2 卡 + 8 间隔的块、块间 20 起）
        val span = m.nodes.maxOf { it.right } - m.nodes.minOf { it.x }
        assertTrue("卡片横向跨度过大: $span", span <= 400f)
        assertNoOverlap(m)
    }

    @Test
    fun 长中文名与长称谓_卡片加宽加高且不重叠() {
        val longName = "欧阳建国伟业长青"
        val longTitle = "外曾祖父的弟弟的长子"
        val me = p("me", self = true)
        val dad = p("dad").copy(name = longName)
        val (ov, gens) = overview(
            listOf(me, dad),
            listOf(r("dad", "me", RelationshipType.PARENT))
        )
        // 注入自定义长称谓模拟长文字
        val byId = ov.byId.toMutableMap()
        byId["dad"] = byId.getValue("dad").copy(customTitle = longTitle)
        val ov2 = ov.copy(byId = byId)
        val m = GraphLayout.build(ov2, gens)
        val dn = m.at("dad")
        // 姓名超过最小宽 → 卡加宽（最多 CARD_MAX_W）或增高（折行）
        assertTrue(dn.w >= GraphGeom.CARD_MIN_W)
        assertTrue(dn.w <= GraphGeom.CARD_MAX_W + 1e-4f)
        assertTrue(dn.h >= GraphGeom.CARD_MIN_H)
        assertNoOverlap(m)
    }

    @Test
    fun 芯片完整矩形计入路由bounds与聚焦范围() {
        // 有隐藏祖辈的家庭：dad 上方芯片可容纳（块内无可见父母入线、
        // 横向在卡片包围内）→ 留下的芯片矩形计入 bounds 与聚焦范围
        val (ov, gens) = overview(
            listOf(
                p("me", self = true), p("dad"), p("mom", Gender.FEMALE),
                p("pgf"), p("pgm", Gender.FEMALE)
            ),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("dad", "mom", RelationshipType.SPOUSE),
                r("pgf", "dad", RelationshipType.PARENT),
                r("pgm", "dad", RelationshipType.PARENT)
            )
        )
        val vis = GraphVisibility.compute(ov, gens, emptySet())
        val m = GraphLayout.build(
            ov, gens, visibleIds = vis.visibleIds,
            collapsedCount = vis.collapsedCount, expandChips = vis.chips)
        assertTrue("应有入图芯片", m.expandChips.isNotEmpty())
        m.expandChips.forEach { assertTrue(it.h >= 48f) }
        val routing = GraphEdgeRouting.route(m)
        for (c in routing.chips) {
            // bounds 覆盖芯片完整矩形
            assertTrue(c.x >= routing.bounds.left - 1e-4f)
            assertTrue(c.right <= routing.bounds.right + 1e-4f)
            assertTrue(c.y >= routing.bounds.top - 1e-4f)
            assertTrue(c.bottom <= routing.bounds.bottom + 1e-4f)
            // 聚焦范围 = 块卡片 ∪ 贴附芯片
            val fb = m.focusBlockOf(
                m.blocks.first { it.id == c.blockId }.memberIds.first())!!
            assertTrue(c.x >= fb.left - 1e-4f && c.right <= fb.right + 1e-4f)
            assertTrue(c.y >= fb.top - 1e-4f && c.bottom <= fb.bottom + 1e-4f)
        }
    }
}
