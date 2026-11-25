package com.hotelapp.staff.feature.guests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.data.dto.GuestDto
import com.hotelapp.staff.data.dto.GuestProfileDto
import com.hotelapp.staff.data.repository.GuestsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GuestsUiState(
    val search: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<GuestDto> = emptyList(),
    val page: Long = 1,
    val total: Long = 0,
    val error: String? = null,
)

class GuestsViewModel(
    private val repository: GuestsRepository,
    realtimeSocketClient: RealtimeSocketClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuestsUiState())
    val uiState: StateFlow<GuestsUiState> = _uiState.asStateFlow()

    private var debounceJob: Job? = null

    init {
        load(1, replace = true)
        viewModelScope.launch {
            realtimeSocketClient.domainEvents.collect { domain ->
                if (domain == "guests" || domain == "bookings") {
                    load(1, replace = true, silent = true)
                }
            }
        }
    }

    fun setSearch(query: String) {
        _uiState.update { it.copy(search = query) }
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(320)
            load(1, replace = true)
        }
    }

    fun refresh(silent: Boolean = false) = load(1, replace = true, silent = silent)

    fun loadMore() {
        val s = _uiState.value
        if (s.isLoadingMore || s.items.size >= s.total) return
        load(s.page + 1, replace = false)
    }

    private fun load(page: Long, replace: Boolean, silent: Boolean = false) {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = replace && it.items.isEmpty(),
                    isRefreshing = replace && it.items.isNotEmpty(),
                    isLoadingMore = !replace,
                    error = null,
                )
            }
            repository.list(search = s.search, page = page)
                .onSuccess { res ->
                    _uiState.update { cur ->
                        cur.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            items = if (replace) res.data else (cur.items + res.data).distinctBy { it.id },
                            page = res.page,
                            total = res.total,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = if (silent && it.items.isNotEmpty()) it.error else err.message,
                        )
                    }
                }
        }
    }
}

data class GuestDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val profile: GuestProfileDto? = null,
    val error: String? = null,
)

class GuestDetailViewModel(
    private val guestId: Long,
    private val repository: GuestsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GuestDetailUiState())
    val uiState: StateFlow<GuestDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = it.profile == null,
                    isRefreshing = it.profile != null,
                    error = null,
                )
            }
            repository.profile(guestId)
                .onSuccess { p ->
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, profile = p, error = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = if (it.profile == null) err.message else it.error,
                        )
                    }
                }
        }
    }
}
