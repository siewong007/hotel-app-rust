package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.GuestRelationsOverviewDto
import com.hotelapp.staff.data.dto.NightAuditPreviewDto
import retrofit2.http.GET
import retrofit2.http.Query

interface OperationsApi {
    @GET("/api/night-audit/preview")
    suspend fun nightAuditPreview(@Query("date") date: String): NightAuditPreviewDto

    @GET("/api/guest-relations/overview")
    suspend fun guestRelationsOverview(): GuestRelationsOverviewDto
}
