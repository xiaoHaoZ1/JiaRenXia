package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupCrypto
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupError
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupJson
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
import io.github.xiaohaoz1.jiarenxia.reminder.ReminderPlanner
import io.github.xiaohaoz1.jiarenxia.reminder.UpcomingBirthdays
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 双生日（阳历 + 农历各一槽）专项规则：
 * 独立存取/删除、第二生日默认不额外开提醒、独立提醒开关、
 * 完整日期冲突时不猜、同日/同发送日通知合并、旧备份兼容。
 */
class DualBirthdayTest : RepoTestBase() {

    private val lunar = IcuLunarCalendar()

    /** 建一个有“我”的父亲，带双生日；返回 personId。 */
    private suspend fun dadWithBoth(
        solar: BirthdayValue = BirthdayValue(PartialDate(1960, 3, 15)),
        lunarB: BirthdayValue
    ): String {
        val me = createSelf()
        return repo.createRelative(
            PersonDraft(
                "爸", Gender.MALE,
                birthdays = PersonBirthdays(solar = solar, lunar = lunarB)
            ), me, RelationRole.FATHER
        ).ok()
    }

    /** 由公历日推一个与其一致的农历生日（年份齐全，可换算）。 */
    private fun lunarMatching(d: LocalDate): BirthdayValue {
        val ld = lunar.fromSolar(d)
        return BirthdayValue(
            PartialDate(ld.year, ld.month, ld.day),
            CalendarType.LUNAR, leapMonth = ld.leap
        )
    }

    // ---------- 存储：两槽独立 ----------

    @Test
    fun 一人双生日_两槽各自落库() = runTest {
        val dad = dadWithBoth(
            lunarB = BirthdayValue(PartialDate(null, 8, 15), CalendarType.LUNAR)
        )
        val detail = repo.observePersonDetail(dad).first()!!
        assertEquals(2, detail.birthdays.size)
        assertEquals(
            BirthdayValue(PartialDate(1960, 3, 15)),
            detail.birthdays[CalendarType.SOLAR]?.value
        )
        assertEquals(
            BirthdayValue(PartialDate(null, 8, 15), CalendarType.LUNAR),
            detail.birthdays[CalendarType.LUNAR]?.value
        )
        // DAO 层同样两行，类型各归各槽
        val rows = db.importantDateDao().getBirthdays(dad)
        assertEquals(
            setOf(ImportantDateType.BIRTHDAY_SOLAR, ImportantDateType.BIRTHDAY_LUNAR),
            rows.map { it.type }.toSet()
        )
    }

    @Test
    fun 第二生日默认不额外开提醒_原配置保留() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(
            PersonDraft(
                "爸", Gender.MALE,
                birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1960, 3, 15)))
            ), me, RelationRole.FATHER
        ).ok()
        // 阳历生日偏好调成非默认再关开关
        repo.setReminderOffsets(dad, setOf(0, 3), ImportantDateType.BIRTHDAY_SOLAR).ok()
        repo.setReminderEnabled(dad, false, ImportantDateType.BIRTHDAY_SOLAR).ok()
        // 补农历生日：默认关闭提醒，阳历偏好原样保留
        repo.setBirthday(
            dad, CalendarType.LUNAR,
            BirthdayValue(PartialDate(null, 8, 15), CalendarType.LUNAR)
        ).ok()
        val detail = repo.observePersonDetail(dad).first()!!
        val solar = detail.birthdays.getValue(CalendarType.SOLAR)
        val lunarD = detail.birthdays.getValue(CalendarType.LUNAR)
        val solarPref = solar.reminder!!
        assertFalse(solarPref.enabled)
        assertEquals(setOf(0, 3), solarPref.reminderOffsets)
        assertFalse(lunarD.reminder?.enabled ?: true)
    }

    @Test
    fun 槽位独立删除与修改() = runTest {
        val dad = dadWithBoth(
            lunarB = BirthdayValue(PartialDate(null, 8, 15), CalendarType.LUNAR)
        )
        val lunarId = db.importantDateDao()
            .getByPersonAndType(dad, ImportantDateType.BIRTHDAY_LUNAR.name)!!.id
        // 删阳历槽：农历槽及其偏好保留
        repo.setBirthday(dad, CalendarType.SOLAR, null).ok()
        var detail = repo.observePersonDetail(dad).first()!!
        assertNull(detail.birthdays[CalendarType.SOLAR])
        assertEquals(1, detail.birthdays.size)
        assertEquals(
            lunarId,
            db.importantDateDao()
                .getByPersonAndType(dad, ImportantDateType.BIRTHDAY_LUNAR.name)!!.id
        )
        // 改农历槽：原地更新保留行 id
        repo.setBirthday(
            dad, CalendarType.LUNAR,
            BirthdayValue(PartialDate(null, 9, 9), CalendarType.LUNAR)
        ).ok()
        detail = repo.observePersonDetail(dad).first()!!
        assertEquals(
            BirthdayValue(PartialDate(null, 9, 9), CalendarType.LUNAR),
            detail.birthdays.getValue(CalendarType.LUNAR).value
        )
        assertEquals(lunarId, detail.birthdays.getValue(CalendarType.LUNAR).dateId)
    }

    @Test
    fun 两槽提醒独立开关() = runTest {
        val dad = dadWithBoth(
            lunarB = BirthdayValue(PartialDate(null, 8, 15), CalendarType.LUNAR)
        )
        repo.setReminderEnabled(dad, true, ImportantDateType.BIRTHDAY_LUNAR).ok()
        var detail = repo.observePersonDetail(dad).first()!!
        assertTrue(detail.birthdays.getValue(CalendarType.SOLAR).reminder!!.enabled)
        assertTrue(detail.birthdays.getValue(CalendarType.LUNAR).reminder!!.enabled)
        // 只关农历，阳历不动
        repo.setReminderEnabled(dad, false, ImportantDateType.BIRTHDAY_LUNAR).ok()
        detail = repo.observePersonDetail(dad).first()!!
        assertTrue(detail.birthdays.getValue(CalendarType.SOLAR).reminder!!.enabled)
        assertFalse(detail.birthdays.getValue(CalendarType.LUNAR).reminder!!.enabled)
        // 没有农历生日时操作该槽位 → NoBirthday
        repo.setBirthday(dad, CalendarType.LUNAR, null).ok()
        assertErr(
            repo.setReminderEnabled(dad, true, ImportantDateType.BIRTHDAY_LUNAR),
            FamilyError.NoBirthday
        )
    }

    @Test
    fun setBirthday历法与取值不一致被拒() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        assertErr(
            repo.setBirthday(
                dad, CalendarType.LUNAR,
                BirthdayValue(PartialDate(1960, 3, 15), CalendarType.SOLAR)
            ),
            FamilyError.InvalidBirthday
        )
    }

    // ---------- 归并规则：冲突不猜 ----------

    @Test
    fun 完整双生日一致_归并出真实出生日() = runTest {
        // 公历 1960-03-15 对应农历 1960 二月十八（以 ICU 实算）
        val lunarB = lunarMatching(LocalDate.of(1960, 3, 15))
        val dad = dadWithBoth(lunarB = lunarB)
        val graph = repo.observeFamilyGraph().first()
        assertEquals(PartialDate(1960, 3, 15), graph.birthdays[dad])
    }

    @Test
    fun 完整双生日冲突_年龄长幼判未知() = runTest {
        // 农历 1960 正月初一换算公历绝非 1960-03-15 → 冲突
        val dad = dadWithBoth(
            lunarB = BirthdayValue(PartialDate(1960, 1, 1), CalendarType.LUNAR)
        )
        val r = BirthdayMath.resolveBirthDate(
            listOf(
                BirthdayValue(PartialDate(1960, 3, 15)),
                BirthdayValue(PartialDate(1960, 1, 1), CalendarType.LUNAR)
            ), lunar
        )
        assertTrue(r.conflict)
        assertNull(r.date)
        // 图谱层不猜：长幼比较拿不到出生日
        val graph = repo.observeFamilyGraph().first()
        assertNull(graph.birthdays[dad])
        // 展示层仍保留两份原始取值
        assertEquals(2, graph.birthdayValues.getValue(dad).size)
        // 年龄同样不猜
        assertNull(
            io.github.xiaohaoz1.jiarenxia.domain.AgeMath.ageAt(
                graph.birthdayValues.getValue(dad), LocalDate.of(2026, 10, 1), lunar
            )
        )
    }

    @Test
    fun 两份都无年份_保持未知不冲突() {
        val r = BirthdayMath.resolveBirthDate(
            listOf(
                BirthdayValue(PartialDate(null, 3, 15)),
                BirthdayValue(PartialDate(null, 8, 15), CalendarType.LUNAR)
            ), lunar
        )
        assertFalse(r.conflict)
        assertNull(r.date)
    }

    @Test
    fun 一份完整一份缺年_用完整的那份() {
        val r = BirthdayMath.resolveBirthDate(
            listOf(
                BirthdayValue(PartialDate(1960, 3, 15)),
                BirthdayValue(PartialDate(null, 8, 15), CalendarType.LUNAR)
            ), lunar
        )
        assertFalse(r.conflict)
        assertEquals(LocalDate.of(1960, 3, 15), r.date)
    }

    // ---------- 提醒合并 ----------

    private fun birthdayEntry(
        person: io.github.xiaohaoz1.jiarenxia.core.model.Person,
        v: BirthdayValue, offsets: Set<Int>
    ) = BirthdayEntry(person, v, reminderEnabled = true, reminderOffsets = offsets)

    @Test
    fun 同日双生日_通知合并一条() {
        val today = LocalDate.of(2026, 10, 1)
        // 阳历生日 10月5日；农历生日换算后也落 10月5日 → 同一天
        val ld = lunar.fromSolar(LocalDate.of(2026, 10, 5))
        val lunarB = BirthdayValue(
            PartialDate(null, ld.month, ld.day), CalendarType.LUNAR, ld.leap
        )
        val p = io.github.xiaohaoz1.jiarenxia.core.model.Person(
            id = "p", name = "爸", gender = Gender.MALE
        )
        val dues = ReminderPlanner.dueNotifications(
            listOf(
                birthdayEntry(p, BirthdayValue(PartialDate(null, 10, 5)), setOf(4)),
                birthdayEntry(p, lunarB, setOf(4))
            ),
            mapOf("p" to "父亲"), today, lunar
        )
        assertEquals(1, dues.size)
        assertEquals(2, dues[0].birthdayParts.size)
        assertEquals(
            "4天后（10月5日）是父亲爸的生日（阳历和农历同一天）",
            ReminderPlanner.text(dues[0], false)
        )
    }

    @Test
    fun 不同目标日同发送日_合并一条不丢内容() {
        val today = LocalDate.of(2026, 10, 1)
        // 农历生日今天到期（offsets {0}）；阳历生日 7 天后（offsets {7}）→ 同发送日
        val ld = lunar.fromSolar(today)
        val lunarB = BirthdayValue(
            PartialDate(null, ld.month, ld.day), CalendarType.LUNAR, ld.leap
        )
        val p = io.github.xiaohaoz1.jiarenxia.core.model.Person(
            id = "p", name = "爸", gender = Gender.MALE
        )
        val dues = ReminderPlanner.dueNotifications(
            listOf(
                birthdayEntry(p, BirthdayValue(PartialDate(null, 10, 8)), setOf(7)),
                birthdayEntry(p, lunarB, setOf(0))
            ),
            mapOf("p" to "父亲"), today, lunar
        )
        assertEquals(1, dues.size)
        assertEquals(2, dues[0].birthdayParts.size)
        val text = ReminderPlanner.text(dues[0], false)
        assertTrue(text, text.contains("今天是父亲爸的农历生日"))
        assertTrue(text, text.contains("7天后（10月8日）是阳历生日"))
    }

    @Test
    fun 关闭提醒的槽位不参与命中() {
        val today = LocalDate.of(2026, 10, 1)
        val p = io.github.xiaohaoz1.jiarenxia.core.model.Person(
            id = "p", name = "爸", gender = Gender.MALE
        )
        val dues = ReminderPlanner.dueNotifications(
            listOf(
                BirthdayEntry(
                    p, BirthdayValue(PartialDate(null, 10, 1)),
                    reminderEnabled = false, reminderOffsets = setOf(0)
                )
            ),
            mapOf("p" to "父亲"), today, lunar
        )
        assertTrue(dues.isEmpty())
    }

    @Test
    fun 时间线同日双生日合并一行() {
        val today = LocalDate.of(2026, 10, 1)
        val ld = lunar.fromSolar(LocalDate.of(2026, 10, 5))
        val lunarB = BirthdayValue(
            PartialDate(null, ld.month, ld.day), CalendarType.LUNAR, ld.leap
        )
        val p = io.github.xiaohaoz1.jiarenxia.core.model.Person(
            id = "p", name = "爸", gender = Gender.MALE
        )
        val rows = UpcomingBirthdays.build(
            listOf(
                birthdayEntry(p, BirthdayValue(PartialDate(null, 10, 5)), setOf(0)),
                birthdayEntry(p, lunarB, setOf(0))
            ),
            mapOf("p" to "父亲"), today, lunar
        )
        assertEquals(1, rows.upcoming.size)
        val row = rows.upcoming.single()
        assertTrue(row.merged)
        assertEquals(2, row.birthdays.size)
    }

    // ---------- 备份兼容（schemaVersion 9 写出新类型；旧 "BIRTHDAY" 可导入） ----------

    private fun zipOf(json: String): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        ZipOutputStream(buf).use { z ->
            z.putNextEntry(ZipEntry("backup.json"))
            z.write(json.toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
        return buf.toByteArray()
    }

    private fun backupJson(datesJson: String): String = """{
        "format": "jiarenxia-backup", "formatVersion": 1, "schemaVersion": 8,
        "createdAt": 1, "appVersion": "0.9.4", "purpose": "backup",
        "persons": [{"id":"p1","name":"爸","gender":"MALE","livingStatus":"ALIVE",
            "isSelf":false,"createdAt":1,"updatedAt":1}],
        "importantDates": $datesJson
    }"""

    @Test
    fun 旧备份BIRTHDAY按历法归入新槽位() {
        val plan = BackupJson.parsePlan(
            zipOf(
                backupJson(
                    """[{"id":"d1","personId":"p1","type":"BIRTHDAY","year":1960,
                        "month":3,"day":15,"calendarType":"SOLAR","createdAt":1,"updatedAt":1},
                       {"id":"d2","personId":"p1","type":"BIRTHDAY",
                        "month":8,"day":15,"calendarType":"LUNAR","createdAt":1,"updatedAt":1}]"""
                )
            )
        )
        val types = plan.importantDates.map { it.type }.toSet()
        assertEquals(
            setOf(ImportantDateType.BIRTHDAY_SOLAR, ImportantDateType.BIRTHDAY_LUNAR),
            types
        )
    }

    /** 期望 parsePlan 因 InvalidContent 失败，返回其 reason。 */
    private fun expectInvalidContent(json: String): String {
        try {
            BackupJson.parsePlan(zipOf(json))
            error("应当拒绝该备份内容")
        } catch (e: BackupCrypto.BackupFailure) {
            val err = e.error
            assertTrue("expected InvalidContent got $err", err is BackupError.InvalidContent)
            return (err as BackupError.InvalidContent).reason
        }
    }

    @Test
    fun 生日类型与历法错配被拒() {
        val reason = expectInvalidContent(
            backupJson(
                """[{"id":"d1","personId":"p1","type":"BIRTHDAY_LUNAR","year":1960,
                    "month":3,"day":15,"calendarType":"SOLAR","createdAt":1,"updatedAt":1}]"""
            )
        )
        assertTrue(reason, reason.contains("mismatch"))
    }

    @Test
    fun 同人同槽位重复被拒() {
        // 第二条为旧格式 BIRTHDAY+SOLAR，映射后与第一条撞同一槽位
        val reason = expectInvalidContent(
            backupJson(
                """[{"id":"d1","personId":"p1","type":"BIRTHDAY_SOLAR","year":1960,
                    "month":3,"day":15,"calendarType":"SOLAR","createdAt":1,"updatedAt":1},
                   {"id":"d2","personId":"p1","type":"BIRTHDAY","year":1961,
                    "month":4,"day":16,"calendarType":"SOLAR","createdAt":1,"updatedAt":1}]"""
            )
        )
        assertTrue(reason, reason.contains("duplicate"))
    }
}
