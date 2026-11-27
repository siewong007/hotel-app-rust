package com.hotelapp.staff.data.dto

import com.hotelapp.staff.core.network.FlexibleDecimalSerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.math.BigDecimal

@Serializable
data class BookingPaymentRecordDto(
    val id: Long,
    @SerialName("booking_id") val bookingId: Long,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("total_amount") val totalAmount: BigDecimal = BigDecimal.ZERO,
    @SerialName("payment_method") val paymentMethod: String = "",
    @SerialName("payment_type") val paymentType: String? = null,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("transaction_reference") val transactionReference: String? = null,
    val notes: String? = null,
    @SerialName("payment_date") val paymentDate: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("void_reason") val voidReason: String? = null,
    @SerialName("voided_at") val voidedAt: String? = null,
    @SerialName("voided_by_name") val voidedByName: String? = null,
    @SerialName("approved_by_name") val approvedByName: String? = null,
    @SerialName("approved_at") val approvedAt: String? = null,
)

@Serializable
data class PaymentWorkflowSummaryDto(
    @SerialName("booking_id") val bookingId: Long,
    @SerialName("booking_status") val bookingStatus: String = "",
    @SerialName("payment_status") val paymentStatus: String = "",
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("total_amount") val totalAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("total_paid") val totalPaid: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("total_refunded") val totalRefunded: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("balance_due") val balanceDue: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("deposit_collected") val depositCollected: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("deposit_refunded") val depositRefunded: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("deposit_forfeited") val depositForfeited: BigDecimal = BigDecimal.ZERO,
    @SerialName("has_failed_payment") val hasFailedPayment: Boolean = false,
    @SerialName("next_action") val nextAction: String = "",
    val warnings: List<String> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RecordPaymentRequestDto(
    @SerialName("booking_id") val bookingId: Long,
    val amount: Double,
    @SerialName("payment_method") val paymentMethod: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("payment_type") val paymentType: String? = "booking",
    @SerialName("transaction_reference") val transactionReference: String? = null,
    val notes: String? = null,
    @SerialName("payment_date") val paymentDate: String? = null,
    @SerialName("idempotency_key") val idempotencyKey: String,
)

@Serializable
data class PendingPaymentEntryDto(
    val id: Long,
    @SerialName("booking_id") val bookingId: Long,
    @SerialName("booking_number") val bookingNumber: String? = null,
    @SerialName("guest_id") val guestId: Long? = null,
    @SerialName("guest_name") val guestName: String? = null,
    @Serializable(with = FlexibleDecimalSerializer::class)
    val amount: BigDecimal = BigDecimal.ZERO,
    @SerialName("payment_method") val paymentMethod: String = "",
    val status: String = "",
    val reference: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("receipt_requested") val receiptRequested: Boolean = false,
    @SerialName("receipt_uploaded") val receiptUploaded: Boolean = false,
    @SerialName("receipt_file_available") val receiptFileAvailable: Boolean = false,
    @SerialName("processed_at") val processedAt: String? = null,
    @SerialName("processed_by_name") val processedByName: String? = null,
    @SerialName("decision_reason") val decisionReason: String? = null,
    @SerialName("check_in_date") val checkInDate: String = "",
    @SerialName("check_out_date") val checkOutDate: String = "",
    @SerialName("room_number") val roomNumber: String? = null,
    @SerialName("booking_status") val bookingStatus: String = "",
)

@Serializable
data class PendingPaymentPageDto(
    val items: List<PendingPaymentEntryDto> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class RejectPaymentRequestDto(
    val reason: String,
)

@Serializable
data class RefundDepositRequestDto(
    val amount: Double,
    @SerialName("payment_method") val paymentMethod: String,
    val note: String,
    @SerialName("transaction_reference") val transactionReference: String? = null,
)

@Serializable
data class ForfeitDepositRequestDto(
    val amount: Double,
    val reason: String,
    val notes: String? = null,
)

@Serializable
data class PaymentActionResponseDto(
    @SerialName("payment_id") val paymentId: Long,
    val status: String,
    @SerialName("booking_status") val bookingStatus: String? = null,
)

@Serializable
data class InvoiceDto(
    val id: Long = 0,
    val uuid: String = "",
    @SerialName("invoice_number") val invoiceNumber: String = "",
    @SerialName("booking_id") val bookingId: Long = 0,
    @SerialName("billing_name") val billingName: String = "",
    @SerialName("billing_address") val billingAddress: String? = null,
    @SerialName("billing_email") val billingEmail: String? = null,
    @SerialName("invoice_date") val invoiceDate: String? = null,
    @SerialName("issue_date") val issueDate: String = "",
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("check_in_date") val checkInDate: String? = null,
    @SerialName("check_out_date") val checkOutDate: String? = null,
    @SerialName("number_of_nights") val numberOfNights: Int? = null,
    @SerialName("room_number") val roomNumber: String? = null,
    @SerialName("room_type") val roomType: String? = null,
    @Serializable(with = FlexibleDecimalSerializer::class)
    val subtotal: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("tax_amount") val taxAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("discount_amount") val discountAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("total_amount") val totalAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("paid_amount") val paidAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("balance_due") val balanceDue: BigDecimal = BigDecimal.ZERO,
    val currency: String = "MYR",
    val status: String = "",
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class InvoicePreviewDto(
    val invoice: InvoiceDto,
    val payment: JsonElement? = null,
    @SerialName("booking_details") val bookingDetails: JsonElement? = null,
)

@Serializable
data class RequestPaymentReceiptRequestDto(
    val message: String? = null,
)

data class PaymentReceiptFile(
    val bytes: ByteArray,
    val contentType: String,
)
