package io.github.xiaohaoz1.jiarenxia.ui.share

import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.data.backup.TestFamily
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** ShareScope：分支预设与断开检查（纯逻辑；TestFamily 谱，姓名即 id）。 */
class ShareScopeTest {

    private val overview = run {
        val graph = TestFamily.graph()
        val self = graph.persons.first { it.isSelf }
        val results = KinshipEngine().resolveAll(graph, self.id)
        val byId = graph.persons.associate {
            it.id to PersonOverview(it, results.getValue(it.id), emptyList())
        }
        FamilyOverview(
            self = byId.getValue(self.id), graph = graph, byId = byId,
            core = emptyList(), others = emptyList()
        )
    }

    /** 谱内全部关系转成无向边两端 id 对。 */
    private val edges = overview.graph.relationships.map {
        it.sourcePersonId to it.targetPersonId
    }

    @Test
    fun 全部预设_返回所有人物() {
        assertEquals(overview.byId.keys, ShareScope.preset(overview, GraphBranch.ALL))
    }

    @Test
    fun 母系预设_我与母系一支() {
        val m = ShareScope.preset(overview, GraphBranch.MOTHER)
        // “我” + 母亲及其父母、舅舅一家
        assertEquals(
            setOf("杨明", "王秀兰", "王德福", "赵淑珍", "王志刚", "孙丽",
                "王磊", "陈晨", "王小宝"),
            m
        )
        // 不含父系、兄弟姐妹、配偶方、子孙与未关联者
        assertFalse(m.contains("杨建国"))
        assertFalse(m.contains("杨帆"))
        assertFalse(m.contains("李雪"))
        assertFalse(m.contains("杨小明"))
        assertFalse(m.contains("赵老师"))
    }

    @Test
    fun 取消勾选中间人物_其后整支报断开() {
        // 母系人选里去掉“母亲”：姥爷/姥姥/舅舅一家都与“我”断开
        val selected = ShareScope.preset(overview, GraphBranch.MOTHER) - "王秀兰"
        assertEquals(
            setOf("王德福", "赵淑珍", "王志刚", "孙丽", "王磊", "陈晨", "王小宝"),
            ShareScope.disconnected(selected, edges, "杨明")
        )
    }

    @Test
    fun 取消勾选舅舅_只报下游断开() {
        // 王志刚本人不在已选集合内，不报；其妻子儿女一支断开
        val selected = ShareScope.preset(overview, GraphBranch.MOTHER) - "王志刚"
        assertEquals(
            setOf("孙丽", "王磊", "陈晨", "王小宝"),
            ShareScope.disconnected(selected, edges, "杨明")
        )
    }

    @Test
    fun 全部已选_只报本来就无关联的人() {
        // 赵老师与“我”没有任何关系路径
        assertEquals(
            setOf("赵老师"),
            ShareScope.disconnected(overview.byId.keys, edges, "杨明")
        )
    }

    @Test
    fun 我不在人选内_全部报断开() {
        assertEquals(
            setOf("甲", "乙"),
            ShareScope.disconnected(
                setOf("甲", "乙"), listOf("甲" to "乙"), "我")
        )
    }
}
