package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonCrudTest : RepoTestBase() {

    @Test
    fun createSelf写入本人手机号与生日提醒() = runTest {
        val id = repo.createSelf(
            PersonDraft(
                name = "  我  ",
                gender = Gender.MALE,
                birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1990, 5, 5))),
                phones = listOf(PhoneDraft(number = "13800138000"))
            )
        ).ok()
        val self = repo.observeSelf().first()
        assertNotNull(self)
        assertEquals(id, self!!.id)
        assertTrue(self.isSelf)
        assertEquals("我", self.name)  // 姓名入库时去首尾空白
        val detail = repo.observePersonDetail(id).first()!!
        assertEquals(1, detail.phones.size)
        assertEquals(BirthdayValue(PartialDate(1990, 5, 5)), detail.birthday)
        assertNotNull(detail.reminder)
        assertTrue(detail.reminder!!.enabled)
        assertEquals(setOf(0, 1), detail.reminder!!.reminderOffsets)
    }

    @Test
    fun 第二个我被拒绝() = runTest {
        createSelf()
        assertErr(repo.createSelf(PersonDraft(name = "另一个我", gender = Gender.FEMALE)),
            FamilyError.SelfAlreadyExists)
    }

    @Test
    fun 空姓名被拒绝() = runTest {
        assertErr(repo.createSelf(PersonDraft(name = "   ", gender = Gender.MALE)),
            FamilyError.NameRequired)
    }

    @Test
    fun createRelative_父亲方向() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(
            PersonDraft("爸爸", Gender.MALE), me, RelationRole.FATHER
        ).ok()
        val rel = db.relationshipDao().getBetween(me, dad).single()
        assertEquals(dad, rel.sourcePersonId)
        assertEquals(me, rel.targetPersonId)
        assertEquals(RelationshipType.PARENT, rel.type)
        assertNull(rel.relativeAgeOrder)
    }

    @Test
    fun createRelative_儿子方向() = runTest {
        val me = createSelf()
        val son = repo.createRelative(PersonDraft("儿子", Gender.MALE), me, RelationRole.SON).ok()
        val rel = db.relationshipDao().getBetween(me, son).single()
        assertEquals(me, rel.sourcePersonId)
        assertEquals(son, rel.targetPersonId)
        assertEquals(RelationshipType.PARENT, rel.type)
    }

    @Test
    fun createRelative_妻子方向() = runTest {
        val me = createSelf()
        val wife = repo.createRelative(PersonDraft("爱人", Gender.FEMALE), me, RelationRole.WIFE).ok()
        val rel = db.relationshipDao().getBetween(me, wife).single()
        // 规范存储：SPOUSE/SIBLING source < target（“我”先建，id 更小）
        assertEquals(me, rel.sourcePersonId)
        assertEquals(wife, rel.targetPersonId)
        assertEquals(RelationshipType.SPOUSE, rel.type)
    }

    @Test
    fun createRelative_哥哥姐姐方向与长幼() = runTest {
        val me = createSelf()
        val bro = repo.createRelative(PersonDraft("哥", Gender.MALE), me, RelationRole.ELDER_BROTHER).ok()
        var rel = db.relationshipDao().getBetween(me, bro).single()
        // 规范化后 source < target；长幼随端点交换取反：
        // “哥长于我”存为 (我→哥, YOUNGER)
        assertEquals(me, rel.sourcePersonId)
        assertEquals(bro, rel.targetPersonId)
        assertEquals(RelationshipType.SIBLING, rel.type)
        assertEquals(RelativeAgeOrder.YOUNGER, rel.relativeAgeOrder)

        val sis = repo.createRelative(PersonDraft("妹", Gender.FEMALE), me, RelationRole.YOUNGER_SISTER).ok()
        rel = db.relationshipDao().getBetween(me, sis).single()
        // “妹幼于我”存为 (我→妹, OLDER)
        assertEquals(RelativeAgeOrder.OLDER, rel.relativeAgeOrder)
    }

    @Test
    fun 删除人物级联清理() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(
            PersonDraft("爸", Gender.MALE,
                birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1960, 1, 1))),
                phones = listOf(PhoneDraft(number = "111"))),
            me, RelationRole.FATHER
        ).ok()
        // 再补一条反向关系确认两端都清理
        val mom = repo.createRelative(PersonDraft("妈", Gender.FEMALE), me, RelationRole.MOTHER).ok()
        db.relationshipDao().insert(
            RelationshipEntity("x", dad, mom, RelationshipType.SPOUSE, null, 0, 0)
        )
        repo.deletePerson(dad).ok()
        assertEquals(0, db.phoneNumberDao().count())
        assertEquals(0, db.importantDateDao().count())
        assertEquals(0, db.reminderPreferenceDao().count())
        assertEquals(0, db.reminderOffsetDao().count())
        // dad 关联的两条关系都删除，mom-me 的保留
        val left = db.relationshipDao().getForPerson(me)
        assertEquals(1, left.size)
        assertEquals(mom, left[0].sourcePersonId)
    }

    @Test
    fun 删除我被拒绝() = runTest {
        val me = createSelf()
        assertErr(repo.deletePerson(me), FamilyError.CannotDeleteSelf)
    }

    @Test
    fun 不存在的人物() = runTest {
        assertErr(repo.updatePerson("ghost", PersonDraft("x", Gender.MALE)), FamilyError.PersonNotFound)
        assertErr(repo.deletePerson("ghost"), FamilyError.PersonNotFound)
        assertErr(repo.removeAvatar("ghost"), FamilyError.PersonNotFound)
        assertErr(repo.setReminderEnabled("ghost", true, ImportantDateType.BIRTHDAY_SOLAR), FamilyError.NoBirthday)
    }

    @Test
    fun 外键约束生效() = runTest {
        createSelf()
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking {
                db.relationshipDao().insert(
                    RelationshipEntity("bad", "me-missing", "other-missing", RelationshipType.PARENT, null, 0, 0)
                )
            }
        }
    }

    @Test
    fun clearAllData清空全部六张表() = runTest {
        val me = createSelf()
        repo.createRelative(
            PersonDraft("爸", Gender.MALE, birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1960, 1, 1))), phones = listOf(PhoneDraft(number = "1"))),
            me, RelationRole.FATHER
        )
        repo.clearAllData()
        assertEquals(0, db.personDao().count())
        assertEquals(0, db.phoneNumberDao().count())
        assertEquals(0, db.relationshipDao().count())
        assertEquals(0, db.importantDateDao().count())
        assertEquals(0, db.reminderPreferenceDao().count())
        assertEquals(0, db.reminderOffsetDao().count())
        assertNull(repo.observeSelf().first())
    }

    @Test
    fun 详情页返回该人两端全部关系() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        val detail = repo.observePersonDetail(dad).first()!!
        assertEquals(1, detail.relationships.size)
        assertEquals(RelationshipType.PARENT, detail.relationships[0].type)
        assertFalse(repo.observePersonDetail("ghost").first() != null)
    }
}
