package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.domain.NEW_PERSON
import io.github.xiaohaoz1.jiarenxia.domain.RelativePlan
import io.github.xiaohaoz1.jiarenxia.domain.SiblingLineage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 称谓录入的多关系事务写入（DEVELOPMENT_PLAN 2.14）：
 * 新人物 + 兄妹 + 确认的共同父母一次事务；任一校验失败整体回滚。
 */
class CreateRelativeSpecsTest : RepoTestBase() {

    private fun draft(name: String, gender: Gender = Gender.FEMALE) =
        PersonDraft(name = name, gender = gender)

    /** 建“我”+ 爸 + 妈，返回三元组。 */
    private suspend fun familyOfThree(): Triple<String, String, String> {
        val me = createSelf()
        val dad = repo.createRelative(draft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        val mom = repo.createRelative(draft("妈", Gender.FEMALE), me, RelationRole.MOTHER).ok()
        return Triple(me, dad, mom)
    }

    private suspend fun relsOf(id: String) =
        db.relationshipDao().getForPerson(id)

    @Test
    fun 亲姐姐带同父同母一次事务写入() = runTest {
        val (me, dad, mom) = familyOfThree()
        val specs = RelativePlan.buildSpecs(
            NEW_PERSON, me, RelationRole.ELDER_SISTER,
            SiblingLineage.REAL, parentIds = listOf(dad, mom)
        )
        val sis = repo.createRelative(draft("姐姐"), specs).ok()

        val rels = relsOf(sis)
        // 兄妹（OLDER）+ 爸、妈两条 PARENT，共三条
        assertEquals(3, rels.size)
        val sib = rels.single { it.type == RelationshipType.SIBLING }
        // 对称关系规范化为 source<target；姐姐相对我是年长一方
        assertNotNull(sib.relativeAgeOrder)
        val parents = rels.filter { it.type == RelationshipType.PARENT }
            .map { it.sourcePersonId }.toSet()
        assertEquals(setOf(dad, mom), parents)
        // 目标都是新人物
        assertTrue(parents.isNotEmpty())
        assertTrue(rels.filter { it.type == RelationshipType.PARENT }
            .all { it.targetPersonId == sis })
    }

    @Test
    fun 未知长幼只写兄妹不带长幼() = runTest {
        val me = createSelf()
        val specs = RelativePlan.buildSpecs(
            NEW_PERSON, me, RelationRole.SISTER, SiblingLineage.REAL
        )
        val sis = repo.createRelative(draft("某姐妹"), specs).ok()
        val sib = relsOf(sis).single { it.type == RelationshipType.SIBLING }
        assertNull(sib.relativeAgeOrder)
    }

    @Test
    fun 仅一位祖辈时只补已知父亲() = runTest {
        val (me, dad, _) = familyOfThree()
        // 同父不同母：只挂 dad
        val specs = RelativePlan.buildSpecs(
            NEW_PERSON, me, RelationRole.YOUNGER_SISTER,
            SiblingLineage.REAL, parentIds = listOf(dad)
        )
        val sis = repo.createRelative(draft("同父妹妹"), specs).ok()
        val parents = relsOf(sis).filter { it.type == RelationshipType.PARENT }
        assertEquals(1, parents.size)
        assertEquals(dad, parents.first().sourcePersonId)
    }

    @Test
    fun 堂姑经中间人落库_父母边加申报堂表亲_不产生直接兄妹() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(draft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        val gfd = repo.createRelative(draft("爷爷", Gender.MALE), dad, RelationRole.FATHER).ok()
        val guncle = repo.createRelative(
            draft("伯公", Gender.MALE), gfd, RelationRole.ELDER_BROTHER).ok()

        // 堂姑 = 伯公的女儿；与爸爸之间申报堂表亲而非兄妹
        val specs = RelativePlan.buildSpecs(
            NEW_PERSON, dad, RelationRole.SISTER,
            SiblingLineage.COUSIN, middleId = guncle
        )
        val aunt = repo.createRelative(draft("堂姑"), specs).ok()
        val rels = relsOf(aunt)
        assertEquals(2, rels.size)
        val parent = rels.single { it.type == RelationshipType.PARENT }
        assertEquals(guncle, parent.sourcePersonId)
        assertTrue(rels.none { it.type == RelationshipType.SIBLING })
        // 与爸爸之间落一条申报堂表亲记录（SISTER 角色：长幼未知）
        val between = db.relationshipDao().getBetween(aunt, dad)
        assertEquals(1, between.size)
        assertEquals(RelationshipType.COUSIN, between.first().type)
        assertNull(between.first().relativeAgeOrder)
    }

    @Test
    fun 堂表亲申报记录保留所选长幼_对称规范化方向正确() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(draft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        // 新人（堂姐）比爸爸年长：ELDER_SISTER 角色 → 新人相对锚点 OLDER
        val specs = RelativePlan.buildSpecs(
            NEW_PERSON, dad, RelationRole.ELDER_SISTER, SiblingLineage.COUSIN
        )
        val aunt = repo.createRelative(draft("堂姐"), specs).ok()
        val between = db.relationshipDao().getBetween(aunt, dad)
        assertEquals(1, between.size)
        val cousin = between.first()
        assertEquals(RelationshipType.COUSIN, cousin.type)
        // 规范化为 source<target；年龄序字段始终表示 source 相对 target
        val expected = if (aunt < dad) RelativeAgeOrder.OLDER else RelativeAgeOrder.YOUNGER
        assertEquals(expected, cousin.relativeAgeOrder)
    }

    @Test
    fun 只记称呼建人物不写关系并落自定义称呼() = runTest {
        val me = createSelf()
        val specs = RelativePlan.buildSpecs(
            NEW_PERSON, me, RelationRole.SISTER, SiblingLineage.TITLE_ONLY
        )
        val aunt = repo.createRelative(draft("某人"), specs, customTitle = "姑姑").ok()
        // 无任何关系记录
        assertTrue(relsOf(aunt).isEmpty())
        // 观察者=“我”的自定义称呼已写入
        val titles = repo.observeCustomTitles().first()
        assertEquals("姑姑", titles[aunt])
        assertNotNull(db.personDao().getById(aunt))
    }

    @Test
    fun 多关系写入任一失败整体回滚() = runTest {
        val (me, dad, _) = familyOfThree()
        val specs = listOf(
            // 第一条合法：新人物是我的妹妹
            RelationRole.YOUNGER_SISTER.toRelationshipSpec(NEW_PERSON, me),
            // 第二条非法：不存在的人物做父亲
            RelationshipSpec("ghost", NEW_PERSON, RelationshipType.PARENT, null)
        )
        val result = repo.createRelative(draft("回滚妹"), specs)
        assertTrue(result is FamilyResult.Failure)
        // 人物与第一条关系都应回滚：人物表仍只有 3 人
        assertEquals(3, db.personDao().getAll().size)
        // 没有任何新增关系（我仍只有爸、妈两条）
        assertEquals(2, db.relationshipDao().getAll().size)
        // 幽灵 id 不应残留
        assertNull(db.personDao().getById("ghost"))
    }

    @Test
    fun addRelationships任一失败整体回滚() = runTest {
        val me = createSelf()
        val a = repo.createRelative(draft("甲", Gender.MALE), me, RelationRole.BROTHER).ok()
        val b = repo.createRelative(draft("乙", Gender.MALE), me, RelationRole.BROTHER).ok()
        val ok = RelationshipSpec(a, me, RelationshipType.PARENT, null)
        val bad = RelationshipSpec(b, "ghost", RelationshipType.PARENT, null)
        val result = repo.addRelationships(listOf(ok, bad))
        assertTrue(result is FamilyResult.Failure)
        // 第一条也未落库
        assertTrue(db.relationshipDao().getBetween(a, me)
            .none { it.type == RelationshipType.PARENT })
    }

    @Test
    fun 超长称呼被拒绝且人物回滚() = runTest {
        val me = createSelf()
        val specs = RelativePlan.buildSpecs(
            NEW_PERSON, me, RelationRole.SISTER, SiblingLineage.TITLE_ONLY
        )
        val result = repo.createRelative(draft("某人"), specs, "x".repeat(13))
        assertErr(result, FamilyError.InvalidCustomTitle)
        assertEquals(1, db.personDao().getAll().size)
    }

    @Test
    fun addRelationships同事务写自定义称呼() = runTest {
        val me = createSelf()
        val a = repo.createRelative(draft("甲", Gender.MALE), me, RelationRole.BROTHER).ok()
        val result = repo.addRelationships(
            emptyList(), customTitleFor = a, customTitle = "大表哥")
        assertTrue(result is FamilyResult.Success)
        assertEquals("大表哥", repo.observeCustomTitles().first()[a])
    }

    @Test
    fun addRelationships称呼校验失败时关系一并回滚() = runTest {
        val me = createSelf()
        // 两位待关联人物之间暂无关系，a→b PARENT 是合法规格；不能用同辈互当父子。
        val a = repo.createRelative(draft("甲", Gender.MALE), emptyList()).ok()
        val b = repo.createRelative(draft("乙", Gender.MALE), emptyList()).ok()
        val ok = RelationshipSpec(a, b, RelationshipType.PARENT, null)
        val result = repo.addRelationships(
            listOf(ok), customTitleFor = a, customTitle = "x".repeat(13))
        assertErr(result, FamilyError.InvalidCustomTitle)
        // 称呼非法：父母关系也不落库
        assertTrue(db.relationshipDao().getBetween(a, b)
            .none { it.type == RelationshipType.PARENT })
    }

    @Test
    fun addRelationships称呼目标是我则整体回滚() = runTest {
        val me = createSelf()
        val a = repo.createRelative(draft("甲", Gender.MALE), emptyList()).ok()
        val b = repo.createRelative(draft("乙", Gender.MALE), emptyList()).ok()
        val ok = RelationshipSpec(a, b, RelationshipType.PARENT, null)
        val result = repo.addRelationships(
            listOf(ok), customTitleFor = me, customTitle = "自己")
        assertErr(result, FamilyError.CustomTitleOnSelf)
        assertTrue(db.relationshipDao().getBetween(a, b)
            .none { it.type == RelationshipType.PARENT })
    }
}
