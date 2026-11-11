package io.github.xiaohaoz1.jiarenxia.ui.med

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.Medication
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.MedicationRepository
import io.github.xiaohaoz1.jiarenxia.domain.AgeMath
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class MedDoctorUiState(
    val loading: Boolean = true,
    val personName: String = "",
    /** 年龄文案（“62 岁”/“出生第 N 天”）；无生日为 null。 */
    val ageText: String? = null,
    /** 未停用的用药（含 COURSE_ENDED；新的在前）。 */
    val medications: List<Medication> = emptyList()
)

/**
 * 「给医生看」页数据（FAMILY_AFFAIRS 23）：只读摘要，
 * 不出现“已经吃了 N 天”“已停药”等服药事实。
 */
@HiltViewModel
class MedDoctorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: FamilyRepository,
    medicationRepository: MedicationRepository,
    observeOverview: ObserveFamilyOverview,
    lunar: LunarCalendar
) : ViewModel() {

    private val personId: String = checkNotNull(savedState["personId"])
    private val _ui = MutableStateFlow(MedDoctorUiState())
    val ui: StateFlow<MedDoctorUiState> = _ui

    init {
        viewModelScope.launch {
            combine(
                repository.observePersonDetail(personId),
                medicationRepository.observeForPerson(personId),
                observeOverview(),
                repository.observeDateEntries()
            ) { detail, meds, overview, dates ->
                MedDoctorUiState(
                    loading = false,
                    personName = detail?.person?.name ?: "",
                    ageText = detail?.person?.let { p ->
                        AgeMath.ageText(
                            overview?.byId?.get(personId)?.birthdays
                                ?: detail.birthdays.values.map { it.value },
                            p.livingStatus,
                            dates.firstOrNull {
                                it.type == ImportantDateType.DEATH_ANNIVERSARY &&
                                    it.person.id == personId
                            }?.date,
                            LocalDate.now(),
                            lunar
                        )
                    },
                    medications = meds.filter { it.stoppedEpochDay == null }
                )
            }.collect { _ui.value = it }
        }
    }
}
