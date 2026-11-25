package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.StaffNotificationsResponseDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface NotificationsApi {
    @GET("/api/system/notifications")
    suspend fun list(): StaffNotificationsResponseDto

    @POST("/api/system/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: Long): JsonElement

    @POST("/api/system/notifications/read-all")
    suspend fun markAllRead(): JsonElement
}
