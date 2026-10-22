package io.github.xiaohaoz1.jiarenxia.ui.graph

/**
 * 各边关系词的落点（屏幕与导出图共用）：阶段B 起由
 * [GraphEdgeRouting] 按实际折线空白线段产出锚点，本层只做
 * 同位置去重错开——不再假设同 y 同行或按 x 分组。
 */
object GraphEdgeLabels {

    /** 一个词的最终落点：x/y 为布局单位（与节点同坐标系）。 */
    data class Placement(val text: String, val x: Float, val y: Float)

    /** 同一锚点挤进多个词时竖向错开的步距（布局单位）。 */
    private const val STACK_STEP = 0.16f

    /** 由路由结果产出落点；同锚点的词按顺序竖向错开。 */
    fun compute(routing: GraphEdgeRouting.Routing): List<Placement> {
        val out = ArrayList<Placement>(routing.labels.size)
        routing.labels
            .groupBy { (it.x * 100).toInt() to (it.y * 100).toInt() }
            .values.forEach { g ->
                g.forEachIndexed { i, l ->
                    out += Placement(l.text, l.x, l.y + i * STACK_STEP)
                }
            }
        return out
    }
}
