package com.hotelapp.staff.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.data.dto.SearchGroupDto
import com.hotelapp.staff.data.repository.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val selectedType: String = "all",
    val isLoading: Boolean = false,
    val groups: List<SearchGroupDto> = emptyList(),
    val error: String? = null,
    val hasSearched: Boolean = false,
)

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val serverConfigStore: ServerConfigStore?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value, error = null) }
        searchJob?.cancel()
        if (value.trim().length < 2) {
            _uiState.update { it.copy(isLoading = false, groups = emptyList(), hasSearched = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(280)
            executeSearch(saveHistory = false)
        }
    }

    fun setType(type: String) {
        if (_uiState.value.selectedType == type) return
        _uiState.update { it.copy(selectedType = type) }
        searchJob?.cancel()
        if (_uiState.value.query.trim().length >= 2) {
            searchJob = viewModelScope.launch { executeSearch(saveHistory = false) }
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        if (_uiState.value.query.trim().length < 2) return
        searchJob = viewModelScope.launch { executeSearch(saveHistory = true) }
    }

    fun applyRecentQuery(query: String) {
        _uiState.update { it.copy(query = query, error = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { executeSearch(saveHistory = false) }
    }

    fun saveCurrentSearchToHistory() {
        val trimmed = _uiState.value.query.trim()
        if (trimmed.length < 2) return
        viewModelScope.launch {
            runCatching { serverConfigStore?.addRecentSearch(trimmed) }
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            serverConfigStore?.clearRecentSearches()
        }
    }

    private suspend fun executeSearch(saveHistory: Boolean) {
        val snapshot = _uiState.value
        val trimmed = snapshot.query.trim()
        val requestedType = snapshot.selectedType
        if (trimmed.length < 2) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        searchRepository.search(
            query = trimmed,
            types = requestedType.takeIf { it != "all" },
            limit = 20,
        ).onSuccess { resp ->
            val current = _uiState.value
            if (current.query.trim() != trimmed || current.selectedType != requestedType) {
                return@onSuccess
            }
            if (saveHistory) {
                runCatching { serverConfigStore?.addRecentSearch(trimmed) }
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    groups = resp.groups.filter { g -> g.results.isNotEmpty() },
                    error = null,
                    hasSearched = true,
                )
            }
        }.onFailure { err ->
            val current = _uiState.value
            if (current.query.trim() != trimmed || current.selectedType != requestedType) {
                return@onFailure
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = err.message ?: "Search failed.",
                    hasSearched = true,
                )
            }
        }
    }
}
