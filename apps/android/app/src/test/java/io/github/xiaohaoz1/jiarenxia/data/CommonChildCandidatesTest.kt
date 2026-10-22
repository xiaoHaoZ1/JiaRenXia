package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结为夫妻后的“共同子女”候选：一方已录子女中，
 * 已录父母没有另一方、也没有与另一方同性别父母的人才会被问到。
 */
class CommonChildCandidatesTest : RepoTestBase() {

    private fun draft(name: String, gender: Gender) = PersonDraft(name, gender)

    @Test
    fun 一方有女儿时列为候选_两边查询结果一致() = runTest {
        val me = createSelf()
        val mom = repo.createRelative(
            draft("王蓓蓓", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val dau = repo.createRelative(
            draft("安安", Gender.FEMALE), mom, RelationRole.DAUGHTER).ok()
        val yang = repo.createRelative(
            draft("杨超", Gender.MALE), me, RelationRole.BROTHER).ok()

        val list = repo.commonChildCandidates(yang, mom)
        assertEquals(1, list.size)
        assertEquals(dau, list[0].child.id)
        assertEquals(mom, list[0].existingParentId)
        assertEquals("女儿", list[0].childLabel)
        // 参数顺序不影响结果（两边都查）
        assertEquals(list, repo.commonChildCandidates(mom, yang))
    }

    @Test
    fun 孩子已有同性别父母时不问() = runTest {
        val me = createSelf()
        val mom = repo.createRelative(
            draft("王蓓蓓", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val dau = repo.createRelative(
            draft("安安", Gender.FEMALE), mom, RelationRole.DAUGHTER).ok()
        // 安安已录有父亲：新丈夫（男）不再问
        repo.createRelative(draft("前夫", Gender.MALE), dau, RelationRole.FATHER)
        val yang = repo.createRelative(
            draft("杨超", Gender.MALE), me, RelationRole.BROTHER).ok()
        assertTrue(repo.commonChildCandidates(yang, mom).isEmpty())
    }

    @Test
    fun 双方各有子女时两边都列出() = runTest {
        val me = createSelf()
        val a = repo.createRelative(
            draft("老张", Gender.MALE), me, RelationRole.BROTHER).ok()
        val b = repo.createRelative(
            draft("阿珍", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val son = repo.createRelative(
            draft("小军", Gender.MALE), a, RelationRole.SON).ok()
        val dau = repo.createRelative(
            draft("小芳", Gender.FEMALE), b, RelationRole.DAUGHTER).ok()

        val list = repo.commonChildCandidates(a, b)
        // a 侧子女在前
        assertEquals(listOf(son, dau), list.map { it.child.id })
        assertEquals(listOf(a, b), list.map { it.existingParentId })
        assertEquals(listOf("儿子", "女儿"), list.map { it.childLabel })
    }

    @Test
    fun 已是共同子女不再问() = runTest {
        val me = createSelf()
        val a = repo.createRelative(
            draft("老张", Gender.MALE), me, RelationRole.BROTHER).ok()
        val b = repo.createRelative(
            draft("阿珍", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val son = repo.createRelative(
            draft("小军", Gender.MALE), a, RelationRole.SON).ok()
        // 小军也记了阿珍为母亲 → 已是共同子女
        repo.addRelationship(son, b, RelationRole.MOTHER)
        assertTrue(repo.commonChildCandidates(a, b).isEmpty())
    }
}
