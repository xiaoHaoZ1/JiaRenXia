package io.github.xiaohaoz1.jiarenxia.ui.share

import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranch
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranches

/**
 * 家人共享的分享范围（FAMILY_AFFAIRS 9 部分分享）。
 *
 * - [preset] 按关系图分支给出整支人选（如“母系一支”），供勾选起点；
 * - [disconnected] 找出勾选结果里与“我”断开的人——中间人物被取消
 *   勾选时，其后一整支虽然还在人选内却已无路可达，界面据此提示。
 *
 * 纯逻辑，不依赖 Android，可直接单元测试。
 */
object ShareScope {

    /**
     * 分支预设的人物 id 集合：ALL 返回全部人物；其他分支返回
     * 关系图对应视图显示的人（[GraphBranches.visibleIds]，含“我”）。
     */
    fun preset(overview: FamilyOverview, branch: GraphBranch): Set<String> {
        if (branch == GraphBranch.ALL) return overview.byId.keys.toSet()
        return GraphBranches.visibleIds(overview, branch)
    }

    /**
     * 已选人物中从 [selfId] 出发到不了的人：只用两端都已选的关系
     * （视为无向边）做连通检查，不可达即返回。返回的是还在
     * [selected] 里的人——通常因为中间人物没被勾选而断开。
     * [selfId] 不在 [selected] 内时视为无人可达，返回整个集合。
     */
    fun disconnected(
        selected: Set<String>,
        relationships: List<Pair<String, String>>,
        selfId: String
    ): Set<String> {
        val adjacent = HashMap<String, ArrayList<String>>()
        relationships.forEach { (a, b) ->
            if (a != b && a in selected && b in selected) {
                adjacent.getOrPut(a) { ArrayList() }.add(b)
                adjacent.getOrPut(b) { ArrayList() }.add(a)
            }
        }
        val reached = HashSet<String>()
        if (selfId in selected) {
            val queue = ArrayDeque<String>()
            reached += selfId
            queue.addLast(selfId)
            while (!queue.isEmpty()) {
                val cur = queue.removeFirst()
                adjacent[cur]?.forEach { next ->
                    if (reached.add(next)) queue.addLast(next)
                }
            }
        }
        return selected - reached
    }
}
