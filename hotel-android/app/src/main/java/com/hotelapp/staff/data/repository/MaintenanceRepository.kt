package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.MaintenanceApi
import com.hotelapp.staff.data.dto.CreateMaintenanceTicketRequestDto
import com.hotelapp.staff.data.dto.MaintenanceTicketDto
import com.hotelapp.staff.data.dto.MaintenanceTicketListResponseDto
import com.hotelapp.staff.data.dto.UpdateMaintenanceTicketRequestDto

interface MaintenanceRepository {
    suspend fun list(
        status: String? = null,
        priority: String? = null,
        page: Long = 1,
    ): Result<MaintenanceTicketListResponseDto>
    suspend fun create(
        request: CreateMaintenanceTicketRequestDto,
        idempotencyKey: String? = null,
    ): Result<MaintenanceTicketDto>
    suspend fun update(id: Long, request: UpdateMaintenanceTicketRequestDto): Result<MaintenanceTicketDto>
}

class DefaultMaintenanceRepository(
    private val api: MaintenanceApi,
) : MaintenanceRepository {
    override suspend fun list(
        status: String?,
        priority: String?,
        page: Long,
    ): Result<MaintenanceTicketListResponseDto> = safeApiCall {
        api.list(status = status, priority = priority, page = page, pageSize = 30)
    }

    override suspend fun create(
        request: CreateMaintenanceTicketRequestDto,
        idempotencyKey: String?,
    ): Result<MaintenanceTicketDto> =
        safeApiCall { api.create(request, idempotencyKey = idempotencyKey) }

    override suspend fun update(id: Long, request: UpdateMaintenanceTicketRequestDto): Result<MaintenanceTicketDto> =
        safeApiCall { api.update(id, request) }
}
