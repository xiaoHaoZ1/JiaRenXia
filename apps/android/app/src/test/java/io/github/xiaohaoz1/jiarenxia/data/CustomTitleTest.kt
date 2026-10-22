package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.domain.PersonSearch
import io.github.xiaohaoz1.jiarenxia.domain.pinyin.PinyinIndex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自定义称呼（DATABASE_SCHEMA 25.2）：仓库校验、覆盖显示、搜索命中。 */
class CustomTitleTest : RepoTestBase() {

    private suspend fun fixture(): Pair<String, String> {
        val me = createSelf()
        val uncle = repo.createRelative(
            PersonDraft("杨建春", Gender.MALE), me, RelationRole.BROTHER
        ).ok()
        return me to uncle
    }

    @Test
    fun 设置与读取() = runTest {
        val (_, uncle) = fixture()
        repo.setCustomTitle(uncle, "大伯").ok()
        assertEquals(mapOf(uncle to "大伯"), repo.observeCustomTitles().first())
    }

    @Test
    fun 空白与null删除() = runTest {
        val (_, uncle) = fixture()
        repo.setCustomTitle(uncle, "大伯").ok()
        repo.setCustomTitle(uncle, null).ok()
        assertEquals(emptyMap<String, String>(), repo.observeCustomTitles().first())
        repo.setCustomTitle(uncle, "大伯").ok()
        repo.setCustomTitle(uncle, "   ").ok()
        assertEquals(emptyMap<String, String>(), repo.observeCustomTitles().first())
    }

    @Test
    fun 首尾空白被去除() = runTest {
        val (_, uncle) = fixture()
        repo.setCustomTitle(uncle, "  大伯  ").ok()
        assertEquals("大伯", repo.observeCustomTitles().first()[uncle])
    }

    @Test
    fun 超长被拒() = runTest {
        val (_, uncle) = fixture()
        // 16 字（>12）被拒
        assertErr(repo.setCustomTitle(uncle, "一二三四五六七八九十十一十二十三十四"),
            FamilyError.InvalidCustomTitle)
        // 恰好 12 字合法
        repo.setCustomTitle(uncle, "一二三四五六七八九十甲乙").ok()   // 恰好 12 字
    }

    @Test
    fun 对自己与非存在目标被拒() = runTest {
        val (me, uncle) = fixture()
        assertErr(repo.setCustomTitle(me, "自"), FamilyError.CustomTitleOnSelf)
        assertErr(repo.setCustomTitle("ghost", "x"), FamilyError.PersonNotFound)
        assertEquals(emptyMap<String, String>(), repo.observeCustomTitles().first())
        assertTrue(repo.observePersonDetail(uncle).first() != null)
    }

    @Test
    fun 更新同一目标覆盖旧值() = runTest {
        val (_, uncle) = fixture()
        repo.setCustomTitle(uncle, "大伯").ok()
        repo.setCustomTitle(uncle, "老舅").ok()
        assertEquals(mapOf(uncle to "老舅"), repo.observeCustomTitles().first())
    }

    @Test
    fun 删除人物级联删除称呼() = runTest {
        val (_, uncle) = fixture()
        repo.setCustomTitle(uncle, "大伯").ok()
        repo.deletePerson(uncle).ok()
        assertEquals(emptyMap<String, String>(), repo.observeCustomTitles().first())
    }

    @Test
    fun 清空全部数据连称呼一起删() = runTest {
        val (_, uncle) = fixture()
        repo.setCustomTitle(uncle, "大伯").ok()
        repo.clearAllData()
        assertEquals(emptyMap<String, String>(), repo.observeCustomTitles().first())
    }
}

/** 称谓覆盖显示（自定义 > 标准 > 结构）与拼音/中文搜索。 */
class CustomTitleOverlayTest : RepoTestBase() {

    @Test
    fun displayTitle优先自定义称呼() = runTest {
        val me = createSelf()
        val uncle = repo.createRelative(
            PersonDraft("杨建春", Gender.MALE), me, RelationRole.ELDER_BROTHER
        ).ok()
        // 未设置：标准称谓
        var ov = repo.observePersonDetail(uncle).first()!!
        assertNull(ov.reminder)
        repo.setCustomTitle(uncle, "大伯").ok()
        val titles = repo.observeCustomTitles().first()
        // overview 聚合层覆盖显示
        val o = io.github.xiaohaoz1.jiarenxia.domain.PersonOverview(
            person = ov.person,
            result = io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine()
                .resolve(
                    io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph(
                        listOf(repo.observePersonDetail(me).first()!!.person, ov.person),
                        repo.observePersonDetail(uncle).first()!!.relationships +
                            repo.observePersonDetail(me).first()!!.relationships
                    ),
                    me, uncle
                ),
            birthdays = listOfNotNull(ov.birthday),
            customTitle = titles[uncle]
        )
        assertEquals("大伯", o.displayTitle)
        assertEquals("哥哥", o.result.standardTitle)
    }

    @Test
    fun 搜索命中自定义称呼与其拼音() = runTest {
        val me = createSelf()
        val uncle = repo.createRelative(
            PersonDraft("杨建春", Gender.MALE), me, RelationRole.ELDER_BROTHER
        ).ok()
        repo.setCustomTitle(uncle, "大伯").ok()
        val titles = repo.observeCustomTitles().first()
        val person = repo.observePersonDetail(uncle).first()!!.person
        val engine = io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine()
        val graph = io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph(
            persons = listOf(
                repo.observePersonDetail(me).first()!!.person, person
            ),
            relationships = repo.observePersonDetail(uncle).first()!!.relationships +
                repo.observePersonDetail(me).first()!!.relationships
        )
        val o = io.github.xiaohaoz1.jiarenxia.domain.PersonOverview(
            person = person, result = engine.resolve(graph, me, uncle),
            birthdays = emptyList(), customTitle = titles[uncle]
        )
        assertEquals(listOf("杨建春"), PersonSearch.filter(listOf(o), "大伯").map { it.person.name })
        assertEquals(listOf("杨建春"), PersonSearch.filter(listOf(o), "dabo").map { it.person.name })
    }

    @Test
    fun 通知文案用自定义称呼() = runTest {
        // worker 里 titles map 由 o.displayTitle 构建 → 覆盖后的称谓直接进通知文案
        val due = io.github.xiaohaoz1.jiarenxia.reminder.DueReminder(
            "u", "杨建春", "大伯", 0, java.time.LocalDate.of(2026, 10, 5)
        )
        assertEquals("今天是大伯杨建春的生日",
            io.github.xiaohaoz1.jiarenxia.reminder.ReminderPlanner.text(due, false))
    }
}
