package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.FactCategory
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.data.db.PersonFactEntity
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 「关于TA」仓库的规范化、事务与兼容行为。 */
class PersonFactRepositoryTest : RepoTestBase() {
    private var factSeq = 0
    private lateinit var facts: PersonFactRepositoryImpl

    @Before
    fun setUpFacts() {
        facts = PersonFactRepositoryImpl(
            db,
            object : IdGenerator { override fun newId() = "fact-" + (++factSeq) },
            object : TimeSource { override fun now() = now }
        )
    }

    @Test
    fun add规范化内容日期并丢弃普通分类label() = runTest {
        val person = createSelf()
        val content = "  " + "甲".repeat(45) + "  "
        facts.add(person, FactCategory.FOOD_LIKE, "不应保存", content).ok()

        val row = facts.observeForPerson(person).first().single()
        assertEquals("甲".repeat(40), row.content)
        assertNull(row.label)
        assertEquals(
            Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate(),
            row.recordedOn
        )
    }

    @Test
    fun add校验名称内容与人物() = runTest {
        val person = createSelf()
        assertErr(
            facts.add(person, FactCategory.SIZE, " ", "37"),
            FamilyError.FactLabelRequired
        )
        assertErr(
            facts.add(person, FactCategory.CUSTOM, null, "水果店"),
            FamilyError.FactLabelRequired
        )
        assertErr(
            facts.add(person, FactCategory.HOBBY, null, "  "),
            FamilyError.FactContentRequired
        )
        assertErr(
            facts.add("missing", FactCategory.HOBBY, null, "广场舞"),
            FamilyError.PersonNotFound
        )
    }

    @Test
    fun update更新内容日期时间_相同值不写库_不存在报错() = runTest {
        val person = createSelf()
        facts.add(person, FactCategory.SIZE, "鞋码", "37").ok()
        val old = db.personFactDao().getAll().single()
        now += 86_400_000L
        facts.update(old.id, "鞋码", "38").ok()
        val changed = db.personFactDao().getById(old.id)!!
        assertEquals("38", changed.content)
        assertEquals(old.recordedEpochDay + 1, changed.recordedEpochDay)
        assertEquals(now, changed.updatedAt)

        now += 1_000L
        facts.update(old.id, "鞋码", "38").ok()
        assertEquals(changed.updatedAt, db.personFactDao().getById(old.id)!!.updatedAt)
        assertErr(facts.update("missing", null, "值"), FamilyError.FactNotFound)
    }

    @Test
    fun delete与删人物级联() = runTest {
        val me = createSelf()
        val person = repo.createRelative(
            PersonDraft("妈妈", Gender.FEMALE), me, RelationRole.MOTHER
        ).ok()
        facts.add(person, FactCategory.HOBBY, null, "戏曲").ok()
        val id = db.personFactDao().getAll().single().id
        facts.delete(id)
        assertTrue(db.personFactDao().getAll().isEmpty())

        facts.add(person, FactCategory.HOBBY, null, "戏曲").ok()
        repo.deletePerson(person)
        assertTrue(db.personFactDao().getAll().isEmpty())
    }

    @Test
    fun observe只返回本人并按创建时间升序() = runTest {
        val me = createSelf()
        val other = repo.createRelative(
            PersonDraft("妈妈", Gender.FEMALE), me, RelationRole.MOTHER
        ).ok()
        facts.add(me, FactCategory.HOBBY, null, "第一条").ok()
        now += 1
        facts.add(other, FactCategory.HOBBY, null, "别人").ok()
        now += 1
        facts.add(me, FactCategory.HOBBY, null, "第二条").ok()
        assertEquals(
            listOf("第一条", "第二条"),
            facts.observeForPerson(me).first().map { it.content }
        )
    }

    @Test
    fun 未知分类按其他自定义显示() = runTest {
        val person = createSelf()
        db.personFactDao().insert(
            PersonFactEntity("future", person, "FUTURE_X", null, "未来值", 1, 1, 1)
        )
        val model = facts.observeForPerson(person).first().single()
        assertEquals(FactCategory.CUSTOM, model.category)
        assertEquals("其他", model.label)
    }
}
