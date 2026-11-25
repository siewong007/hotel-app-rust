package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.BookingBoardSummaryDto
import com.hotelapp.staff.data.dto.BookingCancellationRequestDto
import com.hotelapp.staff.data.dto.BookingDto
import com.hotelapp.staff.data.dto.BookingStatusUpdateDto
import com.hotelapp.staff.data.dto.BookingTimelineEntryDto
import com.hotelapp.staff.data.dto.BookingWithDetailsDto
import com.hotelapp.staff.data.dto.CheckInAdvisoryDto
import com.hotelapp.staff.data.dto.CheckInRequestDto
import com.hotelapp.staff.data.dto.CreateBookingRequestDto
import com.hotelapp.staff.data.dto.NewReservationsResponseDto
import com.hotelapp.staff.data.dto.PaginatedResponseDto
import com.hotelapp.staff.data.dto.ReleaseBookingRequestDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BookingsApi {
    @GET("/api/bookings/summary")
    suspend fun summary(): BookingBoardSummaryDto

    @GET("/api/bookings/new-reservations")
    suspend fun newReservations(): NewReservationsResponseDto

    @GET("/api/bookings")
    suspend fun list(
        @Query("page") page: Long = 1,
        @Query("page_size") pageSize: Long = 25,
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
        @Query("view") view: String? = null,
    ): PaginatedResponseDto<BookingWithDetailsDto>

    @POST("/api/bookings")
    suspend fun create(@Body body: CreateBookingRequestDto): BookingDto

    @GET("/api/bookings/{id}")
    suspend fun get(@Path("id") id: Long): BookingWithDetailsDto

    @GET("/api/bookings/{id}/timeline")
    suspend fun timeline(@Path("id") id: Long): List<BookingTimelineEntryDto>

    @GET("/api/bookings/{id}/checkin-advisory")
    suspend fun checkinAdvisory(@Path("id") id: Long): CheckInAdvisoryDto

    @POST("/api/bookings/{id}/checkin")
    suspend fun checkIn(
        @Path("id") id: Long,
        @Body request: CheckInRequestDto = CheckInRequestDto(),
    ): BookingDto

    @PATCH("/api/bookings/{id}")
    suspend fun updateStatus(
        @Path("id") id: Long,
        @Body body: BookingStatusUpdateDto,
    ): BookingDto

    @POST("/api/bookings/void")
    suspend fun voidBooking(@Body body: BookingCancellationRequestDto): JsonElement

    @POST("/api/bookings/{id}/release")
    suspend fun releaseBooking(
        @Path("id") id: Long,
        @Body body: ReleaseBookingRequestDto,
    ): JsonElement
}
