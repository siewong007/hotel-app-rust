package com.hotelapp.staff.feature.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.format.MoneyInputParser
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.data.dto.BookingGroupDetailDto
import com.hotelapp.staff.data.dto.BookingPaymentRecordDto
import com.hotelapp.staff.data.dto.BookingTimelineEntryDto
import com.hotelapp.staff.data.dto.BookingWithDetailsDto
import com.hotelapp.staff.data.dto.CheckInAdvisoryDto
import com.hotelapp.staff.data.dto.CheckInGuestUpdateDto
import com.hotelapp.staff.data.dto.CheckInPaymentRecordDto
import com.hotelapp.staff.data.dto.CheckInRequestDto
import com.hotelapp.staff.data.dto.ForfeitDepositRequestDto
import com.hotelapp.staff.data.dto.GroupPayRequestDto
import com.hotelapp.staff.data.dto.InvoiceDto
import com.hotelapp.staff.data.dto.PaymentWorkflowSummaryDto
import com.hotelapp.staff.data.dto.RecordPaymentRequestDto
import com.hotelapp.staff.data.dto.RefundDepositRequestDto
import com.hotelapp.staff.data.repository.BookingsRepository
import com.hotelapp.staff.data.repository.PaymentsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

sealed interface BookingDetailEffect {
    data class ShowMessage(val message: String, val isError: Boolean = false) : BookingDetailEffect
}

data class BookingDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSubmittingAction: Boolean = false,
    val booking: BookingWithDetailsDto? = null,
    val groupDetail: BookingGroupDetailDto? = null,
    val timeline: List<BookingTimelineEntryDto> = emptyList(),
    val advisory: CheckInAdvisoryDto? = null,
    val paymentSummary: PaymentWorkflowSummaryDto? = null,
    val payments: List<BookingPaymentRecordDto> = emptyList(),
    val invoicePreview: InvoiceDto? = null,
    val isLoadingInvoice: Boolean = false,
    val invoiceError: String? = null,
    val error: String? = null,
    val refreshError: String? = null,
    val actionError: String? = null,
    val actionNotice: String? = null,
)

class BookingDetailViewModel(
    bookingId: Long,
    private val repository: BookingsRepository,
    private val paymentsRepository: PaymentsRepository? = null,
    private val canReadPayments: Boolean = false,
    realtimeSocketClient: RealtimeSocketClient? = null,
    private val onBookingMutated: (() -> Unit)? = null,
    private val sessionProvider: (() -> StaffSession)? = null,
) : ViewModel() {

    private var bookingId: Long = bookingId
    private val _uiState = MutableStateFlow(BookingDetailUiState())
    val uiState: StateFlow<BookingDetailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<BookingDetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var refreshJob: Job? = null
    private var wsDebounceJob: Job? = null
    private var invoiceJob: Job? = null

    init {
        refresh()
        if (realtimeSocketClient != null) {
            viewModelScope.launch {
                realtimeSocketClient.domainEvents.collect { domain ->
                    if (domain in setOf("bookings", "payments", "rooms")) {
                        wsDebounceJob?.cancel()
                        wsDebounceJob = viewModelScope.launch {
                            delay(250)
                            refresh(silent = true)
                        }
                    }
                }
            }
        }
    }

    private fun canReadPaymentsNow(): Boolean =
        sessionProvider?.invoke()?.hasPermission("payments:read") ?: canReadPayments

    fun setBookingId(newId: Long) {
        if (bookingId == newId && _uiState.value.booking != null) {
            refresh(silent = true)
            return
        }
        bookingId = newId
        refreshJob?.cancel()
        wsDebounceJob?.cancel()
        invoiceJob?.cancel()
        _uiState.value = BookingDetailUiState()
        refresh(silent = false)
    }

    fun clearFeedback() {
        _uiState.update { it.copy(actionNotice = null, actionError = null, refreshError = null) }
    }

    fun clearRefreshError() {
        _uiState.update { it.copy(refreshError = null) }
    }

    fun reportInvoiceError(message: String) {
        _uiState.update { it.copy(invoiceError = message) }
    }

    fun dismissInvoicePreview() {
        invoiceJob?.cancel()
        _uiState.update {
            it.copy(
                invoicePreview = null,
                isLoadingInvoice = false,
                invoiceError = null,
                actionError = null,
            )
        }
    }

    fun refresh(silent: Boolean = false) {
        val targetId = bookingId
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update {
                if (silent) it else it.copy(
                    isLoading = it.booking == null,
                    isRefreshing = it.booking != null,
                    error = null,
                    refreshError = null,
                )
            }
            val detailRes = repository.detail(targetId)
            detailRes.onSuccess { booking ->
                if (bookingId != targetId) return@onSuccess
                val readPayments = canReadPaymentsNow() && paymentsRepository != null
                coroutineScope {
                    val timelineDef = async { repository.timeline(targetId) }
                    val summaryDef = if (readPayments) async { paymentsRepository!!.workflowSummary(targetId) } else null
                    val paymentsDef = if (readPayments) async { paymentsRepository!!.bookingPayments(targetId) } else null
                    val advisoryDef = if (booking.status.lowercase() in CHECKIN_ELIGIBLE_STATUSES) {
                        async { repository.checkinAdvisory(targetId).getOrNull() }
                    } else null
                    val groupDef = if (!booking.groupNumber.isNullOrBlank()) {
                        async { repository.bookingGroup(targetId).getOrNull() }
                    } else null

                    val timeline = timelineDef.await().getOrDefault(emptyList())
                    val summary = summaryDef?.await()?.getOrNull()
                    val paymentRows = paymentsDef?.await()?.getOrNull()
                    val advisory = advisoryDef?.await()
                    val group = groupDef?.await()
                    if (bookingId != targetId) return@coroutineScope
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            booking = booking,
                            groupDetail = if (!booking.groupNumber.isNullOrBlank()) (group ?: it.groupDetail) else null,
                            timeline = timeline,
                            advisory = advisory,
                            paymentSummary = summary ?: it.paymentSummary,
                            payments = paymentRows ?: it.payments,
                            error = null,
                            refreshError = null,
                        )
                    }
                }
            }.onFailure { err ->
                if (bookingId != targetId) return@onFailure
                val msg = err.message ?: "Unable to load booking."
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (it.booking == null) msg else it.error,
                        refreshError = if (it.booking != null && !silent) msg else it.refreshError,
                    )
                }
            }
        }
    }

    fun recordPayment(
        amountText: String,
        method: String,
        reference: String,
        notes: String,
        paymentType: String = "booking",
        idempotencyKey: String = UUID.randomUUID().toString(),
        onSuccess: () -> Unit,
    ) {
        if (_uiState.value.isSubmittingAction) return
        val normalizedType = paymentType.trim().lowercase().ifEmpty { "booking" }
        val isGroupRoomCharge = !_uiState.value.booking?.groupNumber.isNullOrBlank() && normalizedType == "booking"
        val repo = paymentsRepository
        if (!isGroupRoomCharge && repo == null) return

        val parsedAmount = if (isGroupRoomCharge) null else MoneyInputParser.parsePositiveAmount(amountText)
        if (!isGroupRoomCharge && parsedAmount == null) {
            _uiState.update { it.copy(actionError = "Enter a valid payment amount greater than 0 (max 2 decimal places).") }
            return
        }
        val targetId = bookingId
        _uiState.update { it.copy(isSubmittingAction = true, actionError = null, actionNotice = null) }
        viewModelScope.launch {
            val result = if (isGroupRoomCharge) {
                repository.groupPay(
                    targetId,
                    GroupPayRequestDto(
                        paymentMethod = method,
                        transactionReference = reference.trim().takeIf { it.isNotEmpty() },
                        notes = notes.trim().takeIf { it.isNotEmpty() },
                    ),
                )
            } else {
                repo!!.recordPayment(
                    RecordPaymentRequestDto(
                        bookingId = targetId,
                        amount = parsedAmount!!.toDouble(),
                        paymentMethod = method,
                        paymentType = normalizedType,
                        transactionReference = reference.trim().takeIf { it.isNotEmpty() },
                        notes = notes.trim().takeIf { it.isNotEmpty() },
                        idempotencyKey = idempotencyKey,
                    ),
                )
            }
            result.onSuccess {
                val msg = if (isGroupRoomCharge) "Group balance payment recorded." else "Payment recorded."
                _uiState.update { it.copy(isSubmittingAction = false, actionError = null, actionNotice = msg) }
                _effects.trySend(BookingDetailEffect.ShowMessage(msg))
                onBookingMutated?.invoke()
                refresh(silent = true)
                onSuccess()
            }.onFailure { err ->
                val msg = err.message ?: "Failed to record payment."
                _uiState.update { it.copy(isSubmittingAction = false, actionError = msg) }
            }
        }
    }

    fun refundDeposit(
        amount: BigDecimal,
        maxHeld: BigDecimal,
        method: String,
        note: String,
        reference: String,
        onSuccess: () -> Unit,
    ) {
        if (_uiState.value.isSubmittingAction) return
        val repo = paymentsRepository ?: return
        if (amount <= BigDecimal.ZERO || (maxHeld > BigDecimal.ZERO && amount > maxHeld)) {
            _uiState.update { it.copy(actionError = "Enter a deposit refund amount up to ${maxHeld.toPlainString()}.") }
            return
        }
        val trimmedNote = note.trim()
        if (trimmedNote.length < MIN_DEPOSIT_REASON_LEN || trimmedNote.length > MAX_DEPOSIT_REASON_LEN) {
            _uiState.update { it.copy(actionError = "Refund note must be $MIN_DEPOSIT_REASON_LEN to $MAX_DEPOSIT_REASON_LEN characters.") }
            return
        }
        val targetId = bookingId
        _uiState.update { it.copy(isSubmittingAction = true, actionError = null, actionNotice = null) }
        viewModelScope.launch {
            repo.refundDeposit(
                bookingId = targetId,
                request = RefundDepositRequestDto(
                    amount = amount.toDouble(),
                    paymentMethod = method,
                    note = trimmedNote,
                    transactionReference = reference.trim().takeIf { it.isNotEmpty() },
                ),
            ).onSuccess {
                _uiState.update { it.copy(isSubmittingAction = false, actionError = null, actionNotice = "Deposit refunded.") }
                _effects.trySend(BookingDetailEffect.ShowMessage("Deposit refunded."))
                onBookingMutated?.invoke()
                refresh(silent = true)
                onSuccess()
            }.onFailure { err ->
                _uiState.update { it.copy(isSubmittingAction = false, actionError = err.message ?: "Deposit refund failed.") }
            }
        }
    }

    fun forfeitDeposit(
        amount: BigDecimal,
        maxHeld: BigDecimal,
        reason: String,
        notes: String,
        onSuccess: () -> Unit,
    ) {
        if (_uiState.value.isSubmittingAction) return
        val repo = paymentsRepository ?: return
        if (amount <= BigDecimal.ZERO || (maxHeld > BigDecimal.ZERO && amount > maxHeld)) {
            _uiState.update { it.copy(actionError = "Enter a forfeit amount up to ${maxHeld.toPlainString()}.") }
            return
        }
        val trimmedReason = reason.trim()
        if (trimmedReason.length < MIN_DEPOSIT_REASON_LEN || trimmedReason.length > MAX_DEPOSIT_REASON_LEN) {
            _uiState.update { it.copy(actionError = "Forfeit reason must be $MIN_DEPOSIT_REASON_LEN to $MAX_DEPOSIT_REASON_LEN characters.") }
            return
        }
        val targetId = bookingId
        _uiState.update { it.copy(isSubmittingAction = true, actionError = null, actionNotice = null) }
        viewModelScope.launch {
            repo.forfeitDeposit(
                bookingId = targetId,
                request = ForfeitDepositRequestDto(
                    amount = amount.toDouble(),
                    reason = trimmedReason,
                    notes = notes.trim().takeIf { it.isNotEmpty() },
                ),
            ).onSuccess {
                _uiState.update { it.copy(isSubmittingAction = false, actionError = null, actionNotice = "Deposit forfeited.") }
                _effects.trySend(BookingDetailEffect.ShowMessage("Deposit forfeited."))
                onBookingMutated?.invoke()
                refresh(silent = true)
                onSuccess()
            }.onFailure { err ->
                _uiState.update { it.copy(isSubmittingAction = false, actionError = err.message ?: "Deposit forfeit failed.") }
            }
        }
    }

    fun openInvoicePreview() {
        if (_uiState.value.isLoadingInvoice) return
        val repo = paymentsRepository ?: return
        val targetId = bookingId
        invoiceJob?.cancel()
        _uiState.update { it.copy(isLoadingInvoice = true, invoiceError = null, actionError = null) }
        invoiceJob = viewModelScope.launch {
            repo.invoicePreview(targetId)
                .onSuccess { preview ->
                    if (bookingId != targetId) return@onSuccess
                    _uiState.update { it.copy(isLoadingInvoice = false, invoicePreview = preview.invoice, invoiceError = null) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoadingInvoice = false) }
                    _effects.trySend(BookingDetailEffect.ShowMessage(err.message ?: "Unable to load invoice.", isError = true))
                }
        }
    }

    fun generateOfficialInvoice() {
        if (_uiState.value.isLoadingInvoice) return
        val repo = paymentsRepository ?: return
        val targetId = bookingId
        invoiceJob?.cancel()
        _uiState.update { it.copy(isLoadingInvoice = true, invoiceError = null, actionError = null) }
        invoiceJob = viewModelScope.launch {
            repo.generateInvoice(targetId)
                .onSuccess { invoice ->
                    if (bookingId != targetId) return@onSuccess
                    _uiState.update {
                        it.copy(
                            isLoadingInvoice = false,
                            invoicePreview = invoice,
                            invoiceError = null,
                            actionError = null,
                        )
                    }
                    _effects.trySend(BookingDetailEffect.ShowMessage("Invoice ${invoice.invoiceNumber} generated."))
                    onBookingMutated?.invoke()
                    refresh(silent = true)
                }
                .onFailure { err ->
                    val msg = err.message ?: "Unable to generate invoice."
                    _uiState.update { it.copy(isLoadingInvoice = false, invoiceError = msg) }
                }
        }
    }

    fun checkIn(
        firstName: String,
        lastName: String,
        phone: String,
        icNumber: String = "",
        paymentAmount: String,
        paymentMethod: String,
        onSuccess: () -> Unit,
    ) {
        if (_uiState.value.isSubmittingAction) return
        val trimmedAmount = paymentAmount.trim()
        val parsedAmount = if (trimmedAmount.isNotEmpty()) {
            val valid = MoneyInputParser.parsePositiveAmount(trimmedAmount)
            if (valid == null) {
                _uiState.update {
                    it.copy(actionError = "Enter a valid payment amount greater than 0 (max 2 decimal places).")
                }
                return
            }
            valid
        } else null

        val targetId = bookingId
        _uiState.update { it.copy(isSubmittingAction = true, actionError = null, actionNotice = null) }
        viewModelScope.launch {
            val guestUpdate = if (firstName.isNotBlank() || lastName.isNotBlank() || phone.isNotBlank() || icNumber.isNotBlank()) {
                CheckInGuestUpdateDto(
                    firstName = firstName.trim().takeIf { it.isNotEmpty() },
                    lastName = lastName.trim().takeIf { it.isNotEmpty() },
                    phone = phone.trim().takeIf { it.isNotEmpty() },
                    icNumber = icNumber.trim().takeIf { it.isNotEmpty() },
                )
            } else null

            val paymentRecord = parsedAmount?.let {
                CheckInPaymentRecordDto(
                    amount = it.toDouble(),
                    paymentMethod = paymentMethod,
                    paymentType = "booking",
                )
            }

            repository.checkIn(
                targetId,
                CheckInRequestDto(guestUpdate = guestUpdate, paymentRecord = paymentRecord),
            ).onSuccess {
                _uiState.update { it.copy(isSubmittingAction = false, actionError = null, actionNotice = "Guest checked in.") }
                _effects.trySend(BookingDetailEffect.ShowMessage("Guest checked in."))
                onBookingMutated?.invoke()
                refresh(silent = true)
                onSuccess()
            }.onFailure { err ->
                val msg = err.message ?: "Check-in failed."
                _uiState.update { it.copy(isSubmittingAction = false, actionError = msg) }
            }
        }
    }

    fun checkOut(onSuccess: () -> Unit = {}) {
        if (_uiState.value.isSubmittingAction) return
        val targetId = bookingId
        _uiState.update { it.copy(isSubmittingAction = true, actionError = null, actionNotice = null) }
        viewModelScope.launch {
            repository.checkOut(targetId)
                .onSuccess {
                    _uiState.update { it.copy(isSubmittingAction = false, actionError = null, actionNotice = "Booking checked out.") }
                    _effects.trySend(BookingDetailEffect.ShowMessage("Booking checked out."))
                    onBookingMutated?.invoke()
                    refresh(silent = true)
                    onSuccess()
                }
                .onFailure { err ->
                    val msg = err.message ?: "Check-out failed."
                    _uiState.update { it.copy(isSubmittingAction = false, actionError = msg) }
                }
        }
    }

    fun voidBooking(reason: String, onSuccess: () -> Unit) {
        if (_uiState.value.isSubmittingAction) return
        val targetId = bookingId
        _uiState.update { it.copy(isSubmittingAction = true, actionError = null, actionNotice = null) }
        viewModelScope.launch {
            repository.voidBooking(targetId, reason)
                .onSuccess {
                    _uiState.update { it.copy(isSubmittingAction = false, actionError = null, actionNotice = "Booking voided.") }
                    _effects.trySend(BookingDetailEffect.ShowMessage("Booking voided."))
                    onBookingMutated?.invoke()
                    refresh(silent = true)
                    onSuccess()
                }
                .onFailure { err ->
                    val msg = err.message ?: "Void failed."
                    _uiState.update { it.copy(isSubmittingAction = false, actionError = msg) }
                }
        }
    }

    fun releaseHold(reason: String, onSuccess: () -> Unit) {
        if (_uiState.value.isSubmittingAction) return
        val trimmed = reason.trim()
        if (trimmed.isEmpty()) {
            val msg = "A reason is required to release an unpaid hold."
            _uiState.update { it.copy(actionError = msg) }
            return
        }
        if (trimmed.length < MIN_RELEASE_REASON_LEN) {
            val msg = "Enter at least $MIN_RELEASE_REASON_LEN characters to release an unpaid hold."
            _uiState.update { it.copy(actionError = msg) }
            return
        }
        val targetId = bookingId
        _uiState.update { it.copy(isSubmittingAction = true, actionError = null, actionNotice = null) }
        viewModelScope.launch {
            repository.releaseHold(targetId, trimmed)
                .onSuccess {
                    _uiState.update { it.copy(isSubmittingAction = false, actionError = null, actionNotice = "Unpaid hold released.") }
                    _effects.trySend(BookingDetailEffect.ShowMessage("Unpaid hold released."))
                    onBookingMutated?.invoke()
                    refresh(silent = true)
                    onSuccess()
                }
                .onFailure { err ->
                    val msg = err.message ?: "Unable to release hold."
                    _uiState.update { it.copy(isSubmittingAction = false, actionError = msg) }
                }
        }
    }

    fun reactivateBooking(onSuccess: () -> Unit = {}) {
        if (_uiState.value.isSubmittingAction) return
        val targetId = bookingId
        val isGroup = !_uiState.value.booking?.groupNumber.isNullOrBlank()
        _uiState.update { it.copy(isSubmittingAction = true, actionError = null, actionNotice = null) }
        viewModelScope.launch {
            val res = if (isGroup) repository.groupReactivate(targetId) else repository.reactivate(targetId)
            res.onSuccess {
                val msg = if (isGroup) "Group booking reactivated." else "Booking reactivated."
                _uiState.update { it.copy(isSubmittingAction = false, actionError = null, actionNotice = msg) }
                _effects.trySend(BookingDetailEffect.ShowMessage(msg))
                onBookingMutated?.invoke()
                refresh(silent = true)
                onSuccess()
            }.onFailure { err ->
                val msg = err.message ?: "Unable to reactivate booking."
                _uiState.update { it.copy(isSubmittingAction = false, actionError = msg) }
            }
        }
    }

    companion object {
        private const val MIN_RELEASE_REASON_LEN = 4
        private const val MIN_DEPOSIT_REASON_LEN = 3
        private const val MAX_DEPOSIT_REASON_LEN = 500
        private val CHECKIN_ELIGIBLE_STATUSES = setOf(
            "confirmed",
            "partial_complimentary",
            "fully_complimentary",
        )
    }
}
