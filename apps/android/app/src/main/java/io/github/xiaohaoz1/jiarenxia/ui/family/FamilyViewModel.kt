package io.github.xiaohaoz1.jiarenxia.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 家庭页状态：整体视图 + 每人默认拨出号码 + 今天（用于生日倒计时）。 */
data class FamilyUiState(
    val overview: FamilyOverview? = null,
    val defaultNumbers: Map<String, String> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    /** personId → 已 N 天未联系（仅逾期者，联系提醒）。 */
    val contactOverdue: Map<String, Int> = emptyMap()
)

/** 家庭页 ViewModel：订阅家庭整体视图与默认号码表。 */
@HiltViewModel
class FamilyViewModel @Inject constructor(
    observeOverview: ObserveFamilyOverview,
    repository: FamilyRepository,
    val lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar,
    val appEvents: io.github.xiaohaoz1.jiarenxia.data.AppEvents,
    private val contactRepository: io.github.xiaohaoz1.jiarenxia.data
        .ContactRepository) : ViewModel() {

    val ui: StateFlow<FamilyUiState> =
        combine(
            combine(
                observeOverview(), repository.observeDefaultNumbers()
            ) { ov, nums -> ov to nums },
            contactRepository.observePlanList(),
            contactRepository.observeAllLogs()
        ) { (ov, nums), plans, logs ->
            val today = LocalDate.now()
            val logsByPerson = logs.groupBy { it.personId }
            val overdue = plans.mapNotNull { plan ->
                if (!io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
                        .eligible(ov?.byId?.get(plan.personId)?.person)
                ) return@mapNotNull null
                val status = io.github.xiaohaoz1.jiarenxia.domain
                    .ContactSchedule.status(
                        logsByPerson[plan.personId].orEmpty(),
                        plan.intervalDays, plan.createdAt, today)
                if (status.due) plan.personId to
                    (status.daysSinceLast ?: status.overdueDays +
                        plan.intervalDays) else null
            }.toMap()
            FamilyUiState(
                ov, nums, today, contactOverdue = overdue)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FamilyUiState())

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
}
