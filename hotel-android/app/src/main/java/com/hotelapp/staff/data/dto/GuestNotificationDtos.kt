package com.hotelapp.staff.data.dto

import com.hotelapp.staff.core.network.FlexibleDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class GuestDto(
    val id: Long,
    @SerialName("nick_name") val nickName: String = "",
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val nationality: String? = null,
    @SerialName("address_line1") val addressLine1: String? = null,
    val city: String? = null,
    val country: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("guest_type") val guestType: String = "regular",
    @SerialName("tourism_type") val tourismType: String? = null,
    @SerialName("company_name") val companyName: String? = null,
    @SerialName("vip_status") val vipStatus: String? = null,
    val tags: List<String>? = null,
    val notes: String? = null,
    @SerialName("special_requests") val specialRequests: String? = null,
    @SerialName("is_blacklisted") val isBlacklisted: Boolean? = null,
    @SerialName("blacklist_reason") val blacklistReason: String? = null,
    @SerialName("bookings_count") val bookingsCount: Long? = null,
    @SerialName("last_stay_date") val lastStayDate: String? = null,
) {
    fun displayName(): String {
        val full = listOfNotNull(
            firstName?.trim()?.takeIf { it.isNotEmpty() },
            lastName?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ")
        return full.ifBlank { nickName.trim().ifBlank { "Guest #$id" } }
    }
}

@Serializable
data class GuestPaginatedResponseDto(
    val data: List<GuestDto> = emptyList(),
    val total: Long = 0,
    val page: Long = 1,
    @SerialName("page_size") val pageSize: Long = 25,
)

@Serializable
data class GuestSummaryDto(
    @SerialName("completed_stays") val completedStays: Long = 0,
    @SerialName("total_nights") val totalNights: Long = 0,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("total_room_revenue") val totalRoomRevenue: BigDecimal = BigDecimal.ZERO,
    @SerialName("last_stay_at") val lastStayAt: String? = null,
    @SerialName("next_stay_at") val nextStayAt: String? = null,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("outstanding_balance") val outstandingBalance: BigDecimal = BigDecimal.ZERO,
    @SerialName("total_bookings") val totalBookings: Long = 0,
    @SerialName("active_booking_id") val activeBookingId: Long? = null,
    @SerialName("active_booking_number") val activeBookingNumber: String? = null,
)

@Serializable
data class GuestProfileBookingDto(
    val id: Long,
    @SerialName("booking_number") val bookingNumber: String? = null,
    @SerialName("check_in_date") val checkInDate: String,
    @SerialName("check_out_date") val checkOutDate: String,
    val nights: Long = 1,
    val status: String,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("total_amount") val totalAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("total_paid") val totalPaid: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("balance_due") val balanceDue: BigDecimal = BigDecimal.ZERO,
    @SerialName("room_number") val roomNumber: String,
    @SerialName("room_type") val roomType: String,
)

@Serializable
data class GuestSensitiveProfileDto(
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    @SerialName("id_type") val idType: String? = null,
    @SerialName("id_number") val idNumber: String? = null,
    @SerialName("id_expiry") val idExpiry: String? = null,
    @SerialName("id_country") val idCountry: String? = null,
)

@Serializable
data class GuestProfileDto(
    val guest: GuestDto,
    val summary: GuestSummaryDto,
    @SerialName("ekyc_summary") val ekycSummary: GuestEkycStatusSummaryDto,
    val reservations: List<GuestProfileBookingDto> = emptyList(),
    val sensitive: GuestSensitiveProfileDto? = null,
)

@Serializable
data class StaffNotificationItemDto(
    val id: Long,
    @SerialName("audience_permission") val audiencePermission: String? = null,
    val kind: String,
    val subject: String? = null,
    val title: String,
    val body: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null,
    val priority: String = "normal",
    val category: String = "system",
    @SerialName("action_url") val actionUrl: String? = null,
    @SerialName("action_label_key") val actionLabelKey: String? = null,
    @SerialName("requires_action") val requiresAction: Boolean = false,
    @SerialName("action_pending") val actionPending: Boolean = true,
)

@Serializable
data class StaffNotificationsResponseDto(
    val unread: Long = 0,
    @SerialName("urgent_unread") val urgentUnread: Long = 0,
    @SerialName("action_pending_count") val actionPendingCount: Long = 0,
    val items: List<StaffNotificationItemDto> = emptyList(),
)
