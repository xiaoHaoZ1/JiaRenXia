package io.github.xiaohaoz1.jiarenxia.domain.lunar

import java.time.LocalDate

/**
 * 查表农历实现（农历 1900–2100 年），取代按天文算法推算的 [IcuLunarCalendar]。
 *
 * 为什么换：android.icu 的 ChineseCalendar 天文精度有限，朔日贴近北京时间午夜时
 * 会把月首算错一天（如 2027 年春节算成 2 月 7 日，官方为 2 月 6 日），整月农历日期随之错位。
 *
 * 数据来源：香港天文台《公历与农历日期对照表》1901–2100 年逐日原文生成，
 * 解码结果与原文逐日核对 73,049 天无差异；农历 1900 年（1900-01-31 起）与
 * 2100 年腊月取自通行的 lunarInfo 历表，该表其余年份与天文台逐年一致。
 *
 * 编码：每个农历年一个 Int。低 4 位 = 闰月月份（0 无闰月）；从第 4 位起，
 * 第 j 位（该年第 j 个月，按出现顺序、含闰月，0 起）为 1 表示大月 30 天，否则小月 29 天。
 *
 * 表外日期：[toSolar]/[leapMonthOf]/[daysInMonth] 返回 null（与旧实现的年份范围一致）；
 * [fromSolar] 交给 [fallback]（App 内为 ICU 实现，保持旧行为），未提供时抛 IllegalArgumentException。
 */
class TableLunarCalendar(
    private val fallback: LunarCalendar? = null
) : LunarCalendar {

    override fun toSolar(year: Int, month: Int, day: Int, leap: Boolean): LocalDate? {
        if (year !in MIN_YEAR..MAX_YEAR || month !in 1..12 || day < 1) return null
        val i = year - MIN_YEAR
        val code = CODES[i]
        val j = indexOf(code, month, leap)
        if (j < 0 || day > monthLength(code, j)) return null
        var epochDay = NEW_YEAR[i]
        for (k in 0 until j) epochDay += monthLength(code, k)
        return LocalDate.ofEpochDay(epochDay + day - 1)
    }

    override fun fromSolar(date: LocalDate): LunarDate {
        val epochDay = date.toEpochDay()
        if (epochDay < NEW_YEAR.first() || epochDay >= NEW_YEAR.last()) {
            return fallback?.fromSolar(date)
                ?: throw IllegalArgumentException("农历表范围外的日期：$date")
        }
        // 二分找所在农历年：NEW_YEAR[i] <= epochDay < NEW_YEAR[i + 1]
        var lo = 0
        var hi = CODES.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (NEW_YEAR[mid] <= epochDay) lo = mid else hi = mid - 1
        }
        val code = CODES[lo]
        val leapMonth = code and 0xF
        var offset = (epochDay - NEW_YEAR[lo]).toInt()
        var j = 0
        while (offset >= monthLength(code, j)) {
            offset -= monthLength(code, j)
            j++
        }
        val isLeap = leapMonth != 0 && j == leapMonth
        val month = if (leapMonth != 0 && j >= leapMonth) j else j + 1
        return LunarDate(year = MIN_YEAR + lo, month = month, day = offset + 1, leap = isLeap)
    }

    override fun leapMonthOf(lunarYear: Int): Int? {
        if (lunarYear !in MIN_YEAR..MAX_YEAR) return null
        return (CODES[lunarYear - MIN_YEAR] and 0xF).takeIf { it != 0 }
    }

    override fun daysInMonth(lunarYear: Int, month: Int, leap: Boolean): Int? {
        if (lunarYear !in MIN_YEAR..MAX_YEAR || month !in 1..12) return null
        val code = CODES[lunarYear - MIN_YEAR]
        val j = indexOf(code, month, leap)
        return if (j < 0) null else monthLength(code, j)
    }

    companion object {
        const val MIN_YEAR = 1900
        const val MAX_YEAR = 2100

        /** 农历 1900–2100 年编码（见类注释）；由天文台对照表生成，勿手改。 */
        private val CODES = intArrayOf(
            0x16d28, 0x07520, 0x0ea50, 0x164a5, 0x064b0, 0x0a9b0, 0x15564, 0x056a0, 0x0b590, 0x17522, // 1900-1909
            0x07520, 0x1b256, 0x0b250, 0x0a4b0, 0x14ab5, 0x02ad0, 0x056b0, 0x0b692, 0x0da90, 0x1d927, // 1910-1919
            0x0e920, 0x0d250, 0x1a4d5, 0x0a560, 0x02b60, 0x15b54, 0x06d40, 0x0ea90, 0x1e922, 0x0e920, // 1920-1929
            0x0d266, 0x052b0, 0x0a570, 0x12b65, 0x0b5a0, 0x06d40, 0x0ec93, 0x07490, 0x16937, 0x0a930, // 1930-1939
            0x052b0, 0x0a5b6, 0x0aad0, 0x056a0, 0x1b554, 0x0ba40, 0x0b490, 0x1a932, 0x0a950, 0x152d7, // 1940-1949
            0x05360, 0x0aad0, 0x15aa5, 0x05b20, 0x0da50, 0x1d4a3, 0x0d4a0, 0x0a958, 0x0a970, 0x05560, // 1950-1959
            0x0ab56, 0x0ad50, 0x06d20, 0x0ea54, 0x0ea50, 0x064a0, 0x0c973, 0x0a9b0, 0x155a7, 0x056a0, // 1960-1969
            0x0b690, 0x17525, 0x0b520, 0x0b250, 0x164b4, 0x0a4b0, 0x14ab8, 0x02ad0, 0x056d0, 0x0b696, // 1970-1979
            0x0da90, 0x0d920, 0x1d254, 0x0d250, 0x1a4da, 0x0a560, 0x02b60, 0x05b56, 0x06d50, 0x0ea90, // 1980-1989
            0x1e925, 0x0e920, 0x0d260, 0x0a563, 0x0a570, 0x14d68, 0x035a0, 0x06d50, 0x16c95, 0x07490, // 1990-1999
            0x06930, 0x152b4, 0x052b0, 0x0a5b0, 0x155a2, 0x056a0, 0x1b557, 0x0ba40, 0x0b490, 0x1a935, // 2000-2009
            0x0a950, 0x052d0, 0x0aad4, 0x0ab50, 0x15aa9, 0x05d20, 0x0da50, 0x1d4a6, 0x0d4a0, 0x0c950, // 2010-2019
            0x152e4, 0x05560, 0x0ab50, 0x15b22, 0x06d20, 0x0ea56, 0x07250, 0x064b0, 0x0c975, 0x0cab0, // 2020-2029
            0x055a0, 0x0ad63, 0x0b690, 0x1752b, 0x0b520, 0x0b250, 0x1a4b6, 0x0a4b0, 0x04ab0, 0x055b5, // 2030-2039
            0x05ad0, 0x0b6a0, 0x1b522, 0x0d920, 0x1d257, 0x0d250, 0x0a550, 0x14ad5, 0x04b60, 0x05b50, // 2040-2049
            0x0daa3, 0x0ec90, 0x1e928, 0x0e920, 0x0d260, 0x0a566, 0x0a570, 0x05560, 0x06d54, 0x07550, // 2050-2059
            0x07490, 0x0e933, 0x06930, 0x152b7, 0x052b0, 0x0a5b0, 0x155a5, 0x056a0, 0x0b650, 0x174a4, // 2060-2069
            0x0b4a0, 0x1a958, 0x0a950, 0x052d0, 0x0aad6, 0x0ab50, 0x05aa0, 0x0ba54, 0x0da50, 0x0d4a0, // 2070-2079
            0x1c953, 0x0c960, 0x194e7, 0x05560, 0x0ab50, 0x15b25, 0x06d20, 0x0ea50, 0x0e4a4, 0x068b0, // 2080-2089
            0x0c978, 0x04ab0, 0x055b0, 0x0ad66, 0x0b6a0, 0x07520, 0x17254, 0x0b450, 0x0a8b0, 0x149b2, // 2090-2099
            0x04ab0 // 2100
        )

        /** 各农历年正月初一的 epochDay（末元素为农历 2101 年正月初一，作右开边界）。 */
        private val NEW_YEAR: LongArray = LongArray(CODES.size + 1).also { a ->
            a[0] = LocalDate.of(1900, 1, 31).toEpochDay()
            for (i in CODES.indices) {
                val code = CODES[i]
                var days = 0
                for (j in 0 until monthCount(code)) days += monthLength(code, j)
                a[i + 1] = a[i] + days
            }
        }

        private fun monthCount(code: Int): Int = if ((code and 0xF) != 0) 13 else 12

        private fun monthLength(code: Int, j: Int): Int =
            if (((code shr (4 + j)) and 1) == 1) 30 else 29

        /** 农历 (月, 是否闰) → 该年第几个月（0 起）；该月不存在返回 -1。 */
        private fun indexOf(code: Int, month: Int, leap: Boolean): Int {
            val leapMonth = code and 0xF
            return when {
                leap -> if (leapMonth == month) month else -1
                leapMonth != 0 && month > leapMonth -> month
                else -> month - 1
            }
        }
    }
}
