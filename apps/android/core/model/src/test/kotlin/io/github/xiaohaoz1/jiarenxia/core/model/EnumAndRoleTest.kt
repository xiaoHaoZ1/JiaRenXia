package io.github.xiaohaoz1.jiarenxia.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeAgeOrderTest {
    @Test
    fun inverse翻转() {
        assertEquals(RelativeAgeOrder.YOUNGER, RelativeAgeOrder.OLDER.inverse())
        assertEquals(RelativeAgeOrder.OLDER, RelativeAgeOrder.YOUNGER.inverse())
        assertEquals(RelativeAgeOrder.SAME, RelativeAgeOrder.SAME.inverse())
    }
}

class RelationRoleTest {
    @Test
    fun 十二个角色的标签与性别() {
        assertEquals(Gender.MALE, RelationRole.FATHER.gender)
        assertEquals(Gender.FEMALE, RelationRole.MOTHER.gender)
        assertEquals(Gender.MALE, RelationRole.SON.gender)
        assertEquals(Gender.FEMALE, RelationRole.DAUGHTER.gender)
        assertEquals(Gender.MALE, RelationRole.HUSBAND.gender)
        assertEquals(Gender.FEMALE, RelationRole.WIFE.gender)
        assertEquals(Gender.MALE, RelationRole.ELDER_BROTHER.gender)
        assertEquals(Gender.FEMALE, RelationRole.ELDER_SISTER.gender)
        assertEquals(Gender.MALE, RelationRole.YOUNGER_BROTHER.gender)
        assertEquals(Gender.FEMALE, RelationRole.YOUNGER_SISTER.gender)
        assertEquals(Gender.MALE, RelationRole.BROTHER.gender)
        assertEquals(Gender.FEMALE, RelationRole.SISTER.gender)
        assertEquals("兄弟（长幼未知）", RelationRole.BROTHER.label)
        assertEquals("姐妹（长幼未知）", RelationRole.SISTER.label)
    }

    @Test
    fun toRelationshipSpec_父母() {
        val s = RelationRole.FATHER.toRelationshipSpec("p", "a")
        assertEquals(RelationshipSpec("p", "a", RelationshipType.PARENT, null), s)
        val m = RelationRole.MOTHER.toRelationshipSpec("p", "a")
        assertEquals(RelationshipSpec("p", "a", RelationshipType.PARENT, null), m)
    }

    @Test
    fun toRelationshipSpec_子女() {
        assertEquals(RelationshipSpec("a", "p", RelationshipType.PARENT, null),
            RelationRole.SON.toRelationshipSpec("p", "a"))
        assertEquals(RelationshipSpec("a", "p", RelationshipType.PARENT, null),
            RelationRole.DAUGHTER.toRelationshipSpec("p", "a"))
    }

    @Test
    fun toRelationshipSpec_配偶() {
        assertEquals(RelationshipSpec("p", "a", RelationshipType.SPOUSE, null),
            RelationRole.HUSBAND.toRelationshipSpec("p", "a"))
        assertEquals(RelationshipSpec("p", "a", RelationshipType.SPOUSE, null),
            RelationRole.WIFE.toRelationshipSpec("p", "a"))
    }

    @Test
    fun toRelationshipSpec_兄弟姐妹() {
        assertEquals(RelationshipSpec("p", "a", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            RelationRole.ELDER_BROTHER.toRelationshipSpec("p", "a"))
        assertEquals(RelationshipSpec("p", "a", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            RelationRole.ELDER_SISTER.toRelationshipSpec("p", "a"))
        assertEquals(RelationshipSpec("p", "a", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
            RelationRole.YOUNGER_BROTHER.toRelationshipSpec("p", "a"))
        assertEquals(RelationshipSpec("p", "a", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
            RelationRole.YOUNGER_SISTER.toRelationshipSpec("p", "a"))
        assertEquals(RelationshipSpec("p", "a", RelationshipType.SIBLING, null),
            RelationRole.BROTHER.toRelationshipSpec("p", "a"))
        assertEquals(RelationshipSpec("p", "a", RelationshipType.SIBLING, null),
            RelationRole.SISTER.toRelationshipSpec("p", "a"))
    }
}
