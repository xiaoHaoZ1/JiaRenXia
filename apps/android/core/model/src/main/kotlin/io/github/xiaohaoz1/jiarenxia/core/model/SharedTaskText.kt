package io.github.xiaohaoz1.jiarenxia.core.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 分享文本 → 家事预填（“分享 → 家人侠”记家事）：
 * 标题取文本前 [TITLE_MAX] 字（换行折叠为空格），语首的提醒话术
 * （记得提醒我/别忘了/记一下…）与日期说法剥掉不进标题；语尾的
 * 日期/时刻补充同样剥掉（“挂号。早上7点”→“挂号”）；
 * 识别文本中的日期说法作为建议日期——「X天后」「明天/后天/大后天」
 * 「周三/下周五/礼拜X」「周末/下周末」「X月X日/号」「下月X号」「月底/月中/月初」「X号」。
 * 识别不到时用当天；日期只是预填，用户在家事编辑页可改可删。
 */
object SharedTaskText {

    /** 预填结果：标题、备注（原文较长或跨行时保留全文）、建议日期、建议提醒时刻（一天内分钟数，可空）。 */
    data class Parsed(
        val title: String,
        val note: String?,
        val anchorDate: LocalDate,
        val remindMinutes: Int? = null
    )

    const val TITLE_MAX = 30
    private const val NOTE_MAX = 200

    /** 空白文本返回 null。 */
    fun parse(text: String, today: LocalDate): Parsed? {
        val raw = text.trim()
        if (raw.isEmpty()) return null
        // 语首的提醒话术与日期说法剥掉再作标题（“记得提醒我明天买牛奶”→“买牛奶”），
        // 语尾的日期/时刻补充也剥掉（“挂号。早上7点”→“挂号”）；
        // 剥空了说明整句都是话术，退回原文标题
        val stripped = stripTail(stripLead(raw))
        val base = if (stripped.isEmpty()) raw else stripped
        val title = base.replace(Regex("\\s+"), " ").take(TITLE_MAX)
        // 标题装不下的内容放进备注，保住上下文（谁说的、细节）
        val note = if (raw.length > TITLE_MAX || raw.contains('\n')) {
            raw.take(NOTE_MAX)
        } else {
            null
        }
        return Parsed(
            title = title, note = note,
            anchorDate = anchorDateOf(raw, today),
            remindMinutes = remindMinutesOf(raw)
        )
    }

    /**
     * 一天内提醒时刻：优先明确时刻（下午3点/15:30/8点半），其次时段词
     * （早上 8:00、上午 10:00、中午 12:00、下午 14:00、傍晚 18:00、晚上/明晚 19:00）。
     * 裸「X点」时段不明时按家事语境取：1–6 点当下午/晚上（+12），7–11 当上午，12–23 原样。
     */
    private fun remindMinutesOf(text: String): Int? {
        CLOCK_TIME.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            return (h * 60 + min).takeIf { h < 24 && min < 60 }
        }
        CLOCK_WORD.find(text)?.let { m ->
            val period = m.groupValues[1]
            var h = m.groupValues[2].toIntOrNull() ?: return@let
            val min = when (m.groupValues[3]) {
                "半" -> 30
                else -> m.groupValues[3].removeSuffix("分").toIntOrNull() ?: 0
            }
            if (h > 24 || min >= 60) return@let
            h = when (period) {
                "下午", "晚上", "傍晚", "明晚", "今晚", "夜里" -> if (h < 12) h + 12 else h
                "中午" -> if (h <= 2) h + 12 else h // 中午1点=13:00；中午11/12点原样
                "凌晨" -> h
                else -> h // 早上/上午/无前缀
            }
            if (period.isEmpty() && h in 1..6) h += 12 // 裸「3点」家事语境多是下午/晚上
            return h * 60 + min
        }
        PERIOD_TIME.find(text)?.let { m ->
            return when (m.groupValues[1]) {
                "明早", "今早", "早上", "一早" -> 8 * 60
                "上午" -> 10 * 60
                "中午" -> 12 * 60
                "下午" -> 14 * 60
                "傍晚" -> 18 * 60
                else -> 19 * 60 // 明晚/今晚/晚上/夜里
            }
        }
        return null
    }

    /**
     * 优先级：X天后 → 大后天/后天/明天 → 下周X → 周末 → 本周X/周X/星期X/礼拜X
     * → 下月X号 → X月X日/号（含明年）→ 月底/月中/月初 → X号。
     * 同一文本多处命中时按此顺序取第一类说法。
     */
    private fun anchorDateOf(text: String, today: LocalDate): LocalDate {
        DAYS_LATER.find(text)?.let { m ->
            val n = m.groupValues[1].toIntOrNull()
                ?: cnNum(m.groupValues[1])
                ?: return@let
            return today.plusDays(n.coerceAtMost(365).toLong())
        }
        when {
            text.contains("大后天") -> return today.plusDays(3)
            text.contains("后天") -> return today.plusDays(2)
            REL_TOMORROW.containsMatchIn(text) -> return today.plusDays(1)
        }
        NEXT_WEEK.find(text)?.let { m ->
            val w = weekdayOf(m.groupValues[1]) ?: return@let
            // 下周X = 下周一为起点的下一周的第 w 天
            var daysToNextMonday = (DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7
            if (daysToNextMonday == 0) daysToNextMonday = 7
            return today.plusDays(daysToNextMonday + w - 1L)
        }
        WEEKEND.find(text)?.let { m ->
            if (m.groupValues[1] == "下") {
                // 下周末 = 下周的周六
                var d = (DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7
                if (d == 0) d = 7
                return today.plusDays(d + DayOfWeek.SATURDAY.value - 1L)
            }
            // 周末/这周末/本周末 = 本周六（今天周日时顺延到下个周六）
            val d = (DayOfWeek.SATURDAY.value - today.dayOfWeek.value + 7) % 7
            return today.plusDays(d.toLong())
        }
        THIS_WEEK.find(text)?.let { m ->
            val w = weekdayOf(m.groupValues[1]) ?: return@let
            val d = (w - today.dayOfWeek.value + 7) % 7
            return today.plusDays(d.toLong())
        }
        NEXT_MONTH.find(text)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@let
            val nm = today.plusMonths(1)
            val d = day.coerceAtMost(nm.lengthOfMonth())
            return LocalDate.of(nm.year, nm.month, d)
        }
        MONTH_DAY.find(text)?.let { m ->
            val nextYear = m.groupValues[1].isNotEmpty()
            val month = m.groupValues[2].toIntOrNull() ?: return@let
            val day = m.groupValues[3].toIntOrNull() ?: return@let
            var year = today.year + if (nextYear) 1 else 0
            var d = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return today
            // 今年的日期已过 → 顺延到明年（“10月20号交物业费”10月21日说→明年）
            if (!nextYear && d.isBefore(today)) {
                year += 1
                d = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return today
            }
            return d
        }
        MONTH_PART.find(text)?.let { m ->
            val nextMonth = m.groupValues[1].isNotEmpty()
            val day = when (m.groupValues[2]) {
                "底", "末" -> 31
                "中" -> 15
                else -> 1 // 初
            }
            var base = today.plusMonths(if (nextMonth) 1 else 0)
            var date = runCatching {
                LocalDate.of(base.year, base.month, day.coerceAtMost(base.lengthOfMonth()))
            }.getOrNull() ?: return@let
            // 本月的月初/月中已过 → 顺延下月
            if (!nextMonth && date.isBefore(today)) {
                base = base.plusMonths(1)
                date = runCatching {
                    LocalDate.of(base.year, base.month, day.coerceAtMost(base.lengthOfMonth()))
                }.getOrNull() ?: return@let
            }
            return date
        }
        DAY_OF_MONTH.find(text)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@let
            var date = runCatching {
                LocalDate.of(today.year, today.month, day.coerceAtMost(today.lengthOfMonth()))
            }.getOrNull() ?: return@let
            if (date.isBefore(today)) {
                val nm = today.plusMonths(1)
                date = runCatching {
                    LocalDate.of(nm.year, nm.month, day.coerceAtMost(nm.lengthOfMonth()))
                }.getOrNull() ?: return@let
            }
            return date
        }
        return today
    }

    /** 中文小写数字（十/二十/二十五）转 Int，不支持返回 null。 */
    private fun cnNum(s: String): Int? {
        val d = mapOf(
            '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        if (s.isEmpty()) return null
        if (!s.contains('十')) return s.singleOrNull()?.let { d[it] }
        val parts = s.split('十')
        val tens = if (parts[0].isEmpty()) 1 else d[parts[0].first()] ?: return null
        val ones = parts.getOrNull(1)?.let { if (it.isEmpty()) 0 else d[it.first()] }
            ?: return null
        return tens * 10 + ones
    }

    /** 循环剥掉语首的提醒话术/日期词（“明早记得提醒我”两种叠一起也剥干净）。 */
    private fun stripLead(text: String): String {
        var t = text
        while (true) {
            val n = LEAD_PREFIX.replaceFirst(t, "")
                .trimStart('，', ',', '。', '：', ':', ' ', '　')
            if (n == t) return t
            t = n
        }
    }

    /**
     * 循环剥掉语尾的日期/时刻补充（“挂号。早上7点”→“挂号”）。
     * 只剥时间类说法：话术词在语尾可能是正常语义（“我不记得”“找师傅帮忙”），不剥。
     */
    private fun stripTail(text: String): String {
        var t = text
        while (true) {
            val n = TAIL_SUFFIX.replace(t, "")
                .trimEnd('，', ',', '。', '：', ':', ' ', '　')
            if (n == t) return t
            t = n
        }
    }

    private fun weekdayOf(c: String): Int? = when (c) {
        "一" -> 1
        "二" -> 2
        "三" -> 3
        "四" -> 4
        "五" -> 5
        "六" -> 6
        "日", "天" -> 7
        else -> null
    }

    // 「HH:mm」明确时刻
    private val CLOCK_TIME = Regex("(\\d{1,2})\\s*[:：]\\s*(\\d{2})")
    // 「[时段]X点[半/分]」
    private val CLOCK_WORD =
        Regex("(下午|晚上|中午|早上|上午|凌晨|傍晚|明早|明晚|今晚|夜里)?\\s*(\\d{1,2})\\s*[点时]\\s*(半|\\d{1,2}\\s*分)?")
    // 时段词兜底（无明表时刻时）
    private val PERIOD_TIME = Regex("(明早|今早|早上|一早|上午|中午|下午|傍晚|明晚|今晚|晚上|夜里)")
    private val REL_TOMORROW = Regex("明天|明早|明晚|明儿")
    // 「X天后」：阿拉伯数字或中文小写（三天后/15天后）
    private val DAYS_LATER = Regex("(\\d{1,3}|[一二两三四五六七八九十]{1,3})\\s*天后")
    // 「下下周X」不算下周（第一个“下”的前置检查）
    private val NEXT_WEEK = Regex("(?<!下)下个?(?:周|星期|礼拜)([一二三四五六日天])")
    // 周末 → 周六；「下周末」按下周算
    private val WEEKEND = Regex("([下本这])?个?周末")
    // 排除「每/上/下周X」与「一/两/半周三次」这类量词误命中
    private val THIS_WEEK =
        Regex("(?<![下上每一两半])(?:本|这)?个?(?:周|星期|礼拜)([一二三四五六日天])")
    // 语首剥除项：长词在前（记得提醒我 先于 记得，大后天 先于 后天）
    private val LEAD_PREFIX = Regex(
        "^(?:记得提醒我|记得提醒|一定要记得|一定记得|千万记得|千万别忘|提醒我一下|提醒我|" +
            "别忘了|别忘记|别忘|不要忘了|不要忘记|帮我记一下|帮忙记一下|帮我记|帮忙记|记一下|记着|记得|" +
            "(?:[一二两三四五六七八九十]{1,3}|\\d{1,3})\\s*天后|" +
            "\\d{1,2}:\\d{2}|" +
            "(?:[上下]午|晚上|中午|早上|凌晨|明早|明晚|今晚|夜里)?\\s*\\d{1,2}\\s*(?:点|时)(?:半|\\d{1,2}\\s*分)?|" +
            "大后天|后天|明早|明晚|明儿|明天|今晚[上夜里]?|今儿|今天|今早|" +
            "[下本这]?个?周末|" +
            "(?:本|这|下)?个?(?:周|星期|礼拜)[一二三四五六日天]|" +
            "(?:明年)?\\d{1,2}月\\d{1,2}[日号]|下?个?月\\d{1,2}[日号]|下?个?月(?:底|末|初|中)|月底|月末|月初|月中|\\d{1,2}[号日](?!后)|" +
            "待会[儿]?|一会[儿]?|回头|早上|上午|中午|下午|晚上|傍晚|帮我|帮忙|麻烦你?)"
    )
    // 语尾剥除项：只含日期/时刻表达（话术在语尾可能是正文，见 stripTail）；可连续多段（“，周五，3点”）
    private val TAIL_SUFFIX = Regex(
        "(?:[，,。；;：:、\\s　]*(?:" +
            "(?:[一二两三四五六七八九十]{1,3}|\\d{1,3})\\s*天后|" +
            "\\d{1,2}[:：]\\d{2}|" +
            "(?:[上下]午|晚上|中午|早上|凌晨|明早|明晚|今晚|夜里)?\\s*\\d{1,2}\\s*(?:点|时)(?:半|\\d{1,2}\\s*分)?|" +
            "大后天|后天|明早|明晚|明儿|明天|今晚[上夜里]?|今儿|今天|今早|" +
            "[下本这]?个?周末|" +
            "(?:本|这|下)?个?(?:周|星期|礼拜)[一二三四五六日天]|" +
            "(?:明年)?\\d{1,2}月\\d{1,2}[日号]|下?个?月\\d{1,2}[日号]|下?个?月(?:底|末|初|中)|月底|月末|月初|月中|\\d{1,2}[号日](?!后)|" +
            "待会[儿]?|一会[儿]?|回头|早上|上午|中午|下午|晚上|傍晚))+[，,。；;：:、.…\\s　]*$"
    )
    private val NEXT_MONTH = Regex("下(?:个)?月(\\d{1,2})[日号]")
    private val MONTH_DAY = Regex("(明年)?(\\d{1,2})月(\\d{1,2})[日号]")
    // 月底/月末/月初/月中（含「下个月…」）
    private val MONTH_PART = Regex("(下)?个?月(底|末|初|中)")
    // 单独说「X号/X日」：不能紧跟在 数字/月/号 后（避免吃掉「10月20号」的尾巴），「3号后」这类也不算
    private val DAY_OF_MONTH = Regex("(?<![\\d月号])(\\d{1,2})[号日](?!后)")
}
