package io.github.xiaohaoz1.jiarenxia.domain.ask

import io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
import io.github.xiaohaoz1.jiarenxia.core.model.Medication
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 「问一问」拼问题：称呼/年龄分档/用药/孕期/新生儿，逐字断言。 */
class AskTextTest {

    private val today = LocalDate.of(2026, 11, 11)

    private fun med(
        name: String = "头孢克肟分散片",
        dose: String? = "1 片",
        spec: String? = null,
        purpose: String? = "支气管炎",
        usage: MedUsage = MedUsage.SHORT_COURSE,
        courseDays: Int? = null,
        start: LocalDate = LocalDate.of(2026, 10, 20)
    ) = Medication(
        id = "m1", personId = "p1", name = name, dose = dose, spec = spec,
        meal = null, usage = usage, courseDays = courseDays,
        startEpochDay = start.toEpochDay(), purpose = purpose, note = null,
        refillEpochDay = null, stoppedEpochDay = null,
        createdAt = 0L, updatedAt = 0L, times = emptyList()
    )

    // ---------- 称呼 ----------

    @Test
    fun 称呼_我是我() {
        assertEquals("我", AskText.label(isSelf = true, kinTitle = null))
        assertEquals("我", AskText.label(isSelf = true, kinTitle = "父亲"))
    }

    @Test
    fun 称呼_有称谓加我() {
        assertEquals("我母亲", AskText.label(false, "母亲"))
        assertEquals("我妻子", AskText.label(false, "妻子"))
    }

    @Test
    fun 称呼_无称谓() {
        assertEquals("我的一位家人", AskText.label(false, null))
        assertEquals("我的一位家人", AskText.label(false, "  "))
    }

    // ---------- 年龄 ----------

    @Test
    fun 年龄_无确切年份或未满一岁省略() {
        assertNull(AskText.age(null))
        assertNull(AskText.age(0))
    }

    @Test
    fun 年龄_一到九岁() {
        assertEquals("1 岁", AskText.age(1))
        assertEquals("9 岁", AskText.age(9))
    }

    @Test
    fun 年龄_十岁起约数() {
        assertEquals("10 多岁", AskText.age(10))
        assertEquals("10 多岁", AskText.age(19))
        assertEquals("60 多岁", AskText.age(60))
        assertEquals("60 多岁", AskText.age(62))
        assertEquals("60 多岁", AskText.age(69))
        assertEquals("70 多岁", AskText.age(70))
    }

    // ---------- 用药 ----------

    @Test
    fun 用药_短期待确认逐字() {
        // 规格例：label=我父亲 age=70 多岁，短期无天数（待确认）
        val text = AskText.forMedication(
            "我父亲", "70 多岁",
            med(usage = MedUsage.SHORT_COURSE, courseDays = null), today)
        assertEquals(
            "我父亲，70 多岁。记录的药是头孢克肟分散片，每次 1 片，" +
                "记录的用途是支气管炎，记录的开始日期是 10月20日，" +
                "疗程还没问清楚。这个药一般吃多久？能不能长期吃？",
            text)
    }

    @Test
    fun 用药_短期有天数进行中() {
        val text = AskText.forMedication(
            "我", null,
            med(courseDays = 7, start = LocalDate.of(2026, 11, 8)), today)
        // 今天 11-11，最后一天 11-14，未到期；无 age 段
        assertEquals(
            "我。记录的药是头孢克肟分散片，每次 1 片，" +
                "记录的用途是支气管炎，记录的开始日期是 11月8日，" +
                "记录的疗程是 7 天。这个药一般吃多久？能不能长期吃？",
            text)
    }

    @Test
    fun 用药_短期已到期() {
        val text = AskText.forMedication(
            "我的一位家人", "5 岁",
            med(courseDays = 7, start = LocalDate.of(2026, 10, 20)), today)
        assertEquals(
            "我的一位家人，5 岁。记录的药是头孢克肟分散片，每次 1 片，" +
                "记录的用途是支气管炎，记录的开始日期是 10月20日，" +
                "记录的 7 天疗程已到期。这个药一般吃多久？能不能长期吃？",
            text)
    }

    @Test
    fun 用药_长期与需要时吃() {
        val long = AskText.forMedication(
            "我母亲", "60 多岁",
            med(name = "二甲双胍", dose = null, spec = "500mg/片",
                purpose = null, usage = MedUsage.LONG_TERM), today)
        assertEquals(
            "我母亲，60 多岁。记录的药是二甲双胍，规格 500mg/片，" +
                "记录的开始日期是 10月20日，记录为长期吃。" +
                "这个药长期吃要注意什么？",
            long)
        val prn = AskText.forMedication(
            "我", null,
            med(name = "布洛芬", usage = MedUsage.AS_NEEDED,
                courseDays = null, purpose = null), today)
        assertEquals(
            "我。记录的药是布洛芬，每次 1 片，" +
                "记录的开始日期是 10月20日，记录为需要时吃。" +
                "这个药什么情况下吃？一天最多吃几次？",
            prn)
    }

    @Test
    fun 用药_不写真名() {
        // 带姓名的夹具：输出只含称谓与记录字段，不得出现真名
        val text = AskText.forMedication(
            "我父亲", "70 多岁",
            med(name = "头孢克肟分散片"), today)
        assertFalse(text.contains("杨建国"))
        assertFalse(text.contains("王秀兰"))
    }

    // ---------- 孕期 ----------

    @Test
    fun 孕期_孕周整周() {
        // 预产期 2026-12-20，今天 11-11 → 孕天数 280-(39)=241 → 34 周
        val due = LocalDate.of(2026, 12, 20)
        val text = AskText.forPregnancy("我妻子", due, today)
        assertEquals(
            "我妻子怀孕 34 周。这个阶段要注意什么？", text)
        // 我本人
        assertEquals(
            "我怀孕 34 周。这个阶段要注意什么？",
            AskText.forPregnancy("我", due, today))
    }

    // ---------- 新生宝宝 ----------

    @Test
    fun 新生儿_未满月写天数() {
        val birth = LocalDate.of(2026, 10, 24) // 出生第 19 天
        val text = AskText.forNewborn(birth, today)
        assertEquals(
            "我家宝宝出生 19 天。宝宝这个阶段要注意什么？" +
                "妈妈坐月子要注意什么？",
            text)
        // 出生当天 = 第 1 天
        assertEquals(
            "我家宝宝出生 1 天。宝宝这个阶段要注意什么？" +
                "妈妈坐月子要注意什么？",
            AskText.forNewborn(today, today))
    }

    @Test
    fun 新生儿_满月后写整月不带零头() {
        val birth = LocalDate.of(2026, 9, 15)
        // 11-11：满 1 个月零零头 → "1 个月"
        assertEquals(
            "我家宝宝 1 个月。宝宝这个阶段要注意什么？" +
                "妈妈坐月子要注意什么？",
            AskText.forNewborn(birth, today))
        // 满月当天即进入“月”写法
        assertEquals(
            "我家宝宝 1 个月。宝宝这个阶段要注意什么？" +
                "妈妈坐月子要注意什么？",
            AskText.forNewborn(LocalDate.of(2026, 10, 11), today))
        // 11-15 才满 2 个月（零头天数不进文案）
        assertEquals(
            "我家宝宝 1 个月。宝宝这个阶段要注意什么？" +
                "妈妈坐月子要注意什么？",
            AskText.forNewborn(birth, LocalDate.of(2026, 11, 14)))
        assertEquals(
            "我家宝宝 2 个月。宝宝这个阶段要注意什么？" +
                "妈妈坐月子要注意什么？",
            AskText.forNewborn(birth, LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun 不含其他家人姓名与隐私字段() {
        // 称谓路径产物里不允许出现真名（PersonOverview 只贡献称谓）
        val label = AskText.label(false, "母亲")
        assertFalse(label.contains("李雪"))
        assertFalse(label.contains("139"))
        // 新生儿固定文案不含宝宝真名
        assertFalse(
            AskText.forNewborn(LocalDate.of(2026, 10, 24), today)
                .contains("小宝"))
    }
}
