package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.CreateMaintenanceTicketRequestDto
import com.hotelapp.staff.data.dto.MaintenanceTicketDto
import com.hotelapp.staff.data.dto.MaintenanceTicketListResponseDto
import com.hotelapp.staff.data.dto.UpdateMaintenanceTicketRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MaintenanceApi {
    @GET("/api/maintenance")
    suspend fun list(
        @Query("status") status: String? = null,
        @Query("priority") priority: String? = null,
        @Query("page") page: Long = 1,
        @Query("page_size") pageSize: Long = 30,
    ): MaintenanceTicketListResponseDto

    @POST("/api/maintenance")
    suspend fun create(
        @Body body: CreateMaintenanceTicketRequestDto,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): MaintenanceTicketDto

    @PATCH("/api/maintenance/{id}")
    suspend fun update(
        @Path("id") id: Long,
        @Body body: UpdateMaintenanceTicketRequestDto,
    ): MaintenanceTicketDto
}
