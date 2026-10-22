package io.github.xiaohaoz1.jiarenxia.data.backup

import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 测试家庭全量称谓表（testdata 夹具的同一份规格 → 纯 FamilyGraph）。
 * 任何一行与引擎不符都会在这里炸出来：改引擎前先改表，反之亦然。
 */
class TestFamilyTitlesTest {

    @Test
    fun `测试家庭全部称谓符合期望表`() {
        val graph = TestFamily.graph()
        assertEquals(55, graph.persons.size)
        assertEquals(54, graph.relationships.size)

        val results = KinshipEngine().resolveAll(graph, TestFamily.SELF_NAME)
        val failures = StringBuilder()
        for ((name, expectedTitle, expectedStructural) in TestFamily.expectedTitles) {
            val r = results[name] ?: run {
                failures.append("\n$name 无推导结果")
                continue
            }
            if (r.standardTitle != expectedTitle) {
                failures.append(
                    "\n$name: standardTitle=${r.standardTitle} 期望 $expectedTitle" +
                        " (kind=${r.kind}, 结构=${r.structuralDescription}, 路径=" +
                        r.relationshipPath.map { it.type } + ")"
                )
            }
            if (expectedStructural != null && r.structuralDescription != expectedStructural) {
                failures.append(
                    "\n$name: 结构=${r.structuralDescription} 期望 $expectedStructural"
                )
            }
        }
        // 杨建春有自定义称谓 大伯 → 展示层为 大伯
        val yjc = results["杨建春"]!!
        val display = TestFamily.customTitles["杨建春"] ?: yjc.displayTitle
        assertEquals("大伯", display)
        assertEquals("伯父", yjc.standardTitle)
        // 赵老师未关联
        assertEquals(KinshipKind.UNRELATED, results["赵老师"]!!.kind)
        assertEquals("未关联到我", results["赵老师"]!!.structuralDescription)
        assertEquals("", failures.toString())
    }
}
