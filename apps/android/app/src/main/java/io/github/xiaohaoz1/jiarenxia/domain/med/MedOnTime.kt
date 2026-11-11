package io.github.xiaohaoz1.jiarenxia.domain.med

import io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
import io.github.xiaohaoz1.jiarenxia.core.model.Medication
import io.github.xiaohaoz1.jiarenxia.core.model.MedicationPersonPrefs
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * 「按时吃」提醒的纯逻辑（FAMILY_AFFAIRS 23）：
 * 槽位计算、下一次触发、送达文案与长辈首页高亮判定。
 * 不触碰 Android API，可 JVM 单测。
 */
object MedOnTime {

    /** 总开关 + 「按时吃提醒」子开关（两个都开才排闹钟）。 */
    data class Settings(val remindersEnabled: Boolean, val onTimeEnabled: Boolean)

    /** 「人物 + 时刻」槽位：该时刻当天要吃的药（未停用、有时刻、非需要时吃、日期有效）。 */
    data class Slot(
        val personId: String,
        val minute: Int,
        val meds: List<Medication>
    )

    /** 该药在 [date] 是否仍在服用计划内（只看药自身规则，不看人物与开关）。 */
    fun scheduledOn(med: Medication, date: LocalDate): Boolean {
        if (med.stoppedEpochDay != null) return false
        if (med.usage == MedUsage.AS_NEEDED) return false
        if (med.times.isEmpty()) return false
        if (date.isBefore(LocalDate.ofEpochDay(med.startEpochDay))) return false
        if (med.usage == MedUsage.SHORT_COURSE) {
            val days = med.courseDays
            if (days != null) {
                val last = LocalDate.ofEpochDay(med.startEpochDay)
                    .plusDays((days - 1).toLong())
                if (date.isAfter(last)) return false
            }
        }
        return true
    }

    /**
     * 今天需要按时提醒的「人物 + 时刻」槽位。
     * 总开关与子开关都开、人物在世、该人按时吃生效
     * （显式 onTimeEnabled 优先；null 时“我”开、他人关）才产出。
     */
    fun slots(
        meds: List<Medication>,
        prefsByPerson: Map<String, MedicationPersonPrefs>,
        selfId: String?,
        livingIds: Set<String>,
        settings: Settings,
        today: LocalDate
    ): List<Slot> {
        if (!settings.remindersEnabled || !settings.onTimeEnabled) return emptyList()
        val byPerson = meds.groupBy { it.personId }
        val out = mutableListOf<Slot>()
        livingIds.forEach { pid ->
            val effective = prefsByPerson[pid]?.onTimeEnabled ?: (pid == selfId)
            if (!effective) return@forEach
            val dayMeds = byPerson[pid].orEmpty().filter { scheduledOn(it, today) }
            dayMeds.flatMap { it.times }.distinct().sorted().forEach { minute ->
                val atMinute = dayMeds.filter { minute in it.times }
                if (atMinute.isNotEmpty()) out += Slot(pid, minute, atMinute)
            }
        }
        return out.sortedWith(compareBy({ it.personId }, { it.minute }))
    }

    /**
     * 长辈首页「今天要吃的药」：只看“我”的药与日期规则，
     * 不看任何提醒开关（提醒关了计划照样显示）。
     */
    fun planSlots(
        meds: List<Medication>,
        selfId: String?,
        livingIds: Set<String>,
        today: LocalDate
    ): List<Slot> {
        if (selfId == null || selfId !in livingIds) return emptyList()
        val dayMeds = meds.filter {
            it.personId == selfId && scheduledOn(it, today)
        }
        return dayMeds.flatMap { it.times }.distinct().sorted().mapNotNull { minute ->
            val at = dayMeds.filter { minute in it.times }
            if (at.isEmpty()) null else Slot(selfId, minute, at)
        }
    }

    /**
     * 该槽的下一次触发时刻：今天该时刻未过 → 今天；已过 → 从明天起
     * 找第一个仍有药有效的日子，最多找 7 天，找不到返回 null。
     */
    fun nextTrigger(slot: Slot, now: ZonedDateTime): ZonedDateTime? {
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (slot.meds.none { scheduledOn(it, date) }) continue
            val trigger = ZonedDateTime.of(
                date,
                LocalTime.of(slot.minute / 60, slot.minute % 60),
                now.zone
            )
            if (offset == 0) {
                if (!trigger.isBefore(now)) return trigger
            } else {
                return trigger
            }
        }
        return null
    }

    /**
     * 通知正文。触发超过 60 分钟（含时钟漂移）返回 null 不发；
     * ≤5 分钟用“该吃药了”，5–60 分钟用“的药”（只写原定计划，不催促）。
     */
    fun deliveryText(
        personLabel: String?,
        isSelf: Boolean,
        minute: Int,
        meds: List<Medication>,
        scheduledAt: Instant,
        now: Instant
    ): String? {
        val diff = Duration.between(scheduledAt, now)
        if (diff > Duration.ofMinutes(60)) return null
        val time = "%02d:%02d".format(minute / 60, minute % 60)
        val list = drugListText(meds)
        val who = if (isSelf) "" else personLabel.orEmpty()
        return if (diff <= Duration.ofMinutes(5)) {
            "$time ${who}该吃药了：$list"
        } else {
            "$time ${who}的药：$list"
        }
    }

    /** 药名列表："{药名} {每次用量}"（没用量只写药名），疗程待确认加后缀，"、"连接。 */
    fun drugListText(meds: List<Medication>): String =
        meds.joinToString("、") { med ->
            buildString {
                append(med.name)
                med.dose?.takeIf { it.isNotBlank() }?.let { append(' ').append(it) }
                if (MedSchedule.coursePending(med)) append("（疗程待确认）")
            }
        }

    /**
     * 长辈首页高亮：按时刻升序的分钟表中，第一个 ≥ now−30 分钟的行下标；
     * 全部更早返回 -1（所有行都置灰）。
     */
    fun highlightIndex(minutes: List<Int>, nowMinuteOfDay: Int): Int {
        val threshold = nowMinuteOfDay - 30
        return minutes.indexOfFirst { it >= threshold }
    }
}
