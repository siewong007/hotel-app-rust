package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.EkycApi
import com.hotelapp.staff.data.dto.EkycAdminListResponseDto
import com.hotelapp.staff.data.dto.EkycApplicationDetailDto
import com.hotelapp.staff.data.dto.EkycDashboardMetricsDto
import com.hotelapp.staff.data.dto.EkycReasonCodeDto
import com.hotelapp.staff.data.dto.EkycReviewActionRequestDto
import com.hotelapp.staff.data.dto.EkycSensitiveRevealRequestDto
import com.hotelapp.staff.data.dto.EkycSensitiveRevealResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface EkycRepository {
    suspend fun dashboard(): Result<EkycDashboardMetricsDto>
    suspend fun applications(
        status: String? = null,
        riskLevel: String? = null,
        search: String? = null,
        page: Long = 1,
    ): Result<EkycAdminListResponseDto>
    suspend fun applicationDetail(id: Long): Result<EkycApplicationDetailDto>
    suspend fun applyAction(id: Long, request: EkycReviewActionRequestDto): Result<EkycApplicationDetailDto>
    suspend fun reasonCodes(): Result<List<EkycReasonCodeDto>>
    suspend fun fetchDocumentBytes(id: Long, kind: String): Result<ByteArray> =
        Result.failure(UnsupportedOperationException("Document download not supported."))
    suspend fun revealSensitive(id: Long, field: String, reason: String): Result<EkycSensitiveRevealResponseDto> =
        Result.failure(UnsupportedOperationException("Sensitive reveal not supported."))
}

class DefaultEkycRepository(
    private val api: EkycApi,
) : EkycRepository {
    override suspend fun dashboard(): Result<EkycDashboardMetricsDto> = safeApiCall { api.dashboard() }

    override suspend fun applications(
        status: String?,
        riskLevel: String?,
        search: String?,
        page: Long,
    ): Result<EkycAdminListResponseDto> = safeApiCall {
        api.applications(
            status = status?.takeIf { it.isNotBlank() && it != "all" },
            riskLevel = riskLevel?.takeIf { it.isNotBlank() && it != "all" },
            search = search?.trim()?.takeIf { it.isNotEmpty() },
            page = page,
            pageSize = 20,
        )
    }

    override suspend fun applicationDetail(id: Long): Result<EkycApplicationDetailDto> =
        safeApiCall { api.applicationDetail(id) }

    override suspend fun applyAction(
        id: Long,
        request: EkycReviewActionRequestDto,
    ): Result<EkycApplicationDetailDto> = safeApiCall { api.applyAction(id, request) }

    override suspend fun reasonCodes(): Result<List<EkycReasonCodeDto>> = safeApiCall { api.reasonCodes() }

    override suspend fun fetchDocumentBytes(id: Long, kind: String): Result<ByteArray> = safeApiCall {
        withContext(Dispatchers.IO) {
            api.document(id, kind).use { it.bytes() }
        }
    }

    override suspend fun revealSensitive(
        id: Long,
        field: String,
        reason: String,
    ): Result<EkycSensitiveRevealResponseDto> = safeApiCall {
        api.revealSensitive(id, EkycSensitiveRevealRequestDto(field = field.trim(), reason = reason.trim()))
    }
}
