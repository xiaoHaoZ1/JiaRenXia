package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** generations 分代（DEVELOPMENT_PLAN 2.11 二）与 coreGroup 分组（2.2）。 */
class GenerationsTest {

    private val engine = KinshipEngine()

    private val graph = T.graph(
        listOf(
            T.p("me", Gender.MALE, self = true),
            T.p("dad", Gender.MALE),
            T.p("gp", Gender.MALE),
            T.p("uncle", Gender.MALE),
            T.p("wife", Gender.FEMALE),
            T.p("fil", Gender.MALE),
            T.p("bro", Gender.MALE),
            T.p("son", Gender.MALE),
            T.p("stranger", Gender.MALE)
        ),
        listOf(
            T.rel("dad", "me", RelationshipType.PARENT),
            T.rel("gp", "dad", RelationshipType.PARENT),
            T.rel("uncle", "dad", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            T.rel("wife", "me", RelationshipType.SPOUSE),
            T.rel("fil", "wife", RelationshipType.PARENT),
            T.rel("bro", "me", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            T.rel("me", "son", RelationshipType.PARENT)
        )
    )

    @Test
    fun 分代() {
        val gen = engine.generations(graph, "me")
        assertEquals(-2, gen["gp"])
        assertEquals(-1, gen["dad"])
        assertEquals(-1, gen["uncle"])
        assertEquals(-1, gen["fil"])
        assertEquals(0, gen["me"])
        assertEquals(0, gen["wife"])
        assertEquals(0, gen["bro"])
        assertEquals(1, gen["son"])
        assertFalse(gen.containsKey("stranger"))
    }
}

class CoreGroupTest {

    private val engine = KinshipEngine()

    private fun resolve(target: String, femaleObserver: Boolean = false): KinshipResult {
        val me = T.p("me", if (femaleObserver) Gender.FEMALE else Gender.MALE, self = true)
        val persons = mutableListOf(
            me,
            T.p("dad", Gender.MALE), T.p("mom", Gender.FEMALE),
            T.p("gmM", Gender.FEMALE),
            T.p("spouse", if (femaleObserver) Gender.MALE else Gender.FEMALE),
            T.p("daughter", Gender.FEMALE),
            T.p("spouseMom", Gender.FEMALE),
            T.p("bro", Gender.MALE),
            T.p("uncle", Gender.MALE),
            T.p("bw", Gender.FEMALE)
        )
        val rels = listOf(
            T.rel("dad", "me", RelationshipType.PARENT),
            T.rel("mom", "me", RelationshipType.PARENT),
            T.rel("gmM", "mom", RelationshipType.PARENT),
            T.rel("spouse", "me", RelationshipType.SPOUSE),
            T.rel("me", "daughter", RelationshipType.PARENT),
            T.rel("spouseMom", "spouse", RelationshipType.PARENT),
            T.rel("bro", "me", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            T.rel("uncle", "dad", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            T.rel("bw", "bro", RelationshipType.SPOUSE)
        )
        return engine.resolve(T.graph(persons, rels), "me", target)
    }

    @Test
    fun 五类核心家人分组() {
        assertEquals(CoreGroup.PARENTS, resolve("dad").coreGroup)
        assertEquals(CoreGroup.GRANDPARENTS, resolve("gmM").coreGroup)
        assertEquals(CoreGroup.SPOUSE, resolve("spouse").coreGroup)
        assertEquals(CoreGroup.CHILDREN, resolve("daughter").coreGroup)
        assertEquals(CoreGroup.SPOUSE_PARENTS, resolve("spouseMom", femaleObserver = true).coreGroup)
    }

    @Test
    fun 非核心路径与本人为null() {
        assertNull(resolve("bro").coreGroup)   // 哥哥
        assertNull(resolve("uncle").coreGroup) // 伯父
        assertNull(resolve("bw").coreGroup)    // 嫂子
        assertNull(resolve("me").coreGroup)    // 我（SELF）
    }
}
