package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.BookingPaymentRecordDto
import com.hotelapp.staff.data.dto.InvoiceDto
import com.hotelapp.staff.data.dto.InvoicePreviewDto
import com.hotelapp.staff.data.dto.PaymentActionResponseDto
import com.hotelapp.staff.data.dto.PaymentWorkflowSummaryDto
import com.hotelapp.staff.data.dto.PendingPaymentPageDto
import com.hotelapp.staff.data.dto.RecordPaymentRequestDto
import com.hotelapp.staff.data.dto.RejectPaymentRequestDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface PaymentsApi {
    @GET("/api/payments/all-payments/{bookingId}")
    suspend fun bookingPayments(@Path("bookingId") bookingId: Long): List<BookingPaymentRecordDto>

    @GET("/api/payments/workflow-summary/{bookingId}")
    suspend fun workflowSummary(@Path("bookingId") bookingId: Long): PaymentWorkflowSummaryDto

    @POST("/api/payments/record-payment")
    suspend fun recordPayment(@Body body: RecordPaymentRequestDto): JsonElement

    @GET("/api/admin/payments/pending")
    suspend fun pendingPayments(
        @Query("page") page: Long = 1,
        @Query("per_page") perPage: Long = 25,
    ): PendingPaymentPageDto

    @PUT("/api/admin/payments/{paymentId}/approve")
    suspend fun approvePayment(@Path("paymentId") paymentId: Long): PaymentActionResponseDto

    @PUT("/api/admin/payments/{paymentId}/reject")
    suspend fun rejectPayment(
        @Path("paymentId") paymentId: Long,
        @Body body: RejectPaymentRequestDto,
    ): PaymentActionResponseDto

    @GET("/api/invoices/preview/{bookingId}")
    suspend fun invoicePreview(@Path("bookingId") bookingId: Long): InvoicePreviewDto

    @POST("/api/invoices/generate/{bookingId}")
    suspend fun generateInvoice(@Path("bookingId") bookingId: Long): InvoiceDto
}
