package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.HealthResponseDto
import com.hotelapp.staff.data.dto.PublicSettingDto
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface SettingsApi {
    @GET("/health")
    suspend fun health(): HealthResponseDto

    @GET("/api/settings/public")
    suspend fun publicSettings(): List<PublicSettingDto>

    @GET("/api/settings")
    suspend fun settings(@Query("category") category: String? = null): List<PublicSettingDto>

    @GET
    suspend fun healthAt(@Url url: String): HealthResponseDto

    @GET
    suspend fun publicSettingsAt(@Url url: String): List<PublicSettingDto>
}
