package io.github.xiaohaoz1.jiarenxia.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 分享文本 → 家事预填：标题截断、备注保留、日期说法识别（today 固定为周三）。 */
class SharedTaskTextTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 14) // 周三

    @Test
    fun `空白与空文本返回 null`() {
        assertNull(SharedTaskText.parse("", today))
        assertNull(SharedTaskText.parse("   \n  ", today))
    }

    @Test
    fun `短文本全部进标题且无备注`() {
        val p = SharedTaskText.parse("取快递", today)!!
        assertEquals("取快递", p.title)
        assertNull(p.note)
        assertEquals(today, p.anchorDate)
    }

    @Test
    fun `语首话术与日期不进标题`() {
        assertEquals("买酱油", SharedTaskText.parse("记得买酱油", today)!!.title)
        assertEquals("买牛奶", SharedTaskText.parse("记得提醒我明天买牛奶", today)!!.title)
        assertEquals("交材料", SharedTaskText.parse("明早记得提醒我交材料", today)!!.title)
        assertEquals("交物业费", SharedTaskText.parse("10月20号交物业费", today)!!.title)
        assertEquals("取快递", SharedTaskText.parse("帮我取快递", today)!!.title)
        assertEquals("一起吃饭", SharedTaskText.parse("晚上一起吃饭", today)!!.title)
        // 剥完只剩话术时退回原文，不留空标题；语中的「记得」不动
        assertEquals("记得提醒我", SharedTaskText.parse("记得提醒我", today)!!.title)
        assertEquals("媳妇说记得带钥匙", SharedTaskText.parse("媳妇说记得带钥匙", today)!!.title)
    }

    @Test
    fun `语尾时间与日期补充不进标题`() {
        val p = SharedTaskText.parse("后天提醒我挂号。早上7点。", today)!!
        assertEquals("挂号", p.title)
        assertEquals(today.plusDays(2), p.anchorDate)
        assertEquals(7 * 60, p.remindMinutes)
        // 语尾日期/时刻补充可连续多段，时刻仍照常识别
        val q = SharedTaskText.parse("买牛奶，周五，下午3点", today)!!
        assertEquals("买牛奶", q.title)
        assertEquals(today.plusDays(2), q.anchorDate)
        assertEquals(15 * 60, q.remindMinutes)
        assertEquals("倒垃圾", SharedTaskText.parse("倒垃圾 晚上", today)!!.title)
        // 语尾话术属正文不剥
        assertEquals("我不记得", SharedTaskText.parse("我不记得", today)!!.title)
    }

    @Test
    fun `今晚与礼拜说法`() {
        assertEquals(today, SharedTaskText.parse("今晚上一起看电影", today)!!.anchorDate)
        assertEquals(today.plusDays(2), SharedTaskText.parse("礼拜五接孩子", today)!!.anchorDate)
        assertEquals(today.plusDays(7), SharedTaskText.parse("下礼拜三聚餐", today)!!.anchorDate)
    }

    @Test
    fun `长文本标题截断 30 字且全文进备注`() {
        val raw = "这是一条特别长的要记下来的家事内容超过三十个字以后就会被截断标题"
        val p = SharedTaskText.parse(raw, today)!!
        assertEquals(SharedTaskText.TITLE_MAX, p.title.length)
        assertEquals(raw, p.note)
    }

    @Test
    fun `多行文本换行折叠为空格且原文进备注`() {
        val p = SharedTaskText.parse("媳妇说要记的\n第一行\n第二行", today)!!
        assertEquals("媳妇说要记的 第一行 第二行", p.title)
        assertNotNull(p.note)
    }

    @Test
    fun `明天后天大后天`() {
        assertEquals(today.plusDays(1), SharedTaskText.parse("明天记得买酱油", today)!!.anchorDate)
        assertEquals(today.plusDays(1), SharedTaskText.parse("明早去拿快递", today)!!.anchorDate)
        assertEquals(today.plusDays(2), SharedTaskText.parse("后天交材料", today)!!.anchorDate)
        assertEquals(today.plusDays(3), SharedTaskText.parse("大后天去接娃", today)!!.anchorDate)
    }

    @Test
    fun `周X 为本周最近的对应星期`() {
        // 今天周三：周五 = +2，周三 = 当天，周一 = +5（本周已过→下周一前的周一即下周？不——本周一已过，算下周一）
        assertEquals(today.plusDays(2), SharedTaskText.parse("周五接孩子", today)!!.anchorDate)
        assertEquals(today, SharedTaskText.parse("周三去医院复查", today)!!.anchorDate)
        assertEquals(today.plusDays(5), SharedTaskText.parse("周一要洗车", today)!!.anchorDate)
        assertEquals(today.plusDays(4), SharedTaskText.parse("星期日回爸妈家", today)!!.anchorDate)
        assertEquals(today.plusDays(2), SharedTaskText.parse("这个周五接孩子", today)!!.anchorDate)
    }

    @Test
    fun `下周X 落在下周`() {
        // 今天周三，下周三 = +7；下周一 = +5
        assertEquals(today.plusDays(7), SharedTaskText.parse("下周三部门聚餐", today)!!.anchorDate)
        assertEquals(today.plusDays(5), SharedTaskText.parse("下周一开学", today)!!.anchorDate)
        assertEquals(today.plusDays(11), SharedTaskText.parse("下星期天去看望姥姥", today)!!.anchorDate)
        assertEquals(today.plusDays(7), SharedTaskText.parse("下个周三聚餐", today)!!.anchorDate)
    }

    @Test
    fun `X月X日今年未过用今年已过顺延明年`() {
        assertEquals(
            LocalDate.of(2026, 10, 20),
            SharedTaskText.parse("10月20号交物业费", today)!!.anchorDate
        )
        assertEquals(
            LocalDate.of(2027, 9, 1),
            SharedTaskText.parse("9月1日开学的材料要准备", today)!!.anchorDate
        )
        assertEquals(
            LocalDate.of(2027, 3, 8),
            SharedTaskText.parse("明年3月8号领证纪念日", today)!!.anchorDate
        )
    }

    @Test
    fun `下月X号`() {
        assertEquals(
            LocalDate.of(2026, 11, 5),
            SharedTaskText.parse("下月5号还信用卡", today)!!.anchorDate
        )
    }

    @Test
    fun `相对日期优先于星期与具体日期`() {
        assertEquals(today.plusDays(1), SharedTaskText.parse("明天周五之前交给我", today)!!.anchorDate)
    }

    @Test
    fun `量词与重复说法不误判`() {
        // 「每周三」是重复节奏不是具体日期；「一/两周三次」是次数
        assertEquals(today, SharedTaskText.parse("每周三晚上都要倒垃圾", today)!!.anchorDate)
        assertEquals(today, SharedTaskText.parse("一周三次吃药别忘了", today)!!.anchorDate)
        assertEquals(today, SharedTaskText.parse("下下周三才开庭", today)!!.anchorDate)
    }

    @Test
    fun `X天后支持数字与中文小写`() {
        assertEquals(today.plusDays(3), SharedTaskText.parse("3天后还书", today)!!.anchorDate)
        assertEquals(today.plusDays(2), SharedTaskText.parse("两天后倒垃圾", today)!!.anchorDate)
        assertEquals(today.plusDays(10), SharedTaskText.parse("十天后复查", today)!!.anchorDate)
        assertEquals("还书", SharedTaskText.parse("3天后还书", today)!!.title)
    }

    @Test
    fun `周末与下周末`() {
        // 今天周三：周末 = 本周六 +3；下周末 = 下周六 +10
        assertEquals(today.plusDays(3), SharedTaskText.parse("周末去超市", today)!!.anchorDate)
        assertEquals(today.plusDays(3), SharedTaskText.parse("这周末去洗车", today)!!.anchorDate)
        assertEquals(today.plusDays(10), SharedTaskText.parse("下周末去露营", today)!!.anchorDate)
        assertEquals("去超市", SharedTaskText.parse("周末去超市", today)!!.title)
    }

    @Test
    fun `单独X号本月未过用本月已过顺延下月`() {
        // 今天 10 月 14 日：15号 → 本月15；5号已过 → 11月5
        assertEquals(
            LocalDate.of(2026, 10, 15),
            SharedTaskText.parse("15号发工资", today)!!.anchorDate
        )
        assertEquals(
            LocalDate.of(2026, 11, 5),
            SharedTaskText.parse("5号交房租", today)!!.anchorDate
        )
        assertEquals("交房租", SharedTaskText.parse("5号交房租", today)!!.title)
    }

    @Test
    fun `月底月中月初与下个月`() {
        assertEquals(LocalDate.of(2026, 10, 31), SharedTaskText.parse("月底对账", today)!!.anchorDate)
        assertEquals(LocalDate.of(2026, 10, 15), SharedTaskText.parse("月中发工资", today)!!.anchorDate)
        assertEquals(LocalDate.of(2026, 11, 1), SharedTaskText.parse("月初交报表", today)!!.anchorDate)
        assertEquals(LocalDate.of(2026, 11, 30), SharedTaskText.parse("下个月底结账", today)!!.anchorDate)
        assertEquals("对账", SharedTaskText.parse("月底对账", today)!!.title)
    }

    @Test
    fun `时段词映射提醒时刻`() {
        // 周三 10-14：中午→12:00、下午→14:00、晚上→19:00、早上→8:00、上午→10:00
        assertEquals(12 * 60, SharedTaskText.parse("明天中午提醒我吃板面", today)!!.remindMinutes)
        assertEquals(19 * 60, SharedTaskText.parse("明晚记得倒垃圾", today)!!.remindMinutes)
        assertEquals(14 * 60, SharedTaskText.parse("下午去开会", today)!!.remindMinutes)
        assertEquals(8 * 60, SharedTaskText.parse("早上送娃", today)!!.remindMinutes)
        // 无时段词 → 不填时刻
        assertNull(SharedTaskText.parse("明天买牛奶", today)!!.remindMinutes)
        // 时段词不进标题
        assertEquals("吃板面", SharedTaskText.parse("明天中午提醒我吃板面", today)!!.title)
    }

    @Test
    fun `明确时刻优先于时段词`() {
        assertEquals(15 * 60, SharedTaskText.parse("下午3点开会", today)!!.remindMinutes)
        assertEquals(20 * 60, SharedTaskText.parse("明晚8点拿快递", today)!!.remindMinutes)
        assertEquals(15 * 60 + 30, SharedTaskText.parse("3点半接娃", today)!!.remindMinutes)
        assertEquals(19 * 60 + 30, SharedTaskText.parse("19:30 吃晚饭", today)!!.remindMinutes)
        assertEquals(13 * 60, SharedTaskText.parse("中午1点送餐到", today)!!.remindMinutes)
        // 裸「8点」按上午，「3点」按下午
        assertEquals(8 * 60, SharedTaskText.parse("8点出门", today)!!.remindMinutes)
        assertEquals("开会", SharedTaskText.parse("下午3点开会", today)!!.title)
    }

    @Test
    fun `无日期说法默认当天`() {
        assertEquals(today, SharedTaskText.parse("媳妇让买的清单", today)!!.anchorDate)
    }
}
