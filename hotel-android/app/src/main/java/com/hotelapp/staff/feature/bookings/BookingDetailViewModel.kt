package com.hotelapp.staff.feature.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.data.dto.BookingPaymentRecordDto
import com.hotelapp.staff.data.dto.BookingTimelineEntryDto
import com.hotelapp.staff.data.dto.BookingWithDetailsDto
import com.hotelapp.staff.data.dto.CheckInAdvisoryDto
import com.hotelapp.staff.data.dto.CheckInGuestUpdateDto
import com.hotelapp.staff.data.dto.CheckInPaymentRecordDto
import com.hotelapp.staff.data.dto.CheckInRequestDto
import com.hotelapp.staff.data.dto.InvoiceDto
import com.hotelapp.staff.data.dto.PaymentWorkflowSummaryDto
import com.hotelapp.staff.data.dto.RecordPaymentRequestDto
import com.hotelapp.staff.data.repository.BookingsRepository
import com.hotelapp.staff.data.repository.PaymentsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface BookingDetailEffect {
    data class ShowMessage(val message: String, val isError: Boolean = false) : BookingDetailEffect
}

data class BookingDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSubmittingAction: Boolean = false,
    val booking: BookingWithDetailsDto? = null,
    val timeline: List<BookingTimelineEntryDto> = emptyList(),
    val advisory: CheckInAdvisoryDto? = null,
    val paymentSummary: PaymentWorkflowSummaryDto? = null,
    val payments: List<BookingPaymentRecordDto> = emptyList(),
    val invoicePreview: InvoiceDto? = null,
    val isLoadingInvoice: Boolean = false,
    val error: String? = null,
    val actionError: String? = null,
    val actionNotice: String? = null,
)

class BookingDetailViewModel(
    private val bookingId: Long,
    private val repository: BookingsRepository,
    private val paymentsRepository: PaymentsRepository? = null,
    private val canReadPayments: Boolean = false,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookingDetailUiState())
    val uiState: StateFlow<BookingDetailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<BookingDetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        refresh()
    }

    fun clearFeedback() {
        _uiState.update { it.copy(actionNotice = null, actionError = null) }
    }

    fun dismissInvoicePreview() {
        _uiState.update { it.copy(invoicePreview = null, isLoadingInvoice = false) }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = it.booking == null,
                    isRefreshing = it.booking != null,
                    error = null,
                )
            }
            val detailRes = repository.detail(bookingId)
            val timelineDef = async { repository.timeline(bookingId) }
            val summaryDef = if (canReadPayments && paymentsRepository != null) {
                async { paymentsRepository.workflowSummary(bookingId) }
            } else null
            val paymentsDef = if (canReadPayments && paymentsRepository != null) {
                async { paymentsRepository.bookingPayments(bookingId) }
            } else null

            detailRes.onSuccess { booking ->
                val advisory = if (booking.status == "confirmed") {
                    repository.checkinAdvisory(bookingId).getOrNull()
                } else {
                    null
                }
                val summary = summaryDef?.await()?.getOrNull()
                val paymentRows = paymentsDef?.await()?.getOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        booking = booking,
                        timeline = timelineDef.await().getOrDefault(emptyList()),
                        advisory = advisory,
                        paymentSummary = summary ?: it.paymentSummary,
                        payments = paymentRows ?: it.payments,
                        error = null,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (it.booking == null) (err.message ?: "Unable to load booking.") else it.error,
                        actionError = if (it.booking != null) (err.message ?: "Refresh failed.") else null,
                    )
                }
                if (_uiState.value.booking != null) {
                    _effects.trySend(BookingDetailEffect.ShowMessage(err.message ?: "Refresh failed.", isError = true))
                }
            }
        }
    }

    fun recordPayment(
        amountText: String,
        method: String,
        reference: String,
        notes: String,
        onSuccess: () -> Unit,
    ) {
        val repo = paymentsRepository ?: return
        val amount = amountText.trim().toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _effects.trySend(BookingDetailEffect.ShowMessage("Enter a valid payment amount greater than 0.", isError = true))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingAction = true, actionError = null) }
            repo.recordPayment(
                RecordPaymentRequestDto(
                    bookingId = bookingId,
                    amount = amount,
                    paymentMethod = method,
                    paymentType = "booking",
                    transactionReference = reference.trim().takeIf { it.isNotEmpty() },
                    notes = notes.trim().takeIf { it.isNotEmpty() },
                    idempotencyKey = UUID.randomUUID().toString(),
                ),
            ).onSuccess {
                _uiState.update { it.copy(isSubmittingAction = false, actionNotice = "Payment recorded.") }
                _effects.trySend(BookingDetailEffect.ShowMessage("Payment recorded."))
                refresh(silent = true)
                onSuccess()
            }.onFailure { err ->
                val msg = err.message ?: "Failed to record payment."
                _uiState.update { it.copy(isSubmittingAction = false, actionError = msg) }
                _effects.trySend(BookingDetailEffect.ShowMessage(msg, isError = true))
            }
        }
    }

    fun openInvoicePreview() {
        val repo = paymentsRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInvoice = true) }
            repo.invoicePreview(bookingId)
                .onSuccess { preview ->
                    _uiState.update { it.copy(isLoadingInvoice = false, invoicePreview = preview.invoice) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoadingInvoice = false) }
                    _effects.trySend(BookingDetailEffect.ShowMessage(err.message ?: "Unable to load invoice.", isError = true))
                }
        }
    }

    fun generateOfficialInvoice() {
        val repo = paymentsRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInvoice = true) }
            repo.generateInvoice(bookingId)
                .onSuccess { invoice ->
                    _uiState.update { it.copy(isLoadingInvoice = false, invoicePreview = invoice) }
                    _effects.trySend(BookingDetailEffect.ShowMessage("Invoice ${invoice.invoiceNumber} generated."))
                    refresh(silent = true)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoadingInvoice = false) }
                    _effects.trySend(BookingDetailEffect.ShowMessage(err.message ?: "Unable to generate invoice.", isError = true))
                }
        }
    }

    fun checkIn(
        firstName: String,
        lastName: String,
        phone: String,
        paymentAmount: String,
        paymentMethod: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingAction = true, actionError = null, actionNotice = null) }
            val guestUpdate = if (firstName.isNotBlank() || lastName.isNotBlank() || phone.isNotBlank()) {
                CheckInGuestUpdateDto(
                    firstName = firstName.trim().takeIf { it.isNotEmpty() },
                    lastName = lastName.trim().takeIf { it.isNotEmpty() },
                    phone = phone.trim().takeIf { it.isNotEmpty() },
                )
            } else null

            val parsedAmount = paymentAmount.trim().toDoubleOrNull()
            val paymentRecord = if (parsedAmount != null && parsedAmount > 0.0) {
                CheckInPaymentRecordDto(
                    amount = parsedAmount,
                    paymentMethod = paymentMethod,
                    paymentType = "booking",
                )
            } else null

            repository.checkIn(
                bookingId,
                CheckInRequestDto(guestUpdate = guestUpdate, paymentRecord = paymentRecord),
            ).onSuccess {
                _uiState.update { it.copy(isSubmittingAction = false, actionNotice = "Guest checked in.") }
                _effects.trySend(BookingDetailEffect.ShowMessage("Guest checked in."))
                refresh(silent = true)
                onSuccess()
            }.onFailure { err ->
                val msg = err.message ?: "Check-in failed."
                _uiState.update { it.copy(isSubmittingAction = false, actionError = msg) }
                _effects.trySend(BookingDetailEffect.ShowMessage(msg, isError = true))
            }
        }
    }

    fun checkOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingAction = true, actionError = null, actionNotice = null) }
            repository.checkOut(bookingId)
                .onSuccess {
                    _uiState.update { it.copy(isSubmittingAction = false, actionNotice = "Booking checked out.") }
                    _effects.trySend(BookingDetailEffect.ShowMessage("Booking checked out."))
                    refresh(silent = true)
                }
                .onFailure { err ->
                    val msg = err.message ?: "Check-out failed."
                    _uiState.update { it.copy(isSubmittingAction = false, actionError = msg) }
                    _effects.trySend(BookingDetailEffect.ShowMessage(msg, isError = true))
                }
        }
    }

    fun voidBooking(reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingAction = true, actionError = null, actionNotice = null) }
            repository.voidBooking(bookingId, reason)
                .onSuccess {
                    _uiState.update { it.copy(isSubmittingAction = false, actionNotice = "Booking voided.") }
                    _effects.trySend(BookingDetailEffect.ShowMessage("Booking voided."))
                    refresh(silent = true)
                    onSuccess()
                }
                .onFailure { err ->
                    val msg = err.message ?: "Void failed."
                    _uiState.update { it.copy(isSubmittingAction = false, actionError = msg) }
                    _effects.trySend(BookingDetailEffect.ShowMessage(msg, isError = true))
                }
        }
    }

    fun releaseHold(reason: String, onSuccess: () -> Unit) {
        if (reason.isBlank()) {
            val msg = "A reason is required to release an unpaid hold."
            _uiState.update { it.copy(actionError = msg) }
            _effects.trySend(BookingDetailEffect.ShowMessage(msg, isError = true))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingAction = true, actionError = null, actionNotice = null) }
            repository.releaseHold(bookingId, reason)
                .onSuccess {
                    _uiState.update { it.copy(isSubmittingAction = false, actionNotice = "Unpaid hold released.") }
                    _effects.trySend(BookingDetailEffect.ShowMessage("Unpaid hold released."))
                    refresh(silent = true)
                    onSuccess()
                }
                .onFailure { err ->
                    val msg = err.message ?: "Unable to release hold."
                    _uiState.update { it.copy(isSubmittingAction = false, actionError = msg) }
                    _effects.trySend(BookingDetailEffect.ShowMessage(msg, isError = true))
                }
        }
    }
}
