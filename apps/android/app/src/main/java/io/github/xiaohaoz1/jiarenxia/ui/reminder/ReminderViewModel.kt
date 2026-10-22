package io.github.xiaohaoz1.jiarenxia.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.domain.AffairTimeline
import io.github.xiaohaoz1.jiarenxia.domain.DateRow
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.reminder.BirthdayRow
import io.github.xiaohaoz1.jiarenxia.reminder.TaskSchedule
import io.github.xiaohaoz1.jiarenxia.reminder.UpcomingBirthdays
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 家事页一条事项行：事项 + 当期日期/倒计时 + 关联人展示名。 */
data class TaskRow(
    val task: FamilyTask,
    val dueDate: LocalDate,
    /** 距今天天数；负数为逾期。 */
    val daysUntil: Int,
    /** 关联人的称谓（或姓名）胶囊；null = 全家的事。 */
    val personLabel: String?
)

data class ReminderUiState(
    val loading: Boolean = true,
    val remindersEnabled: Boolean = true,
    val taskRemindersEnabled: Boolean = true,
    val today: List<BirthdayRow> = emptyList(),
    val upcoming: List<BirthdayRow> = emptyList(),
    /** 未来 30 天的纪念日/忌日（并入时间线）。 */
    val dateRows: List<DateRow> = emptyList(),
    /** 今天发生的纪念日/忌日。 */
    val dateRowsToday: List<DateRow> = emptyList(),
    val defaultNumbers: Map<String, String> = emptyMap(),
    /** 今天要办（含逾期，逾期置顶）；未来 30 天；以后；已办。 */
    val tasksToday: List<TaskRow> = emptyList(),
    /** 今天该联系的家人（逾期的联系提醒，联系提醒）。 */
    val contactDue: List<ContactDueRow> = emptyList(),
    val tasksNext30: List<TaskRow> = emptyList(),
    val tasksLater: List<TaskRow> = emptyList(),
    val tasksDone: List<TaskRow> = emptyList()
)

/** 家事页：生日提醒 + 家庭事项（DEVELOPMENT_PLAN 2.5、FAMILY_AFFAIRS 5）。 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val repository: FamilyRepository,
    private val taskRepository: FamilyTaskRepository,
    private val settings: SettingsRepository,
    private val observeOverview: ObserveFamilyOverview,
    private val lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar,
    val appEvents: io.github.xiaohaoz1.jiarenxia.data.AppEvents,
    private val contactRepository: io.github.xiaohaoz1.jiarenxia.data
        .ContactRepository) : ViewModel() {

    /** 已办撤销：记下被改掉的事项原状，撤销时整体写回。 */
    private var lastDoneSnapshot: FamilyTask? = null

    /** Snackbar 消息 + 是否需要“撤销”按钮。 */
    val snack = MutableStateFlow<Pair<String, Boolean>?>(null)

    val ui: StateFlow<ReminderUiState> = combine(
        repository.observeBirthdayEntries(),
        observeOverview(),
        settings.remindersEnabled,
        settings.taskRemindersEnabled,
        repository.observeDefaultNumbers()
    ) { entries, overview, enabled, tasksEnabled, numbers ->
        val titles = overview?.byId?.mapValues { (_, o) ->
            val k = o.result.kind
            if (k == KinshipKind.UNRELATED || k == KinshipKind.SELF) null else o.displayTitle
        } ?: emptyMap()
        val names = overview?.byId?.mapValues { (_, o) -> o.person.name } ?: emptyMap()
        val res = UpcomingBirthdays.build(entries, titles, LocalDate.now(), lunar)
        val dates = repository.getDateEntries()
        val (dToday, dNext30) = AffairTimeline.dateRows(dates, titles, LocalDate.now(), lunar)
        ReminderUiState(
            loading = false,
            remindersEnabled = enabled,
            taskRemindersEnabled = tasksEnabled,
            today = res.today,
            upcoming = res.upcoming,
            dateRows = dNext30,
            dateRowsToday = dToday,
            defaultNumbers = numbers
        ) to Triple(titles, names, overview)
    }.flatMapLatest { (base, aux) ->
        val (titles, names, overview) = aux
        taskRepository.observeTasks().map { tasks ->
            val today = LocalDate.now()
            val rows = tasks.map { t ->
                TaskRow(
                    task = t,
                    dueDate = TaskSchedule.dueDate(t),
                    daysUntil = TaskSchedule.daysUntil(t, today),
                    personLabel = t.personId?.let { pid ->
                        if (pid == overview?.self?.person?.id) "我"
                        else titles[pid] ?: names[pid]
                    }
                )
            }.sortedBy { it.dueDate }
            val undone = rows.filter { !it.task.done }
            // overview 随状态链下传：联系提醒复用同一份，不再二次订阅
            base.copy(
                tasksToday = undone.filter { it.dueDate <= today },
                tasksNext30 = undone.filter { it.dueDate > today && it.dueDate <= today.plusDays(30) },
                tasksLater = undone.filter { it.dueDate > today.plusDays(30) },
                tasksDone = rows.filter { it.task.done }.sortedByDescending { it.task.lastDoneDate }
            ) to overview
        }
    }.combine(
        contactRepository.observePlanList()
            .combine(contactRepository.observeAllLogs()) { p, l -> p to l }
    ) { (st, ov), (plans, logs) ->
        val today = LocalDate.now()
        val logsBy = logs.groupBy { it.personId }
        val titles2 = ov?.byId?.mapValues { (_, o) ->
            val k = o.result.kind
            if (k == KinshipKind.UNRELATED || k == KinshipKind.SELF) null
            else o.displayTitle
        }.orEmpty()
        st.copy(
            contactDue = plans.mapNotNull { plan ->
                if (!io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
                        .eligible(ov?.byId?.get(plan.personId)?.person)
                ) return@mapNotNull null
                val status = io.github.xiaohaoz1.jiarenxia.domain
                    .ContactSchedule.status(
                        logsBy[plan.personId].orEmpty(),
                        plan.intervalDays, plan.createdAt, today)
                if (!status.due) return@mapNotNull null
                val label = titles2[plan.personId]
                    ?: ov?.byId?.get(plan.personId)?.person?.name
                    ?: return@mapNotNull null
                ContactDueRow(
                    personId = plan.personId,
                    label = label,
                    daysSinceLast = status.daysSinceLast,
                    overdueDays = status.overdueDays)
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderUiState())

    /** “已办”：重复事项推进到下期，不重复进已办；发 Snackbar 供撤销。 */
    fun markDone(row: TaskRow) {
        val task = row.task
        lastDoneSnapshot = task
        viewModelScope.launch {
            when (taskRepository.markDone(task.id, LocalDate.now())) {
                is FamilyResult.Success -> snack.value = "已标记办完" to true
                is FamilyResult.Failure -> snack.value = "保存失败，请重试" to false
            }
        }
    }

    /** 撤销上一次“已办”。 */
    fun undoDone() {
        val snapshot = lastDoneSnapshot ?: return
        lastDoneSnapshot = null
        viewModelScope.launch {
            when (taskRepository.restore(snapshot)) {
                is FamilyResult.Success -> snack.value = null
                is FamilyResult.Failure ->
                    snack.value = "事项已删除，无法撤销" to false
            }
        }
    }

    fun snackConsumed() {
        snack.value = null
    }

    // ---------- 导出 ----------

    /** 提醒导出结果：csv 文本 + 数据行数（0 时界面提示“已导出表头”）。 */
    data class RemindersCsvResult(val csv: String, val rowCount: Int)

    /**
     * 家事提醒 CSV：[from, to] 公历范围内的生日/纪念日/忌日 + 家庭事项；
     * [includeOverdue] 另收未完成逾期事项（不受范围限制）。
     */
    suspend fun buildRemindersCsv(
        from: LocalDate, to: LocalDate, includeOverdue: Boolean
    ): RemindersCsvResult {
        val overview = observeOverview().first()
        val selfId = overview?.self?.person?.id
        // 查不到的人物返回 null：导出层回退到条目里的人物姓名
        val labelOf: (String) -> String? = { pid ->
            if (pid == selfId) "我" else overview?.byId?.get(pid)?.displayTitle
        }
        val rows = io.github.xiaohaoz1.jiarenxia.domain.export.ReminderExport.build(
            birthdays = repository.getBirthdayEntries(),
            dates = repository.getDateEntries(),
            tasks = taskRepository.getTasks(),
            labelOf = labelOf,
            from = from, to = to, includeOverdue = includeOverdue,
            today = LocalDate.now(), lunar = lunar
        )
        return RemindersCsvResult(
            io.github.xiaohaoz1.jiarenxia.domain.export.CsvExport.remindersCsv(rows),
            rows.size
        )
    }

    /** 拨给家人：记下待提示（10 分钟内回 App 弹“联系上了吗”）。 */
    fun onDialMade(person: io.github.xiaohaoz1.jiarenxia.core.model.Person,
        label: String) {
        appEvents.pendingContactPrompt =
            io.github.xiaohaoz1.jiarenxia.data.ContactPrompt(
                person.id, label, System.currentTimeMillis())
    }

    /** “联系上了”：按拨号时刻记一条电话类型的联系。 */
    fun logCallContact(prompt: io.github.xiaohaoz1.jiarenxia.data.ContactPrompt) {
        viewModelScope.launch {
            contactRepository.logContact(
                prompt.personId,
                io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod.CALL,
                null, prompt.atMillis)
        }
    }

    /** 时间线“记一次”：自选方式与备注。 */
    fun logContactNow(
        personId: String,
        method: io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod,
        note: String?
    ) {
        viewModelScope.launch {
            contactRepository.logContact(
                personId, method, note, System.currentTimeMillis())
        }
    }

}

/** 联系提醒时间线条目（“今天”分组）。 */
data class ContactDueRow(
    val personId: String,
    /** 称谓或姓名（“该联系 X 了”用）。 */
    val label: String,
    /** 距上次联系的天数；从未联系为 null。 */
    val daysSinceLast: Int?,
    val overdueDays: Int
)
