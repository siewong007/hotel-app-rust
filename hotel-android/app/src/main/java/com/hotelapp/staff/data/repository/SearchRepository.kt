package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.SearchApi
import com.hotelapp.staff.data.dto.GlobalSearchResponseDto

interface SearchRepository {
    suspend fun search(
        query: String,
        types: String? = null,
        limit: Int = 20,
    ): Result<GlobalSearchResponseDto>
}

class DefaultSearchRepository(
    private val api: SearchApi,
) : SearchRepository {
    override suspend fun search(
        query: String,
        types: String?,
        limit: Int,
    ): Result<GlobalSearchResponseDto> = safeApiCall {
        api.search(
            query = query.trim(),
            types = types?.takeIf { it.isNotBlank() && it != "all" },
            limit = limit,
        )
    }
}
