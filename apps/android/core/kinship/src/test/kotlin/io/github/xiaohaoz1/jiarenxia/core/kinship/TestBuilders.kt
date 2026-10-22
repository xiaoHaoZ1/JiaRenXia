package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder

/** 测试用的小型家庭图构建辅助。 */
object T {
    private var seq = 0

    fun p(id: String, gender: Gender = Gender.MALE, self: Boolean = false) =
        Person(id = id, name = id, gender = gender, isSelf = self)

    fun rel(src: String, tgt: String, type: RelationshipType, order: RelativeAgeOrder? = null): Relationship {
        seq += 1
        return Relationship("r", src, tgt, type, order)
    }

    fun graph(
        persons: List<Person>,
        rels: List<Relationship>,
        birthdays: Map<String, PartialDate> = emptyMap()
    ) = FamilyGraph(persons, rels, birthdays)

    fun bd(y: Int, m: Int, d: Int) = PartialDate(y, m, d)
    fun bd(m: Int, d: Int) = PartialDate(null, m, d)
}

/** 常见家庭：我(男) + 父母 + 配偶 + 一儿一女。 */
fun baseFamily(): Pair<FamilyGraph, String> {
    val me = "me"
    val persons = listOf(
        T.p(me, Gender.MALE, self = true),
        T.p("dad", Gender.MALE), T.p("mom", Gender.FEMALE),
        T.p("wife", Gender.FEMALE),
        T.p("son", Gender.MALE), T.p("daughter", Gender.FEMALE)
    )
    val rels = listOf(
        T.rel("dad", me, RelationshipType.PARENT),
        T.rel("mom", me, RelationshipType.PARENT),
        T.rel("wife", me, RelationshipType.SPOUSE),
        T.rel(me, "son", RelationshipType.PARENT),
        T.rel(me, "daughter", RelationshipType.PARENT)
    )
    return T.graph(persons, rels) to me
}
