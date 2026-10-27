package io.github.xiaohaoz1.jiarenxia.data.backup

import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 明朝演示家庭全量称谓表（与 MingFamily 同一份规格 → 纯 FamilyGraph）。
 * 朱棣视角：岳父岳母、嫂子、妹夫、外甥、儿媳、孙子等称谓必须都推对。
 */
class MingFamilyTitlesTest {

    @Test
    fun `明朝家庭全部称谓符合期望表`() {
        val graph = MingFamily.graph()
        assertEquals(65, graph.persons.size)
        assertEquals(90, graph.relationships.size)

        val results = KinshipEngine().resolveAll(graph, MingFamily.SELF_NAME)
        val failures = StringBuilder()
        for ((name, expectedTitle, expectedStructural) in MingFamily.expectedTitles) {
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
        assertEquals("", failures.toString())
    }
}
