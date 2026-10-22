package io.github.xiaohaoz1.jiarenxia.ui.addfamily

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** buildQuickEntries：合并后的快捷入口卡（祖辈/配偶父母成对）。 */
class QuickEntriesTest {

    private fun person(id: String, gender: Gender = Gender.MALE, self: Boolean = false) =
        Person(id = id, name = id, gender = gender, isSelf = self)

    private fun rel(s: String, t: String, type: RelationshipType) =
        Relationship("r--", s, t, type)

    private fun overview(
        persons: List<Person>,
        rels: List<Relationship>
    ): FamilyOverview {
        val graph = FamilyGraph(persons, rels)
        val self = persons.first { it.isSelf }
        val results = KinshipEngine().resolveAll(graph, self.id)
        val byId = persons.associate {
            it.id to PersonOverview(it, results.getValue(it.id), emptyList())
        }
        return FamilyOverview(
            self = byId.getValue(self.id), graph = graph, byId = byId,
            core = emptyList(), others = emptyList()
        )
    }

    private val self = person("me", self = true)

    @Test
    fun 空家庭() {
        val e = buildQuickEntries(overview(listOf(self), emptyList()))
        val labels = e.map { it.label }
        assertEquals(
            listOf(
                "父亲", "母亲", "配偶", "子女", "兄弟姐妹", "爷爷奶奶", "姥爷姥姥",
                "配偶的父母",
                "伯叔姑", "舅舅姨妈", "堂表兄弟姐妹", "侄子外甥", "儿媳女婿", "孙辈",
                "祖辈的兄弟姐妹", "父母的堂表亲", "配偶的兄弟姐妹", "配偶的叔伯姑姨",
                "其他亲属"
            ),
            labels
        )
        // 祖辈与配偶父母禁用并带提示
        assertFalse(e.first { it.label == "爷爷奶奶" }.enabled)
        assertEquals("请先添加父亲", e.first { it.label == "爷爷奶奶" }.hint)
        assertEquals(listOf(RelationRole.FATHER), e.first { it.label == "爷爷奶奶" }.hintRoles)
        assertFalse(e.first { it.label == "姥爷姥姥" }.enabled)
        assertFalse(e.first { it.label == "配偶的父母" }.enabled)
        assertEquals("请先添加配偶", e.first { it.label == "配偶的父母" }.hint)
        assertEquals(
            listOf(RelationRole.HUSBAND, RelationRole.WIFE),
            e.first { it.label == "配偶的父母" }.hintRoles
        )
    }

    @Test
    fun 仅父亲_祖辈卡可用且双角色() {
        val dad = person("dad")
        val e = buildQuickEntries(
            overview(listOf(self, dad), listOf(rel("dad", "me", RelationshipType.PARENT)))
        )
        val gp = e.first { it.label == "爷爷奶奶" }
        assertTrue(gp.enabled)
        assertEquals("dad", gp.anchorId)
        assertEquals(listOf(RelationRole.FATHER, RelationRole.MOTHER), gp.roles)
        // “父亲”卡消失，“姥爷姥姥”仍禁用
        assertTrue(e.none { it.label == "父亲" })
        assertFalse(e.first { it.label == "姥爷姥姥" }.enabled)
    }

    @Test
    fun 父系祖辈齐全_卡隐藏() {
        val dad = person("dad")
        val gp1 = person("gp1")
        val gp2 = person("gp2", Gender.FEMALE)
        val e = buildQuickEntries(
            overview(
                listOf(self, dad, gp1, gp2),
                listOf(
                    rel("dad", "me", RelationshipType.PARENT),
                    rel("gp1", "dad", RelationshipType.PARENT),
                    rel("gp2", "dad", RelationshipType.PARENT)
                )
            )
        )
        assertTrue(e.none { it.label == "爷爷奶奶" })
        // 只缺奶奶时卡片仍在、只剩 MOTHER 角色
        val e2 = buildQuickEntries(
            overview(
                listOf(self, dad, gp1),
                listOf(
                    rel("dad", "me", RelationshipType.PARENT),
                    rel("gp1", "dad", RelationshipType.PARENT)
                )
            )
        )
        val card = e2.first { it.label == "爷爷奶奶" }
        assertEquals(listOf(RelationRole.MOTHER), card.roles)
    }

    @Test
    fun 配偶父母称谓按性别() {
        val wife = person("wife", Gender.FEMALE)
        val e = buildQuickEntries(
            overview(listOf(self, wife), listOf(rel("me", "wife", RelationshipType.SPOUSE)))
        )
        assertTrue(e.any { it.label == "岳父岳母" })
        assertEquals(
            listOf(RelationRole.FATHER, RelationRole.MOTHER),
            e.first { it.label == "岳父岳母" }.roles
        )

        val selfF = person("me", Gender.FEMALE, self = true)
        val husband = person("husband")
        val e2 = buildQuickEntries(
            overview(listOf(selfF, husband), listOf(rel("me", "husband", RelationshipType.SPOUSE)))
        )
        assertTrue(e2.any { it.label == "公公婆婆" })

        // 同性配偶 → 通用称谓
        val husband2 = person("husband2")
        val e3 = buildQuickEntries(
            overview(listOf(self, husband2), listOf(rel("me", "husband2", RelationshipType.SPOUSE)))
        )
        assertTrue(e3.any { it.label == "配偶的父母" })
    }
}

// ---------- “更多亲戚”分区（B2）----------

private fun mkPerson(id: String, gender: Gender = Gender.MALE, self: Boolean = false) =
    Person(id = id, name = id, gender = gender, isSelf = self)

private fun mkRel(s: String, t: String, type: RelationshipType) =
    Relationship("r--", s, t, type)

private fun overviewFor(
    persons: List<Person>,
    rels: List<Relationship>
): FamilyOverview {
    val graph = FamilyGraph(persons, rels)
    val self = persons.first { it.isSelf }
    val results = KinshipEngine().resolveAll(graph, self.id)
    val byId = persons.associate {
        it.id to PersonOverview(it, results.getValue(it.id), emptyList())
    }
    return FamilyOverview(
        self = byId.getValue(self.id), graph = graph, byId = byId,
        core = emptyList(), others = emptyList()
    )
}

private fun testFamilyOverview(): FamilyOverview {
    val graph = io.github.xiaohaoz1.jiarenxia.data.backup.TestFamily.graph()
    val self = graph.persons.first { it.isSelf }
    val results = KinshipEngine().resolveAll(graph, self.id)
    val byId = graph.persons.associate {
        it.id to PersonOverview(it, results.getValue(it.id), emptyList())
    }
    return FamilyOverview(
        self = byId.getValue(self.id), graph = graph, byId = byId,
        core = emptyList(), others = emptyList()
    )
}

class QuickEntriesMoreTest {

    private val sibRoles = listOf(
        RelationRole.ELDER_BROTHER, RelationRole.YOUNGER_BROTHER,
        RelationRole.ELDER_SISTER, RelationRole.YOUNGER_SISTER,
        RelationRole.BROTHER, RelationRole.SISTER
    )

    /** TestFamily 全家谱：十个入口全部可用且锚点正确。 */
    @Test
    fun 测试家庭_更多亲戚锚点() {
        val e = buildQuickEntries(testFamilyOverview())
        fun find(l: String) = e.first { it.label == l }

        find("伯叔姑").let {
            assertTrue(it.enabled)
            assertEquals("杨建国", it.anchorId)
            assertEquals(sibRoles, it.roles)
        }
        find("舅舅姨妈").let {
            assertTrue(it.enabled); assertEquals("王秀兰", it.anchorId)
        }
        find("堂表兄弟姐妹").let {
            assertTrue(it.enabled)
            assertEquals(
                setOf("杨建春", "杨建英", "王志刚"),
                it.anchorCandidates.toSet()
            )
        }
        find("侄子外甥").let {
            assertTrue(it.enabled); assertEquals("杨帆", it.anchorId)
        }
        find("儿媳女婿").let {
            assertTrue(it.enabled); assertEquals("杨小明", it.anchorId)
            assertEquals(listOf(RelationRole.HUSBAND, RelationRole.WIFE), it.roles)
        }
        find("孙辈").let {
            assertTrue(it.enabled); assertEquals("杨小明", it.anchorId)
        }
        find("祖辈的兄弟姐妹").let {
            assertTrue(it.enabled)
            assertEquals(
                setOf("杨福生", "李桂英", "王德福", "赵淑珍"),
                it.anchorCandidates.toSet()
            )
        }
        find("父母的堂表亲").let {
            assertTrue(it.enabled)
            assertEquals(
                setOf("杨福来", "杨福英", "李桂林"),
                it.anchorCandidates.toSet()
            )
        }
        find("配偶的兄弟姐妹").let {
            assertTrue(it.enabled); assertEquals("李雪", it.anchorId)
        }
        find("配偶的叔伯姑姨").let {
            assertTrue(it.enabled)
            assertEquals(setOf("李国强", "陈美华"), it.anchorCandidates.toSet())
        }
        // 其他亲属仍是最后一项：trailing（不属于更多亲戚分区，onboarding 也有）
        assertEquals("其他亲属", e.last().label)
        assertTrue(e.last().trailing)
        assertFalse(e.last().moreSection)
    }

    /** onboarding 视图过滤后：只剩基础条目 + 垫底的其他亲属（回归：其他亲属曾误入 moreSection）。 */
    @Test
    fun onboarding_其他亲属仍显示且垫底() {
        val e = buildQuickEntries(testFamilyOverview())
        val onboarding = e.filter { !it.moreSection }
        assertEquals("其他亲属", onboarding.last().label)
        assertTrue(onboarding.none { it.moreSection })
        // 普通模式的最后也是其他亲属
        assertEquals("其他亲属", e.last().label)
        // 更多亲戚分区整体垫底于基础条目之前
        val lastBase = e.indexOfLast { !it.moreSection && !it.trailing }
        val firstMore = e.indexOfFirst { it.moreSection }
        assertTrue(firstMore > lastBase)
    }

    /** 只有“我”：分区全部禁用，提示与 hintRoles/hintAnchorId 符合表。 */
    @Test
    fun 更多亲戚_仅我时禁用() {
        val onlySelf = listOf(mkPerson("me", self = true))
        val e = buildQuickEntries(overviewFor(onlySelf, emptyList()))
        fun find(l: String) = e.first { it.label == l }

        find("伯叔姑").let {
            assertFalse(it.enabled)
            assertEquals("请先添加父亲", it.hint)
            assertEquals(listOf(RelationRole.FATHER), it.hintRoles)
            assertEquals("me", it.hintAnchorId)
        }
        find("舅舅姨妈").let {
            assertFalse(it.enabled)
            assertEquals("请先添加母亲", it.hint)
            assertEquals(listOf(RelationRole.MOTHER), it.hintRoles)
        }
        find("堂表兄弟姐妹").let {
            assertFalse(it.enabled)
            assertEquals("请先添加伯叔姑或舅舅姨妈", it.hint)
            assertTrue(it.hintRoles.isEmpty())   // 无角色 → 界面不出现“去添加”
        }
        find("侄子外甥").let {
            assertFalse(it.enabled)
            assertEquals("请先添加兄弟姐妹", it.hint)
            assertEquals(sibRoles, it.hintRoles)
        }
        find("儿媳女婿").let {
            assertFalse(it.enabled)
            assertEquals("请先添加子女", it.hint)
            assertEquals(
                listOf(RelationRole.SON, RelationRole.DAUGHTER), it.hintRoles
            )
        }
        find("孙辈").let {
            assertFalse(it.enabled); assertEquals("请先添加子女", it.hint)
        }
        find("祖辈的兄弟姐妹").let {
            assertFalse(it.enabled)
            assertEquals("请先添加爷爷奶奶或姥爷姥姥", it.hint)
            assertTrue(it.hintRoles.isEmpty())
        }
        find("父母的堂表亲").let {
            assertFalse(it.enabled)
            assertEquals("请先添加祖辈的兄弟姐妹", it.hint)
            assertTrue(it.hintRoles.isEmpty())
        }
        find("配偶的兄弟姐妹").let {
            assertFalse(it.enabled)
            assertEquals("请先添加配偶", it.hint)
            assertEquals(
                listOf(RelationRole.HUSBAND, RelationRole.WIFE), it.hintRoles
            )
        }
        find("配偶的叔伯姑姨").let {
            assertFalse(it.enabled)
            assertEquals("请先添加配偶", it.hint)
            assertEquals(
                listOf(RelationRole.HUSBAND, RelationRole.WIFE), it.hintRoles
            )
            assertEquals("me", it.hintAnchorId)
        }
    }

    /** 有配偶但配偶无父母 → “请先添加配偶的父母”且锚点为配偶。 */
    @Test
    fun 配偶的叔伯姑姨_有配偶无父母() {
        val me = mkPerson("me", self = true)
        val wife = mkPerson("wife", Gender.FEMALE)
        val e = buildQuickEntries(
            overviewFor(
                listOf(me, wife),
                listOf(mkRel("me", "wife", RelationshipType.SPOUSE))
            )
        )
        val it = e.first { x -> x.label == "配偶的叔伯姑姨" }
        assertFalse(it.enabled)
        assertEquals("请先添加配偶的父母", it.hint)
        assertEquals(listOf(RelationRole.FATHER, RelationRole.MOTHER), it.hintRoles)
        assertEquals("wife", it.hintAnchorId)
    }

    /** 祖辈标签与提示文案固定为引擎标准称谓（姥爷姥姥）。 */
    @Test
    fun 祖辈标签与提示() {
        val e = buildQuickEntries(
            overviewFor(listOf(mkPerson("me", self = true)), emptyList())
        )
        assertTrue(e.any { it.label == "姥爷姥姥" })
        assertEquals(
            "请先添加爷爷奶奶或姥爷姥姥",
            e.first { it.label == "祖辈的兄弟姐妹" }.hint
        )
    }
}
