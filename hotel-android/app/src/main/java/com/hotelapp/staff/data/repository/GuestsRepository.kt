package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.GuestsApi
import com.hotelapp.staff.data.dto.GuestPaginatedResponseDto
import com.hotelapp.staff.data.dto.GuestProfileDto

interface GuestsRepository {
    suspend fun list(search: String? = null, page: Long = 1): Result<GuestPaginatedResponseDto>
    suspend fun profile(id: Long): Result<GuestProfileDto>
}

class DefaultGuestsRepository(
    private val api: GuestsApi,
) : GuestsRepository {
    override suspend fun list(search: String?, page: Long): Result<GuestPaginatedResponseDto> = safeApiCall {
        api.list(search = search?.trim()?.takeIf { it.isNotEmpty() }, page = page, pageSize = 25)
    }

    override suspend fun profile(id: Long): Result<GuestProfileDto> = safeApiCall { api.profile(id) }
}
