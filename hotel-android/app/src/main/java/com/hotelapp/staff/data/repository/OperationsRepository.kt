package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.OperationsApi
import com.hotelapp.staff.data.dto.GuestRelationsOverviewDto
import com.hotelapp.staff.data.dto.NightAuditPreviewDto
import java.time.LocalDate

interface OperationsRepository {
    suspend fun nightAuditPreview(date: String = LocalDate.now().toString()): Result<NightAuditPreviewDto>
    suspend fun guestRelationsOverview(): Result<GuestRelationsOverviewDto>
}

class DefaultOperationsRepository(
    private val api: OperationsApi,
) : OperationsRepository {
    override suspend fun nightAuditPreview(date: String): Result<NightAuditPreviewDto> =
        safeApiCall { api.nightAuditPreview(date) }

    override suspend fun guestRelationsOverview(): Result<GuestRelationsOverviewDto> =
        safeApiCall { api.guestRelationsOverview() }
}
