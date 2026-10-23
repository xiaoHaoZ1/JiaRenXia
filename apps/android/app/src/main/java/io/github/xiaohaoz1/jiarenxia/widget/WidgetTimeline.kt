package io.github.xiaohaoz1.jiarenxia.widget

import android.content.Context
import android.os.UserManager
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.ContactLog
import io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask
import io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry
import io.github.xiaohaoz1.jiarenxia.data.ContactPlan
import io.github.xiaohaoz1.jiarenxia.data.DateEntry
import io.github.xiaohaoz1.jiarenxia.domain.AffairTimeline
import io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.anniversary.CoupleCounter
import io.github.xiaohaoz1.jiarenxia.domain.anniversary.CoupleCounter.CoupleLine
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.reminder.TaskSchedule
import io.github.xiaohaoz1.jiarenxia.reminder.UpcomingBirthdays
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** 插件行类型（决定左侧色条颜色）。 */
enum class WidgetItemKind { BIRTHDAY, ANNIVERSARY, MEMORIAL, CONTACT, TASK }

/** 桌面插件时间线一行。 */
data class WidgetItem(
    val key: String,
    val kind: WidgetItemKind,
    /** 主文案：“奶奶生日”/“换驾驶证”/“该联系老爸了”。 */
    val title: String,
    /** 副文案：周年/农历原文/关联人/联系间隔，可为空。 */
    val note: String?,
    /** 距今天天数；负数为逾期。 */
    val daysUntil: Int,
    /** 点击深链：personId → 人物详情；taskId → 事项编辑；都空 → 家事页。 */
    val personId: String? = null,
    val taskId: String? = null,
)

/** 插件数据状态。 */
sealed interface WidgetState {
    /** 未解锁（Direct Boot）：Room 属凭据保护存储，显示占位。 */
    data object Locked : WidgetState
    /** 近期没有事项。 */
    data object Empty : WidgetState
    /** 读取异常兜底。 */
    data object Error : WidgetState
    data class Ready(val items: List<WidgetItem>, val couple: CoupleLine? = null) : WidgetState
}

/**
 * 插件时间线组装：与家事页同口径（生日 + 纪念日/忌日 + 家事 + 联系提醒，
 * 今天到未来 30 天，逾期事项置顶）。[assemble] 为纯函数便于单测。
 */
object WidgetTimeline {

    /** RemoteViews 列表不宜过长。 */
    const val MAX_ROWS = 30

    suspend fun load(context: Context): WidgetState {
        val unlocked = context.getSystemService(UserManager::class.java)
            ?.isUserUnlocked != false
        if (!unlocked) return WidgetState.Locked
        return try {
            val ep = WidgetEntryPoint.of(context)
            assemble(
                birthdays = ep.familyRepository().getBirthdayEntries(),
                dates = ep.familyRepository().getDateEntries(),
                tasks = ep.taskRepository().getTasks(),
                plans = ep.contactRepository().observePlanList().first(),
                logs = ep.contactRepository().observeAllLogs().first(),
                overview = ep.observeOverview().invoke().first(),
                today = LocalDate.now(),
                lunar = ep.lunarCalendar(),
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            WidgetState.Error
        }
    }

    fun assemble(
        birthdays: List<BirthdayEntry>,
        dates: List<DateEntry>,
        tasks: List<FamilyTask>,
        plans: List<ContactPlan>,
        logs: List<ContactLog>,
        overview: FamilyOverview?,
        today: LocalDate,
        lunar: LunarCalendar,
    ): WidgetState {
        val titles = overview?.byId?.mapValues { (_, o) ->
            val k = o.result.kind
            if (k == KinshipKind.UNRELATED || k == KinshipKind.SELF) null
            else o.displayTitle
        }.orEmpty()
        val names = overview?.byId?.mapValues { it.value.person.name }.orEmpty()
        val selfId = overview?.self?.person?.id
        val window = UpcomingBirthdays.WINDOW_DAYS

        val items = mutableListOf<WidgetItem>()

        // 生日（同一人阳历/农历落同一公历日的已合并为一行）
        val bRes = UpcomingBirthdays.build(birthdays, titles, today, lunar)
        for (row in bRes.today + bRes.upcoming) {
            items += WidgetItem(
                key = "b:" + row.person.id,
                kind = WidgetItemKind.BIRTHDAY,
                title = (row.title ?: row.person.name) + "生日",
                note = if (row.merged) "双历同日" else row.lunarText,
                daysUntil = row.daysUntil,
                personId = row.person.id,
            )
        }

        // 纪念日 / 忌日
        val (dToday, dNext) = AffairTimeline.dateRows(dates, titles, today, lunar, overview)
        for (row in dToday + dNext) {
            items += WidgetItem(
                key = "d:" + row.personId + ":" + row.kind + ":" + row.occurrence + ":" + (row.milestoneDays ?: 0),
                kind = if (row.kind.isCoupleShared) {
                    WidgetItemKind.ANNIVERSARY
                } else {
                    WidgetItemKind.MEMORIAL
                },
                title = row.label,
                note = listOfNotNull(row.yearLabel, row.lunarText)
                    .joinToString(" · ").ifEmpty { null },
                daysUntil = row.daysUntil,
                personId = row.personId,
            )
        }

        // 联系提醒：到期未联系的家人（均归入“今天”）
        val logsBy = logs.groupBy { it.personId }
        for (plan in plans) {
            val person = overview?.byId?.get(plan.personId)?.person
            if (!ContactSchedule.eligible(person)) continue
            val status = ContactSchedule.status(
                logsBy[plan.personId].orEmpty(), plan.intervalDays,
                plan.createdAt, today)
            if (!status.due) continue
            val label = titles[plan.personId] ?: names[plan.personId] ?: continue
            items += WidgetItem(
                key = "c:" + plan.personId,
                kind = WidgetItemKind.CONTACT,
                title = "该联系" + label + "了",
                note = status.daysSinceLast?.let { "已经 " + it + " 天没联系" }
                    ?: "还没联系过",
                daysUntil = 0,
                personId = plan.personId,
            )
        }

        // 家事任务：未办且当期落在窗口内（逾期的 daysUntil<0 排在最前）
        for (t in tasks) {
            if (t.done) continue
            val days = TaskSchedule.daysUntil(t, today)
            if (days > window) continue
            val label = t.personId?.let { pid ->
                if (pid == selfId) "我" else titles[pid] ?: names[pid]
            }
            items += WidgetItem(
                key = "t:" + t.id,
                kind = WidgetItemKind.TASK,
                title = t.title,
                note = label,
                daysUntil = days,
                taskId = t.id,
            )
        }

        val couple = CoupleCounter.counter(dates, overview, today, lunar)
        if (items.isEmpty() && couple == null) return WidgetState.Empty
        items.sortWith(compareBy({ it.daysUntil }, { it.kind.ordinal }, { it.title }))
        return WidgetState.Ready(items.take(MAX_ROWS), couple)
    }
}
