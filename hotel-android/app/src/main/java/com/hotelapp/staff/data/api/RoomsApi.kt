package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.HotelOccupancySummaryDto
import com.hotelapp.staff.data.dto.RoomDetailedStatusDto
import com.hotelapp.staff.data.dto.RoomDto
import com.hotelapp.staff.data.dto.RoomStatusUpdateInputDto
import com.hotelapp.staff.data.dto.RoomWithOccupancyDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface RoomsApi {
    @GET("/api/rooms/with-occupancy")
    suspend fun withOccupancy(): List<RoomWithOccupancyDto>

    @GET("/api/rooms/occupancy/summary")
    suspend fun occupancySummary(): HotelOccupancySummaryDto

    @GET("/api/rooms/{id}/detailed")
    suspend fun detailed(@Path("id") id: Long): RoomDetailedStatusDto

    @PUT("/api/rooms/{id}/status")
    suspend fun updateStatus(
        @Path("id") id: Long,
        @Body input: RoomStatusUpdateInputDto,
    ): RoomDto

    @POST("/api/rooms/{id}/end-cleaning")
    suspend fun endCleaning(@Path("id") id: Long): JsonElement

    @POST("/api/rooms/{id}/end-maintenance")
    suspend fun endMaintenance(@Path("id") id: Long): RoomDto
}
