package com.hotelapp.staff.feature.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.data.dto.PaymentReceiptFile
import com.hotelapp.staff.data.dto.PendingPaymentEntryDto
import com.hotelapp.staff.data.repository.PaymentsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PendingPaymentsEffect {
    data class ShowMessage(val message: String, val isError: Boolean = false) : PendingPaymentsEffect
}

data class PendingPaymentsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isMutating: Boolean = false,
    val hasMore: Boolean = true,
    val hasLoadedOnce: Boolean = false,
    val items: List<PendingPaymentEntryDto> = emptyList(),
    val page: Long = 1,
    val total: Long = 0,
    val error: String? = null,
    val actionError: String? = null,
    val receiptPaymentId: Long? = null,
    val receiptFile: PaymentReceiptFile? = null,
    val isLoadingReceipt: Boolean = false,
    val receiptError: String? = null,
)

class PendingPaymentsViewModel(
    private val repository: PaymentsRepository,
    realtimeSocketClient: RealtimeSocketClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PendingPaymentsUiState())
    val uiState: StateFlow<PendingPaymentsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<PendingPaymentsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var receiptJob: Job? = null

    init {
        load(1, replace = true)
        viewModelScope.launch {
            realtimeSocketClient.domainEvents.collect { domain ->
                if (domain == "payments" || domain == "bookings") {
                    load(1, replace = true, silent = true)
                }
            }
        }
    }

    fun clearActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    fun openReceipt(paymentId: Long) {
        receiptJob?.cancel()
        _uiState.update {
            it.copy(
                receiptPaymentId = paymentId,
                receiptFile = null,
                isLoadingReceipt = true,
                receiptError = null,
            )
        }
        receiptJob = viewModelScope.launch {
            repository.downloadReceipt(paymentId)
                .onSuccess { file ->
                    _uiState.update {
                        if (it.receiptPaymentId == paymentId) {
                            it.copy(isLoadingReceipt = false, receiptFile = file, receiptError = null)
                        } else it
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        if (it.receiptPaymentId == paymentId) {
                            it.copy(isLoadingReceipt = false, receiptError = err.message ?: "Unable to load receipt.")
                        } else it
                    }
                }
        }
    }

    fun dismissReceipt() {
        receiptJob?.cancel()
        _uiState.update {
            it.copy(
                receiptPaymentId = null,
                receiptFile = null,
                isLoadingReceipt = false,
                receiptError = null,
            )
        }
    }

    fun refresh(silent: Boolean = false) {
        loadMoreJob?.cancel()
        load(1, replace = true, silent = silent)
    }

    fun loadMore() {
        val s = _uiState.value
        if (!s.hasMore || s.isLoadingMore || s.items.size >= s.total) return
        load(s.page + 1, replace = false)
    }

    fun approve(paymentId: Long, onSuccess: () -> Unit = {}) {
        if (_uiState.value.isMutating) return
        _uiState.update { it.copy(isMutating = true, actionError = null) }
        viewModelScope.launch {
            repository.approvePayment(paymentId)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, actionError = null) }
                    _effects.trySend(PendingPaymentsEffect.ShowMessage("Payment approved."))
                    onSuccess()
                    load(1, replace = true, silent = true)
                }
                .onFailure { err ->
                    val msg = err.message ?: "Approval failed."
                    _uiState.update { it.copy(isMutating = false, actionError = msg) }
                    _effects.trySend(PendingPaymentsEffect.ShowMessage(msg, isError = true))
                }
        }
    }

    fun reject(paymentId: Long, reason: String, onSuccess: () -> Unit = {}) {
        if (_uiState.value.isMutating) return
        val trimmed = reason.trim()
        if (trimmed.isEmpty()) {
            val msg = "Rejection reason is required."
            _uiState.update { it.copy(actionError = msg) }
            _effects.trySend(PendingPaymentsEffect.ShowMessage(msg, isError = true))
            return
        }
        _uiState.update { it.copy(isMutating = true, actionError = null) }
        viewModelScope.launch {
            repository.rejectPayment(paymentId, trimmed)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, actionError = null) }
                    _effects.trySend(PendingPaymentsEffect.ShowMessage("Payment rejected."))
                    onSuccess()
                    load(1, replace = true, silent = true)
                }
                .onFailure { err ->
                    val msg = err.message ?: "Rejection failed."
                    _uiState.update { it.copy(isMutating = false, actionError = msg) }
                    _effects.trySend(PendingPaymentsEffect.ShowMessage(msg, isError = true))
                }
        }
    }

    fun requestReceipt(paymentId: Long, message: String? = null, onSuccess: () -> Unit = {}) {
        if (_uiState.value.isMutating) return
        _uiState.update { it.copy(isMutating = true, actionError = null) }
        viewModelScope.launch {
            repository.requestReceipt(paymentId, message)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false, actionError = null) }
                    _effects.trySend(PendingPaymentsEffect.ShowMessage("Receipt request sent to guest."))
                    onSuccess()
                    load(1, replace = true, silent = true)
                }
                .onFailure { err ->
                    val msg = err.message ?: "Unable to request receipt."
                    _uiState.update { it.copy(isMutating = false, actionError = msg) }
                    _effects.trySend(PendingPaymentsEffect.ShowMessage(msg, isError = true))
                }
        }
    }

    private fun load(page: Long, replace: Boolean, silent: Boolean = false) {
        if (replace) {
            loadJob?.cancel()
            loadMoreJob?.cancel()
        } else {
            loadMoreJob?.cancel()
        }
        val launched = viewModelScope.launch {
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = replace && it.items.isEmpty(),
                    isRefreshing = replace && it.items.isNotEmpty(),
                    isLoadingMore = !replace,
                    error = null,
                )
            }
            repository.pendingPayments(page = page, perPage = 25)
                .onSuccess { res ->
                    _uiState.update { cur ->
                        val merged = if (replace) res.items else (cur.items + res.items).distinctBy { it.id }
                        val moreAvailable = res.items.isNotEmpty() &&
                            merged.size < res.total &&
                            (replace || merged.size > cur.items.size)
                        cur.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            hasMore = moreAvailable,
                            hasLoadedOnce = true,
                            items = merged,
                            page = page,
                            total = res.total,
                            error = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { cur ->
                        cur.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = if (silent && cur.hasLoadedOnce) cur.error else (err.message ?: "Unable to load pending payments."),
                        )
                    }
                }
        }
        if (replace) {
            loadJob = launched
        } else {
            loadMoreJob = launched
        }
    }
}
