package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** 多原生家庭与旁支子女的合成回归，所有姓名与手机资料无关。 */
class GraphFamilyGroupingTest {
    companion object {
        /** 十八人家庭：双方祖辈、父母、姑姑一家、姨妈舅舅、表姐妹的女儿。 */
        fun overview(femaleSelf: Boolean = false, reversedParent: Boolean = false,
                     ageGroups: Boolean = false): FamilyOverview {
            val people = listOf(
                Triple("self", "陈志远", Gender.MALE), Triple("partner", "林晓晴", Gender.FEMALE),
                Triple("father", "陈建国", Gender.MALE), Triple("mother", "周秀华", Gender.FEMALE),
                Triple("pgf", "陈永安", Gender.MALE), Triple("pgm", "王桂兰", Gender.FEMALE),
                Triple("mgf", "周国平", Gender.MALE), Triple("mgm", "李玉芬", Gender.FEMALE),
                Triple("inlawFather", "林振华", Gender.MALE), Triple("inlawMother", "吴文英", Gender.FEMALE),
                Triple("paternalAunt", "陈静怡", Gender.FEMALE), Triple("uncleByMarriage", "许文成", Gender.MALE),
                Triple("maternalAunt", "周秋月", Gender.FEMALE), Triple("maternalUncle", "周明轩", Gender.MALE),
                Triple("paternalCousin", "许思宁", Gender.FEMALE), Triple("cousinPartner", "赵承安", Gender.MALE),
                Triple("maternalCousin", "周亦辰", Gender.MALE), Triple("niece", "赵小禾", Gender.FEMALE)
            ).map { (id, name, gender) -> Person(id, name, gender, isSelf = id == if (femaleSelf) "partner" else "self") }.toMutableList()
            val rels = mutableListOf<Relationship>()
            fun r(a: String, b: String, t: RelationshipType) { rels += Relationship("r${rels.size}", a, b, t, null) }
            fun couple(a: String, b: String) = r(a, b, RelationshipType.SPOUSE)
            fun parent(a: String, b: String) = r(a, b, RelationshipType.PARENT)
            couple("self", "partner"); couple("father", "mother")
            couple("pgf", "pgm"); couple("mgf", "mgm"); couple("inlawFather", "inlawMother")
            couple("uncleByMarriage", "paternalAunt"); couple("cousinPartner", "paternalCousin")
            for (p in listOf("pgf", "pgm")) for (c in listOf("father", "paternalAunt")) parent(p, c)
            for (p in listOf("mgf", "mgm")) for (c in listOf("mother", "maternalAunt", "maternalUncle")) parent(p, c)
            parent("father", "self"); parent("mother", "self")
            parent("inlawFather", "partner"); parent("inlawMother", "partner")
            if (reversedParent) parent("paternalCousin", "uncleByMarriage")
            else parent("uncleByMarriage", "paternalCousin")
            parent("paternalAunt", "paternalCousin")
            parent("maternalUncle", "maternalCousin")
            // 仅记录一位父母，不因其存在配偶就推断另一条亲子关系。
            parent("paternalCousin", "niece")
            if (ageGroups) {
                people += Person("younger", "陈子衡", Gender.MALE)
                for (id in listOf("paternalCousin", "maternalCousin")) {
                    rels += Relationship("older-$id", id, "self", RelationshipType.COUSIN,
                        io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder.OLDER)
                }
                rels += Relationship("younger", "younger", "self", RelationshipType.SIBLING,
                    io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder.YOUNGER)
                parent("father", "younger"); parent("mother", "younger")
            }
            val graph = FamilyGraph(people, rels)
            val self = people.first { it.isSelf }
            val results = KinshipEngine().resolveAll(graph, self.id)
            val byId = people.associate { it.id to PersonOverview(it, results.getValue(it.id)) }
            return FamilyOverview(byId.getValue(self.id), graph, byId, emptyList(), emptyList())
        }
    }

    private fun model(femaleSelf: Boolean = false): GraphModel {
        val ov = overview(femaleSelf)
        return GraphLayout.build(ov, KinshipEngine().generations(ov.graph, ov.self.person.id))
    }
    private fun GraphModel.at(id: String) = nodes.first { it.personId == id }

    @Test fun 同辈按长幼分排_仍在父母与晚辈之间() {
        val base = overview()
        val younger = Person("younger", "陈子衡", Gender.MALE)
        val rels = base.graph.relationships + listOf(
            Relationship("older", "paternalCousin", "self", RelationshipType.COUSIN,
                io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder.OLDER),
            Relationship("younger", "younger", "self", RelationshipType.SIBLING,
                io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder.YOUNGER))
        val graph = FamilyGraph(base.graph.persons + younger, rels)
        val result = KinshipEngine().resolveAll(graph, "self")
        val ov = base.copy(graph = graph, byId = base.byId +
            (younger.id to PersonOverview(younger, result.getValue(younger.id))))
        val m = GraphLayout.build(ov, KinshipEngine().generations(graph, "self"))
        assertTrue(m.at("father").bottom < m.at("paternalCousin").y)
        assertTrue(m.at("paternalCousin").bottom < m.at("self").y)
        assertTrue(m.at("self").bottom < m.at("younger").y)
        assertTrue(m.at("younger").bottom < m.at("niece").y)
        assertEquals(0, m.at("younger").generation)
        assertEquals(0, m.at("paternalCousin").generation)
        assertEquals(m.at("paternalCousin").y, m.at("cousinPartner").y, 0.01f)
        assertEquals(m.at("self").y, m.at("maternalCousin").y, 0.01f)
    }

    @Test fun 长幼分排不重复画堂表亲线_不同家庭不共用侧廊竖线() {
        val ov = overview(ageGroups = true)
        val m = GraphLayout.build(ov, KinshipEngine().generations(ov.graph, "self"))
        assertFalse(m.edges.any { it.type == RelationshipType.COUSIN })
        assertTrue(ov.graph.relationships.any { it.type == RelationshipType.COUSIN })
        val paths = GraphEdgeRouting.route(m).paths
        val mom = m.at("paternalCousin")
        val younger = m.at("younger")
        val parentPath = paths.first { it.points.size == 4 &&
            abs(it.points.first().x - mom.cx) < 0.01f &&
            abs(it.points.first().y - mom.bottom) < 0.01f }
        val youngerPath = paths.first { it.points.size == 4 &&
            abs(it.points.last().x - younger.cx) < 0.01f &&
            abs(it.points.last().y - younger.y) < 0.01f }
        assertTrue(abs(parentPath.points[2].x - youngerPath.points[0].x) >= 2f)
    }

    @Test fun 母亲的兄弟姐妹相邻成组_不隔着父亲() {
        val m = model()
        val mom = m.at("mother")
        for (id in listOf("maternalAunt", "maternalUncle")) {
            val n = m.at(id)
            assertEquals("少量同辈不应被机械折到下一排", mom.y, n.y, 0.01f)
            assertTrue("母系旁支应在母亲一侧，不能跨过父亲", n.x >= mom.right)
        }
        assertTrue("父系旁支应在父亲一侧", m.at("paternalAunt").right < m.at("father").x)
    }

    @Test fun 旁支女儿位于自己的母亲下方_不能移到本人下方() {
        val m = model()
        val child = m.at("niece")
        val parent = m.at("paternalCousin")
        assertEquals(parent.generation + 1, child.generation)
        assertEquals("仅记录母亲的孩子，应沿母亲向下排列", parent.cx, child.cx, 0.01f)
        assertTrue(abs(child.cx - parent.cx) < abs(child.cx - m.at("self").cx))
        assertFalse(m.edges.any { it.fromId == "cousinPartner" && it.toId == "niece" })
    }

    @Test fun 舅舅的孩子沿舅舅这一支向下排() {
        val m = model()
        val child = m.at("maternalCousin")
        val parent = m.at("maternalUncle")
        assertTrue("表亲应靠自己的父亲，而非姑姑一家", abs(child.cx - parent.cx) < abs(child.cx - m.at("paternalAunt").cx))
    }

    @Test fun 男方的表亲不能因为父母横坐标而越过主夫妻到女方侧() {
        for (femaleSelf in listOf(false, true)) {
            val m = model(femaleSelf)
            assertTrue("男方表亲应在男方左边，女方本人视角也不换边",
                m.at("maternalCousin").right < m.at("self").x)
        }
    }

    @Test fun 反向亲子记录不倒画成上一辈到下一辈的折返实线() {
        val ov = overview(reversedParent = true)
        val m = GraphLayout.build(ov, KinshipEngine().generations(ov.graph, ov.self.person.id))
        val bad = m.edges.single { it.fromId == "paternalCousin" && it.toId == "uncleByMarriage" }
        val routing = GraphEdgeRouting.route(m)
        assertEquals(listOf(bad), m.parentGenerationConflicts)
        assertTrue("原始关系仍保留，不篡改资料", ov.graph.relationships.any {
            it.sourcePersonId == bad.fromId && it.targetPersonId == bad.toId
        })
        assertTrue("冲突亲子关系不能反着画成合法的父母子女线", routing.paths.none { it.edge == bad })
        val child = m.at("paternalCousin")
        assertTrue("有效母女关系仍直落卡片上端口", routing.paths.any { p ->
            p.style == GraphEdgeRouting.RouteStyle.SOLID && p.points.size == 2 &&
                abs(p.points.first().x - child.cx) < 0.01f &&
                abs(p.points.last().x - child.cx) < 0.01f &&
                abs(p.points.last().y - child.y) < 0.01f
        })
    }

    @Test fun 女方本人视角也保持同样的男方家庭内部顺序() {
        val a = model(); val b = model(true)
        for (m in listOf(a, b)) {
            assertTrue(m.at("paternalAunt").x < m.at("father").x)
            assertTrue(m.at("mother").x < m.at("maternalUncle").x)
            assertEquals(m.at("paternalCousin").cx, m.at("niece").cx, 0.01f)
        }
    }

    @Test fun 非关联线的交叉有跨线标记_不画成家庭接点() {
        val m = model()
        val routing = GraphEdgeRouting.route(m)
        val segments = routing.paths.flatMap { it.points.zipWithNext() }
        for ((a, b) in segments) for ((c, d) in segments) {
            if (abs(a.x - b.x) > 0.01f || abs(c.y - d.y) > 0.01f) continue
            val crossing = a.x > minOf(c.x, d.x) + 0.01f && a.x < maxOf(c.x, d.x) - 0.01f &&
                c.y > minOf(a.y, b.y) + 0.01f && c.y < maxOf(a.y, b.y) - 0.01f
            if (crossing) assertTrue("非关联线交叉必须明确标注：$a→$b 与 $c→$d",
                routing.crossings.any { abs(it.x - a.x) < 0.01f && abs(it.y - c.y) < 0.01f && it.radius > 0 })
        }
    }
}
