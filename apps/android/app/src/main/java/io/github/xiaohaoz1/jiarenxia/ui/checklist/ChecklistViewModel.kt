package io.github.xiaohaoz1.jiarenxia.ui.checklist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.model.ChecklistItem
import io.github.xiaohaoz1.jiarenxia.data.ChecklistRepository
import io.github.xiaohaoz1.jiarenxia.data.ChecklistWithItemsModel
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChecklistUiState(
    val loading: Boolean = true,
    val model: ChecklistWithItemsModel? = null,
    /** 一次性提示（删除条目等）。 */
    val notice: String? = null,
    /** 删除整张清单成功 → 页面返回。 */
    val deleted: Boolean = false,
    val error: String? = null
)

/** 清单页 ViewModel（FAMILY_AFFAIRS 26：待产包等清单）。 */
@HiltViewModel
class ChecklistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChecklistRepository
) : ViewModel() {

    private val checklistId: String = checkNotNull(savedStateHandle["checklistId"])

    private val _ui = MutableStateFlow(ChecklistUiState())
    val ui: StateFlow<ChecklistUiState> = _ui

    init {
        viewModelScope.launch {
            repository.observe(checklistId).collect { model ->
                _ui.update {
                    it.copy(loading = false, model = model)
                }
            }
        }
    }

    fun setChecked(itemId: String, checked: Boolean) {
        viewModelScope.launch {
            val r = repository.setChecked(itemId, checked)
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    fun addItem(groupName: String, title: String) {
        viewModelScope.launch {
            val r = repository.addItem(checklistId, groupName, title)
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    fun renameItem(itemId: String, title: String) {
        viewModelScope.launch {
            val r = repository.renameItem(itemId, title)
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    /** 删除条目：不二次确认；成功后由页面弹“已删除「标题」”。 */
    fun deleteItem(item: ChecklistItem) {
        viewModelScope.launch {
            val r = repository.deleteItem(item.id)
            when (r) {
                is FamilyResult.Success -> _ui.update {
                    it.copy(notice = "已删除「" + item.title + "」")
                }
                is FamilyResult.Failure -> _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    /** 删除整张清单（含全部条目），成功后页面返回。 */
    fun deleteChecklist() {
        viewModelScope.launch {
            val r = repository.deleteChecklist(checklistId)
            when (r) {
                is FamilyResult.Success -> _ui.update { it.copy(deleted = true) }
                is FamilyResult.Failure -> _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    fun onNoticeShown() = _ui.update { it.copy(notice = null) }
    fun onErrorShown() = _ui.update { it.copy(error = null) }
}
