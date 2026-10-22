package io.github.xiaohaoz1.jiarenxia.ui.graph

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.data.backup.TestFamily
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOrdering
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 关系图图片导出（V0.9）：渲染尺寸规则 + 分支筛选 + 相册值。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GraphExportTest {

    private fun overviewOf(
        graph: io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
    ): FamilyOverview {
        val engine = KinshipEngine()
        val self = graph.persons.first { it.isSelf }
        val results = engine.resolveAll(graph, self.id)
        val byId = graph.persons.associate { person ->
            person.id to PersonOverview(
                person, results.getValue(person.id),
                graph.birthdayValues[person.id] ?: emptyList(),
                solarBirthday = graph.birthdays[person.id]
            )
        }
        val rest = byId.values.filter { it.person.id != self.id }
        return FamilyOverview(
            self = byId.getValue(self.id),
            graph = graph,
            byId = byId,
            core = FamilyOrdering.core(
                rest.filter { it.result.coreGroup != null }, byId),
            others = FamilyOrdering.others(
                rest.filter { it.result.coreGroup == null })
        )
    }

    private fun infos(ov: FamilyOverview) = ov.byId.mapValues { (_, o) ->
        GraphImageRenderer.NodeInfo(
            name = o.person.name,
            title = if (o.person.isSelf) "我" else o.displayTitle,
            deceased = o.person.livingStatus !=
                io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.ALIVE)
    }

    private fun tinyGraph(): io.github.xiaohaoz1.jiarenxia.core.kinship
        .FamilyGraph {
        // 3 人：我 + 爸 + 妈
        fun p(id: String, name: String, self: Boolean = false) =
            io.github.xiaohaoz1.jiarenxia.core.model.Person(
                id = id, name = name,
                gender = io.github.xiaohaoz1.jiarenxia.core.model.Gender.MALE,
                isSelf = self)
        fun r(a: String, b: String) =
            io.github.xiaohaoz1.jiarenxia.core.model.Relationship(
                a + b, a, b,
                io.github.xiaohaoz1.jiarenxia.core.model
                    .RelationshipType.PARENT, null)
        return io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph(
            listOf(p("me", "杨明", true), p("dad", "杨建国"), p("mom", "王秀兰")),
            listOf(r("dad", "me"), r("mom", "me")),
            emptyMap(), emptyMap())
    }

    @Test
    fun 小家庭_标准版宽1080() {
        val ov = overviewOf(tinyGraph())
        val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
        val model = GraphLayout.build(ov, gens)
        val bmp = GraphImageRenderer.render(
            model, infos(ov), ov.self.person.id,
            GraphImageRenderer.Quality.STANDARD,
            GraphImageRenderer.titleLine("杨明", LocalDate.of(2026, 10, 1)))
        // 小家庭放大到 1080 宽
        assertEquals(1080, bmp.width)
        assertTrue(bmp.height in 200..2048)
        // 不透明背景
        assertEquals(255, android.graphics.Color.alpha(bmp.getPixel(2, 2)))
    }

    @Test
    fun 测试家庭55人_渲染成功且限边() {
        val ov = overviewOf(TestFamily.graph())
        assertEquals(55, ov.graph.persons.size)
        val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
        val model = GraphLayout.build(ov, gens)
        val bmp = GraphImageRenderer.render(
            model, infos(ov), ov.self.person.id,
            GraphImageRenderer.Quality.STANDARD,
            GraphImageRenderer.titleLine("杨明", LocalDate.of(2026, 10, 1)))
        // 大家庭：最长边不超过 2048（含标题/落款区）
        assertTrue(maxOf(bmp.width, bmp.height) <= 2048)
        // 姓名字号换算后不低于渲染器自身的缩放下限
        val scale = GraphImageRenderer.scaleOf(
            model, GraphImageRenderer.Quality.STANDARD)
        assertTrue(
            "姓名字号过小：" + (22f * scale),
            22f * scale >= 12f)
        // 落盘一张供人工查看（roborazzi 输出目录）
        val dir = File("build/outputs/roborazzi").apply { mkdirs() }
        File(dir, "graph_export_55.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue(File(dir, "graph_export_55.png").length() > 10_000)
    }

    @Test
    fun 高清版_标准2倍_最长边不超4096() {
        val ov = overviewOf(tinyGraph())
        val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
        val model = GraphLayout.build(ov, gens)
        val std = GraphImageRenderer.render(
            model, infos(ov), ov.self.person.id,
            GraphImageRenderer.Quality.STANDARD,
            GraphImageRenderer.titleLine("杨明", LocalDate.of(2026, 10, 1)))
        val hd = GraphImageRenderer.render(
            model, infos(ov), ov.self.person.id,
            GraphImageRenderer.Quality.HD,
            GraphImageRenderer.titleLine("杨明", LocalDate.of(2026, 10, 1)))
        // 小家庭：高清为标准 2 倍
        assertEquals(std.width * 2, hd.width)
        assertTrue(maxOf(hd.width, hd.height) <= 4096)
        // 55 人家庭高清：最长边 4096 内
        val ov55 = overviewOf(TestFamily.graph())
        val gens55 = KinshipEngine().generations(
            ov55.graph, ov55.self.person.id)
        val m55 = GraphLayout.build(ov55, gens55)
        val hd55 = GraphImageRenderer.render(
            m55, infos(ov55), ov55.self.person.id,
            GraphImageRenderer.Quality.HD,
            GraphImageRenderer.titleLine("杨明", LocalDate.of(2026, 10, 1)))
        assertTrue(maxOf(hd55.width, hd55.height) <= 4096)
    }

    @Test
    fun 分支筛选_导出节点等于筛选后集合() {
        val ov = overviewOf(TestFamily.graph())
        val all = KinshipEngine().generations(ov.graph, ov.self.person.id)
        val paternal = io.github.xiaohaoz1.jiarenxia.ui.graph
            .GraphBranches.filter(all, ov, GraphBranch.FATHER)
        val model = GraphLayout.build(ov, paternal)
        // 模型节点集 = 筛选后集合（导出直接用同一个 model）
        assertEquals(paternal.keys, model.nodes.map { it.personId }.toSet())
        val bmp = GraphImageRenderer.render(
            model, infos(ov), ov.self.person.id,
            GraphImageRenderer.Quality.STANDARD,
            GraphImageRenderer.titleLine("杨明", LocalDate.of(2026, 10, 1)))
        assertNotNull(bmp)
    }

    @Test
    fun 相册值_目录与MIME() {
        val v = GraphExport.albumValues("家人侠-关系图-20261001.png")
        assertEquals("Pictures/家人侠",
            v.getAsString(android.provider.MediaStore.Images.Media
                .RELATIVE_PATH))
        assertEquals("image/png",
            v.getAsString(android.provider.MediaStore.Images.Media
                .MIME_TYPE))
        assertEquals(1, v.getAsInteger(
            android.provider.MediaStore.Images.Media.IS_PENDING))
    }

    @Test
    fun 写缓存_文件名含日期() {
        val name = GraphExport.fileName(LocalDate.of(2026, 10, 1))
        assertEquals("家人侠-关系图-20261001.png", name)
    }

    @Test
    fun 卡片矩形两两不相交() {
        val ov = overviewOf(TestFamily.graph())
        val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
        val model = GraphLayout.build(ov, gens)
        val rects = model.nodes.map { n ->
            n.personId to GraphImageRenderer.nodeRect(
                model, GraphImageRenderer.Quality.STANDARD, n)
        }
        val bad = StringBuilder()
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            if (android.graphics.RectF.intersects(
                    rects[i].second, rects[j].second)
            ) {
                bad.append(rects[i].first).append("×")
                    .append(rects[j].first).append(" ")
            }
        }
        assertTrue("节点卡片重叠：", bad.isEmpty())
    }

    @Test
    fun 标题文字顶部不被裁() {
        val ov = overviewOf(tinyGraph())
        val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
        val model = GraphLayout.build(ov, gens)
        val bmp = GraphImageRenderer.render(
            model, infos(ov), ov.self.person.id,
            GraphImageRenderer.Quality.STANDARD,
            GraphImageRenderer.titleLine("杨明", LocalDate.of(2026, 10, 1)))
        // 顶部存在留白（第 0 行是背景色），且标题区出现非背景像素
        assertEquals(
            -1, nonBgRow(bmp, 0))
        var firstInk = -1
        for (y in 0 until bmp.height) {
            if (nonBgRow(bmp, y) >= 0) { firstInk = y; break }
        }
        assertTrue("标题未画出", firstInk > 0)
        // 标题顶部与图顶之间应留有空隙（不小于字号的 1.5 倍换算）
        assertTrue("标题贴边：firstInk=", firstInk >= 10)
    }

    /** 返回该行第一个非背景像素的 x；整行背景返回 -1。 */
    private fun nonBgRow(bmp: android.graphics.Bitmap, y: Int): Int {
        for (x in 0 until bmp.width step 4) {
            val c = bmp.getPixel(x, y)
            if (android.graphics.Color.alpha(c) == 255 &&
                android.graphics.Color.red(c) != 0xFA
            ) return x
        }
        return -1
    }
}
