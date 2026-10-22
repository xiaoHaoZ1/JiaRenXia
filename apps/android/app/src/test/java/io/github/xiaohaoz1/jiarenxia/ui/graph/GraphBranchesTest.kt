package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.data.backup.TestFamily
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GraphBranches：测试家庭谱上五个分支的成员归属与筛选。 */
class GraphBranchesTest {

    private fun testFamilyOverview(): FamilyOverview {
        val graph = TestFamily.graph()
        val self = graph.persons.first { it.isSelf }
        val results = KinshipEngine().resolveAll(graph, self.id)
        val byId = graph.persons.associate {
            it.id to PersonOverview(it, results.getValue(it.id), emptyList())
        }
        return FamilyOverview(
            self = byId.getValue(self.id), graph = graph, byId = byId,
            core = emptyList(), others = emptyList()
        )
    }

    private val ov = testFamilyOverview()

    private fun members(branch: GraphBranch): Set<String> =
        ov.byId.values.filter { GraphBranches.branchOf(it, ov.byId) == branch }
            .map { it.person.name }.toSet()

    @Test
    fun 父系分支() {
        val m = members(GraphBranch.FATHER)
        assertTrue(m.containsAll(
            setOf("杨建国", "杨福生", "杨建春", "杨磊", "杨建华", "张建明")
        ))
        assertFalse(m.contains("王德福"))
        assertFalse(m.contains("李国强"))
    }

    @Test
    fun 母系分支() {
        val m = members(GraphBranch.MOTHER)
        assertTrue(m.containsAll(setOf("王秀兰", "王德福", "王志刚", "王磊")))
        assertFalse(m.contains("杨建国"))
    }

    @Test
    fun 配偶方分支() {
        val m = members(GraphBranch.SPOUSE)
        // 配偶本人 + 配偶的兄弟姐妹一支（兄弟/其配偶子女）
        assertTrue(m.containsAll(
            setOf("李雪", "李强", "何静", "李小龙")
        ))
        assertFalse(m.contains("李国强"))   // 归配偶父系
        assertFalse(m.contains("陈美华"))   // 归配偶母系
        assertFalse(m.contains("杨帆"))
    }

    @Test
    fun 配偶父系分支() {
        val m = members(GraphBranch.SPOUSE_FATHER)
        assertTrue(m.containsAll(
            setOf("李国强", "李老爷子", "刘奶奶", "李国华", "马丽",
                "李国栋", "李伟", "李老二", "李国平")
        ))
        assertFalse(m.contains("李雪"))
        assertFalse(m.contains("陈美华"))
    }

    @Test
    fun 配偶母系分支() {
        val m = members(GraphBranch.SPOUSE_MOTHER)
        assertTrue(m.containsAll(setOf("陈美华", "陈美玲")))
        assertFalse(m.contains("李国强"))
    }

    @Test
    fun 兄弟姐妹分支() {
        val m = members(GraphBranch.SIBLINGS)
        assertTrue(m.containsAll(setOf("杨帆", "赵敏", "杨小帆", "杨豆豆")))
        assertFalse(m.contains("杨明"))
    }

    @Test
    fun 子孙分支() {
        val m = members(GraphBranch.DESCENDANTS)
        assertTrue(m.containsAll(
            setOf("杨小明", "吴佳", "吴建军", "杨乐乐", "杨小小")
        ))
        assertFalse(m.contains("杨帆"))
    }

    @Test
    fun 未关联与本人不属于任何分支() {
        val zls = ov.byId.getValue("赵老师")
        assertNull(GraphBranches.branchOf(zls, ov.byId))
        assertNull(GraphBranches.branchOf(ov.self, ov.byId))
    }

    @Test
    fun 筛选后我总在表内_未关联者仍计未关联() {
        val engine = KinshipEngine()
        val allGens = engine.generations(ov.graph, ov.self.person.id)
        assertFalse(allGens.containsKey("赵老师"))
        GraphBranch.entries.forEach { b ->
            val gens = GraphBranches.filter(allGens, ov, b)
            assertTrue("self missing in $b", gens.containsKey(ov.self.person.id))
            // 与“我”无路径的人数按未筛选世代表计算
            val unconnected = ov.graph.persons.count { it.id !in allGens.keys }
            assertEquals(1, unconnected)
        }
        // 父系分支不含母系祖辈
        val fatherGens = GraphBranches.filter(
            allGens, ov, GraphBranch.FATHER
        )
        assertTrue(fatherGens.containsKey("杨建国"))
        assertFalse(fatherGens.containsKey("王秀兰"))
        assertFalse(fatherGens.containsKey("李雪"))
    }

    @Test
    fun 全部视图_配偶一系收起只留配偶本人() {
        val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
        val all = GraphBranches.filter(gens, ov, GraphBranch.ALL)
        // 配偶本人在，配偶方亲属全部收起
        assertTrue(all.containsKey("李雪"))
        listOf("李国强", "陈美华", "李老爷子", "李国华", "李强", "李国平",
            "陈美玲", "何静", "李小龙").forEach {
            assertFalse("$it should be hidden in ALL", all.containsKey(it))
        }
        // 我方亲属与共同子女不受影响
        listOf("杨建国", "王秀兰", "杨帆", "杨小明").forEach {
            assertTrue("$it missing in ALL", all.containsKey(it))
        }
    }

    @Test
    fun 配偶方视图_含配偶一系与共同子女() {
        val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
        val spouse = GraphBranches.filter(gens, ov, GraphBranch.SPOUSE)
        listOf("李雪", "李国强", "陈美华", "李强", "李国平", "杨小明").forEach {
            assertTrue("$it missing in SPOUSE", spouse.containsKey(it))
        }
        assertFalse(spouse.containsKey("杨帆"))
        // 配偶父系：只有父亲一支 + 配偶本人锚点
        val sf = GraphBranches.filter(gens, ov, GraphBranch.SPOUSE_FATHER)
        assertTrue(sf.containsKey("李雪"))
        assertTrue(sf.containsKey("李国强"))
        assertTrue(sf.containsKey("李国平"))
        assertFalse(sf.containsKey("陈美华"))
        assertFalse(sf.containsKey("李强"))
        assertFalse(sf.containsKey("杨小明"))
        // 配偶母系
        val sm = GraphBranches.filter(gens, ov, GraphBranch.SPOUSE_MOTHER)
        assertTrue(sm.containsKey("李雪"))
        assertTrue(sm.containsKey("陈美华"))
        assertTrue(sm.containsKey("陈美玲"))
        assertFalse(sm.containsKey("李国强"))
    }

    @Test
    fun available_有成员分支且ALL在首() {
        val av = GraphBranches.available(ov)
        assertEquals(GraphBranch.ALL, av.first())
        assertTrue(av.containsAll(
            listOf(
                GraphBranch.FATHER, GraphBranch.MOTHER, GraphBranch.SPOUSE,
                GraphBranch.SPOUSE_FATHER, GraphBranch.SPOUSE_MOTHER,
                GraphBranch.SIBLINGS, GraphBranch.DESCENDANTS
            )
        ))
        // 只有“我”时只剩 ALL（界面因此不显示筛选行）
        val soloGraph = FamilyGraph(listOf(ov.self.person), emptyList())
        val soloResults = KinshipEngine().resolveAll(soloGraph, ov.self.person.id)
        val soloOv = PersonOverview(ov.self.person, soloResults.getValue(ov.self.person.id), emptyList())
        val solo = FamilyOverview(soloOv, soloGraph, mapOf(soloOv.person.id to soloOv), emptyList(), emptyList())
        assertEquals(listOf(GraphBranch.ALL), GraphBranches.available(solo))
    }
}
