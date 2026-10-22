package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** GraphStructure：主锚解析、家庭 block、左右归属 BFS。 */
class GraphStructureTest {

    private val engine = KinshipEngine()
    private var seq = 0

    private fun p(id: String, gender: Gender = Gender.MALE, self: Boolean = false) =
        Person(id = id, name = id, gender = gender, isSelf = self)
    private fun r(src: String, tgt: String, type: RelationshipType) =
        Relationship("r" + (++seq), src, tgt, type, null)

    private fun env(
        persons: List<Person>, rels: List<Relationship>
    ): Triple<FamilyGraph, Map<String, Int>, Map<String, PersonOverview>> {
        val graph = FamilyGraph(persons, rels)
        val self = persons.first { it.isSelf }
        val results = engine.resolveAll(graph, self.id)
        val byId = persons.associate {
            it.id to PersonOverview(it, results.getValue(it.id), emptyList())
        }
        return Triple(graph, engine.generations(graph, self.id), byId)
    }

    // ---------- 主锚 ----------

    @Test
    fun 主锚_显式配偶优先() {
        val (g, _, _) = env(
            listOf(p("me", self = true), p("wife", Gender.FEMALE), p("son")),
            listOf(
                r("me", "wife", RelationshipType.SPOUSE),
                r("me", "son", RelationshipType.PARENT)
            )
        )
        val a = GraphStructure.anchorsOf(g, "me")
        assertEquals("wife", a.partnerId)
        assertTrue(a.partnerIsSpouse)
    }

    @Test
    fun 主锚_无配偶用唯一共同父母组合() {
        val (g, _, _) = env(
            listOf(p("me", self = true), p("ex", Gender.FEMALE), p("son")),
            listOf(
                r("me", "son", RelationshipType.PARENT),
                r("ex", "son", RelationshipType.PARENT)
            )
        )
        val a = GraphStructure.anchorsOf(g, "me")
        assertEquals("ex", a.partnerId)
        assertEquals(false, a.partnerIsSpouse)
    }

    @Test
    fun 主锚_多位共同父母不组合() {
        val (g, _, _) = env(
            listOf(
                p("me", self = true), p("a", Gender.FEMALE),
                p("b", Gender.FEMALE), p("s1"), p("s2")
            ),
            listOf(
                r("me", "s1", RelationshipType.PARENT),
                r("a", "s1", RelationshipType.PARENT),
                r("me", "s2", RelationshipType.PARENT),
                r("b", "s2", RelationshipType.PARENT)
            )
        )
        assertEquals(null, GraphStructure.anchorsOf(g, "me").partnerId)
    }

    // ---------- block ----------

    @Test
    fun block_配偶成对男左女右() {
        val (g, gens, byId) = env(
            listOf(p("me", self = true), p("wife", Gender.FEMALE), p("son")),
            listOf(
                r("me", "wife", RelationshipType.SPOUSE),
                r("me", "son", RelationshipType.PARENT)
            )
        )
        val blocks = GraphStructure.blocksOf(g, gens, gens.keys, byId)
        val anchor = blocks.single { "me" in it.memberIds }
        assertEquals(listOf("me", "wife"), anchor.memberIds)
        // 儿子单人 block
        val son = blocks.single { "son" in it.memberIds }
        assertEquals(listOf("son"), son.memberIds)
    }

    @Test
    fun block_共同父母配对_跨代不组合() {
        val (g, gens, byId) = env(
            listOf(p("me", self = true), p("dad"), p("mom", Gender.FEMALE)),
            listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT)
            )
        )
        val blocks = GraphStructure.blocksOf(g, gens, gens.keys, byId)
        // dad+mom 是 me 的共同父母 → 成一个 block（男左女右）
        val parents = blocks.single { "dad" in it.memberIds }
        assertEquals(listOf("dad", "mom"), parents.memberIds)
    }

    @Test
    fun block_每人只进一个block() {
        // 异常：me 有 w1、w2 两个配偶记录 → 只与排序第一位成 block，w2 独立
        val (g, gens, byId) = env(
            listOf(p("me", self = true), p("w1", Gender.FEMALE), p("w2", Gender.FEMALE)),
            listOf(
                r("me", "w1", RelationshipType.SPOUSE),
                r("me", "w2", RelationshipType.SPOUSE)
            )
        )
        val blocks = GraphStructure.blocksOf(g, gens, gens.keys, byId)
        val joined = blocks.filter { "me" in it.memberIds }
        assertEquals(1, joined.size)
        // w2 仍在独立 block 中（不丢人）
        assertTrue(blocks.any { it.memberIds == listOf("w2") })
        assertEquals(3, blocks.sumOf { it.memberIds.size })
    }

    // ---------- 左右归属 ----------

    @Test
    fun 归属_父路径左母路径右_子孙居中() {
        val (g, _, byId) = env(
            listOf(
                p("me", self = true), p("wife", Gender.FEMALE),
                p("dad"), p("mom", Gender.FEMALE),
                p("pUncle"), p("mUncle"), p("wSis", Gender.FEMALE),
                p("son"), p("grandson")
            ),
            listOf(
                r("me", "wife", RelationshipType.SPOUSE),
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("pUncle", "dad", RelationshipType.SIBLING),
                r("mUncle", "mom", RelationshipType.SIBLING),
                r("wSis", "wife", RelationshipType.SIBLING),
                r("me", "son", RelationshipType.PARENT),
                r("son", "grandson", RelationshipType.PARENT)
            )
        )
        val a = GraphStructure.anchorsOf(g, "me")
        val asg = GraphStructure.assign(g, a, byId)
        // self 男方=左根：男方整族（含自己的母亲）在左、妻系在右
        assertEquals(GraphSide.LEFT, asg["dad"]!!.side)
        assertEquals(GraphSide.LEFT, asg["mom"]!!.side)
        assertEquals(GraphSide.LEFT, asg["pUncle"]!!.side)
        assertEquals(GraphSide.LEFT, asg["mUncle"]!!.side)
        assertEquals(GraphSide.RIGHT, asg["wSis"]!!.side)
        // 锚与直接子女/子孙居中（子孙不做两侧通道）
        assertEquals(GraphSide.CENTER, asg["me"]!!.side)
        assertEquals(GraphSide.CENTER, asg["wife"]!!.side)
        assertEquals(GraphSide.CENTER, asg["son"]!!.side)
        assertEquals(GraphSide.CENTER, asg["grandson"]!!.side)
    }

    @Test
    fun 归属_女self镜像() {
        // 女self：丈夫是男根 → 丈夫路径左，自己路径右
        val (g, _, byId) = env(
            listOf(
                p("me", Gender.FEMALE, self = true), p("husband"),
                p("dad"), p("hBro")
            ),
            listOf(
                r("me", "husband", RelationshipType.SPOUSE),
                r("dad", "me", RelationshipType.PARENT),
                r("hBro", "husband", RelationshipType.SIBLING)
            )
        )
        val a = GraphStructure.anchorsOf(g, "me")
        val asg = GraphStructure.assign(g, a, byId)
        assertEquals(GraphSide.LEFT, asg["hBro"]!!.side)
        assertEquals(GraphSide.RIGHT, asg["dad"]!!.side)
    }

    @Test
    fun 归属_双根等距归左_仅居中子孙相连归中() {
        val (g, _, byId) = env(
            listOf(
                p("me", self = true), p("wife", Gender.FEMALE),
                p("son"), p("dil", Gender.FEMALE),   // 儿媳只经儿子连接
                p("cross")                          // 同时连两边的人
            ),
            listOf(
                r("me", "wife", RelationshipType.SPOUSE),
                r("me", "son", RelationshipType.PARENT),
                r("son", "dil", RelationshipType.SPOUSE),
                r("me", "cross", RelationshipType.COUSIN),
                r("wife", "cross", RelationshipType.COUSIN)
            )
        )
        val a = GraphStructure.anchorsOf(g, "me")
        val asg = GraphStructure.assign(g, a, byId)
        // 儿媳只经儿子（居中通道外）可达 → CENTER
        assertEquals(GraphSide.CENTER, asg["dil"]!!.side)
        // cross 距两根等距（各 2 步：根-对方根被排除→经 self? 主锚边被排除）
        // me→cross 1 步；wife→cross 经 wife 1 步——等距归 LEFT（男方）
        assertEquals(GraphSide.LEFT, asg["cross"]!!.side)
    }
}
