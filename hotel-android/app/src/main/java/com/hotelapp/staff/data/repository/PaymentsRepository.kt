package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.PaymentsApi
import com.hotelapp.staff.data.dto.BookingPaymentRecordDto
import com.hotelapp.staff.data.dto.ForfeitDepositRequestDto
import com.hotelapp.staff.data.dto.InvoiceDto
import com.hotelapp.staff.data.dto.InvoicePreviewDto
import com.hotelapp.staff.data.dto.PaymentActionResponseDto
import com.hotelapp.staff.data.dto.PaymentReceiptFile
import com.hotelapp.staff.data.dto.PaymentWorkflowSummaryDto
import com.hotelapp.staff.data.dto.PendingPaymentPageDto
import com.hotelapp.staff.data.dto.RecordPaymentRequestDto
import com.hotelapp.staff.data.dto.RefundDepositRequestDto
import com.hotelapp.staff.data.dto.RejectPaymentRequestDto
import com.hotelapp.staff.data.dto.RequestPaymentReceiptRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PaymentsRepository {
    suspend fun bookingPayments(bookingId: Long): Result<List<BookingPaymentRecordDto>>
    suspend fun workflowSummary(bookingId: Long): Result<PaymentWorkflowSummaryDto>
    suspend fun recordPayment(request: RecordPaymentRequestDto): Result<Unit>
    suspend fun pendingPayments(page: Long = 1, perPage: Long = 25): Result<PendingPaymentPageDto>
    suspend fun approvePayment(paymentId: Long): Result<PaymentActionResponseDto>
    suspend fun rejectPayment(paymentId: Long, reason: String): Result<PaymentActionResponseDto>
    suspend fun downloadReceipt(paymentId: Long): Result<PaymentReceiptFile> =
        Result.failure(UnsupportedOperationException("Receipt download not supported."))
    suspend fun requestReceipt(paymentId: Long, message: String? = null): Result<Unit> =
        Result.failure(UnsupportedOperationException("Request receipt not supported."))
    suspend fun invoicePreview(bookingId: Long): Result<InvoicePreviewDto>
    suspend fun generateInvoice(bookingId: Long): Result<InvoiceDto>
    suspend fun refundDeposit(bookingId: Long, request: RefundDepositRequestDto): Result<Unit> =
        Result.failure(UnsupportedOperationException("Refund deposit not supported."))
    suspend fun forfeitDeposit(bookingId: Long, request: ForfeitDepositRequestDto): Result<Unit> =
        Result.failure(UnsupportedOperationException("Forfeit deposit not supported."))
}

class DefaultPaymentsRepository(
    private val api: PaymentsApi,
) : PaymentsRepository {
    override suspend fun bookingPayments(bookingId: Long): Result<List<BookingPaymentRecordDto>> =
        safeApiCall { api.bookingPayments(bookingId) }

    override suspend fun workflowSummary(bookingId: Long): Result<PaymentWorkflowSummaryDto> =
        safeApiCall { api.workflowSummary(bookingId) }

    override suspend fun recordPayment(request: RecordPaymentRequestDto): Result<Unit> =
        safeApiCall { api.recordPayment(request) }

    override suspend fun pendingPayments(page: Long, perPage: Long): Result<PendingPaymentPageDto> =
        safeApiCall { api.pendingPayments(page = page, perPage = perPage) }

    override suspend fun approvePayment(paymentId: Long): Result<PaymentActionResponseDto> =
        safeApiCall { api.approvePayment(paymentId) }

    override suspend fun rejectPayment(paymentId: Long, reason: String): Result<PaymentActionResponseDto> =
        safeApiCall { api.rejectPayment(paymentId, RejectPaymentRequestDto(reason = reason.trim())) }

    override suspend fun invoicePreview(bookingId: Long): Result<InvoicePreviewDto> =
        safeApiCall { api.invoicePreview(bookingId) }

    override suspend fun generateInvoice(bookingId: Long): Result<InvoiceDto> =
        safeApiCall { api.generateInvoice(bookingId) }

    override suspend fun refundDeposit(bookingId: Long, request: RefundDepositRequestDto): Result<Unit> =
        safeApiCall { api.refundDeposit(bookingId, request) }

    override suspend fun forfeitDeposit(bookingId: Long, request: ForfeitDepositRequestDto): Result<Unit> =
        safeApiCall { api.forfeitDeposit(bookingId, request) }

    override suspend fun downloadReceipt(paymentId: Long): Result<PaymentReceiptFile> =
        safeApiCall {
            withContext(Dispatchers.IO) {
                api.downloadReceipt(paymentId).use { body ->
                    val contentType = body.contentType()?.toString() ?: "application/octet-stream"
                    PaymentReceiptFile(bytes = body.bytes(), contentType = contentType)
                }
            }
        }

    override suspend fun requestReceipt(paymentId: Long, message: String?): Result<Unit> =
        safeApiCall {
            api.requestReceipt(
                paymentId = paymentId,
                body = RequestPaymentReceiptRequestDto(message = message?.trim()?.takeIf { it.isNotEmpty() }),
            )
            Unit
        }
}
