package io.github.xiaohaoz1.jiarenxia.ui.elder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.domain.AffairTimeline
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.elder.ElderHome
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombCalendar
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombPrefs
import io.github.xiaohaoz1.jiarenxia.reminder.UpcomingBirthdays
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 长辈首页状态：页头行 + 最近的日子 + 家人名单 + 默认号码表。 */
data class ElderUiState(
    val loading: Boolean = true,
    /** “11月7日 星期六 · 农历十月廿七”。 */
    val headerLine: String = "",
    val upcoming: List<ElderHome.UpcomingRow> = emptyList(),
    val members: List<ElderHome.MemberRow> = emptyList(),
    /** 人物 id → 默认号码（“打电话”按钮用）。 */
    val defaultNumbers: Map<String, String> = emptyMap()
)

/**
 * 长辈模式首页 ViewModel：合成页头、最近 30 天的日子（生日/纪念日/上坟）与家人名单。
 * 上坟日仅在上坟提醒总开关开启时产出（窗口与家事页一致，今天起 30 天）。
 */
@HiltViewModel
class ElderViewModel @Inject constructor(
    private val repository: FamilyRepository,
    private val settings: SettingsRepository,
    private val observeOverview: ObserveFamilyOverview,
    private val lunar: LunarCalendar
) : ViewModel() {

    /** 上坟设置快照（与 ReminderViewModel 同口径）。 */
    private data class TombPrefSnapshot(
        val enabled: Boolean,
        val days: Set<TombDay>,
        val zhongyuanDay: Int,
        val excludedIds: Set<String>
    )

    private val tombPrefs = combine(
        settings.tombRemindersEnabled,
        settings.tombDays,
        settings.tombZhongyuanDay,
        settings.tombExcludedPersonIds
    ) { en, days, zy, ex -> TombPrefSnapshot(en, days, zy, ex) }

    val ui: StateFlow<ElderUiState> = combine(
        repository.observeBirthdayEntries(),
        observeOverview(),
        repository.observeDefaultNumbers(),
        tombPrefs
    ) { entries, overview, numbers, tp ->
        val today = LocalDate.now()
        val titles = overview?.byId?.mapValues { (_, o) ->
            val k = o.result.kind
            if (k == io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind.UNRELATED ||
                k == io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind.SELF
            ) null else o.displayTitle
        } ?: emptyMap()
        val birthdays = UpcomingBirthdays.build(entries, titles, today, lunar)
        val dates = repository.getDateEntries()
        val (dToday, dNext30) = AffairTimeline.dateRows(
            dates, titles, today, lunar, overview)
        val tomb = if (tp.enabled) TombCalendar.events(
            today = today,
            windowDays = 30,
            lunar = lunar,
            mourners = TombCalendar.mourners(overview),
            deathEntries = dates,
            prefs = TombPrefs(tp.days, tp.zhongyuanDay, tp.excludedIds)
        ) else emptyList()
        ElderUiState(
            loading = false,
            headerLine = ElderHome.headerLine(today, lunar),
            upcoming = ElderHome.upcoming(
                birthdays = birthdays,
                dates = dToday to dNext30,
                tomb = tomb,
                today = today
            ),
            members = ElderHome.members(overview),
            defaultNumbers = numbers
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ElderUiState())

    /** 退出长辈模式（页底部长按 3 秒触发）。 */
    fun exitElderMode() {
        viewModelScope.launch { settings.setElderMode(false) }
    }
}
