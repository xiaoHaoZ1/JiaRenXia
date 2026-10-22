package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 手机号规则（DEVELOPMENT_PLAN 2.8）。 */
class PhoneRulesTest : RepoTestBase() {

    private suspend fun phones(id: String) = repo.observePersonDetail(id).first()!!.phones

    @Test
    fun 同一草稿内格式不同的相同号码被拒绝() = runTest {
        assertErr(
            repo.createSelf(PersonDraft("我", Gender.MALE, phones = listOf(
                PhoneDraft(number = "138 0013-8000"),
                PhoneDraft(number = "13800138000")
            ))),
            FamilyError.DuplicatePhone
        )
    }

    @Test
    fun 多个主号码被拒绝() = runTest {
        assertErr(
            repo.createSelf(PersonDraft("我", Gender.MALE, phones = listOf(
                PhoneDraft(number = "111", isPrimary = true),
                PhoneDraft(number = "222", isPrimary = true)
            ))),
            FamilyError.MultiplePrimary
        )
    }

    @Test
    fun 停用号码自动取消主号码() = runTest {
        val id = createSelf()
        val p = repo.createRelative(PersonDraft("爸", Gender.MALE, phones = listOf(
            PhoneDraft(number = "111", status = PhoneStatus.INACTIVE, isPrimary = true)
        )), id, RelationRole.FATHER).ok()
        val ph = phones(p).single()
        assertEquals(PhoneStatus.INACTIVE, ph.status)
        assertFalse(ph.isPrimary)
    }

    @Test
    fun 表单顺序保留且不自动设主() = runTest {
        val me = createSelf()
        val p = repo.createRelative(PersonDraft("爸", Gender.MALE, phones = listOf(
            PhoneDraft(number = "333"),
            PhoneDraft(number = "111"),
            PhoneDraft(number = "222")
        )), me, RelationRole.FATHER).ok()
        val list = phones(p)
        assertEquals(listOf("333", "111", "222"), list.map { it.number })
        assertTrue(list.none { it.isPrimary })  // 不自动设主号码
    }

    @Test
    fun 更新时保留_删除_新增() = runTest {
        val me = createSelf()
        val p = repo.createRelative(PersonDraft("爸", Gender.MALE, phones = listOf(
            PhoneDraft(number = "111"),
            PhoneDraft(number = "222")
        )), me, RelationRole.FATHER).ok()
        val before = phones(p)
        val keep = before[0]
        val drop = before[1]
        // 草稿：保留 keep（改号码）、删掉 drop、新增一个
        repo.updatePerson(p, PersonDraft("爸", Gender.MALE, phones = listOf(
            PhoneDraft(id = keep.id, number = "999"),
            PhoneDraft(number = "888")
        ))).ok()
        val after = phones(p)
        assertEquals(2, after.size)
        assertEquals(keep.id, after[0].id)
        assertEquals("999", after[0].number)
        assertTrue(after.none { it.number == "222" })
        assertEquals("888", after[1].number)
        assertTrue(after[1].createdAt > keep.createdAt)  // 新号码按表单顺序赋 createdAt
    }

    @Test
    fun 号码对调不产生唯一约束冲突() = runTest {
        val me = createSelf()
        val p = repo.createRelative(PersonDraft("爸", Gender.MALE, phones = listOf(
            PhoneDraft(number = "111"),
            PhoneDraft(number = "222")
        )), me, RelationRole.FATHER).ok()
        val before = phones(p)
        val a = before.first { it.number == "111" }
        val b = before.first { it.number == "222" }
        // 两行号码对调
        val r = repo.updatePerson(p, PersonDraft("爸", Gender.MALE, phones = listOf(
            PhoneDraft(id = a.id, number = "222"),
            PhoneDraft(id = b.id, number = "111")
        )))
        assertTrue(r is FamilyResult.Success)
        val after = phones(p)
        assertEquals("222", after.first { it.id == a.id }.number)
        assertEquals("111", after.first { it.id == b.id }.number)
    }

    @Test
    fun 空号码被拒绝() = runTest {
        assertErr(
            repo.createSelf(PersonDraft("我", Gender.MALE, phones = listOf(PhoneDraft(number = "  ")))),
            FamilyError.EmptyPhoneNumber
        )
    }

    @Test
    fun 不同人物允许相同号码() = runTest {
        val me = createSelf()
        repo.createRelative(PersonDraft("爸", Gender.MALE, phones = listOf(PhoneDraft(number = "555"))),
            me, RelationRole.FATHER).ok()
        val r = repo.createRelative(PersonDraft("妈", Gender.FEMALE, phones = listOf(PhoneDraft(number = "555"))),
            me, RelationRole.MOTHER)
        assertTrue(r is FamilyResult.Success)
    }
}
