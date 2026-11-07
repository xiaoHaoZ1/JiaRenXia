package io.github.xiaohaoz1.jiarenxia.data.backup

import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 演示版两份内置家庭的称谓断言（常驻，不依赖 -PtestFamily）：
 * 新郎版取 MingFamily 期望表中保留人的子集，新娘版按规格逐项核对；
 * 另断言两份无同名人物，保证碰一碰合并不会出现「可能是同一人」。
 */
class DemoFamilyTitlesTest {

    private fun assertTitles(graph: io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph,
                            self: String,
                            expected: List<Triple<String, String?, String?>>) {
        val results = KinshipEngine().resolveAll(graph, self)
        val failures = StringBuilder()
        for ((name, expectedTitle, _) in expected) {
            val r = results[name] ?: run {
                failures.append("\n$name 无推导结果")
                continue
            }
            if (r.standardTitle != expectedTitle) {
                failures.append(
                    "\n$name: standardTitle=${r.standardTitle} 期望 $expectedTitle" +
                        " (结构=${r.structuralDescription}, 路径=" +
                        r.relationshipPath.map { it.type } + ")"
                )
            }
        }
        assertEquals("", failures.toString())
    }

    @Test
    fun `新郎版裁剪后称谓保持`() {
        val graph = MingFamily.graph(DemoFamilies.GROOM_KEEP)
        // 36 人 = 我 + 35 位保留规格人
        assertEquals(36, graph.persons.size)
        // 被删两支不得出现
        for (deleted in DemoFamilies.GROOM_DELETED) {
            assertTrue("不应存在 $deleted", graph.persons.none { it.name == deleted })
        }
        val expected = MingFamily.expectedTitles.filter { it.first in DemoFamilies.GROOM_KEEP }
        assertTitles(graph, MingFamily.SELF_NAME, expected)
    }

    @Test
    fun `新娘版称谓符合规格`() {
        val graph = DemoFamilies.brideGraph()
        assertEquals(9, graph.persons.size)
        val expected = DemoFamilies.BRIDE_TITLES.map { Triple(it.first, it.second, null) }
        assertTitles(graph, DemoFamilies.BRIDE_SELF, expected)
    }

    @Test
    fun `两份演示家庭无同名人物重叠`() {
        val groomNames = MingFamily.graph(DemoFamilies.GROOM_KEEP).persons.map { it.name }.toSet()
        val brideNames = DemoFamilies.brideGraph().persons.map { it.name }.toSet()
        val overlap = groomNames intersect brideNames
        // “我”各自不同名：朱棣 vs 徐皇后——徐皇后在新郎版被删、新娘版是“我”
        assertTrue("两份演示家庭存在同名人物：$overlap", overlap.isEmpty())
    }
}
