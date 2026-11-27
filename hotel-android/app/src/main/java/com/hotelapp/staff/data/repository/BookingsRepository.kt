package com.hotelapp.staff.data.repository

import com.hotelapp.staff.core.network.HotelJson
import com.hotelapp.staff.core.network.safeApiCall
import com.hotelapp.staff.data.api.BookingsApi
import com.hotelapp.staff.data.dto.BookingBoardSummaryDto
import com.hotelapp.staff.data.dto.BookingCancellationRequestDto
import com.hotelapp.staff.data.dto.BookingDto
import com.hotelapp.staff.data.dto.BookingGroupDetailDto
import com.hotelapp.staff.data.dto.BookingStatusUpdateDto
import com.hotelapp.staff.data.dto.BookingTimelineEntryDto
import com.hotelapp.staff.data.dto.BookingWithDetailsDto
import com.hotelapp.staff.data.dto.CheckInAdvisoryDto
import com.hotelapp.staff.data.dto.CheckInRequestDto
import com.hotelapp.staff.data.dto.CreateBookingRequestDto
import com.hotelapp.staff.data.dto.GroupPayRequestDto
import com.hotelapp.staff.data.dto.NewReservationsResponseDto
import com.hotelapp.staff.data.dto.PaginatedResponseDto
import com.hotelapp.staff.data.dto.ReleaseBookingRequestDto
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement

interface BookingsRepository {
    suspend fun summary(): Result<BookingBoardSummaryDto>
    suspend fun newReservations(): Result<NewReservationsResponseDto>
    suspend fun list(
        page: Long,
        pageSize: Long = 25,
        search: String? = null,
        view: String? = null,
    ): Result<PaginatedResponseDto<BookingWithDetailsDto>>
    suspend fun createBooking(request: CreateBookingRequestDto): Result<BookingDto>
    suspend fun detail(id: Long): Result<BookingWithDetailsDto>
    suspend fun timeline(id: Long): Result<List<BookingTimelineEntryDto>>
    suspend fun checkinAdvisory(id: Long): Result<CheckInAdvisoryDto>
    suspend fun checkIn(id: Long, request: CheckInRequestDto): Result<Unit>
    suspend fun checkOut(id: Long): Result<Unit>
    suspend fun voidBooking(id: Long, reason: String?): Result<Unit>
    suspend fun releaseHold(id: Long, reason: String): Result<Unit>
    suspend fun groupPay(id: Long, request: GroupPayRequestDto): Result<Unit> =
        Result.failure(UnsupportedOperationException("Group payment not supported."))
    suspend fun bookingGroup(id: Long): Result<BookingGroupDetailDto?> = Result.success(null)
    suspend fun reactivate(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("Reactivate booking not supported."))
    suspend fun groupReactivate(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("Group reactivate not supported."))
}

class DefaultBookingsRepository(
    private val api: BookingsApi,
) : BookingsRepository {
    override suspend fun summary(): Result<BookingBoardSummaryDto> = safeApiCall { api.summary() }

    override suspend fun newReservations(): Result<NewReservationsResponseDto> = safeApiCall { api.newReservations() }

    override suspend fun list(
        page: Long,
        pageSize: Long,
        search: String?,
        view: String?,
    ): Result<PaginatedResponseDto<BookingWithDetailsDto>> = safeApiCall {
        api.list(
            page = page,
            pageSize = pageSize,
            search = search?.trim()?.takeIf { it.isNotEmpty() },
            view = view?.takeIf { it.isNotBlank() && it != "all" },
        )
    }

    override suspend fun createBooking(request: CreateBookingRequestDto): Result<BookingDto> = safeApiCall {
        api.create(request)
    }

    override suspend fun detail(id: Long): Result<BookingWithDetailsDto> = safeApiCall { api.get(id) }

    override suspend fun timeline(id: Long): Result<List<BookingTimelineEntryDto>> = safeApiCall { api.timeline(id) }

    override suspend fun checkinAdvisory(id: Long): Result<CheckInAdvisoryDto> = safeApiCall { api.checkinAdvisory(id) }

    override suspend fun checkIn(id: Long, request: CheckInRequestDto): Result<Unit> = safeApiCall {
        api.checkIn(id, request)
    }

    override suspend fun checkOut(id: Long): Result<Unit> = safeApiCall {
        api.updateStatus(id, BookingStatusUpdateDto(status = "checked_out"))
    }

    override suspend fun voidBooking(id: Long, reason: String?): Result<Unit> = safeApiCall {
        api.voidBooking(BookingCancellationRequestDto(bookingId = id, reason = reason?.trim()?.takeIf { it.isNotEmpty() }))
    }

    override suspend fun releaseHold(id: Long, reason: String): Result<Unit> = safeApiCall {
        api.releaseBooking(id, ReleaseBookingRequestDto(reason = reason.trim()))
    }

    override suspend fun groupPay(id: Long, request: GroupPayRequestDto): Result<Unit> = safeApiCall {
        api.groupPay(id, request)
    }

    override suspend fun bookingGroup(id: Long): Result<BookingGroupDetailDto?> = safeApiCall {
        val el = api.bookingGroup(id)
        if (el is JsonNull) null else HotelJson.decodeFromJsonElement<BookingGroupDetailDto>(el)
    }

    override suspend fun reactivate(id: Long): Result<Unit> = safeApiCall {
        api.reactivate(id)
        Unit
    }

    override suspend fun groupReactivate(id: Long): Result<Unit> = safeApiCall {
        api.groupReactivate(id)
        Unit
    }
}
