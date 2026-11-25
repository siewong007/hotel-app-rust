package com.hotelapp.staff.feature.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.data.dto.PendingPaymentEntryDto
import com.hotelapp.staff.data.repository.PaymentsRepository
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
    val items: List<PendingPaymentEntryDto> = emptyList(),
    val page: Long = 1,
    val total: Long = 0,
    val error: String? = null,
)

class PendingPaymentsViewModel(
    private val repository: PaymentsRepository,
    realtimeSocketClient: RealtimeSocketClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PendingPaymentsUiState())
    val uiState: StateFlow<PendingPaymentsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<PendingPaymentsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

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

    fun refresh(silent: Boolean = false) = load(1, replace = true, silent = silent)

    fun loadMore() {
        val s = _uiState.value
        if (s.isLoadingMore || s.items.size >= s.total) return
        load(s.page + 1, replace = false)
    }

    fun approve(paymentId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true) }
            repository.approvePayment(paymentId)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false) }
                    _effects.trySend(PendingPaymentsEffect.ShowMessage("Payment approved."))
                    load(1, replace = true, silent = true)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isMutating = false) }
                    _effects.trySend(PendingPaymentsEffect.ShowMessage(err.message ?: "Approval failed.", isError = true))
                }
        }
    }

    fun reject(paymentId: Long, reason: String) {
        if (reason.isBlank()) {
            _effects.trySend(PendingPaymentsEffect.ShowMessage("Rejection reason is required.", isError = true))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true) }
            repository.rejectPayment(paymentId, reason)
                .onSuccess {
                    _uiState.update { it.copy(isMutating = false) }
                    _effects.trySend(PendingPaymentsEffect.ShowMessage("Payment rejected."))
                    load(1, replace = true, silent = true)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isMutating = false) }
                    _effects.trySend(PendingPaymentsEffect.ShowMessage(err.message ?: "Rejection failed.", isError = true))
                }
        }
    }

    private fun load(page: Long, replace: Boolean, silent: Boolean = false) {
        viewModelScope.launch {
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
                        cur.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            items = if (replace) res.items else (cur.items + res.items).distinctBy { it.id },
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
                            error = if (silent && cur.items.isNotEmpty()) cur.error else err.message,
                        )
                    }
                }
        }
    }
}
