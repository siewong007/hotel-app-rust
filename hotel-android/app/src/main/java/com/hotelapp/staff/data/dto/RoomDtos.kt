package com.hotelapp.staff.data.dto

import com.hotelapp.staff.core.network.FlexibleDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class HotelOccupancySummaryDto(
    @SerialName("total_rooms") val totalRooms: Long = 0,
    @SerialName("occupied_rooms") val occupiedRooms: Long = 0,
    @SerialName("available_rooms") val availableRooms: Long = 0,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("occupancy_rate") val occupancyRate: BigDecimal? = null,
    @SerialName("total_adults") val totalAdults: Long = 0,
    @SerialName("total_children") val totalChildren: Long = 0,
    @SerialName("total_infants") val totalInfants: Long = 0,
    @SerialName("total_guests") val totalGuests: Long = 0,
    @SerialName("total_capacity") val totalCapacity: Long = 0,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("guest_occupancy_rate") val guestOccupancyRate: BigDecimal? = null,
)

@Serializable
data class RoomDto(
    val id: Long,
    @SerialName("room_number") val roomNumber: String,
    @SerialName("room_type") val roomType: String,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("price_per_night") val pricePerNight: BigDecimal = BigDecimal.ZERO,
    val available: Boolean = true,
    val status: String? = null,
    val description: String? = null,
    @SerialName("max_occupancy") val maxOccupancy: Int = 2,
    val notes: String? = null,
    @SerialName("is_smoking") val isSmoking: Boolean? = null,
)

@Serializable
data class RoomWithOccupancyDto(
    val id: Long,
    @SerialName("room_number") val roomNumber: String,
    @SerialName("room_type") val roomType: String,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("price_per_night") val pricePerNight: BigDecimal = BigDecimal.ZERO,
    val available: Boolean = true,
    val status: String? = null,
    val description: String? = null,
    @SerialName("max_occupancy") val maxOccupancy: Int = 2,
    val notes: String? = null,
    @SerialName("is_smoking") val isSmoking: Boolean? = null,
    @SerialName("current_adults") val currentAdults: Int = 0,
    @SerialName("current_children") val currentChildren: Int = 0,
    @SerialName("current_infants") val currentInfants: Int = 0,
    @SerialName("current_total_guests") val currentTotalGuests: Int = 0,
    @SerialName("is_occupied") val isOccupied: Boolean = false,
    @SerialName("current_booking_id") val currentBookingId: Long? = null,
    @SerialName("current_guest_id") val currentGuestId: Long? = null,
)

@Serializable
data class RoomEventDto(
    val id: Long,
    @SerialName("room_id") val roomId: Long,
    @SerialName("event_type") val eventType: String,
    val status: String? = null,
    val priority: String? = null,
    val notes: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class RoomDetailedStatusDto(
    val id: Long,
    @SerialName("room_number") val roomNumber: String,
    @SerialName("room_type") val roomType: String,
    val status: String,
    val available: Boolean,
    @SerialName("current_booking") val currentBooking: BookingWithDetailsDto? = null,
    @SerialName("next_booking") val nextBooking: BookingWithDetailsDto? = null,
    @SerialName("recent_events") val recentEvents: List<RoomEventDto> = emptyList(),
    @SerialName("maintenance_notes") val maintenanceNotes: String? = null,
    @SerialName("status_notes") val statusNotes: String? = null,
)

@Serializable
data class RoomStatusUpdateInputDto(
    val status: String,
    val reason: String? = null,
    val notes: String? = null,
)
