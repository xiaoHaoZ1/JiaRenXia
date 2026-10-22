package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 父母补全只使用显式事实，所有含糊与异常情况均不推荐。 */
class ParentCompletionTest {
    private val child = Person("child", "小安", Gender.MALE)
    private val mother = Person("mother", "阿芳", Gender.FEMALE)
    private val father = Person("father", "阿明", Gender.MALE)
    private fun edge(a: String, b: String, type: RelationshipType) =
        Relationship("$a-$b-$type", a, b, type)
    private val parent = edge(mother.id, child.id, RelationshipType.PARENT)
    private val spouse = edge(father.id, mother.id, RelationshipType.SPOUSE)
    private fun graph(vararg edges: Relationship) =
        FamilyGraph(listOf(child, mother, father), edges.toList())

    @Test
    fun 母亲已有丈夫_只推荐该孩子且保留保存提示() {
        val ask = ParentCompletion.ask(graph(parent, spouse), child.id, "小安")!!
        assertEquals(mother.id, ask.spouseAId)
        assertEquals(father.id, ask.spouseBId)
        assertEquals("父亲", ask.parentLabel)
        assertEquals("小安", ask.noticeName)
        assertEquals(listOf(child.id), ask.candidates.map { it.child.id })
        assertEquals(father.id, ask.checkedSpecs().single().sourcePersonId)
        assertEquals(child.id, ask.checkedSpecs().single().targetPersonId)
    }

    @Test
    fun 反向子女记录与配偶记录均支持_父亲补母亲() {
        val ask = ParentCompletion.ask(graph(
            edge(child.id, father.id, RelationshipType.CHILD), spouse), child.id)!!
        assertEquals("母亲", ask.parentLabel)
        assertEquals(mother.id, ask.spouseBId)
    }

    @Test
    fun 已有父母或无父母不问() {
        assertNull(ParentCompletion.ask(graph(spouse), child.id))
        assertNull(ParentCompletion.ask(graph(parent, spouse,
            edge(father.id, child.id, RelationshipType.PARENT)), child.id))
    }

    @Test
    fun 多配偶或同性配偶不问() {
        val other = Person("other", "另一人", Gender.MALE)
        val multiple = graph(parent, spouse).copy(persons = listOf(child, mother, father, other),
            relationships = listOf(parent, spouse,
                edge(mother.id, other.id, RelationshipType.SPOUSE)))
        assertNull(ParentCompletion.ask(multiple, child.id))
        assertNull(ParentCompletion.ask(graph(parent, spouse).copy(
            persons = listOf(child, mother, father.copy(gender = Gender.FEMALE))), child.id))
    }

    @Test
    fun 孩子本身与缺失人物等异常不问() {
        assertNull(ParentCompletion.ask(graph(parent,
            edge(mother.id, child.id, RelationshipType.SPOUSE)), child.id))
        assertNull(ParentCompletion.ask(graph(parent, spouse).copy(
            persons = listOf(child, mother)), child.id))
        assertNull(ParentCompletion.ask(graph(parent, spouse,
            edge("missing", child.id, RelationshipType.PARENT)), child.id))
        assertNull(ParentCompletion.ask(graph(parent, spouse), "missing"))
    }
}
