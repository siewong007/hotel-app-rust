package com.hotelapp.staff.data.dto

import com.hotelapp.staff.core.network.FlexibleDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class PaginatedResponseDto<T>(
    val data: List<T> = emptyList(),
    val total: Long = 0,
    val page: Long = 1,
    @SerialName("page_size") val pageSize: Long = 25,
)

@Serializable
data class BookingBoardSummaryDto(
    val arriving: Long = 0,
    @SerialName("ready_to_check_in") val readyToCheckIn: Long = 0,
    @SerialName("in_house") val inHouse: Long = 0,
    @SerialName("guests_in_house") val guestsInHouse: Long = 0,
    val departing: Long = 0,
    val upcoming: Long = 0,
    @SerialName("normal_due") val normalDue: Long = 0,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("normal_due_amount") val normalDueAmount: BigDecimal = BigDecimal.ZERO,
    @SerialName("company_due") val companyDue: Long = 0,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("company_due_amount") val companyDueAmount: BigDecimal = BigDecimal.ZERO,
    @SerialName("company_outstanding_months") val companyOutstandingMonths: Int = 1,
)

@Serializable
data class NewReservationItemDto(
    val id: Long,
    @SerialName("booking_number") val bookingNumber: String? = null,
    @SerialName("guest_name") val guestName: String,
    @SerialName("room_number") val roomNumber: String,
    @SerialName("check_in_date") val checkInDate: String,
    @SerialName("check_out_date") val checkOutDate: String,
    val status: String,
)

@Serializable
data class NewReservationsResponseDto(
    @SerialName("visible_from") val visibleFrom: String = "07:00",
    val reservations: List<NewReservationItemDto> = emptyList(),
)

@Serializable
data class GuestEkycStatusSummaryDto(
    @SerialName("guest_id") val guestId: Long = 0,
    @SerialName("ekyc_verification_id") val ekycVerificationId: Long? = null,
    val status: String = "not_submitted",
    @SerialName("self_checkin_enabled") val selfCheckinEnabled: Boolean = false,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("can_auto_checkin") val canAutoCheckin: Boolean = false,
    @SerialName("auto_checkin_block_reason") val autoCheckinBlockReason: String? = null,
    @SerialName("auto_checkin_block_code") val autoCheckinBlockCode: String? = null,
)

@Serializable
data class BookingWithDetailsDto(
    val id: Long,
    @SerialName("booking_number") val bookingNumber: String,
    @SerialName("folio_number") val folioNumber: String? = null,
    @SerialName("guest_id") val guestId: Long,
    @SerialName("guest_name") val guestName: String,
    @SerialName("guest_email") val guestEmail: String? = null,
    @SerialName("guest_type") val guestType: String? = null,
    @SerialName("guest_tourism_type") val guestTourismType: String? = null,
    @SerialName("room_id") val roomId: Long,
    @SerialName("room_number") val roomNumber: String,
    @SerialName("room_type") val roomType: String,
    @SerialName("room_type_code") val roomTypeCode: String? = null,
    @SerialName("check_in_date") val checkInDate: String,
    @SerialName("check_out_date") val checkOutDate: String,
    val adults: Int? = null,
    val children: Int? = null,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("price_per_night") val roomRate: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("total_amount") val totalAmount: BigDecimal = BigDecimal.ZERO,
    val status: String,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    val source: String? = null,
    @SerialName("booking_channel_id") val bookingChannelId: Long? = null,
    @SerialName("booking_channel_name") val bookingChannelName: String? = null,
    @SerialName("booking_channel_type") val bookingChannelType: String? = null,
    @SerialName("ota_reference") val otaReference: String? = null,
    val remarks: String? = null,
    @SerialName("special_requests") val specialRequests: String? = null,
    @SerialName("is_complimentary") val isComplimentary: Boolean? = null,
    @SerialName("deposit_paid") val depositPaid: Boolean? = null,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("deposit_amount") val depositAmount: BigDecimal? = null,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("total_paid") val totalPaid: BigDecimal? = null,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("total_refunded") val totalRefunded: BigDecimal? = null,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("balance_due") val balanceDue: BigDecimal? = null,
    @SerialName("company_id") val companyId: Long? = null,
    @SerialName("company_name") val companyName: String? = null,
    @SerialName("payment_note") val paymentNote: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_tourist") val isTourist: Boolean? = null,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("tourism_tax_amount") val tourismTaxAmount: BigDecimal? = null,
    @SerialName("extra_bed_count") val extraBedCount: Int? = null,
    @SerialName("invoice_number") val invoiceNumber: String? = null,
    @SerialName("cleaning_preference") val cleaningPreference: Boolean? = null,
    @SerialName("smoking_preference") val smokingPreference: String? = null,
    @SerialName("room_is_smoking") val roomIsSmoking: Boolean? = null,
    @SerialName("created_by_name") val createdByName: String? = null,
    @SerialName("group_number") val groupNumber: String? = null,
    @SerialName("ekyc_summary") val ekycSummary: GuestEkycStatusSummaryDto? = null,
)

@Serializable
data class BookingDto(
    val id: Long,
    @SerialName("booking_number") val bookingNumber: String,
    @SerialName("guest_id") val guestId: Long,
    @SerialName("room_id") val roomId: Long,
    @SerialName("check_in_date") val checkInDate: String,
    @SerialName("check_out_date") val checkOutDate: String,
    val status: String,
    @SerialName("payment_status") val paymentStatus: String? = null,
)

@Serializable
data class BookingTimelineEntryDto(
    val id: String,
    val source: String,
    @SerialName("event_type") val eventType: String,
    val title: String,
    val description: String? = null,
    @SerialName("status_from") val statusFrom: String? = null,
    @SerialName("status_to") val statusTo: String? = null,
    val amount: String? = null,
    @SerialName("actor_username") val actorUsername: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CheckInAdvisoryDto(
    @SerialName("needs_attention") val needsAttention: Boolean = false,
    val reason: String? = null,
    val message: String? = null,
    @SerialName("prior_total_bookings") val priorTotalBookings: Long = 0,
    @SerialName("prior_company_bookings") val priorCompanyBookings: Long = 0,
    @SerialName("suggested_company_id") val suggestedCompanyId: Long? = null,
    @SerialName("suggested_company_name") val suggestedCompanyName: String? = null,
)

@Serializable
data class CheckInGuestUpdateDto(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val phone: String? = null,
)

@Serializable
data class CheckInPaymentRecordDto(
    val amount: Double,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("payment_type") val paymentType: String? = null,
    val notes: String? = null,
)

@Serializable
data class CheckInRequestDto(
    @SerialName("guest_update") val guestUpdate: CheckInGuestUpdateDto? = null,
    @SerialName("payment_record") val paymentRecord: CheckInPaymentRecordDto? = null,
)

@Serializable
data class BookingStatusUpdateDto(
    val status: String,
)

@Serializable
data class BookingCancellationRequestDto(
    @SerialName("booking_id") val bookingId: Long,
    val reason: String? = null,
)

@Serializable
data class ReleaseBookingRequestDto(
    val reason: String,
)
