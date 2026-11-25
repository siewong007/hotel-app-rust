package com.hotelapp.staff.core.sync

import kotlinx.serialization.Serializable

/**
 * Only non-financial operational mutations may be queued offline.
 * Financial and booking-state mutations (checkIn, checkOut, recordPayment,
 * approvePayment, rejectPayment, voidBooking, releaseHold, createBooking, ekycAction)
 * MUST remain strictly online-only.
 */
enum class PendingMutationKind {
    HOUSEKEEPING_TASK_UPDATE,
    ROOM_STATUS_UPDATE,
    ROOM_END_CLEANING,
    MAINTENANCE_CREATE,
}

@Serializable
data class QueuedRoomStatusPayload(
    val status: String,
    val notes: String? = null,
)
