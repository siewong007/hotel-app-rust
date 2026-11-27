package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.GlobalSearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface SearchApi {
    @GET("/api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("types") types: String? = null,
        @Query("limit") limit: Int = 15,
    ): GlobalSearchResponseDto
}
