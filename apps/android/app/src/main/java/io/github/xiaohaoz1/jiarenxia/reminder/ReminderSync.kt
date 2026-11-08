package io.github.xiaohaoz1.jiarenxia.reminder

import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 提醒同步器（DEVELOPMENT_PLAN 2.5）：应用启动时 reconcile 一次，
 * 随后每当生日/纪念日数据、事项数据、联系计划与记录、四类开关、
 * 各自的提醒时刻或上坟日子/名单设置变化（防抖 500ms）重新调度。
 * WorkManager 会跨重启保持任务，无需 BOOT 广播。
 */
@Singleton
class ReminderSync @Inject constructor(
    private val scheduler: ReminderScheduler,
    private val repository: FamilyRepository,
    private val taskRepository: io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepository,
    private val contactRepository: io.github.xiaohaoz1.jiarenxia.data.ContactRepository,
    private val homeVisitRepository: io.github.xiaohaoz1.jiarenxia.data
        .HomeVisitRepository,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            scheduler.reconcile()
        }
        scope.launch {
            // 数据侧：生日、纪念日/忌日、家庭事项、联系计划与联系记录、回家看看计划
            val dataWatch = combine<Any?, DataWatch>(
                repository.observeBirthdayEntries(),
                repository.observeDateEntries(),
                taskRepository.observeTasks(),
                contactRepository.observePlanList(),
                contactRepository.observeAllLogs(),
                homeVisitRepository.observeHomes()
            ) { arr ->
                @Suppress("UNCHECKED_CAST")
                DataWatch(
                    arr[0] as List<io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry>,
                    arr[1] as List<io.github.xiaohaoz1.jiarenxia.data.DateEntry>,
                    arr[2] as List<io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask>,
                    arr[3] as List<io.github.xiaohaoz1.jiarenxia.data.ContactPlan>,
                    arr[4] as List<io.github.xiaohaoz1.jiarenxia.core.model.ContactLog>,
                    arr[5] as List<io.github.xiaohaoz1.jiarenxia.data.HomePlan>
                )
            }
            // 设置侧：四个总开关、四个提醒时刻、上坟日子/名单与回家节前提醒开关
            val settingsWatch = combine<Any?, SettingsWatch>(
                settings.remindersEnabled,
                settings.taskRemindersEnabled,
                settings.contactRemindersEnabled,
                settings.tombRemindersEnabled,
                settings.reminderMinuteOfDay,
                settings.taskReminderMinuteOfDay,
                settings.contactReminderMinuteOfDay,
                settings.tombReminderMinuteOfDay,
                settings.tombDays,
                settings.tombZhongyuanDay,
                settings.tombExcludedPersonIds,
                settings.tombIncludedPersonIds,
                settings.homeVisitHolidayNudge
            ) { v ->
                SettingsWatch(
                    v[0] as Boolean, v[1] as Boolean, v[2] as Boolean,
                    v[3] as Boolean,
                    v[4] as Int, v[5] as Int, v[6] as Int, v[7] as Int,
                    (v[8] as Set<*>).filterIsInstance<io.github.xiaohaoz1.jiarenxia
                        .domain.tomb.TombDay>().toSet(),
                    v[9] as Int,
                    (v[10] as Set<*>).filterIsInstance<String>().toSet(),
                    (v[11] as Set<*>).filterIsInstance<String>().toSet(),
                    v[12] as Boolean
                )
            }
            combine(dataWatch, settingsWatch) { d, s -> ReminderWatch(d, s) }
                .distinctUntilChanged()
                .debounce(500)
                .collectLatest { scheduler.reconcile() }
        }
    }

    private data class DataWatch(
        val birthdays: List<io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry>,
        val dates: List<io.github.xiaohaoz1.jiarenxia.data.DateEntry>,
        val tasks: List<io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask>,
        val contactPlans: List<io.github.xiaohaoz1.jiarenxia.data.ContactPlan>,
        val contactLogs: List<io.github.xiaohaoz1.jiarenxia.core.model.ContactLog>,
        val homeVisits: List<io.github.xiaohaoz1.jiarenxia.data.HomePlan>
    )

    private data class SettingsWatch(
        val remindersEnabled: Boolean,
        val taskRemindersEnabled: Boolean,
        val contactRemindersEnabled: Boolean,
        val tombRemindersEnabled: Boolean,
        val birthdayMinute: Int,
        val taskMinute: Int,
        val contactMinute: Int,
        val tombMinute: Int,
        val tombDays: Set<io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay>,
        val tombZhongyuanDay: Int,
        val tombExcludedIds: Set<String>,
        val tombIncludedIds: Set<String>,
        val homeVisitHolidayNudge: Boolean
    )

    private data class ReminderWatch(val data: DataWatch, val settings: SettingsWatch)
}
