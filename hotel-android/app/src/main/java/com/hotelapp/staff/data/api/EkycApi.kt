package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.EkycAdminListResponseDto
import com.hotelapp.staff.data.dto.EkycApplicationDetailDto
import com.hotelapp.staff.data.dto.EkycDashboardMetricsDto
import com.hotelapp.staff.data.dto.EkycReasonCodeDto
import com.hotelapp.staff.data.dto.EkycReviewActionRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface EkycApi {
    @GET("/api/ekyc/admin/dashboard")
    suspend fun dashboard(): EkycDashboardMetricsDto

    @GET("/api/ekyc/admin/applications")
    suspend fun applications(
        @Query("status") status: String? = null,
        @Query("risk_level") riskLevel: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Long = 1,
        @Query("page_size") pageSize: Long = 20,
    ): EkycAdminListResponseDto

    @GET("/api/ekyc/admin/applications/{id}")
    suspend fun applicationDetail(@Path("id") id: Long): EkycApplicationDetailDto

    @POST("/api/ekyc/admin/applications/{id}/actions")
    suspend fun applyAction(
        @Path("id") id: Long,
        @Body body: EkycReviewActionRequestDto,
    ): EkycApplicationDetailDto

    @GET("/api/ekyc/admin/reason-codes")
    suspend fun reasonCodes(): List<EkycReasonCodeDto>
}
