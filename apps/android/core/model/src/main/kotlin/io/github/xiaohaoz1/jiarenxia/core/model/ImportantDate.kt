package io.github.xiaohaoz1.jiarenxia.core.model

/**
 * 重要日期类型（DATABASE_SCHEMA 25.7）。
 *
 * 生日按历法拆为 [BIRTHDAY_SOLAR] / [BIRTHDAY_LUNAR] 两种类型：
 * 数据库 UNIQUE(person_id, type) 不变，恰好保证每人最多一条阳历生日、
 * 一条农历生日；忌日 / 结婚纪念日的每人一条约束原样保留。
 */
enum class ImportantDateType {
    /** 阳历生日。 */
    BIRTHDAY_SOLAR,
    /** 农历生日。 */
    BIRTHDAY_LUNAR,
    DEATH_ANNIVERSARY,
    /** 结婚纪念日（婚礼/婚宴日，“结婚了多久”）。 */
    WEDDING_ANNIVERSARY,
    /** 领证纪念日（登记日，“领证了多久”）。 */
    LICENSE_ANNIVERSARY,
    /** 恋爱纪念日（在一起的日子，“恋爱了多久”）。 */
    DATING_ANNIVERSARY;

    /** 两种历法生日都算生日（提醒渠道/文案等按此分流）。 */
    val isBirthday: Boolean get() = this == BIRTHDAY_SOLAR || this == BIRTHDAY_LUNAR

    /**
     * 夫妻共享的纪念日：一对夫妻每种类型共一条，挂在任一方名下，
     * 详情页/碰一碰/合并去重都按此口径（结婚 / 领证 / 恋爱）。
     */
    val isCoupleShared: Boolean get() =
        this == WEDDING_ANNIVERSARY ||
            this == LICENSE_ANNIVERSARY ||
            this == DATING_ANNIVERSARY

    /** 生日类型对应的历法；非生日类型为 null。 */
    val birthdayCalendar: CalendarType? get() = when (this) {
        BIRTHDAY_SOLAR -> CalendarType.SOLAR
        BIRTHDAY_LUNAR -> CalendarType.LUNAR
        else -> null
    }

    companion object {
        /** 某历法对应的生日类型。 */
        fun birthdayOf(calendar: CalendarType): ImportantDateType = when (calendar) {
            CalendarType.SOLAR -> BIRTHDAY_SOLAR
            CalendarType.LUNAR -> BIRTHDAY_LUNAR
        }
    }
}

/** 历法类型：公历 / 农历。 */
enum class CalendarType { SOLAR, LUNAR }

/**
 * 生日取值：日期 + 历法 + 是否闰月。
 * LUNAR 时 month 为农历月（1..12）、day 为农历日（1..30），
 * leapMonth 仅在 LUNAR 下有意义。
 */
data class BirthdayValue(
    val date: PartialDate,
    val calendar: CalendarType = CalendarType.SOLAR,
    val leapMonth: Boolean = false
)

/**
 * 一个人的双生日：最多一条阳历、一条农历，互相独立编辑/删除。
 * 槽位与历法一致性由构造校验保证。
 */
data class PersonBirthdays(
    val solar: BirthdayValue? = null,
    val lunar: BirthdayValue? = null
) {
    init {
        require(solar == null || solar.calendar == CalendarType.SOLAR) { "solar slot" }
        require(lunar == null || lunar.calendar == CalendarType.LUNAR) { "lunar slot" }
        require(solar == null || !solar.leapMonth) { "solar leapMonth" }
    }

    /** 指定历法的生日。 */
    fun get(calendar: CalendarType): BirthdayValue? =
        if (calendar == CalendarType.SOLAR) solar else lunar

    /** 替换指定历法的生日（null 清除）。 */
    fun with(calendar: CalendarType, value: BirthdayValue?): PersonBirthdays =
        if (calendar == CalendarType.SOLAR) copy(solar = value) else copy(lunar = value)

    /** 已填的生日（阳历在前）。 */
    val list: List<BirthdayValue> get() = listOfNotNull(solar, lunar)

    fun isEmpty(): Boolean = solar == null && lunar == null
    fun isNotEmpty(): Boolean = !isEmpty()
}

/**
 * 人物的重要日期（生日 / 忌日 / 结婚纪念日）。
 * 生日行的 type 与 calendarType 必须一致：BIRTHDAY_SOLAR↔SOLAR、BIRTHDAY_LUNAR↔LUNAR。
 */
data class ImportantDate(
    val id: String,
    val personId: String,
    val type: ImportantDateType = ImportantDateType.BIRTHDAY_SOLAR,
    val date: PartialDate,
    val calendarType: CalendarType = CalendarType.SOLAR,
    val isLeapMonth: Boolean = false,
    val note: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
