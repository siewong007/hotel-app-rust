package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.HousekeepingApi
import com.hotelapp.staff.data.dto.AssignableStaffMemberDto
import com.hotelapp.staff.data.dto.CreateHousekeepingTaskRequestDto
import com.hotelapp.staff.data.dto.HousekeepingBoardResponseDto
import com.hotelapp.staff.data.dto.HousekeepingTaskDto
import com.hotelapp.staff.data.dto.HousekeepingTaskListResponseDto
import com.hotelapp.staff.data.dto.UpdateHousekeepingTaskRequestDto

interface HousekeepingRepository {
    suspend fun board(): Result<HousekeepingBoardResponseDto>
    suspend fun tasks(
        status: String? = null,
        unassigned: Boolean? = null,
        page: Long = 1,
    ): Result<HousekeepingTaskListResponseDto>
    suspend fun createTask(request: CreateHousekeepingTaskRequestDto): Result<HousekeepingTaskDto>
    suspend fun updateTask(id: Long, request: UpdateHousekeepingTaskRequestDto): Result<HousekeepingTaskDto>
    suspend fun assignableStaff(scope: String = "housekeeping"): Result<List<AssignableStaffMemberDto>>
}

class DefaultHousekeepingRepository(
    private val api: HousekeepingApi,
) : HousekeepingRepository {
    override suspend fun board(): Result<HousekeepingBoardResponseDto> = safeApiCall { api.board() }

    override suspend fun tasks(
        status: String?,
        unassigned: Boolean?,
        page: Long,
    ): Result<HousekeepingTaskListResponseDto> = safeApiCall {
        api.tasks(status = status, unassigned = unassigned, page = page, pageSize = 30)
    }

    override suspend fun createTask(request: CreateHousekeepingTaskRequestDto): Result<HousekeepingTaskDto> =
        safeApiCall { api.createTask(request) }

    override suspend fun updateTask(id: Long, request: UpdateHousekeepingTaskRequestDto): Result<HousekeepingTaskDto> =
        safeApiCall { api.updateTask(id, request) }

    override suspend fun assignableStaff(scope: String): Result<List<AssignableStaffMemberDto>> =
        safeApiCall { api.assignableStaff(scope) }
}
