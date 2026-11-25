package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.NotificationsApi
import com.hotelapp.staff.data.dto.StaffNotificationsResponseDto

interface NotificationsRepository {
    suspend fun list(): Result<StaffNotificationsResponseDto>
    suspend fun markRead(id: Long): Result<Unit>
    suspend fun markAllRead(): Result<Unit>
}

class DefaultNotificationsRepository(
    private val api: NotificationsApi,
) : NotificationsRepository {
    override suspend fun list(): Result<StaffNotificationsResponseDto> = safeApiCall { api.list() }
    override suspend fun markRead(id: Long): Result<Unit> = safeApiCall { api.markRead(id) }
    override suspend fun markAllRead(): Result<Unit> = safeApiCall { api.markAllRead() }
}
