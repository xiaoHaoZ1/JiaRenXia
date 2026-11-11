package io.github.xiaohaoz1.jiarenxia.ui.med

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.model.MedMeal
import io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
import io.github.xiaohaoz1.jiarenxia.core.model.Medication
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.MedicationDraft
import io.github.xiaohaoz1.jiarenxia.data.MedicationRepository
import io.github.xiaohaoz1.jiarenxia.domain.med.MedSchedule
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 每天服用时刻上限（规格：至多 4 个，重复忽略）。 */
const val MED_TIMES_MAX = 4

/** 医嘱备注上限（规格 ≤200 字）。 */
const val MED_NOTE_MAX = 200

data class MedEditUiState(
    val medId: String? = null,
    val personId: String = "",
    /** 已有记录（编辑态）；新建为 null。 */
    val existing: Medication? = null,
    /** 载入完成（编辑态等 get() 返回后置位）。 */
    val loaded: Boolean = false,
    val name: String = "",
    val purpose: String = "",
    val dose: String = "",
    val spec: String = "",
    /** 「怎么吃」：null = 未选（新建不预选）。 */
    val usage: MedUsage? = null,
    /** 短期疗程天数输入（文本，1..365）。 */
    val courseDaysText: String = "",
    /** 「疗程待确认」勾选：禁用天数输入、保存时 courseDays 记 null。 */
    val coursePending: Boolean = false,
    /** 长期吃的「下次拿药日」；null = 不填。 */
    val refillDate: LocalDate? = null,
    /** 记录的开始日期（默认今天）。 */
    val startDate: LocalDate = LocalDate.now(),
    /** 每天服用时刻（0..1439，已排序去重）。 */
    val times: List<Int> = emptyList(),
    /** 饭前/饭后/随餐；null = 不填。 */
    val meal: MedMeal? = null,
    val note: String = "",
    /** 「医生让继续吃」点过后卡内提示（编辑 COURSE_ENDED 时）。 */
    val courseEndedHint: Boolean = false,
    /** 长期用药确认弹窗待展示。 */
    val confirmLongTerm: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false
) {
    /** 保存按钮可点：药名非空、选了怎么吃；短期时天数合法或勾了待确认。 */
    val canSave: Boolean
        get() {
            if (name.isBlank() || usage == null) return false
            if (usage == MedUsage.SHORT_COURSE && !coursePending) {
                val n = courseDaysText.trim().toIntOrNull()
                if (n == null || n !in 1..365) return false
            }
            return true
        }
}

/**
 * 用药编辑页（FAMILY_AFFAIRS 23）：新建（personId 预填）或按 medId 载入。
 * 「长期吃」确认弹窗判定走 [MedSchedule.needsLongTermConfirm] 纯函数。
 */
@HiltViewModel
class MedEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val medicationRepository: MedicationRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(MedEditUiState())
    val ui: StateFlow<MedEditUiState> = _ui

    init {
        val medId = savedState.get<String>("medId")?.takeIf { it.isNotEmpty() }
        val personId = checkNotNull(savedState.get<String>("personId"))
        _ui.value = MedEditUiState(medId = medId, personId = personId)
        if (medId != null) {
            viewModelScope.launch {
                val med = medicationRepository.get(medId)
                if (med != null) {
                    _ui.value = MedEditUiState(
                        medId = med.id,
                        personId = personId,
                        existing = med,
                        loaded = true,
                        name = med.name,
                        purpose = med.purpose ?: "",
                        dose = med.dose ?: "",
                        spec = med.spec ?: "",
                        usage = med.usage,
                        courseDaysText = med.courseDays?.toString() ?: "",
                        coursePending = med.usage == MedUsage.SHORT_COURSE &&
                            med.courseDays == null,
                        refillDate = med.refillEpochDay
                            ?.let { LocalDate.ofEpochDay(it) },
                        startDate = LocalDate.ofEpochDay(med.startEpochDay),
                        times = med.times,
                        meal = med.meal,
                        note = med.note ?: ""
                    )
                } else {
                    _ui.value = _ui.value.copy(loaded = true)
                }
            }
        } else {
            _ui.value = _ui.value.copy(loaded = true)
        }
    }

    fun setName(v: String) { _ui.value = _ui.value.copy(name = v.take(30)) }
    fun setPurpose(v: String) { _ui.value = _ui.value.copy(purpose = v.take(30)) }
    fun setDose(v: String) { _ui.value = _ui.value.copy(dose = v.take(20)) }
    fun setSpec(v: String) { _ui.value = _ui.value.copy(spec = v.take(20)) }
    fun setNote(v: String) { _ui.value = _ui.value.copy(note = v.take(MED_NOTE_MAX)) }

    /** 「怎么吃」：切到需要时吃时清空时刻（AS_NEEDED 不存时刻）。 */
    fun setUsage(u: MedUsage?) {
        _ui.value = _ui.value.copy(
            usage = u,
            times = if (u == MedUsage.AS_NEEDED) emptyList() else _ui.value.times
        )
    }

    fun setCourseDaysText(v: String) {
        _ui.value = _ui.value.copy(courseDaysText = v.filter { it.isDigit() }.take(3))
    }

    fun setCoursePending(v: Boolean) {
        _ui.value = _ui.value.copy(coursePending = v)
    }

    fun setRefillDate(d: LocalDate?) {
        _ui.value = _ui.value.copy(refillDate = d)
    }

    fun setStartDate(d: LocalDate) {
        _ui.value = _ui.value.copy(startDate = d)
    }

    /** 添加服用时刻：重复忽略；已满 4 个时忽略。 */
    fun addTime(minute: Int) {
        val cur = _ui.value.times
        if (minute in cur || cur.size >= MED_TIMES_MAX) return
        _ui.value = _ui.value.copy(times = (cur + minute).sorted())
    }

    fun removeTime(minute: Int) {
        _ui.value = _ui.value.copy(times = _ui.value.times - minute)
    }

    fun setMeal(m: MedMeal?) {
        _ui.value = _ui.value.copy(meal = m)
    }

    /** 「记录已停用」：stopped_epoch_day = 今天。 */
    fun markStopped() {
        val id = _ui.value.medId ?: return
        viewModelScope.launch {
            when (medicationRepository.markStopped(id, LocalDate.now())) {
                is FamilyResult.Success -> Unit
                is FamilyResult.Failure ->
                    _ui.value = _ui.value.copy(error = "操作失败，请重试")
            }
        }
    }

    /** 「重新开始吃」：清除停用标记（不改开始日期）。 */
    fun clearStopped() {
        val id = _ui.value.medId ?: return
        viewModelScope.launch {
            when (medicationRepository.clearStopped(id)) {
                is FamilyResult.Success -> Unit
                is FamilyResult.Failure ->
                    _ui.value = _ui.value.copy(error = "操作失败，请重试")
            }
        }
    }

    /** 「医生让继续吃」：卡内提示，不改数据。 */
    fun showCourseEndedHint() {
        _ui.value = _ui.value.copy(courseEndedHint = true)
    }

    /** 保存：需要长期确认时先弹窗（confirmLongTerm），确认后走 [confirmLongTermSave]。 */
    fun save() {
        val st = _ui.value
        if (st.saving || st.saved || !st.loaded || !st.canSave) return
        if (MedSchedule.needsLongTermConfirm(st.existing?.usage, st.usage)) {
            _ui.value = st.copy(confirmLongTerm = true)
            return
        }
        doSave()
    }

    fun confirmLongTermSave() {
        _ui.value = _ui.value.copy(confirmLongTerm = false)
        doSave()
    }

    fun dismissLongTermConfirm() {
        _ui.value = _ui.value.copy(confirmLongTerm = false)
    }

    private fun doSave() {
        val st = _ui.value
        val usage = st.usage ?: return
        val courseDays = if (usage == MedUsage.SHORT_COURSE && !st.coursePending)
            st.courseDaysText.trim().toIntOrNull() else null
        _ui.value = st.copy(saving = true)
        viewModelScope.launch {
            try {
                val res = medicationRepository.save(
                    MedicationDraft(
                        id = st.medId,
                        personId = st.personId,
                        name = st.name,
                        dose = st.dose.ifBlank { null },
                        spec = st.spec.ifBlank { null },
                        meal = st.meal,
                        usage = usage,
                        courseDays = courseDays,
                        startEpochDay = st.startDate.toEpochDay(),
                        purpose = st.purpose.ifBlank { null },
                        note = st.note.ifBlank { null },
                        refillEpochDay = if (usage == MedUsage.LONG_TERM)
                            st.refillDate?.toEpochDay() else null,
                        times = if (usage == MedUsage.AS_NEEDED)
                            emptyList() else st.times
                    )
                )
                when (res) {
                    is FamilyResult.Success ->
                        _ui.value = _ui.value.copy(saved = true, error = null)
                    is FamilyResult.Failure ->
                        _ui.value = _ui.value.copy(error = res.error.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "保存失败，请重试")
            } finally {
                _ui.value = _ui.value.copy(saving = false)
            }
        }
    }

    fun delete() {
        val id = _ui.value.medId ?: return
        viewModelScope.launch {
            medicationRepository.delete(id)
            _ui.value = _ui.value.copy(deleted = true)
        }
    }

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null)
    }
}
