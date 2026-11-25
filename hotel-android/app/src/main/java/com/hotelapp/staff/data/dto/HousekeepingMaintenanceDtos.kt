package com.hotelapp.staff.data.dto

import com.hotelapp.staff.core.network.FlexibleDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class HousekeepingTaskDto(
    val id: Long,
    @SerialName("room_id") val roomId: Long,
    @SerialName("room_number") val roomNumber: String,
    @SerialName("room_type") val roomType: String,
    @SerialName("task_type") val taskType: String,
    val priority: String,
    val status: String,
    @SerialName("assigned_to") val assignedTo: Long? = null,
    @SerialName("assigned_to_name") val assignedToName: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    @SerialName("task_date") val taskDate: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val notes: String? = null,
    @SerialName("inspection_notes") val inspectionNotes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class HousekeepingBoardRoomDto(
    val id: Long,
    @SerialName("room_number") val roomNumber: String,
    @SerialName("room_type") val roomType: String,
    val floor: Int? = null,
    val status: String,
    @SerialName("open_task") val openTask: HousekeepingTaskDto? = null,
)

@Serializable
data class HousekeepingBoardResponseDto(
    val rooms: List<HousekeepingBoardRoomDto> = emptyList(),
)

@Serializable
data class HousekeepingTaskListResponseDto(
    val items: List<HousekeepingTaskDto> = emptyList(),
    val total: Long = 0,
    val page: Long = 1,
    @SerialName("page_size") val pageSize: Long = 25,
)

@Serializable
data class CreateHousekeepingTaskRequestDto(
    @SerialName("room_id") val roomId: Long,
    @SerialName("task_type") val taskType: String? = null,
    val priority: String? = null,
    @SerialName("assigned_to") val assignedTo: Long? = null,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateHousekeepingTaskRequestDto(
    val priority: String? = null,
    val status: String? = null,
    @SerialName("assigned_to") val assignedTo: Long? = null,
    val notes: String? = null,
    @SerialName("inspection_notes") val inspectionNotes: String? = null,
    @SerialName("clear_assignee") val clearAssignee: Boolean? = null,
)

@Serializable
data class AssignableStaffMemberDto(
    val id: Long,
    @SerialName("full_name") val fullName: String? = null,
    val username: String,
) {
    fun displayName(): String = fullName?.takeIf { it.isNotBlank() } ?: username
}

@Serializable
data class MaintenanceTicketDto(
    val id: Long,
    @SerialName("room_id") val roomId: Long? = null,
    @SerialName("room_number") val roomNumber: String? = null,
    @SerialName("ticket_number") val ticketNumber: String,
    val title: String,
    val description: String? = null,
    val category: String,
    val priority: String,
    val status: String,
    @SerialName("assigned_to") val assignedTo: Long? = null,
    @SerialName("assigned_to_name") val assignedToName: String? = null,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("estimated_cost") val estimatedCost: BigDecimal? = null,
    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("actual_cost") val actualCost: BigDecimal? = null,
    @SerialName("resolution_notes") val resolutionNotes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class MaintenanceTicketListResponseDto(
    val items: List<MaintenanceTicketDto> = emptyList(),
    val total: Long = 0,
    val page: Long = 1,
    @SerialName("page_size") val pageSize: Long = 25,
)

@Serializable
data class CreateMaintenanceTicketRequestDto(
    @SerialName("room_id") val roomId: Long? = null,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val priority: String? = null,
    @SerialName("assigned_to") val assignedTo: Long? = null,
)

@Serializable
data class UpdateMaintenanceTicketRequestDto(
    val title: String? = null,
    val description: String? = null,
    val category: String? = null,
    val priority: String? = null,
    val status: String? = null,
    @SerialName("assigned_to") val assignedTo: Long? = null,
    @SerialName("resolution_notes") val resolutionNotes: String? = null,
    @SerialName("clear_assignee") val clearAssignee: Boolean? = null,
)
