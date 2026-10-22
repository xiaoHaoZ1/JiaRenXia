package io.github.xiaohaoz1.jiarenxia.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonSearch
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val query: String = "",
    val results: List<PersonOverview> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    observeOverview: ObserveFamilyOverview
) : ViewModel() {

    private val query = MutableStateFlow("")

    val ui: StateFlow<SearchUiState> =
        combine(query, observeOverview()) { q, overview ->
            SearchUiState(
                query = q,
                results = PersonSearch.filter(overview?.byId?.values.orEmpty().toList(), q)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState())

    fun onQuery(q: String) { query.value = q }
}
