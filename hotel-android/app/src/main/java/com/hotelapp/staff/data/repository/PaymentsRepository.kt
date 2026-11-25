package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.PaymentsApi
import com.hotelapp.staff.data.dto.BookingPaymentRecordDto
import com.hotelapp.staff.data.dto.InvoiceDto
import com.hotelapp.staff.data.dto.InvoicePreviewDto
import com.hotelapp.staff.data.dto.PaymentActionResponseDto
import com.hotelapp.staff.data.dto.PaymentWorkflowSummaryDto
import com.hotelapp.staff.data.dto.PendingPaymentPageDto
import com.hotelapp.staff.data.dto.RecordPaymentRequestDto
import com.hotelapp.staff.data.dto.RejectPaymentRequestDto

interface PaymentsRepository {
    suspend fun bookingPayments(bookingId: Long): Result<List<BookingPaymentRecordDto>>
    suspend fun workflowSummary(bookingId: Long): Result<PaymentWorkflowSummaryDto>
    suspend fun recordPayment(request: RecordPaymentRequestDto): Result<Unit>
    suspend fun pendingPayments(page: Long = 1, perPage: Long = 25): Result<PendingPaymentPageDto>
    suspend fun approvePayment(paymentId: Long): Result<PaymentActionResponseDto>
    suspend fun rejectPayment(paymentId: Long, reason: String): Result<PaymentActionResponseDto>
    suspend fun invoicePreview(bookingId: Long): Result<InvoicePreviewDto>
    suspend fun generateInvoice(bookingId: Long): Result<InvoiceDto>
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
}
