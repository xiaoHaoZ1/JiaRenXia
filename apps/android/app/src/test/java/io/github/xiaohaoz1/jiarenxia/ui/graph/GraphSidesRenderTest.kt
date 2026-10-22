package io.github.xiaohaoz1.jiarenxia.ui.graph

import android.graphics.Bitmap
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.backup.TestFamily
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 关系图分区修复的效果图（与屏幕同一套 GraphLayout + GraphImageRenderer）。
 * 输出 build/outputs/roborazzi/graph_sides_*.png 供人工对比。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GraphSidesRenderTest {

    private fun overviewOf(graph: FamilyGraph): FamilyOverview {
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
        return FamilyOverview(
            self = byId.getValue(self.id), graph = graph, byId = byId,
            core = emptyList(), others = emptyList()
        )
    }

    private fun infos(ov: FamilyOverview) = ov.byId.mapValues { (_, o) ->
        GraphImageRenderer.NodeInfo(
            name = o.person.name,
            title = if (o.person.isSelf) "我" else o.displayTitle,
            deceased = o.person.livingStatus !=
                io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.ALIVE)
    }

    private fun renderTo(
        graph: FamilyGraph, selfName: String, file: File
    ) {
        val ov = overviewOf(graph)
        val gens = KinshipEngine().generations(ov.graph, ov.self.person.id)
        val model = GraphLayout.build(ov, gens)
        val bmp = GraphImageRenderer.render(
            model, infos(ov), ov.self.person.id,
            GraphImageRenderer.Quality.STANDARD,
            GraphImageRenderer.titleLine(selfName, LocalDate.of(2026, 10, 1)))
        file.parentFile?.mkdirs()
        file.outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue(file.length() > 5_000)
    }

    /** 模拟真机结构（合成姓名）：父系姑姑一家 / 母系舅舅一家 / 双方祖辈。 */
    private fun deviceLikeGraph(): FamilyGraph {
        fun pp(name: String, gender: Gender = Gender.MALE, self: Boolean = false) =
            Person(id = name, name = name, gender = gender, isSelf = self)
        var seq = 0
        fun rr(src: String, tgt: String, t: RelationshipType) =
            Relationship("r" + (++seq), src, tgt, t, null)
        val persons = listOf(
            pp("杨永", self = true),
            pp("杨林"), pp("马春", Gender.FEMALE),
            pp("杨桓"), pp("王淑", Gender.FEMALE),
            pp("马丰"), pp("姥妮", Gender.FEMALE),
            pp("杨秀", Gender.FEMALE), pp("王宝"),
            pp("王蓓", Gender.FEMALE), pp("杨超"),
            pp("安女", Gender.FEMALE),
            pp("马舅"), pp("马革")
        )
        val rels = listOf(
            rr("杨林", "杨永", RelationshipType.PARENT),
            rr("马春", "杨永", RelationshipType.PARENT),
            rr("杨林", "马春", RelationshipType.SPOUSE),
            rr("杨桓", "杨林", RelationshipType.PARENT),
            rr("王淑", "杨林", RelationshipType.PARENT),
            rr("杨桓", "王淑", RelationshipType.SPOUSE),
            rr("杨桓", "杨秀", RelationshipType.PARENT),
            rr("王淑", "杨秀", RelationshipType.PARENT),
            rr("马丰", "马春", RelationshipType.PARENT),
            rr("姥妮", "马春", RelationshipType.PARENT),
            rr("马丰", "姥妮", RelationshipType.SPOUSE),
            rr("马丰", "马舅", RelationshipType.PARENT),
            rr("姥妮", "马舅", RelationshipType.PARENT),
            rr("马舅", "马革", RelationshipType.PARENT),
            rr("王宝", "杨秀", RelationshipType.SPOUSE),
            rr("王宝", "王蓓", RelationshipType.PARENT),
            rr("杨秀", "王蓓", RelationshipType.PARENT),
            rr("杨超", "王蓓", RelationshipType.SPOUSE),
            rr("杨超", "安女", RelationshipType.PARENT),
            rr("王蓓", "安女", RelationshipType.PARENT)
        )
        return FamilyGraph(persons, rels, emptyMap(), emptyMap())
    }

    @Test
    fun 渲染模拟真机家庭与测试家庭() {
        val dir = File("build/outputs/roborazzi")
        renderTo(deviceLikeGraph(), "杨永",
            File(dir, "graph_sides_device_like.png"))
        renderTo(TestFamily.graph(), "杨明",
            File(dir, "graph_sides_family55.png"))
    }
}
