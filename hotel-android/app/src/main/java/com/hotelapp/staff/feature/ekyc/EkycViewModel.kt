package com.hotelapp.staff.feature.ekyc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.data.dto.EkycApplicationDetailDto
import com.hotelapp.staff.data.dto.EkycApplicationSummaryDto
import com.hotelapp.staff.data.dto.EkycDashboardMetricsDto
import com.hotelapp.staff.data.dto.EkycReasonCodeDto
import com.hotelapp.staff.data.dto.EkycReviewActionRequestDto
import com.hotelapp.staff.data.repository.EkycRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface EkycEffect {
    data class ShowMessage(val message: String, val isError: Boolean = false) : EkycEffect
}

data class EkycUiState(
    val statusFilter: String = "all",
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val isMutating: Boolean = false,
    val metrics: EkycDashboardMetricsDto = EkycDashboardMetricsDto(),
    val items: List<EkycApplicationSummaryDto> = emptyList(),
    val page: Long = 1,
    val total: Long = 0,
    val selectedDetail: EkycApplicationDetailDto? = null,
    val reasonCodes: List<EkycReasonCodeDto> = emptyList(),
    val error: String? = null,
    val sheetError: String? = null,
)

class EkycViewModel(
    session: StaffSession,
    private val repository: EkycRepository,
    realtimeSocketClient: RealtimeSocketClient? = null,
) : ViewModel() {

    private val canReview = session.hasPermission("ekyc:review")

    private val _uiState = MutableStateFlow(EkycUiState())
    val uiState: StateFlow<EkycUiState> = _uiState.asStateFlow()

    private val _effects = Channel<EkycEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var debounceJob: Job? = null

    init {
        refresh()
        if (canReview) {
            loadReasonCodes()
        }
        if (realtimeSocketClient != null) {
            viewModelScope.launch {
                realtimeSocketClient.domainEvents.collect { domain ->
                    if (domain == "ekyc") {
                        refresh(silent = true)
                    }
                }
            }
        }
    }

    fun setStatusFilter(status: String) {
        if (_uiState.value.statusFilter == status) return
        _uiState.update { it.copy(statusFilter = status) }
        refresh()
    }

    fun setSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(300)
            refresh()
        }
    }

    fun refresh(silent: Boolean = false) {
        load(page = 1, replace = true, silent = silent)
    }

    fun loadMore() {
        val s = _uiState.value
        if (s.isLoadingMore || s.items.size >= s.total) return
        load(page = s.page + 1, replace = false, silent = false)
    }

    private fun load(page: Long, replace: Boolean, silent: Boolean) {
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
            repository.applications(
                status = s.statusFilter,
                search = s.searchQuery,
                page = page,
            ).onSuccess { res ->
                _uiState.update { cur ->
                    cur.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        metrics = res.metrics,
                        items = if (replace) res.data else (cur.items + res.data).distinctBy { it.id },
                        page = res.page,
                        total = res.total,
                        error = null,
                    )
                }
            }.onFailure { err ->
                _uiState.update { cur ->
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

    fun openDetail(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetail = true, selectedDetail = null, sheetError = null) }
            repository.applicationDetail(id)
                .onSuccess { detail ->
                    _uiState.update { it.copy(isLoadingDetail = false, selectedDetail = detail) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoadingDetail = false, sheetError = err.message) }
                }
        }
    }

    fun dismissDetail() {
        _uiState.update { it.copy(selectedDetail = null, isLoadingDetail = false, sheetError = null) }
    }

    fun applyAction(
        id: Long,
        expectedVersion: Int,
        action: String,
        reasonCode: String? = null,
        reason: String? = null,
        customerMessage: String? = null,
        selfCheckinEnabled: Boolean? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, sheetError = null) }
            repository.applyAction(
                id = id,
                request = EkycReviewActionRequestDto(
                    action = action,
                    expectedVersion = expectedVersion,
                    reasonCode = reasonCode,
                    reason = reason?.trim()?.takeIf { it.isNotEmpty() },
                    customerMessage = customerMessage?.trim()?.takeIf { it.isNotEmpty() },
                    selfCheckinEnabled = selfCheckinEnabled,
                    idempotencyKey = UUID.randomUUID().toString(),
                ),
            ).onSuccess { updated ->
                _uiState.update { it.copy(isMutating = false, selectedDetail = updated) }
                _effects.trySend(EkycEffect.ShowMessage("Application updated (${updated.summary.status})."))
                refresh(silent = true)
            }.onFailure { err ->
                _uiState.update { it.copy(isMutating = false, sheetError = err.message) }
            }
        }
    }

    private fun loadReasonCodes() {
        viewModelScope.launch {
            repository.reasonCodes().onSuccess { list ->
                _uiState.update { it.copy(reasonCodes = list.filter { c -> c.isActive }) }
            }
        }
    }
}
