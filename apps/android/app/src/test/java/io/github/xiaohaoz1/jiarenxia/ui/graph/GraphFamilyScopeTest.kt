package io.github.xiaohaoz1.jiarenxia.ui.graph

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.db.toModel
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/** 使用实际四代备份验证六个入口的范围和关系图几何，不接触真实家庭资料。 */
@ConscryptMode(ConscryptMode.Mode.OFF)
class GraphFamilyScopeTest : RepoTestBase() {
    private lateinit var overview: FamilyOverview
    private lateinit var generations: Map<String, Int>

    @Before
    fun 读取四代合成家庭() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plan = File("../testdata/jrx_four_generation_family.jrxb").inputStream().use {
            BackupManager(context, db, FakeSettingsRepository())
                .inspect(it, "test1234".toCharArray()).getOrThrow()
        }
        val graph = FamilyGraph(plan.persons.map { it.toModel() }, plan.relationships.map { it.toModel() })
        val self = graph.persons.single { it.isSelf }
        val engine = KinshipEngine()
        val titles = engine.resolveAll(graph, self.id)
        val byId = graph.persons.associate {
            it.id to PersonOverview(it, titles.getValue(it.id), emptyList())
        }
        overview = FamilyOverview(byId.getValue(self.id), graph, byId, emptyList(), emptyList())
        generations = engine.generations(graph, self.id)
        assertEquals(155, graph.persons.size)
    }

    private fun id(name: String) = overview.graph.persons.single { it.name == name }.id
    private fun names(branch: GraphBranch) = GraphFamilyScope.visibleIds(overview, branch)
        .map { overview.byId.getValue(it).person.name }.toSet()

    @Test
    fun 六入口顺序名称锚点和人数符合已确认草图() {
        assertEquals(listOf(GraphBranch.ALL, GraphBranch.FATHER, GraphBranch.MOTHER,
            GraphBranch.SPOUSE, GraphBranch.SPOUSE_FATHER, GraphBranch.SPOUSE_MOTHER), GraphFamilyScope.tabs)
        assertEquals(listOf("我的家庭", "父系", "母系", "配偶系", "配偶父系", "配偶母系"),
            GraphFamilyScope.tabs.map { GraphFamilyScope.labelOf(it) })
        assertEquals(listOf("沈明远", "沈建安", "许文静", "林知夏", "林志诚", "叶舒云"),
            GraphFamilyScope.tabs.map { overview.byId.getValue(GraphFamilyScope.anchorOf(overview, it)!!).person.name })
        assertEquals(listOf(17, 101, 10, 18, 54, 6),
            GraphFamilyScope.tabs.map { GraphFamilyScope.visibleIds(overview, it).size })
    }

    @Test
    fun 我的家庭只到父母的旁支而父系展开祖辈兄弟至我下一辈() {
        val mine = names(GraphBranch.ALL)
        assertTrue(mine.containsAll(listOf("沈明远", "林知夏", "沈建安", "许文静", "沈怀山",
            "周淑兰", "许松年", "方桂芳", "沈雅琴", "郑志成", "郑书宁", "顾云舟", "顾小禾")))
        assertFalse(mine.contains("沈怀松"))
        val father = names(GraphBranch.FATHER)
        assertTrue(father.containsAll(listOf("沈怀松", "梁淑琴", "沈建东安", "苏美东安",
            "沈东安轩", "唐东安晴", "沈东安嘉乐", "沈东安嘉悦")))
        assertFalse(father.contains("林敬海"))
        val spouseFather = names(GraphBranch.SPOUSE_FATHER)
        assertTrue(spouseFather.containsAll(listOf("林敬海", "吴秀海", "林海文", "蒋海清",
            "林海晓阳", "白海思宁", "林海小满")))
        assertFalse(spouseFather.contains("沈怀松"))
    }

    @Test
    fun 补配偶不会沿配偶引入其原生家庭() {
        val mine = names(GraphBranch.ALL)
        assertTrue(mine.contains("林知夏"))
        assertFalse(mine.contains("林志诚"))
        assertFalse(mine.contains("叶舒云"))
        assertFalse(mine.contains("林敬山"))
        val mother = names(GraphBranch.MOTHER)
        assertTrue(mother.contains("沈建安"))
        assertFalse(mother.contains("沈怀山"))
    }

    @Test
    fun 超过代际边界的后代不入图但我的家庭保留本人孙辈() {
        val extra = listOf("旁支第五代", "我的孙子", "我的曾孙")
            .map { Person(it, it, Gender.MALE) }
        val links = listOf(
            Relationship("extra-1", id("沈东安嘉乐"), "旁支第五代", RelationshipType.PARENT),
            Relationship("extra-2", id("沈星河"), "我的孙子", RelationshipType.PARENT),
            Relationship("extra-3", "我的孙子", "我的曾孙", RelationshipType.PARENT)
        )
        val graph = FamilyGraph(overview.graph.persons + extra, overview.graph.relationships + links)
        val results = KinshipEngine().resolveAll(graph, overview.self.person.id)
        val byId = graph.persons.associate {
            it.id to PersonOverview(it, results.getValue(it.id), emptyList())
        }
        val extended = FamilyOverview(byId.getValue(overview.self.person.id), graph, byId,
            emptyList(), emptyList())
        val father = GraphFamilyScope.visibleIds(extended, GraphBranch.FATHER)
        assertTrue(id("沈东安嘉乐") in father)
        assertFalse("旁支第五代" in father)
        val mine = GraphFamilyScope.visibleIds(extended, GraphBranch.ALL)
        assertTrue("我的孙子" in mine)
        assertFalse("我的曾孙" in mine)
    }

    @Test
    fun 切换范围保留我且所有称谓仍按我推导() {
        val expected = KinshipEngine().resolveAll(overview.graph, overview.self.person.id)
        GraphFamilyScope.tabs.forEach { branch ->
            val visible = GraphFamilyScope.visibleIds(overview, branch)
            assertTrue("$branch 必须保留我", overview.self.person.id in visible)
            GraphFamilyLayout.build(overview, generations.filterKeys { it in visible })
            visible.forEach { personId ->
                assertEquals("$branch 改变了人物称谓 $personId", expected.getValue(personId),
                    overview.byId.getValue(personId).result)
            }
        }
    }

    @Test
    fun 远房孩子与配偶补全相对我的实际路径且不改变近亲标准称谓() {
        val before = overview.byId.mapValues { it.value.result }
        val resolved = GraphFamilyTitles.resolve(overview)
        val remoteNames = listOf("沈东安嘉乐", "林海小满", "白海思宁")
        assertTrue("合成家庭必须覆盖旧引擎五步之外的亲属",
            remoteNames.any { overview.byId.getValue(id(it)).result.kind == KinshipKind.UNRELATED })
        remoteNames.forEach { name ->
            val result = resolved.byId.getValue(id(name)).result
            assertFalse("$name 不应误作未关联", result.kind == KinshipKind.UNRELATED)
            assertTrue("$name 应保留完整实际路径", result.relationshipPath.isNotEmpty())
            assertEquals(overview.self.person.id, result.observerId)
            assertEquals(id(name), result.relationshipPath.last().personId)
            assertTrue("$name 应有可阅读关系描述", result.displayTitle.isNotBlank())
            assertFalse(result.displayTitle.contains("未关联"))
        }
        overview.byId.forEach { (personId, person) ->
            if (person.result.standardTitle != null) {
                assertEquals("近亲标准称谓不应改变 $personId", person.result,
                    resolved.byId.getValue(personId).result)
            }
        }
        assertEquals("不得修改原始总览", before, overview.byId.mapValues { it.value.result })
    }

    @Test
    fun 远房自定义称呼保留而真正孤立人物仍未关联() {
        val remoteId = id("林海小满")
        val isolated = Person("isolated", "合成孤立人物", Gender.MALE)
        val graph = FamilyGraph(overview.graph.persons + isolated, overview.graph.relationships)
        val results = KinshipEngine().resolveAll(graph, overview.self.person.id)
        val byId = graph.persons.associate { person ->
            person.id to PersonOverview(person, results.getValue(person.id), emptyList(),
                customTitle = if (person.id == remoteId) "小满同学" else null)
        }
        val extended = FamilyOverview(byId.getValue(overview.self.person.id), graph, byId,
            emptyList(), emptyList())
        val resolved = GraphFamilyTitles.resolve(extended)
        assertEquals("小满同学", resolved.byId.getValue(remoteId).displayTitle)
        assertFalse(resolved.byId.getValue(remoteId).result.kind == KinshipKind.UNRELATED)
        assertEquals(KinshipKind.UNRELATED, resolved.byId.getValue(isolated.id).result.kind)
        assertTrue(resolved.byId.getValue(isolated.id).result.relationshipPath.isEmpty())
        val filtered = generations.filterKeys { it in GraphFamilyScope.visibleIds(overview, GraphBranch.ALL) }
        assertEquals("未关联计数必须按全图真实连通性统计", 1,
            GraphFamilyLayout.build(extended, filtered).unconnectedCount)
    }

    @Test
    fun 我的家庭无家支定位其余根节点可见且夫妻不重复() {
        assertTrue(GraphFamilyScope.familyRoots(overview, GraphBranch.ALL).isEmpty())
        GraphFamilyScope.tabs.drop(1).forEach { branch ->
            val roots = GraphFamilyScope.familyRoots(overview, branch)
            if (branch == GraphBranch.SPOUSE_MOTHER) {
                // 此份合成资料未录入岳母的父母，不凭空制造可定位的祖辈家支。
                assertTrue(roots.isEmpty())
            } else {
                assertTrue("$branch 应提供家支定位", roots.isNotEmpty())
            }
            assertEquals(roots.size, roots.toSet().size)
            assertTrue(GraphFamilyScope.visibleIds(overview, branch).containsAll(roots))
            overview.graph.relationships.filter { it.type == RelationshipType.SPOUSE }.forEach {
                assertFalse("$branch 夫妻不应重复成为两个家支", it.sourcePersonId in roots && it.targetPersonId in roots)
            }
        }
        val fatherRoots = GraphFamilyScope.familyRoots(overview, GraphBranch.FATHER)
        listOf("沈怀山", "沈怀松", "沈怀柏", "沈怀竹", "沈怀梅").forEach { name ->
            assertTrue("父系缺少家支 $name", id(name) in fatherRoots)
        }
    }

    @Test
    fun 六视图同代同排卡片不重叠连线完整且布局确定() {
        GraphFamilyScope.tabs.forEach { branch ->
            val visible = GraphFamilyScope.visibleIds(overview, branch)
            val filtered = generations.filterKeys { it in visible }
            val model = GraphFamilyLayout.build(overview, filtered)
            assertEquals("$branch 收起的已关联亲属不应计入未关联", 0, model.unconnectedCount)
            assertEquals("$branch 人物必须完整", visible, model.nodes.map { it.personId }.toSet())
            assertEquals("$branch 布局应确定", model, GraphFamilyLayout.build(overview, filtered))
            model.nodes.groupBy { it.generation }.forEach { (generation, nodes) ->
                assertEquals("$branch 第 $generation 代必须同排", 1, nodes.map { it.y }.distinct().size)
            }
            model.nodes.forEachIndexed { i, first ->
                model.nodes.drop(i + 1).forEach { second ->
                    val overlap = first.x < second.right - 0.001f && second.x < first.right - 0.001f &&
                        first.y < second.bottom - 0.001f && second.y < first.bottom - 0.001f
                    assertFalse("$branch 人物卡片重叠 ${first.personId}/${second.personId}", overlap)
                }
            }
            val expectedEdges = overview.graph.relationships
                .filter { it.sourcePersonId in visible && it.targetPersonId in visible }
                .map { Triple(it.sourcePersonId, it.targetPersonId, it.type) }.toSet()
            assertEquals("$branch 关系连线必须完整", expectedEdges,
                model.edges.map { Triple(it.fromId, it.toId, it.type) }.toSet())
            GraphEdgeRouting.route(model).paths.forEach { path ->
                path.points.zipWithNext().forEach { (a, b) ->
                    model.nodes.forEach { node ->
                        val epsilon = 0.001f
                        val crossed = if (a.x == b.x) {
                            a.x > node.x + epsilon && a.x < node.right - epsilon &&
                                minOf(a.y, b.y) < node.bottom - epsilon &&
                                maxOf(a.y, b.y) > node.y + epsilon
                        } else if (a.y == b.y) {
                            a.y > node.y + epsilon && a.y < node.bottom - epsilon &&
                                minOf(a.x, b.x) < node.right - epsilon &&
                                maxOf(a.x, b.x) > node.x + epsilon
                        } else {
                            // 路由侧廊包含短斜入段，用参数区间求其与卡片内部的交集。
                            val tx1 = (node.x + epsilon - a.x) / (b.x - a.x)
                            val tx2 = (node.right - epsilon - a.x) / (b.x - a.x)
                            val ty1 = (node.y + epsilon - a.y) / (b.y - a.y)
                            val ty2 = (node.bottom - epsilon - a.y) / (b.y - a.y)
                            maxOf(0f, minOf(tx1, tx2), minOf(ty1, ty2)) <
                                minOf(1f, maxOf(tx1, tx2), maxOf(ty1, ty2))
                        }
                        assertFalse("$branch 连线穿过人物卡片 ${node.personId}: $a -> $b", crossed)
                    }
                }
            }
        }
    }
}
