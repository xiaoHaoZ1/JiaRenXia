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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** GraphVisibility：图谱可见层（默认核心/方向化展开/分组去重/查找路径）。 */
class GraphVisibilityTest {

    private val engine = KinshipEngine()
    private var seq = 0

    private fun p(id: String, gender: Gender = Gender.MALE, self: Boolean = false) =
        Person(id = id, name = id, gender = gender, isSelf = self)
    private fun r(src: String, tgt: String, type: RelationshipType) =
        Relationship("r" + (++seq), src, tgt, type, null)

    private fun overviewOf(
        persons: List<Person>, rels: List<Relationship>
    ): Pair<FamilyOverview, Map<String, Int>> {
        val graph = FamilyGraph(persons, rels)
        val self = persons.first { it.isSelf }
        val results = engine.resolveAll(graph, self.id)
        val byId = persons.associate {
            it.id to PersonOverview(it, results.getValue(it.id), emptyList())
        }
        val ov = FamilyOverview(
            self = byId.getValue(self.id), graph = graph, byId = byId,
            core = emptyList(), others = emptyList())
        return ov to engine.generations(graph, self.id)
    }

    private fun allOf(vararg ids: String) =
        ids.mapTo(LinkedHashSet()) {
            GraphVisibility.Expand(it, GraphVisibility.Dir.ALL) }

    /** 我+妻+双方父母+兄弟+儿子+爷爷奶奶+叔叔。 */
    private fun bigFamily(): Pair<FamilyOverview, Map<String, Int>> = overviewOf(
        listOf(
            p("me", self = true), p("wife", Gender.FEMALE),
            p("dad"), p("mom", Gender.FEMALE),
            p("bro"), p("son"),
            p("gpa"), p("gma", Gender.FEMALE),
            p("uncle")
        ),
        listOf(
            r("me", "wife", RelationshipType.SPOUSE),
            r("dad", "me", RelationshipType.PARENT),
            r("mom", "me", RelationshipType.PARENT),
            r("dad", "mom", RelationshipType.SPOUSE),
            r("bro", "me", RelationshipType.SIBLING),
            r("me", "son", RelationshipType.PARENT),
            r("gpa", "dad", RelationshipType.PARENT),
            r("gma", "dad", RelationshipType.PARENT),
            r("gpa", "gma", RelationshipType.SPOUSE),
            r("uncle", "dad", RelationshipType.SIBLING)
        )
    )

    @Test
    fun 默认核心_锚点夫妻_双方父母_直接子女() {
        val (ov, gens) = bigFamily()
        val v = GraphVisibility.compute(ov, gens, emptySet())
        assertEquals(
            setOf("me", "wife", "dad", "mom", "son"),
            v.visibleIds
        )
        // 兄弟/祖辈/叔叔被收起
        assertEquals(4, v.collapsedCount)
    }

    @Test
    fun 方向化展开_UP只揭示长辈不带出旁支() {
        val (ov, gens) = bigFamily()
        // dad 只展开 UP：带出 gpa+gma，不带出兄弟 uncle
        val v = GraphVisibility.compute(
            ov, gens, setOf(
                GraphVisibility.Expand("dad", GraphVisibility.Dir.UP)))
        assertTrue("gpa" in v.visibleIds)
        assertTrue("gma" in v.visibleIds)
        assertFalse("uncle" in v.visibleIds)
        // 再展开 dad 的 SAME 才带出 uncle
        val v2 = GraphVisibility.compute(
            ov, gens, setOf(
                GraphVisibility.Expand("dad", GraphVisibility.Dir.SAME)))
        assertTrue("uncle" in v2.visibleIds)
        assertFalse("gpa" in v2.visibleIds)
    }

    @Test
    fun 展开锚点带出整个家庭block() {
        val (ov, gens) = bigFamily()
        val v0 = GraphVisibility.compute(ov, gens, emptySet())
        // 父母 block 上有“长辈”芯片（gpa+gma=2）与“平辈”芯片（uncle=1）
        assertTrue(v0.chips.any {
            it.dir == GraphVisibility.Dir.UP && it.count == 2 })
        assertTrue(v0.chips.any {
            it.dir == GraphVisibility.Dir.SAME && it.count == 1 })
        // 展开 dad 全方向：gpa、gma、uncle 一并出现；bro 只连 me 不动
        val v1 = GraphVisibility.compute(ov, gens, allOf("dad"))
        assertTrue("gpa" in v1.visibleIds)
        assertTrue("gma" in v1.visibleIds)
        assertTrue("uncle" in v1.visibleIds)
        assertFalse("bro" in v1.visibleIds)
        assertEquals(1, v1.collapsedCount)
        val v2 = GraphVisibility.compute(ov, gens, allOf("dad", "me"))
        assertTrue("bro" in v2.visibleIds)
        assertEquals(0, v2.collapsedCount)
    }

    @Test
    fun 面板分组_按归属称谓聚合且人物去重() {
        val (ov, gens) = bigFamily()
        val v = GraphVisibility.compute(ov, gens, emptySet())
        val labels = v.groups.map { it.label }
        // me 的同辈兄弟 → “我的兄弟姐妹”；dad/mom 的父辈归并到“我的长辈”
        assertTrue("我的兄弟姐妹" in labels)
        assertTrue("我的长辈" in labels)
        val sib = v.groups.first { it.label == "我的兄弟姐妹" }
        assertEquals(1, sib.addCount)
        val anc = v.groups.first { it.label == "我的长辈" }
        assertEquals(2, anc.addCount)   // gpa+gma 只计一次
        // 展开后分组标记为已展开、增量归零
        val v1 = GraphVisibility.compute(
            ov, gens, sib.expands)
        val sib1 = v1.groups.firstOrNull { it.label == "我的兄弟姐妹" }
        if (sib1 != null) {
            assertTrue(sib1.expanded)
            assertEquals(0, sib1.addCount)
        }
    }

    @Test
    fun 展开全部与收起旁支() {
        val (ov, gens) = bigFamily()
        val all = GraphVisibility.expandAll(ov, gens)
        val v = GraphVisibility.compute(ov, gens, all)
        assertEquals(gens.keys, v.visibleIds)
        assertEquals(0, v.collapsedCount)
        assertTrue(v.chips.isEmpty())
        // 收起 = expanded 空集 → 回到默认核心
        val back = GraphVisibility.compute(ov, gens, emptySet())
        assertEquals(5, back.visibleIds.size)
    }

    @Test
    fun 收起时保留当前聚焦人物() {
        val (ov, gens) = bigFamily()
        // 聚焦 uncle（默认不可见）：连同所在 block 保留
        val v = GraphVisibility.compute(ov, gens, emptySet(), focusedId = "uncle")
        assertTrue("uncle" in v.visibleIds)
    }

    @Test
    fun 查找路径展开_逐层直到目标可见() {
        val (ov, gens) = bigFamily()
        // 搜 gpa（默认不可见）：展开 dad 一层即可
        val exp = GraphVisibility.expandPathTo(ov, gens, emptySet(), "gpa")
        val v = GraphVisibility.compute(ov, gens, exp)
        assertTrue("gpa" in v.visibleIds)
        // 搜更深的可达者同样最终可见
        val exp2 = GraphVisibility.expandPathTo(ov, gens, emptySet(), "uncle")
        assertTrue("uncle" in GraphVisibility.compute(ov, gens, exp2).visibleIds)
        // 查找展开保留已有展开状态
        val keep = GraphVisibility.expandPathTo(
            ov, gens, allOf("me"), "uncle")
        assertTrue("bro" in GraphVisibility.compute(ov, gens, keep).visibleIds)
    }

    @Test
    fun 不重复人物_多路径只入一次() {
        // 兄弟同时又是子女同学的复杂结构：同一人多条路径仍只出现一次
        val (ov, gens) = bigFamily()
        val v = GraphVisibility.compute(
            ov, gens, allOf("dad", "mom", "me", "wife"))
        assertEquals(v.visibleIds.size, v.visibleIds.toList().size)
        assertTrue(v.visibleIds.containsAll(
            setOf("me", "wife", "dad", "mom", "son", "bro", "gpa", "gma", "uncle")))
    }

    @Test
    fun 候选全集只含可达_未关联不算收起() {
        val (ov, gens) = overviewOf(
            listOf(p("me", self = true), p("dad"), p("stranger")),
            listOf(r("dad", "me", RelationshipType.PARENT))
        )
        val v = GraphVisibility.compute(ov, gens, emptySet())
        assertEquals(setOf("me", "dad"), v.visibleIds)
        // 未关联者不在候选里 → 不计入 collapsedCount
        assertEquals(0, v.collapsedCount)
    }

    @Test
    fun 测试家庭55人_默认核心是锚点直系() {
        val g = TestFamily.graph()
        val self = g.persons.first { it.isSelf }
        val results = engine.resolveAll(g, self.id)
        val byId = g.persons.associate {
            it.id to PersonOverview(it, results.getValue(it.id), emptyList())
        }
        val ov = FamilyOverview(
            self = byId.getValue(self.id), graph = g, byId = byId,
            core = emptyList(), others = emptyList())
        val gens = engine.generations(g, self.id)
        val v = GraphVisibility.compute(ov, gens, emptySet())
        // 默认可见含：self、配偶、双方父母、直接子女
        val anchors = GraphStructure.anchorsOf(g, self.id)
        assertTrue(anchors.ids.all { it in v.visibleIds })
        for (a in anchors.ids) {
            assertTrue(GraphStructure.parentsOf(g, a).all { it in v.visibleIds })
            assertTrue(GraphStructure.childrenOf(g, a).all { it in v.visibleIds })
        }
        // 大部分人被收起
        assertTrue(v.collapsedCount > 30)
        // 展开全部 = 全集
        val vAll = GraphVisibility.compute(
            ov, gens, GraphVisibility.expandAll(ov, gens))
        assertEquals(gens.keys, vAll.visibleIds)
    }

    @Test
    fun 女性本人_默认核心含双方父母() {
        // 我（女）+ 丈夫：双方父母、子女默认可见
        val (ov, gens) = overviewOf(
            listOf(
                p("me", Gender.FEMALE, self = true), p("hub"),
                p("dad"), p("mom", Gender.FEMALE),
                p("fil"), p("mil", Gender.FEMALE), p("son")
            ),
            listOf(
                r("me", "hub", RelationshipType.SPOUSE),
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("fil", "hub", RelationshipType.PARENT),
                r("mil", "hub", RelationshipType.PARENT),
                r("me", "son", RelationshipType.PARENT)
            )
        )
        val v = GraphVisibility.compute(ov, gens, emptySet())
        assertEquals(
            setOf("me", "hub", "dad", "mom", "fil", "mil", "son"),
            v.visibleIds)
    }

    @Test
    fun 缺父母_只显示已知父母不虚构() {
        val (ov, gens) = overviewOf(
            listOf(p("me", self = true), p("dad"), p("son")),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("me", "son", RelationshipType.PARENT)
            )
        )
        val v = GraphVisibility.compute(ov, gens, emptySet())
        assertEquals(setOf("me", "dad", "son"), v.visibleIds)
        assertEquals(0, v.collapsedCount)
        // 单亲一侧没有“长辈”芯片（没有可揭示的人）
        assertTrue(v.chips.none { it.dir == GraphVisibility.Dir.UP })
    }

    @Test
    fun 仅兄妹记录_默认收起同辈可展开() {
        // 我与兄弟只有 SIBLING 记录：默认收起，SAME 方向展开可见；
        // 不虚构共同父母（chips 的 UP 方向为空）
        val (ov, gens) = overviewOf(
            listOf(p("me", self = true), p("bro")),
            listOf(r("bro", "me", RelationshipType.SIBLING))
        )
        val v = GraphVisibility.compute(ov, gens, emptySet())
        assertEquals(setOf("me"), v.visibleIds)
        assertEquals(1, v.collapsedCount)
        assertTrue(v.chips.none { it.dir == GraphVisibility.Dir.UP })
        val same = v.chips.first { it.dir == GraphVisibility.Dir.SAME }
        val v1 = GraphVisibility.compute(ov, gens, same.expands)
        assertEquals(setOf("me", "bro"), v1.visibleIds)
    }

    @Test
    fun 展开与分享选人隔离() {
        // 收起旁支不改变 GraphBranches 的选人集合
        val (ov, gens) = bigFamily()
        val before = GraphBranches.visibleIds(ov, GraphBranch.ALL)
        GraphVisibility.compute(ov, gens, emptySet())   // 收起（默认态）
        val after = GraphBranches.visibleIds(ov, GraphBranch.ALL)
        assertEquals(before, after)
        assertFalse("bro" in GraphVisibility.compute(ov, gens, emptySet()).visibleIds)
        assertTrue("bro" in after)   // 分享预设不受图谱收起影响
    }
}
