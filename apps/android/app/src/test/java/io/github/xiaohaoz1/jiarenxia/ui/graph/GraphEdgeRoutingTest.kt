package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.backup.TestFamily
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** GraphEdgeRouting 阶段B：真实 generation 路由、侧廊/通道、共用折线输出。 */
class GraphEdgeRoutingTest {

    private val engine = KinshipEngine()
    private var seq = 0

    private fun p(id: String, gender: Gender = Gender.MALE, self: Boolean = false) =
        Person(id = id, name = id, gender = gender, isSelf = self)
    private fun r(src: String, tgt: String, type: RelationshipType) =
        Relationship("r" + (++seq), src, tgt, type, null)

    private fun modelOf(
        persons: List<Person>, rels: List<Relationship>
    ): GraphModel {
        val graph = FamilyGraph(persons, rels)
        val self = persons.first { it.isSelf }
        val results = engine.resolveAll(graph, self.id)
        val byId = persons.associate {
            it.id to PersonOverview(it, results.getValue(it.id), emptyList())
        }
        val ov = FamilyOverview(
            self = byId.getValue(self.id), graph = graph, byId = byId,
            core = emptyList(), others = emptyList())
        return GraphLayout.build(ov, engine.generations(graph, self.id))
    }

    private fun familyModel(): GraphModel {
        val g = TestFamily.graph()
        val self = g.persons.first { it.isSelf }
        val results = engine.resolveAll(g, self.id)
        val byId = g.persons.associate {
            it.id to PersonOverview(it, results.getValue(it.id), emptyList())
        }
        val ov = FamilyOverview(
            self = byId.getValue(self.id), graph = g, byId = byId,
            core = emptyList(), others = emptyList())
        return GraphLayout.build(ov, engine.generations(g, self.id))
    }

    // ---------- 家庭单元分组 ----------

    @Test
    fun 同组父母与子女复用一条家庭总线() {
        val m = modelOf(
            listOf(p("me", self = true), p("dad"), p("mom", Gender.FEMALE)),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT)
            )
        )
        val routing = GraphEdgeRouting.route(m)
        // 一条横向总线（edge==null 的纯横线段）覆盖父母主干与子女落点
        val buses = routing.paths.filter { p ->
            p.edge == null && p.points.size == 2 &&
                p.points[0].y == p.points[1].y
        }
        assertEquals(1, buses.size)
        // dad/mom 无配偶记录 → 两个单人块：主干在两人卡心中点
        val dadN = m.nodes.first { it.personId == "dad" }
        val momN = m.nodes.first { it.personId == "mom" }
        val meN = m.nodes.first { it.personId == "me" }
        val stem = (dadN.cx + momN.cx) / 2f
        val l = buses[0].points[0].x; val rr = buses[0].points[1].x
        assertTrue(l <= minOf(stem, meN.cx) + 1e-4f)
        assertTrue(rr >= maxOf(stem, meN.cx) - 1e-4f)
        // 同块父母：主干（家庭中点）+ 总线 + 子女直落
        val solid = routing.paths.count {
            it.edge == null && it.style == GraphEdgeRouting.RouteStyle.SOLID
        }
        assertTrue(solid >= 3)
    }

    @Test
    fun 同块夫妻主干从家庭中点下落() {
        val m = modelOf(
            listOf(p("me", self = true), p("dad"), p("mom", Gender.FEMALE)),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("dad", "mom", RelationshipType.SPOUSE)
            )
        )
        val routing = GraphEdgeRouting.route(m)
        val dad = m.nodes.first { it.personId == "dad" }
        val mom = m.nodes.first { it.personId == "mom" }
        val midX = (minOf(dad.x, mom.x) +
                (if (dad.x <= mom.x) dad.w else mom.w) +
                maxOf(dad.x, mom.x)) / 2f
        val midY = minOf(dad.cy, mom.cy)
        // 主干段起点 = 两卡之间的配偶线高度中点，直落不绕侧廊
        assertTrue(routing.paths.any { p ->
            p.edge == null && p.points.size == 2 &&
                abs(p.points[0].x - midX) < 1e-3f &&
                abs(p.points[0].y - midY) < 1e-3f &&
                abs(p.points[1].x - midX) < 1e-3f
        })
    }

    @Test
    fun 简单三人家庭_连线不出卡片横向范围() {
        // 主审探针：夫妻 + 一个子女，无障碍时全部折线点
        // 都落在三张卡片的横向包围范围内（不绕外侧/不进侧廊）
        val m = modelOf(
            listOf(p("me", self = true), p("dad"), p("mom", Gender.FEMALE)),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("dad", "mom", RelationshipType.SPOUSE)
            )
        )
        val routing = GraphEdgeRouting.route(m)
        val minX = m.nodes.minOf { it.x }
        val maxX = m.nodes.maxOf { it.right }
        val bad = StringBuilder()
        for (p in routing.paths) {
            if (p.edge != null) continue   // 只检查家庭主干/总线/直落段
            for (q in p.points) {
                if (q.x < minX - 1e-3f || q.x > maxX + 1e-3f) {
                    bad.append("(${q.x},${q.y}) ")
                }
            }
        }
        assertTrue("家庭连线绕出卡片横向范围: " + bad, bad.isEmpty())
    }

    @Test
    fun 不同家庭单元不共用同一条横线() {
        // 我+妻 → 子；兄+嫂 → 侄：两个家庭单元在同一代际通道
        val m = modelOf(
            listOf(
                p("me", self = true), p("wife", Gender.FEMALE),
                p("bro"), p("bWife", Gender.FEMALE),
                p("son"), p("nephew")
            ),
            listOf(
                r("me", "wife", RelationshipType.SPOUSE),
                r("bro", "bWife", RelationshipType.SPOUSE),
                r("bro", "me", RelationshipType.SIBLING),
                r("me", "son", RelationshipType.PARENT),
                r("wife", "son", RelationshipType.PARENT),
                r("bro", "nephew", RelationshipType.PARENT),
                r("bWife", "nephew", RelationshipType.PARENT)
            )
        )
        val routing = GraphEdgeRouting.route(m)
        val buses = routing.paths.filter { p ->
            p.edge == null && p.points.size == 2 &&
                p.points[0].y == p.points[1].y
        }
        assertEquals(2, buses.size)
        val y1 = buses[0].points[0].y; val y2 = buses[1].points[0].y
        // 两条总线要么 y 不同（分道），要么横向不重叠（同道不同段）
        val overlap = buses[0].points[0].x <= buses[1].points[1].x &&
            buses[1].points[0].x <= buses[0].points[1].x
        assertTrue(
            "两个家庭单元的总线不应是同一条线",
            y1 != y2 || !overlap
        )
    }

    // ---------- 每条可见关系恰好画一次 ----------

    /** 边是否在路由中被表达：edge 路径或所在家庭单元的端点组合。 */
    private fun covered(e: GraphEdge, m: GraphModel, routing: GraphEdgeRouting.Routing): Boolean {
        if (routing.paths.any { it.edge == e }) return true
        // 家庭单元：边端点都在单元的父母/子女端里，且代际相邻
        val (p, c) = when (e.type) {
            RelationshipType.PARENT -> e.fromId to e.toId
            RelationshipType.CHILD -> e.toId to e.fromId
            else -> return false
        }
        val np = m.nodes.firstOrNull { it.personId == p } ?: return false
        val nc = m.nodes.firstOrNull { it.personId == c } ?: return false
        if (nc.generation != np.generation + 1) return false
        // 单元路径存在：单亲/非同块父母从该人卡下端口出线
        if (routing.paths.any { path ->
                path.edge == null && path.points.size >= 2 &&
                    abs(path.points[0].x - np.cx) < 1e-3f &&
                    abs(path.points[0].y - np.bottom) < 1e-3f
            }) return true
        // 同块夫妻且两人都是该子女的记录父母：主干从夫妻中点下落
        val blk = m.blockOf(p) ?: return false
        if (blk.memberIds.size != 2) return false
        val ns = blk.memberIds.mapNotNull { id ->
            m.nodes.firstOrNull { it.personId == id } }
        if (ns.size != 2) return false
        val parentsOfC = m.edges.mapNotNull { ee ->
            val pc = when (ee.type) {
                RelationshipType.PARENT -> ee.fromId to ee.toId
                RelationshipType.CHILD -> ee.toId to ee.fromId
                else -> null
            }
            pc?.takeIf { it.second == c }?.first
        }.toSet()
        if (!parentsOfC.containsAll(blk.memberIds)) return false
        val (l, r) = ns.sortedBy { it.x }.let { it[0] to it[1] }
        val midX = (l.right + r.x) / 2f
        val midY = minOf(l.cy, r.cy)
        return routing.paths.any { path ->
            path.edge == null && path.points.size >= 2 &&
                abs(path.points[0].x - midX) < 1e-3f &&
                abs(path.points[0].y - midY) < 1e-3f
        }
    }

    @Test
    fun 单一已知父母_从该人下端口出线() {
        // 只有父亲一条记录：主干从 dad 卡下端口出发，
        // 不从虚构的“夫妻中点”下桩
        val m = modelOf(
            listOf(p("me", self = true), p("dad"), p("son")),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("me", "son", RelationshipType.PARENT)
            )
        )
        val routing = GraphEdgeRouting.route(m)
        val dad = m.nodes.first { it.personId == "dad" }
        assertTrue(routing.paths.any { p ->
            p.edge == null && p.points.size >= 2 &&
                abs(p.points[0].x - dad.cx) < 1e-3f &&
                abs(p.points[0].y - dad.bottom) < 1e-3f
        })
        // 不虚构配偶：dad 所在 block 无配偶，也无中点主干段
        assertTrue(routing.paths.none { p ->
            p.edge == null && p.points.size >= 2 &&
                abs(p.points[0].y - dad.cy) < 1e-3f
        })
    }

    @Test
    fun 两位已知父母不同块_各自下端口加水平连接() {
        // dad 另有配偶 w2（dad+w2 同块），me 的母亲 mom 单人块：
        // me 的父母是 dad+mom 不同块 → 各自下端口 + 匚形水平连接段
        val m = modelOf(
            listOf(
                p("me", self = true), p("dad"), p("mom", Gender.FEMALE),
                p("w2", Gender.FEMALE)
            ),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("dad", "w2", RelationshipType.SPOUSE)
            )
        )
        val routing = GraphEdgeRouting.route(m)
        val dad = m.nodes.first { it.personId == "dad" }
        val mom = m.nodes.first { it.personId == "mom" }
        // 水平连接段在两人所在展示行整行底缘之下（w2 卡比 dad 高，
        // 取 maxOf(父母.bottom) 会穿过 w2）
        val band = m.bands.single { it.generation == -1 }
        val joinY = band.rowBottom(0) + 8f
        // 两人下端口之间有一条水平连接段
        val xs = listOf(dad.cx, mom.cx).sorted()
        assertTrue(routing.paths.any { p ->
            p.edge == null && p.points.size == 2 &&
                abs(p.points[0].x - xs[0]) < 1e-3f &&
                abs(p.points[1].x - xs[1]) < 1e-3f &&
                abs(p.points[0].y - joinY) < 1e-3f &&
                p.points[0].y == p.points[1].y
        })
    }

    @Test
    fun 仅兄妹记录_虚线表达且不虚构父母边() {
        val m = modelOf(
            listOf(p("me", self = true), p("bro")),
            listOf(r("bro", "me", RelationshipType.SIBLING))
        )
        val routing = GraphEdgeRouting.route(m)
        // 兄弟边以虚线独立表达
        assertTrue(routing.paths.any {
            it.edge?.type == RelationshipType.SIBLING &&
                it.style == GraphEdgeRouting.RouteStyle.DASHED
        })
        // 不虚构父母：没有任何无记录来源的实线家庭单元段
        assertTrue(routing.paths.none {
            it.style == GraphEdgeRouting.RouteStyle.SOLID
        })
    }

    @Test
    fun 测试家庭_每条可见关系恰好表达一次() {
        val m = familyModel()
        val routing = GraphEdgeRouting.route(m)
        for (e in m.edges) {
            val direct = routing.paths.count { it.edge == e }
            assertTrue("边 ${e.fromId}→${e.toId}(${e.type}) 重复路径",
                direct <= 1)
            assertTrue("边 ${e.fromId}→${e.toId}(${e.type}) 未被表达",
                covered(e, m, routing))
        }
        // 兄弟虚线保留、堂表亲虚线保留
        m.edges.filter { it.type == RelationshipType.COUSIN }.forEach { e ->
            assertTrue(routing.paths.any {
                it.edge == e && it.style == GraphEdgeRouting.RouteStyle.DASHED
            })
        }
    }

    // ---------- 不穿过卡片 / bounds 完整 ----------

    /** 轴对齐线段与卡片内部是否相交（允许贴边，不允许穿内部）。 */
    private fun segCrossesCard(
        a: GraphEdgeRouting.Pt, b: GraphEdgeRouting.Pt, n: GraphNode
    ): Boolean {
        val eps = 1e-3f
        val l = n.x + eps; val rr = n.right - eps
        val t = n.y + eps; val bb = n.bottom - eps
        return if (a.x == b.x) {
            // 竖线：x 落在卡内、y 范围与卡内高度重叠
            a.x in l..rr &&
                minOf(a.y, b.y) < bb && maxOf(a.y, b.y) > t
        } else {
            a.y in t..bb &&
                minOf(a.x, b.x) < rr && maxOf(a.x, b.x) > l
        }
    }

    @Test
    fun 折线不穿过无关卡片() {
        for (m in listOf(familyModel(), modelOf(
            listOf(
                p("me", self = true), p("dad"), p("mom", Gender.FEMALE),
                p("u1"), p("u2"), p("u3"), p("u4"),
                p("son"), p("gson")
            ),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("dad", "mom", RelationshipType.SPOUSE),
                r("u1", "dad", RelationshipType.SIBLING),
                r("u2", "dad", RelationshipType.SIBLING),
                r("u3", "dad", RelationshipType.SIBLING),
                r("u4", "dad", RelationshipType.SIBLING),
                r("me", "son", RelationshipType.PARENT),
                r("son", "gson", RelationshipType.PARENT),
                r("u1", "gson", RelationshipType.COUSIN)
            )
        ))) {
            val routing = GraphEdgeRouting.route(m)
            val bad = StringBuilder()
            for (p in routing.paths) {
                p.points.zipWithNext().forEach { (a, b) ->
                    m.nodes.forEach { n ->
                        if (segCrossesCard(a, b, n)) {
                            bad.append("(${a.x},${a.y})→(${b.x},${b.y})穿${n.personId} ")
                        }
                    }
                }
            }
            assertTrue("路径穿过卡片: " + bad, bad.isEmpty())
        }
    }

    @Test
    fun bounds覆盖节点与全部路径点() {
        val m = familyModel()
        val routing = GraphEdgeRouting.route(m)
        val b = routing.bounds
        m.nodes.forEach { n ->
            assertTrue(n.x >= b.left - 1e-4f)
            assertTrue(n.right <= b.right + 1e-4f)
            assertTrue(n.y >= b.top - 1e-4f)
            assertTrue(n.bottom <= b.bottom + 1e-4f)
        }
        routing.paths.forEach { p ->
            p.points.forEach { q ->
                assertTrue(q.x >= b.left - 1e-4f && q.x <= b.right + 1e-4f)
                assertTrue(q.y >= b.top - 1e-4f && q.y <= b.bottom + 1e-4f)
            }
        }
        routing.labels.forEach { l ->
            assertTrue(l.x >= b.left - 1e-4f && l.x <= b.right + 1e-4f)
            assertTrue(l.y >= b.top - 1e-4f && l.y <= b.bottom + 1e-4f)
        }
    }

    @Test
    fun 跨展示行兄弟边经侧廊折线() {
        // 4 位叔叔超过 2 列 → 分多行；堂表亲跨行连接
        val m = modelOf(
            listOf(
                p("me", self = true), p("dad"),
                p("u1"), p("u2"), p("u3"), p("cousin")
            ),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("u1", "dad", RelationshipType.SIBLING),
                r("u2", "dad", RelationshipType.SIBLING),
                r("u3", "dad", RelationshipType.SIBLING),
                r("cousin", "me", RelationshipType.COUSIN)
            )
        )
        val routing = GraphEdgeRouting.route(m)
        val dashed = routing.paths.filter {
            it.style == GraphEdgeRouting.RouteStyle.DASHED
        }
        // 堂表亲边存在且是折线（不是简单直线穿过卡片）
        assertTrue(dashed.any { it.edge?.type == RelationshipType.COUSIN })
        val cousinPath = dashed.first { it.edge?.type == RelationshipType.COUSIN }
        assertTrue(cousinPath.points.size >= 4)
    }

    @Test
    fun 配偶路径为两卡间短横线() {
        val m = modelOf(
            listOf(p("me", self = true), p("wife", Gender.FEMALE)),
            listOf(r("me", "wife", RelationshipType.SPOUSE))
        )
        val routing = GraphEdgeRouting.route(m)
        val sp = routing.paths.single {
            it.style == GraphEdgeRouting.RouteStyle.SPOUSE
        }
        // 夫妻短连接：一条水平段，恰好跨越两卡间隙
        assertEquals(2, sp.points.size)
        assertEquals(sp.points[0].y, sp.points[1].y)
        val me = m.nodes.first { it.personId == "me" }
        val wife = m.nodes.first { it.personId == "wife" }
        val xs = sp.points.map { it.x }.sorted()
        val (l, r) = if (me.x <= wife.x) me to wife else wife to me
        assertTrue(xs[0] >= l.right - 1e-3f)
        assertTrue(xs[1] <= r.x + 1e-3f)
    }

    @Test
    fun 跨band松边走内部空列_不绕整张图外缘() {
        // u1（父辈）→ gson（孙辈）堂表亲：跨越多 band
        val m = modelOf(
            listOf(
                p("me", self = true), p("dad"), p("u1"),
                p("son"), p("gson")
            ),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("u1", "dad", RelationshipType.SIBLING),
                r("me", "son", RelationshipType.PARENT),
                r("son", "gson", RelationshipType.PARENT),
                r("u1", "gson", RelationshipType.COUSIN)
            )
        )
        val routing = GraphEdgeRouting.route(m)
        val span = routing.paths.firstOrNull {
            it.edge?.type == RelationshipType.COUSIN
        }!!
        val minX = m.nodes.minOf { it.x }
        val maxX = m.nodes.maxOf { it.right }
        // 旧实现会绕到图外侧缘；新实现走内部空列（留廊缝余量）
        span.points.forEach { q ->
            assertTrue("跨band点超出内部范围: ${q.x}",
                q.x >= minX - 20f && q.x <= maxX + 20f)
        }
    }
}
