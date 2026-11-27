package com.hotelapp.staff.feature.ekyc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.network.ApiException
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
    val hasMore: Boolean = true,
    val hasLoadedOnce: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val isMutating: Boolean = false,
    val isLoadingReasonCodes: Boolean = false,
    val reasonCodesError: String? = null,
    val metrics: EkycDashboardMetricsDto = EkycDashboardMetricsDto(),
    val items: List<EkycApplicationSummaryDto> = emptyList(),
    val page: Long = 1,
    val total: Long = 0,
    val selectedDetail: EkycApplicationDetailDto? = null,
    val reasonCodes: List<EkycReasonCodeDto> = emptyList(),
    val activeDocumentKind: String? = null,
    val activeDocumentBytes: ByteArray? = null,
    val isLoadingDocument: Boolean = false,
    val documentError: String? = null,
    val revealedFields: Map<String, String?> = emptyMap(),
    val isRevealing: Boolean = false,
    val revealError: String? = null,
    val error: String? = null,
    val sheetError: String? = null,
)

class EkycViewModel(
    private val session: StaffSession,
    private val repository: EkycRepository,
    realtimeSocketClient: RealtimeSocketClient? = null,
    private val sessionProvider: () -> StaffSession = { session },
) : ViewModel() {

    private fun canReviewNow(): Boolean =
        sessionProvider().hasAnyPermission("ekyc:review", "ekyc:manage")

    private val _uiState = MutableStateFlow(EkycUiState())
    val uiState: StateFlow<EkycUiState> = _uiState.asStateFlow()

    private val _effects = Channel<EkycEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var debounceJob: Job? = null
    private var wsDebounceJob: Job? = null
    private var listJob: Job? = null
    private var loadMoreJob: Job? = null
    private var detailJob: Job? = null
    private var reasonCodesJob: Job? = null
    private var docJob: Job? = null
    private var revealJob: Job? = null

    init {
        refresh()
        if (canReviewNow()) {
            loadReasonCodes()
        }
        if (realtimeSocketClient != null) {
            viewModelScope.launch {
                realtimeSocketClient.domainEvents.collect { domain ->
                    if (domain == "ekyc") {
                        wsDebounceJob?.cancel()
                        wsDebounceJob = viewModelScope.launch {
                            delay(250)
                            refresh(silent = true)
                            val openId = _uiState.value.selectedDetail?.summary?.id
                            if (openId != null && !_uiState.value.isMutating) {
                                refreshDetailSilently(openId)
                            }
                        }
                    }
                }
            }
        }
    }

    fun setStatusFilter(status: String) {
        if (_uiState.value.statusFilter == status) return
        debounceJob?.cancel()
        loadMoreJob?.cancel()
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
        if (!silent) {
            debounceJob?.cancel()
        }
        loadMoreJob?.cancel()
        if (canReviewNow() && _uiState.value.reasonCodes.isEmpty() && !_uiState.value.isLoadingReasonCodes) {
            loadReasonCodes()
        }
        load(page = 1, replace = true, silent = silent)
    }

    fun loadMore() {
        val s = _uiState.value
        if (!s.hasMore || s.isLoadingMore || s.items.size >= s.total) return
        load(page = s.page + 1, replace = false, silent = false)
    }

    fun retryLoadReasonCodes() {
        if (!canReviewNow()) return
        loadReasonCodes()
    }

    private fun load(page: Long, replace: Boolean, silent: Boolean) {
        if (replace) {
            listJob?.cancel()
            loadMoreJob?.cancel()
        } else {
            loadMoreJob?.cancel()
        }
        val launched = viewModelScope.launch {
            val s = _uiState.value
            val statusAtDispatch = s.statusFilter
            val searchAtDispatch = s.searchQuery
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = replace && it.items.isEmpty(),
                    isRefreshing = replace && it.items.isNotEmpty(),
                    isLoadingMore = !replace,
                    error = null,
                )
            }
            repository.applications(
                status = statusAtDispatch,
                search = searchAtDispatch,
                page = page,
            ).onSuccess { res ->
                _uiState.update { cur ->
                    if (cur.statusFilter != statusAtDispatch || cur.searchQuery != searchAtDispatch) {
                        return@update cur.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                        )
                    }
                    val merged = if (replace) res.data else (cur.items + res.data).distinctBy { it.id }
                    val moreAvailable = res.data.isNotEmpty() &&
                        merged.size < res.total &&
                        (replace || merged.size > cur.items.size)
                    cur.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        hasMore = moreAvailable,
                        hasLoadedOnce = true,
                        metrics = res.metrics,
                        items = merged,
                        page = res.page,
                        total = res.total,
                        error = null,
                    )
                }
            }.onFailure { err ->
                _uiState.update { cur ->
                    if (cur.statusFilter != statusAtDispatch || cur.searchQuery != searchAtDispatch) {
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
                        error = if (silent && cur.hasLoadedOnce) cur.error else err.message,
                    )
                }
            }
        }
        if (replace) {
            listJob = launched
        } else {
            loadMoreJob = launched
        }
    }

    fun openDetail(id: Long) {
        detailJob?.cancel()
        docJob?.cancel()
        revealJob?.cancel()
        detailJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingDetail = true,
                    selectedDetail = null,
                    sheetError = null,
                    activeDocumentKind = null,
                    activeDocumentBytes = null,
                    isLoadingDocument = false,
                    documentError = null,
                    revealedFields = emptyMap(),
                    isRevealing = false,
                    revealError = null,
                )
            }
            repository.applicationDetail(id)
                .onSuccess { detail ->
                    _uiState.update { it.copy(isLoadingDetail = false, selectedDetail = detail) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoadingDetail = false, sheetError = err.message) }
                }
        }
    }

    fun refreshSelectedDetail() {
        val openId = _uiState.value.selectedDetail?.summary?.id ?: return
        if (_uiState.value.isMutating) return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetail = true, sheetError = null) }
            repository.applicationDetail(openId)
                .onSuccess { detail ->
                    _uiState.update { it.copy(isLoadingDetail = false, selectedDetail = detail, sheetError = null) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoadingDetail = false, sheetError = err.message ?: "Refresh failed.") }
                }
        }
    }

    private suspend fun refreshDetailSilently(id: Long) {
        repository.applicationDetail(id).onSuccess { fresh ->
            _uiState.update { cur ->
                if (cur.selectedDetail?.summary?.id == id) cur.copy(selectedDetail = fresh) else cur
            }
        }
    }

    fun dismissDetail() {
        detailJob?.cancel()
        docJob?.cancel()
        revealJob?.cancel()
        _uiState.update {
            it.copy(
                selectedDetail = null,
                isLoadingDetail = false,
                sheetError = null,
                activeDocumentKind = null,
                activeDocumentBytes = null,
                isLoadingDocument = false,
                documentError = null,
                revealedFields = emptyMap(),
                isRevealing = false,
                revealError = null,
            )
        }
    }

    fun openDocument(kind: String) {
        val id = _uiState.value.selectedDetail?.summary?.id ?: return
        docJob?.cancel()
        _uiState.update {
            it.copy(
                activeDocumentKind = kind,
                activeDocumentBytes = null,
                isLoadingDocument = true,
                documentError = null,
            )
        }
        docJob = viewModelScope.launch {
            repository.fetchDocumentBytes(id, kind)
                .onSuccess { bytes ->
                    _uiState.update { cur ->
                        if (cur.selectedDetail?.summary?.id == id && cur.activeDocumentKind == kind) {
                            cur.copy(isLoadingDocument = false, activeDocumentBytes = bytes, documentError = null)
                        } else cur
                    }
                }
                .onFailure { err ->
                    _uiState.update { cur ->
                        if (cur.selectedDetail?.summary?.id == id && cur.activeDocumentKind == kind) {
                            cur.copy(isLoadingDocument = false, documentError = err.message ?: "Unable to load document.")
                        } else cur
                    }
                }
        }
    }

    fun dismissDocument() {
        docJob?.cancel()
        _uiState.update {
            it.copy(
                activeDocumentKind = null,
                activeDocumentBytes = null,
                isLoadingDocument = false,
                documentError = null,
            )
        }
    }

    fun clearRevealError() {
        _uiState.update { it.copy(revealError = null) }
    }

    fun revealSensitiveField(field: String, reason: String, onSuccess: () -> Unit = {}) {
        val id = _uiState.value.selectedDetail?.summary?.id ?: return
        if (_uiState.value.isRevealing) return
        val trimmedReason = reason.trim()
        if (trimmedReason.length < 5) {
            _uiState.update { it.copy(revealError = "Enter at least 5 characters explaining why PII reveal is needed.") }
            return
        }
        revealJob?.cancel()
        _uiState.update { it.copy(isRevealing = true, revealError = null) }
        revealJob = viewModelScope.launch {
            repository.revealSensitive(id, field, trimmedReason)
                .onSuccess { resp ->
                    _uiState.update { cur ->
                        if (cur.selectedDetail?.summary?.id == id) {
                            cur.copy(
                                isRevealing = false,
                                revealError = null,
                                revealedFields = cur.revealedFields + (resp.field to resp.value),
                            )
                        } else cur.copy(isRevealing = false)
                    }
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isRevealing = false, revealError = err.message ?: "Reveal failed.") }
                }
        }
    }

    fun applyAction(
        id: Long,
        expectedVersion: Int,
        action: String,
        reasonCode: String? = null,
        reason: String? = null,
        customerMessage: String? = null,
        selfCheckinEnabled: Boolean? = null,
        idempotencyKey: String = UUID.randomUUID().toString(),
        onSuccess: () -> Unit = {},
    ) {
        if (_uiState.value.isMutating) return
        _uiState.update { it.copy(isMutating = true, sheetError = null) }
        viewModelScope.launch {
            repository.applyAction(
                id = id,
                request = EkycReviewActionRequestDto(
                    action = action,
                    expectedVersion = expectedVersion,
                    reasonCode = reasonCode,
                    reason = reason?.trim()?.takeIf { it.isNotEmpty() },
                    customerMessage = customerMessage?.trim()?.takeIf { it.isNotEmpty() },
                    selfCheckinEnabled = selfCheckinEnabled,
                    idempotencyKey = idempotencyKey,
                ),
            ).onSuccess { updated ->
                _uiState.update { it.copy(isMutating = false, selectedDetail = updated, sheetError = null) }
                _effects.trySend(EkycEffect.ShowMessage("Application updated (${updated.summary.status})."))
                onSuccess()
                refresh(silent = true)
            }.onFailure { err ->
                val isConflict = (err as? ApiException)?.statusCode == 409
                if (isConflict) {
                    val fresh = repository.applicationDetail(id).getOrNull()
                    _uiState.update { cur ->
                        cur.copy(
                            isMutating = false,
                            selectedDetail = fresh ?: cur.selectedDetail,
                            sheetError = if (fresh != null) {
                                "Record was modified by another reviewer (v${fresh.summary.version}) and refreshed. Please review and try again."
                            } else {
                                err.message
                            },
                        )
                    }
                    refresh(silent = true)
                } else {
                    _uiState.update { it.copy(isMutating = false, sheetError = err.message) }
                }
            }
        }
    }

    private fun loadReasonCodes() {
        reasonCodesJob?.cancel()
        _uiState.update { it.copy(isLoadingReasonCodes = true, reasonCodesError = null) }
        reasonCodesJob = viewModelScope.launch {
            repository.reasonCodes()
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            isLoadingReasonCodes = false,
                            reasonCodesError = null,
                            reasonCodes = list.filter { c -> c.isActive },
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingReasonCodes = false,
                            reasonCodesError = err.message ?: "Unable to load reason codes.",
                        )
                    }
                }
        }
    }
}
