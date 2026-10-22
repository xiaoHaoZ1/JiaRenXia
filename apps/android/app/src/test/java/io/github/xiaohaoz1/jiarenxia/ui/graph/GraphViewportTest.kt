package io.github.xiaohaoz1.jiarenxia.ui.graph

import org.junit.Assert.assertEquals
import org.junit.Test

/** GraphViewport 初始视口规则。 */
class GraphViewportTest {

    @Test
    fun 小内容_1倍_包围盒居中() {
        // 内容 200x200，容器 1000x1000，padding 16 → fit > 1 → scale=1
        val v = GraphViewport.initial(200f, 200f, 1000f, 1000f, 100f, 100f, 16f)
        assertEquals(1f, v.scale)
        // 居中：pan = container/2 - content/2
        assertEquals(400f, v.panX)
        assertEquals(400f, v.panY)
    }

    @Test
    fun 中等内容_不自动缩小_放不下的轴居中我() {
        // 内容 1400x1000 超过容器：保持 1.0 阅读比例（不缩成总览），
        // 放不下的两轴都居中“我”的聚焦中心
        val v = GraphViewport.initial(1400f, 1000f, 1000f, 800f, 300f, 300f, 16f)
        assertEquals(1f, v.scale)
        assertEquals(500f - 300f, v.panX, 1e-3f)
        assertEquals(400f - 300f, v.panY, 1e-3f)
    }

    @Test
    fun 超大内容_仍是1倍_我居中() {
        // 大字号/大家庭把内容撑得再大也不自动缩小（阅读优先，可拖动）
        val v = GraphViewport.initial(4000f, 3000f, 1000f, 800f, 1200f, 800f, 16f)
        assertEquals(1f, v.scale)
        assertEquals(500f - 1200f, v.panX, 1e-3f)   // 我横向居中
        assertEquals(400f - 800f, v.panY, 1e-3f)
    }

    @Test
    fun 单轴超出_另一轴仍居中内容() {
        // 宽放得下、高放不下 → 横向居中内容，纵向居中“我”
        val v = GraphViewport.initial(600f, 2000f, 1000f, 800f, 300f, 900f, 16f)
        assertEquals(1f, v.scale)
        assertEquals(500f - 300f, v.panX, 1e-3f)
        assertEquals(400f - 900f, v.panY, 1e-3f)
    }

    @Test
    fun 单节点_1倍居中() {
        // 单节点内容 = 一个节点大小
        val v = GraphViewport.initial(300f, 340f, 1000f, 800f, 150f, 170f, 16f)
        assertEquals(1f, v.scale)
        assertEquals(500f - 150f, v.panX, 1e-3f)
        assertEquals(400f - 170f, v.panY, 1e-3f)
    }

    // ---- centerOn：定位节点到容器中央 ----

    @Test
    fun 定位_缩小态提升到1倍() {
        // 当前 scale 0.4 → 提到 1，节点中心 (100,200) 移到容器中心 (500,400)
        val v = GraphViewport.centerOn(100f, 200f, 1000f, 800f, 0.4f)
        assertEquals(1f, v.scale)
        assertEquals(500f - 100f * 1f, v.panX, 1e-3f)
        assertEquals(400f - 200f * 1f, v.panY, 1e-3f)
    }

    @Test
    fun 定位_保持当前缩放() {
        val v = GraphViewport.centerOn(300f, 200f, 1000f, 800f, 1.5f)
        assertEquals(1.5f, v.scale)
        assertEquals(500f - 300f * 1.5f, v.panX, 1e-3f)
        assertEquals(400f - 200f * 1.5f, v.panY, 1e-3f)
    }

    @Test
    fun 定位_超过2点5倍收敛() {
        val v = GraphViewport.centerOn(300f, 200f, 1000f, 800f, 5f)
        assertEquals(2.5f, v.scale)
        assertEquals(500f - 300f * 2.5f, v.panX, 1e-3f)
        assertEquals(400f - 200f * 2.5f, v.panY, 1e-3f)
    }

    // ---- focusRegion：默认恢复 1.0 阅读比例；普通展开定位才保留缩放 ----

    @Test
    fun 聚焦区域_缩小态恢复1倍() {
        // 总览 0.3 下点人 → 恢复 1 倍，区域中心落在容器 45% 高度
        val v = GraphViewport.focusRegion(400f, 300f, 1000f, 800f, 0.3f)
        assertEquals(1f, v.scale)
        assertEquals(500f - 400f * 1f, v.panX, 1e-3f)
        assertEquals(800f * 0.45f - 300f * 1f, v.panY, 1e-3f)
    }

    @Test
    fun 聚焦区域_回到我_放大态恢复到1倍() {
        // 手动放大到 2.5 → “回到我”/总览点人等返回阅读动作必须
        // 明确回到 1.0，不得沿用当前缩放
        val v = GraphViewport.focusRegion(400f, 300f, 1000f, 800f, 2.5f)
        assertEquals(1f, v.scale)
        assertEquals(500f - 400f, v.panX, 1e-3f)
        assertEquals(800f * 0.45f - 300f, v.panY, 1e-3f)
    }

    @Test
    fun 聚焦区域_普通展开定位_保留当前缩放() {
        // preserveScale=true 仅供展开/聚焦使用：用户放大 1.4 倍后
        // 点芯片展开旁支，定位新家庭时不把缩放打回 1.0
        val v = GraphViewport.focusRegion(
            400f, 300f, 1000f, 800f, 1.4f, preserveScale = true)
        assertEquals(1.4f, v.scale)
        assertEquals(500f - 400f * 1.4f, v.panX, 1e-3f)
        assertEquals(800f * 0.45f - 300f * 1.4f, v.panY, 1e-3f)
    }

    @Test
    fun 聚焦区域_普通定位_低于1倍抬回1倍() {
        val v = GraphViewport.focusRegion(
            400f, 300f, 1000f, 800f, 0.4f, preserveScale = true)
        assertEquals(1f, v.scale)
    }

    // ---- fitAll：只缩放当前已展开范围 ----

    @Test
    fun 总览_恰好放下已展开范围_不超1倍() {
        // 内容 2000x1500，容器 1000x800，顶部提示条 40、留白 12
        val v = GraphViewport.fitAll(
            2000f, 1500f, 1000f, 800f,
            topInset = 40f, padding = 12f)
        val expect = minOf(
            (1000f - 24f) / 2000f,
            (800f - 40f - 24f) / 1500f)
        assertEquals(expect, v.scale, 1e-4f)
        assert(v.scale <= 1f)
        // 内容在扣掉顶部提示条后的可用区内居中
        assertEquals(500f - 2000f * v.scale / 2f, v.panX, 1e-3f)
        val availH = 800f - 40f - 24f
        assertEquals(40f + 12f + availH / 2f - 1500f * v.scale / 2f,
            v.panY, 1e-3f)
    }

    @Test
    fun 总览_小内容不放大() {
        val v = GraphViewport.fitAll(300f, 300f, 1000f, 800f, padding = 12f)
        assertEquals(1f, v.scale)
    }

    @Test
    fun 总览_超大内容有0点15下界() {
        // 极小 fit 也保持 0.15，不至于缩到不可辨认
        val v = GraphViewport.fitAll(20000f, 20000f, 1000f, 800f)
        assertEquals(0.15f, v.scale)
    }
}
