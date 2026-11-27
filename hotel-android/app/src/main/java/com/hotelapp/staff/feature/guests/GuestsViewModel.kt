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
    val endReached: Boolean = false,
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
    private var loadJob: Job? = null

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

    fun refresh(silent: Boolean = false) {
        debounceJob?.cancel()
        load(1, replace = true, silent = silent)
    }

    fun retry() {
        debounceJob?.cancel()
        load(1, replace = true, silent = false)
    }

    fun loadMore() {
        val s = _uiState.value
        if (s.isLoadingMore || s.endReached || s.items.size >= s.total) return
        load(s.page + 1, replace = false)
    }

    private fun load(page: Long, replace: Boolean, silent: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val queryAtDispatch = _uiState.value.search
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = replace && it.items.isEmpty(),
                    isRefreshing = replace && it.items.isNotEmpty(),
                    isLoadingMore = !replace,
                    endReached = if (replace) false else it.endReached,
                    error = null,
                )
            }
            repository.list(search = queryAtDispatch, page = page)
                .onSuccess { res ->
                    _uiState.update { cur ->
                        if (cur.search != queryAtDispatch) {
                            return@update cur.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                            )
                        }
                        val preservePagedScroll = silent && replace && cur.page > 1 && cur.items.isNotEmpty()
                        val nextItems = when {
                            preservePagedScroll -> {
                                val updatedById = res.data.associateBy { it.id }
                                val mergedHead = res.data
                                val preservedTail = cur.items.filter { it.id !in updatedById }
                                (mergedHead + preservedTail).distinctBy { it.id }
                            }
                            replace -> res.data
                            else -> (cur.items + res.data).distinctBy { it.id }
                        }
                        val nextPage = if (preservePagedScroll) cur.page else res.page
                        val reachedEnd = if (preservePagedScroll) {
                            nextItems.size >= res.total || cur.endReached
                        } else {
                            res.data.isEmpty() ||
                                nextItems.size >= res.total ||
                                (!replace && nextItems.size == cur.items.size)
                        }
                        cur.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            endReached = reachedEnd,
                            items = nextItems,
                            page = nextPage,
                            total = res.total,
                            error = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { cur ->
                        if (cur.search != queryAtDispatch) {
                            return@update cur.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                            )
                        }
                        cur.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = if (silent && cur.items.isNotEmpty()) cur.error else err.message,
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
    val refreshError: String? = null,
)

class GuestDetailViewModel(
    guestId: Long,
    private val repository: GuestsRepository,
) : ViewModel() {
    private var guestId: Long = guestId
    private val _uiState = MutableStateFlow(GuestDetailUiState())
    val uiState: StateFlow<GuestDetailUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun setGuestId(newId: Long) {
        if (newId == guestId && (_uiState.value.profile != null || _uiState.value.isLoading)) {
            return
        }
        guestId = newId
        _uiState.value = GuestDetailUiState()
        refresh()
    }

    fun refresh(silent: Boolean = false) {
        val targetId = guestId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = it.profile == null,
                    isRefreshing = it.profile != null,
                    error = null,
                    refreshError = null,
                )
            }
            repository.profile(targetId)
                .onSuccess { p ->
                    if (guestId != targetId) return@onSuccess
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            profile = p,
                            error = null,
                            refreshError = null,
                        )
                    }
                }
                .onFailure { err ->
                    if (guestId != targetId) return@onFailure
                    _uiState.update {
                        val msg = err.message ?: "Unable to load guest profile."
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = if (it.profile == null) msg else it.error,
                            refreshError = if (it.profile != null && !silent) msg else it.refreshError,
                        )
                    }
                }
        }
    }
}
