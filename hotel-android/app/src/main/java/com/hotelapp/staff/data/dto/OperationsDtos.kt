package com.hotelapp.staff.data.dto

import com.hotelapp.staff.core.network.FlexibleDecimalSerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreateBookingRequestDto(
    @SerialName("guest_id") val guestId: Long,
    @SerialName("room_id") val roomId: Long,
    @SerialName("check_in_date") val checkInDate: String,
    @SerialName("check_out_date") val checkOutDate: String,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("amount_paid") val amountPaid: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val source: String? = "walk_in",
    @SerialName("booking_remarks") val bookingRemarks: String? = null,
    @SerialName("special_requests") val specialRequests: String? = null,
    @SerialName("deposit_amount") val depositAmount: Double? = null,
    @SerialName("deposit_paid") val depositPaid: Boolean? = null,
)

@Serializable
data class NightAuditRoomSnapshotDto(
    val total: Int = 0,
    val available: Int = 0,
    val occupied: Int = 0,
    val reserved: Int = 0,
    val maintenance: Int = 0,
    val dirty: Int = 0,
)

@Serializable
data class NightAuditPreviewDto(
    @SerialName("audit_date") val auditDate: String = "",
    @SerialName("can_run") val canRun: Boolean = false,
    @SerialName("already_run") val alreadyRun: Boolean = false,
    @SerialName("total_unposted") val totalUnposted: Int = 0,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("estimated_revenue") val estimatedRevenue: BigDecimal = BigDecimal.ZERO,
    @SerialName("room_snapshot") val roomSnapshot: NightAuditRoomSnapshotDto = NightAuditRoomSnapshotDto(),
)

@Serializable
data class OverviewBookingItemDto(
    @SerialName("booking_id") val bookingId: Long,
    @SerialName("guest_id") val guestId: Long,
    @SerialName("guest_name") val guestName: String = "",
    val status: String = "",
    @SerialName("room_label") val roomLabel: String? = null,
    @SerialName("is_vip") val isVip: Boolean = false,
)

@Serializable
data class OverviewBookingSectionDto(
    val count: Long = 0,
    val items: List<OverviewBookingItemDto> = emptyList(),
)

@Serializable
data class OverviewSupportItemDto(
    @SerialName("conversation_id") val conversationId: Long,
    @SerialName("conversation_number") val conversationNumber: String = "",
    @SerialName("guest_id") val guestId: Long? = null,
    @SerialName("guest_name") val guestName: String? = null,
    val status: String = "",
    val priority: String? = null,
    val subject: String = "",
)

@Serializable
data class OverviewSupportSectionDto(
    val open: Long = 0,
    @SerialName("waiting_for_staff") val waitingForStaff: Long = 0,
    val items: List<OverviewSupportItemDto> = emptyList(),
)

@Serializable
data class OverviewReviewItemDto(
    @SerialName("review_id") val reviewId: Long,
    @SerialName("guest_id") val guestId: Long,
    @SerialName("guest_name") val guestName: String = "",
    val rating: Double? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class OverviewReviewSectionDto(
    val count: Long = 0,
    val items: List<OverviewReviewItemDto> = emptyList(),
)

@Serializable
data class FollowUpQueueItemDto(
    @SerialName("note_id") val noteId: Long,
    @SerialName("guest_id") val guestId: Long,
    @SerialName("guest_name") val guestName: String = "",
    val subject: String? = null,
    @SerialName("interaction_type") val interactionType: String = "",
    @SerialName("follow_up_at") val followUpAt: String = "",
    @SerialName("assigned_to_name") val assignedToName: String? = null,
    val snippet: String? = null,
)

@Serializable
data class OverviewFollowUpSectionDto(
    val count: Long = 0,
    val items: List<FollowUpQueueItemDto> = emptyList(),
)

@Serializable
data class GuestRelationsOverviewDto(
    val arrivals: OverviewBookingSectionDto = OverviewBookingSectionDto(),
    @SerialName("in_house") val inHouse: OverviewBookingSectionDto = OverviewBookingSectionDto(),
    val departures: OverviewBookingSectionDto = OverviewBookingSectionDto(),
    @SerialName("vip_arrivals") val vipArrivals: OverviewBookingSectionDto = OverviewBookingSectionDto(),
    val support: OverviewSupportSectionDto? = null,
    val reviews: OverviewReviewSectionDto? = null,
    @SerialName("follow_ups") val followUps: OverviewFollowUpSectionDto = OverviewFollowUpSectionDto(),
)
