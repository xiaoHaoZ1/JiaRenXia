package io.github.xiaohaoz1.jiarenxia.domain.lunar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 查表农历校验。期望值取自香港天文台《公历与农历日期对照表》1901–2100
 * （农历 1900 年取通行 lunarInfo 历表）；纯 JVM，不依赖 android.icu。
 */
class TableLunarCalendarTest {

    private val lunar = TableLunarCalendar()

    /** 农历 1900–2100 年各年正月初一（天文台对照表）。 */
    private val newYears = listOf(
        "1900-01-31", "1901-02-19", "1902-02-08", "1903-01-29", "1904-02-16", "1905-02-04",
        "1906-01-25", "1907-02-13", "1908-02-02", "1909-01-22", "1910-02-10", "1911-01-30",
        "1912-02-18", "1913-02-06", "1914-01-26", "1915-02-14", "1916-02-03", "1917-01-23",
        "1918-02-11", "1919-02-01", "1920-02-20", "1921-02-08", "1922-01-28", "1923-02-16",
        "1924-02-05", "1925-01-24", "1926-02-13", "1927-02-02", "1928-01-23", "1929-02-10",
        "1930-01-30", "1931-02-17", "1932-02-06", "1933-01-26", "1934-02-14", "1935-02-04",
        "1936-01-24", "1937-02-11", "1938-01-31", "1939-02-19", "1940-02-08", "1941-01-27",
        "1942-02-15", "1943-02-05", "1944-01-25", "1945-02-13", "1946-02-02", "1947-01-22",
        "1948-02-10", "1949-01-29", "1950-02-17", "1951-02-06", "1952-01-27", "1953-02-14",
        "1954-02-03", "1955-01-24", "1956-02-12", "1957-01-31", "1958-02-18", "1959-02-08",
        "1960-01-28", "1961-02-15", "1962-02-05", "1963-01-25", "1964-02-13", "1965-02-02",
        "1966-01-21", "1967-02-09", "1968-01-30", "1969-02-17", "1970-02-06", "1971-01-27",
        "1972-02-15", "1973-02-03", "1974-01-23", "1975-02-11", "1976-01-31", "1977-02-18",
        "1978-02-07", "1979-01-28", "1980-02-16", "1981-02-05", "1982-01-25", "1983-02-13",
        "1984-02-02", "1985-02-20", "1986-02-09", "1987-01-29", "1988-02-17", "1989-02-06",
        "1990-01-27", "1991-02-15", "1992-02-04", "1993-01-23", "1994-02-10", "1995-01-31",
        "1996-02-19", "1997-02-07", "1998-01-28", "1999-02-16", "2000-02-05", "2001-01-24",
        "2002-02-12", "2003-02-01", "2004-01-22", "2005-02-09", "2006-01-29", "2007-02-18",
        "2008-02-07", "2009-01-26", "2010-02-14", "2011-02-03", "2012-01-23", "2013-02-10",
        "2014-01-31", "2015-02-19", "2016-02-08", "2017-01-28", "2018-02-16", "2019-02-05",
        "2020-01-25", "2021-02-12", "2022-02-01", "2023-01-22", "2024-02-10", "2025-01-29",
        "2026-02-17", "2027-02-06", "2028-01-26", "2029-02-13", "2030-02-03", "2031-01-23",
        "2032-02-11", "2033-01-31", "2034-02-19", "2035-02-08", "2036-01-28", "2037-02-15",
        "2038-02-04", "2039-01-24", "2040-02-12", "2041-02-01", "2042-01-22", "2043-02-10",
        "2044-01-30", "2045-02-17", "2046-02-06", "2047-01-26", "2048-02-14", "2049-02-02",
        "2050-01-23", "2051-02-11", "2052-02-01", "2053-02-19", "2054-02-08", "2055-01-28",
        "2056-02-15", "2057-02-04", "2058-01-24", "2059-02-12", "2060-02-02", "2061-01-21",
        "2062-02-09", "2063-01-29", "2064-02-17", "2065-02-05", "2066-01-26", "2067-02-14",
        "2068-02-03", "2069-01-23", "2070-02-11", "2071-01-31", "2072-02-19", "2073-02-07",
        "2074-01-27", "2075-02-15", "2076-02-05", "2077-01-24", "2078-02-12", "2079-02-02",
        "2080-01-22", "2081-02-09", "2082-01-29", "2083-02-17", "2084-02-06", "2085-01-26",
        "2086-02-14", "2087-02-03", "2088-01-24", "2089-02-10", "2090-01-30", "2091-02-18",
        "2092-02-07", "2093-01-27", "2094-02-15", "2095-02-05", "2096-01-25", "2097-02-12",
        "2098-02-01", "2099-01-21", "2100-02-09"
    ).map(LocalDate::parse)

    /** 有闰月的农历年 → 闰几月（天文台对照表）。 */
    private val leapMonths = mapOf(
        1900 to 8, 1903 to 5, 1906 to 4, 1909 to 2, 1911 to 6, 1914 to 5, 1917 to 2, 1919 to 7,
        1922 to 5, 1925 to 4, 1928 to 2, 1930 to 6, 1933 to 5, 1936 to 3, 1938 to 7, 1941 to 6,
        1944 to 4, 1947 to 2, 1949 to 7, 1952 to 5, 1955 to 3, 1957 to 8, 1960 to 6, 1963 to 4,
        1966 to 3, 1968 to 7, 1971 to 5, 1974 to 4, 1976 to 8, 1979 to 6, 1982 to 4, 1984 to 10,
        1987 to 6, 1990 to 5, 1993 to 3, 1995 to 8, 1998 to 5, 2001 to 4, 2004 to 2, 2006 to 7,
        2009 to 5, 2012 to 4, 2014 to 9, 2017 to 6, 2020 to 4, 2023 to 2, 2025 to 6, 2028 to 5,
        2031 to 3, 2033 to 11, 2036 to 6, 2039 to 5, 2042 to 2, 2044 to 7, 2047 to 5, 2050 to 3,
        2052 to 8, 2055 to 6, 2058 to 4, 2061 to 3, 2063 to 7, 2066 to 5, 2069 to 4, 2071 to 8,
        2074 to 6, 2077 to 4, 2080 to 3, 2082 to 7, 2085 to 5, 2088 to 4, 2090 to 8, 2093 to 6,
        2096 to 4, 2099 to 2
    )

    @Test
    fun 各年正月初一() {
        assertEquals(201, newYears.size)
        newYears.forEachIndexed { i, d ->
            val y = 1900 + i
            assertEquals("农历 $y 年正月初一", d, lunar.toSolar(y, 1, 1, false))
            assertEquals(LunarDate(y, 1, 1, false), lunar.fromSolar(d))
            // 前一天是上一农历年的最后一天（1900 年除外）
            if (i > 0) assertEquals(y - 1, lunar.fromSolar(d.minusDays(1)).year)
        }
    }

    @Test
    fun 各年闰月() {
        for (y in 1900..2100) {
            assertEquals("农历 $y 年闰月", leapMonths[y], lunar.leapMonthOf(y))
        }
    }

    @Test
    fun 已知日期锚点() {
        // 2027 春节：ICU 天文算法算成 2 月 7 日，天文台为 2 月 6 日
        assertEquals(LocalDate.of(2027, 2, 6), lunar.toSolar(2027, 1, 1, false))
        // 2027 年除夕（农历 2026 年腊月廿九，该月小月）
        assertEquals(29, lunar.daysInMonth(2026, 12, false))
        assertEquals(LocalDate.of(2027, 2, 5), lunar.toSolar(2026, 12, 29, false))
        assertNull(lunar.toSolar(2026, 12, 30, false))
        assertEquals(LocalDate.of(2026, 9, 25), lunar.toSolar(2026, 8, 15, false))
        assertEquals(LocalDate.of(2024, 8, 18), lunar.toSolar(2024, 7, 15, false))
        assertEquals(LocalDate.of(2025, 9, 6), lunar.toSolar(2025, 7, 15, false))
        assertEquals(LocalDate.of(2025, 11, 20), lunar.toSolar(2025, 10, 1, false))
        // 闰月：2023 闰二月初一、2025 闰六月初一（小月）、2033 闰十一月初一
        assertEquals(LocalDate.of(2023, 3, 22), lunar.toSolar(2023, 2, 1, true))
        assertEquals(LocalDate.of(2025, 7, 25), lunar.toSolar(2025, 6, 1, true))
        assertEquals(LunarDate(2025, 6, 1, true), lunar.fromSolar(LocalDate.of(2025, 7, 25)))
        assertEquals(29, lunar.daysInMonth(2025, 6, true))
        assertNull(lunar.toSolar(2025, 6, 30, true))
        assertEquals(LocalDate.of(2033, 12, 22), lunar.toSolar(2033, 11, 1, true))
        // 闰月之后的月份序号正确：2025 七月初一 = 2025-08-23
        assertEquals(LocalDate.of(2025, 8, 23), lunar.toSolar(2025, 7, 1, false))
    }

    @Test
    fun 不存在的农历日() {
        assertNull(lunar.toSolar(2026, 6, 1, true))
        assertNull(lunar.daysInMonth(2026, 6, true))
        assertNull(lunar.toSolar(2026, 13, 1, false))
        assertNull(lunar.toSolar(2026, 1, 0, false))
        assertNull(lunar.toSolar(2026, 1, 31, false))
    }

    @Test
    fun 全范围逐日往返且连续() {
        var d = newYears.first()
        val end = LocalDate.of(2101, 1, 28)
        var prev: LunarDate? = null
        while (d <= end) {
            val ld = lunar.fromSolar(d)
            assertEquals(d, lunar.toSolar(ld.year, ld.month, ld.day, ld.leap))
            if (prev != null && ld.day != 1) assertEquals(prev.day + 1, ld.day)
            prev = ld
            d = d.plusDays(1)
        }
        assertEquals(LunarDate(2100, 12, 29, false), lunar.fromSolar(end))
    }

    @Test
    fun 表外年份() {
        assertNull(lunar.toSolar(1899, 1, 1, false))
        assertNull(lunar.toSolar(2101, 1, 1, false))
        assertNull(lunar.leapMonthOf(1899))
        assertNull(lunar.daysInMonth(2101, 1, false))
        assertThrows(IllegalArgumentException::class.java) {
            lunar.fromSolar(LocalDate.of(1899, 12, 31))
        }
        assertThrows(IllegalArgumentException::class.java) {
            lunar.fromSolar(LocalDate.of(2101, 1, 29))
        }
    }

    @Test
    fun 表外日期交给后备实现() {
        val marker = LunarDate(1888, 8, 8, false)
        val withFallback = TableLunarCalendar(object : LunarCalendar {
            override fun toSolar(year: Int, month: Int, day: Int, leap: Boolean) = null
            override fun fromSolar(date: LocalDate) = marker
            override fun leapMonthOf(lunarYear: Int) = null
            override fun daysInMonth(lunarYear: Int, month: Int, leap: Boolean) = null
        })
        assertEquals(marker, withFallback.fromSolar(LocalDate.of(1888, 9, 13)))
        assertEquals(LunarDate(2027, 1, 1, false),
            withFallback.fromSolar(LocalDate.of(2027, 2, 6)))
    }
}
