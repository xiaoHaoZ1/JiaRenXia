package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 忌日 / 结婚纪念日校验与“一对夫妻一条”规则（V0.8）。 */
class ImportantDatesTest : RepoTestBase() {

    private suspend fun me() = createSelf()
    private suspend fun spouseOf(name: String, meId: String, g: Gender) =
        repo.createRelative(PersonDraft(name, g), meId,
            if (g == Gender.FEMALE) RelationRole.WIFE else RelationRole.HUSBAND).ok()

    @Test
    fun 在世的人不能设忌日() = runTest {
        val m = me()
        val dad = repo.createRelative(
            PersonDraft("爸", Gender.MALE), m, RelationRole.FATHER).ok()
        assertErr(
            repo.setDeathAnniversary(
                dad, BirthdayValue(PartialDate(2020, 1, 1))),
            FamilyError.NotDeceased
        )
    }

    @Test
    fun 已故者可设忌日并可删除() = runTest {
        val m = me()
        val gp = repo.createRelative(
            PersonDraft("爷", Gender.MALE,
                livingStatus = LivingStatus.DECEASED),
            m, RelationRole.FATHER).ok()
        repo.setDeathAnniversary(
            gp, BirthdayValue(PartialDate(2020, 8, 25),
                CalendarType.LUNAR)).ok()
        val entries = repo.getDateEntries()
        assertEquals(1, entries.count {
            it.type == ImportantDateType.DEATH_ANNIVERSARY
        })
        repo.setDeathAnniversary(gp, null).ok()
        assertEquals(0, repo.getDateEntries().count {
            it.type == ImportantDateType.DEATH_ANNIVERSARY
        })
    }

    @Test
    fun 无配偶不能设结婚纪念日() = runTest {
        val m = me()
        assertErr(
            repo.setWeddingAnniversary(
                m, BirthdayValue(PartialDate(2015, 5, 20))),
            FamilyError.NoSpouse
        )
    }

    @Test
    fun 一对夫妻只有一条纪念日() = runTest {
        val m = me()
        val w = spouseOf("李雪", m, Gender.FEMALE)
        // 挂在我名下
        repo.setWeddingAnniversary(
            m, BirthdayValue(PartialDate(2015, 5, 20))).ok()
        var entries = repo.getDateEntries().filter {
            it.type == ImportantDateType.WEDDING_ANNIVERSARY
        }
        assertEquals(1, entries.size)
        assertEquals(m, entries[0].person.id)
        assertEquals("李雪", entries[0].partnerName)
        // 同一条纪念日，双方视角的“与谁”都要正确（真机回归：
        // 记在王秀兰名下时，她的页面此前错显自己名字）
        assertEquals("李雪", entries[0].partnerNameFor(m))
        assertEquals("我", entries[0].partnerNameFor(w))
        // 再从配偶那边设置 → 改同一条（仍只有我名下那一条）
        repo.setWeddingAnniversary(
            w, BirthdayValue(PartialDate(2015, 5, 21))).ok()
        entries = repo.getDateEntries().filter {
            it.type == ImportantDateType.WEDDING_ANNIVERSARY
        }
        assertEquals(1, entries.size)
        assertEquals(21, entries[0].date.date.day)
        // 删除从配偶侧也清掉
        repo.setWeddingAnniversary(w, null).ok()
        assertEquals(0, repo.getDateEntries().count {
            it.type == ImportantDateType.WEDDING_ANNIVERSARY
        })
    }

    @Test
    fun 默认提前天数_忌日与纪念日() = runTest {
        val m = me()
        val gp = repo.createRelative(
            PersonDraft("爷", Gender.MALE,
                livingStatus = LivingStatus.DECEASED),
            m, RelationRole.FATHER).ok()
        repo.setDeathAnniversary(
            gp, BirthdayValue(PartialDate(2020, 1, 1))).ok()
        val w = spouseOf("李雪", m, Gender.FEMALE)
        repo.setWeddingAnniversary(
            m, BirthdayValue(PartialDate(2015, 5, 20))).ok()
        val entries = repo.getDateEntries()
        assertEquals(setOf(3, 0), entries.first {
            it.type == ImportantDateType.DEATH_ANNIVERSARY
        }.reminderOffsets)
        assertEquals(setOf(1, 0), entries.first {
            it.type == ImportantDateType.WEDDING_ANNIVERSARY
        }.reminderOffsets)
    }

    @Test
    fun 按类型的提醒开关() = runTest {
        val m = me()
        val w = spouseOf("李雪", m, Gender.FEMALE)
        repo.setWeddingAnniversary(
            m, BirthdayValue(PartialDate(2015, 5, 20))).ok()
        repo.setReminderEnabled(
            m, false, ImportantDateType.WEDDING_ANNIVERSARY).ok()
        val e = repo.getDateEntries().first {
            it.type == ImportantDateType.WEDDING_ANNIVERSARY
        }
        assertEquals(false, e.reminderEnabled)
    }

    private val lunarCal =
        io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar()

    @Test
    fun 农历生日年龄换算() {
        // 农历 1964年正月初一 ≈ 公历 1964-02-13（ICU 需 Robolectric）
        val b = BirthdayValue(
            PartialDate(1964, 1, 1), CalendarType.LUNAR)
        val age = io.github.xiaohaoz1.jiarenxia.domain.AgeMath.ageAt(
            listOf(b), java.time.LocalDate.of(2026, 10, 1), lunarCal)
        assertEquals(62, age)
    }

    @Test
    fun 农历忌日通知_三天后() {
        val gp = io.github.xiaohaoz1.jiarenxia.core.model.Person(
            id = "gp", name = "杨福生",
            gender = Gender.MALE)
        val e = DateEntry(
            type = ImportantDateType.DEATH_ANNIVERSARY,
            person = gp,
            date = BirthdayValue(
                PartialDate(2016, 8, 25), CalendarType.LUNAR),
            reminderEnabled = true,
            reminderOffsets = setOf(3, 0))
        // 先算出 2025 年农历八月廿五的公历日，再取 3 天前
        val occ = lunarCal.toSolar(2025, 8, 25, false)!!
        val dues = io.github.xiaohaoz1.jiarenxia.reminder.ReminderPlanner
            .dueDateReminders(
                listOf(e), mapOf("gp" to "爷爷"),
                occ.minusDays(3), lunarCal)
        assertEquals(1, dues.size)
        val text = io.github.xiaohaoz1.jiarenxia.reminder.ReminderPlanner
            .text(dues[0], false)
        assertTrue(text.startsWith("3天后"))
        assertTrue(text.contains("爷爷杨福生的忌日"))
        assertTrue(text.contains("农历八月廿五"))
    }

    // ---------- 恋爱 / 领证纪念日（夫妻共享） ----------

    @Test
    fun 无配偶不能设恋爱领证纪念日() = runTest {
        val m = me()
        assertErr(
            repo.setCoupleAnniversary(
                m, ImportantDateType.DATING_ANNIVERSARY,
                BirthdayValue(PartialDate(2015, 5, 20))),
            FamilyError.NoSpouse
        )
        assertErr(
            repo.setCoupleAnniversary(
                m, ImportantDateType.LICENSE_ANNIVERSARY,
                BirthdayValue(PartialDate(2015, 5, 20))),
            FamilyError.NoSpouse
        )
    }

    @Test
    fun 非夫妻类型不能走共享纪念日接口() = runTest {
        val m = me()
        val w = spouseOf("李雪", m, Gender.FEMALE)
        assertErr(
            repo.setCoupleAnniversary(
                m, ImportantDateType.DEATH_ANNIVERSARY,
                BirthdayValue(PartialDate(2020, 1, 1))),
            FamilyError.InvalidDateType
        )
        assertErr(
            repo.setCoupleAnniversary(
                w, ImportantDateType.BIRTHDAY_SOLAR,
                BirthdayValue(PartialDate(1990, 1, 1))),
            FamilyError.InvalidDateType
        )
    }

    @Test
    fun 恋爱领证各一条_挂在任一方名下都算() = runTest {
        val m = me()
        val w = spouseOf("李雪", m, Gender.FEMALE)
        // 恋爱记在我名下，领记录在配偶名下
        repo.setCoupleAnniversary(
            m, ImportantDateType.DATING_ANNIVERSARY,
            BirthdayValue(PartialDate(2013, 7, 1))).ok()
        repo.setCoupleAnniversary(
            w, ImportantDateType.LICENSE_ANNIVERSARY,
            BirthdayValue(PartialDate(2015, 5, 20))).ok()
        var entries = repo.getDateEntries()
        val dating = entries.first {
            it.type == ImportantDateType.DATING_ANNIVERSARY
        }
        val license = entries.first {
            it.type == ImportantDateType.LICENSE_ANNIVERSARY
        }
        assertEquals(m, dating.person.id)
        assertEquals(w, license.person.id)
        // 双方视角“与谁”都正确
        assertEquals("李雪", dating.partnerNameFor(m))
        assertEquals("我", dating.partnerNameFor(w))
        assertEquals("我", license.partnerNameFor(w))
        assertEquals("李雪", license.partnerNameFor(m))
        // 从配偶侧改我名下的恋爱记录 → 仍是同一条
        repo.setCoupleAnniversary(
            w, ImportantDateType.DATING_ANNIVERSARY,
            BirthdayValue(PartialDate(2013, 7, 2))).ok()
        entries = repo.getDateEntries().filter {
            it.type == ImportantDateType.DATING_ANNIVERSARY
        }
        assertEquals(1, entries.size)
        assertEquals(2, entries[0].date.date.day)
        // 从我侧清掉配偶名下的领证记录 → 两边都没了
        repo.setCoupleAnniversary(
            m, ImportantDateType.LICENSE_ANNIVERSARY, null).ok()
        assertEquals(0, repo.getDateEntries().count {
            it.type == ImportantDateType.LICENSE_ANNIVERSARY
        })
    }

    @Test
    fun 三种共享纪念日互不干扰() = runTest {
        val m = me()
        spouseOf("李雪", m, Gender.FEMALE)
        repo.setCoupleAnniversary(
            m, ImportantDateType.DATING_ANNIVERSARY,
            BirthdayValue(PartialDate(2013, 7, 1))).ok()
        repo.setCoupleAnniversary(
            m, ImportantDateType.LICENSE_ANNIVERSARY,
            BirthdayValue(PartialDate(2015, 5, 20))).ok()
        repo.setCoupleAnniversary(
            m, ImportantDateType.WEDDING_ANNIVERSARY,
            BirthdayValue(PartialDate(2015, 10, 1))).ok()
        assertEquals(3, repo.getDateEntries().count { it.type.isCoupleShared })
        // 默认偏移与结婚一致（提前1天+当天）
        assertEquals(setOf(1, 0), repo.getDateEntries().first {
            it.type == ImportantDateType.DATING_ANNIVERSARY
        }.reminderOffsets)
        // 清恋爱不影响另外两条
        repo.setCoupleAnniversary(
            m, ImportantDateType.DATING_ANNIVERSARY, null).ok()
        val rest = repo.getDateEntries().filter { it.type.isCoupleShared }
        assertEquals(2, rest.size)
        assertTrue(rest.none { it.type == ImportantDateType.DATING_ANNIVERSARY })
    }

    // ---------- 共同父母记为夫妻（V0.9 修复） ----------

    @Test
    fun 共同父母_推荐并一键记为夫妻() = runTest {
        val m = me()
        val dad = repo.createRelative(
            PersonDraft("杨建国", Gender.MALE), m,
            RelationRole.FATHER).ok()
        val mom = repo.createRelative(
            PersonDraft("王秀兰", Gender.FEMALE), m,
            RelationRole.MOTHER).ok()
        // 双方都无配偶 → 推荐对方
        assertEquals(mom, repo.coParentCandidate(dad)?.id)
        assertEquals(dad, repo.coParentCandidate(mom)?.id)
        // “不是”分支：不操作 → 两人之间没有任何关系
        assertTrue(db.relationshipDao().getBetween(dad, mom).isEmpty())
        // “是”分支：同一事务记为夫妻 + 纪念日
        repo.linkSpouseAndSetWeddingAnniversary(
            dad, mom, BirthdayValue(PartialDate(1986, 10, 18))).ok()
        val rels = db.relationshipDao().getBetween(dad, mom)
        assertEquals(1, rels.count {
            it.type == io.github.xiaohaoz1.jiarenxia.core.model
                .RelationshipType.SPOUSE
        })
        // 规范存储：source < target
        assertTrue(rels.first {
            it.type == io.github.xiaohaoz1.jiarenxia.core.model
                .RelationshipType.SPOUSE
        }.let { it.sourcePersonId < it.targetPersonId })
        assertEquals(1, repo.getDateEntries().count {
            it.type == ImportantDateType.WEDDING_ANNIVERSARY
        })
        // 已有配偶后不再推荐
        assertNull(repo.coParentCandidate(dad))
    }

    @Test
    fun 多位异性共同父母_不推荐() = runTest {
        val m = me()
        val dad = repo.createRelative(
            PersonDraft("杨建国", Gender.MALE), m,
            RelationRole.FATHER).ok()
        val mom = repo.createRelative(
            PersonDraft("王秀兰", Gender.FEMALE), m,
            RelationRole.MOTHER).ok()
        // 孩子另有一位母亲记录（再婚数据）→ 爸有两位异性候选 → 不推荐
        db.personDao().insert(
            io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity(
                id = "x-mom", name = "另一位妈",
                gender = Gender.FEMALE, isSelf = false, note = null,
                livingStatus = io.github.xiaohaoz1.jiarenxia.core.model
                    .LivingStatus.ALIVE,
                avatarPath = null, createdAt = 1, updatedAt = 1))
        db.relationshipDao().insert(
            io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity(
                id = "extra-mom", sourcePersonId = "x-mom",
                targetPersonId = m,
                type = io.github.xiaohaoz1.jiarenxia.core.model
                    .RelationshipType.PARENT,
                relativeAgeOrder = null, createdAt = 1, updatedAt = 1))
        assertNull(repo.coParentCandidate(dad))
        // 妈这边候选仍唯一（另一位妈不是异性候选）
        assertEquals(dad, repo.coParentCandidate(mom)?.id)
    }

    @Test
    fun 对方已有配偶_不推荐() = runTest {
        val m = me()
        val dad = repo.createRelative(
            PersonDraft("杨建国", Gender.MALE), m,
            RelationRole.FATHER).ok()
        val mom = repo.createRelative(
            PersonDraft("王秀兰", Gender.FEMALE), m,
            RelationRole.MOTHER).ok()
        // 妈妈另有配偶 → 不推荐
        val other = repo.createRelative(
            PersonDraft("外人", Gender.MALE), emptyList()).ok()
        repo.addRelationship(mom, other, RelationRole.HUSBAND).ok()
        assertNull(repo.coParentCandidate(dad))
        assertNull(repo.coParentCandidate(mom))
    }

    @Test
    fun 删除配偶关系后纪念日条目随之更新() = runTest {
        val m = me()
        val w = spouseOf("妻", m, Gender.FEMALE)
        repo.setWeddingAnniversary(
            m, BirthdayValue(PartialDate(2018, 5, 20))).ok()
        // observeDateEntries 必须把关系表当真实依赖：删掉夫妻关系后，
        // 纪念日条目的 partner 应立即变 null，而不是等别的表变化才刷新
        val seen = CopyOnWriteArrayList<Boolean>()
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            repo.observeDateEntries().collect { l ->
                val e = l.firstOrNull {
                    it.type == ImportantDateType.WEDDING_ANNIVERSARY
                }
                seen.add(e != null && e.partnerId == w)
            }
        }
        try {
            val deadline = System.currentTimeMillis() + 10_000
            while (seen.lastOrNull() != true && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
            }
            assertEquals("未收到初始纪念日条目: $seen", true, seen.lastOrNull())
            val rel = db.relationshipDao().getAll()
                .single { it.type == RelationshipType.SPOUSE }
            repo.removeRelationship(rel.id).ok()
            val deadline2 = System.currentTimeMillis() + 10_000
            while (seen.lastOrNull() != false && System.currentTimeMillis() < deadline2) {
                Thread.sleep(25)
            }
            assertEquals("关系删除后 partner 未刷新: $seen", false, seen.lastOrNull())
        } finally {
            scope.cancel()
        }
    }
}
