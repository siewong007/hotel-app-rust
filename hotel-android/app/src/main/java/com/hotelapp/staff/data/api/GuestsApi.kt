package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.GuestPaginatedResponseDto
import com.hotelapp.staff.data.dto.GuestProfileDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GuestsApi {
    @GET("/api/guests")
    suspend fun list(
        @Query("search") search: String? = null,
        @Query("page") page: Long = 1,
        @Query("page_size") pageSize: Long = 25,
    ): GuestPaginatedResponseDto

    @GET("/api/guests/{id}/profile")
    suspend fun profile(@Path("id") id: Long): GuestProfileDto
}
