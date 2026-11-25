package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.RoomsApi
import com.hotelapp.staff.data.dto.HotelOccupancySummaryDto
import com.hotelapp.staff.data.dto.RoomDetailedStatusDto
import com.hotelapp.staff.data.dto.RoomStatusUpdateInputDto
import com.hotelapp.staff.data.dto.RoomWithOccupancyDto

interface RoomsRepository {
    suspend fun occupancySummary(): Result<HotelOccupancySummaryDto>
    suspend fun roomsWithOccupancy(): Result<List<RoomWithOccupancyDto>>
    suspend fun roomDetail(id: Long): Result<RoomDetailedStatusDto>
    suspend fun updateStatus(id: Long, status: String, notes: String?): Result<Unit>
    suspend fun endCleaning(id: Long): Result<Unit>
    suspend fun endMaintenance(id: Long): Result<Unit>
}

class DefaultRoomsRepository(
    private val api: RoomsApi,
) : RoomsRepository {
    override suspend fun occupancySummary(): Result<HotelOccupancySummaryDto> = safeApiCall { api.occupancySummary() }

    override suspend fun roomsWithOccupancy(): Result<List<RoomWithOccupancyDto>> = safeApiCall { api.withOccupancy() }

    override suspend fun roomDetail(id: Long): Result<RoomDetailedStatusDto> = safeApiCall { api.detailed(id) }

    override suspend fun updateStatus(id: Long, status: String, notes: String?): Result<Unit> = safeApiCall {
        api.updateStatus(
            id,
            RoomStatusUpdateInputDto(
                status = status,
                reason = notes?.trim()?.takeIf { it.isNotEmpty() },
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    override suspend fun endCleaning(id: Long): Result<Unit> = safeApiCall { api.endCleaning(id) }

    override suspend fun endMaintenance(id: Long): Result<Unit> = safeApiCall { api.endMaintenance(id) }
}
