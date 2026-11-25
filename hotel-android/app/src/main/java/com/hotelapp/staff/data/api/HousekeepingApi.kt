package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.AssignableStaffMemberDto
import com.hotelapp.staff.data.dto.CreateHousekeepingTaskRequestDto
import com.hotelapp.staff.data.dto.HousekeepingBoardResponseDto
import com.hotelapp.staff.data.dto.HousekeepingTaskDto
import com.hotelapp.staff.data.dto.HousekeepingTaskListResponseDto
import com.hotelapp.staff.data.dto.UpdateHousekeepingTaskRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HousekeepingApi {
    @GET("/api/housekeeping/board")
    suspend fun board(): HousekeepingBoardResponseDto

    @GET("/api/housekeeping/tasks")
    suspend fun tasks(
        @Query("status") status: String? = null,
        @Query("unassigned") unassigned: Boolean? = null,
        @Query("page") page: Long = 1,
        @Query("page_size") pageSize: Long = 30,
    ): HousekeepingTaskListResponseDto

    @POST("/api/housekeeping/tasks")
    suspend fun createTask(@Body body: CreateHousekeepingTaskRequestDto): HousekeepingTaskDto

    @PATCH("/api/housekeeping/tasks/{id}")
    suspend fun updateTask(
        @Path("id") id: Long,
        @Body body: UpdateHousekeepingTaskRequestDto,
    ): HousekeepingTaskDto

    @GET("/api/housekeeping/assignable-staff")
    suspend fun assignableStaff(@Query("scope") scope: String): List<AssignableStaffMemberDto>
}
