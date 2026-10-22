package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 关系写入校验（DEVELOPMENT_PLAN 2.3 六）。 */
class RelationshipRulesTest : RepoTestBase() {

    @Test
    fun 母亲的丈夫不能再记为女儿的儿子() = runTest {
        val me = createSelf("陈志远")
        val sister = repo.createRelative(PersonDraft("陈静怡", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val girl = repo.createRelative(PersonDraft("许思宁", Gender.FEMALE), sister, RelationRole.DAUGHTER).ok()
        val husband = repo.createRelative(PersonDraft("许文成", Gender.MALE), sister, RelationRole.HUSBAND).ok()
        val before = db.relationshipDao().getAll()
        org.junit.Assert.assertTrue(repo.addRelationship(girl, husband, RelationRole.SON) is FamilyResult.Failure)
        assertEquals(before, db.relationshipDao().getAll())
        repo.addRelationship(girl, husband, RelationRole.FATHER).ok()
    }

    @Test
    fun 反向重复被拒绝() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        // dad 已有 dad PARENT me；再让 me 当 dad 的“儿子” → 反向重复
        assertErr(repo.addRelationship(dad, me, RelationRole.SON), FamilyError.DuplicatePair)
        // 同方向重复同样拒绝
        assertErr(repo.addRelationship(me, dad, RelationRole.FATHER), FamilyError.DuplicatePair)
    }

    @Test
    fun 第三位父母被拒绝() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        repo.createRelative(PersonDraft("妈", Gender.FEMALE), me, RelationRole.MOTHER).ok()
        // 第三个人物先挂到别处
        val other = repo.createRelative(PersonDraft("某人", Gender.MALE), dad, RelationRole.SON).ok()
        assertErr(repo.addRelationship(me, other, RelationRole.FATHER), FamilyError.TooManyParents)
    }

    @Test
    fun 第二位配偶从两端都被拒绝() = runTest {
        val me = createSelf()
        val wife = repo.createRelative(PersonDraft("妻", Gender.FEMALE), me, RelationRole.WIFE).ok()
        val dad = repo.createRelative(PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        // 与待校验端无既有关系的第三/第四人（否则先命中 DuplicatePair）
        val other1 = repo.createRelative(PersonDraft("A", Gender.FEMALE), dad, RelationRole.SISTER).ok()
        val other2 = repo.createRelative(PersonDraft("B", Gender.FEMALE), dad, RelationRole.SISTER).ok()
        // 我已有一位配偶
        assertErr(repo.addRelationship(me, other1, RelationRole.WIFE), FamilyError.SpouseAlreadyExists)
        // 妻子已有一位配偶（从另一端检查）
        assertErr(repo.addRelationship(wife, other2, RelationRole.WIFE), FamilyError.SpouseAlreadyExists)
    }

    @Test
    fun 祖先循环被拒绝() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        val gp = repo.createRelative(PersonDraft("爷", Gender.MALE), dad, RelationRole.FATHER).ok()
        // 让爷爷做我的儿子 → 爷爷已是我的祖先
        assertErr(repo.addRelationship(me, gp, RelationRole.SON), FamilyError.AncestorCycle)
    }

    @Test
    fun 自关系与缺失人物被拒绝() = runTest {
        val me = createSelf()
        assertErr(repo.addRelationship(me, me, RelationRole.BROTHER), FamilyError.SelfRelation)
        assertErr(repo.addRelationship(me, "ghost", RelationRole.BROTHER), FamilyError.PersonNotFound)
    }

    @Test
    fun 长幼设置方向换算() = runTest {
        val me = createSelf()
        val bro = repo.createRelative(PersonDraft("兄弟", Gender.MALE), me, RelationRole.BROTHER).ok()
        val rel = db.relationshipDao().getBetween(me, bro).single()
        assertNull(rel.relativeAgeOrder)

        // 规范化后 source=我(id 小)、target=兄弟：subject 是 target → 取反存
        repo.setSiblingAgeOrder(rel.id, bro, RelativeAgeOrder.OLDER).ok()
        assertEquals(RelativeAgeOrder.YOUNGER, db.relationshipDao().getById(rel.id)!!.relativeAgeOrder)

        // subject 是 source：原样存
        repo.setSiblingAgeOrder(rel.id, me, RelativeAgeOrder.OLDER).ok()
        assertEquals(RelativeAgeOrder.OLDER, db.relationshipDao().getById(rel.id)!!.relativeAgeOrder)

        // null 清除
        repo.setSiblingAgeOrder(rel.id, bro, null).ok()
        assertNull(db.relationshipDao().getById(rel.id)!!.relativeAgeOrder)
    }

    @Test
    fun 非兄弟关系不能设长幼() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        val rel = db.relationshipDao().getBetween(me, dad).single()
        assertErr(repo.setSiblingAgeOrder(rel.id, dad, RelativeAgeOrder.OLDER), FamilyError.NotSibling)
        assertErr(repo.setSiblingAgeOrder("missing-rel", dad, RelativeAgeOrder.OLDER), FamilyError.RelationshipNotFound)
    }
}
